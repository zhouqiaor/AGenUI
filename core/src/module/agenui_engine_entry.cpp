#include "agenui_engine_entry.h"
#include <atomic>
#include <mutex>
#include "agenui_engine_impl.h"
#include "agenui_logger_internal.h"
#include "agenui_type_define.h"

namespace agenui {

// Global engine instance, protected by mutex to ensure thread-safe initialization
// Uses mutex+flag instead of std::call_once to allow re-initialization after destroy.
static std::mutex g_initMutex;
static bool g_initialized = false;
static std::atomic<AGenUIEngine*> g_agenUIEngine(nullptr);

IAGenUIEngine* initAGenUIEngine() {
    std::lock_guard<std::mutex> lock(g_initMutex);
    if (!g_initialized) {
        auto* engine = new AGenUIEngine();
        engine->start();
        g_agenUIEngine = engine;
        g_initialized = true;
        AGENUI_LOG("engine:%p", engine);
    }
    return g_agenUIEngine;
}

IAGenUIEngine* getAGenUIEngine() {
    return g_agenUIEngine;
}

void destroyAGenUIEngine() {
    std::lock_guard<std::mutex> lock(g_initMutex);
    auto* engine = g_agenUIEngine.exchange(nullptr);
    AGENUI_LOG("engine:%p", engine);
    if (engine) {
        engine->stop();
        SAFELY_DELETE(engine);
    }
    g_initialized = false;
}

} // namespace agenui
