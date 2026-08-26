// AGenUI Windows P2 - Full-chain integration tests
// Tests the complete A2UI JSON -> listener capture chain by simulating
// onComponentsAdd / onComponentsUpdate callbacks with multi-component trees,
// nested layouts, large data sets, and Yoga layout coordinates.
//
// The AGenUI engine itself (DLL + COM init) is NOT started; instead we feed
// the same JSON the engine would produce directly into the listener, which
// is the exact contract the engine satisfies via IAGenUIMessageListener.

#include "win_message_listener.h"

#include <gtest/gtest.h>
#include <nlohmann/json.hpp>

#include <string>
#include <vector>

using agenui_win::CapturedComponent;
using agenui_win::WindowsMessageListener;
using agenui::ComponentsAddMessage;
using agenui::ComponentsUpdateMessage;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

// Build a single ComponentsAddMessage from a JSON component string.
static ComponentsAddMessage makeAdd(const std::string& componentJson,
                                    const std::string& parentId = "root",
                                    const std::string& componentId = "cmp") {
    ComponentsAddMessage m;
    m.parentId = parentId;
    m.componentId = componentId;
    m.component = componentJson;
    return m;
}

// Build a single-element vector of ComponentsAddMessage. Convenience wrapper
// for tests that pass exactly one component to onComponentsAdd.
static std::vector<ComponentsAddMessage> makeAddOne(const std::string& componentJson,
                                                    const std::string& parentId = "root",
                                                    const std::string& componentId = "cmp") {
    return {makeAdd(componentJson, parentId, componentId)};
}

// Find a captured component by id. Returns nullptr if not found.
static const CapturedComponent* findById(const std::vector<CapturedComponent>& comps,
                                         const std::string& id) {
    for (const auto& c : comps) {
        if (c.id == id) return &c;
    }
    return nullptr;
}

// Build a Text component JSON with the given id/text and optional styles.
static std::string makeTextJson(const std::string& id,
                                const std::string& text,
                                const std::string& stylesJson = "") {
    std::string out = R"({"id":")" + id + R"(","component":"Text","text":")" + text + R"(")";
    if (!stylesJson.empty()) {
        out += ",\"styles\":" + stylesJson;
    }
    out += "}";
    return out;
}

// Build a Button component JSON with the given id/text and optional styles.
static std::string makeButtonJson(const std::string& id,
                                  const std::string& text,
                                  const std::string& stylesJson = "") {
    std::string out = R"({"id":")" + id + R"(","component":"Button","text":")" + text + R"(")";
    if (!stylesJson.empty()) {
        out += ",\"styles\":" + stylesJson;
    }
    out += "}";
    return out;
}

// Build an Image component JSON with the given id/src and optional styles.
static std::string makeImageJson(const std::string& id,
                                 const std::string& src,
                                 const std::string& stylesJson = "") {
    std::string out = R"({"id":")" + id + R"(","component":"Image","src":")" + src + R"(")";
    if (!stylesJson.empty()) {
        out += ",\"styles\":" + stylesJson;
    }
    out += "}";
    return out;
}

// Build a Column container JSON with the given id and children JSON array.
static std::string makeColumnJson(const std::string& id,
                                  const std::string& childrenArray,
                                  const std::string& stylesJson = "") {
    std::string out = R"({"id":")" + id + R"(","component":"Column","children":)" + childrenArray;
    if (!stylesJson.empty()) {
        out += ",\"styles\":" + stylesJson;
    }
    out += "}";
    return out;
}

