# 小艺风格开源项目复用分析报告

> **项目**: AGenUI Widget (`com.amap.agenuiplayground`)
> **基准**: HarmonyOS 6 小艺 (XiaoYi) 交互设计模式
> **日期**: 2026-08-25
> **状态**: 调研完成，待评审

---

## 一、小艺核心设计模式拆解

| 模式 | 小艺实现 | AGenUI Widget 对应需求 |
|------|---------|----------------------|
| **灵动球** | 圆形半透明悬浮球，可拖拽，悬浮展开功能菜单，光晕状态指示（绿/黄/红） | Widget 右上角 AI 入口按钮 → 呼出右侧抽屉面板 |
| **投喂感应区** | 右下角拖拽区，拖入文件触发小艺面板 | AI 输入面板的文件导入 tab（SAF + DocumentReader） |
| **智慧光感** | 交互时涟漪动画，光影跟随光标 | 面板展开动效 + 输入交互反馈动画 |
| **极简输入法** | 默认隐藏候选栏，柔和色调，纯净设计 | 键盘 tab 输入框 + 快捷建议 chips |
| **多线程对话分栏** | 左侧历史 + 右侧当前对话 | 大屏横屏分屏：左侧 Widget 渲染 + 右侧 SSE 流 |
| **右键直达** | 右键高频小艺功能 | Widget 长按/右键 → 模板切换快捷菜单 |

---

## 二、开源项目对比矩阵（按小艺模式映射）

### 2.1 悬浮球 / 灵动球类

| 项目 | 语言/技术 | Stars | 协议 | 小艺模式覆盖 | 可复用度 | 关键特性 |
|------|----------|-------|------|------------|---------|---------|
| **Floating-Bubble-View** (dofire) | Java + Compose | ~500 | MIT | 灵动球（拖拽/吸附/展开） | ★★★★☆ | ExpandableBubbleService、可拖拽、close-bubble 行为、animate-to-edge 吸边 |
| **bubbles-for-android** (txusballesteros) | Java | ~2.5k | Apache-2.0 | 灵动球（ChatHead 风格） | ★★★☆☆ | 拖拽+删除区、Service 架构、`com.txusballesteros:bubbles:1.2.1` |
| **chat_bubble** (lizy-coding) | Kotlin/Compose | ~300 | MIT | 智慧光感（气泡变形动画） | ★★★★☆ | Morphing 过渡、贝塞尔曲线拖拽、粒子爆炸、可定制样式 |
| **companion-widget-LLM** (TaAnhQuan) | Java | ~200 | MIT | 灵动球 + 多模态 + Rive 动画 | ★★★★★ | 悬浮聊天球、多 LLM 后端、语音输入、截图、Rive 动画、Markdown 渲染 |

### 2.2 语音输入 / 极简输入法类

| 项目 | 语言/技术 | Stars | 协议 | 小艺模式覆盖 | 可复用度 | 关键特性 |
|------|----------|-------|------|------------|---------|---------|
| **LexiSharp-Keyboard / 言犀键盘** (BryceWG) | Kotlin | ~1k | Apache-2.0 | 极简输入 + 灵动球 + 语音 | ★★★★★ | 10+ ASR 引擎可切换、LLM 后处理、Material3 设计、悬浮球反馈(灰/红/蓝)、智能停顿检测 |
| **BiBi-Keyboard / 说点啥** (zeta987) | Kotlin | ~400 | Apache-2.0 | 极简输入 + 灵动球 | ★★★☆☆ | 类似 LexiSharp，功能稍少，语音 + AI 后处理 + 悬浮球 |
| **whisperIMEplus** (woheller69) | Java | ~150 | GPL-3 | 极简输入 + 语音浮窗 | ★★★★☆ | 透明浮窗主题、底部圆角、96dp 麦克风、VAD 自动模式、ONNX + WebRTC VAD |
| **AI Assistant for Android** (sourabanand001) | Kotlin | ~300 | MIT | 灵动球 + 语音 + 端侧 | ★★★★☆ | 离线优先、Silero VAD、屏幕视觉、蓝牙支持、默认助手替换、AES-256-GCM |
| **DeepSeekWidget** (rajit2004) | Kotlin | ~100 | MIT | 语音入口 Widget | ★★★☆☆ | 4.3MB 极简、语音按钮 Widget、Trampoline Activity、Material You |

