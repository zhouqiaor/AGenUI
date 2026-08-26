// AGenUI Windows P2 — Unit tests for utility functions
// Tests parsePixelValue, ParseHexColor, ToWide extracted to win_utils.h /
// win_message_listener.h as free functions in the agenui_win namespace.

#include "win_message_listener.h"
#include "win_utils.h"

#include <gtest/gtest.h>
#include <cstring>

using agenui_win::parsePixelValue;
using agenui_win::parseFloatValue;
using agenui_win::ParseHexColor;
using agenui_win::ToWide;

// ---------------------------------------------------------------------------
// parsePixelValue
// ---------------------------------------------------------------------------
TEST(ParsePixelValue, ParsesPxSuffix) {
    EXPECT_FLOAT_EQ(parsePixelValue("48px", 0.0f), 48.0f);
}

TEST(ParsePixelValue, ParsesBareNumber) {
    EXPECT_FLOAT_EQ(parsePixelValue("32", 0.0f), 32.0f);
}

TEST(ParsePixelValue, ReturnsDefaultOnEmpty) {
    EXPECT_FLOAT_EQ(parsePixelValue("", 24.0f), 24.0f);
}

TEST(ParsePixelValue, ReturnsDefaultOnNonDigit) {
    EXPECT_FLOAT_EQ(parsePixelValue("abc", 24.0f), 24.0f);
}

TEST(ParsePixelValue, ParsesDecimal) {
    EXPECT_FLOAT_EQ(parsePixelValue("12.5px", 0.0f), 12.5f);
}

TEST(ParsePixelValue, StopsAtNonDigit) {
    // "16abc" parses as 16 before hitting 'a'
    EXPECT_FLOAT_EQ(parsePixelValue("16abc", 0.0f), 16.0f);
}

// ---------------------------------------------------------------------------
// parseFloatValue (JSON variant)
// ---------------------------------------------------------------------------
TEST(ParseFloatValue, ParsesNumber) {
    nlohmann::json j = 100.5;
    EXPECT_FLOAT_EQ(parseFloatValue(j), 100.5f);
}

TEST(ParseFloatValue, ParsesStringWithPx) {
    nlohmann::json j = "200px";
    EXPECT_FLOAT_EQ(parseFloatValue(j), 200.0f);
}

TEST(ParseFloatValue, ReturnsZeroOnNull) {
    nlohmann::json j = nullptr;
    EXPECT_FLOAT_EQ(parseFloatValue(j), 0.0f);
}

TEST(ParseFloatValue, ReturnsZeroOnBool) {
    nlohmann::json j = true;
    EXPECT_FLOAT_EQ(parseFloatValue(j), 0.0f);
}

// ---------------------------------------------------------------------------
// ParseHexColor
// ---------------------------------------------------------------------------
TEST(ParseHexColor, ParsesSixDigitHex) {
    // #007DFF -> (0, 0.478, 1.0, 1.0)
    D2D1_COLOR_F c = ParseHexColor("#007DFF", D2D1::ColorF(0, 0, 0, 1));
    EXPECT_FLOAT_EQ(c.r, 0.0f / 255.0f);
    EXPECT_FLOAT_EQ(c.g, 0x7D / 255.0f);
    EXPECT_FLOAT_EQ(c.b, 0xFF / 255.0f);
    EXPECT_FLOAT_EQ(c.a, 1.0f);
}

TEST(ParseHexColor, ParsesEightDigitHexWithAlpha) {
    // #000000E6 -> (0, 0, 0, ~0.9)
    D2D1_COLOR_F c = ParseHexColor("#000000E6", D2D1::ColorF(0, 0, 0, 1));
    EXPECT_FLOAT_EQ(c.r, 0.0f);
    EXPECT_FLOAT_EQ(c.g, 0.0f);
    EXPECT_FLOAT_EQ(c.b, 0.0f);
    EXPECT_NEAR(c.a, 0.9f, 0.01f);  // 0xE6 = 230 / 255 ≈ 0.902
}

TEST(ParseHexColor, ReturnsDefaultOnEmpty) {
    D2D1_COLOR_F def = D2D1::ColorF(0.1f, 0.2f, 0.3f, 0.4f);
    D2D1_COLOR_F c = ParseHexColor("", def);
    EXPECT_FLOAT_EQ(c.r, 0.1f);
    EXPECT_FLOAT_EQ(c.g, 0.2f);
    EXPECT_FLOAT_EQ(c.b, 0.3f);
    EXPECT_FLOAT_EQ(c.a, 0.4f);
}

