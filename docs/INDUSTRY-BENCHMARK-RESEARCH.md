# 业界调研报告 — 会议/教育 Widget 交互体验对标

> 生成日期：2026-08-27
> 分支：main
> 调研范围：会议、教育、笔记、AI 平台类 Widget/小组件的交互体验
> 基线：AGenUI 20 轮迭代后（10 模板 + Bitmap 渲染 + LLM 意图识别 + 三层缓存）

---

## 一、调研对象与方法

### 1.1 调研产品矩阵

| 产品 | 平台 | 场景 | 调研重点 |
|------|------|------|----------|
| Google Material Design Widget 指南 | Android | 通用 | 官方设计规范、布局类型、交互限制 |
| Jetpack Glance (2024/2025) | Android | 通用 | 现代 Widget 开发范式、声明式 UI |
| 腾讯会议 | 全平台 | 会议 | 浮窗/画中画、会中交互、一键入会 |
| 飞书小组件 | iOS/Android | 办公/会议 | 日历/任务/云文档/常用工具四类 Widget |
| Notion Widget | iOS/Android | 笔记/教育 | 页面/数据库/AI 快捷方式 Widget |
| Apple WidgetKit (iOS 17+) | iOS | 通用 | AppIntent 交互式 Widget、TimelineProvider |
| Coze 意图识别节点 | 云端 | AI Agent | 意图分类、Agent 路由、工作流编排 |
| Dify 工作流 | 云端 | AI Agent | RAG 检索、条件分支、多智能体路由 |

### 1.2 调研方法

- **WebSearch**：7 组中英文关键词搜索，覆盖官方文档、技术博客、最佳实践
- **WebFetch**：抓取官方设计规范和 API 文档，提取交互模式细节
- **代码分析**：阅读 AGenUI 项目 `PROJECT-STATUS-v3.md`、`ARCHITECTURE-REVIEW.md`、`GLANCE-EVOLUTION-EVALUATION.md` 等 5 份核心文档
- **对比维度**：交互模式、意图识别、性能策略、体验反馈

---

## 二、业界产品对比矩阵

### 2.1 交互模式对比

| 维度 | AGenUI | Google Material Widget | 腾讯会议 | 飞书 | Notion | Apple WidgetKit |
|------|--------|------------------------|----------|------|--------|------------------|
| **渲染技术** | Bitmap 渲染 + RemoteViews | 原生 RemoteViews / Glance | 原生应用浮窗 | 原生 RemoteViews | 原生 WidgetKit | SwiftUI |
| **交互能力** | PendingIntent 点击 | 点击 + CompoundButton (Android 12+) | 浮窗 + 画中画 + 工具栏 | 点击 + 编辑 + 任务勾选 | 点击跳转 + AI 快捷方式 | Button + Toggle (iOS 17+) |
| **响应式布局** | 固定 300x400 | SizeMode.Responsive 断点 | 浮窗可拉伸 | 小/中/大三尺寸 | 小/中/大 + 可编辑 | 五种 widgetFamily |
| **可编辑性** | 模板栏切换 | 配置 Activity | 浮窗位置/大小 | 长按编辑（iOS） | 长按编辑选择页面 | AppIntentConfiguration |
| **空状态** | 无 | 有引导 | N/A（应用内） | 有登录引导 | 有 AI 快捷方式 | 有 placeholder |
| **加载状态** | 无 | 有 | N/A | 有 | 有 | 有 |
| **错误状态** | pushErrorWidget | 无标准 | N/A | 无 | 无 | ErrorContent |
| **评分 (1-5)** | **2.5** | **4.5** | **4.0** | **4.0** | **4.0** | **4.5** |

### 2.2 意图识别对比

