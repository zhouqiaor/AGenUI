# Phase 3A — 性能优化 + 渐进渲染

> Worktree: `C:/Code/AGenUI-wt-phase3a` | 分支: `feature/phase3a-perf`
> 基线: `38270b7` (main, Phase 2 合入) | 制定: 2026-08-26

---

## 一、目标

解决 Phase 2 遗留的三大性能问题，使 widget 生成达到用户可感知的流畅体验。

| 指标 | 当前 | 目标 |
|------|------|------|
| 首字延迟 | 21-33s (qwen3.7-plus) | <3s (qwen-turbo) |
| 首组件渲染 | 等全部收完再渲染 | <1s (流式渐进) |
| Widget 尺寸 | 固定 300px | 自适应 widget 实际 size |
| Bitmap 内存 | 每次 createBitmap | 复用 + recycle |

---

## 二、任务拆分

### F1: 快速模型切换 + 多模型策略

**文件**: `WidgetLLMConfig.java`, `WidgetLLMClient.java`

- 新增 `qwen-turbo` 作为快速模型选项（百炼，首字延迟 ~1-2s）
- 模型优先级：turbo（默认快） → plus（质量备选） → doubao（兜底）
- `WidgetLLMConfig` 增加 `getModelByTier(tier)` 方法
- 验收：`qwen-turbo` 首字延迟 <3s

### F2: 流式渐进渲染验证 + 修复

**文件**: `WidgetRenderActivity.java`, `WidgetPartialParser.java`

- 当前问题：`onChunk` 调了 `receiveTextChunk` + `scheduleRefresh`，但实际 bitmap 只在"生成中"占位和最终结果切换
- 需验证：LLM 流式过程中，partialParser 提取的 JSON chunk → SurfaceManager 增量构建 → measure+draw → bitmap 推送
- 日志确认 `doRefresh` 在流式过程中被调用且输出了非占位 bitmap
- 验收：流式过程中 widget 内容肉眼可见渐进式更新（非一次性出现）

### F3: Widget 尺寸自适应

**文件**: `WidgetRenderActivity.java` (surfaceSize 方法), `A2UIWidgetProvider.java`

- `surfaceSize()` 从固定 `SurfaceSize(300, 0)` 改为读取 widget 实际尺寸
- 用 `AppWidgetManager.getAppWidgetOptions()` 获取 portrait/landscape 尺寸
- 按密度转 a2ui 逻辑单位（pv * 2）
- 验收：不同尺寸 widget 正确自适应渲染

### F4: Bitmap 内存管理

**文件**: `WidgetRenderActivity.java`

- 流式刷新过程中复用同一个 Bitmap（避免频繁 alloc）
- 最终渲染后 `bitmap.recycle()`
- >800KB 自动 JPEG 压缩已有，确认路径正常
- 验收：连续 10 次生成无 OOM，GC 频率不异常

### F5: Prompt 调优 — 动态 few-shot

**文件**: `WidgetPromptBuilder.java`, `WidgetHistoryRepository.java`

- 从历史记录中取 success=true 的高分样本作为 few-shot
- 按关键词分类（天气/待办/日程/通用）选对应 few-shot
- 验收：LLM 生成合法 JSON 概率 >80%（20 次统计）

---

## 三、验收标准

- [ ] `qwen-turbo` 首字延迟 <3s
- [ ] 流式渐进渲染：每 500ms-1s 刷新，widget 内容渐进式更新
- [ ] Widget 尺寸自适应（不同 widget size 正确渲染）
- [ ] 连续 10 次生成无 OOM
- [ ] LLM 合法 JSON 概率 >80%（20 次统计）

---

## 四、技术约束

- 构建命令: `cd playground/android && ANDROID_HOME=/c/Programs/Android/Sdk ./gradlew assembleDebug`
- 测试设备: `200.49.0.251:5555`
- adb 命令必须带 `-s 200.49.0.251:5555`
- 不引入新第三方依赖
- 只在 `feature/phase3a-perf` 分支提交
