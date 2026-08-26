# 测试迭代结果报告

> **Worktree**: `C:/Code/AGenUI-p2-test-v3`
> **分支**: `p2-test-iter`（基于 `b5ed66c`）
> **设备**: `200.49.0.251:5555`（IdeaHub 大屏, Android 15, 3840×2160, 480dpi）
> **日期**: 2026-08-26
> **构建工具**: Gradle 8.11.1（`./gradlew`）

---

## 1. 编译结果

- **状态**: ✅ 通过
- **命令**: `ANDROID_HOME="/c/Programs/Android/Sdk" ./gradlew :app:compileDebugAndroidTestJavaWithJavac --no-daemon --console=plain`
- **耗时**: 33s
- **备注**: 仅有 deprecation/unchecked 警告，无编译错误
  - `注: 某些输入文件使用或覆盖了已过时的 API。`
  - `注: 某些输入文件使用了未经检查或不安全的操作。`
- **可执行任务**: 40 actionable tasks: 40 executed

测试 APK 构建命令：`./gradlew :app:assembleDebugAndroidTest`，耗时 16s，构建成功。

---

## 2. 测试运行结果

- **总用例数（计划）**: 266（来自 `numtests=266`）
- **实际完成用例数**: 38（在第 39 个用例时进程崩溃）
- **通过**: 35
- **失败**: 1
- **跳过（ignored/assumption failed）**: 2
- **未运行**: 228（因进程崩溃中断）
- **运行耗时**: 约 4m14s（崩溃前）
- **运行命令**: `adb -s 200.49.0.251:5555 shell am instrument -w -r -e package com.amap.agenuiplayground.tests com.amap.agenuiplayground.test/androidx.test.runner.AndroidJUnitRunner`

### 进程崩溃说明

测试在运行到 `SDKRiskProbeConcurrentDestroyBridgeTest#testRISK28c_rapidCreateDestroyCycles` 时发生 **native 段错误（SIGSEGV）**，进程被杀，后续 228 个用例未能运行。崩溃详情见第 5 节。

---

## 3. 失败用例详情

| 类名 | 方法名 | 失败原因 |
|------|--------|----------|
| `ComponentRenderTest` | `testRender_05_modalWithTrigger` | `java.lang.AssertionError: Component count should be 4 (Modal not registered) expected:<4> but was:<5>` (ComponentRenderTest.java:166) |

### 跳过用例（ignored）

| 类名 | 方法名 | 说明 |
|------|--------|------|
| `FunctionCallTest` | `testSKILL07_validateRequired` | 被 ignored（AssumptionFailed 或条件不满足） |
| `PlatformFunctionTest` | `testFUNC04_functionResultSerialization` | 被 ignored（AssumptionFailed 或条件不满足） |

---

## 4. 按测试类分组结果

| 测试类 | 用例数 | 通过 | 失败 | 跳过 | 状态 |
|--------|--------|------|------|------|------|
| `ComponentRenderTest` | 6 | 5 | 1 | 0 | ✅ 完成 |
| `FunctionCallTest` | 15 | 14 | 0 | 1 | ✅ 完成 |
| `InitializationTest` | 3 | 3 | 0 | 0 | ✅ 完成 |
| `MultiSurfaceTest` | 3 | 3 | 0 | 0 | ✅ 完成 |
| `PlatformFunctionTest` | 4 | 3 | 0 | 1 | ✅ 完成 |
| `SDKRiskProbeCombinedStressTest` | 4 | 4 | 0 | 0 | ✅ 完成 |
| `SDKRiskProbeConcurrentCoordinatorTest` | 3 | 3 | 0 | 0 | ✅ 完成 |
| `SDKRiskProbeConcurrentDestroyBridgeTest` | 0 | 0 | 0 | 0 | ❌ 崩溃（第1个用例） |
| 其余 41 个测试类 | — | — | — | — | ⏸ 未运行 |

**已完成类**: 7 个（全部用例跑完）
**崩溃类**: 1 个（`SDKRiskProbeConcurrentDestroyBridgeTest`，第 1 个用例即崩溃）
**未运行类**: 41 个

---

## 5. 关键日志摘要

### 5.1 崩溃日志（native SIGSEGV）

测试进程在运行 `SDKRiskProbeConcurrentDestroyBridgeTest#testRISK28c_rapidCreateDestroyCycles` 时发生 **native 段错误**：

```
08-26 08:04:58.543 F/libc    (10243): Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR),
    fault addr 0x70616d612e6d6f6b in tid 14355 (batch-destroyer), pid 10243 (genuiplayground)
```

**Backtrace（关键帧）**:
```
#00 pc 0000000000135444  libamap_AGenUI.so (agenui::jni_removeEventListener(_JNIEnv*, _jclass*, int, _jobject*)+804)
#01 pc 000000000040c900  libart.so (art_quick_generic_jni_trampoline+144)
#02 pc 00000000022c1190  memfd:jit-cache (com.amap.agenui.render.surface.SurfaceManager.removeMessageListener+288)
#03 pc 00000000022402dc  memfd:jit-cache (com.amap.agenui.render.surface.SurfaceManager.destroy+172)
...
#09 pc 000000000000e3cc  (com.amap.agenuiplayground.tests.SDKRiskProbeConcurrentDestroyBridgeTest.lambda$testRISK28c_rapidCreateDestroyCycles$7+0)
```

