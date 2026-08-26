#!/bin/bash
# Full test run — separated approach:
#   Phase 1: All non-destructive classes in ONE instrumentation call (fresh process)
#   Phase 2: Each destructive class in a SEPARATE instrumentation call (fresh process each)
#
# Destructive classes call AGenUI.destroy() in test methods, corrupting engine state
# (std::call_once prevents C++ re-init). Each must run in its own process to get a
# fresh call_once flag.
#
# Device: 200.49.0.251:5555

DEVICE="200.49.0.251:5555"
APP_PKG="com.amap.agenuiplayground"
TEST_PKG="com.amap.agenuiplayground.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
RESULT_DIR="C:/Code/AGenUI-p2-test-v3/test_results_separated"
mkdir -p "$RESULT_DIR"

# ---- Phase 1: Safe classes (single instrumentation call) ----
# Excludes: destructive classes (call destroy()) + crash-prone risk probes
SAFE_CLASSES=(
  "com.amap.agenuiplayground.tests.ComponentRenderTest"
  "com.amap.agenuiplayground.tests.FunctionCallTest"
  "com.amap.agenuiplayground.tests.InitializationTest"
  "com.amap.agenuiplayground.tests.MultiSurfaceTest"
  "com.amap.agenuiplayground.tests.PlatformFunctionTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeCombinedStressTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeConcurrentCoordinatorTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeConcurrentDestroyBridgeTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeConfigApiStackOverflowTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeDeepJsonCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeExtendedLifecycleTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeExtremeStyleValuesTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeFormatNumberOOMCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeFuncRegUnregRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeFuncRegisterRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeInitCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeListenerSelfUnregDeadlockTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeMultiSMFloodTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeNativeMemoryLeakTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeProtocolFuzzTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeRawIdTypeMismatchTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeReentrantDeadlockTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeSMDestroyRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeStreamDestroyRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeStreamPluginSurfaceIdCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeSurfaceSizeProviderDeadlockTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeTextChunkStylesPathTypeMismatchTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeTextChunkTypeMismatchTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeWidthAndPayloadTest"
  "com.amap.agenuiplayground.tests.StreamTest"
  "com.amap.agenuiplayground.tests.SurfaceLifecycleTest"
  "com.amap.agenuiplayground.tests.WidgetDegradationTest"
  "com.amap.agenuiplayground.tests.WidgetE2ETest"
  "com.amap.agenuiplayground.tests.WidgetLLMConfigTest"
  "com.amap.agenuiplayground.tests.WidgetLogicTest"
  "com.amap.agenuiplayground.tests.WidgetPartialParserTest"
  "com.amap.agenuiplayground.tests.WidgetRenderTest"
  "com.amap.agenuiplayground.tests.WidgetScreenshotTest"
  "com.amap.agenuiplayground.tests.WidgetValidatorTest"
)

# ---- Phase 2: Crash-prone + Destructive classes (each in its own process) ----
# Crash-prone: risk probes that can crash the native process (deep nesting, type mismatch, etc.)
# Destructive: call AGenUI.destroy() which corrupts engine state via std::call_once
# Both need separate processes to avoid affecting other tests.
SEPARATE_CLASSES=(
  # Crash-prone risk probes
  "com.amap.agenuiplayground.tests.SDKRiskProbeDeepComponentTreeTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeJsonTypeMismatchTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeFunctionUnregisterRaceTest"
  # Destructive (call destroy())
  "com.amap.agenuiplayground.tests.SDKRiskProbeConfigDestroyRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineDestroyRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineDestroyUAFTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineLifecycleStressTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineReinitFailureTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineSelfJoinCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeJniBridgeRaceTest"
)

# Summary tracking
TOTAL_PASS=0
TOTAL_FAIL=0
TOTAL_SKIP=0
TOTAL_CRASH=0
CRASHED_CLASSES=""

echo "=========================================="
echo "AGenUI Full Test Run (separated approach)"
echo "Device: $DEVICE"
echo "Phase 1: ${#SAFE_CLASSES[@]} safe classes (single process)"
echo "Phase 2: ${#SEPARATE_CLASSES[@]} crash-prone + destructive classes (separate processes)"
echo "Started: $(date)"
echo "=========================================="

# ============ Phase 1 ============
echo ""
echo ">>> PHASE 1: Non-destructive classes (single instrumentation)"
echo ""

CLASS_ARG=$(IFS=,; echo "${SAFE_CLASSES[*]}")
PHASE1_FILE="$RESULT_DIR/PHASE1_safe.txt"

