// AGenUI Windows Playground — minimal Win32 console app
// Phase 0: verify that agenui_windows.dll loads and the C++ core initializes.

#include "agenui_windows_entry.h"
#include "agenui_surface_manager_interface.h"

#include <cstdio>
#include <cstdlib>

int main() {
    printf("[AGenUI Windows Playground] Phase 0 smoke test\n");
    printf("==============================================\n");

    auto* engine = agenuiInit();
    if (!engine) {
        printf("FAIL: agenuiInit() returned nullptr\n");
        return 1;
    }
    printf("PASS: agenuiInit() succeeded, engine=%p\n", (void*)engine);

    auto* engine2 = agenuiGetEngine();
    if (engine != engine2) {
        printf("FAIL: agenuiGetEngine() returned different pointer\n");
        return 1;
    }
    printf("PASS: agenuiGetEngine() returned same singleton\n");

    const char* version = agenuiGetVersion();
    printf("PASS: agenuiGetVersion() = \"%s\"\n", version ? version : "(null)");

    // Create a SurfaceManager to verify the full engine chain
    auto* surfaceMgr = engine->createSurfaceManager();
    if (!surfaceMgr) {
        printf("FAIL: createSurfaceManager() returned nullptr\n");
        return 1;
    }
    printf("PASS: createSurfaceManager() succeeded, instanceId=%d\n",
           surfaceMgr->getInstanceId());

    // Begin/end a text stream (no data, just lifecycle)
    surfaceMgr->beginTextStream();
    surfaceMgr->endTextStream();
    printf("PASS: beginTextStream()/endTextStream() cycle OK\n");

    // Cleanup
    engine->destroySurfaceManager(surfaceMgr);
    printf("PASS: destroySurfaceManager() OK\n");

    agenuiDestroy();
    printf("PASS: agenuiDestroy() OK\n");

    printf("\n==============================================\n");
    printf("All Phase 0 smoke tests PASSED\n");
    return 0;
}
