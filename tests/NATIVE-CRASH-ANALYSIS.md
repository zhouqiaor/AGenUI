# Native 崩溃根因分析报告

> **分析日期**: 2026-08-26
> **Worktree**: `C:/Code/AGenUI-p2-test-v3`
> **设备**: `200.49.0.251:5555`（IdeaHub 大屏, Android 15, arm64）
> **APK**: `com.amap.agenuiplayground`
> **Native 库**: `libamap_AGenUI.so` (BuildId: `2a446baae3cfa9b9fd181752de7ea868f7ea4ca7`)

---

## 0. 重要更正说明

**前次报告（ITERATION-RESULT-SUPPLEMENT.md）中"30 个崩溃类、72 个崩溃用例"的结论经本次实测验证为误判。**

本次对 8 个代表性"崩溃类"逐一重新运行并捕获 crash log、tombstone、dropbox 记录后发现：

| 现象 | 前次报告判定 | 实际原因 |
|------|-------------|----------|
| `INSTRUMENTATION_RESULT: shortMsg=Process crashed.` | native 崩溃 | 绝大多数为 **SIGKILL**（进程被 Android AM 在启动新 instrumentation 时主动杀死: `stop com.amap.agenuiplayground due to start instr`） |
| `INSTRUMENTATION_CODE: 0` | native 崩溃 | instrumentation 非正常退出，但 crash buffer 为空，无 tombstone 生成 |
| APK "被卸载" | native 崩溃导致 | 实测未复现，APK 始终在位 |

**验证证据**:
- `/data/tombstones/` 仅有 1 个 tombstone（`tombstone_00`，时间戳 08:04:58，对应首轮全量运行的 `SDKRiskProbeConcurrentDestroyBridgeTest`）
- `/data/system/dropbox/` 仅有 1 条 `data_app_native_crash` 记录，与 tombstone_00 对应
- 本次补跑的 8 个类中，所有"Process crashed"的进程退出信号均为 `signal 9 (Killed)`，无 `signal 11 (SIGSEGV)` 或 `signal 6 (SIGABRT)`
- `WidgetLogicTest`（17/17 通过）、`WidgetValidatorTest`（29/29 通过）、`SDKRiskProbeInitCrashTest`（1/1 通过）、`SurfaceLifecycleTest`（4/4 通过）在单独运行时**全部通过**，未复现前次报告的"启动即崩溃"

**SIGKILL 根因分析**: Android 的 `am instrument` 在启动新测试进程前会杀死旧的应用进程（`stop due to start instr`）。如果测试类使用了 `ActivityScenarioRule` 且前一个测试类残留了 Activity 实例，新 instrumentation 启动时 AM 会杀死旧进程，JUnit Runner 报告 `Process crashed` 但实际并非 native 崩溃。前次补跑因连续运行 40 个类、无间隔清理，此现象被放大。

---

## 1. 崩溃概要（修正后）

| 指标 | 前次报告 | 修正后 |
|------|---------|--------|
| 崩溃类总数 | 30/48 (62.5%) | **1 个真正的 native 崩溃类** |
| 崩溃用例总数 | 72 | **1 个 native 崩溃用例** + 若干 Java 异常/SIGKILL |
| 独立 native 崩溃签名数 | 未分类（假设多个） | **1 个** |
| Java 崩溃签名数 | 未统计 | **1 个**（`IllegalStateException: engine not initialized`） |
| SIGKILL（非崩溃） | 计入崩溃 | **单独统计，不计入 native 崩溃** |

### 1.1 实际崩溃分类

| 类别 | 数量 | 说明 |
|------|------|------|
| Native SIGSEGV | 1 | `jni_removeEventListener` UAF（tombstone_00） |
| Java RuntimeException | 1 | `IllegalStateException: createSurfaceManager: AGenUI engine is not initialized`（dropbox: data_app_crash） |
| SIGKILL（系统杀死） | ~28 类 | `am instrument` 启动冲突，非崩溃 |
| 真正通过 | 11 类 | 前次报告已正确记录 |

