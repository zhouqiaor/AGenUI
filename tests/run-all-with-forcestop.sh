#!/bin/bash
# Full test run with am force-stop between classes to avoid SIGKILL misjudgment
# Device: 200.49.0.251:5555
# App: com.amap.agenuiplayground
# Test runner: androidx.test.runner.AndroidJUnitRunner

DEVICE="200.49.0.251:5555"
APP_PKG="com.amap.agenuiplayground"
TEST_PKG="com.amap.agenuiplayground.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
RESULT_DIR="C:/Code/AGenUI-p2-test-v3/test_results_rerun"
mkdir -p "$RESULT_DIR"

# All 50 test classes (excluding base/helper classes)
CLASSES=(
  "A2UIPlaygroundYogaSmokeTest"
  "ComponentRenderTest"
  "FunctionCallTest"
  "InitializationTest"
  "MultiSurfaceTest"
  "PlatformFunctionTest"
  "SDKRiskProbeCombinedStressTest"
  "SDKRiskProbeConcurrentCoordinatorTest"
  "SDKRiskProbeConcurrentDestroyBridgeTest"
  "SDKRiskProbeConfigApiStackOverflowTest"
  "SDKRiskProbeConfigDestroyRaceTest"
  "SDKRiskProbeDeepComponentTreeTest"
  "SDKRiskProbeDeepJsonCrashTest"
  "SDKRiskProbeEngineDestroyRaceTest"
  "SDKRiskProbeEngineDestroyUAFTest"
  "SDKRiskProbeEngineLifecycleStressTest"
  "SDKRiskProbeEngineReinitFailureTest"
  "SDKRiskProbeEngineSelfJoinCrashTest"
  "SDKRiskProbeExtendedLifecycleTest"
  "SDKRiskProbeExtremeStyleValuesTest"
  "SDKRiskProbeFormatNumberOOMCrashTest"
  "SDKRiskProbeFuncRegUnregRaceTest"
  "SDKRiskProbeFuncRegisterRaceTest"
  "SDKRiskProbeFunctionUnregisterRaceTest"
  "SDKRiskProbeInitCrashTest"
  "SDKRiskProbeJniBridgeRaceTest"
  "SDKRiskProbeJsonTypeMismatchTest"
  "SDKRiskProbeListenerSelfUnregDeadlockTest"
  "SDKRiskProbeMultiSMFloodTest"
  "SDKRiskProbeNativeMemoryLeakTest"
  "SDKRiskProbeProtocolFuzzTest"
  "SDKRiskProbeRawIdTypeMismatchTest"
  "SDKRiskProbeReentrantDeadlockTest"
  "SDKRiskProbeSMDestroyRaceTest"
  "SDKRiskProbeStreamDestroyRaceTest"
  "SDKRiskProbeStreamPluginSurfaceIdCrashTest"
  "SDKRiskProbeSurfaceSizeProviderDeadlockTest"
  "SDKRiskProbeTextChunkStylesPathTypeMismatchTest"
  "SDKRiskProbeTextChunkTypeMismatchTest"
  "SDKRiskProbeWidthAndPayloadTest"
  "StreamTest"
  "SurfaceLifecycleTest"
  "WidgetDegradationTest"
  "WidgetE2ETest"
  "WidgetLLMConfigTest"
  "WidgetLogicTest"
  "WidgetPartialParserTest"
  "WidgetRenderTest"
  "WidgetScreenshotTest"
  "WidgetValidatorTest"
)

