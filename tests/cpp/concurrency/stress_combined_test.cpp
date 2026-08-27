// Concurrency stress tests — expanded edge cases.
//
// Tests thread safety under extreme conditions:
// - Many surfaces created/deleted concurrently
// - Rapid begin/end stream from multiple threads
// - Listener registration during event dispatch
// - Memory pressure with many components

#include <gtest/gtest.h>
#include <atomic>
#include <chrono>
#include <vector>
#include <thread>

#include "agenui_engine.h"
#include "agenui_engine_entry.h"
#include "agenui_surface_manager_interface.h"
#include "support/mock_message_listener.h"
#include "support/scoped_surface_manager.h"
#include "support/test_env.h"
#include "support/thread_sync_helper.h"

namespace {

std::string makeCreateSurface(const std::string& surfaceId) {
    return R"({"version":"v0.9","createSurface":{"surfaceId":")"
           + surfaceId + R"(","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})";
}

std::string makeDeleteSurface(const std::string& surfaceId) {
    return R"({"version":"v0.9","deleteSurface":{"surfaceId":")"
           + surfaceId + R"("}})";
}

class ConcurrencyStressTest : public ::testing::Test {
protected:
    void SetUp() override {
        engine = std::make_unique<agenui::Engine>();
        engine->initialize();
        sm = std::make_unique<ScopedSurfaceManager>(engine.get());
    }
    void TearDown() override {
        sm.reset();
        engine.reset();
    }
    std::unique_ptr<agenui::Engine> engine;
    std::unique_ptr<ScopedSurfaceManager> sm;
};

// =============================================================================
// Many concurrent surface creations
// =============================================================================

TEST_F(ConcurrencyStressTest, Create20Surfaces_Concurrent) {
    const int numThreads = 4;
    const int surfacesPerThread = 5;
    std::atomic<int> successCount(0);

    std::vector<std::thread> threads;
    for (int t = 0; t < numThreads; t++) {
        threads.emplace_back([this, t, &successCount]() {
            for (int i = 0; i < surfacesPerThread; i++) {
                std::string sid = "stress_" + std::to_string(t) + "_" + std::to_string(i);
                sm->beginTextStream();
                sm->receiveTextChunk(makeCreateSurface(sid));
                sm->endTextStream();
                if (sm->findSurface(sid) != nullptr) {
                    successCount++;
                }
            }
        });
    }
    for (auto& t : threads) t.join();

    EXPECT_EQ(successCount.load(), numThreads * surfacesPerThread);
}

// =============================================================================
// Concurrent create + delete
// =============================================================================

TEST_F(ConcurrencyStressTest, CreateAndDelete_Concurrent) {
    std::atomic<bool> stop(false);
    std::atomic<int> createCount(0);
    std::atomic<int> deleteCount(0);

    // Creator thread
    std::thread creator([this, &stop, &createCount]() {
        for (int i = 0; i < 50 && !stop; i++) {
            std::string sid = "cad_" + std::to_string(i);
            sm->beginTextStream();
            sm->receiveTextChunk(makeCreateSurface(sid));
            sm->endTextStream();
            createCount++;
        }
    });

    // Deleter thread
    std::thread deleter([this, &stop, &deleteCount]() {
        for (int i = 0; i < 50 && !stop; i++) {
            std::string sid = "cad_" + std::to_string(i);
            sm->beginTextStream();
            sm->receiveTextChunk(makeDeleteSurface(sid));
            sm->endTextStream();
            deleteCount++;
        }
    });

    creator.join();
    stop = true;
    deleter.join();

    // No crash = pass
    SUCCEED();
}

// =============================================================================
// Concurrent stream from multiple threads to same surface
// =============================================================================

TEST_F(ConcurrencyStressTest, ConcurrentStream_SameSurface) {
    std::string sid = "shared_stress";
    sm->beginTextStream();
    sm->receiveTextChunk(makeCreateSurface(sid));
    sm->endTextStream();
    ASSERT_NE(sm->findSurface(sid), nullptr);

    // Multiple threads streaming to the same surface simultaneously
    std::vector<std::thread> threads;
    for (int t = 0; t < 5; t++) {
        threads.emplace_back([this, sid, t]() {
            std::string msg = R"({"version":"v0.9","updateDataModel":{"surfaceId":")"
                            + sid + R"(","data":{"thread":)" + std::to_string(t) + "}}}";
            sm->beginTextStream();
            sm->receiveTextChunk(msg);
            sm->endTextStream();
        });
    }
    for (auto& t : threads) t.join();
    SUCCEED();
}