**分析**:
- 崩溃线程名: `batch-destroyer`（批量销毁线程）
- 崩溃位置: `libamap_AGenUI.so` 的 `agenui::jni_removeEventListener` 函数
- 调用链: `SDKRiskProbeConcurrentDestroyBridgeTest.lambda$testRISK28c_rapidCreateDestroyCycles$7` → `SurfaceManager.destroy` → `SurfaceManager.removeMessageListener` → `jni_removeEventListener`（native）
- fault addr `0x70616d612e6d6f6b` 解码为 ASCII 字符串 `kom.amap`（疑似已释放/复用的对象指针被解引用）
- **根因推测**: 在快速创建-销毁 Surface 的并发压测中，native 层 event listener 对象存在 use-after-free 或竞态条件，导致访问已释放的内存。这与测试用例 `testRISK28c_rapidCreateDestroyCycles` 的设计目的（探测并发销毁竞态）一致——测试成功探测到了一个真实的 native 崩溃风险。

### 5.2 崩溃前 SurfaceManager 日志

崩溃前有大量 SurfaceManager 销毁日志，显示 instanceId 649/650 被反复销毁：
```
08-26 08:04:58.558 I/SurfaceManager: [destroySurfaceManager@185] SurfaceManager destroyed: engineId=649
08-26 08:04:58.559 I/SurfaceManager: [destroy@213] SurfaceManager destroyed, instanceId=649
08-26 08:04:58.559 I/AGenUI  : [destroySurfaceManager@185] SurfaceManager destroyed: engineId=650
08-26 08:04:58.559 I/SurfaceManager: [destroy@213] SurfaceManager destroyed, instanceId=650
...（重复数十次）
```

### 5.3 初始化阶段警告

```
08-26 08:00:45.819 W/ActivityThread: Package uses different ABI(s) than its instrumentation:
    package[com.amap.agenuiplayground]: arm64-v8a, null
    instrumentation[com.amap.agenuiplayground.test]: null, null
```
此警告未影响测试运行，但提示测试 APK 未显式声明 ABI。

### 5.4 FontRegistry 错误（非致命）

```
08-26 08:00:47.027 E/FontRegistry: at com.amap.agenuiplayground.A2UIPlaygroundActivity.initAGenUI(A2UIPlaygroundActivity.java:725)
08-26 08:00:47.032 E/FontRegistry: at com.amap.agenuiplayground.A2UIPlaygroundActivity.initAGenUI(A2UIPlaygroundActivity.java:726)
08-26 08:00:47.036 E/FontRegistry: at com.amap.agenuiplayground.A2UIPlaygroundActivity.initAGenUI(A2UIPlaygroundActivity.java:727)
```
FontRegistry 报错但未阻止测试进行。

---

## 6. 测试覆盖率摘要

本次运行未配置代码覆盖率插件（Jacoco/Kover），无法获取覆盖率数据。如需覆盖率统计，需在 `build.gradle` 中启用 `testCoverageEnabled` 并使用 `createDebugCoverageReport`。

---

## 7. 结论与建议

### 结论

1. **编译**: 测试代码编译通过，无错误。
2. **已运行测试**: 38 个用例中 35 通过、1 失败、2 跳过，通过率 92.1%（排除跳过后为 97.2%）。
3. **中断**: 进程在 `SDKRiskProbeConcurrentDestroyBridgeTest` 发生 native SIGSEGV 崩溃，导致 228 个用例未运行。
4. **失败用例**: `ComponentRenderTest#testRender_05_modalWithTrigger` — Modal 组件计数不符预期（预期 4，实际 5），疑似 Modal 组件注册逻辑变更未同步更新测试断言。
5. **崩溃用例**: `testRISK28c_rapidCreateDestroyCycles` 成功探测到 `libamap_AGenUI.so` 中 `jni_removeEventListener` 的 use-after-free/竞态缺陷。

### 建议

1. **优先修复 native 崩溃**: `agenui::jni_removeEventListener` 在并发销毁场景下的段错误是严重稳定性风险。建议：
   - 在 native 层对 event listener 集合加锁或使用原子操作
   - 销毁流程中加入对象有效性校验
   - 考虑在 `SurfaceManager.destroy` 中加入幂等性保护，避免重复销毁

2. **修复 `testRender_05_modalWithTrigger`**: 核对 Modal 组件注册逻辑，更新断言期望值或修正组件树计数逻辑。

3. **补跑未运行测试**: 228 个用例因崩溃未运行。建议：
   - 先修复 native 崩溃后全量重跑
   - 或按类分批运行，跳过 `SDKRiskProbeConcurrentDestroyBridgeTest` 以跑通其余类

4. **分批运行建议**: 可按以下顺序补跑（避开崩溃类）：
   ```bash
   # 按-排除崩溃类运行其余测试
   adb -s 200.49.0.251:5555 shell am instrument -w -r \
     -e package com.amap.agenuiplayground.tests \
     -e class com.amap.agenuiplayground.tests.SDKRiskProbeConfigApiStackOverflowTest,com.amap.agenuiplayground.tests.SDKRiskProbeConfigDestroyRaceTest,... \
     com.amap.agenuiplayground.test/androidx.test.runner.AndroidJUnitRunner
   ```

5. **测试 APK ABI 声明**: 在 `build.gradle` 的 `androidTest` 配置中显式声明 `ndkFilter 'arm64-v8a'`，消除 ABI 不匹配警告。

6. **覆盖率**: 如需覆盖率数据，在 `app/build.gradle` 的 `debug` buildType 中加 `enableAndroidTestCoverage true`，并配合 Jacoco 插件。
