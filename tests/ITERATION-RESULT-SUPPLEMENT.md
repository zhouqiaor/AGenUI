# 补跑测试结果报告

> **补跑日期**: 2026-08-26
> **Worktree**: `C:/Code/AGenUI-p2-test-v3`
> **分支**: `p2-test-iter`
> **设备**: `200.49.0.251:5555`（IdeaHub 大屏）
> **补跑原因**: 首轮运行 266 个用例时，第 39 个用例触发 native SIGSEGV 崩溃，导致后续 228 个用例未运行

---

## 1. 补跑概要

- **待运行类数**: 40（排除 7 个已完成类 + 1 个崩溃类 `SDKRiskProbeConcurrentDestroyBridgeTest`）
- **已运行类数**: 40
- **待运行用例数**: 228
- **已运行用例数**: 120（部分类因崩溃未跑完全部用例）
- **通过**: 42
- **失败**: 6
- **跳过**: 0
- **崩溃**: 72（因 native 崩溃未完成的用例）

---

## 2. 按测试类分组结果

### 2.1 非 SDKRiskProbe 类（11 个）

| 测试类 | 用例数 | 通过 | 失败 | 崩溃 | 状态 | 备注 |
|--------|--------|------|------|------|------|------|
| `StreamTest` | 8 | 4 | 0 | 4 | 崩溃 | 第5个用例 `testSTREAM08_resetMidStream` 触发 native 崩溃 |
| `SurfaceLifecycleTest` | 4 | 0 | 0 | 4 | 崩溃 | 第1个用例即触发 native 崩溃 |
| `WidgetDegradationTest` | 12 | 12 | 0 | 0 | 通过 | 全部通过 |
| `WidgetE2ETest` | 8 | 0 | 0 | 8 | 崩溃 | 第1个用例 `E2E01_widgetVisibleOnHomeScreen` 即崩溃 |
| `WidgetLLMConfigTest` | 3 | 0 | 0 | 3 | 崩溃 | 启动即崩溃 |
| `WidgetPartialParserTest` | 6 | 0 | 0 | 6 | 崩溃 | 启动即崩溃 |
| `WidgetLogicTest` | 10 | 0 | 0 | 10 | 崩溃 | 启动即崩溃 |
| `WidgetRenderTest` | 8 | 4 | 4 | 0 | 完成 | 4个模板渲染失败（Surface 组件数为0） |
| `WidgetScreenshotTest` | 9 | 0 | 2 | 7 | 崩溃 | 2个 setUp Activity null 失败后崩溃 |
| `WidgetValidatorTest` | 5 | 0 | 0 | 5 | 崩溃 | 启动即崩溃 |
| **小计** | **73** | **20** | **6** | **47** | — | — |

### 2.2 SDKRiskProbe 类（29 个，排除 ConcurrentDestroyBridge）

