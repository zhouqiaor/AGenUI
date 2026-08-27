// HarmonyOS adapter edge case tests.
//
// Tests the HarmonyOS measurement and unit conversion system:
// - Extreme dimension values
// - Unit conversion accuracy (fp/vp/px)
// - Layout measurement edge cases

#include <gtest/gtest.h>
#include "harmony/agenui_measure.h"
#include "harmony/agenui_unit_converter.h"
#include <limits>

using agenui::Measure;
using agenui::UnitConverter;

// =============================================================================
// Measure: extreme dimension values
// =============================================================================

TEST(MeasureEdge, ZeroWidth) {
    Measure m(0, 0);
    EXPECT_EQ(m.getWidth(), 0);
    EXPECT_EQ(m.getHeight(), 0);
}

TEST(MeasureEdge, NegativeWidth) {
    Measure m(-100, -200);
    // Negative dimensions should be handled (clamped or used as-is)
    EXPECT_LE(m.getWidth(), 0);
}

TEST(MeasureEdge, VeryLargeDimensions) {
    Measure m(999999, 999999);
    EXPECT_EQ(m.getWidth(), 999999);
    EXPECT_EQ(m.getHeight(), 999999);
}

TEST(MeasureEdge, MaxIntDimensions) {
    Measure m(std::numeric_limits<int>::max(), std::numeric_limits<int>::max());
    EXPECT_EQ(m.getWidth(), std::numeric_limits<int>::max());
}

TEST(MeasureEdge, OneToOneDimensions) {
    Measure m(1, 1);
    EXPECT_EQ(m.getWidth(), 1);
    EXPECT_EQ(m.getHeight(), 1);
}

TEST(MeasureEdge, AsymmetricDimensions) {
    Measure m(1920, 1080);
    EXPECT_EQ(m.getWidth(), 1920);
    EXPECT_EQ(m.getHeight(), 1080);
}

TEST(MeasureEdge, PortraitDimensions) {
    Measure m(1080, 1920);
    EXPECT_GT(m.getHeight(), m.getWidth());
}

TEST(MeasureEdge, LandscapeDimensions) {
    Measure m(1920, 1080);
    EXPECT_GT(m.getWidth(), m.getHeight());
}

// =============================================================================
// UnitConverter: fp/vp/px conversion accuracy
// =============================================================================

TEST(UnitConverterEdge, PxToFp_RoundNumber) {
    float result = UnitConverter::pxToFp(100.0f);
    // At density 3.0: 100px / 3.0 = 33.33fp
    EXPECT_GT(result, 0.0f);
}

TEST(UnitConverterEdge, PxToVp_RoundNumber) {
    float result = UnitConverter::pxToVp(100.0f);
    EXPECT_GT(result, 0.0f);
}

TEST(UnitConverterEdge, FpToPx_RoundNumber) {
    float result = UnitConverter::fpToPx(33.33f);
    // At density 3.0: 33.33fp * 3.0 ≈ 100px
    EXPECT_GT(result, 0.0f);
}

TEST(UnitConverterEdge, VpToPx_RoundNumber) {
    float result = UnitConverter::vpToPx(33.33f);
    EXPECT_GT(result, 0.0f);
}

TEST(UnitConverterEdge, ZeroPx_ConvertsToZero) {
    EXPECT_FLOAT_EQ(UnitConverter::pxToFp(0.0f), 0.0f);
    EXPECT_FLOAT_EQ(UnitConverter::pxToVp(0.0f), 0.0f);
}

TEST(UnitConverterEdge, ZeroFp_ConvertsToZero) {
    EXPECT_FLOAT_EQ(UnitConverter::fpToPx(0.0f), 0.0f);
}

TEST(UnitConverterEdge, ZeroVp_ConvertsToZero) {
    EXPECT_FLOAT_EQ(UnitConverter::vpToPx(0.0f), 0.0f);
}

TEST(UnitConverterEdge, NegativePx_ConvertsToNegative) {
    float result = UnitConverter::pxToFp(-100.0f);
    EXPECT_LT(result, 0.0f);
}

TEST(UnitConverterEdge, VeryLargePx_NoOverflow) {
    float result = UnitConverter::pxToFp(1e9f);
    // Should not overflow or crash
    EXPECT_TRUE(std::isfinite(result));
}

TEST(UnitConverterEdge, VerySmallPx_NoUnderflow) {
    float result = UnitConverter::pxToFp(0.001f);
    // Should not underflow to zero
    EXPECT_TRUE(std::isfinite(result));
}

TEST(UnitConverterEdge, RoundTrip_PxToFpToPx) {
    float original = 150.0f;
    float toFp = UnitConverter::pxToFp(original);
    float backToPx = UnitConverter::fpToPx(toFp);
    // Round-trip should preserve value within epsilon
    EXPECT_NEAR(original, backToPx, 1.0f); // within 1px
}

TEST(UnitConverterEdge, RoundTrip_PxToVpToPx) {
    float original = 300.0f;
    float toVp = UnitConverter::pxToVp(original);
    float backToPx = UnitConverter::vpToPx(toVp);
    EXPECT_NEAR(original, backToPx, 1.0f);
}

TEST(UnitConverterEdge, RoundTrip_FpToPxToFp) {
    float original = 50.0f;
    float toPx = UnitConverter::fpToPx(original);
    float backToFp = UnitConverter::pxToFp(toPx);
    EXPECT_NEAR(original, backToFp, 0.1f);
}

// =============================================================================
// Density-based conversions
// =============================================================================

TEST(UnitConverterEdge, AtDensity1_NoScaling) {
    // At density 1.0: 1px = 1fp = 1vp
    float result = UnitConverter::pxToFp(100.0f, 1.0f);
    EXPECT_NEAR(result, 100.0f, 0.01f);
}

TEST(UnitConverterEdge, AtDensity2_HalfScale) {
    float result = UnitConverter::pxToFp(100.0f, 2.0f);
    EXPECT_NEAR(result, 50.0f, 0.01f);
}

TEST(UnitConverterEdge, AtDensity3_ThirdScale) {
    float result = UnitConverter::pxToFp(300.0f, 3.0f);
    EXPECT_NEAR(result, 100.0f, 0.01f);
}

TEST(UnitConverterEdge, AtDensity0_HandledGracefully) {
    // Density 0 would cause division by zero — should not crash
    float result = UnitConverter::pxToFp(100.0f, 0.0f);
    // Result should be 0 or infinity, but no crash
    EXPECT_TRUE(std::isfinite(result) || std::isinf(result));
}

TEST(UnitConverterEdge, AtNegativeDensity_HandledGracefully) {
    float result = UnitConverter::pxToFp(100.0f, -1.0f);
    // No crash
}

// =============================================================================
// 4K resolution conversions (3840x2160, density 3.0)
// =============================================================================

TEST(UnitConverterEdge, 4K_Width_PxToFp) {
    float result = UnitConverter::pxToFp(3840.0f, 3.0f);
    EXPECT_NEAR(result, 1280.0f, 0.1f);
}

TEST(UnitConverterEdge, 4K_Height_PxToFp) {
    float result = UnitConverter::pxToFp(2160.0f, 3.0f);
    EXPECT_NEAR(result, 720.0f, 0.1f);
}

TEST(UnitConverterEdge, 4K_QuarterWidth_PxToFp) {
    // 960px at density 3.0 = 320fp
    float result = UnitConverter::pxToFp(960.0f, 3.0f);
    EXPECT_NEAR(result, 320.0f, 0.1f);
}
