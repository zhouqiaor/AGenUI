#include <gtest/gtest.h>
#include "style_parser/agenui_color_parser.h"

using agenui::ColorParser;
using agenui::ColorValue;
using agenui::ColorValueType;

// ============================================================================
// Gradient edge cases — malformed input, extreme values, format variations
// ============================================================================

TEST(GradientParserEdge, LinearEmptyStops_ReturnsError) {
    ColorValue cv;
    EXPECT_FALSE(ColorParser::parse("linear-gradient()", cv));
}

TEST(GradientParserEdge, LinearSingleStop_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("linear-gradient(90deg, #FF0000)", cv));
    EXPECT_EQ(cv.type, ColorValueType::Gradient);
    EXPECT_GE(cv.gradientStops.size(), 1u);
}

TEST(GradientParserEdge, LinearGradientWithoutAngle_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("linear-gradient(to right, #FF0000, #0000FF)", cv));
    EXPECT_EQ(cv.type, ColorValueType::Gradient);
}

TEST(GradientParserEdge, LinearGradientWithDirection_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("linear-gradient(to bottom right, #FF0000, #0000FF)", cv));
    EXPECT_EQ(cv.type, ColorValueType::Gradient);
}

TEST(GradientParserEdge, RadialGradient_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("radial-gradient(circle, #FF0000, #0000FF)", cv));
    EXPECT_EQ(cv.type, ColorValueType::Gradient);
}

TEST(GradientParserEdge, GradientWithRgbaStops_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("linear-gradient(45deg, rgba(255,0,0,1), rgba(0,0,255,0.5))", cv));
    EXPECT_EQ(cv.type, ColorValueType::Gradient);
}

TEST(GradientParserEdge, GradientWithManyStops_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("linear-gradient(90deg, #F00, #0F0, #00F, #FFF, #000)", cv));
    EXPECT_EQ(cv.type, ColorValueType::Gradient);
    EXPECT_GE(cv.gradientStops.size(), 5u);
}

TEST(GradientParserEdge, GradientWithPercentage_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("linear-gradient(90deg, #FF0000 0%, #0000FF 100%)", cv));
    EXPECT_EQ(cv.type, ColorValueType::Gradient);
}

TEST(GradientParserEdge, MalformedGradientKeyword_ReturnsError) {
    ColorValue cv;
    EXPECT_FALSE(ColorParser::parse("gradient(invalid)", cv));
}

TEST(GradientParserEdge, TruncatedGradientInput_ReturnsError) {
    ColorValue cv;
    EXPECT_FALSE(ColorParser::parse("linear-gradient(90deg,", cv));
}

// ============================================================================
// Named color parsing edge cases
// ============================================================================

TEST(NamedColorEdge, Transparent_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("transparent", cv));
    EXPECT_EQ(cv.type, ColorValueType::Solid);
    EXPECT_EQ(cv.solidColor & 0xFF000000, 0x00000000);
}

TEST(NamedColorEdge, CurrentColor_ParsesOrFails) {
    ColorValue cv;
    // currentColor should parse as a keyword (behavior depends on implementation)
    bool result = ColorParser::parse("currentColor", cv);
    if (result) {
        EXPECT_EQ(cv.type, ColorValueType::Solid);
    }
    // Either way, no crash
}

TEST(NamedColorEdge, InvalidColorName_ReturnsError) {
    ColorValue cv;
    EXPECT_FALSE(ColorParser::parse("notacolor", cv));
}

TEST(NamedColorEdge, CaseInsensitiveNamedColor_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("RED", cv));
    EXPECT_EQ(cv.type, ColorValueType::Solid);
}

// ============================================================================
// Hex color edge cases
// ============================================================================

TEST(HexColorEdge, ThreeDigitHex_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("#F00", cv));
    EXPECT_EQ(cv.type, ColorValueType::Solid);
}

TEST(HexColorEdge, SixDigitHex_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("#FF0000", cv));
    EXPECT_EQ(cv.type, ColorValueType::Solid);
}

TEST(HexColorEdge, EightDigitHexWithAlpha_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("#FF000080", cv));
    EXPECT_EQ(cv.type, ColorValueType::Solid);
}

TEST(HexColorEdge, InvalidHexLength_ReturnsError) {
    ColorValue cv;
    EXPECT_FALSE(ColorParser::parse("#FF00", cv));
    EXPECT_FALSE(ColorParser::parse("#FF0000000", cv));
}

TEST(HexColorEdge, HashOnly_ReturnsError) {
    ColorValue cv;
    EXPECT_FALSE(ColorParser::parse("#", cv));
}

TEST(HexColorEdge, EmptyString_ReturnsError) {
    ColorValue cv;
    EXPECT_FALSE(ColorParser::parse("", cv));
}

TEST(HexColorEdge, LowercaseHex_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("#abcdef", cv));
    EXPECT_EQ(cv.type, ColorValueType::Solid);
}

TEST(HexColorEdge, MixedCaseHex_Parses) {
    ColorValue cv;
    ASSERT_TRUE(ColorParser::parse("#aBcDeF", cv));
    EXPECT_EQ(cv.type, ColorValueType::Solid);
}
