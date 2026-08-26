# Glance 演进技术方案

> Worktree: `C:/Code/AGenUI-wt-glance` | 分支: `feature/glance-evolution`
> 基线: `4a69e53` (Phase 4 PoC) | 制定: 2026-08-26
> 更新: 2026-08-27 — 20轮自迭代 + 架构检视 + 业界调研 + R21-R40 修复迭代

---

## 零、迭代实施状态

### Sprint 1: 状态持久化 + 文件缓存 Bitmap ✅ 完成

| 轮次 | 文件 | 改动 |
|------|------|------|
| R1 | `libs.versions.toml` + `build.gradle` | kotlin/compose/glance/datastore/work-runtime 依赖声明 |
| R2 | `A2UIGlanceStateDefinition.kt` | DataStore 状态持久化（template/bitmapPath/viewMode/hasContent/lastUpdateTs），修复 edit() 调用 |
| R3 | `GlanceBitmapCache.kt` | 原子写入（temp+rename）、WEBP 压缩、inSampleSize 防止 OOM |
| R4-5 | `A2UIGlanceWidget.kt` | 重写：移除 sharedBitmap、stateDefinition override、SizeMode.Exact、三布局（Compact/Standard/Expanded） |

### Sprint 2: WorkManager 后台更新 + 响应式布局 ✅ 完成

| 轮次 | 文件 | 改动 |
|------|------|------|
| R6-8 | `GlanceRenderWorker.kt` | CoroutineWorker 全管线：SurfaceManager→Surface→Canvas→Bitmap→文件缓存→状态更新→widget update |
| R9-10 | `A2UIGlanceWidget.kt` | Receiver onEnabled/onDisabled 管理 Worker 生命周期；修复 GlanceId→cacheId 映射 |

### Sprint 3: Glance 原生组件混合 + 交互增强 ✅ 完成

| 轮次 | 文件 | 改动 |
|------|------|------|
| R11-12 | `GlanceActionCallbacks.kt` | ToggleViewModeAction、RefreshAction、ClearContentAction（actionRunCallback） |
| R13-14 | `A2UIGlanceWidget.kt` + `widget_info.xml` + `strings.xml` | 可点击按钮（切换/刷新）、空状态"点击生成"、widget info XML 更新 |

### Sprint 4: 对比验证 + 评估报告 ✅ 完成

| 轮次 | 文件 | 改动 |
|------|------|------|
| R15-17 | `GlanceActionCallbacks.kt` + `A2UIGlanceWidget.kt` | 清理 action callback import、manifest 验证 |
| R18 | `A2UIGlanceStateDefinition.kt` + `A2UIGlanceWidget.kt` | ErrorState（errorMsg、ErrorContent 渲染、Worker 错误时更新状态） |
| R19 | `GLANCE-EVOLUTION-PLAN.md` | 本节实施状态更新 |
| R20 | `GLANCE-EVOLUTION-EVALUATION.md` | 最终评估报告（见下方） |

### Sprint 5: 架构检视 + 业界调研 + 修复迭代 ✅ 完成

| 轮次 | 文件 | 改动 |
|------|------|------|
| R21 | `GlanceRenderWorker.kt` | 修复编译错误: `release()` → `destroy()` + 添加 `removeListener` |
| R22 | `GlanceBitmapCache.kt` | FILE_SUFFIX `.png` → `.webp` (格式与内容匹配) |
| R23 | `GlanceBitmapCache.kt` | `fos.fd.sync()` 包裹 try-catch (防 SyncFailedException) |
| R24 | `A2UIGlanceWidget.kt` | `SizeMode.Exact` → `SizeMode.Responsive` + DpSize 三断点 (业界最佳实践) |
| R25 | `A2UIGlanceWidget.kt` | provideGlance 使用 `stateDefinition` 字段 (减少实例化) |
| R26-27 | `GlanceRenderWorker.kt` | listener 提取为成员变量 + cleanup 正确移除 (修复内存泄漏) |
| R28 | `GlanceRenderWorker.kt` | 添加 `filterWeatherComponents()` viewMode 过滤逻辑 |
| R29 | `GlanceRenderWorker.kt` | 周期 30min → 15min (WorkManager 最小值) |
| R30-32 | `GlanceRenderWorker.kt` | SurfaceSize 构造参数 int→float (API 签名修复) |
| R33-34 | `A2UIGlanceWidget.kt` | 移除未用 import + 文档注释更新 |
| R35-36 | `GLANCE-EVOLUTION-EVALUATION.md` | 文档更新: SizeMode.Responsive + R21-R34 修复表 |
| R37-38 | `GlanceRenderWorker.kt` | 文档注释更新 + viewMode 状态写入验证 |
| R39 | `GlanceActionCallbacks.kt` | ToggleViewMode 添加 clearError() 确保切换后无残留错误 |
| R40 | `GLANCE-EVOLUTION-PLAN.md` | 本节更新 |

