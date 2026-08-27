#include "agenui_streaming_content_parser.h"
#include "agenui_logger_internal.h"
#include <cstring>
#include "nlohmann/json.hpp"
#include "module/agenui_thread_manager.h"
#include "surface/agenui_surface_coordinator.h"

namespace agenui {

    StreamingContentParser::StreamingContentParser(SurfaceCoordinator* coordinator)
        : _coordinator(coordinator) {
        _markdownPlugin = std::unique_ptr<MarkdownStreamPlugin>(new MarkdownStreamPlugin());
        _textPlugin = std::unique_ptr<TextStreamPlugin>(new TextStreamPlugin());
        _compositePlugin = std::unique_ptr<CompositeStreamPlugin>(new CompositeStreamPlugin());
        _compositePlugin->addPlugin(_markdownPlugin.get());
        _compositePlugin->addPlugin(_textPlugin.get());
        _extractor.setPlugin(_compositePlugin.get());
    }

    StreamingContentParser::~StreamingContentParser() {
        stop();
    }

    bool StreamingContentParser::start() {
        return true;
    }

    void StreamingContentParser::stop() {
    }

    void StreamingContentParser::setQueryContent(const std::string &content) {
        _queryContent = content;
    }

    void StreamingContentParser::processDataBeginning() {
        AGENUI_LOG("processing begin");
        resetState();

        AGENUI_PERFORMANCE_LOG("stream_begin", "");
    }

    void StreamingContentParser::processDataAssembling(const std::string& data) {
        AGENUI_PERFORMANCE_LOG("stream_assembling_begin", "");
        AGENUI_LOG("%s", data.c_str());
        _extractor.appendData(data);
        auto results = _extractor.driveParser();

        // Cross-chunk coalescing: try to merge with pending results from
        // the previous chunk if within the 16ms frame window.
        tryCrossChunkCoalesce(results);

        dispatchParseResultsBatched(results);

        // Record timestamp for the next chunk's coalescing decision.
        _lastChunkTime = std::chrono::steady_clock::now();

        AGENUI_PERFORMANCE_LOG("stream_assembling_end", "");
    }

    void StreamingContentParser::processDataEnding() {
        AGENUI_LOG("processing end");
        // Flush any pending coalesced updates before resetting.
        flushPendingUpdates();
        resetState();

        AGENUI_PERFORMANCE_LOG("stream_end", "");
    }

    void StreamingContentParser::tryCrossChunkCoalesce(
            std::vector<ProtocolStreamExtractor::ParseResult>& results) {
        if (_pendingUpdates.empty()) {
            return;
        }

        // Check if we're still within the coalescing window.
        auto now = std::chrono::steady_clock::now();
        auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
            now - _lastChunkTime).count();

        if (elapsed > COALESCE_WINDOW_MS) {
            // Window expired — flush pending and don't coalesce.
            flushPendingUpdates();
            return;
        }

        // Check if current results start with ComponentUpdate for the same surfaceId.
        if (results.empty()) {
            return;
        }

        const auto& first = results[0];
        if (first.type != ProtocolStreamExtractor::ParseResult::Type::ComponentUpdate) {
            // NormalEvent interrupts — flush pending first.
            flushPendingUpdates();
            return;
        }

        if (first.surfaceId != _pendingSurfaceId) {
            // Different surfaceId — flush pending.
            flushPendingUpdates();
            return;
        }

