#pragma once

#include <string>
#include <vector>

namespace agenui {

/**
 * @brief A single hit region in the HitMap.
 * @remark Populated by VirtualDOM::exportHitMap() after layout, consumed
 *         by the platform rendering layer to build clickable overlays.
 */
struct HitRegion {
    std::string componentId;   ///< Component ID for event routing
    float x = 0.0f;           ///< Left edge in px (relative to surface)
    float y = 0.0f;           ///< Top edge in px
    float w = 0.0f;           ///< Width in px
    float h = 0.0f;           ///< Height in px
    std::string action;        ///< Action type (e.g. "click", "navigate")
    std::string actionValue;   ///< Action value (e.g. route or payload)
};

/**
 * @brief Virtual DOM observer interface
 * @remark Used to listen for structural change events on the virtual DOM tree
 */
class IVirtualDOMObserver {
public:
    /**
     * @brief Virtual destructor
     */
    virtual ~IVirtualDOMObserver() = default;

    /**
     * @brief Node update callback
     * @param componentId Component ID
     * @param nodeJson Node JSON string
     * @remark Called when a virtual DOM node is updated
     */
    virtual void onNodeUpdate(const std::string& componentId, const std::string& nodeJson) = 0;

    /**
     * @brief Node added callback
     * @param parentId Parent node ID
     * @param nodeJson Node JSON string
     * @remark Called when a virtual DOM node is added
     */
    virtual void onNodeAdded(const std::string& parentId, const std::string& nodeJson) = 0;

    /**
     * @brief Node removed callback
     * @param parentId Parent node ID
     * @param id Node ID
     * @remark Called when a virtual DOM node is removed
     */
    virtual void onNodeRemoved(const std::string& parentId, const std::string& id) = 0;

    /**
     * @brief HitMap ready callback
     * @param hitRegions Vector of hit regions computed from the laid-out tree
     * @remark Called after layout when the HitMap has been exported. Default
     *        implementation is a no-op so existing observers are unaffected.
     *        The platform rendering layer overrides this to build clickable
     *        overlays (e.g. Glance HitRegionOverlay).
     */
    virtual void onHitMapReady(const std::vector<HitRegion>& hitRegions) { (void)hitRegions; }
};

}  // namespace agenui