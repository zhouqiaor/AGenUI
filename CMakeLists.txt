cmake_minimum_required(VERSION 3.20)

project(AGenUI LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# Platform selection options
# ---------------------------------------------------------------------------
option(AGENUI_BUILD_ANDROID "Build AGenUI for Android (Gradle-based, not CMake)" OFF)
option(AGENUI_BUILD_HARMONY "Build AGenUI for HarmonyOS (Gradle-based, not CMake)" OFF)
option(AGENUI_BUILD_IOS      "Build AGenUI for iOS (Xcode-based, not CMake)" OFF)
option(AGENUI_BUILD_WINDOWS "Build AGenUI for Windows (Direct2D/DirectWrite/WIC)" OFF)

# ---------------------------------------------------------------------------
# Windows platform (CMake-native, MSVC)
#
# The Windows platform is the only CMake-native build target. Android uses
# Gradle, HarmonyOS uses DevEco/Gradle, iOS uses Xcode. This root CMakeLists
# serves as the unified entry point so CI can invoke:
#
#   cmake -B build -DAGENUI_BUILD_WINDOWS=ON
#   cmake --build build --config Release
#   ctest --test-dir build --build-config Release
#
# On non-Windows platforms, simply omit -DAGENUI_BUILD_WINDOWS=ON.
# ---------------------------------------------------------------------------
if(AGENUI_BUILD_WINDOWS)
    if(NOT WIN32)
        message(FATAL_ERROR "AGenUI Windows platform requires a Windows host with MSVC.")
    endif()
    add_subdirectory(platforms/windows)
endif()

# ---------------------------------------------------------------------------
# Print configuration summary
# ---------------------------------------------------------------------------
message(STATUS "")
message(STATUS "=== AGenUI Build Configuration ===")
message(STATUS "  Android:  ${AGENUI_BUILD_ANDROID}")
message(STATUS "  Harmony:  ${AGENUI_BUILD_HARMONY}")
message(STATUS "  iOS:      ${AGENUI_BUILD_IOS}")
message(STATUS "  Windows:  ${AGENUI_BUILD_WINDOWS}")
message(STATUS "  C++ Std:  ${CMAKE_CXX_STANDARD}")
message(STATUS "================================")
message(STATUS "")