TEST(ParseHexColor, ReturnsDefaultOnInvalid) {
    D2D1_COLOR_F def = D2D1::ColorF(0.5f, 0.5f, 0.5f, 1.0f);
    D2D1_COLOR_F c = ParseHexColor("invalid", def);
    EXPECT_FLOAT_EQ(c.r, 0.5f);
    EXPECT_FLOAT_EQ(c.g, 0.5f);
    EXPECT_FLOAT_EQ(c.b, 0.5f);
    EXPECT_FLOAT_EQ(c.a, 1.0f);
}

TEST(ParseHexColor, ReturnsDefaultOnMissingHash) {
    D2D1_COLOR_F def = D2D1::ColorF(0.5f, 0.5f, 0.5f, 1.0f);
    D2D1_COLOR_F c = ParseHexColor("007DFF", def);
    EXPECT_FLOAT_EQ(c.r, 0.5f);
}

TEST(ParseHexColor, ReturnsDefaultOnWrongLength) {
    D2D1_COLOR_F def = D2D1::ColorF(0.5f, 0.5f, 0.5f, 1.0f);
    D2D1_COLOR_F c = ParseHexColor("#123", def);  // 3 digits not supported
    EXPECT_FLOAT_EQ(c.r, 0.5f);
}

TEST(ParseHexColor, ParsesBlack) {
    D2D1_COLOR_F c = ParseHexColor("#000000", D2D1::ColorF(1, 1, 1, 1));
    EXPECT_FLOAT_EQ(c.r, 0.0f);
    EXPECT_FLOAT_EQ(c.g, 0.0f);
    EXPECT_FLOAT_EQ(c.b, 0.0f);
    EXPECT_FLOAT_EQ(c.a, 1.0f);
}

TEST(ParseHexColor, ParsesWhite) {
    D2D1_COLOR_F c = ParseHexColor("#FFFFFF", D2D1::ColorF(0, 0, 0, 1));
    EXPECT_FLOAT_EQ(c.r, 1.0f);
    EXPECT_FLOAT_EQ(c.g, 1.0f);
    EXPECT_FLOAT_EQ(c.b, 1.0f);
    EXPECT_FLOAT_EQ(c.a, 1.0f);
}

TEST(ParseHexColor, ParsesFullAlpha) {
    D2D1_COLOR_F c = ParseHexColor("#FF0000FF", D2D1::ColorF(0, 0, 0, 1));
    EXPECT_FLOAT_EQ(c.r, 1.0f);
    EXPECT_FLOAT_EQ(c.g, 0.0f);
    EXPECT_FLOAT_EQ(c.b, 0.0f);
    EXPECT_FLOAT_EQ(c.a, 1.0f);
}

TEST(ParseHexColor, ParsesZeroAlpha) {
    D2D1_COLOR_F c = ParseHexColor("#FF000000", D2D1::ColorF(0, 0, 0, 1));
    EXPECT_FLOAT_EQ(c.r, 1.0f);
    EXPECT_FLOAT_EQ(c.g, 0.0f);
    EXPECT_FLOAT_EQ(c.b, 0.0f);
    EXPECT_FLOAT_EQ(c.a, 0.0f);
}

// ---------------------------------------------------------------------------
// ToWide
// ---------------------------------------------------------------------------
TEST(ToWide, ConvertsAscii) {
    std::wstring w = ToWide("Hello");
    EXPECT_EQ(w, L"Hello");
}

TEST(ToWide, ConvertsEmpty) {
    std::wstring w = ToWide("");
    EXPECT_EQ(w, L"");
}

TEST(ToWide, ConvertsUtf8Chinese) {
    // "你好" in UTF-8
    std::wstring w = ToWide("\xE4\xBD\xA0\xE5\xA5\xBD");
    EXPECT_EQ(w, L"你好");
}

TEST(ToWide, ConvertsMixedAlphaNumeric) {
    std::wstring w = ToWide("abc123XYZ");
    EXPECT_EQ(w, L"abc123XYZ");
}

TEST(ToWide, LengthMatches) {
    std::wstring w = ToWide("Hello");
    EXPECT_EQ(w.size(), 5u);
}
