// Streaming pipeline edge case tests — malformed, truncated, and boundary inputs.
//
// Tests how the streaming parser handles:
// - Truncated JSON chunks (partial messages across chunk boundaries)
// - Malformed JSON that doesn't parse
// - Extremely large chunks
// - Empty chunks
// - Chunks with only whitespace
// - Rapid-fire small chunks (1-byte each)
// - Chunks with embedded null bytes
// - UTF-8 boundary splitting (multi-byte chars split across chunks)

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

std::string makeCreateSurface(const std::string& surfaceId) {
    return R"({"version":"v0.9","createSurface":{"surfaceId":")"
           + surfaceId + R"(","catalogId":"x","theme":{},"sendDataModel":false,"animated":false}})";
}

class StreamEdgeCaseTest : public ::testing::Test {
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

    void sendChunked(const std::string& fullText, int chunkSize) {
        sm->beginTextStream();
        for (size_t i = 0; i < fullText.size(); i += chunkSize) {
            int len = std::min((int)chunkSize, (int)(fullText.size() - i));
            sm->receiveTextChunk(fullText.substr(i, len));
        }
        sm->endTextStream();
    }

    std::unique_ptr<agenui::Engine> engine;
    std::unique_ptr<ScopedSurfaceManager> sm;
};

// =============================================================================
// Empty / whitespace chunks
// =============================================================================

TEST_F(StreamEdgeCaseTest, EmptyChunk_NoCrash) {
    std::string surfaceId = "edge_empty";
    sendChunked(makeCreateSurface(surfaceId), 1);
    EXPECT_NE(sm->findSurface(surfaceId), nullptr);

    sm->beginTextStream();
    sm->receiveTextChunk("");
    sm->endTextStream();
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, WhitespaceOnlyChunk_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk("   \t\n\r  ");
    sm->endTextStream();
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, MultipleEmptyChunks_NoCrash) {
    sm->beginTextStream();
    for (int i = 0; i < 10; i++) {
        sm->receiveTextChunk("");
    }
    sm->endTextStream();
    SUCCEED();
}

// =============================================================================
// 1-byte chunk splitting — every byte is its own chunk
// =============================================================================

TEST_F(StreamEdgeCaseTest, OneByteChunks_CreateSurface_Parses) {
    std::string surfaceId = "edge_1byte";
    sendChunked(makeCreateSurface(surfaceId), 1);
    EXPECT_NE(sm->findSurface(surfaceId), nullptr);
}

TEST_F(StreamEdgeCaseTest, OneByteChunks_TwoMessages_Parses) {
    std::string surfaceId1 = "edge_1b_a";
    std::string surfaceId2 = "edge_1b_b";
    std::string combined = makeCreateSurface(surfaceId1) + makeCreateSurface(surfaceId2);
    sendChunked(combined, 1);
    EXPECT_NE(sm->findSurface(surfaceId1), nullptr);
    EXPECT_NE(sm->findSurface(surfaceId2), nullptr);
}

// =============================================================================
// Malformed JSON — should not crash the parser
// =============================================================================

TEST_F(StreamEdgeCaseTest, MalformedJson_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk("{broken json}");
    sm->endTextStream();
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, TruncatedJson_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk(R"({"version":"v0.9","createSurface":{"surfaceId":"trunc)");
    sm->endTextStream();
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, GarbageText_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk("This is not JSON at all!");
    sm->endTextStream();
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, BinaryData_NoCrash) {
    sm->beginTextStream();
    std::string binary;
    for (int i = 0; i < 256; i++) {
        binary += static_cast<char>(i);
    }
    sm->receiveTextChunk(binary);
    sm->endTextStream();
    SUCCEED();
}

// =============================================================================
// UTF-8 boundary splitting
// =============================================================================

