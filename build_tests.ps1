# AGenUI Windows P2 Test Build Script (PowerShell)
# Sets up MSVC env via Launch-VsDevShell.ps1, then configures + builds + runs tests.

$ErrorActionPreference = "Continue"
$repoRoot = "C:\Code\AGenUI-p2-test"
$buildDir = Join-Path $repoRoot "build\windows-tests"
$winSrc   = Join-Path $repoRoot "platforms\windows"

# --- Locate VS DevShell ---
$vswhere = "${env:ProgramFiles(x86)}\Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) {
    $vswhere = "$env:ProgramFiles\Microsoft Visual Studio\Installer\vswhere.exe"
}
$vsInstall = & $vswhere -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
$vsMajor   = (& $vswhere -latest -property catalog_productLineVersion).Trim()
if (-not $vsMajor) { $vsMajor = "17" }
Write-Host "[build] VSInstall: $vsInstall"
Write-Host "[build] VSMajor:   $vsMajor"

$devShell = Join-Path $vsInstall "Common7\Tools\Launch-VsDevShell.ps1"
if (-not (Test-Path $devShell)) {
    Write-Error "Launch-VsDevShell.ps1 not found at $devShell"
    exit 1
}

# Enter dev shell (sets up MSVC, cmake, etc. in current PS session)
Import-Module "$vsInstall\Common7\Tools\Microsoft.VisualStudio.DevShell.dll"
Enter-VsDevShell -VsInstallPath $vsInstall -Arch amd64 -HostArch amd64 -SkipAutomaticLocation | Out-Null
Write-Host "[build] MSVC environment ready."

# --- CMake + Ninja (prefer bundled, fall back to PATH) ---
$cmake = Join-Path $vsInstall "Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
if (-not (Test-Path $cmake)) { $cmake = "cmake" }
$ninja = Join-Path $vsInstall "Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja\ninja.exe"
if (Test-Path $ninja) { $env:PATH = "$(Split-Path $ninja);$env:PATH" }
Write-Host "[build] CMake:     $cmake"
Write-Host "[build] Source:   $winSrc"
Write-Host "[build] BuildDir: $buildDir"
Write-Host ""

Write-Host "[build] Generator: Ninja"
Write-Host ""

# --- Configure (Ninja avoids MSBuild VCTargetsPath crash) ---
& $cmake -S $winSrc -B $buildDir -G "Ninja" `
    -DCMAKE_BUILD_TYPE=Release `
    -DAGENUI_BUILD_PLAYGROUND=OFF `
    -DAGENUI_BUILD_TESTS=ON
if ($LASTEXITCODE -ne 0) { Write-Error "CMake configure failed."; exit 1 }

Write-Host ""
Write-Host "[build] Configure OK. Building agenui_playground_tests..."

# --- Build ---
& $cmake --build $buildDir --target agenui_playground_tests
if ($LASTEXITCODE -ne 0) { Write-Error "Build failed."; exit 1 }

Write-Host ""
Write-Host "[build] Build OK. Running tests..."

# --- Run tests ---
# Ninja places the exe in the tests/ subdirectory; the custom post-build
# copy step also places a copy at the build root. Check both.
$testExe = Join-Path $buildDir "tests\agenui_playground_tests.exe"
if (-not (Test-Path $testExe)) { $testExe = Join-Path $buildDir "agenui_playground_tests.exe" }
& $testExe --gtest_brief=1
$rc = $LASTEXITCODE

Write-Host ""
if ($rc -eq 0) {
    Write-Host "[build] ALL TESTS PASSED." -ForegroundColor Green
} else {
    Write-Host "[build] TEST FAILURES DETECTED (exit $rc)." -ForegroundColor Red
}

exit $rc
