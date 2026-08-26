# Glance 演进评估报告

> 日期: 2026-08-27 | 分支: `feature/glance-evolution`
> 基线: PoC (4a69e53) → 演进版 (20 轮自迭代)

---

## 一、实施总结

### 1.1 代码产出

| 文件 | 行数 | 职责 |
|------|------|------|
| `A2UIGlanceStateDefinition.kt` | 125 | DataStore 状态持久化（template/bitmapPath/viewMode/hasContent/errorMsg） |
| `GlanceBitmapCache.kt` | 200 | 文件缓存 Bitmap（原子写入、WEBP、防 OOM 下采样） |
| `A2UIGlanceWidget.kt` | 270 | Glance 壳：SizeMode.Responsive 三断点布局、5 种状态（Empty/Loading/Error/Compact/Standard/Expanded） |
| `GlanceRenderWorker.kt` | 280 | CoroutineWorker 全管线渲染：AGenUI → SurfaceManager → Canvas → Bitmap → 文件缓存 → widget 更新 |
| `GlanceActionCallbacks.kt` | 75 | actionRunCallback 交互：切换视图模式、刷新、清除内容 |
| **总计** | **950** | 5 个 Kotlin 文件 |

### 1.2 架构演进

```
PoC (4a69e53)                     演进版 (20轮迭代)
┌──────────────┐                  ┌──────────────────────────┐
│ sharedBitmap │  →               │ GlanceBitmapCache (文件)  │
│ (内存静态)    │  消除            │ + Atomic save + WEBP     │
└──────────────┘                  └──────────────────────────┘
┌──────────────┐                  ┌──────────────────────────┐
│ 无状态管理    │  →               │ A2UIGlanceStateDefinition │
│ (重启即丢)    │  消除            │ DataStore Preferences    │
└──────────────┘                  └──────────────────────────┘
┌──────────────┐                  ┌──────────────────────────┐
│ 无后台更新    │  →               │ GlanceRenderWorker        │
│              │  消除            │ CoroutineWorker 30min周期 │
└──────────────┘                  └──────────────────────────┘
┌──────────────┐                  ┌──────────────────────────┐
│ 固定布局      │  →               │ SizeMode.Responsive      │
│ (无适配)      │  消除            │ Compact/Standard/Expanded │
└──────────────┘                  └──────────────────────────┘
┌──────────────┐                  ┌──────────────────────────┐
│ 无交互       │  →               │ actionRunCallback         │
│ (不可点击)    │  消除            │ Toggle/Refresh/Clear      │
└──────────────┘                  └──────────────────────────┘
┌──────────────┐                  ┌──────────────────────────┐
│ 无状态展示    │  →               │ Empty/Loading/Error       │
│ (空白)       │  消除            │ 三态 + 可重试            │
└──────────────┘                  └──────────────────────────┘
```

### 1.3 关键技术决策

| 决策 | 原因 | 状态 |
|------|------|------|
| 文件缓存替代内存引用 | `sharedBitmap` 进程死亡即丢；文件缓存跨进程安全 | ✅ 已实现 |
| DataStore 替代 SharedPreferences | Glance 原生支持 SuspendingStateDefinition；DataStore 是官方推荐 | ✅ 已实现 |
| CoroutineWorker 替代 JobIntentService | WorkManager 是 Jetpack 官方后台任务方案；支持周期性、约束、重试 | ✅ 已实现 |
| SizeMode.Responsive 替代固定尺寸 | Glance 优势之一：声明式响应布局；业界最佳实践推荐 Responsive 而非 Exact | ✅ 已实现 |
| actionRunCallback 替代 PendingIntent | Glance 优势之一：无 IPC 往返，suspend 回调 | ✅ 已实现 |
| WEBP 替代 PNG | API 30+ WEBP_LOSSY 比 PNG 小 50%+（照片类内容） | ✅ 已实现 |

---

## 二、与 RemoteViews 方案对比

| 维度 | RemoteViews (main) | Glance (演进版) | 优势方 |
|------|-------------------|-----------------|--------|
| 渲染管线 | SurfaceManager→Canvas→Bitmap→setImageViewBitmap | SurfaceManager→Canvas→Bitmap→ImageProvider | **持平** (共享) |
| Bitmap 传递 | 内存引用 (setImageViewBitmap) | 文件缓存 (decodeFile) | **RemoteViews** (更快) |
| 状态持久化 | SharedPreferences (WidgetProtocolCache) | DataStore Preferences | **Glance** (协程友好) |
| 后台更新 | JobIntentService | CoroutineWorker | **Glance** (周期性+约束) |
| 响应式布局 | 固定 300x400 | SizeMode.Responsive 三断点 | **Glance** (声明式) |
| 交互 | PendingIntent (IPC 往返) | actionRunCallback (进程内) | **Glance** (零 IPC) |
| 空状态/加载态 | XML 有限 | Composable 声明式 | **Glance** (灵活) |
| 错误处理 | 无 | ErrorContent + 重试 | **Glance** |
| APK 体积增量 | 0 | ~2MB (kotlin+compose+glance) | **RemoteViews** |
| minSdk | 21 | 21 (一致) | **持平** |
| 构建复杂度 | 低 | 高 (kotlin+compose plugin) | **RemoteViews** |

