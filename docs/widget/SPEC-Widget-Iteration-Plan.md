# AGenUI 桌面小组件 + 输入交互 — 迭代计划

> 基于 `poc_v1/` 现有分析文档 + 业界开源项目调研，制定从技术验证到产品化的完整迭代路线。
>
> 制定日期：2026-08-25 | 状态：初版

---

## 一、现状分析总结

### 1.1 已有资产（poc_v1/）

| 资产 | 行数 | 核心价值 |
|------|------|----------|
| `engine.js` | 2278 | 完整 LLM 渲染引擎：36 组件 Catalog、SSE 流式、多模型 Failover、增量 JSON 解析、三级降级链 |
| `server.js` | 53K | 三端投屏服务（大屏/手机/平板）、SSE 推送、多屏状态同步、断线补发 |
| `agenui-widget-design.html` | 1270 | Widget 适配方案：Bitmap 渲染桥接架构、四种渲染路径评估、Phase 1/2 路线图 |
| `widget-input-opensource-analysis.html` | 1965 | 语音/文件/轻量 Activity 开源方案：Vosk+VAD、SAF+DocumentReader、透明 Activity |
| `widget-opensource-interaction-analysis.html` | 1813 | Widget 交互方案：RemoteViews 能力边界、PendingIntent 跨进程、三链路统一入口 |
| `web-renderer-design.html` | 577 | Web 端渲染器：复用 @a2ui/react + @a2ui/web_core，esm.sh CDN 零安装 |
| `agenui-converter.js` | 184 | AGenUI 扁平组件 → A2UI 嵌套信封转换器 |

### 1.2 核心技术决策（已确认）

1. **渲染路径**：Bitmap 渲染桥接（方案 B）— App 进程内 AGenUI 引擎渲染 → `View.draw(Canvas)` → Bitmap → `setImageViewBitmap()`
2. **输入交互**：透明 Activity + PendingIntent 跨进程 — 文字/语音/文件三链路统一入口
3. **语音识别**：Vosk (离线) + WebRTC VAD — 点击即用、首字延迟 <200ms
4. **文件导入**：Android SAF + DocumentReader — 零依赖文件选择 + 全格式解析
5. **流式渲染**：LLM SSE → `receiveTextChunk` 增量构建 → 周期性 `renderAndRefresh` → Bitmap 增量刷新
6. **Web 预览**：esm.sh CDN 加载 @a2ui/react — 浏览器内实时预览，无需真机

### 1.3 关键技术风险

| 风险 | 等级 | 缓解方案 | 状态 |
|------|------|----------|------|
| R1: SurfaceManager 需 Activity Context | 高 | Service Context + ContextThemeWrapper 或透明 Activity 中转 | ✅ 已解决（透明 Activity） |
| R2: Bitmap 跨进程传输 1MB 限制 | 中 | >800KB 降级 setImageViewUri + FileProvider | ✅ 暂不触发（240-324KB） |
| R3: 线程同步（主线程回调 vs 后台渲染） | 中 | CountDownLatch + 5s 超时 + Handler.post 同步 | ✅ 已解决 |
| R4: LLM 输出不确定性 | 中 | 三级校验 + 降级模板 + few-shot 优化 | ⏳ Phase 2 验证 |
| R5: Widget 更新频率 30min 限制 | 低 | 代码主动调用 `updateAppWidget()` 无限制 | ✅ 无限制确认 |

---

## 二、迭代计划

### Phase 0：技术验证初版（Web 端原型）⬅️ **当前执行**

**目标**：在浏览器中验证"用户输入 → LLM 生成 A2UI → 流式渲染 → Widget 预览"的完整技术链路，不依赖 Android 真机。

**技术选型**：
- 复用 `engine.js`（LLM 引擎）+ `server.js`（SSE 服务）
- 新增 `widget-poc.html`（Widget 模拟器 + 输入交互 UI）
- 通过 esm.sh CDN 加载 `@a2ui/react` + `@a2ui/web_core` 做真实 A2UI 渲染