| 维度 | AGenUI | Coze | Dify | Apple App Intent |
|------|--------|------|------|------------------|
| **识别方式** | 关键词 + 同义词 + 模糊匹配 + LLM | LLM 意图识别节点 | LLM + 条件分支路由 | 用户配置 AppIntent |
| **分类数量** | 10 类模板 | 极速模式 ≤10 / 完整模式 ≤50 | 无限制（DAG 节点） | 由开发者定义 |
| **上下文感知** | WidgetConversationMemory 多轮记忆 | 对话历史 + 会话轮数 | 上下文工程 + RAG | 无对话上下文 |
| **兜底策略** | 降级模板 + 推荐 | "其他"分支转人工 | 条件分支 + 兜底节点 | 无匹配不触发 |
| **配置灵活性** | 硬编码关键词词典 | 可视化配置 + 系统提示词 | 可视化工作流编辑器 | 代码定义 |
| **动态调整** | 需重新编译 | 运行时配置 | 运行时配置 | 需 App 更新 |
| **置信度评分** | WidgetTemplateRecommender | classificationId + reason | 无显式评分 | 无 |
| **异常处理** | LLM Failover + 降级模板 | 超时/重试/异常分支 | 条件分支兜底 | perform() 抛异常 |
| **评分 (1-5)** | **3.0** | **4.5** | **4.0** | **3.5** |

### 2.3 性能策略对比

| 维度 | AGenUI | Google 官方示例 | 飞书 | Apple WidgetKit |
|------|--------|----------------|------|------------------|
| **缓存层级** | L1 ProtocolCache + L2 BitmapCache + L3 SurfacePool + L4 Preloader | WorkManager + DataStore | 未公开 | TimelineProvider + 系统快照 |
| **预渲染** | prerenderAll 10 模板 | WorkManager 周期更新 | 未公开 | TimelineProvider 预生成 |
| **刷新机制** | 主动 updateAppWidget + 系统 30min | WorkManager 15min 最小 | 系统 onUpdate | TimelineProvider 15min 最小 |
| **Bitmap 传递** | 内存 setImageViewBitmap | 文件缓存 + ImageProvider | 未公开 | 系统快照（无需跨进程） |
| **内存预算** | LruCache 3MB | 无固定上限 | 未公开 | 系统管理 |
| **增量更新** | 全量重绘 | 局部更新 | 未公开 | TimelineEntry 差分 |
| **评分 (1-5)** | **3.5** | **4.0** | **3.5** | **4.5** |

### 2.4 体验反馈对比

| 维度 | AGenUI | Google Material | 腾讯会议 | 飞书 | Notion | Apple |
|------|--------|-----------------|----------|------|--------|-------|
| **交互反馈速度** | PendingIntent IPC 往返 | actionRunCallback 进程内 | 浮窗实时 | 点击即响应 | 点击即响应 | AppIntent 进程内 |
| **视觉一致性** | Bitmap 整图绘制（与系统主题脱节） | Material You 动态配色 | 蓝色品牌一致 | 跟随系统主题 | 跟随系统主题 | 跟随系统主题 |
| **可访问性** | 无无障碍支持 | Material 颜色角色 + 对比度 | 未明确 | 未明确 | 未明确 | HIG 无障碍标准 |
| **深色模式** | 无 | 动态主题适配 | 有 | 有 | 有 | 有 |
| **动画过渡** | 无（Bitmap 静态） | 无（RemoteViews 限制） | 浮窗动画 | 切换动画 | 无 | 无（静态快照） |
| **多实例支持** | 单实例 | 多实例 | 多浮窗 | 多 Widget | 多 Widget | 多 Widget |
| **评分 (1-5)** | **2.0** | **4.0** | **4.0** | **4.0** | **4.0** | **4.5** |

---

## 三、AGenUI 的优势和差距

### 3.1 AGenUI 的优势

#### 优势 1：AI 驱动的动态内容生成（差异化核心竞争力）

AGenUI 是调研中唯一通过 LLM 实时生成 Widget 内容的产品。业界产品（飞书、Notion、腾讯会议）的 Widget 都是展示**已有数据**（日历事件、任务列表、文档内容），而 AGenUI 能根据用户自然语言输入**动态创建** Widget 内容。