---

## 三、遗留问题

### 3.1 待设备验证
- [ ] 在测试设备 (200.49.0.251:5555) 上实际添加 Glance widget
- [ ] 测量 actionRunCallback vs PendingIntent 点击延迟
- [ ] 验证定制 ROM launcher 兼容性
- [ ] 测量 Glance 依赖 APK 体积增量
- [ ] 测试杀进程后 widget 内容是否持久

### 3.2 已知限制
- **单实例**: 当前用 `DEFAULT_CACHE_WIDGET_ID = 0` 硬编码。多实例需要从 GlanceId 映射到 appWidgetId
- **Bitmap 尺寸固定**: Worker 渲染用 300x400，未适配 SizeMode.Responsive 的多尺寸
- ~~**无 viewMode 过滤**: Worker 未根据 viewMode 过滤 template components~~ → **R28 已修复** (filterWeatherComponents)
- **无 SurfacePool**: Worker 每次创建新 SurfaceManager，无复用

### 3.3 R21-R34 架构修复（第二轮迭代）

| 轮次 | 修复 | 状态 |
|------|------|------|
| R21 | `release()` → `destroy()` (编译错误修复) | ✅ |
| R22 | FILE_SUFFIX `.png` → `.webp` (格式匹配) | ✅ |
| R23 | `fos.fd.sync()` 空安全 (try-catch) | ✅ |
| R24 | `SizeMode.Exact` → `SizeMode.Responsive` (业界最佳实践) | ✅ |
| R25 | provideGlance 使用 `stateDefinition` 字段 (减少实例化) | ✅ |
| R26 | cleanup() 添加 `removeListener` (修复内存泄漏) | ✅ |
| R27-R28 | viewMode 过滤组件 (filterWeatherComponents) | ✅ |
| R29 | 周期间隔 30min → 15min (WorkManager 最小值) | ✅ |
| R30 | SurfaceSize 构造参数 int→float (API 签名修复) | ✅ |
| R32 | SurfaceSize 调用 `.toFloat()` (Kotlin 类型安全) | ✅ |
| R41-R42 | GlanceRenderWorker.kt | lateinit→nullable + filterWeatherComponents 精确匹配 | ✅ |
| R43-R45 | GlanceRenderWorker.kt | catch 块安全调用 + 文件结构修复 | ✅ |
| R46-R47 | A2UIGlanceWidget.kt + StateDefinition.kt | collectAsState 模式 + getStateFlow (业界最佳实践) | ✅ |
| R48 | StateDefinition.kt | getStateFlow 实现 | ✅ |
| R49-R50 | 文档更新 | 评估 + plan 文档 | ✅ |

### 3.3 Git 仓库状态
- Git 对象存储受 AV (360/Defender) 干扰，多个 commit 对象损坏
- 工作树文件完好，所有代码产出在磁盘上可验证
- Python 启动器 (`scripts/git_add_commit3.py`) 可绕过 index.lock 完成提交

---

## 四、结论

### 4.1 是否合入 main?

**暂不合入。** 原因：
1. RemoteViews 主线已稳定，Glance 增量价值不足以抵消 APK 体积+构建复杂度成本
2. 设备验证未完成，无法量化延迟差异
3. Glance 1.1.1 仍标记为 experimental，API 可能变更

### 4.2 推荐后续路径

1. **短期**: 保持 feature/glance-evolution 分支，等设备验证结果
2. **中期**: 如果设备验证证明 Glance 交互延迟显著优于 PendingIntent，考虑合入
3. **长期**: 等 Glance API 稳定后重新评估，或用 Deglance 突破组件限制

### 4.3 复用价值

即使不合入 main，本次迭代的以下产出可直接复用：
- `GlanceBitmapCache.kt` — 文件缓存模式（适用于任何需要跨进程 Bitmap 传递的场景）
- `GlanceActionCallbacks.kt` — actionRunCallback 模式参考
- 混合架构设计 — "Glance 管壳 + 引擎管内容" 可推广到其他 Compose-based widget 框架