// ---------------------------------------------------------------------------
// 1. Multi-component tree test (nested Column)
// ---------------------------------------------------------------------------
TEST(IntegrationTest, MultiComponentTreeCapturesAllLevels) {
    WindowsMessageListener listener;

    // Simulate the engine sending a Column root with 2 Text children via
    // onComponentsAdd. The engine flattens the tree but preserves parentId.
    std::vector<ComponentsAddMessage> msgs;

    // Root Column (parent = "surface")
    msgs.push_back(makeAdd(
        R"({"id":"col1","component":"Column","styles":{"x":0,"y":0,"width":400,"height":600}})",
        "surface", "col1"));

    // Child Text 1 (parent = "col1")
    msgs.push_back(makeAdd(
        R"({"id":"t1","component":"Text","text":"First","styles":{"x":0,"y":0,"width":400,"height":30}})",
        "col1", "t1"));

    // Child Text 2 (parent = "col1")
    msgs.push_back(makeAdd(
        R"({"id":"t2","component":"Text","text":"Second","styles":{"x":0,"y":30,"width":400,"height":30}})",
        "col1", "t2"));

    listener.onComponentsAdd("main", msgs);

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 3u);

    // Root Column
    const auto* col = findById(comps, "col1");
    ASSERT_NE(col, nullptr);
    EXPECT_EQ(col->type, "Column");
    EXPECT_EQ(col->parentId, "surface");
    EXPECT_FLOAT_EQ(col->width, 400.0f);
    EXPECT_FLOAT_EQ(col->height, 600.0f);

    // Child 1
    const auto* t1 = findById(comps, "t1");
    ASSERT_NE(t1, nullptr);
    EXPECT_EQ(t1->type, "Text");
    EXPECT_EQ(t1->parentId, "col1");
    EXPECT_EQ(t1->text, "First");
    EXPECT_FLOAT_EQ(t1->y, 0.0f);

    // Child 2
    const auto* t2 = findById(comps, "t2");
    ASSERT_NE(t2, nullptr);
    EXPECT_EQ(t2->type, "Text");
    EXPECT_EQ(t2->parentId, "col1");
    EXPECT_EQ(t2->text, "Second");
    EXPECT_FLOAT_EQ(t2->y, 30.0f);
}