```
业界产品：数据源 → Widget 展示（静态映射）
AGenUI：用户输入 → LLM → A2UI 协议 → Bitmap 渲染（动态生成）
```

**对标**：Notion 的 AI 快捷方式仅跳转到 AI 对话界面，不直接在 Widget 上展示 AI 生成内容。AGenUI 的"输入即生成"模式在会议/教育场景有独特价值（"帮我生成一个会议倒计时 Widget"）。

#### 优势 2：三层缓存体系设计成熟

AGenUI 的 L1-L4 缓存分层（ProtocolCache → BitmapCache → SurfacePool → Preloader）在业界属领先设计。Google 官方示例仅用 WorkManager + DataStore 两层，飞书/Notion 未公开缓存细节。

**对标**：Apple 的 TimelineProvider + 系统快照虽由系统管理，但开发者无法控制缓存策略。AGenUI 的可控缓存为低延迟渲染提供了基础。

#### 优势 3：意图匹配三级策略

AGenUI 的"精确匹配 → 同义词匹配 → 模糊匹配"三级策略，加上置信度评分（WidgetTemplateRecommender），在端侧意图识别中设计合理。Coze 的极速模式类似但依赖云端 LLM，AGenUI 的本地关键词匹配可离线工作。

#### 优势 4：降级链完整

LLM 多 tier Failover + 意图匹配 + NLU 实体提取 + 对话记忆 + 降级模板，AI 链路的容错设计比 Coze 的"异常分支"更精细。

### 3.2 AGenUI 的差距

#### 差距 1：渲染模式限制交互与视觉一致性（严重）

**现状**：AGenUI 用 Bitmap 整图渲染 → `setImageViewBitmap` 推送，所有内容是一张静态图片。

**业界对比**：
- Google Material Widget 用原生 RemoteViews 组件，支持动态配色、CompoundButton、列表滚动
- 飞书 Widget 用原生组件，跟随系统主题（深色模式、动态配色）
- Apple WidgetKit 用 SwiftUI，系统自动适配主题

**影响**：
- 视觉与系统主题脱节（深色模式不适配、不跟随壁纸动态配色）
- 无法支持交互控件（CheckBox/Switch/RadioButton）
- 无法滚动列表（会议议程多item时只能压缩或截断）
- 无障碍支持缺失（Bitmap 无法提供 contentDescription）

#### 差距 2：单实例限制（严重）

**现状**：AGenUI 仅支持单个 Widget 实例，`DEFAULT_CACHE_WIDGET_ID = 0` 硬编码。

**业界对比**：飞书、Notion、Apple 均支持多 Widget 实例同屏并存。用户典型场景：桌面同时放"今日议程"+"会议倒计时"+"待办事项"三个 Widget。

**影响**：这是当前最大的体验瓶颈，无法满足会议/教育场景的多信息同屏需求。

#### 差距 3：交互反馈延迟（中等）

**现状**：AGenUI 用 PendingIntent（IPC 往返）处理点击。

**业界对比**：
- Jetpack Glance 用 `actionRunCallback`（进程内回调，零 IPC）
- Apple WidgetKit 用 AppIntent（系统进程内执行 perform()）
- 飞书/Notion 点击即跳转（无复杂交互）

**影响**：PendingIntent 的 IPC 序列化导致点击反馈有延迟，尤其在定制 ROM 上更明显。

#### 差距 4：无空状态/加载状态/错误状态的完整状态机（中等）

**现状**：AGenUI 只有 `pushErrorWidget` 简单错误处理，无空状态引导、无加载中占位。

**业界对比**：
- Google 官方：Empty/Loading/Error 三态 + 可重试
- Apple：placeholder + ErrorContent + 重试按钮
- 飞书：未登录时显示登录引导

**影响**：首次添加 Widget 时空白，用户不知如何生成内容；LLM 生成中无加载反馈。

#### 差距 5：意图识别的灵活性与可配置性（中等）

