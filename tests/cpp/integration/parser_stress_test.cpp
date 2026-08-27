// Additional parser stress tests (R141-R150)

#include <gtest/gtest.h>
#include <chrono>
#include <string>
#include <thread>
#include "agenui_engine.h"
#include "agenui_surface_manager_interface.h"
#include "support/mock_message_listener.h"
#include "support/scoped_surface_manager.h"
#include "support/test_env.h"
#include "support/thread_sync_helper.h"

namespace {

class ParserStressTest : public ::testing::Test {
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
};

// R141: 100 sequential createSurface + deleteSurface
TEST_F(ParserStressTest, R141_100CreateDeleteCycle_NoLeak) {
    for (int i = 0; i < 100; ++i) {
        std::string id = "stress-" + std::to_string(i);
        std::string createJson = R"({"version":"v0.9","createSurface":{"surfaceId":")" + id +
            R"(","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})";
        std::string deleteJson = R"({"version":"v0.9","deleteSurface":{"surfaceId":")" + id + R"("}})";
        sm->beginTextStream();
        sm->receiveTextChunk(createJson);
        sm->endTextStream();
        sm->beginTextStream();
        sm->receiveTextChunk(deleteJson);
        sm->endTextStream();
    }
    ::agenui::testing::WaitForWorkerIdle(10000);
    SUCCEED();
}

// R142: 50 rapid updateComponents on same surface
TEST_F(ParserStressTest, R142_50RapidUpdates_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk(R"({"version":"v0.9","createSurface":{"surfaceId":"r142","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})");
    sm->endTextStream();
    ::agenui::testing::WaitForWorkerIdle(2000);

    for (int i = 0; i < 50; ++i) {
        std::string json = R"({"version":"v0.9","updateComponents":{"surfaceId":"r142","components":[{"id":"c)" + std::to_string(i) +
            R"(","type":"Text","properties":{"text":")" + std::to_string(i) + R"("}}]}})";
        sm->beginTextStream();
        sm->receiveTextChunk(json);
        sm->endTextStream();
    }
    ::agenui::testing::WaitForWorkerIdle(5000);
    SUCCEED();
}

// R143: Chunk with 1000 components
TEST_F(ParserStressTest, R143_1000ComponentsInOneChunk_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk(R"({"version":"v0.9","createSurface":{"surfaceId":"r143","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})");
    sm->endTextStream();
    ::agenui::testing::WaitForWorkerIdle(2000);

    std::string components = "[";
    for (int i = 0; i < 1000; ++i) {
        if (i > 0) components += ",";
        components += R"({"id":"c)" + std::to_string(i) + R"(","type":"Text","properties":{"text":"x"}})";
    }
    components += "]";
    std::string json = R"({"version":"v0.9","updateComponents":{"surfaceId":"r143","components":)" + components + "}}";

    sm->beginTextStream();
    sm->receiveTextChunk(json);
    sm->endTextStream();
    ::agenui::testing::WaitForWorkerIdle(10000);
    SUCCEED();
}

// R144: 1-byte chunks for 500 chars
TEST_F(ParserStressTest, R144_1ByteChunks500Chars_NoCrash) {
    std::string json = R"({"version":"v0.9","createSurface":{"surfaceId":"r144","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})";
    sm->beginTextStream();
    for (char c : json) {
        sm->receiveTextChunk(std::string(1, c));
    }
    sm->endTextStream();
    ::agenui::testing::WaitForWorkerIdle(5000);
    SUCCEED();
}

// R145: Mixed valid and invalid chunks
TEST_F(ParserStressTest, R145_MixedValidInvalid_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk(R"({"version":"v0.9","createSurface":{"surfaceId":"r145","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})");
    sm->receiveTextChunk("invalid garbage");
    sm->receiveTextChunk(R"({"version":"v0.9","updateComponents":{"surfaceId":"r145","components":[{"id":"c1","type":"Text","properties":{"text":"ok"}}]}})");
    sm->endTextStream();
    ::agenui::testing::WaitForWorkerIdle(3000);
    SUCCEED();
}

} // namespace