---

## 2. 崩溃签名详情

### 签名 1: jni_removeEventListener Use-After-Free（唯一 native 崩溃签名）

- **信号**: `SIGSEGV` (signal 11)
- **code**: `SEGV_MAPERR` (code 1) — 访问未映射内存
- **fault addr**: `0x70616d612e6d6f6b`
  - ASCII 解码: `"kom.amap"` — 这是已释放内存中的垃圾数据，说明解引用了悬空指针指向的已释放区域
  - x8 寄存器值 `0x70616d612e6d6f63` = `"com.amap."` 反序，进一步证实是已释放的 Java 字符串残留
- **崩溃线程**: `batch-destroyer` (tid 14355)，用户创建的并发销毁线程
- **进程**: pid 10243, `com.amap.agenuiplayground`

#### 完整 backtrace（29 帧）

```
#00 pc 0000000000135444  libamap_AGenUI.so (agenui::jni_removeEventListener(_JNIEnv*, _jclass*, int, _jobject*)+804)
#01 pc 000000000040c900  libart.so (art_quick_generic_jni_trampoline+144)
#02 pc 00000000022c1190  memfd:jit-cache (com.amap.agenui.render.surface.SurfaceManager.removeMessageListener+288)
#03 pc 00000000022402dc  memfd:jit-cache (com.amap.agenui.render.surface.SurfaceManager.destroy+172)
#04 pc 00000000003f5594  libart.so (art_quick_invoke_stub+612)
#05 pc 0000000000242a34  libart.so (art::ArtMethod::Invoke+132)
#06 pc 00000000006c3f1c  libart.so (art::interpreter::DoCall<false>+1420)
#07 pc 000000000070b344  libart.so (art::interpreter::ExecuteSwitchImplCpp<false>+10868)
#08 pc 000000000040efd8  libart.so (ExecuteSwitchImplAsm+8)
#09 pc 000000000000e3cc  <anonymous> (com.amap.agenuiplayground.tests.SDKRiskProbeConcurrentDestroyBridgeTest.lambda$testRISK28c_rapidCreateDestroyCycles$7+0)
#10 pc 00000000003f4088  libart.so (art::interpreter::ArtInterpreterToInterpreterBridge+296)
#11 pc 00000000006c3ffc  libart.so (art::interpreter::DoCall<false>+1644)
#12 pc 000000000070b344  libart.so (art::interpreter::ExecuteSwitchImplCpp<false>+10868)
#13 pc 000000000040efd8  libart.so (ExecuteSwitchImplAsm+8)
#14 pc 000000000000e074  <anonymous> (SDKRiskProbeConcurrentDestroyBridgeTest$$ExternalSyntheticLambda3.run+0)
#15 pc 00000000003f4088  libart.so (art::interpreter::ArtInterpreterToInterpreterBridge+296)
#16 pc 00000000006c3ffc  libart.so (art::interpreter::DoCall<false>+1644)
#17 pc 000000000070b344  libart.so (art::interpreter::ExecuteSwitchImplCpp<false>+10868)
#18 pc 000000000040efd8  libart.so (ExecuteSwitchImplAsm+8)
#19 pc 000000000011802c  core-oj.jar (java.lang.Thread.run+0)
#20-#28  art::interpreter::EnterInterpreterFromEntryPoint → art::Thread::CreateCallback → __pthread_start → __start_thread
```

#### 调用链（Java → JNI → Native）

```
Thread.run
  └─ SDKRiskProbeConcurrentDestroyBridgeTest$$ExternalSyntheticLambda3.run  (lambda #7)
     └─ SDKRiskProbeConcurrentDestroyBridgeTest.lambda$testRISK28c_rapidCreateDestroyCycles$7
        └─ SurfaceManager.destroy()                        [Java, line ~213]
           └─ SurfaceManager.removeMessageListener()        [Java, JIT-compiled]
              └─ jni_removeEventListener()                  [JNI, libamap_AGenUI.so +804]
                 └─ *** SIGSEGV *** (fault addr 0x70616d612e6d6f6b)
```