adb -s "$DEVICE" shell am instrument -w -r \
  -e class "$CLASS_ARG" \
  "$TEST_PKG/$RUNNER" 2>&1 | tee "$PHASE1_FILE"

# Parse Phase 1 results (use || true to avoid double-output)
P1_PASS=$(grep -c "INSTRUMENTATION_STATUS_CODE: 0" "$PHASE1_FILE" 2>/dev/null || true)
P1_FAIL=$(grep -c "INSTRUMENTATION_STATUS_CODE: -2" "$PHASE1_FILE" 2>/dev/null || true)
P1_SKIP=$(grep -c "INSTRUMENTATION_STATUS_CODE: -3" "$PHASE1_FILE" 2>/dev/null || true)
P1_PASS=${P1_PASS:-0}
P1_FAIL=${P1_FAIL:-0}
P1_SKIP=${P1_SKIP:-0}

# Check for crash
if grep -q "Process crashed" "$PHASE1_FILE" 2>/dev/null; then
  echo "WARNING: Phase 1 process crashed! Some classes may not have completed."
  TOTAL_CRASH=$((TOTAL_CRASH + 1))
  CRASHED_CLASSES="$CRASHED_CLASSES phase1"
fi

echo ""
echo "Phase 1 Results: pass=$P1_PASS fail=$P1_FAIL skip=$P1_SKIP"
TOTAL_PASS=$((TOTAL_PASS + P1_PASS))
TOTAL_FAIL=$((TOTAL_FAIL + P1_FAIL))
TOTAL_SKIP=$((TOTAL_SKIP + P1_SKIP))

# ============ Phase 2 ============
echo ""
echo ">>> PHASE 2: Crash-prone + Destructive classes (each in separate process)"
echo ""

P2_IDX=0
for CLASS in "${SEPARATE_CLASSES[@]}"; do
  P2_IDX=$((P2_IDX + 1))
  SHORT_NAME=$(echo "$CLASS" | sed 's/.*\.//')
  FILE="$RESULT_DIR/PHASE2_${P2_IDX}_${SHORT_NAME}.txt"

  echo "  [$P2_IDX/${#SEPARATE_CLASSES[@]}] $SHORT_NAME ..."

  # Force-stop the app before each destructive test to get a fresh process
  adb -s "$DEVICE" shell am force-stop "$APP_PKG" 2>/dev/null
  sleep 1

  adb -s "$DEVICE" shell am instrument -w -r \
    -e class "$CLASS" \
    "$TEST_PKG/$RUNNER" 2>&1 | tee "$FILE"

  # Parse results (use || true to avoid double-output when grep finds 0 matches)
  D_PASS=$(grep -c "INSTRUMENTATION_STATUS_CODE: 0" "$FILE" 2>/dev/null || true)
  D_FAIL=$(grep -c "INSTRUMENTATION_STATUS_CODE: -2" "$FILE" 2>/dev/null || true)
  D_SKIP=$(grep -c "INSTRUMENTATION_STATUS_CODE: -3" "$FILE" 2>/dev/null || true)
  D_PASS=${D_PASS:-0}
  D_FAIL=${D_FAIL:-0}
  D_SKIP=${D_SKIP:-0}

  if grep -q "Process crashed" "$FILE" 2>/dev/null; then
    echo "    CRASHED (native crash or SIGKILL)"
    TOTAL_CRASH=$((TOTAL_CRASH + 1))
    CRASHED_CLASSES="$CRASHED_CLASSES $SHORT_NAME"
  fi

  echo "    Results: pass=$D_PASS fail=$D_FAIL skip=$D_SKIP"

  TOTAL_PASS=$((TOTAL_PASS + D_PASS))
  TOTAL_FAIL=$((TOTAL_FAIL + D_FAIL))
  TOTAL_SKIP=$((TOTAL_SKIP + D_SKIP))

  # force-stop between destructive tests
  adb -s "$DEVICE" shell am force-stop "$APP_PKG" 2>/dev/null
  sleep 1
done

# ============ Summary ============
echo ""
echo "=========================================="
echo "FULL RUN SUMMARY"
echo "=========================================="
echo "Total Pass:  $TOTAL_PASS"
echo "Total Fail:  $TOTAL_FAIL"
echo "Total Skip:  $TOTAL_SKIP"
echo "Crashes:     $TOTAL_CRASH"
if [ -n "$CRASHED_CLASSES" ]; then
  echo "Crashed classes:$CRASHED_CLASSES"
fi
echo "Finished: $(date)"
echo "=========================================="
echo ""
echo "Result files: $RESULT_DIR/"
