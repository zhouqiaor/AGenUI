@echo off
REM ============================================================================
REM AGenUI Windows P2 Test Build Script (batch wrapper)
REM Delegates to build_tests.ps1 which sets up the MSVC environment via
REM Enter-VsDevShell, configures CMake with the Ninja generator, builds the
REM agenui_playground_tests target, and runs the tests.
REM
REM Usage:
REM   build_tests.bat            - build and run tests
REM   build_tests.bat clean      - remove build cache first
REM ============================================================================

setlocal

set "REPO=%~dp0"
set "REPO=%REPO:~0,-1%"

if /i "%~1"=="clean" (
    if exist "%REPO%\build\windows-tests" rmdir /s /q "%REPO%\build\windows-tests"
    echo [build_tests] Cleaned build cache.
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%REPO%\build_tests.ps1"
set "RC=%ERRORLEVEL%"

echo.
if "%RC%"=="0" (
    echo [build_tests] ALL TESTS PASSED.
) else (
    echo [build_tests] TEST FAILURES DETECTED ^(exit %RC%^).
)

endlocal & exit /b %RC%
