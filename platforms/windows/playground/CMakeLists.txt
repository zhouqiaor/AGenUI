cmake_minimum_required(VERSION 3.20)

# ---------------------------------------------------------------------------
# Phase 0: console smoke test (kept for reference)
# ---------------------------------------------------------------------------
add_executable(agenui_playground_test main.cpp)

get_filename_component(AGENUI_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)

target_include_directories(agenui_playground_test PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/../include"
    "${AGENUI_REPO_ROOT}/core/include"
)

target_link_libraries(agenui_playground_test PRIVATE agenui_windows)

target_compile_definitions(agenui_playground_test PRIVATE
    NOMINMAX
    WIN32_LEAN_AND_MEAN
    _CRT_SECURE_NO_WARNINGS
)

target_compile_options(agenui_playground_test PRIVATE /W3 /EHsc /utf-8)

# ---------------------------------------------------------------------------
# Phase 1: Win32 + Direct2D playground app
# ---------------------------------------------------------------------------
add_executable(agenui_playground WIN32 win32_app.cpp)

target_include_directories(agenui_playground PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/../include"
    "${AGENUI_REPO_ROOT}/core/include"
    "${AGENUI_REPO_ROOT}/core/src/third_party"
    "${AGENUI_REPO_ROOT}/core/src/third_party/nlohmann"
)

target_link_libraries(agenui_playground PRIVATE
    agenui_windows
    d2d1
    dwrite
    windowscodecs
    windowscodecs
    urlmon
    user32
    gdi32
)

target_compile_definitions(agenui_playground PRIVATE
    NOMINMAX
    WIN32_LEAN_AND_MEAN
    _CRT_SECURE_NO_WARNINGS
    UNICODE
    _UNICODE
)

target_compile_options(agenui_playground PRIVATE
    /W3 /EHsc /utf-8
    /wd4267 /wd4244 /wd4100 /wd4189
    $<$<CONFIG:Debug>:/Zi /Od>
    $<$<CONFIG:Release>:/O2 /DNDEBUG>
)

# Copy the DLL next to the exe so the playground can load it at runtime
add_custom_command(TARGET agenui_playground POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    $<TARGET_FILE:agenui_windows>
    $<TARGET_FILE_DIR:agenui_playground>
)

add_custom_command(TARGET agenui_playground_test POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    $<TARGET_FILE:agenui_windows>
    $<TARGET_FILE_DIR:agenui_playground_test>
)