        // Merge: prepend pending updates to the current results vector.
        // The pending results are all ComponentUpdate for the same surfaceId.
        // After merge, dispatchParseResultsBatched will see them as contiguous
        // and batch them into one updateComponents call.
        results.insert(results.begin(), _pendingUpdates.begin(), _pendingUpdates.end());
        _pendingUpdates.clear();
        _pendingSurfaceId.clear();
    }

    void StreamingContentParser::flushPendingUpdates() {
        if (_pendingUpdates.empty()) {
            return;
        }
        // Move pending to a local variable to avoid re-buffering inside dispatchParseResultsBatched.
        // dispatchParseResultsBatched would see the single contiguous run as "isLastRun" and
        // re-buffer it into _pendingUpdates instead of dispatching — creating an infinite loop
        // where flushPendingUpdates re-invokes dispatchParseResultsBatched which re-buffers.
        // By moving the data out first, dispatchParseResultsBatched's isLastRun branch will
        // still buffer into _pendingUpdates, but that's fine because we clear it right after.
        auto pending = std::move(_pendingUpdates);
        _pendingSurfaceId.clear();
        dispatchParseResultsBatched(pending);
        // After dispatch, if dispatchParseResultsBatched re-buffered into _pendingUpdates
        // (because it was isLastRun), we need to force-dispatch those now.
        if (!_pendingUpdates.empty()) {
            pending = std::move(_pendingUpdates);
            _pendingSurfaceId.clear();
            // Force dispatch: call sendSingleComponentUpdate / sendBatchedComponentUpdate directly.
            size_t cursor = 0;
            const size_t count = pending.size();
            while (cursor < count) {
                size_t batchEnd = cursor + 1;
                while (batchEnd < count && pending[batchEnd].surfaceId == pending[cursor].surfaceId) {
                    ++batchEnd;
                }
                if (batchEnd - cursor == 1) {
                    sendSingleComponentUpdate(pending[cursor].componentJson,
                                              pending[cursor].surfaceId,
                                              pending[cursor].version);
                } else {
                    sendBatchedComponentUpdate(pending, cursor, batchEnd);
                }
                cursor = batchEnd;
            }
        }
    }

    void StreamingContentParser::dispatchParseResultsBatched(const std::vector<ProtocolStreamExtractor::ParseResult>& results) {
        size_t resultCursor = 0;
        const size_t resultCount = results.size();
        while (resultCursor < resultCount) {
            const auto& head = results[resultCursor];
            if (head.type == ProtocolStreamExtractor::ParseResult::Type::NormalEvent) {
                // NormalEvent flushes any pending coalesced updates first.
                flushPendingUpdates();
                processNormalEvent(head);
                ++resultCursor;
                continue;
            }
            // Collect contiguous ComponentUpdate results with the same surfaceId.
            size_t batchIndex = resultCursor + 1;
            while (batchIndex < resultCount) {
                const auto& cur = results[batchIndex];
                if (cur.type != ProtocolStreamExtractor::ParseResult::Type::ComponentUpdate) {
                    break;
                }
                if (cur.surfaceId != head.surfaceId) {
                    break;
                }
                ++batchIndex;
            }

            // Check if the tail of this batch is the last group in results.
            // If so, buffer them as pending for potential cross-chunk coalescing
            // instead of dispatching immediately. But only if there are more
            // than 1 result in the contiguous run (otherwise fast path is fine
            // to dispatch immediately).
            //
            // Actually, for cross-chunk coalescing to work, we need to hold
            // back the LAST contiguous run of ComponentUpdate results if they're
            // at the end of the current chunk. The next chunk's tryCrossChunkCoalesce
            // will then merge them with the next chunk's leading ComponentUpdates.
            bool isLastRun = (batchIndex >= resultCount);
            size_t runSize = batchIndex - resultCursor;

            if (isLastRun && runSize > 0) {
                // Buffer the last run as pending for cross-chunk coalescing.
                // But if there's only this run (resultCursor == 0), still buffer
                // it — the next chunk or endTextStream will flush it.
                _pendingUpdates.assign(
                    results.begin() + resultCursor,
                    results.begin() + batchIndex);
                _pendingSurfaceId = head.surfaceId;
                resultCursor = batchIndex;
                continue;
            }

            if (runSize == 1) {
                const auto& singleContent = results[resultCursor];
                sendSingleComponentUpdate(singleContent.componentJson, singleContent.surfaceId, singleContent.version);
            } else {
                sendBatchedComponentUpdate(results, resultCursor, batchIndex);
            }
            resultCursor = batchIndex;
        }
    }

    void StreamingContentParser::sendBatchedComponentUpdate(
        const std::vector<ProtocolStreamExtractor::ParseResult>& results,
        size_t start, size_t end) {
        if (!_coordinator || start >= end) {
            return;
        }
        const auto& first = results[start];
        std::string updateJson;
        size_t reserveBytes = 64 + first.surfaceId.size() + first.version.size();
        for (size_t cursor = start; cursor < end; ++cursor) {
            reserveBytes += results[cursor].componentJson.size() + 2;
        }
        updateJson.reserve(reserveBytes);
        updateJson += "{";
        if (!first.version.empty()) {
            updateJson += "\"version\":\"";
            updateJson += first.version;
            updateJson += "\",";
        }
        updateJson += "\"updateComponents\":{\"surfaceId\":\"";
        updateJson += first.surfaceId;
        updateJson += "\",\"components\":[";
        for (size_t k = start; k < end; ++k) {
            if (k > start) updateJson += ",";
            updateJson += results[k].componentJson;
        }
        updateJson += "]}}";
        AGenUIExeCode ret = _coordinator->updateComponents(updateJson);
        if (ret != Execute_Success) {
            AGENUI_LOG("ret:%s, batch:%zu", getExeCodeString(ret).c_str(), end - start);
        }
    }

    void StreamingContentParser::processNormalEvent(const ProtocolStreamExtractor::ParseResult& result) {
        if (!_coordinator) {
            return;
        }

        AGenUIExeCode ret = Execute_Success;
        const std::string& data = result.eventJson;
        if (result.eventType == ProtocolStreamExtractor::EventType::CreateSurface) {
            ret = _coordinator->createSurface(data);
        } else if (result.eventType == ProtocolStreamExtractor::EventType::UpdateDataModel) {
            ret = _coordinator->updateDataModel(data);
        } else if (result.eventType == ProtocolStreamExtractor::EventType::AppendDataModel) {
            ret = _coordinator->appendDataModel(data);
        } else if (result.eventType == ProtocolStreamExtractor::EventType::DeleteSurface) {
            ret = _coordinator->deleteSurface(data);
        }
        if (ret != Execute_Success) {
            AGENUI_LOG("ret:%s, type:%d, data:%s", getExeCodeString(ret).c_str(), result.eventType, data.c_str());
        }
    }

    void StreamingContentParser::sendSingleComponentUpdate(const std::string& componentJson, const std::string& surfaceId, const std::string& version) {
        if (!_coordinator) {
            return;
        }

        std::string updateJson;
        updateJson.reserve(64 + surfaceId.size() + version.size() + componentJson.size());
        updateJson += "{";
        if (!version.empty()) {
            updateJson += "\"version\":\"";
            updateJson += version;
            updateJson += "\",";
        }
        updateJson += "\"updateComponents\":{\"surfaceId\":\"";
        updateJson += surfaceId;
        updateJson += "\",\"components\":[";
        updateJson += componentJson;
        updateJson += "]}}";
        AGenUIExeCode ret = _coordinator->updateComponents(updateJson);
        if (ret != Execute_Success) {
            AGENUI_LOG("ret:%s, data:%s", getExeCodeString(ret).c_str(), updateJson.c_str());
        }
    }

    void StreamingContentParser::resetState() {
        _extractor.reset();
    }

} // namespace agenui
