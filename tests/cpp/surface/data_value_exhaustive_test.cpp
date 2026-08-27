#include <gtest/gtest.h>
#include <gmock/gmock.h>
#include "surface/component_manager/data_value/agenui_data_value_parser.h"
#include "surface/component_manager/data_value/agenui_static_data_value.h"
#include "surface/component_manager/data_value/agenui_data_binding_data_value.h"
#include "surface/component_manager/data_value/agenui_interpolation_expression_data_value.h"
#include "surface/component_manager/data_value/agenui_idata_value_context.h"
#include "surface/agenui_isurface_context.h"

using namespace agenui;

// =============================================================================
// Reuse mock context from data_value_test.cpp
// =============================================================================

class MockDataValueContext2 : public IDataValueContext {
public:
    MOCK_METHOD(int, getInstanceId, (), (const, override));
    MOCK_METHOD(std::string, getSurfaceId, (), (const, override));
    MOCK_METHOD(IDataModel*, getDataModel, (), (const, override));

    MockDataValueContext2() {
        ON_CALL(*this, getInstanceId()).WillByDefault(::testing::Return(1));
        ON_CALL(*this, getSurfaceId()).WillByDefault(::testing::Return("edge_surface"));
        ON_CALL(*this, getDataModel()).WillByDefault(::testing::Return(nullptr));
    }
};

// =============================================================================
// StaticDataValue: Exhaustive JSON type coverage
// =============================================================================

TEST(StaticDataValueExhaustive, StringValue) {
    StaticDataValue v("\"hello world\"");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, IntegerValue) {
    StaticDataValue v("42");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, NegativeIntegerValue) {
    StaticDataValue v("-99");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, FloatValue) {
    StaticDataValue v("3.14159");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, BooleanTrue) {
    StaticDataValue v("true");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, BooleanFalse) {
    StaticDataValue v("false");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, NullValue) {
    StaticDataValue v("null");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    // null should parse without crash
}

TEST(StaticDataValueExhaustive, EmptyString) {
    StaticDataValue v("\"\"");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, EmptyObject) {
    StaticDataValue v("{}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, EmptyArray) {
    StaticDataValue v("[]");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, NestedObject) {
    StaticDataValue v("{\"a\":{\"b\":{\"c\":1}}}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, ArrayOfPrimitives) {
    StaticDataValue v("[1, 2, 3, \"four\", true, null]");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, ArrayOfObjects) {
    StaticDataValue v("[{\"x\":1},{\"y\":2},{\"z\":3}]");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, StringWithSpecialChars) {
    StaticDataValue v("\"hello\\nworld\\t!\"");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, StringWithUnicode) {
    StaticDataValue v("\"\\u4e2d\\u6587\\u6d4b\\u8bd5\"");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, LargeNumber) {
    StaticDataValue v("999999999999");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, VerySmallFloat) {
    StaticDataValue v("0.0000001");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    EXPECT_NE(result, nullptr);
}

TEST(StaticDataValueExhaustive, ScientificNotation) {
    StaticDataValue v("1e10");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    // Should parse or gracefully fail
}

TEST(StaticDataValueExhaustive, DeeplyNestedArray) {
    std::string json = "[[[[[[[[[1]]]]]]]]";
    StaticDataValue v(json);
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    // Should not crash
}

TEST(StaticDataValueExhaustive, MalformedJson_HandledGracefully) {
    StaticDataValue v("{broken");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    // Must not crash
}

// =============================================================================
// DataBindingDataValue: Path resolution edge cases
// =============================================================================

TEST(DataBindingExhaustive, SimplePath_NoCrash) {
    DataBindingDataValue v("${user.name}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
    // No DataModel set → should return null/default without crash
}

TEST(DataBindingExhaustive, DeepPath_NoCrash) {
    DataBindingDataValue v("${a.b.c.d.e.f.g.h}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(DataBindingExhaustive, PathWithArrayIndex_NoCrash) {
    DataBindingDataValue v("${list[0].name}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(DataBindingExhaustive, PathWithNegativeIndex_NoCrash) {
    DataBindingDataValue v("${list[-1]}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(DataBindingExhaustive, PathWithLargeIndex_NoCrash) {
    DataBindingDataValue v("${list[999999]}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(DataBindingExhaustive, EmptyPath_NoCrash) {
    DataBindingDataValue v("${}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(DataBindingExhaustive, PathWithSpaces_NoCrash) {
    DataBindingDataValue v("${  key  }");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(DataBindingExhaustive, MultipleBindings_NoCrash) {
    DataBindingDataValue v("${a}${b}${c}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

// =============================================================================
// InterpolationExpressionDataValue: Template string edge cases
// =============================================================================

TEST(InterpolationExhaustive, SimpleInterpolation_NoCrash) {
    InterpolationExpressionDataValue v("Hello ${name}!");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, MultipleInterpolations_NoCrash) {
    InterpolationExpressionDataValue v("${a} ${b} ${c}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, NoInterpolation_LiteralString) {
    InterpolationExpressionDataValue v("Just a plain string");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, EmptyInterpolation_NoCrash) {
    InterpolationExpressionDataValue v("Prefix ${} Suffix");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, NestedDollarSign_NoCrash) {
    InterpolationExpressionDataValue v("Price: $$${amount}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, UnclosedBrace_NoCrash) {
    InterpolationExpressionDataValue v("Hello ${name");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, OnlyInterpolation_NoCrash) {
    InterpolationExpressionDataValue v("${only}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, EmptyString_NoCrash) {
    InterpolationExpressionDataValue v("");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, ChainedDotPath_NoCrash) {
    InterpolationExpressionDataValue v("${a.b.c.d.e.f}");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}

TEST(InterpolationExhaustive, SpecialCharsInLiteral_NoCrash) {
    InterpolationExpressionDataValue v("Tab\\there\\nNewline ${value} End");
    MockDataValueContext2 ctx;
    auto result = v.getValue(ctx);
}