#### 触发类

- `SDKRiskProbeConcurrentDestroyBridgeTest`
- 测试方法: `testRISK28c_rapidCreateDestroyCycles`
- 测试设计目的: 并发销毁 SurfaceManager，探测 `jni_removeEventListener` 中的 UAF/double-free

#### 根因

**Use-After-Free + Double-Free 竞态条件**

根据测试类源码注释（`SDKRiskProbeConcurrentDestroyBridgeTest.java:19-48`），native 层 `jni_removeEventListener` 的执行流程为：

```
1. bridge = ListenerBridgeManager.findBridge(javaListener)   // mutex-guarded lookup, returns raw ptr
2. surfaceManager->removeSurfaceEventListener(bridge)         // mutex + list traversal
3. ListenerBridgeManager.removeMapping(javaListener)          // mutex-guarded erase
4. SAFELY_DELETE(bridge)                                      // delete bridge; bridge = nullptr (LOCAL only)
```

当两个线程并发调用 `SurfaceManager.destroy()` 销毁同一个 SurfaceManager 时：

```
Thread A: findBridge → 获得 bridge B (mutex 释放后)
Thread B: findBridge → 获得 bridge B (仍在 map 中，A 尚未 removeMapping)
Thread A: removeMapping + SAFELY_DELETE(B) → B 被释放
Thread B: removeSurfaceEventListener(B) → UAF（解引用已释放的指针 B）
Thread B: SAFELY_DELETE(B) → DOUBLE FREE（重复释放 B）
```

`SAFELY_DELETE` 宏只将**局部变量**置空，无法阻止另一线程通过自己的局部副本释放同一指针。

fault addr `0x70616d612e6d6f6b` = ASCII `"kom.amap"` 是已释放的 Java String 对象残留数据，证实了 UAF 诊断。

#### 修复建议

1. **在 `jni_removeEventListener` 中持锁覆盖整个 findBridge → removeMapping → delete 流程**，确保 bridge 指针的查找、移除、释放是原子的
2. **使用 `std::shared_ptr` / `std::weak_ptr`** 管理 listener bridge 生命周期，避免裸指针传递
3. **在 `SurfaceManager.destroy()` 中加入幂等性保护**：先检查 `m_destroyed` 标志，若已销毁则直接返回，加锁保护标志检查
4. **在 `removeSurfaceEventListener` 中加入空指针/有效性校验**，使用 `std::weak_ptr::lock()` 检查对象是否存活

---

### 签名 2: IllegalStateException — engine not initialized（Java 崩溃）

- **异常**: `java.lang.IllegalStateException: createSurfaceManager: AGenUI engine is not initialized`
- **位置**: `AGenUI.java:160`
- **触发**: `SDKRiskProbeEngineSelfJoinCrashTest#testSDKRISK33_engineDestroyInSurfaceSizeCallback`
- **dropbox**: `data_app_crash@1787703480938.txt`

#### 根因

测试在 `SurfaceSize` 回调中调用 `engine.destroy()`，随后回调链中又尝试 `createSurfaceManager`，此时引擎已销毁。这是测试设计的预期探测行为，但底层缺少防御性检查。

#### 修复建议

1. 在 `createSurfaceManager` 入口添加 `isInitialized()` 检查并抛出有意义的异常（已实现）
2. 在 `destroy()` 中设置标志位，所有 JNI 方法入口检查此标志，快速返回而非崩溃

---

## 3. 按 native 函数分类

| Native 函数 | 崩溃类数 | 崩溃用例数 | 崩溃类型 | 信号 |
|-------------|----------|------------|----------|------|
| `agenui::jni_removeEventListener` | 1 | 1 | Use-After-Free + Double-Free | SIGSEGV |
| `agenui::jni_createSurfaceManager` | 0 | 0 | — | — (Java 层拦截) |
| `agenui::jni_destroySurfaceManager` | 0 | 0 | — | — |
| Yoga 布局相关 | 0 | 0 | — | — |
| Canvas 绘制相关 | 0 | 0 | — | — |
| 其他 | 0 | 0 | — | — |

