#pragma once

// AGenUI Windows DLL export entry points
// Mirrors core/include/agenui_engine_entry.h but with __declspec(dllexport)

#include "agenui_engine.h"
#include "agenui_engine_entry.h"

#ifdef AGENUI_WINDOWS_EXPORTS
#define AGENUI_WIN_API __declspec(dllexport)
#else
#define AGENUI_WIN_API __declspec(dllimport)
#endif

extern "C" {

AGENUI_WIN_API agenui::IAGenUIEngine* agenuiInit();
AGENUI_WIN_API agenui::IAGenUIEngine* agenuiGetEngine();
AGENUI_WIN_API void agenuiDestroy();

AGENUI_WIN_API const char* agenuiGetVersion();

}
