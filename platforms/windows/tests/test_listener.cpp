// AGenUI Windows P2 — Component parse tests for WindowsMessageListener
// Sends onComponentsAdd with A2UI component JSON, verifies captured fields.

#include "win_message_listener.h"

#include <gtest/gtest.h>
#include <nlohmann/json.hpp>

using agenui_win::CapturedComponent;
using agenui_win::WindowsMessageListener;
using agenui::ComponentsAddMessage;

// Helper: build a single ComponentsAddMessage vector from a JSON component string.
static std::vector<ComponentsAddMessage> makeAdd(const std::string& componentJson,
                                                 const std::string& parentId = "root") {
    ComponentsAddMessage msg;
    msg.parentId = parentId;
    msg.componentId = "test";
    msg.component = componentJson;
    return {msg};
}

// Helper: find a captured component by id.
static const CapturedComponent* findById(const std::vector<CapturedComponent>& comps,
                                          const std::string& id) {
    for (const auto& c : comps) {
        if (c.id == id) return &c;
    }
    return nullptr;
}

// ---------------------------------------------------------------------------
// Text component
// ---------------------------------------------------------------------------
TEST(ListenerTextTest, CapturesTextFields) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"title","component":"Text","text":"Hello AGenUI!","styles":{"font-size":"48px","text-align":"center","color":"#000000","x":0,"y":0,"width":400,"height":60}})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_EQ(cc.id, "title");
    EXPECT_EQ(cc.type, "Text");
    EXPECT_EQ(cc.text, "Hello AGenUI!");
    EXPECT_FLOAT_EQ(cc.fontSize, 48.0f);
    EXPECT_EQ(cc.textAlign, "center");
    EXPECT_EQ(cc.textColor, "#000000");
    EXPECT_FLOAT_EQ(cc.x, 0.0f);
    EXPECT_FLOAT_EQ(cc.y, 0.0f);
    EXPECT_FLOAT_EQ(cc.width, 400.0f);
    EXPECT_FLOAT_EQ(cc.height, 60.0f);
    EXPECT_TRUE(cc.hasStyles);
}

TEST(ListenerTextTest, CapturesTextWithAlphaColor) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"subtitle","component":"Text","text":"Phase 2","styles":{"font-size":"24px","color":"#000000E6","x":10,"y":20}})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_EQ(cc.textColor, "#000000E6");
    EXPECT_FLOAT_EQ(cc.fontSize, 24.0f);
    EXPECT_FLOAT_EQ(cc.x, 10.0f);
    EXPECT_FLOAT_EQ(cc.y, 20.0f);
}

TEST(ListenerTextTest, TextWithNoStyles) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"plain","component":"Text","text":"No styles"})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_EQ(cc.text, "No styles");
    EXPECT_FALSE(cc.hasStyles);
    // Default font size
    EXPECT_FLOAT_EQ(cc.fontSize, 24.0f);
}

// ---------------------------------------------------------------------------
// Button component
// ---------------------------------------------------------------------------
TEST(ListenerButtonTest, CapturesButtonFields) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"actionBtn","component":"Button","text":"Click Me","styles":{"background-color":"#007DFF","border-radius":"8px","border-width":"0px","padding":"12px","font-size":"16px","x":50,"y":100,"width":200,"height":44}})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_EQ(cc.id, "actionBtn");
    EXPECT_EQ(cc.type, "Button");
    EXPECT_EQ(cc.text, "Click Me");
    EXPECT_EQ(cc.bgColor, "#007DFF");
    EXPECT_FLOAT_EQ(cc.borderRadius, 8.0f);
    EXPECT_FLOAT_EQ(cc.borderWidth, 0.0f);
    EXPECT_FLOAT_EQ(cc.padding, 12.0f);
    EXPECT_FLOAT_EQ(cc.fontSize, 16.0f);
    EXPECT_FLOAT_EQ(cc.x, 50.0f);
    EXPECT_FLOAT_EQ(cc.y, 100.0f);
    EXPECT_FLOAT_EQ(cc.width, 200.0f);
    EXPECT_FLOAT_EQ(cc.height, 44.0f);
}

TEST(ListenerButtonTest, ButtonWithBorderColor) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"btn2","component":"Button","text":"Submit","styles":{"background-color":"#FFFFFF","border-color":"#007DFF","border-radius":"4px","border-width":"2px"}})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_EQ(cc.bgColor, "#FFFFFF");
    EXPECT_EQ(cc.borderColor, "#007DFF");
    EXPECT_FLOAT_EQ(cc.borderRadius, 4.0f);
    EXPECT_FLOAT_EQ(cc.borderWidth, 2.0f);
}

TEST(ListenerButtonTest, ButtonNoStyles) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"plainBtn","component":"Button","text":"OK"})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_EQ(cc.text, "OK");
    EXPECT_FALSE(cc.hasStyles);
    EXPECT_FLOAT_EQ(cc.borderRadius, 0.0f);
    EXPECT_FLOAT_EQ(cc.borderWidth, 0.0f);
}

// ---------------------------------------------------------------------------
// Image component
// ---------------------------------------------------------------------------
TEST(ListenerImageTest, CapturesImageFields) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"heroImage","component":"Image","src":"https://example.com/hero.png","styles":{"width":"200px","height":"120px","x":0,"y":200}})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_EQ(cc.id, "heroImage");
    EXPECT_EQ(cc.type, "Image");
    EXPECT_EQ(cc.src, "https://example.com/hero.png");
    EXPECT_FLOAT_EQ(cc.width, 200.0f);
    EXPECT_FLOAT_EQ(cc.height, 120.0f);
    EXPECT_FLOAT_EQ(cc.x, 0.0f);
    EXPECT_FLOAT_EQ(cc.y, 200.0f);
}

