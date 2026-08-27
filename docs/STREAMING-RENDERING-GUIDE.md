# AGenUI 流式渲染指南

## 概述

AGenUI 的核心特性是原生流式渲染 — LLM 输出的 JSON 可以逐 chunk 解析和渲染,无需等待完整响应。

## 三段式流式协议

```
beginTextStream()  → 初始化解析器状态
receiveTextChunk()  → 逐 chunk 接收并解析 (可多次调用)
endTextStream()    → 结束流,刷新缓冲,清理状态
```

### 协议示例

```kotlin
val sm = engine.createSurfaceManager()
sm.beginTextStream()

// LLM 输出的每个 chunk
for (chunk in llmStream) {
    sm.receiveTextChunk(chunk)
}

sm.endTextStream()
```

## Chunk 解析

`StreamingContentParser` 在每个 `receiveTextChunk` 中:

1. `extractor.appendData(chunk)` — 追加到缓冲区
2. `extractor.driveParser()` — 提取完整的 JSON 消息
3. `tryCrossChunkCoalesce(results)` — 跨 chunk 合并 (R34)
4. `dispatchParseResultsBatched(results)` — 批量分发

### 消息类型

| 类型 | 描述 |
|------|------|
| createSurface | 创建 Surface |
| updateComponents | 更新/新增组件 |
| updateDataModel | 更新数据模型 |
| appendDataModel | 追加数据模型 |
| deleteSurface | 删除 Surface |

## 跨 Chunk Coalescing (R34)

16ms 帧级时间窗口合并:

```
Chunk 1: [ComponentUpdate A, ComponentUpdate B] → pending
   ↓ 16ms 内
Chunk 2: [ComponentUpdate C]
   → 合并: [A, B, C] → 1 次 updateComponents dispatch
```

### 刷新时机

| 事件 | 动作 |
|------|------|
| NormalEvent | 立即刷新 pending |
| >16ms gap | 下一 chunk 到达时刷新 pending |
| endTextStream | 刷新所有 pending |
| 不同 surfaceId | 刷新 pending |

## 组件延迟创建

List 组件的虚拟化 (R29):

- `shouldCreateChildView() = false` — 延迟 createView
- `createChildViews()` — no-op, 不递归创建子组件
- `ComponentAdapter.onBindViewHolder` — 滚动到可视区时才创建

### 收益

| 场景 | 无虚拟化 | 有虚拟化 |
|------|---------|---------|
| 100 项列表 | 100 次 createView | ~5-10 次 |
| 内存 | O(n) | O(viewport) |
| 首帧延迟 | O(n) | O(viewport) |