### Sprint 6: 第二轮架构检视 + 业界调研 + collectAsState 模式 ✅ 完成

| 轮次 | 文件 | 改动 |
|------|------|------|
| R41 | `GlanceRenderWorker.kt` | `lateinit var` → `nullable var` (防 UninitializedPropertyAccessException) |
| R42 | `GlanceRenderWorker.kt` | filterWeatherComponents 精确匹配 (setOf 替代 startsWith) |
| R43-R45 | `GlanceRenderWorker.kt` | catch 块修复 + drawSurfaceToBitmap 方法签名恢复 |
| R46 | `A2UIGlanceWidget.kt` | provideGlance 改为 collectAsState 模式 (业界最佳实践) |
| R47-R48 | `A2UIGlanceStateDefinition.kt` | 添加 `getStateFlow()` 方法 |
| R49-R50 | `GLANCE-EVOLUTION-PLAN.md` + `EVALUATION.md` | 文档更新 |

---

## 一、现状与目标

### 1.1 现状

Phase 4 预研已完成 Glance 环境搭建和 API 验证，结论为"保持 RemoteViews 不迁移"。但 PoC 代码保留在独立分支，具备继续演进的基础。

当前 PoC 的局限：
- `sharedBitmap` 静态变量跨进程传递 — **不可靠**（进程死亡即丢失）
- 仅验证了 API 编译通过，未在设备上实际渲染
- 无状态管理（`GlanceStateDefinition`）
- 无后台更新机制（WorkManager）
- 无响应式布局（SizeMode.Exact 固定尺寸）

### 1.2 目标

在独立 worktree 中持续迭代 Glance 实现，探索 Glance 能为 AGenUI Widget 带来的增量价值，同时不干扰 main 分支的 RemoteViews 主线。

**核心策略：混合架构 — Glance 管壳 + AGenUI Bitmap 管内容**

---

## 二、业界开源项目调研结果

### 2.1 可直接参考的项目

| 项目 | Stars | 价值点 | 复用方式 |
|------|-------|--------|----------|
| **google/glance-experimental-tools** | 184 | 官方实验性工具：`appwidget-host`（应用内预览 RemoteViews）、`appwidget-viewer`（调试快照工具）、`appwidget-testing`（截图测试 Activity）、`appwidget-configuration`（M3 配置界面） | **参考架构**，不引入依赖（API 不稳定） |
| **android/platform-samples** (App Widgets) | 官方 | 官方 Widget 示例集：Weather Widget（状态管理 + WorkManager）、Image Widget（Bitmap 加载）、Canonical Layouts（响应式布局模式） | **直接参考代码模式** |
| **android/user-interface-samples** (Glance) | 官方 | Glance Widget 架构文档 + Weather/Image 示例的 DeepWiki 解析 | **架构参考** |

### 2.2 可学习模式的项目

| 项目 | Stars | 借鉴点 |
|------|-------|--------|
| **LiteKite/Android-AppWidgets** | 8 | AppWidget + WorkManager + Glance 混合实现，展示了 RemoteViews 和 Glance 共存的工程结构 |
| **binayshaw7777/Kalenget** | 12 | 纯 Glance 日历 Widget，Calendar 数据绑定 + 响应式布局 |
| **immortal-forest/spotify-widget** | 6 | Glance + 远程图片加载（Spotify API），Image 组件的网络图片处理模式 |
| **cvb941/Deglance** | 4 | 在 Glance 中使用任意 Composable — 突破 Glance 组件限制的实验 |

### 2.3 不参考的项目

- **DelMonteAJ/community-widgets** — 这是 Glance (非 Android Glance) 桌面仪表盘的社区 Widget 集合，与 Android Jetpack Glance 无关，不参考。

### 2.4 关键技术发现

1. **Stateful Glance 模式** (来自 Michael Samuel 的博客)：Glance Widget 不是"运行中的 UI"，而是"持久化状态的快照渲染"。状态必须持久化到 `DataStore`/`Preferences`，不能依赖内存。这与 AGenUI 的 `WidgetProtocolCache` (SharedPreferences) 理念一致。

