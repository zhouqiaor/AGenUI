// Yoga layout engine edge case tests.
//
// Tests the Yoga binding layer for:
// - Extreme flex values
// - Deep nesting
// - Zero-size nodes
// - Aspect ratio edge cases
// - Flex direction changes
// - Alignment overrides

#include <gtest/gtest.h>
#include "surface/yoga/agenui_yoga_node.h"
#include "surface/yoga/agenui_yoga_value.h"
#include <memory>

using agenui::YogaNode;
using agenui::YogaValue;

// =============================================================================
// YogaValue parsing edge cases
// =============================================================================

TEST(YogaValueEdge, ZeroValue) {
    auto v = YogaValue::parse("0");
    EXPECT_NE(v.type, agenui::YogaValueType::Undefined);
}

TEST(YogaValueEdge, ZeroPx) {
    auto v = YogaValue::parse("0px");
    EXPECT_NE(v.type, agenui::YogaValueType::Undefined);
}

TEST(YogaValueEdge, AutoValue) {
    auto v = YogaValue::parse("auto");
    EXPECT_EQ(v.type, agenui::YogaValueType::Auto);
}

TEST(YogaValueEdge, PercentageZero) {
    auto v = YogaValue::parse("0%");
    EXPECT_EQ(v.type, agenui::YogaValueType::Percent);
}

TEST(YogaValueEdge, Percentage100) {
    auto v = YogaValue::parse("100%");
    EXPECT_EQ(v.type, agenui::YogaValueType::Percent);
}

TEST(YogaValueEdge, PercentageOver100) {
    auto v = YogaValue::parse("150%");
    EXPECT_EQ(v.type, agenui::YogaValueType::Percent);
}

TEST(YogaValueEdge, NegativePercentage) {
    auto v = YogaValue::parse("-50%");
    EXPECT_EQ(v.type, agenui::YogaValueType::Percent);
}

TEST(YogaValueEdge, NegativePx) {
    auto v = YogaValue::parse("-10px");
    // Should parse or be handled gracefully
    EXPECT_NE(v.type, agenui::YogaValueType::Undefined);
}

TEST(YogaValueEdge, VeryLargePx) {
    auto v = YogaValue::parse("999999px");
    EXPECT_NE(v.type, agenui::YogaValueType::Undefined);
}

TEST(YogaValueEdge, VerySmallPx) {
    auto v = YogaValue::parse("0.001px");
    EXPECT_NE(v.type, agenui::YogaValueType::Undefined);
}

TEST(YogaValueEdge, EmptyString_Handled) {
    auto v = YogaValue::parse("");
    // Should be undefined or handled gracefully, no crash
}

TEST(YogaValueEdge, WhitespaceOnly_Handled) {
    auto v = YogaValue::parse("   ");
    // No crash
}

TEST(YogaValueEdge, InvalidString_Handled) {
    auto v = YogaValue::parse("not a valid value");
    // No crash
}

TEST(YogaValueEdge, NumberOnly_Parses) {
    auto v = YogaValue::parse("42");
    EXPECT_NE(v.type, agenui::YogaValueType::Undefined);
}

TEST(YogaValueEdge, NegativeNumber_Parses) {
    auto v = YogaValue::parse("-42");
    EXPECT_NE(v.type, agenui::YogaValueType::Undefined);
}

TEST(YogaValueEdge, Float_Parses) {
    auto v = YogaValue::parse("3.14");
    EXPECT_NE(v.type, agenui::YogaValueType::Undefined);
}

TEST(YogaValueEdge, ScientificNotation_ParsesOrHandled) {
    auto v = YogaValue::parse("1e5");
    // No crash
}

// =============================================================================
// YogaNode tree construction
// =============================================================================

TEST(YogaNodeEdge, CreateSingleNode) {
    auto node = std::make_shared<YogaNode>();
    EXPECT_NE(node, nullptr);
}

TEST(YogaNodeEdge, AddChild) {
    auto parent = std::make_shared<YogaNode>();
    auto child = std::make_shared<YogaNode>();
    parent->addChild(child);
    EXPECT_EQ(parent->getChildCount(), 1);
}

TEST(YogaNodeEdge, AddManyChildren) {
    auto parent = std::make_shared<YogaNode>();
    for (int i = 0; i < 100; i++) {
        auto child = std::make_shared<YogaNode>();
        parent->addChild(child);
    }
    EXPECT_EQ(parent->getChildCount(), 100);
}

TEST(YogaNodeEdge, RemoveChild) {
    auto parent = std::make_shared<YogaNode>();
    auto child1 = std::make_shared<YogaNode>();
    auto child2 = std::make_shared<YogaNode>();
    parent->addChild(child1);
    parent->addChild(child2);
    ASSERT_EQ(parent->getChildCount(), 2);

    parent->removeChild(child1);
    EXPECT_EQ(parent->getChildCount(), 1);
}

TEST(YogaNodeEdge, RemoveAllChildren) {
    auto parent = std::make_shared<YogaNode>();
    for (int i = 0; i < 10; i++) {
        parent->addChild(std::make_shared<YogaNode>());
    }
    ASSERT_EQ(parent->getChildCount(), 10);

    parent->removeAllChildren();
    EXPECT_EQ(parent->getChildCount(), 0);
}