**现状**：AGenUI 的关键词词典、城市名、时间关键词硬编码在 Java 代码中，新增需重新编译。

**业界对比**：
- Coze：可视化配置意图分类 + 系统提示词 + 运行时调整
- Dify：DAG 工作流编辑器 + 条件分支动态路由
- Apple：AppIntentConfiguration 用户配置参数

**影响**：无法 A/B 测试关键词集、无法远程下发新意图、无法动态扩展模板。

#### 差距 6：无响应式布局适配（中等）

**现状**：AGenUI 固定 300x400 尺寸，resize 时不适配。

**业界对比**：
- Google：SizeMode.Responsive + 断点布局（Compact/Standard/Expanded）
- 飞书：小/中/大三尺寸不同信息密度
- Apple：五种 widgetFamily 自适应

#### 差距 7：缺少数据动态绑定（较低）

**现状**：模板中组件文本为静态值，dataModel 作为数据快照存在但未动态绑定（见 PROJECT-STATUS-v3.md 5.1 节）。

**业界对比**：飞书日历 Widget 实时显示日程变化；Notion 数据库 Widget 打开时自动更新。

---

## 四、可落地的改进建议（按优先级排序）

### P0 — 立即改进

#### 建议 1：实现多 Widget 实例支持

**对标**：飞书/Notion/Apple 均支持多实例
**方案**：
- Bitmap 缓存 key 按 appWidgetId 隔离
- SurfaceManager 支持多 surfaceId 并发
- Widget 配置 Activity（选择模板 + 自定义数据）
- 缓存预算按实例数动态分配

**预期收益**：解决最大体验瓶颈，支持"议程+会议+待办"三 Widget 同屏

#### 建议 2：引入空状态/加载状态/错误状态三态

**对标**：Google 官方 Empty/Loading/Error 模式 + Apple placeholder
**方案**：
- 空状态：显示"点击生成 Widget"引导按钮（参考 Glance 演进版的 EmptyContent）
- 加载状态：LLM 生成中显示骨架屏或进度条
- 错误状态：显示错误信息 + 重试按钮（参考 Glance 演进版的 ErrorContent）
- 可复用 `feature/glance-evolution` 分支的 R18 ErrorState 实现

#### 建议 3：Bitmap 生命周期安全修复

**对标**：业界标准 Bitmap 引用管理
**方案**：
- 修复 `pushBitmapToWidget` 中 smaller bitmap 未 recycle 泄漏（P1-3）
- 修复 `WidgetBitmapCache.clear` 重复 recycle 问题（P2-2）
- 增加"使用中"标记避免 cache 时正在被 RemoteViews 使用
- 缓存预算从 3MB 动态调整为 模板数 × 400KB × 2

### P1 — 短期改进

#### 建议 4：响应式布局适配

**对标**：Google SizeMode.Responsive + 飞书三尺寸
**方案**：
- 根据 widget 尺寸选择布局模式（compact/standard/expanded）
- compact：标题 + 关键信息（如天气温度）
- standard：当前 300x400 布局
- expanded：标题 + Bitmap + 按钮组 + 额外详情
- 可参考 `feature/glance-evolution` 的 SizeMode.Responsive 三断点实现

#### 建议 5：模板注册表模式统一配置

**对标**：Coze 可视化配置 + Apple AppIntentConfiguration
**方案**：
- 引入 `WidgetTemplateRegistry`，将模板名/按钮 ID/关键词/模糊变体/模板文件统一配置
- 新增模板只需添加一项 + 创建 JSON 文件
- 关键词词典改为 JSON 配置，支持远程下发
- 消除 `AVAILABLE_TEMPLATES` / `TEMPLATE_BUTTON_IDS` / `DEFAULT_ORDER` 长度不匹配问题

#### 建议 6：意图识别配置化 + 置信度阈值

