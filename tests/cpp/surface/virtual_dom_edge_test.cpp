#include <gtest/gtest.h>
#include "surface/agenui_virtual_dom.h"
#include <memory>
#include <string>

using agenui::VirtualDOM;
using agenui::VirtualNode;
using agenui::NodeType;

// =============================================================================
// VirtualNode creation and property tests
// =============================================================================

TEST(VirtualNodeEdge, CreateRootNode) {
    auto node = std::make_shared<VirtualNode>();
    node->id = "root";
    node->type = "column";
    EXPECT_EQ(node->id, "root");
    EXPECT_EQ(node->type, "column");
    EXPECT_TRUE(node->children.empty());
}

TEST(VirtualNodeEdge, AddChildNode) {
    auto parent = std::make_shared<VirtualNode>();
    parent->id = "parent";
    parent->type = "column";

    auto child = std::make_shared<VirtualNode>();
    child->id = "child1";
    child->type = "text";

    parent->children.push_back(child);
    EXPECT_EQ(parent->children.size(), 1u);
    EXPECT_EQ(parent->children[0]->id, "child1");
}

TEST(VirtualNodeEdge, AddMultipleChildren) {
    auto parent = std::make_shared<VirtualNode>();
    parent->id = "list";
    parent->type = "list";

    for (int i = 0; i < 10; i++) {
        auto child = std::make_shared<VirtualNode>();
        child->id = "item_" + std::to_string(i);
        child->type = "text";
        parent->children.push_back(child);
    }
    EXPECT_EQ(parent->children.size(), 10u);
    EXPECT_EQ(parent->children[5]->id, "item_5");
}

TEST(VirtualNodeEdge, DeeplyNestedTree) {
    auto root = std::make_shared<VirtualNode>();
    root->id = "root";
    root->type = "column";

    auto current = root;
    for (int i = 0; i < 100; i++) {
        auto child = std::make_shared<VirtualNode>();
        child->id = "level_" + std::to_string(i);
        child->type = "container";
        current->children.push_back(child);
        current = child;
    }
    // Walk down to verify
    auto walker = root;
    for (int i = 0; i < 100; i++) {
        ASSERT_EQ(walker->children.size(), 1u);
        walker = walker->children[0];
        EXPECT_EQ(walker->id, "level_" + std::to_string(i));
    }
}

TEST(VirtualNodeEdge, FindById_Root) {
    auto root = std::make_shared<VirtualNode>();
    root->id = "find_me";
    root->type = "column";

    auto found = VirtualDOM::findNode(root, "find_me");
    EXPECT_NE(found, nullptr);
    EXPECT_EQ(found->id, "find_me");
}

TEST(VirtualNodeEdge, FindById_DeepChild) {
    auto root = std::make_shared<VirtualNode>();
    root->id = "root";
    root->type = "column";

    auto current = root;
    for (int i = 0; i < 10; i++) {
        auto child = std::make_shared<VirtualNode>();
        child->id = "node_" + std::to_string(i);
        child->type = "container";
        current->children.push_back(child);
        current = child;
    }

    auto found = VirtualDOM::findNode(root, "node_5");
    EXPECT_NE(found, nullptr);
    EXPECT_EQ(found->id, "node_5");
}

TEST(VirtualNodeEdge, FindById_NotFound) {
    auto root = std::make_shared<VirtualNode>();
    root->id = "root";
    root->type = "column";

    auto found = VirtualDOM::findNode(root, "nonexistent");
    EXPECT_EQ(found, nullptr);
}

TEST(VirtualNodeEdge, FindById_NullRoot) {
    std::shared_ptr<VirtualNode> nullRoot = nullptr;
    auto found = VirtualDOM::findNode(nullRoot, "anything");
    EXPECT_EQ(found, nullptr);
}