**验收标准**：
- [ ] 页面加载 <3s，显示 Widget 模拟器（桌面小组件尺寸 4×3 格）
- [ ] 文字输入 → LLM 流式生成 → A2UI 组件逐步渲染到 Widget 区域
- [ ] 模板切换：至少 3 种内置模板（天气/议程/待办）可一键切换
- [ ] 流式效果：LLM 生成过程中 Widget 内容渐进式更新（非一次性出现）
- [ ] 降级链路：LLM 失败时自动降级到关键词匹配模板
- [ ] 设计令牌：遵循鸿蒙 Design（品牌色 #007DFF、圆角 12/16、柔和阴影）

**交付物**：`widget-poc.html`（自包含单文件）

---

### Phase 1：Android Widget 骨架（Bitmap 链路验证）✅ 已完成

**目标**：在 Android 真机上验证 Bitmap 渲染桥接链路 — 从 A2UI 协议到桌面 Widget 显示。

**验证日期**：2026-08-25

**模块**：
| 模块 | 职责 | 状态 |
|------|------|------|
| `A2UIWidgetProvider` | AppWidgetProvider，RemoteViews 构建、按钮绑定 | ✅ |
| `AGenUIWidgetRenderService` | JobIntentService，启动渲染 Activity | ✅ |
| `WidgetRenderActivity` | 透明 Activity：SurfaceManager + measure/draw + Bitmap | ✅ |
| `WidgetProtocolCache` | SharedPreferences 存储当前模板/协议 JSON | ✅ |
| `WidgetProtocolTemplates` | assets 模板加载 + surfaceId 替换 | ✅ |
| `a2ui_widget_content.xml` | Widget 布局：标题栏 + ImageView + 模板切换栏 | ✅ |
| `a2ui_widget_placeholder.xml` | 加载态布局：ProgressBar + 文本 | ✅ |
| `widget_templates/{weather,agenda,todo}.json` | 3 种静态 A2UI 协议模板 | ✅ |

**验收结果**：
- [x] **Bitmap 渲染管线全链路通过**：AGenUI 引擎 → SurfaceManager → createSurface →
      updateComponents → measure(300px) → layout → draw(Canvas) → Bitmap(300×200/270,
      240-324KB) → RemoteViews.setImageViewBitmap → AppWidgetManager.updateAppWidget ✅
- [x] **三种模板全部渲染成功**：weather(300×270) / agenda / todo(300×200) ✅
- [x] **SurfaceManager 用 Activity Context 初始化成功**（R1 风险已解决）✅
- [x] **Bitmap 大小安全**：240-324KB，远低于 1MB Binder 限制（R2 暂不触发）✅
- [x] **线程同步**：CountDownLatch(5s) + Handler(MainLooper) 正常工作（R3 已解决）✅
- [x] **SurfaceManager 生命周期**：onCreate → destroy 正常清理 ✅
- [ ] 桌面 Widget 实例绑定（需用户手动添加，adb bind 命令此设备不支持）— 部分验证
- [ ] 高分辨率设备 Bitmap >800KB 降级（需更大组件模板触发）— 未触发

**关键经验教训**：
1. **`windowNoDisplay=true` 禁用于异步 Activity**：该属性强制 Activity 在 `onResume()`
   前调 `finish()`，与等待异步 Surface 回调冲突 → `IllegalStateException` 崩溃。
   修复：改用 `windowIsTranslucent=true` + `windowBackground=transparent`（不带 windowNoDisplay）。
2. **SurfaceManager 必须用 Activity Context**：`new SurfaceManager(this)` 中的 `this` 必须是
   Activity，不能用 Service/Application Context。JobIntentService 只负责拉起
   WidgetRenderActivity，不做渲染。
3. **Gradle 8.11.1 最低要求**：AGenUI SDK 需要 Gradle 8.11.1+，本地 Gradle 8.9 不行，必须用 `./gradlew` wrapper。
4. **measure 高度**：`MeasureSpec.UNSPECIFIED` + width=300 EXACTLY → AGenUI 自适应高度 200-270px，正常。

---

### Phase 2：LLM 集成 + 输入交互

**目标**：打通"用户输入 → LLM 生成 → Widget 动态渲染"端到端链路。

