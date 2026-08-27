# 小艺鸿蒙 PC 界面交互分析 × A2UI Playground 复用方案

> **项目**: AGenUI Playground (`com.amap.agenuiplayground`)
> **基准**: HarmonyOS 6/7 鸿蒙电脑小艺 (XiaoYi) AI 助手
> **日期**: 2026-08-27
> **状态**: 调研完成，待评审
> **关联**: `XIAOYI-STYLE-OPENSOURCE-ANALYSIS.md` (开源复用) · `PLAYGROUND-HARMONYOS-CONTROL-COMPARISON.md` (控件差异)

---

## 一、小艺在鸿蒙 PC 上的 5 大交互入口

### 1.1 交互入口全景

鸿蒙电脑小艺设计了 5 种递进的交互入口，从"最低认知负担"到"最高上下文感知"：

| 入口 | 触发方式 | 上下文感知 | A2UI Playground 对应 |
|------|---------|-----------|---------------------|
| **① 唤一声** | 语音唤醒 "小艺小艺" | 无上下文 | 语音 tab → Vosk STT → `streamLLMToPlayground()` |
| **② 按一下** | 小艺智慧键（键盘右 Ctrl 位） | 无上下文 | `toggleInputMode()` → 右侧抽屉滑出 |
| **③ 右键一下** | 右键 → "问问小艺" | 选中内容上下文 | Widget 长按/右键快捷菜单（Phase 2 待实现） |
| **④ 拖一下** | 文件拖拽至投喂感应区 | 文件内容上下文 | 文件 tab → SAF → DocumentReader → `onSend()` |
| **⑤ 圈一下** | 指关节圈选屏幕区域 | 视觉内容上下文 | 暂无对应（Phase 3 截图 OCR 探索） |

### 1.2 交互入口设计原则

小艺的 5 种入口遵循 3 条设计原则：

1. **渐进式上下文** — 从无上下文（语音/按键）到强上下文（拖拽/圈选），用户按需选择入口深度
2. **物理键+手势+语音三模态** — 不依赖单一输入方式，适配不同环境（安静办公室用键盘，嘈杂环境用拖拽）
3. **入口即功能** — 右键菜单自动推荐当前上下文最需要的功能（翻译/摘要/提取），而非通用问答

---

## 二、小艺 AI 输入面板交互拆解

### 2.1 面板形态：右侧侧边抽屉

鸿蒙 PC 小艺的 AI 对话面板采用**右侧滑出侧边面板**形态，而非全屏覆盖或底部弹窗：

```
┌──────────────────────────────────────────────┐
│                                    ┌────────┐│
│                                    │ 小艺面板││
│         桌面 / 应用内容区           │        ││
│         (不被遮挡,                  │ 输入区  ││
│          可见可交互)                │ 历史区  ││
│                                    │ 快捷键  ││
│                                    └────────┘│
└──────────────────────────────────────────────┘
```

**设计要点**：
- 面板宽度约屏幕 25-30%（约 400-480vp）
- 左侧内容区不被遮挡，用户可同时参考内容
- 面板可拖拽调整宽度
- 关闭后面板消失，不残留半透明遮罩

### 2.2 A2UI Playground 现有实现对比

| 特性 | 小艺鸿蒙 PC | A2UI Playground 现状 | 差距 |
|------|------------|---------------------|------|
| 面板位置 | 右侧滑出 | 右侧 DrawerLayout (`gravity=end`) | ✅ 一致 |
| 面板宽度 | ~400-480vp | `wrap_content` (内容撑开) | ⚠ 需固定宽度 |
| 左侧可见 | 桌面可见可交互 | renderContent 可见但不可交互 | ⚠ 可接受(Playground 性质) |
| 背景模糊 | 玻璃拟态 (BlurView) | 纯色背景 (`@color/a2_bg`) | ❌ 缺失模糊 |
| Tab 切换 | 键盘/语音/文件 | 键盘/语音/文件 (`ViewFlipper`) | ✅ 一致 |
| 快捷 chips | 智能推荐 | 天气/待办/日程 (硬编码) | ⚠ 需上下文推荐 |
| 发送反馈 | 流式逐字 + 打字动画 | `streamLLMToPlayground` SSE 流式 | ✅ 已有流式 |
| 状态指示 | 灵动球光晕(灰/蓝/绿/红) | `tvStatus` 文字状态 | ❌ 缺少视觉反馈 |
| 多轮对话 | 历史对话分栏 | `WidgetConversationMemory` 多轮 | ✅ 已有记忆 |
| 右键直达 | 右键菜单 → 高频功能 | 无 | ❌ 待 Phase 2 |

