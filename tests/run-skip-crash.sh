#!/bin/bash
# Run all tests EXCEPT the known crash test (SDKRiskProbeConcurrentDestroyBridgeTest)
# Single instrumentation call, no force-stop
DEVICE="200.49.0.251:5555"
TEST_PKG="com.amap.agenuiplayground.test"
RUNNER="androidx.test.runner.AndroidJUnitRunner"
RESULT_DIR="C:/Code/AGenUI-p2-test-v3/test_results_rerun"
mkdir -p "$RESULT_DIR"

# All classes EXCEPT SDKRiskProbeConcurrentDestroyBridgeTest (known 2nd-order race)
CLASSES=(
  "com.amap.agenuiplayground.tests.ComponentRenderTest"
  "com.amap.agenuiplayground.tests.FunctionCallTest"
  "com.amap.agenuiplayground.tests.InitializationTest"
  "com.amap.agenuiplayground.tests.MultiSurfaceTest"
  "com.amap.agenuiplayground.tests.PlatformFunctionTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeCombinedStressTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeConcurrentCoordinatorTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeConfigApiStackOverflowTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeConfigDestroyRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeDeepComponentTreeTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeDeepJsonCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineDestroyRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineDestroyUAFTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineLifecycleStressTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineReinitFailureTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeEngineSelfJoinCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeExtendedLifecycleTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeExtremeStyleValuesTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeFormatNumberOOMCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeFuncRegUnregRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeFuncRegisterRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeFunctionUnregisterRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeInitCrashTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeJniBridgeRaceTest"
  "com.amap.agenuiplayground.tests.SDKRiskProbeJsonTypeMismatchTest"
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

CLASS_ARG=$(IFS=,; echo "${CLASSES[*]}")
OUTPUT_FILE="$RESULT_DIR/FULL_RUN_SKIP_CRASH.txt"

echo "=========================================="
echo "AGenUI Full Test Run (skip crash test)"
echo "Device: $DEVICE"
echo "Classes: ${#CLASSES[@]} (excluding SDKRiskProbeConcurrentDestroyBridgeTest)"
echo "Started: $(date)"
echo "=========================================="

adb -s "$DEVICE" shell am instrument -w -r \
  -e class "$CLASS_ARG" \
  "$TEST_PKG/$RUNNER" 2>&1 | tee "$OUTPUT_FILE"

echo ""
echo "=========================================="
echo "Finished: $(date)"
echo "=========================================="

# Parse results
PASS=$(grep -c "INSTRUMENTATION_STATUS_CODE: 0" "$OUTPUT_FILE" 2>/dev/null || echo 0)
FAIL=$(grep -c "INSTRUMENTATION_STATUS_CODE: -2" "$OUTPUT_FILE" 2>/dev/null || echo 0)
SKIP=$(grep -c "INSTRUMENTATION_STATUS_CODE: -3" "$OUTPUT_FILE" 2>/dev/null || echo 0)
START=$(grep -c "INSTRUMENTATION_STATUS_CODE: 1" "$OUTPUT_FILE" 2>/dev/null || echo 0)
CRASH=$(grep -c "Process crashed" "$OUTPUT_FILE" 2>/dev/null || echo 0)

echo "Results:"
echo "  Started (code 1): $START"
echo "  Passed (code 0): $PASS"
echo "  Failed (code -2): $FAIL"
echo "  Skipped (code -3): $SKIP"
echo "  Process crashed: $CRASH"
echo ""
echo "  Total run: $((START))"
echo "  Pass rate: $(echo "scale=1; $PASS * 100 / ($START + 0.001)" | bc 2>/dev/null || echo "N/A")%"