**对标**：Coze 极速/完整模式 + 系统提示词
**方案**：
- 关键词词典从 Java 硬编码改为 assets/JSON 配置
- 支持运行时更新关键词集（无需重新编译）
- 增加置信度阈值：低于阈值时走 LLM 兜底（当前已有 WidgetTemplateRecommender）
- 参考 Coze 的"对话历史 + 会话轮数"机制增强多轮意图识别

#### 建议 7：视觉一致性 — 深色模式 + 动态配色

**对标**：Google Material You + 飞书/Apple 跟随系统主题
**方案**：
- Bitmap 渲染时读取系统深色/浅色模式，选择对应配色
- 使用 Material 颜色角色（colorPrimary、colorSurface 等）替代硬编码颜色
- 跟随 Android 12+ 动态壁纸配色（WallpaperColors）
- 这需要 AGenUI 引擎支持主题感知渲染

### P2 — 中期改进

#### 建议 8：混合渲染 — Glance 原生组件叠加 Bitmap

**对标**：Jetpack Glance + Google 官方 Weather Widget 示例
**方案**：
- 保留 Bitmap 作为主内容区（AGenUI 引擎渲染）
- 用 Glance 原生组件渲染标题栏、按钮组、空/加载/错误状态
- `actionRunCallback` 替代 PendingIntent，消除 IPC 延迟
- 可直接复用 `feature/glance-evolution` 分支的混合架构设计

#### 建议 9：端侧推理替代云端 LLM

**对标**：Coze 极速模式（云端快速分类）+ 本地 NLU
**方案**：
- 训练 TFLite 轻量意图分类模型（10 类模板）
- 本地实体提取（时间/地点/人名/数字）替代云端 NLU
- 简单意图走端侧（<50ms），复杂生成走云端 LLM
- 用户无感切换端云策略

#### 建议 10：交互控件支持

**对标**：Google CompoundButton (Android 12+) + Apple Toggle
**方案**：
- 支持 CheckBox（todo 模板标记完成）
- 支持 Switch（会议模板静音/摄像头切换）
- 支持 RadioButton（投票模板选项选择）
- 需从纯 Bitmap 渲染迁移到混合渲染（见建议 8）

#### 建议 11：AI 助手集成

**对标**：Google Assistant App Actions + Apple SiriKit
**方案**：
- 注册系统 VoiceIntent，支持"给待办加一项买菜"等自然语言指令
- AI 助手感知当前 Widget 状态，支持"切换到下周视图"等上下文操作
- 跨 Widget 联动："把今天的会议加到待办" — 从 meeting 提取 action items 写入 todo

### P3 — 长期演进

#### 建议 12：数据动态绑定

**对标**：飞书日历 Widget 实时日程同步 + Notion 数据库 Widget 自动更新
**方案**：
- 模板组件文本与 dataModel 字段动态绑定
- 支持 ContentProvider / DataStore 观察者模式
- 外部数据变化时自动触发 Widget 重渲染

#### 建议 13：跨设备同步

**对标**：飞书多端协同 + Apple iCloud 同步
**方案**：
- 用户 Widget 配置上传云端
- 基于 timestamp + diff 的增量同步
- 多设备适配（手机/平板/大屏自适应布局）

#### 建议 14：无障碍支持

**对标**：Google Material 颜色角色 + Apple HIG 无障碍标准
**方案**：
- Bitmap 渲染时生成对应的 contentDescription
- 确保文本对比度符合 WCAG 标准
- 支持动态字体大小

---

## 五、值得借鉴的设计模式

### 5.1 Google Material Design Widget 规范布局

**模式**：文本/工具栏/列表/网格四类规范布局 + 断点响应式

**借鉴点**：
- AGenUI 的 10 个模板可归类为：天气/会议/课堂属"文本+图片"，议程/日历/待办属"列表"，投票属"操作列表"
- 借鉴断点机制：compact 尺寸只显示标题+核心数据，expanded 尺寸显示完整内容+操作按钮
- 官方提供 Figma Widget Canonical Builder 设计组件库