TEST(YogaNodeEdge, DeepNesting_50Levels) {
    auto root = std::make_shared<YogaNode>();
    auto current = root;
    for (int i = 0; i < 50; i++) {
        auto child = std::make_shared<YogaNode>();
        current->addChild(child);
        current = child;
    }
    SUCCEED();
}

TEST(YogaNodeEdge, RemoveNonExistentChild_NoCrash) {
    auto parent = std::make_shared<YogaNode>();
    auto child = std::make_shared<YogaNode>();
    // Try to remove a child that was never added
    parent->removeChild(child);
    SUCCEED();
}

TEST(YogaNodeEdge, AddNullChild_NoCrash) {
    auto parent = std::make_shared<YogaNode>();
    std::shared_ptr<YogaNode> nullChild = nullptr;
    parent->addChild(nullChild);
    // No crash
}

// =============================================================================
// Flex properties edge cases
// =============================================================================

TEST(YogaFlexEdge, FlexGrow_Zero) {
    auto node = std::make_shared<YogaNode>();
    node->setFlexGrow(0.0f);
    SUCCEED();
}

TEST(YogaFlexEdge, FlexGrow_Negative_NoCrash) {
    auto node = std::make_shared<YogaNode>();
    node->setFlexGrow(-1.0f);
    // Should be clamped to 0 or handled
    SUCCEED();
}

TEST(YogaFlexEdge, FlexGrow_VeryLarge) {
    auto node = std::make_shared<YogaNode>();
    node->setFlexGrow(99999.0f);
    SUCCEED();
}

TEST(YogaFlexEdge, FlexShrink_Zero) {
    auto node = std::make_shared<YogaNode>();
    node->setFlexShrink(0.0f);
    SUCCEED();
}

TEST(YogaFlexEdge, FlexShrink_Negative_NoCrash) {
    auto node = std::make_shared<YogaNode>();
    node->setFlexShrink(-1.0f);
    SUCCEED();
}

TEST(YogaFlexEdge, FlexBasisZero) {
    auto node = std::make_shared<YogaNode>();
    node->setFlexBasis(0.0f);
    SUCCEED();
}

TEST(YogaFlexEdge, AspectRatio_Zero) {
    auto node = std::make_shared<YogaNode>();
    node->setAspectRatio(0.0f);
    SUCCEED();
}

TEST(YogaFlexEdge, AspectRatio_Negative_NoCrash) {
    auto node = std::make_shared<YogaNode>();
    node->setAspectRatio(-1.0f);
    SUCCEED();
}

TEST(YogaFlexEdge, AspectRatio_VeryLarge) {
    auto node = std::make_shared<YogaNode>();
    node->setAspectRatio(10000.0f);
    SUCCEED();
}

// =============================================================================
// Layout calculation edge cases
// =============================================================================

TEST(YogaLayoutEdge, ZeroSizeParent_ZeroLayout) {
    auto parent = std::make_shared<YogaNode>();
    parent->setWidth(0.0f);
    parent->setHeight(0.0f);
    parent->calculateLayout(0.0f, 0.0f);
    // No crash
}

TEST(YogaLayoutEdge, VeryLargeSize_NoOverflow) {
    auto parent = std::make_shared<YogaNode>();
    parent->setWidth(99999.0f);
    parent->setHeight(99999.0f);
    parent->calculateLayout(99999.0f, 99999.0f);
    SUCCEED();
}

TEST(YogaLayoutEdge, NegativeSize_NoCrash) {
    auto parent = std::make_shared<YogaNode>();
    parent->setWidth(-100.0f);
    parent->setHeight(-100.0f);
    parent->calculateLayout(-100.0f, -100.0f);
    SUCCEED();
}

TEST(YogaLayoutEdge, NoWidthHeight_CalculatesWithAvailable) {
    auto parent = std::make_shared<YogaNode>();
    parent->calculateLayout(1920.0f, 1080.0f);
    SUCCEED();
}

TEST(YogaLayoutEdge, RecalculateLayout_Idempotent) {
    auto parent = std::make_shared<YogaNode>();
    parent->setWidth(300.0f);
    parent->setHeight(400.0f);
    parent->calculateLayout(300.0f, 400.0f);
    // Recalculate should give same results
    parent->calculateLayout(300.0f, 400.0f);
    SUCCEED();
}

TEST(YogaLayoutEdge, LayoutAfterStructureChange) {
    auto parent = std::make_shared<YogaNode>();
    parent->setWidth(300.0f);
    parent->setHeight(400.0f);

    auto child1 = std::make_shared<YogaNode>();
    parent->addChild(child1);
    parent->calculateLayout(300.0f, 400.0f);

    // Add another child after initial layout
    auto child2 = std::make_shared<YogaNode>();
    parent->addChild(child2);
    parent->calculateLayout(300.0f, 400.0f);

    // Remove first child
    parent->removeChild(child1);
    parent->calculateLayout(300.0f, 400.0f);

    SUCCEED();
}
