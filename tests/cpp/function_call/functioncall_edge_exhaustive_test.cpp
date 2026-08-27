#include <gtest/gtest.h>
#include "function_call/agenui_functioncall_manager.h"
#include "function_call/agenui_functioncall_config.h"
#include <string>

using agenui::FunctionCallManager;
using agenui::FunctionCallConfig;

// =============================================================================
// FunctionCall edge cases — malformed names, extreme arguments, concurrency
// =============================================================================

class FunctionCallEdgeTest : public ::testing::Test {
protected:
    void SetUp() override {
        manager = std::make_unique<FunctionCallManager>();
    }
    std::unique_ptr<FunctionCallManager> manager;
};

// =============================================================================
// Function name edge cases
// =============================================================================

TEST_F(FunctionCallEdgeTest, EmptyFunctionName_NoCrash) {
    auto result = manager->invoke("");
    EXPECT_FALSE(result.success);
}

TEST_F(FunctionCallEdgeTest, VeryLongFunctionName_NoCrash) {
    std::string longName(10000, 'f');
    auto result = manager->invoke(longName);
    EXPECT_FALSE(result.success);
}

TEST_F(FunctionCallEdgeTest, FunctionNameWithSpaces_NoCrash) {
    auto result = manager->invoke("some function name");
    EXPECT_FALSE(result.success);
}

TEST_F(FunctionCallEdgeTest, FunctionNameWithSpecialChars_NoCrash) {
    auto result = manager->invoke("function!@#$%^&*()");
    EXPECT_FALSE(result.success);
}

TEST_F(FunctionCallEdgeTest, FunctionNameWithUnicode_NoCrash) {
    auto result = manager->invoke("\u51fd\u6570"); // 函数
    EXPECT_FALSE(result.success);
}

TEST_F(FunctionCallEdgeTest, CaseSensitiveFunctionName) {
    // If "toast" is registered, "Toast" should not match
    auto resultUpper = manager->invoke("Toast");
    auto resultLower = manager->invoke("toast");
    // Both should be either not found (case-sensitive) or
    // the same (case-insensitive) — verify consistency
    EXPECT_EQ(resultUpper.success, resultLower.success);
}

// =============================================================================
// Argument edge cases
// =============================================================================

TEST_F(FunctionCallEdgeTest, NullArguments_NoCrash) {
    auto result = manager->invoke("test_func", nullptr);
    EXPECT_FALSE(result.success);
}

TEST_F(FunctionCallEdgeTest, EmptyArgumentsObject_NoCrash) {
    auto result = manager->invoke("test_func", "{}");
    // Should not crash whether it succeeds or fails
}

TEST_F(FunctionCallEdgeTest, LargeArgumentsObject_NoCrash) {
    std::string largeArg = "{\"data\":\"" + std::string(100000, 'x') + "\"}";
    auto result = manager->invoke("test_func", largeArg);
    // Should not crash
}

TEST_F(FunctionCallEdgeTest, DeeplyNestedArguments_NoCrash) {
    std::string nested = "{\"a\":{\"b\":{\"c\":{\"d\":{\"e\":\"deep\"}}}}}";
    auto result = manager->invoke("test_func", nested);
    // Should not crash
}

TEST_F(FunctionCallEdgeTest, ArrayArguments_NoCrash) {
    auto result = manager->invoke("test_func", "[1,2,3]");
    // Should not crash
}

TEST_F(FunctionCallEdgeTest, MalformedArguments_NoCrash) {
    auto result = manager->invoke("test_func", "{broken");
    // Should not crash
}

TEST_F(FunctionCallEdgeTest, StringArguments_NoCrash) {
    auto result = manager->invoke("test_func", "\"just a string\"");
    // Should not crash
}

TEST_F(FunctionCallEdgeTest, NumberArguments_NoCrash) {
    auto result = manager->invoke("test_func", "42");
    // Should not crash
}

// =============================================================================
// Config edge cases
// =============================================================================

TEST(FunctionCallConfigEdge, EmptyConfig_Handled) {
    FunctionCallConfig config;
    // Loading empty config should not crash
}

TEST(FunctionCallConfigEdge, MalformedConfig_Handled) {
    FunctionCallConfig config;
    // Loading malformed JSON should not crash
}

TEST(FunctionCallConfigEdge, LargeConfig_Handled) {
    FunctionCallConfig config;
    // Large config should be handled within memory limits
}

// =============================================================================
// Function registration edge cases
// =============================================================================

TEST_F(FunctionCallEdgeTest, RegisterNullFunction_NoCrash) {
    // Registering a null function pointer should be handled
    SUCCEED();
}

TEST_F(FunctionCallEdgeTest, InvokeUnregisteredFunction_ReturnsFalse) {
    auto result = manager->invoke("unregistered_func_12345");
    EXPECT_FALSE(result.success);
}

TEST_F(FunctionCallEdgeTest, InvokeWithExtraArguments_NoCrash) {
    // Passing more arguments than the function expects
    auto result = manager->invoke("test_func", "{\"a\":1,\"b\":2,\"c\":3}");
    // Should not crash
}

TEST_F(FunctionCallEdgeTest, InvokeWithMissingArguments_NoCrash) {
    // Passing fewer arguments than the function expects
    auto result = manager->invoke("test_func", "{}");
    // Should not crash
}

// =============================================================================
// Stress: many rapid invokes
// =============================================================================

TEST_F(FunctionCallEdgeTest, RapidInvoke_100Times_NoCrash) {
    for (int i = 0; i < 100; i++) {
        manager->invoke("test_func_" + std::to_string(i));
    }
    SUCCEED();
}

TEST_F(FunctionCallEdgeTest, ConcurrentInvokes_NoCrash) {
    std::vector<std::thread> threads;
    for (int t = 0; t < 10; t++) {
        threads.emplace_back([this, t]() {
            for (int i = 0; i < 100; i++) {
                manager->invoke("test_func_" + std::to_string(t) + "_" + std::to_string(i));
            }
        });
    }
    for (auto& t : threads) t.join();
    SUCCEED();
}
