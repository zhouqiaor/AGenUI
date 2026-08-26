cmake_minimum_required(VERSION 3.20)

# ---------------------------------------------------------------------------
# AGenUI Windows P2 Test Suite
# Unit + component parse tests using Google Test + ApprovalTests.cpp
# Target: agenui_playground_tests
# ---------------------------------------------------------------------------

get_filename_component(AGENUI_REPO_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../.." ABSOLUTE)
set(AGENUI_CPP_ROOT "${AGENUI_REPO_ROOT}/core")

include(FetchContent)

# ---- Google Test v1.14.0 ----------------------------------------------------
FetchContent_Declare(googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG        v1.14.0
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(googletest)

# ---- ApprovalTests.cpp v.10.13.0 (single header) ----------------------------
# Download the single-header release so we don't clone the whole repo.
# NOTE: the tag uses a dot: v.10.13.0 and the asset includes the version.
set(APPROVAL_TESTS_URL "https://github.com/approvals/ApprovalTests.cpp/releases/download/v.10.13.0/ApprovalTests.v.10.13.0.hpp")
set(APPROVAL_TESTS_FILE "${CMAKE_CURRENT_BINARY_DIR}/ApprovalTests.hpp")

if(NOT EXISTS "${APPROVAL_TESTS_FILE}")
    message(STATUS "Downloading ApprovalTests.hpp v.10.13.0 ...")
    file(DOWNLOAD
        "${APPROVAL_TESTS_URL}"
        "${APPROVAL_TESTS_FILE}"
        STATUS DOWNLOAD_STATUS
        SHOW_PROGRESS
    )
    list(GET DOWNLOAD_STATUS 0 STATUS_CODE)
    if(NOT STATUS_CODE EQUAL 0)
        message(WARNING "ApprovalTests.hpp download failed (status ${DOWNLOAD_STATUS}); "
                         "tests requiring it will fail to compile. "
                         "You can manually download from:\n  ${APPROVAL_TESTS_URL}\n"
                         "and place it at: ${APPROVAL_TESTS_FILE}")
    else()
        message(STATUS "ApprovalTests.hpp downloaded OK")
    endif()
endif()

# ---------------------------------------------------------------------------
# Test executable
# ---------------------------------------------------------------------------
add_executable(agenui_playground_tests
    test_unit.cpp
    test_listener.cpp
    test_integration.cpp
    test_visual.cpp
    test_golden.cpp
)

target_include_directories(agenui_playground_tests PRIVATE
    "${CMAKE_CURRENT_SOURCE_DIR}/../include"
    "${AGENUI_CPP_ROOT}/include"
    "${AGENUI_CPP_ROOT}/src"
    "${AGENUI_CPP_ROOT}/src/third_party"
    "${AGENUI_CPP_ROOT}/src/third_party/nlohmann"
    "${CMAKE_CURRENT_BINARY_DIR}"  # ApprovalTests.hpp
)

target_link_libraries(agenui_playground_tests PRIVATE
    agenui_windows
    GTest::gtest
    d2d1
    dwrite
    windowscodecs
    user32
    gdi32
)

target_compile_definitions(agenui_playground_tests PRIVATE
    NOMINMAX
    WIN32_LEAN_AND_MEAN
    _CRT_SECURE_NO_WARNINGS
    UNICODE
    _UNICODE
)

target_compile_options(agenui_playground_tests PRIVATE
    /W3 /EHsc /utf-8
    /wd4267 /wd4244 /wd4100 /wd4189
    $<$<CONFIG:Debug>:/Zi /Od>
    $<$<CONFIG:Release>:/O2 /DNDEBUG>
)

# Copy the AGenUI DLL next to the test exe so it can load at runtime
add_custom_command(TARGET agenui_playground_tests POST_BUILD
    COMMAND ${CMAKE_COMMAND} -E copy_if_different
    $<TARGET_FILE:agenui_windows>
    $<TARGET_FILE_DIR:agenui_playground_tests>
)

# Register with CTest
include(GoogleTest)
gtest_discover_tests(agenui_playground_tests)