### 5.2 Apple WidgetKit 的 AppIntent 交互模式

**模式**：iOS 17+ 支持 Button + Toggle + AppIntent 直接交互，无需打开 App

**借鉴点**：
- AGenUI 的 todo 模板可借鉴 Toggle 模式直接标记完成
- 投票模板可借鉴 Button 模式直接选择选项
- AppIntent 的 perform() 异步执行 + Swift 并发模式可参考用于端侧推理

### 5.3 Coze 意图识别的极速/完整双模式

**模式**：极速模式（快速分类，≤10 意图）+ 完整模式（系统提示词，≤50 意图）

**借鉴点**：
- AGenUI 可实现类似双模式：极速模式用本地关键词匹配（当前已有），完整模式调用云端 LLM
- "对话历史 + 会话轮数"机制可增强 WidgetConversationMemory 的多轮意图识别
- 兜底策略（"其他"分支）对应 AGenUI 的降级模板

### 5.4 飞书 Widget 的尺寸分层信息密度

**模式**：小/中/大三种尺寸展示不同信息密度

**借鉴点**：
- AGenUI 固定 300x400 可改为三尺寸：
  - 小尺寸（2x2）：仅标题 + 核心数据（天气温度/会议倒计时）
  - 中尺寸（4x2）：标题 + 数据 + 1-2 操作按钮
  - 大尺寸（4x4）：完整内容 + 模板栏 + 按钮组

### 5.5 Notion 的 AI 快捷方式 Widget

**模式**：Widget 直接提供 AI 对话/相机/语音快捷入口

**借鉴点**：
- AGenUI 可增加"AI 生成"快捷按钮 Widget，一键打开 WidgetInputActivity
- 结合端侧推理（建议 9），实现零延迟意图识别 + 模板生成

### 5.6 Jetpack Glance 的 collectAsState 状态管理模式

**模式**：DataStore + StateFlow + collectAsState 声明式状态管理

**借鉴点**：
- AGenUI 当前用 SharedPreferences（同步阻塞），可迁移到 DataStore（协程友好）
- 状态变化自动触发 Widget 重渲染，替代手动 updateAppWidget 调用
- `feature/glance-evolution` 分支已实现此模式，可参考

---

## 六、总结

### 6.1 AGenUI 的定位

AGenUI 在"AI 驱动动态内容生成"这一维度具有业界差异化优势，是少数能让用户用自然语言**创建** Widget 内容（而非仅展示已有数据）的产品。这一能力在会议/教育场景（"帮我生成一个会议倒计时 Widget"、"做一个课堂笔记卡片"）有独特价值。

### 6.2 核心差距总结

| 差距维度 | 严重程度 | 业界标杆 | AGenUI 现状 |
|----------|----------|----------|-------------|
| 渲染模式（Bitmap vs 原生组件） | 严重 | Google/Apple 原生组件 | Bitmap 整图，无交互控件/主题适配 |
| 多实例支持 | 严重 | 飞书/Notion/Apple 多实例 | 单实例硬编码 |
| 视觉一致性 | 严重 | Material You 动态配色 | 不适配深色模式/动态主题 |
| 交互反馈延迟 | 中等 | Glance actionRunCallback | PendingIntent IPC 往返 |
| 状态完整性 | 中等 | Empty/Loading/Error 三态 | 仅错误处理 |
| 意图配置化 | 中等 | Coze 可视化配置 | 硬编码关键词 |
| 响应式布局 | 中等 | SizeMode.Responsive | 固定 300x400 |

### 6.3 改进路径建议

**短期（1-2 个迭代）**：多实例支持 + 状态三态 + Bitmap 安全 + 响应式布局
**中期（3-5 个迭代）**：混合渲染（Glance + Bitmap）+ 端侧推理 + 意图配置化 + 深色模式
**长期（6+ 个迭代）**：交互控件 + AI 助手集成 + 数据动态绑定 + 跨设备同步