**详细计划**：见 [`docs/research/PHASE2-PLAN.md`](../../../docs/research/PHASE2-PLAN.md)（基于 2025-2026 业界优秀开源项目三轮调研制定）

**子阶段拆分**（总工期估 15 工作日）：
| 子阶段 | 目标 | 工期 |
|--------|------|------|
| P2.1 | LLM Client + 文字输入 + 流式渐进渲染 | 5 天 |
| P2.2 | 语音输入链路（Vosk + Silero VAD） | 3 天 |
| P2.3 | 文件导入链路（SAF + PdfBox-Android） | 2 天 |
| P2.4 | 统一输入面板 UI（三 Tab + 大屏适配） | 2 天 |
| P2.5 | 稳定性 + 降级链路 + 历史记录 | 3 天 |

**模块**：
| 模块 | 职责 | 子阶段 |
|------|------|--------|
| `WidgetLLMClient` | OkHttp SSE 流式调用 LLM，多模型 Failover | P2.1 |
| `WidgetPromptBuilder` | 构建 System Prompt（含 Catalog + few-shot） | P2.1 |
| `WidgetProtocolValidator` | 三级校验：JSON 语法 → 协议结构 → 组件白名单 | P2.1 |
| `WidgetStreamRenderer` | SSE → receiveTextChunk → 周期 measure+draw → Bitmap | P2.1 |
| `WidgetInputActivity` | 透明 Activity，三 Tab 统一入口 | P2.4 |
| `WidgetVoiceManager` | Vosk + Silero VAD 语音识别 | P2.2 |
| `WidgetFileImporter` | SAF 选择 + PdfBox/POI 解析 | P2.3 |
| `WidgetHistoryRepository` | Room 数据库，生成历史 + 预览图 | P2.5 |
| `WidgetDegradationChain` | 三级降级：JSON 修复 → 关键词模板 → 默认模板 | P2.5 |

**验收标准**：
- [ ] 文字输入 → LLM 生成合法 A2UI JSON 概率 >80%
- [ ] 流式渐进渲染：首组件延迟 <1s，后续每 500ms-1s 刷新
- [ ] 语音输入：点击 → VAD 自动检测 → Vosk 识别 → 提交（首字 <500ms）
- [ ] 文件导入：SAF 选择 → PdfBox 解析 → 截断 4000 字 → 送 LLM
- [ ] LLM 失败自动降级到关键词匹配模板
- [ ] Widget 按钮右上角 3 圆形图标（刷新/模板/AI输入）
- [ ] 竖屏 BottomSheet + 横屏 Drawer，三 Tab 切换
- [ ] 生成历史记录到 Room 数据库（最近 50 条可查）
- [ ] 断网时显示上次成功渲染结果（缓存）

**新增风险**（Phase 2）：
| 风险 | 等级 | 缓解方案 |
|------|------|----------|
| R4: LLM 输出 JSON 不合法 | 中 | 三级降级 + JSON 修复 + few-shot 优化 |
| R5: 流式中断（网络断开） | 中 | 基于部分 JSON 渲染 + Toast 提示 |
| R6: Vosk 模型 42MB 体积 | 中 | 动态下载策略，不打包到 APK |
| R7: Android 14+ 后台录音限制 | 高 | 必须用透明 Activity 前台录音 |
| R8: PdfBox 大文件 OOM | 中 | 流式加载 + 逐页处理 + 50 页上限 |
| R9: AGenUI C++ core 线程安全 | 高 | SurfaceManager 调用主线程 + 单实例 |
| R10: Binder 1MB 限制（大 Widget） | 中 | >800KB 自动 JPEG 压缩 |
| R11: WorkManager 任务被杀 | 中 | setForeground + 用户可见通知 |

---

### Phase 3：体验优化 + 稳定性

**目标**：产品级体验，可进入众测。

**优化项**：
- Bitmap 内存管理（复用 + recycle）
- 多 Widget 实例并发渲染（JobIntentService 队列优化）
- 生成历史记录（本地数据库 Room）
- 预览图 / 尺寸自适应
- LLM Prompt 调优（few-shot + 动态 few-shot from 评估高分样本）
- 网络降级（渐进增强：ChartCard 按网络条件降级）