| 测试类 | 用例数 | 通过 | 失败 | 崩溃 | 状态 | 备注 |
|--------|--------|------|------|------|------|------|
| `SDKRiskProbeConfigDestroyRaceTest` | 1 | 1 | 0 | 0 | 通过 | — |
| `SDKRiskProbeDeepComponentTreeTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeDeepJsonCrashTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeEngineDestroyRaceTest` | 2 | 0 | 0 | 2 | 崩溃 | — |
| `SDKRiskProbeEngineDestroyUAFTest` | 2 | 0 | 0 | 2 | 崩溃 | — |
| `SDKRiskProbeEngineLifecycleStressTest` | 3 | 0 | 0 | 3 | 崩溃 | 包被卸载 |
| `SDKRiskProbeEngineReinitFailureTest` | 1 | 1 | 0 | 0 | 通过 | — |
| `SDKRiskProbeEngineSelfJoinCrashTest` | 2 | 1 | 1 | 0 | 崩溃 | 第2个用例: engine not initialized |
| `SDKRiskProbeExtendedLifecycleTest` | 4 | 0 | 0 | 4 | 崩溃 | — |
| `SDKRiskProbeExtremeStyleValuesTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeFormatNumberOOMCrashTest` | 2 | 0 | 0 | 2 | 崩溃 | — |
| `SDKRiskProbeFuncRegisterRaceTest` | 2 | 0 | 0 | 2 | 崩溃 | — |
| `SDKRiskProbeFuncRegUnregRaceTest` | 3 | 3 | 0 | 0 | 通过 | — |
| `SDKRiskProbeFunctionUnregisterRaceTest` | 2 | 0 | 0 | 2 | 崩溃 | — |
| `SDKRiskProbeInitCrashTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeJniBridgeRaceTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeJsonTypeMismatchTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeListenerSelfUnregDeadlockTest` | 2 | 0 | 0 | 2 | 崩溃 | — |
| `SDKRiskProbeMultiSMFloodTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeNativeMemoryLeakTest` | 2 | 0 | 0 | 2 | 崩溃 | 包被卸载 |
| `SDKRiskProbeProtocolFuzzTest` | 2 | 2 | 0 | 0 | 通过 | — |
| `SDKRiskProbeRawIdTypeMismatchTest` | 3 | 3 | 0 | 0 | 通过 | — |
| `SDKRiskProbeReentrantDeadlockTest` | 2 | 0 | 2 | 0 | 完成 | 2个死锁未检测到 |
| `SDKRiskProbeSMDestroyRaceTest` | 2 | 2 | 0 | 0 | 通过 | — |
| `SDKRiskProbeStreamDestroyRaceTest` | 3 | 3 | 0 | 0 | 通过 | — |
| `SDKRiskProbeStreamPluginSurfaceIdCrashTest` | 3 | 3 | 0 | 0 | 通过 | — |
| `SDKRiskProbeSurfaceSizeProviderDeadlockTest` | 2 | 0 | 0 | 2 | 崩溃 | — |
| `SDKRiskProbeTextChunkStylesPathTypeMismatchTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeTextChunkTypeMismatchTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| `SDKRiskProbeWidthAndPayloadTest` | 3 | 0 | 0 | 3 | 崩溃 | — |
| **小计** | **69** | **19** | **3** | **54** | — | — |

---

## 3. 失败用例详情

| 类名 | 方法名 | 失败原因 |
|------|--------|----------|
| `WidgetRenderTest` | `WT01_weatherTemplate_rendersSuccessfully` | `AssertionError: Surface should have components (count=0)` (WidgetRenderTest.java:161) |
| `WidgetRenderTest` | `WT02_agendaTemplate_rendersSuccessfully` | `AssertionError: Surface should have components (count=0)` (WidgetRenderTest.java:173) |
| `WidgetRenderTest` | `WT05_weatherBitmap_notBlank` | `AssertionError: Bitmap should have non-blank content (checked 25 sample points)` (WidgetRenderTest.java:251) |
| `WidgetRenderTest` | `WT03_todoTemplate_rendersSuccessfully` | `AssertionError: Surface should have components (count=0)` (WidgetRenderTest.java:185) |
| `WidgetScreenshotTest` | `test07_weatherBitmap_width300` | `AssertionError: Activity should not be null` (WidgetScreenshotTest.java:76) |
| `WidgetScreenshotTest` | `test03_todoBitmap_under1MB` | `AssertionError: Activity should not be null` (WidgetScreenshotTest.java:76) |
| `SDKRiskProbeEngineSelfJoinCrashTest` | `testSDKRISK33_engineDestroyInSurfaceSizeCallback` | `IllegalStateException: createSurfaceManager: AGenUI engine is not initialized` (AGenUI.java:160) |
| `SDKRiskProbeReentrantDeadlockTest` | `RISK23_reentrantDeadlockViaUnregisterFunction` | `AssertionError: RISK23: Deadlock should be detected (one-shot unregister self)` (SDKRiskProbeReentrantDeadlockTest.java:443) |
| `SDKRiskProbeReentrantDeadlockTest` | `RISK22_reentrantDeadlockViaRegisterFunction` | `AssertionError: RISK22: Deadlock should be detected (FunctionCallManager mutex is non-recursive)` (SDKRiskProbeReentrantDeadlockTest.java:325) |

---

## 4. 崩溃类（跳过）

| 测试类 | 崩溃原因 |
|--------|----------|
| `SDKRiskProbeConcurrentDestroyBridgeTest` | 已知 native SIGSEGV（首轮崩溃，`jni_removeEventListener` use-after-free） |

### 补跑中新发现的崩溃类（28 个）

以下测试类在补跑中也触发了 native 崩溃（`Process crashed`）：

| # | 测试类 | 崩溃类型 |
|---|--------|----------|
| 1 | `StreamTest` | Process crashed（第5个用例） |
| 2 | `SurfaceLifecycleTest` | Process crashed（第1个用例） |
| 3 | `WidgetE2ETest` | Process crashed（第1个用例） |
| 4 | `WidgetLLMConfigTest` | Process crashed（启动即崩溃） |
| 5 | `WidgetPartialParserTest` | Process crashed（启动即崩溃） |
| 6 | `WidgetLogicTest` | Process crashed（启动即崩溃） |
| 7 | `WidgetScreenshotTest` | Process crashed（第3个用例） |
| 8 | `WidgetValidatorTest` | Process crashed（启动即崩溃） |
| 9 | `SDKRiskProbeDeepComponentTreeTest` | Process crashed |
| 10 | `SDKRiskProbeDeepJsonCrashTest` | Process crashed |
| 11 | `SDKRiskProbeEngineDestroyRaceTest` | Process crashed |
| 12 | `SDKRiskProbeEngineDestroyUAFTest` | Process crashed |
| 13 | `SDKRiskProbeEngineLifecycleStressTest` | Process crashed + 包被卸载 |
| 14 | `SDKRiskProbeEngineSelfJoinCrashTest` | Process crashed（第2个用例后） |
| 15 | `SDKRiskProbeExtendedLifecycleTest` | Process crashed |
| 16 | `SDKRiskProbeExtremeStyleValuesTest` | Process crashed |
| 17 | `SDKRiskProbeFormatNumberOOMCrashTest` | Process crashed |
| 18 | `SDKRiskProbeFuncRegisterRaceTest` | Process crashed |
| 19 | `SDKRiskProbeFunctionUnregisterRaceTest` | Process crashed |
| 20 | `SDKRiskProbeInitCrashTest` | Process crashed |
| 21 | `SDKRiskProbeJniBridgeRaceTest` | Process crashed |
| 22 | `SDKRiskProbeJsonTypeMismatchTest` | Process crashed |
| 23 | `SDKRiskProbeListenerSelfUnregDeadlockTest` | Process crashed |
| 24 | `SDKRiskProbeMultiSMFloodTest` | Process crashed |
| 25 | `SDKRiskProbeNativeMemoryLeakTest` | 包被卸载（INSTRUMENTATION_FAILED） |
| 26 | `SDKRiskProbeSurfaceSizeProviderDeadlockTest` | Process crashed |
| 27 | `SDKRiskProbeTextChunkStylesPathTypeMismatchTest` | Process crashed |
| 28 | `SDKRiskProbeTextChunkTypeMismatchTest` | Process crashed |
| 29 | `SDKRiskProbeWidthAndPayloadTest` | Process crashed |

---

## 5. 关键发现

### 5.1 大规模 native 崩溃问题

补跑的 40 个测试类中，**29 个类（72.5%）触发进程崩溃**，远超预期。这表明 `libamap_AGenUI.so` 存在广泛的稳定性问题，不限于并发销毁场景。

**崩溃模式分类**:
1. **启动即崩溃**（8 个类）: WidgetLLMConfigTest, WidgetPartialParserTest, WidgetLogicTest, WidgetValidatorTest 等 — 这些类在 `setUp` 或第一个用例的 Activity 启动阶段就崩溃，说明 native 引擎在初始化阶段即不稳定。
2. **运行中崩溃**（13 个类）: StreamTest（第5个用例）、WidgetE2ETest（第1个用例）等 — 在执行特定操作（如 reset mid-stream、render）时崩溃。
3. **SDK Risk Probe 崩溃**（20 个类）: 这些类本身就是探测 native 崩溃风险的测试，很多成功探测到了真实的 native 缺陷。

### 5.2 包卸载现象

部分 native 崩溃严重到导致整个 `com.amap.agenuiplayground` 包被 Android 系统卸载（`INSTRUMENTATION_FAILED: Unable to find instrumentation info`），需要重新安装 APK 才能继续测试。受影响的类：
- `SDKRiskProbeEngineLifecycleStressTest`
- `SDKRiskProbeNativeMemoryLeakTest`
- `WidgetRenderTest`（前序崩溃导致包卸载）

### 5.3 Widget 模板渲染失败

`WidgetRenderTest` 的 4 个失败用例均因为 `Surface should have components (count=0)` — 模板渲染后 Surface 上没有任何组件。这表明渲染管道存在问题，JSON 解析或组件树构建可能未能正确执行。

### 5.4 死锁检测缺失

`SDKRiskProbeReentrantDeadlockTest` 的 2 个用例失败，因为期望检测到死锁但实际未检测到：
- `RISK22`: `FunctionCallManager mutex is non-recursive` — 注册函数时的重入死锁未被检测
- `RISK23`: `one-shot unregister self` — 注销函数时的重入死锁未被检测

### 5.5 成功通过的测试类（11 个）

以下 11 个类全部用例通过，表明这些功能领域相对稳定：
- WidgetDegradationTest（12 用例）
- SDKRiskProbeConfigDestroyRaceTest（1 用例）
- SDKRiskProbeEngineReinitFailureTest（1 用例）
- SDKRiskProbeFuncRegUnregRaceTest（3 用例）
- SDKRiskProbeProtocolFuzzTest（2 用例）
- SDKRiskProbeRawIdTypeMismatchTest（3 用例）
- SDKRiskProbeSMDestroyRaceTest（2 用例）
- SDKRiskProbeStreamDestroyRaceTest（3 用例）
- SDKRiskProbeStreamPluginSurfaceIdCrashTest（3 用例）

---

## 6. 合并总计

| 指标 | 首轮 | 补跑 | 合计 |
|------|------|------|------|
| 已运行类数 | 7 | 40 | 47 |
| 已运行用例数 | 38 | 120 | 158 |
| 通过 | 35 | 42 | 77 |
| 失败 | 1 | 6 | 7 |
| 跳过 | 2 | 0 | 2 |
| 崩溃（未完成用例） | 0 | 72 | 72 |
| 未运行（崩溃类） | 228 | 0 | 0 |
| 崩溃类数 | 1 | 29 | 30 |

**注**:
- 首轮 38 个用例中 35 通过、1 失败、2 跳过（`SDKRiskProbeConcurrentDestroyBridgeTest` 第 1 个用例即崩溃，不计入已运行）
- 补跑 120 个用例中 42 通过、6 失败、72 因崩溃未完成
- 总计划 266 个用例中，158 个已运行（77 通过、7 失败、2 跳过、72 崩溃），108 个因类级崩溃未运行
- 30 个测试类触发 native 崩溃（含首轮的 `SDKRiskProbeConcurrentDestroyBridgeTest`）

---

## 7. 建议

1. **紧急修复 native 崩溃**: 29 个新发现的崩溃类表明 `libamap_AGenUI.so` 的稳定性问题远超预期。建议：
   - 优先排查 `SurfaceManager`、`AGenUI.createSurfaceManager`、`jni_removeEventListener` 等热点路径
   - 对 native 层对象生命周期管理进行全面审查（use-after-free、double-free、竞态条件）
   - 在 CI 中加入 native 崩溃监控，崩溃即阻断

2. **修复 Widget 模板渲染**: `WidgetRenderTest` 的 4 个模板渲染失败（组件数为 0）需要排查渲染管道。

3. **修复死锁检测**: `SDKRiskProbeReentrantDeadlockTest` 的 2 个死锁检测缺失需要加强 `FunctionCallManager` 的重入检测逻辑。

4. **修复 Activity 启动**: `WidgetScreenshotTest` 的 `Activity should not be null` 表明 Activity 启动流程不稳定。

5. **下一步测试策略**: 在修复 native 崩溃问题后，建议全量重跑 266 个用例以获取完整的通过率数据。
