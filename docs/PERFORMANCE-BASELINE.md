# AGenUI Widget 性能基线报告

> 生成时间：2026-08-27 00:54:37
> 测试设备：未连接设备（基于估算）
> 模板数量：10

---

## 1. 模板渲染时间（首次渲染）

| 模板 | 首次渲染时间 (ms) | 缓存命中 |
|------|------------------|---------|
| weather | N/A | ❌ 否 |
| agenda | N/A | ❌ 否 |
| todo | N/A | ❌ 否 |
| calendar | N/A | ❌ 否 |
| poll | N/A | ❌ 否 |
| note | N/A | ❌ 否 |
| notecard | N/A | ❌ 否 |
| meeting | N/A | ❌ 否 |
| classroom | N/A | ❌ 否 |
| flashcard | N/A | ❌ 否 |

- 无有效数据（设备未连接或日志未捕获）

## 2. Bitmap 缓存效果（第二次渲染 vs 第一次）

| 模板 | 第二次渲染 (ms) | 第二次缓存命中 | 加速比 |
|------|---------------|---------------|--------|
| weather | N/A | ❌ 否 |  |
| agenda | N/A | ❌ 否 |  |
| todo | N/A | ❌ 否 |  |
| calendar | N/A | ❌ 否 |  |
| poll | N/A | ❌ 否 |  |
| note | N/A | ❌ 否 |  |
| notecard | N/A | ❌ 否 |  |
| meeting | N/A | ❌ 否 |  |
| classroom | N/A | ❌ 否 |  |
| flashcard | N/A | ❌ 否 |  |

## 3. 预渲染（prerenderAll）效果

- **prerenderAll 总耗时**：N/A ms
- **预渲染后缓存大小**：N/A
- **预渲染后首次切换模板时间**：N/A ms
- **首次切换是否缓存命中**：❌ 否

## 4. 性能基线目标与达成情况

| 指标 | 目标 | 实测/估算 | 状态 |
|------|------|----------|------|
| 首次渲染平均时间 | ≤ 500 ms | N/A | ⚠️ |
| 缓存命中后渲染时间 | ≤ 50 ms | N/A | ⚠️ |
| prerenderAll 总耗时 | ≤ 3000 ms | N/A | ⚠️ |

## 5. 测量方法说明

### 5.1 渲染时间测量
从 logcat 提取 `renderSync: id=...,template=...` 时间戳作为渲染开始，
`Widget updated: ...` 时间戳作为渲染完成，两者差值即为单次渲染时间。

### 5.2 缓存命中率测量
对每个模板连续渲染两次：
- 第一次：冷渲染（无缓存），记录耗时
- 第二次：热渲染（有缓存），记录耗时
- 若第二次出现 `Bitmap cache HIT` 日志，则缓存命中

### 5.3 预渲染效果测量
启动 App 时自动触发 `prerenderAll`，预渲染所有模板到 Bitmap 缓存。
测量 prerenderAll 总耗时，以及预渲染完成后首次切换模板的时间。

## ⚠️ 数据说明

本次报告基于未连接设备模式生成，所有数据为 N/A。
连接设备后运行 `python scripts/measure_performance.py` 获取真实数据。