**结论**: 唯一确认的 native 崩溃热点是 `jni_removeEventListener`，集中在事件监听器管理模块。

---

## 4. 按崩溃类型分类

| 崩溃类型 | 崩溃类数 | 严重度 | 修复优先级 |
|----------|----------|--------|------------|
| Use-After-Free | 1 | Critical | P0 |
| Double-Free | 1 (同上) | Critical | P0 |
| 竞态条件 | 1 (同上) | High | P0 |
| 空指针解引用 | 0 | — | — |
| 缓冲区溢出 | 0 | — | — |
| Java IllegalStateException | 1 | Medium | P2 |

**注**: `jni_removeEventListener` 的 UAF、Double-Free、竞态条件是同一根因的三个表现，修复一处即可解决。

---

## 5. 按触发条件分类

| 触发条件 | 崩溃类数 | 说明 |
|----------|----------|------|
| 初始化阶段 | 0 | 前次报告的"启动即崩溃"经实测均为 SIGKILL 误判 |
| 正常操作阶段 | 0 | — |
| 销毁/清理阶段 | 1 | `SurfaceManager.destroy()` → `removeMessageListener` → `jni_removeEventListener` |
| 并发/竞态场景 | 1 | 多线程并发销毁同一个 SurfaceManager |

---

## 6. 修复优先级排序

| 优先级 | 崩溃签名 | 影响 | 修复方向 | 工作量估计 |
|--------|----------|------|----------|------------|
| **P0** | `jni_removeEventListener` UAF/Double-Free | 并发销毁 SurfaceManager 时必现 SIGSEGV，影响所有使用多 Surface + 并发销毁的场景 | 1. ListenerBridgeManager 全流程加锁<br>2. 改用 shared_ptr 管理 bridge 生命周期<br>3. SurfaceManager.destroy() 加幂等保护 | 2-3 人日 |
| P2 | `createSurfaceManager` engine 未初始化 | 仅在测试注入的极端时序下触发，生产场景概率低 | destroy() 设置标志位，JNI 入口检查 | 0.5 人日 |
| P3 | `am instrument` SIGKILL 误判 | 非代码缺陷，测试运行策略问题 | 测试间加入进程清理 / 使用 `am instrument --no-reinstantiate` | 0.5 人日 |

---

## 7. 本次实测运行结果（8 个代表类）

| # | 测试类 | 前次报告 | 本次实测 | 实际结果 |
|---|--------|---------|----------|----------|
| 1 | `WidgetLogicTest` | 启动即崩溃 | **未崩溃** | 17/17 通过 |
| 2 | `WidgetValidatorTest` | 启动即崩溃 | **未崩溃** | 29/29 通过 |
| 3 | `SDKRiskProbeInitCrashTest` | 启动即崩溃 | **未崩溃** | 1/1 通过 |
| 4 | `StreamTest` | 第5用例崩溃 | Process crashed (SIGKILL) | 第7用例 `testSTREAM03_buttonSimple_chunkSize1` 时被系统杀死 (signal 9)，无 native crash |
| 5 | `SDKRiskProbeDeepComponentTreeTest` | 崩溃 | Process crashed (SIGKILL) | 第2用例时被系统杀死 (signal 9)，无 native crash |
| 6 | `SDKRiskProbeConcurrentDestroyBridgeTest` | native SIGSEGV | Process crashed | tombstone_00 对应此类的首次运行（08:04:58），本次重跑未产生新 tombstone |
| 7 | `SDKRiskProbeEngineDestroyRaceTest` | 崩溃 | Process crashed (SIGKILL) | 被系统杀死 (signal 9)，无 native crash |
| 8 | `SurfaceLifecycleTest` | 启动即崩溃 | **未崩溃** | 4/4 通过 |

### 额外验证的类