### 2.3 Tab 内容区交互细节

#### 键盘 Tab

| 元素 | 小艺行为 | A2UI Playground 现状 | 复用建议 |
|------|---------|---------------------|---------|
| 输入框 | 多行自适应，回车发送，Shift+回车换行 | `EditText` 多行 + IME_ACTION_SEND | ✅ 已实现 |
| 快捷 chips | 根据当前选中文档/应用上下文推荐 | 天气/待办/日程 3 个硬编码 chip | P1: 接入 NLU 实体推荐 |
| 清空 | 一键清空输入 | `tvAiClear` 清空按钮 | ✅ 已实现 |
| 发送 | 发送后面板不关闭，显示流式回复 | 发送后 `streamLLMToPlayground` | ⚠ 面板应保持开启 |

#### 语音 Tab

| 元素 | 小艺行为 | A2UI Playground 现状 | 复用建议 |
|------|---------|---------------------|---------|
| 麦克风按钮 | 圆形大按钮，按下收音，松开结束 | `btnAiMic` 96dp 圆形按钮 | ✅ 尺寸一致 |
| 识别状态 | 实时显示转写文字 + 波形动画 | `tvVoiceResult` 文字 + `tvVoiceStatus` | ⚠ 缺少波形动画 |
| VAD 端点检测 | 自动检测说话结束 | `WidgetVoskManager` + VAD | ✅ 已实现 |
| 中间结果 | 流式显示 partial result | Vosk `onPartialResult` | ✅ 已实现 |
| 错误处理 | 语音权限/麦克风不可用提示 | `onRequestPermissionsResult` | ✅ 已实现 |

#### 文件 Tab

| 元素 | 小艺行为 | A2UI Playground 现状 | 复用建议 |
|------|---------|---------------------|---------|
| 投喂感应区 | 右下角拖拽区，文件拖入触发面板 | `btnAiSelectFile` 点击选择 | ⚠ 缺少拖拽入口 |
| 文件选择 | 系统文件选择器 | `ACTION_OPEN_DOCUMENT` SAF | ✅ 已实现 |
| 文档解析 | PDF/Word/TXT → 文本 | `PdfTextExtractor` + 纯文本读取 | ✅ 已实现 |
| 预览卡片 | 文件名/大小/类型图标 | `tvAiFilePreview` 纯文本预览 | ⚠ 缺少元信息 |
| 智能操作推荐 | 推荐摘要/翻译/格式转换 | 直接发送给 LLM | P2: 操作类型选择 |

### 2.4 底部操作区

| 元素 | 小艺行为 | A2UI Playground 现状 | 复用建议 |
|------|---------|---------------------|---------|
| 取消 | 关闭面板 | `btnAiDrawerCancel` | ✅ 已实现 |
| 发送 | 发送输入，开始流式 | `btnAiDrawerSend` | ✅ 已实现 |
| 发送状态 | 按钮变灰 + loading 指示 | `setEnabled(false)` + `setAlpha(0.4f)` | ✅ 已实现 |
| 关闭 | 面板滑出收起 | `btnAiDrawerClose` | ✅ 已实现 |
| 完成回调 | 成功/失败提示 | `onSendComplete(success, message)` | ✅ 已实现 |

---

## 三、鸿蒙 PC 小艺视觉规范（HarmonyOS 6/7）

### 3.1 灵动小艺球

鸿蒙 PC 小艺在非交互态以"灵动球"形态悬浮于屏幕右下角：

