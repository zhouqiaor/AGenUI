cmake_minimum_required(VERSION 3.20)
project(AGenUI_Windows LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ---------------------------------------------------------------------------
# AGenUI repo root (this file is at platforms/windows/CMakeLists.txt)
# ---------------------------------------------------------------------------
get_filename_component(AGENUI_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../.." ABSOLUTE)
set(AGENUI_CPP_ROOT "${AGENUI_REPO_ROOT}/core")

# ---------------------------------------------------------------------------
# yoga: FetchContent from GitHub (same as Android/Harmony)
# ---------------------------------------------------------------------------
include(FetchContent)
FetchContent_Declare(yoga
    GIT_REPOSITORY https://github.com/facebook/yoga.git
    GIT_TAG        v2.0.0
    GIT_SHALLOW    TRUE
)
FetchContent_Populate(yoga)

file(GLOB_RECURSE YOGA_SOURCES "${yoga_SOURCE_DIR}/yoga/*.cpp")
add_library(yoga STATIC ${YOGA_SOURCES})
target_include_directories(yoga
    PUBLIC "${yoga_SOURCE_DIR}"
    PRIVATE
        "${yoga_SOURCE_DIR}/include"
        "${yoga_SOURCE_DIR}/include/yoga"
)
target_compile_features(yoga PRIVATE cxx_std_20)
target_compile_options(yoga PRIVATE
    /W3 /wd4267 /wd4244 /wd4100 /wd4189
    /EHsc
)

# ---------------------------------------------------------------------------
# AGenUI C++ core sources (same glob as Android/Harmony, minus JNI)
# ---------------------------------------------------------------------------
file(GLOB_RECURSE AGENUI_CORE_SOURCES
    "${AGENUI_CPP_ROOT}/src/*.cpp"
    "${AGENUI_CPP_ROOT}/src/surface/*.cpp"
    "${AGENUI_CPP_ROOT}/src/module/*.cpp"
    "${AGENUI_CPP_ROOT}/src/stream/*.cpp"
    "${AGENUI_CPP_ROOT}/src/function_call/*.cpp"
    "${AGENUI_CPP_ROOT}/src/style_parser/*.cpp"
)

# Exclude JNI (Android-only), Harmony-only third_party
list(FILTER AGENUI_CORE_SOURCES EXCLUDE REGEX ".*/jni/.*")
list(FILTER AGENUI_CORE_SOURCES EXCLUDE REGEX ".*/third_party/Html.cpp")
list(FILTER AGENUI_CORE_SOURCES EXCLUDE REGEX ".*/third_party/sax/.*")
list(FILTER AGENUI_CORE_SOURCES EXCLUDE REGEX ".*/third_party/ik/.*")
list(FILTER AGENUI_CORE_SOURCES EXCLUDE REGEX ".*/third_party/key_define.cpp")

# ---------------------------------------------------------------------------
# Windows platform SDK sources
# ---------------------------------------------------------------------------
file(GLOB_RECURSE WIN_PLATFORM_SOURCES
    "${CMAKE_CURRENT_SOURCE_DIR}/src/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/components/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/measure/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/dll/*.cpp"
)

# ---------------------------------------------------------------------------
# Include directories
# ---------------------------------------------------------------------------
set(AGENUI_INCLUDE_DIRS
    "${AGENUI_CPP_ROOT}/include"
    "${AGENUI_CPP_ROOT}/src"
    "${AGENUI_CPP_ROOT}/src/third_party"
    "${AGENUI_CPP_ROOT}/src/third_party/nlohmann"
    "${CMAKE_CURRENT_SOURCE_DIR}/include"
    "${yoga_SOURCE_DIR}"
)

# ---------------------------------------------------------------------------
# DLL export (agenui_core.dll)
# ---------------------------------------------------------------------------
add_library(agenui_windows SHARED
    ${AGENUI_CORE_SOURCES}
    ${WIN_PLATFORM_SOURCES}
)

target_include_directories(agenui_windows PRIVATE ${AGENUI_INCLUDE_DIRS})
target_link_libraries(agenui_windows PRIVATE yoga)

target_compile_definitions(agenui_windows PRIVATE
    AGENUI_WINDOWS
    AGENUI_WINDOWS_EXPORTS
    NOMINMAX
    WIN32_LEAN_AND_MEAN
    _CRT_SECURE_NO_WARNINGS
    UNICODE
    _UNICODE
)

target_compile_options(agenui_windows PRIVATE
    /W3 /EHsc /utf-8
    /wd4267 /wd4244 /wd4100 /wd4189 /wd4530
    $<$<CONFIG:Debug>:/Zi /Od>
    $<$<CONFIG:Release>:/O2 /DNDEBUG>
)

# ---------------------------------------------------------------------------
# Playground (optional, Win32 console app for smoke testing)
# ---------------------------------------------------------------------------
option(AGENUI_BUILD_PLAYGROUND "Build the Win32 playground app" ON)
if(AGENUI_BUILD_PLAYGROUND)
    add_subdirectory(playground)
endif()