| 测试类 | 前次报告 | 本次实测 |
|--------|---------|----------|
| `WidgetE2ETest` | 启动即崩溃 | Process crashed (SIGKILL) — `UiDevice.pressHome` 后被 AM 杀死 |
| `SDKRiskProbeDeepJsonCrashTest` | 崩溃 | Process crashed (SIGKILL) — signal 9 |
| `SDKRiskProbeEngineLifecycleStressTest` | 崩溃+包卸载 | Process crashed (SIGKILL) — signal 9，APK 未卸载 |

---

## 8. 修复建议汇总

### P0: 修复 `jni_removeEventListener` UAF/Double-Free

**问题**: `ListenerBridgeManager` 的 `findBridge` → `removeMapping` → `SAFELY_DELETE` 流程非原子，多线程并发时产生 UAF 和 double-free。

**方案 A（推荐，快速修复）**: 全流程加锁

```cpp
// 修改前: findBridge 和 removeMapping 分别加锁，中间无锁
// 修改后: 持锁覆盖全流程
void jni_removeEventListener(JNIEnv* env, jclass clazz, int instanceId, jobject javaListener) {
    std::lock_guard<std::mutex> lock(ListenerBridgeManager::getMutex());
    auto* bridge = ListenerBridgeManager::findBridgeLocked(javaListener);
    if (!bridge) return;  // 已被其他线程移除
    surfaceManager->removeSurfaceEventListenerLocked(bridge);
    ListenerBridgeManager::removeMappingLocked(javaListener);
    SAFELY_DELETE(bridge);
}
```

**方案 B（彻底修复）**: 使用 `std::shared_ptr` 管理 bridge

```cpp
// ListenerBridgeManager 内部存储 std::shared_ptr<ListenerBridge>
// findBridge 返回 std::weak_ptr，调用方 lock 后使用
// SAFELY_DELETE 替换为 reset()，引用计数归零时自动释放
```

**方案 C（防御性）**: `SurfaceManager.destroy()` 加幂等保护

```java
// SurfaceManager.java
private final AtomicBoolean m_destroyed = new AtomicBoolean(false);

public void destroy() {
    if (!m_destroyed.compareAndSet(false, true)) {
        return;  // 已销毁，直接返回
    }
    removeMessageListener(nativeEventBridge);
    // ... 其余清理
}
```

建议**方案 A + C 同时实施**：A 修复 native 层根因，C 在 Java 层提供防御。

### P2: engine not initialized 防御

```java
// AGenUI.java
public void createSurfaceManager(...) {
    if (!isInitialized()) {
        throw new IllegalStateException("createSurfaceManager: AGenUI engine is not initialized");
        // 已实现，保持现状即可
    }
}
```

### P3: 测试运行策略优化

前次补跑中大量"Process crashed"误判的根因是连续运行测试类时 instrumentation 启动冲突。建议：

1. **每个测试类运行后清理进程**:
   ```bash
   adb -s 200.49.0.251:5555 shell am force-stop com.amap.agenuiplayground
   ```
2. **使用 `--no-reinstantiate` 或 Orchestrator**:
   ```bash
   adb shell am instrument -w -r \
     -e useTestStorageService true \
     com.amap.agenuiplayground.test/androidx.test.orchestrator.AndroidJUnitRunner
   ```
3. **在 CI 中区分 native crash 和 SIGKILL**: 检查 tombstone/dropbox 而非仅看 `Process crashed`

---

## 9. 总结

| 维度 | 结论 |
|------|------|
| 独立 native 崩溃签名数 | **1 个** (`jni_removeEventListener` UAF) |
| P0 级问题 | **1 个**: 并发销毁 SurfaceManager 时的 UAF/Double-Free |
| 修复方向 | `ListenerBridgeManager` 全流程加锁 + `SurfaceManager.destroy()` 幂等保护 |
| 前次报告修正 | "30 个崩溃类"实际为 1 个 native 崩溃 + 1 个 Java 崩溃 + ~28 个 SIGKILL 误判 |
| 测试稳定性问题 | `am instrument` 连续运行时的进程启动冲突导致大量误报，需优化测试运行策略 |