2. **WorkManager 集成** (来自官方 Weather Widget 示例)：`CoroutineWorker` 负责周期性获取数据 → 更新 `GlanceStateDefinition` → 调用 `widget.update()`。AGenUI 已有 `AGenUIWidgetRenderService` (JobIntentService)，可自然映射。

3. **Bitmap 加载模式** (来自官方 Image Widget + Spotify Widget)：Glance 的 `ImageProvider(bitmap)` 需要在 `provideGlance` 中准备好 Bitmap，不能在 Composable 内异步加载。AGenUI 的 `SurfaceManager → View.draw(Canvas) → Bitmap` 管线可在 `provideGlance` 的 suspend 阶段完成。

4. **Deglance 突破**：可在 Glance 内嵌入任意 Composable，理论上可以嵌入 AGenUI 的 View 树渲染结果（但这是实验性方案，不作为主线）。

---

## 三、混合架构方案

### 3.1 架构图

```
┌─────────────────────────────────────────────────┐
│                 Glance Widget 层                  │
│  A2UIGlanceWidget (GlanceAppWidget)              │
│  ├── provideGlance: suspend 加载 Bitmap            │
│  ├── GlanceStateDefinition: 持久化 widget 状态     │
│  └── provideContent: Composable 渲染              │
│      ├── Title (Glance Text)                      │
│      ├── Image (ImageProvider(bitmap))            │
│      └── Action buttons (actionRunCallback)       │
├─────────────────────────────────────────────────┤
│              状态持久化层                           │
│  PreferencesGlanceStateDefinition                 │
│  ├── template JSON (widget 内容)                  │
│  ├── bitmap URI (文件路径, 非内存引用)              │
│  └── view mode (current/forecast)                │
├─────────────────────────────────────────────────┤
│              后台更新层                             │
│  GlanceRenderWorker (CoroutineWorker)             │
│  ├── 读取 template JSON                            │
│  ├── SurfaceManager → View.draw(Canvas) → Bitmap  │
│  ├── Bitmap 写入 cacheFile                         │
│  └── updateAppWidgetState → widget.update()      │
├─────────────────────────────────────────────────┤
│              AGenUI 核心层 (复用)                   │
│  SurfaceManager / Token / ComponentRegistry       │
│  (与 RemoteViews 路径完全共享)                     │
└─────────────────────────────────────────────────┘
```

### 3.2 与 RemoteViews 方案的对比

| 层 | RemoteViews (当前) | Glance 演进版 |
|----|-------------------|--------------|
| Widget 壳 | `RemoteViews` XML + `setImageViewBitmap` | `GlanceAppWidget` + `ImageProvider(bitmap)` |
| 状态 | `WidgetProtocolCache` (SharedPreferences) | `PreferencesGlanceStateDefinition` (DataStore) |
| 后台更新 | `AGenUIWidgetRenderService` (JobIntentService) | `GlanceRenderWorker` (CoroutineWorker) |
| 渲染触发 | `PendingIntent.send()` → Activity → draw → push | `widget.update()` → `provideGlance` → draw → provideContent |
| Bitmap 传递 | 内存直接传 `setImageViewBitmap` | 文件缓存 + `BitmapFactory.decodeFile` |
| 响应式 | 固定 300px 宽 | `SizeMode.Exact` + 多布局适配 |

### 3.3 核心改进点

**解决 PoC 的 `sharedBitmap` 问题**：

PoC 用 `@Volatile var sharedBitmap: Bitmap?` 跨进程传递 — 进程死亡即丢失。演进版改为文件缓存：

```kotlin
// Worker 中：SurfaceManager 渲染 → Bitmap → 写文件
val bitmap = renderToBitmap(templateJson)
bitmap.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(cacheFile))

// provideGlance 中：读文件 → Bitmap
val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
provideContent { BitmapContent(bitmap) }
```

---

## 四、迭代计划

### Sprint 1: 状态持久化 + 文件缓存 Bitmap (核心基础)

**目标**：解决 PoC 的 sharedBitmap 可靠性问题，实现真正的 Glance Widget 端到端渲染。

