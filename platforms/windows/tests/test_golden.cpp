// AGenUI Windows P2 — Golden file tests using ApprovalTests.cpp
//
// These tests use ApprovalTests.cpp to lock in the serialised output of:
//   1. CapturedComponent trees (onComponentsAdd callback)
//   2. ParseHexColor results for 10 input strings
//   3. parsePixelValue results for 8 input strings
//   4. A 10-component A2UI JSON tree processed through the listener
//
// Approval workflow:
//   - First run: generates *.received.txt next to the test exe (test fails).
//   - Manually copy *.received.txt -> *.approved.txt to accept the output.
//   - Subsequent runs compare received vs approved and pass if identical.
//
// The gtest listener from ApprovalTests is wired up via a custom main() which
// calls initializeApprovalTestsForGoogleTests() after InitGoogleTest. The
// GTest::gtest_main link dependency is therefore NOT used for this target.

#define APPROVALS_GOOGLETEST_EXISTING_MAIN
#define APPROVALS_GOOGLETEST
#include "ApprovalTests.hpp"
#include <gtest/gtest.h>

#include "win_message_listener.h"
#include "win_utils.h"

#include <nlohmann/json.hpp>
#include <sstream>
#include <string>
#include <vector>

using agenui_win::CapturedComponent;
using agenui_win::WindowsMessageListener;
using agenui_win::parsePixelValue;
using agenui_win::parseFloatValue;
using agenui_win::ParseHexColor;
using agenui::ComponentsAddMessage;
using ApprovalTests::Approvals;

// ---------------------------------------------------------------------------
// Local main() — wires up the ApprovalTests GoogleTest listener.
// GTest::gtest_main is intentionally not linked for this binary so that we
// can call initializeApprovalTestsForGoogleTests() between InitGoogleTest and
// RUN_ALL_TESTS. This single main() serves the whole test executable.
// ---------------------------------------------------------------------------
int main(int argc, char** argv)
{
    ::testing::InitGoogleTest(&argc, argv);
    ApprovalTests::initializeApprovalTestsForGoogleTests();
    // Place approved/received files under an "Approvals" subdirectory next to
    // the test source file so they are version-controlled alongside the code.
    ApprovalTests::ApprovalTestNamer::testConfiguration().subdirectory =
        "Approvals";
    return RUN_ALL_TESTS();
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

// Build a single ComponentsAddMessage from a JSON component string.
static ComponentsAddMessage makeAdd(const std::string& componentJson,
                                    const std::string& parentId = "root",
                                    const std::string& componentId = "cmp")
{
    ComponentsAddMessage m;
    m.parentId = parentId;
    m.componentId = componentId;
    m.component = componentJson;
    return m;
}

// Build a one-element vector of ComponentsAddMessage for convenience.
static std::vector<ComponentsAddMessage>
makeAddOne(const std::string& componentJson,
           const std::string& parentId = "root",
           const std::string& componentId = "cmp")
{
    return {makeAdd(componentJson, parentId, componentId)};
}

// Serialise a CapturedComponent to the golden format:
//   id|type|text|fontSize|bgColor|src|x|y|width|height
// Numbers are emitted with a fixed precision so the golden file is stable.
static void serializeCapturedComponent(const CapturedComponent& cc,
                                       std::ostream& os)
{
    // std::ostringstream for numeric formatting keeps floats deterministic.
    auto fmt = [](float v) -> std::string {
        std::ostringstream ss;
        ss.imbue(std::locale::classic());
        ss.setf(std::ios::fixed, std::ios::floatfield);
        ss.precision(1);
        ss << v;
        return ss.str();
    };
    os << cc.id << "|" << cc.type << "|" << cc.text << "|"
       << fmt(cc.fontSize) << "|" << cc.bgColor << "|" << cc.src << "|"
       << fmt(cc.x) << "|" << fmt(cc.y) << "|"
       << fmt(cc.width) << "|" << fmt(cc.height);
}

// ---------------------------------------------------------------------------
// 1. CapturedComponent serialisation golden
//    Column root + 3 Text + Button + Image, fed via onComponentsAdd.
// ---------------------------------------------------------------------------
TEST(GoldenTest, CapturedComponentSerialisation)
{
    WindowsMessageListener listener;

    std::vector<ComponentsAddMessage> msgs;
    msgs.push_back(makeAdd(
        R"({"id":"root","component":"Column","styles":{"x":0,"y":0,"width":400,"height":600}})",
        "", "root"));
    msgs.push_back(makeAdd(
        R"({"id":"title","component":"Text","text":"Hello AGenUI!","styles":{"font-size":"48px","text-align":"center","color":"#000000","x":0,"y":0,"width":400,"height":60}})",
        "root", "title"));
    msgs.push_back(makeAdd(
        R"({"id":"subtitle","component":"Text","text":"Phase 2 Golden","styles":{"font-size":"24px","color":"#000000E6","x":0,"y":80,"width":400,"height":30}})",
        "root", "subtitle"));
    msgs.push_back(makeAdd(
        R"({"id":"body","component":"Text","text":"Body text line","styles":{"font-size":"16px","text-align":"left","color":"#333333","x":10,"y":130,"width":380,"height":24}})",
        "root", "body"));
    msgs.push_back(makeAdd(
        R"({"id":"actionBtn","component":"Button","text":"Click Me","styles":{"background-color":"#007DFF","border-radius":"8px","border-width":"0px","padding":"12px","font-size":"16px","x":50,"y":200,"width":200,"height":44}})",
        "root", "actionBtn"));
    msgs.push_back(makeAdd(
        R"({"id":"heroImage","component":"Image","src":"https://example.com/hero.png","styles":{"width":"200px","height":"120px","x":0,"y":300}})",
        "root", "heroImage"));

    listener.onComponentsAdd("main", msgs);

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 6u);

    auto serializer = [](const CapturedComponent& cc, std::ostream& os) {
        serializeCapturedComponent(cc, os);
    };

    // verifyAll writes one serialised entry per line.
    Approvals::verifyAll("CapturedComponent tree (Column + 3 Text + Button + Image)",
                         comps, serializer);
}