**关键决策点**：是否从纯 Bitmap 渲染迁移到混合渲染（Glance 原生组件 + Bitmap 内容区），这是解决交互/视觉一致性/响应式三大差距的核心。`feature/glance-evolution` 分支已验证此方案可行性，建议设备验证后合入。

---

## 七、参考来源

| 来源 | URL |
|------|-----|
| Android Widget 设计指南 — 布局 | https://developer.android.google.cn/design/ui/mobile/guides/widgets/layouts |
| Android Widget 设计指南 — 样式 | https://developer.android.google.cn/design/ui/mobile/guides/widgets/style |
| App Widgets 概览 | https://android-dot-devsite-v2-prod.appspot.com/develop/ui/views/appwidgets/overview |
| Widgets on Android UI Design | https://android-dot-devsite-v2-prod.appspot.com/design/ui/widget |
| App Widgets 设计与开发（中文总结） | https://liyuyu.cn/post/app-widgets-design-and-develop/ |
| 腾讯会议浮窗显示 | https://meeting.tencent.com/support/topic/1526 |
| 腾讯会议应用配置 | https://meeting.tencent.com/support/topic/2157/index.html |
| 飞书桌面小组件 | https://www.feishu.cn/hc/zh-CN/articles/360049067876 |
| 飞书日历小组件 | https://www.f.mioffice.cn/hc/zh-CN/articles/504198222843 |
| 飞书常用工具小组件 | https://www.f.mioffice.cn/hc/zh-CN/articles/031796699668 |
| 飞书任务小组件 | https://www.rwork.crc.com.cn/hc/zh-CN/articles/792528522063 |
| Notion 移动端小组件 | http://notion.so/zh-cn/help/mobile-widgets |
| 7 Best Widgets for Android 2026 | https://www.techwench.com/best-widgets-android-home-screen-2026/ |
| iOS 桌面可交互 Widget 开发指南 | https://juejin.cn/post/7576378563546005556 |
| Apple WidgetKit 交互式 Widget | https://developer.apple.com/documentation/widgetkit/adding-interactivity-to-widgets-and-live-activities |
| Making Widgets Interactive using App Intents | https://www.tiagohenriques.dev/blog/interactive-widgets-using-app-app-intents |
| Create Interactive Widgets with WidgetKit & SwiftUI | https://hax4us.github.io/2025-11-19-create-interactive-widgets-with-widgetkit-swiftui/ |
| How to Do App Widgets 2025 | https://widgetopia.io/blog/how-to-do-app-widgets-1764676047771-konf |
| Coze 意图识别节点 | https://docs.coze.cn/guides/intent_recognition_node |
| Coze 识别用户意图教程 | https://docs.coze.cn/tutorial/workflow_intent_recognition |
| Coze+Dify 企业级 Agent 编排 | https://cloud.tencent.cn/developer/article/2704040 |
| AI Agent 工作流五大核心模式 | https://blog.csdn.net/weixin_34354173/article/93950933 |
| Jetpack Glance in 2025 | https://www.codingbihar.com/2025/08/jetpack-glance-in-2025.html |
| Jetpack Glance: Modern Way to Build App Widgets | https://softaai.com/jetpack-glance-a-modern-way-to-build-app-widgets/ |
| Android Widgets with Glance: Google I/O 2024 | https://engineering.zooz.com/@ssharyk/android-widgets-with-glance-whats-new-with-google-i-o-2024-08b85b7ce676 |
| AGenUI 项目状态 v3 | docs/PROJECT-STATUS-v3.md |
| AGenUI 架构检视报告 | docs/ARCHITECTURE-REVIEW.md |
| AGenUI Glance 演进评估 | docs/widget/GLANCE-EVOLUTION-EVALUATION.md |
| AGenUI Glance 演进方案 | docs/widget/GLANCE-EVOLUTION-PLAN.md |
| AGenUI 性能基线 | docs/PERFORMANCE-BASELINE.md |
| AGenUI ROADMAP v2 | docs/ROADMAP-v2.md |