TEST(VirtualNodeEdge, FindById_DuplicateIds_ReturnsFirst) {
    auto root = std::make_shared<VirtualNode>();
    root->id = "dup";
    root->type = "column";

    auto child1 = std::make_shared<VirtualNode>();
    child1->id = "dup";
    child1->type = "text";
    root->children.push_back(child1);

    auto child2 = std::make_shared<VirtualNode>();
    child2->id = "dup";
    child2->type = "text";
    root->children.push_back(child2);

    auto found = VirtualDOM::findNode(root, "dup");
    EXPECT_NE(found, nullptr);
    EXPECT_EQ(found.get(), root.get()); // should find root first
}

TEST(VirtualNodeEdge, RemoveChild_FromParent) {
    auto parent = std::make_shared<VirtualNode>();
    parent->id = "parent";
    parent->type = "column";

    auto child1 = std::make_shared<VirtualNode>();
    child1->id = "c1";
    auto child2 = std::make_shared<VirtualNode>();
    child2->id = "c2";
    auto child3 = std::make_shared<VirtualNode>();
    child3->id = "c3";

    parent->children = {child1, child2, child3};
    ASSERT_EQ(parent->children.size(), 3u);

    // Remove middle child
    parent->children.erase(parent->children.begin() + 1);
    EXPECT_EQ(parent->children.size(), 2u);
    EXPECT_EQ(parent->children[0]->id, "c1");
    EXPECT_EQ(parent->children[1]->id, "c3");
}

TEST(VirtualNodeEdge, ReplaceChild) {
    auto parent = std::make_shared<VirtualNode>();
    parent->id = "parent";
    parent->type = "column";

    auto oldChild = std::make_shared<VirtualNode>();
    oldChild->id = "old";
    parent->children.push_back(oldChild);

    auto newChild = std::make_shared<VirtualNode>();
    newChild->id = "new";
    parent->children[0] = newChild;

    EXPECT_EQ(parent->children[0]->id, "new");
}

TEST(VirtualNodeEdge, ClearAllChildren) {
    auto parent = std::make_shared<VirtualNode>();
    parent->id = "parent";
    parent->type = "column";

    for (int i = 0; i < 5; i++) {
        auto child = std::make_shared<VirtualNode>();
        child->id = "child_" + std::to_string(i);
        parent->children.push_back(child);
    }
    ASSERT_EQ(parent->children.size(), 5u);

    parent->children.clear();
    EXPECT_TRUE(parent->children.empty());
}

TEST(VirtualNodeEdge, EmptyId_ParsesWithoutCrash) {
    auto node = std::make_shared<VirtualNode>();
    node->id = "";
    node->type = "text";
    EXPECT_TRUE(node->id.empty());
}

TEST(VirtualNodeEdge, VeryLongId_NoCrash) {
    auto node = std::make_shared<VirtualNode>();
    node->id = std::string(10000, 'a');
    node->type = "text";
    EXPECT_EQ(node->id.length(), 10000u);
}

TEST(VirtualNodeEdge, IdWithSpecialChars_NoCrash) {
    auto node = std::make_shared<VirtualNode>();
    node->id = "id with spaces & special!@#$%^&*()";
    node->type = "text";
    EXPECT_FALSE(node->id.empty());
}

TEST(VirtualNodeEdge, IdWithUnicode_NoCrash) {
    auto node = std::make_shared<VirtualNode>();
    node->id = "\u4e2d\u6587\u8282\u70b9"; // 中文节点
    node->type = "text";
    EXPECT_FALSE(node->id.empty());
}

TEST(VirtualNodeEdge, ManySiblings_PerformanceSafe) {
    auto parent = std::make_shared<VirtualNode>();
    parent->id = "list";
    parent->type = "list";

    for (int i = 0; i < 1000; i++) {
        auto child = std::make_shared<VirtualNode>();
        child->id = "item_" + std::to_string(i);
        child->type = "text";
        parent->children.push_back(child);
    }
    EXPECT_EQ(parent->children.size(), 1000u);

    // Find last item
    auto found = VirtualDOM::findNode(parent, "item_999");
    EXPECT_NE(found, nullptr);
}