// ---------------------------------------------------------------------------
// 2. Hex colour parsing golden
//    10 inputs serialised as (r,g,b,a) with 4-decimal precision.
// ---------------------------------------------------------------------------
TEST(GoldenTest, HexColorParsing)
{
    struct HexCase { std::string input; };
    const std::vector<HexCase> cases = {
        {"#000000"},      // black, no alpha
        {"#FFFFFF"},      // white
        {"#007DFF"},      // AGenUI blue
        {"#000000E6"},    // black with alpha ~0.9
        {"#FF0000FF"},    // red, full alpha
        {"#FF000000"},    // red, zero alpha
        {"#00FF00"},      // pure green
        {"#333333"},      // dark grey
        {"#FFFFFFCC"},    // white ~80% alpha
        {"007DFF"},       // missing hash -> returns default
    };

    D2D1_COLOR_F defaultColor = D2D1::ColorF(0.5f, 0.5f, 0.5f, 1.0f);

    auto serializer = [&](const HexCase& c, std::ostream& os) {
        D2D1_COLOR_F col = ParseHexColor(c.input, defaultColor);
        // Fixed precision makes the golden stable across toolchains.
        auto fmt = [](float v) -> std::string {
            std::ostringstream ss;
            ss.imbue(std::locale::classic());
            ss.setf(std::ios::fixed, std::ios::floatfield);
            ss.precision(4);
            ss << v;
            return ss.str();
        };
        os << c.input << " -> (" << fmt(col.r) << "," << fmt(col.g) << ","
           << fmt(col.b) << "," << fmt(col.a) << ")";
    };

    Approvals::verifyAll("ParseHexColor results (r,g,b,a)", cases, serializer);
}