| 状态 | 视觉 | 触发条件 | A2UI Playground 对应 |
|------|------|---------|---------------------|
| **待机** | 灰色半透明球 | 无交互 | Widget `btnAiInput` 图标 |
| **激活** | 蓝色 + 涟漪光晕 | 鼠标悬浮/点击 | `switchTab()` selected 态 |
| **处理中** | 蓝色 + 旋转动画 | AI 正在处理 | `tvStatus` = "发送中..." |
| **成功** | 绿色 + 粒子消散 | 回复完成 | `onSendComplete(true)` |
| **错误** | 红色 + 抖动 | 处理失败 | `onSendComplete(false)` |

### 3.2 玻璃拟态面板

| 属性 | 鸿蒙规范值 | A2UI Playground 现值 | 差距 |
|------|-----------|---------------------|------|
| 背景模糊 | 高斯模糊半径 20-40 | 无 (`a2_bg` 纯色) | P0: 集成 BlurView |
| 面板透明度 | 0.92-0.96 | 1.0 (不透明) | 需设 alpha |
| 圆角 | 16vp（面板外角） | 无（DrawerLayout 无圆角） | 需 Shape drawable |
| 阴影 | elevation 8dp + 环境光 | 无 | 需 elevation |
| 分割线 | rgba(0,0,0,0.06) 1dp | `a2_divider` 1dp | ✅ 接近 |

### 3.3 沉浸光感 (HarmonyOS 7 新增)

HarmonyOS 7 新增"沉浸光感"特效，小艺面板交互时：

- **鼠标悬浮** Dock 栏图标 → 空间感光效（图标光影跟随鼠标流动）
- **拖入面板** → 光感吸附动效（面板边缘光晕响应拖入位置）
- **滑动条/音量条** → 粒子动效（拖动时粒子跟随）
- **面板展开/收起** → 引力动效（边缘弹性过渡）

> **A2UI Playground 复用建议**: Phase 2 可参考 `compose-shimmer` 实现面板边缘的光感跟随效果，但 Phase 1 优先保证功能完整性。

### 3.4 品牌色与状态色

