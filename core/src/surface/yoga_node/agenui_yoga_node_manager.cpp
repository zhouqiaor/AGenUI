#include "agenui_yoga_node_manager.h"

#include <yoga/Yoga.h>
#include "surface/yoga_node/agenui_tabs_yoga_helper.h"
#include "surface/virtual_dom/agenui_virtual_dom_node.h"

namespace agenui {

// Maximum Yoga tree depth before layout is skipped to prevent stack overflow.
// Yoga's YGLayoutNodeInternal recurses one frame per tree level; a deeply
// nested component tree (e.g. nested Lists orContainers) can exceed the
// default thread stack (8 MB on most platforms) and trigger SIGSEGV.
// 256 levels is well above any realistic UI tree depth (typical apps <30),
// but safely below the ~512-frame limit that overflows an 8 MB stack.
static constexpr uint32_t kMaxTreeDepth = 256;

YogaNodeManager::YogaNodeManager() {
    // _nodes is the YogaNode pool for all VirtualDOMNodes.
    // The layout root is _nodes["root"], calculated via calculateLayout.
}

YogaNodeManager::~YogaNodeManager() {
    // Detach all YG parent-child relationships first via clearAll(), then batch free,
    // to avoid UAF from uncertain unique_ptr<YogaNode> destruction order.
    clearAll();
}

YogaNode* YogaNodeManager::createNode(const std::string& nodeId) {
    auto it = _nodes.find(nodeId);
    if (it != _nodes.end()) {
        return it->second.get();
    }
    auto node = std::make_unique<YogaNode>();
    auto* ptr = node.get();
    _nodes[nodeId] = std::move(node);
    return ptr;
}
YogaNode* YogaNodeManager::getNode(const std::string& nodeId) const {
    auto it = _nodes.find(nodeId);
    return it != _nodes.end() ? it->second.get() : nullptr;
}

void YogaNodeManager::removeNode(const std::string& nodeId) {
    auto it = _nodes.find(nodeId);
    if (it == _nodes.end()) {
        return;
    }

    YogaNode* node = it->second.get();
    if (node && node->get()) {
        YGNodeRef ygNode = node->get();

        // Detach from parent
        if (node->_hasOwner) {
            YGNodeRef owner = YGNodeGetOwner(ygNode);
            if (owner) {
                YGNodeRemoveChild(owner, ygNode);
            }
            node->_hasOwner = false;
        }

        // Reset _hasOwner for direct children only.
        // Uses YGNodeGetContext (which stores YogaNode* back-pointer) for
        // O(childCount) instead of O(pool) full scan.
        // Previously this iterated the entire _nodes map checking
        // YGNodeGetOwner for each — O(n) where n = total nodes in the pool.
        uint32_t childCount = YGNodeGetChildCount(ygNode);
        for (uint32_t i = 0; i < childCount; ++i) {
            YGNodeRef childYG = YGNodeGetChild(ygNode, i);
            if (!childYG) continue;
            auto* childWrapper = static_cast<YogaNode*>(YGNodeGetContext(childYG));
            if (childWrapper) {
                childWrapper->_hasOwner = false;
            }
        }
        YGNodeRemoveAllChildren(ygNode);
    }

    _nodes.erase(it);
    _tabsSelectedIndices.erase(nodeId);
}

void YogaNodeManager::clearAll() {
    // Detach all YG parent-child relationships before batch destruction.
    // Without this, uncertain map destruction order causes UAF.
    for (auto& kv : _nodes) {
        YogaNode* node = kv.second.get();
        if (!node || !node->get()) continue;
        YGNodeRef ygNode = node->get();
        if (node->_hasOwner) {
            YGNodeRef owner = YGNodeGetOwner(ygNode);
            if (owner) {
                YGNodeRemoveChild(owner, ygNode);
            }
            node->_hasOwner = false;
        }
        YGNodeRemoveAllChildren(ygNode);
    }
    _nodes.clear();
    _rootNode = nullptr;
    _tabsSelectedIndices.clear();
}

void YogaNodeManager::insertChild(const std::string& parentId,
                                   const std::string& childId,
                                   uint32_t index) {
    YogaNode* parent = getNode(parentId);
    YogaNode* child  = getNode(childId);
    if (parent && child) {
        parent->insertChild(*child, index);
    }
}

void YogaNodeManager::removeChild(const std::string& parentId,
                                   const std::string& childId) {
    YogaNode* parent = getNode(parentId);
    YogaNode* child  = getNode(childId);
    if (parent && child) {
        parent->removeChild(*child);
    }
}

void YogaNodeManager::calculateLayout(float rootWidth, float rootHeight) {
    YogaNode* rootNode = _rootNode;
    if (!rootNode || !rootNode->get()) return;

    // Guard: compute tree depth and skip layout if it exceeds the safety limit.
    // This prevents YGNodeCalculateLayout from recursing into a tree so deep
    // that YGLayoutNodeInternal overflows the thread stack and crashes with
    // SIGSEGV. The actual limit is enforced by computeYogaTreeDepth() which
    // walks the YGNode tree (not the VirtualDOM tree) via YGNodeGetChild.
    uint32_t depth = computeYogaTreeDepth(rootNode->get());
    if (depth > kMaxTreeDepth) {
        AGENUI_LOG("calculateLayout: skipping layout, tree depth %u exceeds limit %u",
                   depth, kMaxTreeDepth);
        return;
    }

    // Yoga does NOT apply aspect-ratio on the root node (only on children).
    // If the root has aspect-ratio set and its height is not explicitly defined,
    // manually derive the height from the known root width before calling
    // YGNodeCalculateLayout so Yoga receives an explicit (EXACT) height constraint.
    {
        YGNodeRef ygRoot = rootNode->get();
        float ar = YGNodeStyleGetAspectRatio(ygRoot);
        if (!YGFloatIsUndefined(ar) && ar > 0.0f) {
            // Check that height is NOT explicitly set (unit is Auto or Undefined)
            YGValue hVal = YGNodeStyleGetHeight(ygRoot);
            bool heightIsImplicit = (hVal.unit == YGUnitAuto ||
                                     hVal.unit == YGUnitUndefined ||
                                     (hVal.unit == YGUnitPoint && YGFloatIsUndefined(hVal.value)));
            if (heightIsImplicit && !YGFloatIsUndefined(rootWidth) && rootWidth > 0.0f) {
                float computedH = rootWidth / ar;
                YGNodeStyleSetHeight(ygRoot, computedH);
            }
        }
    }

    YGNodeCalculateLayout(
        rootNode->get(),
        rootWidth,
        YGUndefined,
        YGDirectionLTR);
    (void)rootHeight;
}

bool YogaNodeManager::calculateLayoutWithAdjust(
        std::shared_ptr<VirtualDOMNode> root,
        float surfaceWidth) {
    // Fast path: if no Tabs components are registered, skip the two-pass
    // layout entirely. The second pass (updateMinHeightRecursive + re-layout)
    // only applies when Tabs need their minHeight adjusted after the first
    // layout pass measures inherent content heights.
    if (_tabsSelectedIndices.empty()) {
        calculateLayout(surfaceWidth);
        return false;
    }
    calculateLayout(surfaceWidth);
    if (TabsYogaHelper::updateMinHeightRecursive(root, _tabsSelectedIndices, surfaceWidth)) {
        calculateLayout(surfaceWidth);
        return true;
    }
    return false;
}

void YogaNodeManager::updateTabsSelectedIndex(const std::string& tabsId, int selectedIndex) {
    _tabsSelectedIndices[tabsId] = selectedIndex;
}

uint32_t YogaNodeManager::computeYogaTreeDepth(YGNodeRef root) const {
    if (!root) return 0;
    uint32_t maxDepth = 0;
    uint32_t childCount = YGNodeGetChildCount(root);
    for (uint32_t i = 0; i < childCount; ++i) {
        YGNodeRef child = YGNodeGetChild(root, i);
        if (child) {
            uint32_t childDepth = computeYogaTreeDepth(child);
            if (childDepth > maxDepth) maxDepth = childDepth;
        }
    }
    return maxDepth + 1;
}

}  // namespace agenui