TEST_F(StreamEdgeCaseTest, Utf8SplitAcrossChunks_NoCrash) {
    // Chinese characters are 3 bytes in UTF-8. Split each one.
    // "中文测试" = 12 bytes
    std::string surfaceId = "edge_utf8";
    std::string msg = makeCreateSurface(surfaceId);
    // Insert a Chinese string in the middle
    size_t pos = msg.find("\"x\"");
    if (pos != std::string::npos) {
        msg.replace(pos, 3, "\"中文\"");
    }
    sendChunked(msg, 1);
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, EmojiSplitAcrossChunks_NoCrash) {
    // Emoji are 4 bytes in UTF-8
    std::string emoji = "\xF0\x9F\x98\x80"; // 😀
    sm->beginTextStream();
    // Split the emoji across two chunks
    sm->receiveTextChunk(std::string("{\"emoji\":\""));
    sm->receiveTextChunk(emoji.substr(0, 2));
    sm->receiveTextChunk(emoji.substr(2, 2));
    sm->receiveTextChunk("\"}");
    sm->endTextStream();
    SUCCEED();
}

// =============================================================================
// Large chunk stress
// =============================================================================

TEST_F(StreamEdgeCaseTest, LargeChunk_100KB_NoCrash) {
    std::string surfaceId = "edge_large";
    std::string msg = makeCreateSurface(surfaceId);
    // Pad with a large theme object
    std::string padding(100000, ' ');
    size_t pos = msg.find("\"theme\":{}");
    if (pos != std::string::npos) {
        msg.replace(pos, 10, "\"theme\":{\"pad\":\"" + padding + "\"}");
    }
    sm->beginTextStream();
    sm->receiveTextChunk(msg);
    sm->endTextStream();
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, ManySmallChunks_1000_NoCrash) {
    std::string surfaceId = "edge_many";
    std::string msg = makeCreateSurface(surfaceId);
    sendChunked(msg, 1);
    EXPECT_NE(sm->findSurface(surfaceId), nullptr);

    // Now send 1000 tiny whitespace chunks
    sm->beginTextStream();
    for (int i = 0; i < 1000; i++) {
        sm->receiveTextChunk(" ");
    }
    sm->endTextStream();
    SUCCEED();
}

// =============================================================================
// Chunk boundary at critical positions
// =============================================================================

TEST_F(StreamEdgeCaseTest, SplitAtVersionKey_Parses) {
    std::string msg = makeCreateSurface("edge_split_v");
    // Split right after "version":"v0.9"
    size_t splitPos = msg.find("v0.9") + 4;
    std::string part1 = msg.substr(0, splitPos);
    std::string part2 = msg.substr(splitPos);

    sm->beginTextStream();
    sm->receiveTextChunk(part1);
    sm->receiveTextChunk(part2);
    sm->endTextStream();
    EXPECT_NE(sm->findSurface("edge_split_v"), nullptr);
}

TEST_F(StreamEdgeCaseTest, SplitAtSurfaceId_Parses) {
    std::string surfaceId = "edge_split_sid";
    std::string msg = makeCreateSurface(surfaceId);
    // Split in the middle of the surfaceId
    size_t pos = msg.find(surfaceId);
    size_t mid = pos + surfaceId.length() / 2;
    std::string part1 = msg.substr(0, mid);
    std::string part2 = msg.substr(mid);

    sm->beginTextStream();
    sm->receiveTextChunk(part1);
    sm->receiveTextChunk(part2);
    sm->endTextStream();
    EXPECT_NE(sm->findSurface(surfaceId), nullptr);
}

// =============================================================================
// Repeated begin/end stream calls
// =============================================================================

TEST_F(StreamEdgeCaseTest, DoubleBegin_NoCrash) {
    sm->beginTextStream();
    sm->beginTextStream(); // second begin
    sm->receiveTextChunk(makeCreateSurface("edge_dbl_begin"));
    sm->endTextStream();
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, EndWithoutBegin_NoCrash) {
    sm->endTextStream(); // end without begin
    SUCCEED();
}

TEST_F(StreamEdgeCaseTest, DoubleEnd_NoCrash) {
    sm->beginTextStream();
    sm->receiveTextChunk(makeCreateSurface("edge_dbl_end"));
    sm->endTextStream();
    sm->endTextStream(); // double end
    SUCCEED();
}
