// Additional edge case tests for streaming pipeline and parser (R121-R140)

#include <gtest/gtest.h>
#include <chrono>
#include <string>
#include <thread>
#include "agenui_engine.h"
#include "agenui_engine_entry.h"
#include "agenui_surface_manager_interface.h"
#include "support/mock_message_listener.h"
#include "support/scoped_surface_manager.h"
#include "support/test_env.h"
#include "support/thread_sync_helper.h"

namespace {

class StreamingEdgeCaseTest : public ::testing::Test {
protected:
    ::agenui::testing::ScopedSurfaceManager sm;
    ::agenui::testing::MockMessageListener listener;

    void SetUp() override {
        ASSERT_TRUE(sm);
        sm->addSurfaceEventListener(&listener);
    }
    void TearDown() override {
        if (sm) sm->removeSurfaceEventListener(&listener);
    }
    void Drain(int timeoutMs = 2000) {
        ::agenui::testing::WaitForWorkerIdle(timeoutMs);
    }
};

// R121: Very long surfaceId (1000 chars)
TEST_F(StreamingEdgeCaseTest, R121_LongSurfaceId_NoCrash) {
    std::string longId(1000, 'x');
    std::string json = R"({"version":"v0.9","createSurface":{"surfaceId":")" + longId +
                       R"(","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})";
    sm->beginTextStream();
    sm->receiveTextChunk(json);
    sm->endTextStream();
    Drain(3000);
    SUCCEED();
}

// R122: SurfaceId with special JSON chars
TEST_F(StreamingEdgeCaseTest, R122_SurfaceIdSpecialChars_NoCrash) {
    std::string json = R"({"version":"v0.9","createSurface":{"surfaceId":"test\"quoted\"id","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})";
    sm->beginTextStream();
    sm->receiveTextChunk(json);
    sm->endTextStream();
    Drain();
    SUCCEED();
}

// R123: 10KB single chunk
TEST_F(StreamingEdgeCaseTest, R123_LargeChunk10KB_NoCrash) {
    std::string text(10000, 'A');
    std::string json = R"({"version":"v0.9","updateComponents":{"surfaceId":"r123","components":[{"id":"t1","type":"Text","properties":{"text":")" + text + R"("}}]}})";
    sm->beginTextStream();
    sm->receiveTextChunk(json);
    sm->endTextStream();
    Drain(3000);
    SUCCEED();
}

// R124: Unicode in component text
TEST_F(StreamingEdgeCaseTest, R124_UnicodeText_NoCrash) {
    std::string json = R"({"version":"v0.9","updateComponents":{"surfaceId":"r124","components":[{"id":"u1","type":"Text","properties":{"text":"你好世界🎉"}}]}})";
    sm->beginTextStream();
    sm->receiveTextChunk(json);
    sm->endTextStream();
    Drain();
    SUCCEED();
}

// R125: Nested begin/end
TEST_F(StreamingEdgeCaseTest, R125_NestedBeginEnd_NoCrash) {
    sm->beginTextStream();
    sm->beginTextStream(); // nested begin
    sm->endTextStream();
    sm->endTextStream();
    Drain();
    SUCCEED();
}

// R126: End without begin
TEST_F(StreamingEdgeCaseTest, R126_EndWithoutBegin_NoCrash) {
    sm->endTextStream();
    Drain();
    SUCCEED();
}

// R127: Multiple ends
TEST_F(StreamingEdgeCaseTest, R127_MultipleEnds_NoCrash) {
    sm->beginTextStream();
    sm->endTextStream();
    sm->endTextStream();
    sm->endTextStream();
    Drain();
    SUCCEED();
}

// R128: Empty JSON object chunk
TEST_F(StreamingEdgeCaseTest, R128_EmptyJsonObject_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk("{}");
    sm->endTextStream();
    Drain();
    SUCCEED();
}

// R129: JSON array root (not valid A2UI message)
TEST_F(StreamingEdgeCaseTest, R129_JsonArrayRoot_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk("[]");
    sm->endTextStream();
    Drain();
    SUCCEED();
}

// R130: Binary data in chunk
TEST_F(StreamingEdgeCaseTest, R130_BinaryData_NoCrash) {
    sm->beginTextStream();
    std::string binary;
    binary.push_back(0x00);
    binary.push_back(0x01);
    binary.push_back(0xFF);
    sm->receiveTextChunk(binary);
    sm->endTextStream();
    Drain();
    SUCCEED();
}

} // namespace