// ---------------------------------------------------------------------------
// 2. Complete component tree verification
// Column root -> 3 Text + 1 Button + 1 Image
// ---------------------------------------------------------------------------
TEST(IntegrationTest, CompleteComponentTreeAllFieldsVerified) {
    WindowsMessageListener listener;

    std::vector<ComponentsAddMessage> msgs;

    // Root Column
    msgs.push_back(makeAdd(
        R"({"id":"root","component":"Column","styles":{"x":0,"y":0,"width":800,"height":600}})",
        "surface", "root"));

    // 3 Text components
    msgs.push_back(makeAdd(
        R"({"id":"title","component":"Text","text":"Hello AGenUI","styles":{"font-size":"48px","text-align":"center","color":"#000000","x":0,"y":0,"width":800,"height":60}})",
        "root", "title"));

    msgs.push_back(makeAdd(
        R"({"id":"subtitle","component":"Text","text":"Phase 2 Tests","styles":{"font-size":"24px","color":"#000000E6","x":0,"y":60,"width":800,"height":30}})",
        "root", "subtitle"));

    msgs.push_back(makeAdd(
        R"({"id":"body","component":"Text","text":"Body text here","styles":{"font-size":"16px","text-align":"left","x":0,"y":90,"width":800,"height":24}})",
        "root", "body"));

    // 1 Button
    msgs.push_back(makeAdd(
        R"({"id":"actionBtn","component":"Button","text":"Click Me","styles":{"background-color":"#007DFF","border-radius":"8px","border-width":"0px","padding":"12px","font-size":"16px","x":300,"y":120,"width":200,"height":44}})",
        "root", "actionBtn"));

    // 1 Image
    msgs.push_back(makeAdd(
        R"({"id":"heroImage","component":"Image","src":"https://example.com/hero.png","styles":{"x":0,"y":200,"width":800,"height":400}})",
        "root", "heroImage"));

    listener.onComponentsAdd("main", msgs);

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 6u);

    // ---- Root Column ----
    const auto* root = findById(comps, "root");
    ASSERT_NE(root, nullptr);
    EXPECT_EQ(root->type, "Column");
    EXPECT_EQ(root->parentId, "surface");
    EXPECT_FLOAT_EQ(root->x, 0.0f);
    EXPECT_FLOAT_EQ(root->y, 0.0f);
    EXPECT_FLOAT_EQ(root->width, 800.0f);
    EXPECT_FLOAT_EQ(root->height, 600.0f);
    EXPECT_TRUE(root->hasStyles);

    // ---- Title Text ----
    const auto* title = findById(comps, "title");
    ASSERT_NE(title, nullptr);
    EXPECT_EQ(title->type, "Text");
    EXPECT_EQ(title->parentId, "root");
    EXPECT_EQ(title->text, "Hello AGenUI");
    EXPECT_FLOAT_EQ(title->fontSize, 48.0f);
    EXPECT_EQ(title->textAlign, "center");
    EXPECT_EQ(title->textColor, "#000000");
    EXPECT_FLOAT_EQ(title->x, 0.0f);
    EXPECT_FLOAT_EQ(title->y, 0.0f);
    EXPECT_FLOAT_EQ(title->width, 800.0f);
    EXPECT_FLOAT_EQ(title->height, 60.0f);

    // ---- Subtitle Text ----
    const auto* subtitle = findById(comps, "subtitle");
    ASSERT_NE(subtitle, nullptr);
    EXPECT_EQ(subtitle->type, "Text");
    EXPECT_EQ(subtitle->parentId, "root");
    EXPECT_EQ(subtitle->text, "Phase 2 Tests");
    EXPECT_FLOAT_EQ(subtitle->fontSize, 24.0f);
    EXPECT_EQ(subtitle->textColor, "#000000E6");
    EXPECT_FLOAT_EQ(subtitle->y, 60.0f);

    // ---- Body Text ----
    const auto* body = findById(comps, "body");
    ASSERT_NE(body, nullptr);
    EXPECT_EQ(body->type, "Text");
    EXPECT_EQ(body->parentId, "root");
    EXPECT_EQ(body->text, "Body text here");
    EXPECT_FLOAT_EQ(body->fontSize, 16.0f);
    EXPECT_EQ(body->textAlign, "left");
    EXPECT_FLOAT_EQ(body->y, 90.0f);

    // ---- Button ----
    const auto* btn = findById(comps, "actionBtn");
    ASSERT_NE(btn, nullptr);
    EXPECT_EQ(btn->type, "Button");
    EXPECT_EQ(btn->parentId, "root");
    EXPECT_EQ(btn->text, "Click Me");
    EXPECT_EQ(btn->bgColor, "#007DFF");
    EXPECT_FLOAT_EQ(btn->borderRadius, 8.0f);
    EXPECT_FLOAT_EQ(btn->borderWidth, 0.0f);
    EXPECT_FLOAT_EQ(btn->padding, 12.0f);
    EXPECT_FLOAT_EQ(btn->fontSize, 16.0f);
    EXPECT_FLOAT_EQ(btn->x, 300.0f);
    EXPECT_FLOAT_EQ(btn->y, 120.0f);
    EXPECT_FLOAT_EQ(btn->width, 200.0f);
    EXPECT_FLOAT_EQ(btn->height, 44.0f);

    // ---- Image ----
    const auto* img = findById(comps, "heroImage");
    ASSERT_NE(img, nullptr);
    EXPECT_EQ(img->type, "Image");
    EXPECT_EQ(img->parentId, "root");
    EXPECT_EQ(img->src, "https://example.com/hero.png");
    EXPECT_FLOAT_EQ(img->x, 0.0f);
    EXPECT_FLOAT_EQ(img->y, 200.0f);
    EXPECT_FLOAT_EQ(img->width, 800.0f);
    EXPECT_FLOAT_EQ(img->height, 400.0f);
}