### 2.3 玻璃拟态 / 智慧光感 / 视觉效果类

| 项目 | 语言/技术 | Stars | 协议 | 小艺模式覆盖 | 可复用度 | 关键特性 |
|------|----------|-------|------|------------|---------|---------|
| **BlurView** (dimezis) | Java | ~4k | Apache-2.0 | 玻璃拟态（背景模糊） | ★★★★★ | 实时模糊、多种算法(RenderScript/NDK/Java)、可配半径/圆角/叠加层 |
| **compose-shimmer** (valentinilk) | Kotlin/Compose | ~2k | Apache-2.0 | 智慧光感（骨架微光） | ★★★★☆ | `Modifier.shimmer()`、零依赖 Multiplatform、LLM 渐进渲染占位 |
| **GetStream stream-chat-android-ai** | Kotlin/Compose | ~1.5k | Apache-2.0 | 智慧光感 + 极简输入 | ★★★★★ | StreamingText 逐字显示、AITypingIndicator 动画、ChatComposer 输入栏、SpeechToTextButton 语音+波形 |
| **Ratatoskr** (10-neon) | Kotlin | ~80 | MIT | 灵动球 + 多策略回复 | ★★☆☆☆ | 悬浮球 AI 聊天、生成 3 种回复策略、OpenAI 兼容后端 |

### 2.4 文件导入 / 投喂感应区类

| 项目 | 语言/技术 | Stars | 协议 | 小艺模式覆盖 | 可复用度 | 关键特性 |
|------|----------|-------|------|------------|---------|---------|
| **Android SAF** (系统 API) | — | — | — | 投喂感应区（文件选择） | ★★★★★ | `ACTION_OPEN_DOCUMENT`、零依赖、persistable Uri |
| **DocumentReader** (Asutosh11) | Java | ~30 | MIT | 投喂感应区（文件解析） | ★★★★☆ | 一行 API 解析 PDF/Word/TXT→String、JitPack `0.12` |
| **ComposeFilePicker** (mahdiasd) | Kotlin/Compose | ~150 | Apache-2.0 | 投喂感应区（UI 封装） | ★★★☆☆ | M3 风格文件选择 UI、`io.github.mahdiasd:ComposeFilePicker:1.0.6` |
| **attachments-compose** (gaikwadChetan93) | Kotlin/Compose | ~50 | MIT | 投喂感应区（附件预览） | ★★★☆☆ | 文件名/大小/类型图标预览组件 |

### 2.5 综合 / 端侧 AI 管线类

| 项目 | 语言/技术 | Stars | 协议 | 小艺模式覆盖 | 可复用度 | 关键特性 |
|------|----------|-------|------|------------|---------|---------|
| **AndroidAutoGLM** (sidhu-master) | Kotlin | ~200 | MIT | 灵动球 + 语音 + 浮窗状态 | ★★★☆☆ | Shizuku 触控模拟、语音输入、浮窗状态显示 |
| **SmolChat-Android** (shubham0204) | Kotlin/Compose | ~500 | Apache-2.0 | 多线程对话分栏 | ★★★★☆ | ChatGPT 风格 UI、气泡+Markdown+代码高亮+流式+停止生成 |

---

## 三、AGenUI Widget 复用推荐方案

### 3.1 核心选型（按优先级排序）

#### P0 — 必选依赖（直接复用）

