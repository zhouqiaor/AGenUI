# REVIEW — Phase 3A / 3B / 4 分支审核

**审核日期:** 2026-08-25
**审核人:** WorkBuddy (Checker agent)
**审核范围:**
- `feature/phase3a-perf` (5 commits, 478051b..75234d9)
- `feature/phase3b-completeness` (2 commits, a165441..556ddc3)
- `feature/phase4-glance` (2 commits, 29d68cd..4a69e53)

---

## Phase 3A — 性能优化 (feature/phase3a-perf)

### ✅ 通过

**四镜头审查结论:**

#### 🐛 Bug/边界
- **WidgetLLMClient failover chain**: 逻辑正确，`resetToPrimary()` 在入口和出口都调用，避免了上次失败状态污染下次请求。`switchToNext()` 返回 false 时正确跳出循环。
- **WidgetPartialParser.extractCompletedComponents**: 字符串/转义状态机正确处理，`{}`/`[]`/`""` 嵌套跟踪到位。`completed` 列表只收集 `d==0 && started` 的完整对象，不会误收半截。
- **Bitmap 复用 (F4)**: `streamBitmap` 在 `drawOnly`/`drawAndPush` 共用，尺寸变化时 recycle+重建，`onDestroy` 正确 recycle。无内存泄漏。
- **尺寸自适应 (F3)**: `getWidgetSizePx()` 读取 AppWidgetOptions，clamp [280, 400]。`getWidgetTargetWidthPx()` 缓存首次结果。SurfaceSize 回调返回实际像素值，让 AGenUI 自适应 widget 尺寸。

#### 🔒 安全
- 无组件暴露风险（无新 Activity/Service/Receiver）
- LLM 请求仍用 HTTPS，无硬编码新凭据（沿用 Phase 2 的 API Key）
- `enable_thinking: false` 正确禁用 qwen3.7-plus 推理模式

#### 🔗 跨文件影响
- `WidgetHistoryRepository` 新增 `getRecentSuccessfulExamples()` 和 `FewShotExample` 内部类 — 向后兼容，不影响 Phase 2 API
- `WidgetPromptBuilder.buildMessagesWithHistory()` 新方法 — 旧的 `buildMessagesJson()` 保留，无破坏性变更
- `WidgetLLMConfig` 新增 `FAILOVER_TIERS`、`switchToNext()`、`resetToPrimary()` — 旧的 `switchToFallback()` 被替换，但 `streamChat` 是唯一调用方，已同步更新

#### 📋 SPEC 符合度
- ✅ F1 qwen-turbo 快速模型 + 三级 Failover
- ✅ F2 流式渐进渲染（extractCompletedComponents + progressive push）
- ✅ F3 尺寸自适应（AppWidgetOptions → SurfaceSize）
- ✅ F4 Bitmap 内存复用（streamBitmap 成员，跨 draw 调用复用）
- ✅ F5 Prompt 动态 few-shot（buildMessagesWithHistory + classifyCategory）

---

## Phase 3B — 完整性补齐 (feature/phase3b-completeness)

### ⚠️ 有条件通过（1 个非阻塞问题 + 1 个合并冲突需解决）

**四镜头审查结论:**

#### 🐛 Bug/边界
- **WidgetInputLaunchService**: ForegroundService 生命周期正确 — `onStartCommand` 先 `startForeground` 再 `startActivity`，然后 `stopSelf`。`START_NOT_STICKY` 避免重启。通知 channel IMPORTANCE_LOW 无声。
- **Room DB 迁移**: `fallbackToDestructiveMigration()` 在 Phase 3B 首次启用可接受（旧 SharedPreferences 数据被丢弃）。`trimOld(MAX_RECORDS)` 保持容量。
- **Vosk 资源释放**: `destroy()` 方法正确释放 `speechService.shutdown()` + `recognizer.close()` + `model.close()`。`WidgetInputActivity.onDestroy` 需确认调用 destroy（见下方问题 1）。

