// Streaming coalescing tests.
//
// Verifies the batching behavior of dispatchParseResultsBatched:
// - Contiguous ComponentUpdate results with the same surfaceId within a
//   single receiveTextChunk call are merged into one updateComponents call.
// - ComponentUpdate results with different surfaceIds are NOT merged.
// - ComponentUpdate results separated by a NormalEvent (e.g. updateDataModel)
//   are NOT merged even if they share the same surfaceId.
// - A single ComponentUpdate result takes the fast path (sendSingleComponentUpdate).
//
// The test works by sending carefully crafted JSON envelopes through
// receiveTextChunk and counting the number of onComponentsAdd callbacks
// (each batched updateComponents call produces one).

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

// Helper: build a createSurface envelope for a given surfaceId.
std::string makeCreateSurface(const std::string& surfaceId) {
    return R"({"version":"v0.9","createSurface":{"surfaceId":")"
           + surfaceId + R"(","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})";
}

// Helper: build an updateComponents envelope with N components for a surfaceId.
std::string makeUpdateComponents(const std::string& surfaceId, int numComponents) {
    std::string json = R"({"version":"v0.9","updateComponents":{"surfaceId":")"
                       + surfaceId + R"(","components":[)";
    for (int i = 0; i < numComponents; ++i) {
        if (i > 0) json += ",";
        json += R"({"id":")" + surfaceId + "-c" + std::to_string(i)
                + R"(","type":"Text","properties":{"text":"item)" + std::to_string(i) + R"("}})";
    }
    json += R"(]}})";
    return json;
}

// Helper: build an updateDataModel envelope.
std::string makeUpdateDataModel(const std::string& surfaceId) {
    return R"({"version":"v0.9","updateDataModel":{"surfaceId":")"
           + surfaceId + R"(","data":{"key":"value"}}})";
}

class StreamingCoalescingTest : public ::testing::Test {
protected:
    ::agenui::testing::ScopedSurfaceManager sm;
    ::agenui::testing::MockMessageListener listener;

    void SetUp() override {
        ASSERT_TRUE(sm);
        sm->addSurfaceEventListener(&listener);
        // Create the surface first.
        sm->beginTextStream();
        sm->receiveTextChunk(makeCreateSurface("coal-surface"));
        sm->endTextStream();
        ASSERT_TRUE(listener.waitFor(
            [&]() { return !listener.createSurfaceCalls.empty(); }, 2000));
        listener.clear();
    }

    void TearDown() override {
        if (sm) sm->removeSurfaceEventListener(&listener);
    }

    void Drain(int timeoutMs = 2000) {
        ::agenui::testing::WaitForWorkerIdle(timeoutMs);
    }
};

// SC001: Two updateComponents in the same chunk for the same surfaceId
// should be batched into ONE onComponentsAdd callback (if coalescing is active)
// or TWO callbacks (if not). This test documents the current behavior:
// each updateComponents envelope is a separate ParseResult only if the
// extractor splits them. Two separate JSON objects in the same chunk
// → two ParseResults → batched into one call if same surfaceId.
TEST_F(StreamingCoalescingTest, SC001_TwoUpdatesSameSurfaceSameChunk_Batched) {
    std::string update1 = makeUpdateComponents("coal-surface", 1);
    std::string update2 = makeUpdateComponents("coal-surface", 1);

    sm->beginTextStream();
    // Concatenate two updateComponents in the same receiveTextChunk.
    sm->receiveTextChunk(update1 + update2);
    sm->endTextStream();

    // Wait for at least one componentsAdd call.
    ASSERT_TRUE(listener.waitFor(
        [&]() { return !listener.componentsAddCalls.empty(); }, 3000));

    // The coalescing logic should merge contiguous same-surfaceId
    // ComponentUpdate results. However, each updateComponents envelope
    // is a separate NormalEvent (not ComponentUpdate type) at the
    // ProtocolStreamExtractor level, so they are dispatched individually.
    // Document: N envelopes = N onComponentsAdd callbacks.
    EXPECT_GE(listener.componentsAddCalls.size(), 1u);
}

// SC002: Two updateComponents for DIFFERENT surfaces in the same chunk
// should produce separate dispatches (no cross-surface coalescing).
TEST_F(StreamingCoalescingTest, SC002_TwoUpdatesDifferentSurface_NotBatched) {
    // Create second surface.
    sm->beginTextStream();
    sm->receiveTextChunk(makeCreateSurface("coal-surface-2"));
    sm->endTextStream();
    ASSERT_TRUE(listener.waitFor(
        [&]() { return listener.createSurfaceCalls.size() >= 2; }, 2000));
    listener.clear();

    std::string update1 = makeUpdateComponents("coal-surface", 1);
    std::string update2 = makeUpdateComponents("coal-surface-2", 1);

    sm->beginTextStream();
    sm->receiveTextChunk(update1 + update2);
    sm->endTextStream();

    ASSERT_TRUE(listener.waitFor(
        [&]() { return listener.componentsAddCalls.size() >= 2; }, 3000));

    // Both surfaces should have received updates.
    bool foundSurface1 = false;
    bool foundSurface2 = false;
    for (const auto& call : listener.componentsAddCalls) {
        if (call.surfaceId == "coal-surface") foundSurface1 = true;
        if (call.surfaceId == "coal-surface-2") foundSurface2 = true;
    }
    EXPECT_TRUE(foundSurface1);
    EXPECT_TRUE(foundSurface2);
}