// ---------------------------------------------------------------------------
// 3. Update test - add then update, verify m_components replaced
// ---------------------------------------------------------------------------
TEST(IntegrationTest, UpdateReplacesAllComponents) {
    WindowsMessageListener listener;

    // First: add a set of 3 components
    std::vector<ComponentsAddMessage> addMsgs;
    addMsgs.push_back(makeAdd(makeTextJson("old1", "Old One"), "root", "old1"));
    addMsgs.push_back(makeAdd(makeTextJson("old2", "Old Two"), "root", "old2"));
    addMsgs.push_back(makeAdd(makeButtonJson("oldBtn", "Old Button"), "root", "oldBtn"));
    listener.onComponentsAdd("main", addMsgs);

    auto comps1 = listener.getCapturedComponents();
    ASSERT_EQ(comps1.size(), 3u);
    EXPECT_NE(findById(comps1, "old1"), nullptr);
    EXPECT_NE(findById(comps1, "old2"), nullptr);
    EXPECT_NE(findById(comps1, "oldBtn"), nullptr);

    // Then: onComponentsUpdate should clear and replace with a new set
    std::vector<ComponentsUpdateMessage> updMsgs;

    ComponentsUpdateMessage u1;
    u1.componentId = "new1";
    u1.component = makeTextJson("new1", "New One",
        R"({"x":0,"y":0,"width":200,"height":30})");
    updMsgs.push_back(u1);

    ComponentsUpdateMessage u2;
    u2.componentId = "new2";
    u2.component = makeButtonJson("new2", "New Button",
        R"({"background-color":"#FF0000","x":0,"y":30,"width":200,"height":44})");
    updMsgs.push_back(u2);

    listener.onComponentsUpdate("main", updMsgs);

    auto comps2 = listener.getCapturedComponents();
    ASSERT_EQ(comps2.size(), 2u);

    // Old components should be gone
    EXPECT_EQ(findById(comps2, "old1"), nullptr);
    EXPECT_EQ(findById(comps2, "old2"), nullptr);
    EXPECT_EQ(findById(comps2, "oldBtn"), nullptr);

    // New components present with correct data
    const auto* new1 = findById(comps2, "new1");
    ASSERT_NE(new1, nullptr);
    EXPECT_EQ(new1->type, "Text");
    EXPECT_EQ(new1->text, "New One");
    EXPECT_FLOAT_EQ(new1->width, 200.0f);

    const auto* new2 = findById(comps2, "new2");
    ASSERT_NE(new2, nullptr);
    EXPECT_EQ(new2->type, "Button");
    EXPECT_EQ(new2->text, "New Button");
    EXPECT_EQ(new2->bgColor, "#FF0000");
    EXPECT_FLOAT_EQ(new2->y, 30.0f);
}

// ---------------------------------------------------------------------------
// 4. Large data set test - 20 components
// ---------------------------------------------------------------------------
TEST(IntegrationTest, LargeDataSetTwentyComponentsNoLoss) {
    WindowsMessageListener listener;

    constexpr int kCount = 20;
    std::vector<ComponentsAddMessage> msgs;
    msgs.reserve(kCount);

    for (int i = 0; i < kCount; ++i) {
        std::string id = "cmp_" + std::to_string(i);
        std::string text = "Item " + std::to_string(i);
        // Alternate between Text and Button
        std::string json;
        if (i % 2 == 0) {
            json = makeTextJson(id, text,
                R"({"font-size":"16px","x":0,"y":)" +
                std::to_string(i * 30) +
                R"(,"width":200,"height":30})");
        } else {
            json = makeButtonJson(id, text,
                R"({"background-color":"#007DFF","x":0,"y":)" +
                std::to_string(i * 30) +
                R"(,"width":200,"height":30})");
        }
        msgs.push_back(makeAdd(json, "root", id));
    }

    listener.onComponentsAdd("main", msgs);

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), static_cast<size_t>(kCount));

    // Verify each component is present and has correct id/type/parent.
    for (int i = 0; i < kCount; ++i) {
        std::string id = "cmp_" + std::to_string(i);
        const auto* c = findById(comps, id);
        ASSERT_NE(c, nullptr) << "Missing component " << id;
        EXPECT_EQ(c->parentId, "root") << "Bad parentId for " << id;
        EXPECT_EQ(c->text, "Item " + std::to_string(i))
            << "Bad text for " << id;
        if (i % 2 == 0) {
            EXPECT_EQ(c->type, "Text") << "Bad type for " << id;
        } else {
            EXPECT_EQ(c->type, "Button") << "Bad type for " << id;
        }
        EXPECT_FLOAT_EQ(c->y, static_cast<float>(i * 30))
            << "Bad y for " << id;
    }
}

// ---------------------------------------------------------------------------
// 5. Yoga coordinate verification
// ---------------------------------------------------------------------------
TEST(IntegrationTest, YogaLayoutCoordinatesExtractedCorrectly) {
    WindowsMessageListener listener;

    // Construct a component with explicit x/y/width/height as numbers
    // (the format the engine outputs after Yoga layout).
    listener.onComponentsAdd("main", makeAddOne(
        R"({"id":"yoga1","component":"Text","text":"Yoga","styles":{"x":12.5,"y":34.5,"width":200.5,"height":50.25}})",
        "root", "yoga1"));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_FLOAT_EQ(cc.x, 12.5f);
    EXPECT_FLOAT_EQ(cc.y, 34.5f);
    EXPECT_FLOAT_EQ(cc.width, 200.5f);
    EXPECT_FLOAT_EQ(cc.height, 50.25f);
}