| 任务 | 文件 | 说明 |
|------|------|------|
| 引入 DataStore | `build.gradle` | `androidx.datastore:datastore-preferences` |
| 实现 GlanceStateDefinition | `A2UIGlanceStateDefinition.kt` | 持久化 template JSON + bitmap 文件路径 + viewMode |
| 重写 provideGlance | `A2UIGlanceWidget.kt` | 从 DataStore 读取状态，从文件加载 Bitmap |
| Bitmap 文件缓存 | `GlanceBitmapCache.kt` | `renderToBitmap()` → `cacheFile` → `decodeFile()` |
| 注册 StateDefinition | `A2UIGlanceWidget.kt` | `override val stateDefinition` |

**验收**：Widget 添加到桌面后，杀进程再重开，内容仍在。

### Sprint 2: WorkManager 后台更新 + 响应式布局

**目标**：实现 Glance 版本的后台渲染管线，支持多尺寸适配。

| 任务 | 文件 | 说明 |
|------|------|------|
| GlanceRenderWorker | `GlanceRenderWorker.kt` | `CoroutineWorker` → 读 template → SurfaceManager 渲染 → Bitmap 写文件 → `updateAppWidgetState` → `widget.update()` |
| 注册周期任务 | `A2UIGlanceWidgetReceiver.kt` | `onEnabled` 启动 Worker，`onDisabled` 取消 |
| SizeMode.Exact | `A2UIGlanceWidget.kt` | 根据 widget 尺寸选择布局模式（compact/expanded） |
| 响应式布局 | `A2UIGlanceWidget.kt` | `LocalSize.current` → compact(标题+Bitmap) / expanded(标题+Bitmap+按钮组) |

**验收**：resize widget 时布局自动切换；后台 30 分钟自动刷新。

### Sprint 3: Glance 原生组件混合 + 交互增强

**目标**：在 Bitmap 之上叠加 Glance 原生组件，获得 Compose 声明式交互优势。

| 任务 | 文件 | 说明 |
|------|------|------|
| 混合布局 | `A2UIGlanceWidget.kt` | `Column { Title(Text) + Image(Bitmap) + Buttons(Text clickable) }` |
| 点击交互 | `A2UIGlanceWidget.kt` | `actionRunCallback` → 切换 viewMode / 刷新 / 打开 Activity |
| 加载状态 | `A2UIGlanceWidget.kt` | 状态为 Loading 时显示 Glance 原生占位（Text + CircularProgressIndicator 等效） |
| 空状态 | `A2UIGlanceWidget.kt` | 无 template 时显示引导文案 + "生成 Widget" 按钮 |

**验收**：点击 Glance 按钮 → viewMode 切换 → Widget 重新渲染；加载中显示占位而非空白。

### Sprint 4: 对比验证 + 评估报告

**目标**：与 RemoteViews 方案做端到端对比，产出是否合入 main 的决策依据。

| 任务 | 说明 |
|------|------|
| 延迟对比 | PendingIntent vs actionRunCallback（设备实测） |
| 内存对比 | Widget 刷新前后内存 dump |
| APK 体积 | Glance 依赖增量精确测量 |
| 设备兼容 | 定制 ROM launcher 能否添加 Glance widget |
| 评估报告 | 更新 `PHASE4-GLANCE-EVALUATION.md` |

---

## 五、技术约束

- Glance 版本：`1.1.1`（保持与 PoC 一致）
- Kotlin：`2.0.21` / Compose BOM：`2024.09.03`（保持与 PoC 一致）
- minSdk：`21`（与 RemoteViews 方案一致）
- 测试设备：`200.49.0.251:5555`（定制 ROM，注意 launcher 兼容性）
- AGenUI 核心（SurfaceManager 等）**不修改**，只复用
- 不干扰 main 分支的 RemoteViews 主线

---

## 六、参考来源

| 来源 | URL |
|------|-----|
| Google glance-experimental-tools | https://github.com/google/glance-experimental-tools |
| Android platform-samples (App Widgets) | https://deepwiki.com/android/platform-samples/3.3-app-widgets |
| Android user-interface-samples (Glance) | https://deepwiki.com/android/user-interface-samples/4.2-glance-widgets |
| Weather Widget Example (官方) | https://deepwiki.com/android/user-interface-samples/4.2.1-weather-widget-example |
| Stateful Glance App Widgets (博客) | https://blog.michaelsam94.com/android-jetpack-glance-appwidgets-state |
| Glance Manage and Update (官方文档) | https://developer.android.google.cn/develop/ui/compose/glance/glance-app-widget |
| Build UI with Glance (官方文档) | https://developer.android.google.cn/jetpack/compose/glance/build-ui |
