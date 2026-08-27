#include <gtest/gtest.h>
#include "style_parser/agenui_edge_insets_parser.h"
#include <string>

using agenui::EdgeInsetsParser;
using agenui::EdgeInsets;
using agenui::EdgeInsetValue;
using agenui::EdgeInsetUnit;

// ============================================================================
// Two-value shorthand: vertical horizontal
// ============================================================================

TEST(EdgeInsetsTwoValue, TwoValues_Px) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("10px 20px", r));
    EXPECT_FLOAT_EQ(r.top.value, 10.0f);
    EXPECT_FLOAT_EQ(r.right.value, 20.0f);
    EXPECT_FLOAT_EQ(r.bottom.value, 10.0f);
    EXPECT_FLOAT_EQ(r.left.value, 20.0f);
}

TEST(EdgeInsetsTwoValue, TwoValues_MixedUnit) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("10px 50%", r));
    EXPECT_EQ(r.top.unit, EdgeInsetUnit::Px);
    EXPECT_EQ(r.right.unit, EdgeInsetUnit::Percent);
    EXPECT_EQ(r.bottom.unit, EdgeInsetUnit::Px);
    EXPECT_EQ(r.left.unit, EdgeInsetUnit::Percent);
}

TEST(EdgeInsetsTwoValue, TwoValues_UnitlessNumber) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("5 15", r));
    EXPECT_FLOAT_EQ(r.top.value, 5.0f);
    EXPECT_FLOAT_EQ(r.right.value, 15.0f);
}

// ============================================================================
// Three-value shorthand: top horizontal bottom
// ============================================================================

TEST(EdgeInsetsThreeValue, ThreeValues_Px) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("1px 2px 3px", r));
    EXPECT_FLOAT_EQ(r.top.value, 1.0f);
    EXPECT_FLOAT_EQ(r.right.value, 2.0f);
    EXPECT_FLOAT_EQ(r.bottom.value, 3.0f);
    EXPECT_FLOAT_EQ(r.left.value, 2.0f);
}

TEST(EdgeInsetsThreeValue, ThreeValues_MixedAuto) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("10px auto 20px", r));
    EXPECT_FLOAT_EQ(r.top.value, 10.0f);
    EXPECT_EQ(r.right.unit, EdgeInsetUnit::Auto);
    EXPECT_FLOAT_EQ(r.bottom.value, 20.0f);
    EXPECT_EQ(r.left.unit, EdgeInsetUnit::Auto);
}

// ============================================================================
// Four-value shorthand: top right bottom left (CSS order)
// ============================================================================

TEST(EdgeInsetsFourValue, FourValues_AllPx) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("1px 2px 3px 4px", r));
    EXPECT_FLOAT_EQ(r.top.value, 1.0f);
    EXPECT_FLOAT_EQ(r.right.value, 2.0f);
    EXPECT_FLOAT_EQ(r.bottom.value, 3.0f);
    EXPECT_FLOAT_EQ(r.left.value, 4.0f);
}

TEST(EdgeInsetsFourValue, FourValues_MixedUnits) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("10px 20% auto 5px", r));
    EXPECT_EQ(r.top.unit, EdgeInsetUnit::Px);
    EXPECT_EQ(r.right.unit, EdgeInsetUnit::Percent);
    EXPECT_EQ(r.bottom.unit, EdgeInsetUnit::Auto);
    EXPECT_EQ(r.left.unit, EdgeInsetUnit::Px);
}

TEST(EdgeInsetsFourValue, FourValues_Negative) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("-5px -10px -15px -20px", r));
    EXPECT_FLOAT_EQ(r.top.value, -5.0f);
    EXPECT_FLOAT_EQ(r.right.value, -10.0f);
    EXPECT_FLOAT_EQ(r.bottom.value, -15.0f);
    EXPECT_FLOAT_EQ(r.left.value, -20.0f);
}

// ============================================================================
// Edge cases: whitespace, zero, extreme values
// ============================================================================

TEST(EdgeInsetsEdge, ExtraWhitespace_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("  10px   20px   ", r));
    EXPECT_FLOAT_EQ(r.top.value, 10.0f);
    EXPECT_FLOAT_EQ(r.right.value, 20.0f);
}