| 组件 | 选定项目 | 依赖坐标 | 理由 |
|------|---------|---------|------|
| **背景模糊** | BlurView (dimezis) | `com.github.Dimezis:BlurView:version-2.0.0` | 鸿蒙玻璃拟态规范的核心实现，实时模糊 + 可配圆角/叠加层，完全契合小艺半透明面板 |
| **骨架微光** | compose-shimmer (valentinilk) | `com.valentinilk.shimmer:compose-shimmer:1.3.3` | LLM 流式渲染占位动画，零依赖，`Modifier.shimmer()` 一行集成 |
| **流式文本** | GetStream stream-chat-android-ai | 源码引用（4 个 Compose 组件） | StreamingText 逐字显示 + AITypingIndicator 动画，可脱离 Stream SDK 独立使用 |
| **语音 STT** | Vosk (alphacephei) | `com.alphacephei:vosk-android:0.3.45` | 离线 STT，42MB 中文模型，<200ms 首字延迟，`onPartialResult` 流式 |
| **语音 VAD** | Android VAD (gkonovalov) | JitPack `com.github.gkonovalov.android-vad` | WebRTC 模型 158KB API16+，`setContinuousSpeechListener` 自动端点检测 |
| **文档解析** | DocumentReader (Asutosh11) | `com.github.Asutosh11:DocumentReader:0.12` | 一行 API 解析 PDF/Word/TXT，封装 PdfBox+POI |
| **弹窗/抽屉** | XPopup (junixapp) | `com.lxj:xpopup:2.0.0` | 7 种弹窗含 Drawer 类型，生命周期管理，Widget 输入 UI 承载 |

#### P1 — 强烈推荐（参考/部分复用）

| 组件 | 选定项目 | 复用方式 | 理由 |
|------|---------|---------|------|
| **悬浮球交互参考** | companion-widget-LLM (TaAnhQuan) | 参考架构 + Rive 动画思路 | 唯一同时覆盖悬浮球+多 LLM+语音+截图+Rive 动画的综合项目 |
| **输入法 + ASR 切换参考** | LexiSharp-Keyboard (BryceWG) | 参考多引擎切换架构 + 悬浮球状态反馈设计 | 10+ ASR 引擎切换、LLM 后处理管线、Material3 设计、灰/红/蓝状态反馈 |
| **语音浮窗 UI 参考** | whisperIMEplus (woheller69) | 参考透明浮窗主题 + 96dp 麦克风 + VAD 自动模式 | 技术栈与 Vosk+VAD 方案完全兼容 |
| **气泡变形动画参考** | chat_bubble (lizy-coding) | 参考贝塞尔曲线 + 粒子效果 | 小艺智慧光感的涟漪/变形动画可借鉴 |
| **悬浮球拖拽参考** | Floating-Bubble-View (dofire) | 参考吸边 + 展开 + close-bubble | 灵动球的拖拽/吸附/展开行为 |

#### P2 — 可选探索（Phase 2/3）

| 组件 | 选定项目 | 探索方向 |
|------|---------|---------|
| **离线 ASR 精确识别** | WhisperKitAndroid (Argmax) | QNN 硬件加速，Snapshot 设备 |
| **端侧 AI 助手架构** | AI Assistant for Android (sourabanand001) | 离线优先 + Silero VAD + 屏幕视觉 |
| **多策略回复** | Ratatoskr (10-neon) | 生成 3 种回复策略，用户择优 |
| **对话 UI** | SmolChat-Android (shubham0204) | 大屏分栏对话 UI 参考 |

### 3.2 小艺模式 → 开源项目映射总表

```
小艺模式                    → 推荐开源组合
─────────────────────────────────────────────────────────────
灵动球（悬浮/拖拽/展开）     → Floating-Bubble-View (拖拽引擎) + chat_bubble (变形动画) + companion-widget-LLM (Rive 动画参考)
投喂感应区（文件拖入）       → Android SAF (文件选择) + DocumentReader (解析) + attachments-compose (预览)
智慧光感（涟漪/光影）        → BlurView (玻璃模糊) + compose-shimmer (微光占位) + chat_bubble (粒子效果参考)
极简输入法（纯净输入）       → GetStream ChatComposer (输入栏) + LexiSharp (多引擎切换参考)
多线程对话分栏（历史+当前）  → SmolChat-Android (对话 UI) + GetStream StreamingText (流式渲染)
右键直达（快捷菜单）         → XPopup (Attach/Position 弹窗)
语音输入（实时识别）         → Vosk (STT) + Android VAD (端点检测) + whisperIMEplus (浮窗 UI 参考)
```

