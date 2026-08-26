#pragma once

#include <string>
#include <cstdint>
#include <vector>
#include <mutex>
#include <atomic>
#include <chrono>
#include "agenui_protocol_stream_extractor.h"
#include "agenui_markdown_stream_plugin.h"
#include "agenui_text_stream_plugin.h"
#include "agenui_composite_stream_plugin.h"
#include <memory>

namespace agenui {

class SurfaceCoordinator;

/**
 * @brief Streaming content parser (formerly SessionManager)
 *
 * Handles streaming data reception, parsing, and forwarding.
 *
 * Supported operations:
 * 1. createSurface
 * 2. updateComponents (with streaming support for chunked reception)
 * 3. updateDataModel
 * 4. deleteSurface
 *
 * Cross-chunk coalescing (R34): ComponentUpdate results from consecutive
 * receiveTextChunk calls that arrive within a 16ms frame window and target
 * the same surfaceId are buffered and merged into a single
 * updateComponents dispatch, reducing redundant Yoga full-tree layouts.
 */
class StreamingContentParser {
public:
    explicit StreamingContentParser(SurfaceCoordinator* coordinator);
    ~StreamingContentParser();

    bool start();
    void stop();
    bool isRunning() const { return _isRunning.load(); }

    void setQueryContent(const std::string &content);

    void processDataBeginning();
    void processDataAssembling(const std::string& data);
    void processDataEnding();

private:
    void processNormalEvent(const ProtocolStreamExtractor::ParseResult& result);
    void sendSingleComponentUpdate(const std::string& componentJson, const std::string& surfaceId, const std::string& version);
    void sendBatchedComponentUpdate(const std::vector<ProtocolStreamExtractor::ParseResult>& results,
                                    size_t start, size_t end);
    void dispatchParseResultsBatched(const std::vector<ProtocolStreamExtractor::ParseResult>& results);
    void resetState();

    /**
     * @brief Cross-chunk coalescing: if the previous chunk produced
     *        ComponentUpdate results for a surfaceId, and the current
     *        chunk arrives within COALESCE_WINDOW_MS, merge the pending
     *        results with the current chunk's results before dispatching.
     *        Non-ComponentUpdate results (NormalEvent) always flush the
     *        pending buffer immediately.
     */
    void tryCrossChunkCoalesce(std::vector<ProtocolStreamExtractor::ParseResult>& results);

    /**
     * @brief Flush any pending buffered ComponentUpdate results.
     *        Called by processDataEnding() and when a NormalEvent
     *        interrupts a pending batch.
     */
    void flushPendingUpdates();

    SurfaceCoordinator* _coordinator = nullptr;

    ProtocolStreamExtractor _extractor;
    std::unique_ptr<MarkdownStreamPlugin> _markdownPlugin;
    std::unique_ptr<TextStreamPlugin> _textPlugin;
    std::unique_ptr<CompositeStreamPlugin> _compositePlugin;
    std::recursive_mutex _mutex;

    std::string _queryContent;
    std::string _mockServer = "C4";
    std::atomic_bool _isRunning;

    // -- Cross-chunk coalescing state --
    static constexpr int COALESCE_WINDOW_MS = 16;  // One frame at 60fps
    std::vector<ProtocolStreamExtractor::ParseResult> _pendingUpdates;
    std::string _pendingSurfaceId;
    std::chrono::steady_clock::time_point _lastChunkTime;
};

} // namespace agenui