TOTAL=${#CLASSES[@]}
PASS_COUNT=0
FAIL_COUNT=0
SKIP_COUNT=0
CRASH_COUNT=0
ERROR_COUNT=0
CURRENT=0

echo "=========================================="
echo "AGenUI Full Test Rerun with force-stop"
echo "Device: $DEVICE"
echo "Classes: $TOTAL"
echo "Started: $(date)"
echo "=========================================="

# Summary file
SUMMARY="$RESULT_DIR/SUMMARY.txt"
echo "AGenUI Full Test Rerun - $(date)" > "$SUMMARY"
echo "==========================================" >> "$SUMMARY"

for CLASS in "${CLASSES[@]}"; do
  CURRENT=$((CURRENT + 1))
  FQCN="com.amap.agenuiplayground.tests.$CLASS"
  OUTPUT_FILE="$RESULT_DIR/$CLASS.txt"
  
  echo ""
  echo "[$CURRENT/$TOTAL] Running $CLASS..."
  
  # Force stop before each class to ensure clean process state
  adb -s "$DEVICE" shell am force-stop "$APP_PKG" 2>/dev/null
  sleep 1
  
  # Run the test class
  START_TIME=$(date +%s)
  adb -s "$DEVICE" shell am instrument -w -r -e class "$FQCN" "$TEST_PKG/$RUNNER" 2>&1 | tee "$OUTPUT_FILE"
  END_TIME=$(date +%s)
  DURATION=$((END_TIME - START_TIME))
  
  # Parse results from output
  # JUnit output: "Tests: X, Failures: Y, Errors: Z, Skipped: W"
  RESULT_LINE=$(grep -E "^Tests:" "$OUTPUT_FILE" | tail -1)
  
  if [ -z "$RESULT_LINE" ]; then
    # Check for process crash
    if grep -q "Process crashed" "$OUTPUT_FILE"; then
      STATUS="CRASH"
      CRASH_COUNT=$((CRASH_COUNT + 1))
    else
      STATUS="ERROR"
      ERROR_COUNT=$((ERROR_COUNT + 1))
    fi
    echo "  -> $STATUS (${DURATION}s): no test result line"
    echo "[$CURRENT/$TOTAL] $CLASS: $STATUS (${DURATION}s)" >> "$SUMMARY"
  else
    # Parse test counts
    TESTS=$(echo "$RESULT_LINE" | grep -oP 'Tests: \K\d+')
    FAILURES=$(echo "$RESULT_LINE" | grep -oP 'Failures: \K\d+')
    ERRORS=$(echo "$RESULT_LINE" | grep -oP 'Errors: \K\d+')
    SKIPPED=$(echo "$RESULT_LINE" | grep -oP 'Skipped: \K\d+')
    
    # Handle missing fields
    TESTS=${TESTS:-0}
    FAILURES=${FAILURES:-0}
    ERRORS=${ERRORS:-0}
    SKIPPED=${SKIPPED:-0}
    
    PASSED=$((TESTS - FAILURES - ERRORS - SKIPPED))
    
    PASS_COUNT=$((PASS_COUNT + PASSED))
    FAIL_COUNT=$((FAIL_COUNT + FAILURES))
    SKIP_COUNT=$((SKIP_COUNT + SKIPPED))
    ERROR_COUNT=$((ERROR_COUNT + ERRORS))
    
    if [ "$FAILURES" -gt 0 ] || [ "$ERRORS" -gt 0 ]; then
      STATUS="FAIL"
    else
      STATUS="PASS"
    fi
    
    echo "  -> $STATUS (${DURATION}s): $PASSED pass, $FAILURES fail, $ERRORS err, $SKIPPED skip"
    echo "[$CURRENT/$TOTAL] $CLASS: $STATUS (${DURATION}s) - P:$PASSED F:$FAILURES E:$ERRORS S:$SKIPPED" >> "$SUMMARY"
  fi
done

TOTAL_RUNS=$((PASS_COUNT + FAIL_COUNT + SKIP_COUNT + ERROR_COUNT + CRASH_COUNT))

echo ""
echo "=========================================="
echo "Full Test Rerun Complete"
echo "Finished: $(date)"
echo "=========================================="
echo "Summary:"
echo "  Classes run:  $CURRENT"
echo "  Total tests:  $TOTAL_RUNS"
echo "  Pass:         $PASS_COUNT"
echo "  Fail:         $FAIL_COUNT"
echo "  Error:        $ERROR_COUNT"
echo "  Skip:         $SKIP_COUNT"
echo "  Crash:        $CRASH_COUNT"
echo ""
echo "Pass rate: $(echo "scale=1; $PASS_COUNT * 100 / ($TOTAL_RUNS + 0.001)" | bc 2>/dev/null || echo "N/A")%"
echo "=========================================="

echo "" >> "$SUMMARY"
echo "==========================================" >> "$SUMMARY"
echo "Total classes: $CURRENT" >> "$SUMMARY"
echo "Total tests:   $TOTAL_RUNS" >> "$SUMMARY"
echo "Pass:          $PASS_COUNT" >> "$SUMMARY"
echo "Fail:          $FAIL_COUNT" >> "$SUMMARY"
echo "Error:         $ERROR_COUNT" >> "$SUMMARY"
echo "Skip:          $SKIP_COUNT" >> "$SUMMARY"
echo "Crash:         $CRASH_COUNT" >> "$SUMMARY"
echo "==========================================" >> "$SUMMARY"