// Yoga coordinates can also be strings (e.g. "100px") - verify those too.
TEST(IntegrationTest, YogaLayoutCoordinatesAsStringPx) {
    WindowsMessageListener listener;

    listener.onComponentsAdd("main", makeAddOne(
        R"({"id":"yoga2","component":"Text","text":"YogaStr","styles":{"x":"10px","y":"20px","width":"300px","height":"40px"}})",
        "root", "yoga2"));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_FLOAT_EQ(cc.x, 10.0f);
    EXPECT_FLOAT_EQ(cc.y, 20.0f);
    EXPECT_FLOAT_EQ(cc.width, 300.0f);
    EXPECT_FLOAT_EQ(cc.height, 40.0f);
}

// Mixed numeric/string Yoga coordinates in the same tree.
TEST(IntegrationTest, MixedNumericAndStringYogaCoordinates) {
    WindowsMessageListener listener;

    std::vector<ComponentsAddMessage> msgs;
    msgs.push_back(makeAdd(
        R"({"id":"m1","component":"Text","text":"Num","styles":{"x":0,"y":0,"width":100,"height":20}})",
        "root", "m1"));
    msgs.push_back(makeAdd(
        R"({"id":"m2","component":"Text","text":"Str","styles":{"x":"0px","y":"20px","width":"100px","height":"20px"}})",
        "root", "m2"));
    listener.onComponentsAdd("main", msgs);

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 2u);

    const auto* m1 = findById(comps, "m1");
    ASSERT_NE(m1, nullptr);
    EXPECT_FLOAT_EQ(m1->width, 100.0f);
    EXPECT_FLOAT_EQ(m1->height, 20.0f);

    const auto* m2 = findById(comps, "m2");
    ASSERT_NE(m2, nullptr);
    EXPECT_FLOAT_EQ(m2->width, 100.0f);
    EXPECT_FLOAT_EQ(m2->height, 20.0f);
    EXPECT_FLOAT_EQ(m2->y, 20.0f);
}

// Zero coordinates are preserved (not defaulted).
TEST(IntegrationTest, ZeroYogaCoordinatesPreserved) {
    WindowsMessageListener listener;

    listener.onComponentsAdd("main", makeAddOne(
        R"({"id":"zero","component":"Text","text":"Zero","styles":{"x":0,"y":0,"width":0,"height":0}})",
        "root", "zero"));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_FLOAT_EQ(cc.x, 0.0f);
    EXPECT_FLOAT_EQ(cc.y, 0.0f);
    EXPECT_FLOAT_EQ(cc.width, 0.0f);
    EXPECT_FLOAT_EQ(cc.height, 0.0f);
}

// Full nested tree simulation: verify parent chain across 3 levels.
TEST(IntegrationTest, ThreeLevelNestingParentChainPreserved) {
    WindowsMessageListener listener;

    std::vector<ComponentsAddMessage> msgs;
    // Level 0: root Column (parent = surface)
    msgs.push_back(makeAdd(
        R"({"id":"L0","component":"Column","styles":{"x":0,"y":0,"width":400,"height":600}})",
        "surface", "L0"));
    // Level 1: nested Column (parent = L0)
    msgs.push_back(makeAdd(
        R"({"id":"L1","component":"Column","styles":{"x":10,"y":10,"width":380,"height":580}})",
        "L0", "L1"));
    // Level 2: leaf Text (parent = L1)
    msgs.push_back(makeAdd(
        R"({"id":"L2","component":"Text","text":"Deep","styles":{"x":10,"y":10,"width":360,"height":30}})",
        "L1", "L2"));

    listener.onComponentsAdd("main", msgs);

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 3u);

    const auto* l0 = findById(comps, "L0");
    ASSERT_NE(l0, nullptr);
    EXPECT_EQ(l0->parentId, "surface");
    EXPECT_EQ(l0->type, "Column");

    const auto* l1 = findById(comps, "L1");
    ASSERT_NE(l1, nullptr);
    EXPECT_EQ(l1->parentId, "L0");
    EXPECT_EQ(l1->type, "Column");

    const auto* l2 = findById(comps, "L2");
    ASSERT_NE(l2, nullptr);
    EXPECT_EQ(l2->parentId, "L1");
    EXPECT_EQ(l2->type, "Text");
    EXPECT_EQ(l2->text, "Deep");
}