---

### Phase 4：Glance 迁移（可选，技术预研）

**目标**：评估 Jetpack Glance 替代 RemoteViews 的可行性和收益。

**评估维度**：
- Glance actionRunCallback vs PendingIntent 延迟对比
- Glance StateFlow 响应式更新 vs 手动 updateAppWidget
- Glance @Preview 可预览性
- APK 体积增量（Glance ~2MB）
- 结论：如果 Glance + Bitmap 混合方案有明确体验优势且体积可控，则迁移；否则保持 RemoteViews

---

## 三、技术架构图

```
┌─────────────────────────────────────────────────────────┐
│                    用户设备桌面                           │
│  ┌─────────────────────────────────────────────────────┐ │
│  │         A2UI Widget (RemoteViews)                   │ │
│  │  ┌──────────┬──────────────────────┬────────────┐  │ │
│  │  │ 标题栏    │   ImageView(Bitmap)   │  按钮栏    │  │ │
│  │  │ TextView  │   A2UI 渲染结果       │ 刷新/模板  │  │ │
│  │  └──────────┴──────────────────────┴────────────┘  │ │
│  └──────────────────────┬──────────────────────────────┘ │
│                         │ PendingIntent                   │
│  ┌──────────────────────▼──────────────────────────────┐ │
│  │           App 进程                                    │ │
│  │  ┌───────────────────────────────────────────────┐   │ │
│  │  │     WidgetInputActivity (透明 Activity)        │   │ │
│  │  │  ┌─────────┬──────────┬─────────────────────┐  │   │ │
│  │  │  │ 键盘输入 │ 语音输入  │    文件导入         │  │   │ │
│  │  │  │ TextField│ Vosk+VAD │  SAF+DocumentReader │  │   │ │
│  │  │  └─────────┴──────────┴─────────────────────┘  │   │ │
│  │  └───────────────────────┬────────────────────────┘   │ │
│  │                          │ prompt                      │ │
│  │  ┌───────────────────────▼────────────────────────┐   │ │
│  │  │     AGenUIWidgetLLMService                      │   │ │
│  │  │  SSE 流式 → receiveTextChunk → 增量构建 Surface  │   │ │
│  │  │  → 周期性 measure+draw → Bitmap                 │   │ │
│  │  └───────────────────────┬────────────────────────┘   │ │
│  │                          │ Bitmap                      │ │
│  │  ┌───────────────────────▼────────────────────────┐   │ │
│  │  │     AppWidgetManager.updateAppWidget()          │   │ │
│  │  └───────────────────────────────────────────────┘   │ │
│  └──────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

---

## 四、Phase 0 详细设计

### 4.1 页面结构

```
widget-poc.html (自包含单文件)
├── 顶部工具栏
│   ├── AGenUI Widget POC 标题
│   ├── LLM 状态指示灯
│   └── 设备模式切换（手机/平板/桌面）
├── 左侧：Widget 模拟器（4×3 格桌面区域）
│   ├── Widget 标题栏（标题 + 3 圆形按钮：刷新/模板/AI输入）
│   ├── A2UI 渲染区（@a2ui/react 真实渲染）
│   └── 底部模板快捷栏
├── 右侧：输入面板（可折叠）
│   ├── Tab 切换：键盘 / 语音 / 文件
│   ├── 键盘：输入框 + 快捷 chips + 发送
│   ├── 语音：波形动画 + Vosk 模拟 + 识别结果
│   └── 文件：文件选择 + 解析预览 + 补充指令
└── 底部：调试面板
    ├── SSE 事件流日志
    ├── A2UI 协议 JSON 查看
    └── 渲染耗时 / Token 计数
```

### 4.2 数据流

```
用户输入 (text/voice/file)
    │
    ▼
POST /generate (SSE)
    │
    ▼
engine.js: classifyIntent → buildPrompt → callLLMStreamed
    │
    ▼  onComp 回调（每解析到一个完整组件）
    │
    ▼
SSE comp 帧 → 前端 onComp handler
    │
    ▼