// ---------------------------------------------------------------------------
// 3. Pixel value parsing golden
//    8 inputs serialised as "input -> output".
// ---------------------------------------------------------------------------
TEST(GoldenTest, PixelValueParsing)
{
    struct PxCase { std::string input; float defaultValue; };
    const std::vector<PxCase> cases = {
        {"48px",     0.0f},
        {"32",       0.0f},
        {"",        24.0f},  // empty -> default
        {"abc",     24.0f},  // non-digit -> default
        {"12.5px",   0.0f},
        {"16abc",    0.0f},  // stops at non-digit
        {"100.0px",  0.0f},
        {"0px",      0.0f},
    };

    auto serializer = [](const PxCase& c, std::ostream& os) {
        float result = parsePixelValue(c.input, c.defaultValue);
        std::ostringstream ss;
        ss.imbue(std::locale::classic());
        ss.setf(std::ios::fixed, std::ios::floatfield);
        ss.precision(1);
        ss << c.input << " -> " << result;
        os << ss.str();
    };

    Approvals::verifyAll("parsePixelValue results", cases, serializer);
}

// ---------------------------------------------------------------------------
// 4. Multi-component JSON golden (10 components through the listener)
//    Locks in the full parse + capture behaviour to detect regressions.
// ---------------------------------------------------------------------------
TEST(GoldenTest, MultiComponentTree)
{
    WindowsMessageListener listener;

    std::vector<ComponentsAddMessage> msgs;
    msgs.push_back(makeAdd(
        R"({"id":"root","component":"Column","styles":{"x":0,"y":0,"width":320,"height":640}})",
        "", "root"));
    msgs.push_back(makeAdd(
        R"({"id":"h1","component":"Text","text":"Title","styles":{"font-size":"32px","color":"#000000","x":0,"y":0,"width":320,"height":40}})",
        "root", "h1"));
    msgs.push_back(makeAdd(
        R"({"id":"h2","component":"Text","text":"Subtitle","styles":{"font-size":"20px","color":"#666666","x":0,"y":50,"width":320,"height":24}})",
        "root", "h2"));
    msgs.push_back(makeAdd(
        R"({"id":"p1","component":"Text","text":"Paragraph one","styles":{"font-size":"14px","text-align":"left","color":"#333333","x":10,"y":90,"width":300,"height":20}})",
        "root", "p1"));
    msgs.push_back(makeAdd(
        R"({"id":"p2","component":"Text","text":"Paragraph two","styles":{"font-size":"14px","color":"#333333","x":10,"y":120,"width":300,"height":20}})",
        "root", "p2"));
    msgs.push_back(makeAdd(
        R"({"id":"btnPrimary","component":"Button","text":"Confirm","styles":{"background-color":"#007DFF","border-radius":"8px","font-size":"16px","x":20,"y":160,"width":140,"height":40}})",
        "root", "btnPrimary"));
    msgs.push_back(makeAdd(
        R"({"id":"btnSecondary","component":"Button","text":"Cancel","styles":{"background-color":"#FFFFFF","border-color":"#007DFF","border-radius":"8px","border-width":"1px","font-size":"16px","x":170,"y":160,"width":130,"height":40}})",
        "root", "btnSecondary"));
    msgs.push_back(makeAdd(
        R"({"id":"avatar","component":"Image","src":"https://example.com/avatar.png","styles":{"width":"64px","height":"64px","x":10,"y":220}})",
        "root", "avatar"));
    msgs.push_back(makeAdd(
        R"({"id":"banner","component":"Image","src":"/local/banner.png","styles":{"width":"300px","height":"80px","x":10,"y":300}})",
        "root", "banner"));
    msgs.push_back(makeAdd(
        R"({"id":"footer","component":"Text","text":"v1.0.0","styles":{"font-size":"10px","color":"#999999","x":10,"y":620,"width":300,"height":14}})",
        "root", "footer"));

    listener.onComponentsAdd("main", msgs);

    auto comps = listener.getCapturedComponents();
    ASSERT_EQ(comps.size(), 10u);

    auto serializer = [](const CapturedComponent& cc, std::ostream& os) {
        serializeCapturedComponent(cc, os);
    };

    Approvals::verifyAll("10-component tree (Column + Text + Button + Image)",
                         comps, serializer);
}