TEST(EdgeInsetsEdge, Zero_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("0", r));
    EXPECT_FLOAT_EQ(r.top.value, 0.0f);
}

TEST(EdgeInsetsEdge, ZeroPx_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("0px 0px 0px 0px", r));
    EXPECT_FLOAT_EQ(r.top.value, 0.0f);
    EXPECT_FLOAT_EQ(r.right.value, 0.0f);
    EXPECT_FLOAT_EQ(r.bottom.value, 0.0f);
    EXPECT_FLOAT_EQ(r.left.value, 0.0f);
}

TEST(EdgeInsetsEdge, VeryLargeValue_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("999999px", r));
    EXPECT_FLOAT_EQ(r.top.value, 999999.0f);
}

TEST(EdgeInsetsEdge, VerySmallValue_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("0.001px", r));
    EXPECT_FLOAT_EQ(r.top.value, 0.001f);
}

TEST(EdgeInsetsEdge, EmptyString_ReturnsError) {
    EdgeInsets r;
    EXPECT_FALSE(EdgeInsetsParser::parse("", r));
}

TEST(EdgeInsetsEdge, WhitespaceOnly_ReturnsError) {
    EdgeInsets r;
    EXPECT_FALSE(EdgeInsetsParser::parse("   ", r));
}

TEST(EdgeInsetsEdge, InvalidUnit_ReturnsError) {
    EdgeInsets r;
    EXPECT_FALSE(EdgeInsetsParser::parse("10pt", r));
}

TEST(EdgeInsetsEdge, FiveValues_ReturnsError) {
    EdgeInsets r;
    EXPECT_FALSE(EdgeInsetsParser::parse("1px 2px 3px 4px 5px", r));
}

TEST(EdgeInsetsEdge, AllAuto_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("auto", r));
    EXPECT_EQ(r.top.unit, EdgeInsetUnit::Auto);
    EXPECT_EQ(r.right.unit, EdgeInsetUnit::Auto);
    EXPECT_EQ(r.bottom.unit, EdgeInsetUnit::Auto);
    EXPECT_EQ(r.left.unit, EdgeInsetUnit::Auto);
}

TEST(EdgeInsetsEdge, CalcExpression_ParsesOrHandled) {
    EdgeInsets r;
    // calc() should either parse or be rejected gracefully
    bool result = EdgeInsetsParser::parse("calc(10px + 5px)", r);
    if (result) {
        EXPECT_TRUE(r.top.isCalc);
    }
    // No crash either way
}

TEST(EdgeInsetsEdge, Percentage0_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("0%", r));
    EXPECT_EQ(r.top.unit, EdgeInsetUnit::Percent);
    EXPECT_FLOAT_EQ(r.top.value, 0.0f);
}

TEST(EdgeInsetsEdge, Percentage100_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("100%", r));
    EXPECT_EQ(r.top.unit, EdgeInsetUnit::Percent);
    EXPECT_FLOAT_EQ(r.top.value, 100.0f);
}

TEST(EdgeInsetsEdge, PercentageOver100_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("150%", r));
    EXPECT_EQ(r.top.unit, EdgeInsetUnit::Percent);
    EXPECT_FLOAT_EQ(r.top.value, 150.0f);
}

TEST(EdgeInsetsEdge, NegativePercentage_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("-50%", r));
    EXPECT_EQ(r.top.unit, EdgeInsetUnit::Percent);
    EXPECT_FLOAT_EQ(r.top.value, -50.0f);
}

TEST(EdgeInsetsEdge, TabSeparated_Parses) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("10px\t20px", r));
    EXPECT_FLOAT_EQ(r.top.value, 10.0f);
    EXPECT_FLOAT_EQ(r.right.value, 20.0f);
}

TEST(EdgeInsetsEdge, MixedPercentageAndPx_FourValues) {
    EdgeInsets r;
    ASSERT_TRUE(EdgeInsetsParser::parse("10% 20px 30% 40px", r));
    EXPECT_EQ(r.top.unit, EdgeInsetUnit::Percent);
    EXPECT_EQ(r.right.unit, EdgeInsetUnit::Px);
    EXPECT_EQ(r.bottom.unit, EdgeInsetUnit::Percent);
    EXPECT_EQ(r.left.unit, EdgeInsetUnit::Px);
}
