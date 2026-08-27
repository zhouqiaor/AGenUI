# AGenUI 性能调优指南

## 已实现的优化

### R29: List 垂直虚拟化
- 100 项垂直列表从 O(100) 全量创建 → O(~5-10) 可视区创建
- 统一 RecyclerView + YogaLayoutManager + ComponentAdapter

### R31: Yoga 布局优化
- `removeNode`: O(pool) → O(childCount), 用 YGNodeGetContext 反向指针
- `calculateLayoutWithAdjust`: 无 Tabs 时跳过两遍布局

### R34: 跨 chunk 16ms coalescing
- 高频小 chunk: N 次 updateComponents → 1 次
- 减少 N-1 次全树 Yoga 布局

### R91: VirtualDOM findChild O(1)
- `_childIndex` unordered_map 替代线性扫描
- 100 sibling 树: ~50 比较 → 1 hash lookup

### stylesCache
- `extractStyles()` 缓存解析的 JSON Map
- styles key 变更时自动失效

### BatchScope
- 同 surfaceId 的 N 个 updateComponents 折叠为 1 次 Yoga 布局

## 性能基准计划

| 场景 | 指标 | 目标 |
|------|------|------|
| 100 项 List 创建 | 首帧延迟 | < 100ms |
| 1000 项 List 滚动 | FPS | > 55 |
| 流式 100 chunk | updateComponents dispatch | < 20 次 (coalescing) |
| Surface size 变更 | relayout 延迟 | < 16ms |
| 10 Surface 并发 | 内存增量 | < 50MB |

## 调优参数

| 参数 | 位置 | 默认值 | 说明 |
|------|------|--------|------|
| COALESCE_WINDOW_MS | StreamingContentParser.h | 16 | 跨 chunk 合并窗口 |
| prefetch | YogaLayoutManager | false | RV 预取开关 |
| itemAnimator | ListComponent | null | 关闭插入动画 |

## 未实现的优化 (P2-P3)

### Tabs 增量布局
当前 Tabs 变更触发全树 `YGNodeCalculateLayout`,应只对受影响子树计算。

### Yoga 节点池预分配
减少高频创建/销毁场景的 `YGNodeNew`/`YGNodeFree` 开销。

### SurfaceCoordinator 消息队列批量化
多个 surfaceId 的消息可批量处理,减少线程切换。