| 语义 | 色值 | 用途 | A2UI Playground 现值 |
|------|------|------|---------------------|
| 小艺蓝 | `#007DFF` | 品牌色/激活态 | `a2_bg_elevated` (#0D1117 深色) |
| 按压蓝 | `#0066D6` | 按压反馈 | — |
| 品牌表面 | `rgba(0,125,255,0.08)` | 选中态背景 | — |
| 待机灰 | `rgba(0,0,0,0.40)` | 占位/禁用 | `a2_text_secondary` |
| 成功绿 | `#007DFF` → 变体 | 成功反馈 | — |
| 错误红 | `#FA3B3B` | 错误反馈 | — |

> **注意**: A2UI Playground 当前使用深色会议主题 (`#0D1117` / `#161B22`)，而非小艺蓝。这是会议大屏场景的合理选择 — 远距离可读性优先于品牌一致性。

---

## 四、会议大屏场景专项分析

### 4.1 场景定义

| 维度 | 值 |
|------|-----|
| 设备 | 会议大屏 (3840×2160 / 2560×1440) |
| 观看距离 | 3-5 米 |
| 操作方式 | 触控 + 遥控器 + 键鼠 |
| 光照条件 | 会议室照明/投影暗环境 |
| 并发用户 | 多人观看，单人操作 |

### 4.2 大屏交互设计约束

| 约束 | 小艺鸿蒙 PC 方案 | A2UI Playground 方案 | 评估 |
|------|-----------------|---------------------|------|
| **远距离可读** | 字号 20-32fp | 标题 32sp / 正文 28sp / 辅助 24sp | ✅ 已对齐 |
| **触控目标** | ≥48vp | 按钮 ≥96dp / 图标 80dp / Chips 64dp | ✅ 已满足 |
| **对比度** | 高对比深色/浅色 | 深色主题 (#0D1117) + 白色文字 | ✅ 适合暗环境 |
| **面板宽度** | ~400vp | `wrap_content` | ⚠ 大屏上可能过窄 |
| **输入方式** | 键鼠+触控+语音 | 键盘+语音+文件 | ✅ 三模态 |
| **反馈可见性** | 灵动球光晕(3-5米可见) | `tvStatus` 小字状态 | ❌ 反馈不够醒目 |

### 4.3 会议大屏 AI Input 交互流程

```
用户操作                          系统响应
─────────                        ─────────
① 点击 Widget AI 按钮  ──────→   右侧抽屉滑出 (250ms)
                                 面板从右边缘弹性展开
                                 左侧 Widget 内容可见

② 选择输入方式        ──────→   Tab 切换 (ViewFlipper)
   键盘 Tab                      多行输入框 + chips
   语音 Tab                      96dp 麦克风 + VAD
   文件 Tab                      SAF + DocumentReader

③ 输入并发送          ──────→   面板保持开启
                                 发送按钮变灰
                                 tvStatus = "生成中..."

④ LLM 流式回复        ──────→   Widget Surface 渐进渲染
   (streamLLMToPlayground)      左侧内容区实时更新
                                 compose-shimmer 占位动画

⑤ 生成完成            ──────→   onSendComplete(true)
                                 tvStatus = "生成完成"
                                 发送按钮恢复

⑥ 关闭面板            ──────→   抽屉滑出收起
   点击关闭/取消                  底部输入栏恢复
   生成完成自动关闭?              → 否: 保持开启，用户主动关闭
```

### 4.4 大屏特有的交互增强

1. **面板宽度自适应**: 大屏上面板应占屏幕 20-25%（768-960px @3840），而非内容撑开
2. **状态条醒目化**: `tvStatus` 字号应 ≥28sp，颜色用品牌蓝/红/绿区分状态
3. **生成进度可视化**: 左侧 Widget 渲染区应有 compose-shimmer 骨架屏，而非空白等待
4. **面板保持策略**: 生成完成后面板保持开启（小艺 PC 方案），用户可继续追问或关闭

---

## 五、小艺 PC 交互 → A2UI Playground 复用矩阵

### 5.1 已实现（直接复用）

| 小艺 PC 模式 | A2UI Playground 实现 | 文件 | 评估 |
|-------------|---------------------|------|------|
| 右侧侧边面板 | `DrawerLayout` `gravity=end` | `activity_a2ui_playground.xml` | ✅ |
| 键盘/语音/文件三 Tab | `ViewFlipper` + `switchTab()` | `drawer_ai_input.xml` | ✅ |
| 多行输入 + 快捷 chips | `EditText` + 3 chips | `drawer_ai_input.xml` | ✅ |
| 语音 STT (Vosk) | `WidgetVoskManager` | `AiInputDrawerController.java` | ✅ |
| VAD 端点检测 | Android VAD (WebRTC) | `WidgetVoiceHelper.java` | ✅ |
| 文件 SAF 选择 | `ACTION_OPEN_DOCUMENT` | `AiInputDrawerController.java` | ✅ |
| PDF/TXT 解析 | `PdfTextExtractor` + 纯文本 | `AiInputDrawerController.java` | ✅ |
| LLM 流式渲染 | `streamLLMToPlayground()` SSE | `A2UIPlaygroundActivity.java` | ✅ |
| 多轮对话记忆 | `WidgetConversationMemory` | `A2UIPlaygroundActivity.java` | ✅ |
| NLU 实体提取 | `WidgetNLUParser` | `A2UIPlaygroundActivity.java` | ✅ |
| 渐进渲染 | `WidgetPartialParser` | `A2UIPlaygroundActivity.java` | ✅ |
| 发送状态管理 | `onSendComplete()` | `AiInputDrawerController.java` | ✅ |
| 底部输入栏切换 | `toggleInputMode()` | `A2UIPlaygroundActivity.java` | ✅ |

### 5.2 需增强（部分实现，需优化）

| 小艺 PC 模式 | 当前状态 | 差距 | 优先级 |
|-------------|---------|------|--------|
| 面板宽度固定 | `wrap_content` | 设为 `560dp` 或屏幕 25% | P1 |
| 背景模糊 | 纯色 | 集成 BlurView (P0 依赖) | P1 |
| 状态视觉反馈 | 文字 `tvStatus` | 增加颜色状态条 (灰→蓝→绿→红) | P1 |
| 生成进度占位 | 无 | 左侧 renderContent 加 compose-shimmer | P1 |
| 语音波形动画 | 无 | 麦克风按钮旁加波形/脉冲动画 | P2 |
| 文件拖拽入口 | 仅点击选择 | 支持 `dragAndDrop` API | P2 |
| 上下文 chips | 硬编码 3 个 | 接入 NLU 实体动态生成 | P2 |

### 5.3 待实现（Phase 2/3 新增）

| 小艺 PC 模式 | A2UI Playground 计划 | 阶段 |
|-------------|---------------------|------|
| 右键直达 | Widget 长按 → 模板切换快捷菜单 | Phase 2 |
| 灵动球悬浮 | Widget btnAiInput → 悬浮球 (Floating-Bubble-View) | Phase 2 (探索) |
| 圈选 OCR | 截图 → OCR → 发送 | Phase 3 (探索) |
| 多线程对话分栏 | 左历史 + 右当前对话 | Phase 2 (大屏分屏) |
| 复制感应 | 剪贴板监听 → 推荐 chips | Phase 2 |
| 小艺慧记 | 会议实时转写 + 摘要 | Phase 3 (会议场景) |

---

## 六、Widget 点击 → 会议大屏显示 AI Input 完整流程

### 6.1 当前实现流程（已跑通）

```
Widget RemoteViews
  └── btnAiInput (36dp ImageButton)
       └── PendingIntent → A2UIWidgetProvider.ACTION_AI_INPUT
            └── WidgetInputActivity (透明主题)
                 └── XPopup Drawer / DrawerLayout
                      └── AiInputDrawerController.bind()
                           ├── Tab: 键盘 → EditText + chips
                           ├── Tab: 语音 → Vosk + VAD
                           └── Tab: 文件 → SAF + DocumentReader
                                └── onSend(text)
                                     └── WidgetLLMService
                                          └── SSE → receiveTextChunk
                                               └── Surface → Bitmap → Widget 刷新
```

### 6.2 Playground 内嵌流程（已跑通）

```
A2UIPlaygroundActivity
  └── Toolbar 菜单 → toggleInputMode()
       └── showBottomInput(false) + openAiDrawer()
            └── DrawerLayout.openDrawer(END)
                 └── AiInputDrawerController.bind(aiDrawerRoot)
                      ├── Tab: 键盘/语音/文件
                      └── onSend(text) → streamLLMToPlayground(text)
                           └── WidgetLLMClient.streamChat()
                                └── onChunk(delta) → WidgetPartialParser.feed()
                                     └── surfaceManager.receiveTextChunk()
                                          └── renderContent 渐进渲染
```

### 6.3 会议大屏目标流程（增强方案）

```
会议大屏 (3840×2160)
  ┌─────────────────────────────────────────────────────────┐
  │  Widget 内容区 (左侧 70%)          │  AI Input (右侧 30%)│
  │  ┌───────────────────────┐         │  ┌─────────────────┐│
  │  │                       │         │  │ Header [关闭]    ││
  │  │  AGenUI Surface       │         │  │ 状态: 生成中...  ││
  │  │  (渐进渲染)            │         │  ├─────────────────┤│
  │  │  ← compose-shimmer   │         │  │ 键盘|语音|文件   ││
  │  │     占位动画           │         │  ├─────────────────┤│
  │  │                       │         │  │ 输入区           ││
  │  │                       │         │  │ [chips] [清空]  ││
  │  └───────────────────────┘         │  ├─────────────────┤│
  │  [天气][日程][待办][会议]...       │  │ [取消] [发送]   ││
  │  ─────────────────────────         │  └─────────────────┘│
  └─────────────────────────────────────────────────────────┘

关键增强点:
  1. 面板固定 30% 屏宽 (~1152px @3840)
  2. 左侧 Surface 渐进渲染 + shimmer 占位
  3. 状态条醒目化 (字号 ≥28sp, 颜色编码)
  4. 生成完成后面板保持开启
  5. 支持多轮追问 (ConversationMemory 已有)
```

---

## 七、Action Items（按优先级）

### P0 — 立即可做（不引入新依赖）

| # | Action | 文件 | 预估工作量 |
|---|--------|------|-----------|
| 1 | 面板宽度固定为 560dp | `activity_a2ui_playground.xml` → `drawerRightContainer` `layout_width` | 1 行 |
| 2 | 状态条颜色编码 | `AiInputDrawerController.setStatus()` → 按状态设置 `tvStatus` textColor | 10 行 |
| 3 | 生成完成后面板保持 | `AiInputDrawerController.onSendComplete()` → 不自动关闭 | 已实现 |
| 4 | renderContent 加占位 View | `streamLLMToPlayground()` → 渲染前显示 ProgressBar | 5 行 |

### P1 — 本周内（引入 P0 依赖）

| # | Action | 依赖 | 预估工作量 |
|---|--------|------|-----------|
| 5 | BlurView 背景模糊 | `com.github.Dimezis:BlurView:version-2.0.0` | ~50 行 |
| 6 | compose-shimmer 占位 | `com.valentinilk.shimmer:compose-shimmer:1.3.3` | ~30 行 |
| 7 | 面板圆角 + elevation | `Shape drawable` + `elevation=8dp` | XML |
| 8 | 发送按钮 loading 动画 | `ProgressBar` 内嵌按钮 | ~20 行 |

### P2 — Phase 2 探索

| # | Action | 参考 | 说明 |
|---|--------|------|------|
| 9 | NLU 动态 chips | `WidgetNLUParser` 已有 | 根据 NLU 实体生成 chip |
| 10 | 语音波形动画 | GetStream `AITypingIndicator` | 麦克风旁波形脉冲 |
| 11 | 文件拖拽入口 | Android `dragAndDrop` API | 拖文件到面板触发 |
| 12 | 多线程对话分栏 | SmolChat-Android UI | 大屏左历史+右当前 |
| 13 | Widget 长按快捷菜单 | XPopup AttachPopup | 模板切换/刷新/AI输入 |

### P3 — Phase 3 探索

| # | Action | 说明 |
|---|--------|------|
| 14 | 灵动球悬浮 (Floating-Bubble-View) | Widget → 桌面悬浮球 |
| 15 | 圈选 OCR | 截图 → OCR → 发送 |
| 16 | 小艺慧记 (会议转写) | 实时 STT + 摘要生成 |
| 17 | 复制感应 | 剪贴板监听 → 推荐 chips |

---

## 八、结论

1. **A2UI Playground 的 AI Input 面板已实现小艺 PC 约 60% 的核心交互** — 右侧抽屉形态、三 Tab 切换、语音/文件/键盘三模态输入、LLM 流式渲染、多轮对话记忆均已跑通

2. **最大差距在视觉层** — 缺少 BlurView 玻璃拟态、compose-shimmer 渐进占位、状态颜色编码。这些是 P1 依赖（BlurView 50KB + compose-shimmer 30KB），集成成本低

3. **会议大屏场景已有合理适配** — 深色主题（#0D1117）、大字号（32sp/28sp/24sp）、大触控目标（≥96dp）均满足 3-5 米远距离可读可触控要求

4. **小艺 PC 的"渐进式上下文"设计原则值得借鉴** — 5 种入口从无上下文到强上下文递进，A2UI Playground 已有键盘（无上下文）→语音（轻上下文）→文件（强上下文）的递进，与小艺一致

5. **Phase 2 最高价值方向**: 右键直达快捷菜单 + NLU 动态 chips — 这两个功能能显著降低用户认知负担，且已有 `WidgetNLUParser` 和 `XPopup` 基础设施

6. **不推荐在 Phase 1 追求"灵动球悬浮"** — 这是 Phase 2/3 探索方向，Phase 1 应优先保证现有抽屉面板的视觉质量和交互稳定性