TEST(ListenerImageTest, ImageNumericDimensions) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"img2","component":"Image","src":"/local/path.png","styles":{"width":150,"height":90}})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_FLOAT_EQ(cc.width, 150.0f);
    EXPECT_FLOAT_EQ(cc.height, 90.0f);
}

TEST(ListenerImageTest, ImageNoSrc) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"imgEmpty","component":"Image"})"
    ));

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    const auto& cc = comps[0];
    EXPECT_TRUE(cc.src.empty());
}

// ---------------------------------------------------------------------------
// Multiple components
// ---------------------------------------------------------------------------
TEST(ListenerMultiTest, CapturesMultipleComponents) {
    WindowsMessageListener listener;

    std::vector<ComponentsAddMessage> msgs;
    ComponentsAddMessage m1;
    m1.parentId = "root";
    m1.componentId = "title";
    m1.component = R"({"id":"title","component":"Text","text":"Title","styles":{"font-size":"32px"}})";
    msgs.push_back(m1);

    ComponentsAddMessage m2;
    m2.parentId = "root";
    m2.componentId = "btn";
    m2.component = R"({"id":"btn","component":"Button","text":"Go","styles":{"background-color":"#007DFF"}})";
    msgs.push_back(m2);

    ComponentsAddMessage m3;
    m3.parentId = "root";
    m3.componentId = "img";
    m3.component = R"({"id":"img","component":"Image","src":"https://x.com/a.png"})";
    msgs.push_back(m3);

    listener.onComponentsAdd("main", msgs);

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 3u);

    const auto* title = findById(comps, "title");
    ASSERT_NE(title, nullptr);
    EXPECT_EQ(title->type, "Text");
    EXPECT_EQ(title->text, "Title");

    const auto* btn = findById(comps, "btn");
    ASSERT_NE(btn, nullptr);
    EXPECT_EQ(btn->type, "Button");
    EXPECT_EQ(btn->bgColor, "#007DFF");

    const auto* img = findById(comps, "img");
    ASSERT_NE(img, nullptr);
    EXPECT_EQ(img->type, "Image");
    EXPECT_EQ(img->src, "https://x.com/a.png");
}

TEST(ListenerMultiTest, UpdateReplacesComponents) {
    WindowsMessageListener listener;

    // First add a component
    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"old","component":"Text","text":"Old"})"
    ));
    auto comps1 = listener.getCapturedComponents();
    ASSERT_EQ(comps1.size(), 1u);
    EXPECT_EQ(comps1[0].id, "old");

    // onComponentsUpdate clears and replaces
    agenui::ComponentsUpdateMessage upd;
    upd.componentId = "new";
    upd.component = R"({"id":"new","component":"Text","text":"New"})";

    listener.onComponentsUpdate("main", {upd});
    auto comps2 = listener.getCapturedComponents();
    ASSERT_EQ(comps2.size(), 1u);
    EXPECT_EQ(comps2[0].id, "new");
}

// ---------------------------------------------------------------------------
// Error handling
// ---------------------------------------------------------------------------
TEST(ListenerErrorTest, InvalidJsonDoesNotCrash) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd("not valid json"));
    EXPECT_TRUE(listener.getCapturedComponents().empty());
}

TEST(ListenerErrorTest, EmptyJsonDoesNotCrash) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(""));
    EXPECT_TRUE(listener.getCapturedComponents().empty());
}

TEST(ListenerErrorTest, PartialJsonDoesNotCrash) {
    WindowsMessageListener listener;
    listener.onComponentsAdd("main", makeAdd(R"({"id":"broken","component":})"));
    EXPECT_TRUE(listener.getCapturedComponents().empty());
}

TEST(ListenerErrorTest, MissingFieldsUsesDefaults) {
    WindowsMessageListener listener;
    // Valid JSON object but missing component/text/styles
    listener.onComponentsAdd("main", makeAdd(R"({"id":"empty","component":"Text"})"));
    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 1u);
    EXPECT_EQ(comps[0].id, "empty");
    EXPECT_EQ(comps[0].type, "Text");
    EXPECT_TRUE(comps[0].text.empty());
    EXPECT_FALSE(comps[0].hasStyles);
}

// ---------------------------------------------------------------------------
// Surface lifecycle
// ---------------------------------------------------------------------------
TEST(ListenerSurfaceTest, CreateSurfaceClearsComponents) {
    WindowsMessageListener listener;

    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"x","component":"Text","text":"X"})"
    ));
    EXPECT_FALSE(listener.getCapturedComponents().empty());

    agenui::CreateSurfaceMessage msg;
    msg.surfaceId = "newSurface";
    listener.onCreateSurface(msg);

    EXPECT_TRUE(listener.getCapturedComponents().empty());
    EXPECT_TRUE(listener.hasSurface());
    EXPECT_EQ(listener.getSurfaceId(), "newSurface");
}

TEST(ListenerSurfaceTest, DeleteSurfaceClearsComponents) {
    WindowsMessageListener listener;

    listener.onComponentsAdd("main", makeAdd(
        R"({"id":"x","component":"Text","text":"X"})"
    ));

    agenui::DeleteSurfaceMessage msg;
    msg.surfaceId = "main";
    listener.onDeleteSurface(msg);

    EXPECT_TRUE(listener.getCapturedComponents().empty());
    EXPECT_FALSE(listener.hasSurface());
}