MessageProcessor.processMessages([createSurface, updateComponents, updateDataModel])
    │
    ▼
A2uiSurface 组件渲染到 DOM
    │
    ▼
Widget 模拟器区域渐进式更新
```

### 4.3 内置模板

三种静态 A2UI 协议模板（用于 Phase 0 验证渲染链路，不依赖 LLM）：

1. **天气卡片**：Card + Column + Text（城市/温度/天气描述）+ Row（湿度/风力）
2. **会议议程**：Card + Column + List（议程项列表）+ Button（加入会议）
3. **待办清单**：Card + Column + List（待办项 + CheckBox）+ Text（完成率）

---

## 五、依赖清单

### Phase 0（Web 端）

| 依赖 | 来源 | 用途 |
|------|------|------|
| `@a2ui/react@0.10.2` | esm.sh CDN | A2UI React 渲染器 |
| `@a2ui/web_core@0.10.6` | esm.sh CDN | MessageProcessor / SurfaceModel |
| `React 19.2` | esm.sh CDN | 渲染框架 |
| `htm` | esm.sh CDN | 无 JSX 的类 JSX 语法 |
| `engine.js` | 本地 poc_v1 | LLM 引擎 |
| `server.js` | 本地 poc_v1 | SSE 服务 |

### Phase 1-2（Android 端）

**Phase 1 已用**：
| 依赖 | 版本 | 用途 |
|------|------|------|
| AGenUI SDK | 源码依赖 | A2UI 渲染引擎 |
| AndroidX AppCompat / Material | — | 基础 UI |

**Phase 2 新增**（详见 `docs/research/PHASE2-PLAN.md`）：
| 依赖 | 版本 | 用途 | 体积 |
|------|------|------|------|
| `com.squareup.okhttp3:okhttp-sse` | 4.12.0 | SSE 流式调用 LLM | ~200KB |
| `com.alphacephei:vosk-android` | 0.3.70 | 离线中文 STT | ~5MB lib + 42MB 模型（动态下载） |
| `com.github.gkonovalov.android-vad:silero` | 2.0.10 | 语音活动检测（精确） | ~2MB |
| `com.github.gkonovalov.android-vad:webrtc` | 2.0.10 | VAD 轻量过滤（双级联用） | ~158KB |
| `com.tom-roush:pdfbox-android` | 2.0.27.0 | PDF 文本提取 | ~5MB |
| `com.lxj:xpopup` | 2.10.0 | 弹窗/抽屉框架 | ~500KB |
| `com.github.Dimezis:BlurView` | 3.2.0 | 玻璃拟态背景 | ~200KB |
| `com.valentinilk.shimmer:compose-shimmer` | 1.3.3 | 骨架屏占位 | ~100KB |
| `androidx.work:work-runtime-ktx` | 2.10.0 | WorkManager 持久化任务 | ~1MB |
| `androidx.room:room-runtime` | 2.6.1 | 历史记录数据库 | ~2MB |

**APK 净增估算**：~15MB（不含 Vosk 模型 42MB，模型动态下载）

---

## 六、验收里程碑

| 里程碑 | 内容 | 验收方式 |
|--------|------|----------|
| M0 | Phase 0 Web 原型 | 浏览器打开 widget-poc.html，文字输入 → 流式渲染 |
| M1 | Phase 1 Android 骨架 | 真机桌面添加 Widget，显示天气卡片，三模板切换 ✅ 已完成 |
| M2.1 | Phase 2.1 LLM + 文字 + 流式 | 真机文字输入 → Widget 渐进式更新 |
| M2.2 | Phase 2.2 语音输入 | 真机语音输入 → 识别 → 提交 LLM |
| M2.3 | Phase 2.3 文件导入 | 真机选择 PDF → 解析 → 提交 LLM |
| M2.4 | Phase 2.4 统一输入面板 | 竖屏 BottomSheet + 横屏 Drawer，三 Tab 切换 |
| M2.5 | Phase 2.5 稳定性 + 历史 | 断网测试 + LLM 失败降级 + 历史记录查看 |
| M4 | Phase 3 体验优化 | 众测包发到 200.49.56.157，收集反馈 |
