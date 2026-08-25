// AGenUI Windows DLL entry — thin wrapper that delegates to the C++ core's
// initAGenUIEngine / getAGenUIEngine / destroyAGenUIEngine, and registers
// the Windows-specific IMeasurement + IPlatformFunction implementations.

#include "agenui_windows_entry.h"
#include "agenui_engine_entry.h"
#include "agenui_measurement.h"
#include "agenui_platform_function.h"
#include "agenui_surface_size_provider.h"

#include <string>

namespace {
    bool g_initialized = false;
}

extern "C" {

AGENUI_WIN_API agenui::IAGenUIEngine* agenuiInit() {
    if (g_initialized) {
        return agenui::getAGenUIEngine();
    }
    auto* engine = agenui::initAGenUIEngine();
    if (engine) {
        g_initialized = true;
        // TODO Phase 1: register Windows-specific IMeasurement implementations
        //   (DirectWrite text measurement, WIC image measurement)
        // TODO Phase 1: register Windows-specific IPlatformFunction implementations
        //   (function call bridge for host integration)
    }
    return engine;
}

AGENUI_WIN_API agenui::IAGenUIEngine* agenuiGetEngine() {
    return agenui::getAGenUIEngine();
}

AGENUI_WIN_API void agenuiDestroy() {
    agenui::destroyAGenUIEngine();
    g_initialized = false;
}

AGENUI_WIN_API const char* agenuiGetVersion() {
    static const char* version = agenui::getAGenUIVersion();
    return version;
}

} // extern "C"