// =============================================================================
// Listener registration during dispatch
// =============================================================================

TEST_F(ConcurrencyStressTest, RegisterListener_DuringDispatch) {
    std::string sid = "listener_stress";
    sm->beginTextStream();
    sm->receiveTextChunk(makeCreateSurface(sid));
    sm->endTextStream();

    std::atomic<int> callbackCount(0);

    // Thread 1: continuously send updates
    std::thread sender([this, sid]() {
        for (int i = 0; i < 100; i++) {
            std::string msg = R"({"version":"v0.9","updateDataModel":{"surfaceId":")"
                            + sid + R"(","data":{"i":)" + std::to_string(i) + "}}}";
            sm->beginTextStream();
            sm->receiveTextChunk(msg);
            sm->endTextStream();
        }
    });

    // Thread 2: continuously register/unregister listeners
    std::thread registrant([this, &callbackCount]() {
        for (int i = 0; i < 50; i++) {
            auto listener = std::make_shared<MockMessageListener>();
            sm->addListener(listener);
            std::this_thread::sleep_for(std::chrono::milliseconds(1));
            sm->removeListener(listener);
        }
    });

    sender.join();
    registrant.join();
    SUCCEED();
}

// =============================================================================
// Memory pressure: many components
// =============================================================================

TEST_F(ConcurrencyStressTest, ManyComponents_500_NoCrash) {
    std::string sid = "many_comp_stress";
    sm->beginTextStream();
    sm->receiveTextChunk(makeCreateSurface(sid));
    sm->endTextStream();

    // Create 500 components
    std::string components = R"({"version":"v0.9","updateComponents":{"surfaceId":")"
                            + sid + R"(","components":[)";
    for (int i = 0; i < 500; i++) {
        if (i > 0) components += ",";
        components += R"({"id":"c_)" + std::to_string(i) + R"(","type":"text","properties":{"text":"item)" + std::to_string(i) + R"("}})";
    }
    components += "]}";

    sm->beginTextStream();
    sm->receiveTextChunk(components);
    sm->endTextStream();
    SUCCEED();
}

// =============================================================================
// Rapid fire: create → update → delete cycle
// =============================================================================

TEST_F(ConcurrencyStressTest, RapidCreateUpdateDelete_100Cycles) {
    for (int i = 0; i < 100; i++) {
        std::string sid = "cycle_" + std::to_string(i);
        sm->beginTextStream();
        sm->receiveTextChunk(makeCreateSurface(sid));
        sm->receiveTextChunk(R"({"version":"v0.9","updateDataModel":{"surfaceId":")"
                           + sid + R"(","data":{"i":)" + std::to_string(i) + "}}}");
        sm->receiveTextChunk(makeDeleteSurface(sid));
        sm->endTextStream();
    }
    SUCCEED();
}

// =============================================================================
// Race: delete surface while another thread is streaming to it
// =============================================================================

TEST_F(ConcurrencyStressTest, DeleteWhileStreaming_NoCrash) {
    std::string sid = "race_delete";
    sm->beginTextStream();
    sm->receiveTextChunk(makeCreateSurface(sid));
    sm->endTextStream();

    std::atomic<bool> streaming(true);

    // Streamer
    std::thread streamer([this, sid, &streaming]() {
        while (streaming) {
            sm->beginTextStream();
            sm->receiveTextChunk(R"({"version":"v0.9","updateDataModel":{"surfaceId":")"
                               + sid + R"(","data":{"x":1}}}");
            sm->endTextStream();
        }
    });

    // Give streamer some time to start
    std::this_thread::sleep_for(std::chrono::milliseconds(10));

    // Deleter
    sm->beginTextStream();
    sm->receiveTextChunk(makeDeleteSurface(sid));
    sm->endTextStream();

    streaming = false;
    streamer.join();

    SUCCEED();
}