### 3.3 不推荐直接依赖的项目

| 项目 | 原因 |
|------|------|
| bubbles-for-android | 老旧 Java Service 架构，Floating-Bubble-View 更现代（支持 Compose） |
| BiBi-Keyboard | 功能与 LexiSharp 重叠但较少维护，LexiSharp 更活跃 |
| DeepSeekWidget | 过于简单（仅 Trampoline + 在线 API），不适合离线场景 |
| AndroidAutoGLM | 依赖 Shizuku（需 ADB 激活），不适合生产 Widget |
| Ratatoskr | 回复策略概念有趣但实现粗糙，仅做思路参考 |
| ComposeFilePicker | SAF 已足够，第三方封装增加无谓依赖 |

---

## 四、集成架构设计

### 4.1 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                    Widget 层 (RemoteViews)                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  天气内容区    刷新(32vp)  模板(32vp)  AI输入(32vp)       │   │
│  │  Bitmap 渲染                          ← 右上角3图标      │   │
│  └──────────────────────────────────────────────────────────┘   │
└──────────────────────┬──────────────────────────────────────────┘
                       │ PendingIntent
┌──────────────────────▼──────────────────────────────────────────┐
│              WidgetInputActivity (透明主题)                      │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  XPopup Drawer / DrawerLayout 右侧抽屉                   │    │
│  │  ┌─────────────────────────────────────────────────┐     │    │
│  │  │  BlurView 背景 (小艺半透明面板)                    │     │    │
│  │  │  ┌─────────┬─────────┬─────────┐                │     │    │
│  │  │  │ 键盘tab  │ 语音tab │ 文件tab │  ← GetStream   │     │    │
│  │  │  │          │         │         │    ChatComposer │     │    │
│  │  │  │ 输入框    │ Vosk    │ SAF     │                │     │    │
│  │  │  │ chips    │ VAD     │ DocRdr  │                │     │    │
│  │  │  │ 发送     │ 麦克风   │ 发送    │                │     │    │
│  │  │  └─────────┴─────────┴─────────┘                │     │    │
│  │  │  compose-shimmer (加载占位)                        │     │    │
│  │  └──────────────────────────────────────────────────┘     │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────┬──────────────────────────────────────────┘
                       │ Intent + Text
┌──────────────────────▼──────────────────────────────────────────┐
│           AGenUIWidgetLLMService (后台服务)                       │
│  ┌──────────┐  ┌──────────────┐  ┌───────────────────────────┐  │
│  │ 文本输入  │  │ Vosk 识别结果 │  │ DocumentReader 解析结果   │  │
│  └────┬─────┘  └──────┬───────┘  └──────────┬───────────────┘  │
│       └───────────────┬┴──────────────────────┘                 │
│                       ▼                                         │
│              LLM SSE 流式请求                                     │
│                       │                                         │
│              AGenUI receiveTextChunk                              │
│                       │                                         │
│              Surface → Bitmap → Widget 刷新                      │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 小艺风格视觉规范（基于鸿蒙 design-tokens.json）

| 属性 | 值 | 小艺对应 |
|------|-----|---------|
| 面板背景 | `rgba(255,255,255,0.96)` + BlurView(半径 20) | 半透明面板 + 玻璃拟态 |
| 品牌色 | `#007DFF` | 小艺蓝 |
| 品牌按压色 | `#0066D6` | 按压反馈 |
| 品牌表面色 | `rgba(0,125,255,0.08)` | 选中态背景 |
| 文字主色 | `rgba(0,0,0,0.90)` | 正文 |
| 文字次色 | `rgba(0,0,0,0.60)` | 辅助说明 |
| 文字三级 | `rgba(0,0,0,0.40)` | 占位符 |
| 分割线 | `rgba(0,0,0,0.08)` | tab 分隔 |
| 遮罩 | `rgba(0,0,0,0.45)` | 抽屉 scrim |
| 圆角 | 16vp（面板）/ 12vp（卡片）/ 8vp（按钮） | 鸿蒙规范 |
| 间距 | 4vp 基数（4/8/12/16/24/32/48） | 鸿蒙规范 |
| 动效 | 250ms `cubic-bezier(0.4,0,0.2,1)` | 面板展开/收起 |
| 涟漪效果 | `radial-gradient(circle, rgba(0,125,255,0.15)→transparent)` | 智慧光感 |
| 状态反馈 | 灰(待机) → 蓝(激活) → 绿(成功) → 红(错误) | 灵动球光晕 |