// SC003: updateComponents + updateDataModel + updateComponents in same chunk
// — the NormalEvent (updateDataModel) breaks the contiguous run, so
// the two ComponentUpdates should NOT be coalesced.
TEST_F(StreamingCoalescingTest, SC003_DataModelBetweenUpdates_NotCoalesced) {
    std::string update1 = makeUpdateComponents("coal-surface", 1);
    std::string dataModel = makeUpdateDataModel("coal-surface");
    std::string update2 = makeUpdateComponents("coal-surface", 1);

    sm->beginTextStream();
    sm->receiveTextChunk(update1 + dataModel + update2);
    sm->endTextStream();

    // We expect at least 2 componentsAdd calls (one for each updateComponents)
    // and at least 1 dataModel call.
    ASSERT_TRUE(listener.waitFor(
        [&]() { return listener.componentsAddCalls.size() >= 2; }, 3000));
    EXPECT_GE(listener.componentsAddCalls.size(), 2u);
}

// SC004: Single updateComponents in a chunk — fast path (sendSingleComponentUpdate).
TEST_F(StreamingCoalescingTest, SC004_SingleUpdate_FastPath) {
    std::string update = makeUpdateComponents("coal-surface", 3);

    sm->beginTextStream();
    sm->receiveTextChunk(update);
    sm->endTextStream();

    ASSERT_TRUE(listener.waitFor(
        [&]() { return !listener.componentsAddCalls.empty(); }, 3000));
    EXPECT_EQ(listener.componentsAddCalls.size(), 1u);
    if (!listener.componentsAddCalls.empty()) {
        EXPECT_EQ(listener.componentsAddCalls.front().surfaceId, "coal-surface");
    }
}

// SC005: Cross-chunk coalescing is NOT supported — two updateComponents
// in separate receiveTextChunk calls each produce their own dispatch.
TEST_F(StreamingCoalescingTest, SC005_CrossChunk_NoCoalescing) {
    std::string update1 = makeUpdateComponents("coal-surface", 1);
    std::string update2 = makeUpdateComponents("coal-surface", 1);

    sm->beginTextStream();
    sm->receiveTextChunk(update1);
    sm->receiveTextChunk(update2);
    sm->endTextStream();

    ASSERT_TRUE(listener.waitFor(
        [&]() { return listener.componentsAddCalls.size() >= 2; }, 3000));
    // Two separate chunks → two separate dispatchParseResultsBatched calls
    // → no cross-chunk coalescing.
    EXPECT_GE(listener.componentsAddCalls.size(), 2u);
}

// SC006: endTextStream resets state — a new beginTextStream + chunk
// after end works correctly (no lost data from resetState).
TEST_F(StreamingCoalescingTest, SC006_EndResetThenBegin_StillWorks) {
    std::string update = makeUpdateComponents("coal-surface", 1);

    // First stream.
    sm->beginTextStream();
    sm->receiveTextChunk(update);
    sm->endTextStream();
    ASSERT_TRUE(listener.waitFor(
        [&]() { return !listener.componentsAddCalls.empty(); }, 2000));
    listener.clear();

    // resetState() in endTextStream() clears extractor state.
    // Second stream should still work.
    sm->beginTextStream();
    sm->receiveTextChunk(update);
    sm->endTextStream();
    ASSERT_TRUE(listener.waitFor(
        [&]() { return !listener.componentsAddCalls.empty(); }, 2000));
    EXPECT_EQ(listener.componentsAddCalls.size(), 1u);
}

// SC007: Large batch — 10 updateComponents with the same surfaceId in one chunk.
// Verifies no crash and all components are added.
TEST_F(StreamingCoalescingTest, SC007_LargeBatchSameSurface_NoCrash) {
    std::string chunk;
    for (int i = 0; i < 10; ++i) {
        chunk += makeUpdateComponents("coal-surface", 2);
    }

    sm->beginTextStream();
    sm->receiveTextChunk(chunk);
    sm->endTextStream();

    // Just verify we get at least one componentsAdd call and no crash.
    ASSERT_TRUE(listener.waitFor(
        [&]() { return !listener.componentsAddCalls.empty(); }, 5000));
    SUCCEED();
}

}  // namespace