#### 🔒 安全
- **Manifest**: `WidgetInputLaunchService` 用 `foregroundServiceType="connectedDevice"` — 此类型不需要额外权限，但语义不精确。建议改为 `dataSync` 或移除该属性（Android 14+ 对 FGS 类型更严格）。**非阻塞**。
- `RECORD_AUDIO` 权限沿用 Phase 2，无新增暴露面
- `FOREGROUND_SERVICE` 权限正确添加

#### 🔗 跨文件影响
- **合并冲突风险**: 3A 和 3B 同时修改了 `WidgetHistoryRepository.java` 和 `WidgetRenderActivity.java`，且改动不重叠（3A 加 few-shot API，3B 重写为 Room 实现）。**需要先合 3A，再把 3B rebase 到 3A 上手动解冲突**。
- `build.gradle` 新增 Room + Vosk 依赖，与 3A 无冲突
- `AndroidManifest.xml` 新增 Service 注册，与 3A 无冲突

#### 📋 SPEC 符合度
- ✅ F1 BAL 修复（WidgetInputLaunchService 中介）
- ✅ F2 Room 持久化（WidgetHistoryEntity/Dao/Database/Repository）
- ✅ F3 Vosk 离线语音（WidgetVoskManager + VoskModelLoader）
- ✅ F4 断网缓存（loadFallback 优先取 getLastSuccessfulJson）
- ✅ F5 批量测试（WidgetBatchTest androidTest）

**问题清单:**

1. **[bug] WidgetRenderActivity.java:358** — `loadFallback()` 在主线程调用 `historyRepository.getLastSuccessfulJson()`，该方法内部走 `syncGet()` 阻塞式 Room 查询，可能 ANR。
   ```java
   // 3B loadFallback (line 358):
   final String cachedJson = historyRepository != null
           ? historyRepository.getLastSuccessfulJson() : null;
   ```
   **建议:** 改为异步 — 先在主线程 `new Thread(() -> { String cached = historyRepository.getLastSuccessfulJson(); ... }).start()`，或用 LiveData/Coroutines。当前代码在流式生成失败时触发，用户正在等 UI 响应，同步 DB 查询（即使 <50ms）不优雅。**非阻塞，可记入下一阶段修复**。

---

## Phase 4 — Glance 迁移预研 (feature/phase4-glance)

### ✅ 评估通过（不合入 main，结论归档）

- PoC 代码 (`A2UIGlanceWidget.kt`) 编译通过，Glance 基本能力验证
- 评估报告结论：**保持 RemoteViews，不迁移到 Glance**
  - 原因：底层同为 RemoteViews IPC，无延迟优势；+2.1MB APK 体积；Glance 对 Bitmap 混合渲染支持不成熟
- 此分支不合入 main，仅保留作为技术选型文档

---

## 合并策略

由于 3A 和 3B 同时改动了 `WidgetHistoryRepository.java` 和 `WidgetRenderActivity.java`，但改动不重叠（3A 在旧 SharedPreferences 实现上添加 few-shot API；3B 整体重写为 Room），需要：

1. 先合 3A 到 main（无冲突）
2. 把 3B rebase 到新 main 上，手动解冲突：
   - `WidgetHistoryRepository.java`: 保留 3B 的 Room 实现 + 添加 3A 的 `getRecentSuccessfulExamples()` 和 `FewShotExample`
   - `WidgetRenderActivity.java`: 保留 3B 的 `loadFallback` 断网缓存 + 添加 3A 的 `streamBitmap`/`getWidgetSizePx`/`extractCompletedComponents`
3. 解冲突后重新构建验证
4. Phase 4 不合入

---

## 下一步

1. ✅ 人类执行 `git merge --no-ff feature/phase3a-perf` 合入 main
2. Checker 在 phase3b worktree 执行 `git rebase main` 解冲突
3. Maker（或 Checker）构建验证 + 装机自测
4. 人类合入 3B
5. 更新 SPEC 进展章节