### 4.3 新增依赖体积估算

| 依赖 | 大小 | 用途 |
|------|------|------|
| Vosk 中文模型 | ~42MB | 离线 STT |
| BlurView | ~50KB | 玻璃模糊 |
| compose-shimmer | ~30KB | 骨架微光 |
| XPopup | ~500KB | 弹窗/抽屉 |
| DocumentReader + 依赖 | ~8MB | 文档解析 |
| Android VAD (WebRTC) | ~158KB | 语音端点检测 |
| GetStream AI 组件 | ~200KB | 流式文本/输入栏（源码引用） |
| **总计** | **~51MB** | （Vosk 模型占 82%） |

---

## 五、Phase 1 落地清单

### 5.1 即可直接集成

- [ ] `build.gradle` 添加 P0 依赖
- [ ] `WidgetInputActivity` 透明主题 + XPopup Drawer
- [ ] BlurView 包裹抽屉内容区
- [ ] 键盘 tab: GetStream ChatComposer + chips
- [ ] 语音 tab: Vosk + Android VAD + 96dp 麦克风按钮
- [ ] 文件 tab: SAF → DocumentReader → 预览卡片
- [ ] compose-shimmer: LLM 等待期间骨架屏
- [ ] GetStream StreamingText: LLM 流式文本显示

### 5.2 参考但不直接依赖

- [ ] companion-widget-LLM: 研究其 Rive 动画 + 多 LLM 后端架构
- [ ] LexiSharp-Keyboard: 研究其 ASR 多引擎切换 + 状态反馈设计
- [ ] chat_bubble: 研究其贝塞尔变形动画（小艺智慧光感实现参考）
- [ ] Floating-Bubble-View: 研究其吸边/展开行为（Phase 2 灵动球探索）

### 5.3 风险与缓解

| 风险 | 概率 | 影响 | 缓解 |
|------|------|------|------|
| Vosk 模型 42MB 增加 APK 体积 | 高 | 中 | 使用 Dynamic Feature Module 按需下载 |
| BlurView 在低配设备性能差 | 中 | 中 | 降级为纯色半透明背景 |
| XPopup Drawer 与 DrawerLayout 冲突 | 低 | 高 | 统一用 XPopup Drawer，不混用 |
| GetStream 组件脱离 SDK 使用 | 中 | 低 | 源码引用（4 个独立 Composable），不引入 Stream SDK |
| DocumentReader 大文件 OOM | 中 | 高 | 限制文件大小（<10MB），大文件截断解析 |

---

## 六、结论

本次调研覆盖 15+ 开源项目，按小艺 6 大设计模式分类映射后，确定 **7 个 P0 必选依赖 + 5 个 P1 参考项目 + 4 个 P2 探索方向**。核心结论：

1. **没有单一项目能完整复刻小艺**，但组合 7 个 P0 依赖即可覆盖小艺全部核心模式
2. **companion-widget-LLM** 是最接近的综合参考项目（悬浮球+多 LLM+语音+Rive 动画），但 Java 实现较重
3. **LexiSharp-Keyboard** 的多引擎切换 + 状态反馈设计最值得参考（灰/红/蓝三态）
4. **GetStream AI 组件** 是 Compose 生态最成熟的流式 UI 组件库，4 个组件均可独立使用
5. **BlurView + compose-shimmer** 组合实现小艺玻璃拟态 + 智慧光感视觉效果
6. 总新增依赖体积 ~51MB（Vosk 模型占 82%），可用 Dynamic Feature Module 优化
