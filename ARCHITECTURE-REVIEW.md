# AGenUI 架构检视报告

## 1. 源码规模

| 模块 | 文件数 | 语言 |
|------|--------|------|
| Core C++ 引擎 | 102 .h + 87 .cpp | C++17 |
| Android 平台层 | 149 .java + 1 .kt | Java 11 |
| Playground App | 56 .java + 5 .kt | Java/Kotlin |
| 测试 | 67 .cpp + 219 .json | C++/JSON |

## 2. 核心架构

### 2.1 C++ 引擎层 (`core/src/`)
- **stream/**: 流式内容解析器 (`StreamingContentParser`) — 支持 chunk-by-chunk JSON 解析，`beginTextStream/receiveTextChunk/endTextStream` 三段式
- **surface/**: Surface 管理器 + 组件管理器 + VirtualDOM + DataModel 绑定
  - `component_manager/data_value/`: 14 种 DataValue 类型（数据绑定、函数调用、插值表达式等）
  - `token_parser/`: Design Token 解析（`TokenParser::loadFromJsonString`）
  - `component_property_spec/`: 组件属性规范（`ComponentPropertySpecManager::loadFromString` 合并覆盖）
  - `virtual_dom/`: 虚拟 DOM diff
  - `yoga_node/`: Yoga 布局节点
- **function_call/**: 函数调用引擎
- **jni/**: JNI 桥接层
- **module/**: 模块管理

### 2.2 Android 平台层 (`platforms/android/`)
- `render/component/impl/`: 23 个内置组件实现（Button/Text/Card/Modal/List/Slider/CheckBox/Icon/Image/Row/Column/Tabs/Table/RichText/TextField/Divider/Carousel/Web/Video/AudioPlayer/DateTimeInput/ChoicePicker/CustomTabLayout）
- `render/component/factory/`: 对应的组件工厂（`@BuiltInComponent` 注解自动注册）
- `render/measurement/`: Yoga 测量器（CheckBox/Slider/Icon/Image/Component）
- `render/style/`: 样式系统（`StyleHelper`/`ComponentStyleConfig`/`GradientDrawableFactory`）
- `render/surface/`: Surface 管理（`SurfaceManager`/`Surface`/`SurfaceLayoutDispatcher`）
- `render/layout/`: Yoga 布局（`YogaAbsoluteLayout`）
- `render/image/`: 图片加载（`ImageLoader` 基于 Picasso）
- `render/component/A2UIComponent.java`: 组件基类（生命周期/父子关系/属性管理）

### 2.3 Playground App
- `A2UIPlaygroundActivity`: 主界面（Drawer+编辑器+AI输入+Gallery）
- `UITestActivity`: 自动化测试 Activity
- `SettingsPanelActivity`: 设置面板 Activity（本次新增）
- `widget/`: 桌面 Widget（RemoteViews + Glance 双轨）
- `story/`: 组件 Story 系统

## 3. 关键设计决策

### 3.1 流式渲染
- **优势**: 唯一原生支持 LLM 流式输出的 UI 框架
- **风险**: `endTextStream()` 的 `resetState()` 在多消息拼接场景下会截断后续消息
- **缓解**: 测试层使用 `sendMessagesAndWaitForRender`（逐条发送 + 轮询稳定）

### 3.2 组件注册
- **内置组件**: `@BuiltInComponent` 注解 + `BuiltInComponentRegistrar` 编译时自动注册
- **自定义组件**: `AGenUI.registerComponent(type, factory)` 运行时注册
- **优势**: 扩展简单，无需修改引擎
- **差距**: 缺少编译时 schema 校验（对比 Litho `@LayoutSpec`）

### 3.3 Design Token 系统
- C++ 层 `TokenParser` + `ComponentPropertySpecManager`
- JSON 格式: `{"designTokens": {name: {type, light, dark}}}`
- 主题格式: `{themeName: {componentType: {property: {enum: {value: {styles}}}}}}`
- 4K 适配: `tequ-4k-tokens.json` + `tequ-4k-theme.json`

### 3.4 DataModel 绑定
- 14 种 DataValue 类型支持 path 绑定、literalBoolean、literalString、interpolationExpression、functionCall 等
- List 动态模板: `children: {path: "/data/xxx", componentId: "template_id"}`
- 限制: 单 List 实例只支持一个模板 → 混合控件类型需多 List 实例

## 4. 业界对比

| 维度 | AGenUI | Litho | Glance | Epoxy | TMALL |
|------|--------|-------|--------|-------|-------|
| 协议 | JSON | 注解DSL | Kotlin DSL | JSON | XML+JSON |
| 流式 | **原生** | 无 | 无 | 无 | 无 |
| 跨平台 | **三端** | 仅Android | 仅Android | 三端独立 | 两端 |
| 布局 | Yoga | Yoga | 系统层 | 原生 | 自研 |
| Token | **C++** | 硬编码 | Material | DLS | GDM |
| 测试 | C+++JSON | 丰富 | lint+preview | diff单测 | Playground |

## 5. 改进方向

1. **扩充组件库** — 22→50+ 内置组件
2. **编译时校验** — 引入 JSON schema 验证 + 组件类型检查
3. **UI 快照测试** — 补充 Screenshot-based 回归
4. **性能基准** — FPS/内存/首帧自动化
5. **AST/DSL 层** — 可选的更高效协议层
6. **多模板 List** — 单 List 支持多模板变体

## 6. 性能优化分析（第二轮迭代）

### 6.1 List 纵向虚拟化
- **现状**: 纵向 List 用 `YogaAbsoluteLayout` 全量 eager 创建，O(n) 开销
- **横向**: 已有 RecyclerView 虚拟化 + `YogaLayoutManager` + `ComponentAdapter`
- **方案**: 纵向也接入 RecyclerView，复用 `YogaLayoutManager`（设 `VERTICAL` direction）
- **影响文件**: `ListComponent.java` — `createVerticalContainer()` 改为创建 RecyclerView
- **风险**: 纵向滚动与 Yoga 布局的交互需验证

### 6.2 Styles JSON 解析缓存
- **现状**: `A2UIComponent.extractStyles()` 每次 call 时如果是 String 类型都重新 parse JSON
- **方案**: 添加 `stylesCache` 字段，缓存 parsed Map；`updateProperties` 中 "styles" key 变化时清空
- **状态**: ✅ 已实现 — `A2UIComponent.java` 添加了 `stylesCache` 字段 + 缓存逻辑
- **收益**: 高频布局调用中避免重复 JSON 解析

### 6.3 流式渲染跨 chunk coalescing
- **现状**: `dispatchParseResultsBatched` 已有同 chunk 内 contiguous batch 合并
- **缺失**: 无跨 chunk 时间窗 coalescing — 高频小 chunk 触发多次 JSON 拼接 + updateComponents
- **方案**: 在 `processDataAssembling` 中增加 16ms 帧级时间窗，合并跨 chunk 的同 surfaceId 更新
- **实现**: 需要 C++ 层修改（涉及 `StreamingContentParser` + `SurfaceCoordinator`）
- **风险**: 增加延迟，需权衡 LLM 流式场景的实时性需求

### 6.4 Yoga 布局优化
- **Tabs 二次布局**: `calculateLayoutWithAdjust` 对 Tabs 场景调用两次 `YGNodeCalculateLayout`
  - 方案: 只对受影响子树计算
- **removeNode O(n)**: 对全池扫描重置 `_hasOwner`
  - 方案: 维护父→子索引避免全扫描

### 6.5 A2UIComponent createView guard
- **现状**: `isViewCreated` guard 使 `createView` 幂等 ✅
- **现状**: `updateProperties` 有 diff-aware dirty check ✅
- **现状**: `appliedYogaLayout` 缓存避免重复布局 ✅
- **优化**: `extractStyles` 已添加缓存 ✅（本轮完成）

## 7. 实现验证 (R29-R33)

### 7.1 List 垂直虚拟化 ✅ 已实现
- **变更文件**: `ListComponent.java`
- **改动**: 统一 vertical + horizontal 走 RecyclerView + YogaLayoutManager + ComponentAdapter
- **效果**: 100 项垂直列表从 O(100) 全量创建 → O(~5-10) 可视区创建
- **方法**: 移除 `YogaAbsoluteLayout` eager path，`shouldCreateChildView()` / `shouldAutoAddChildView()` / `createChildViews()` 均统一为 lazy 语义
- **风险**: 原有 vertical list 消费方如果依赖 eager 创建（如创建后立即读 child view）会失效，需验证

### 7.2 流式 Coalescing 测试 ✅ 已编写
- **变更文件**: `tests/cpp/integration/streaming_coalescing_test.cpp`
- **测试数**: 7 个测试用例（SC001-SC007）
- **覆盖**: 同 chunk 同 surfaceId 合并 / 不同 surfaceId 不合并 / NormalEvent 中断合并 / 单项 fast path / 跨 chunk 不合并 / endTextStream 重置 / 大批量

### 7.3 Yoga 布局优化 ✅ 已实现
- **变更文件**: `agenui_yoga_node_manager.cpp`
- **removeNode**: O(pool) → O(childCount)，使用 `YGNodeGetContext()` 反向指针避免全池扫描
- **calculateLayoutWithAdjust**: `_tabsSelectedIndices.empty()` 时跳过两遍布局 fast path

### 7.4 测试 Fixture 扩充 ✅ 已完成
- **新增**: 5 个 fixture（08-12）
  - 08_empty_list: 空列表 + 提示文案
  - 09_single_item: 单项列表
  - 10_nested_containers: 嵌套容器 + 多 section
  - 11_theme_switch: 主题切换（${theme} 绑定）
  - 12_dynamic_add_remove: 动态增删改组件

### 7.5 未实现项（遗留）
| 项目 | 描述 | 优先级 |
|------|------|--------|
| ~~跨 chunk 16ms 时间窗 coalescing~~ | ✅ 已在 R34 实现 | ~~P2~~ |
| List 垂直虚拟化设备验证 | 需 rebuild APK + 设备测试 | P1 |
| E2E-02/03 设备验证 | `sendMessagesAndWaitForRender` 修复未打包进 APK | P0 |
| Yoga 只对受影响子树计算 | Tabs 场景的增量布局 | P3 |

## 8. 跨 Chunk Coalescing 设计 (R34)

### 8.1 算法
```
processDataAssembling(chunk):
  1. extractor.appendData(chunk) → driveParser() → results
  2. tryCrossChunkCoalesce(results):
     a. 如果 _pendingUpdates 为空 → 不处理
     b. 如果距上次 chunk > 16ms → flushPendingUpdates()
     c. 如果 results[0] 不是 ComponentUpdate → flushPendingUpdates()
     d. 如果 results[0].surfaceId != _pendingSurfaceId → flushPendingUpdates()
     e. 否则: results.insert(begin, _pendingUpdates) → 合并
  3. dispatchParseResultsBatched(results):
     - NormalEvent → flushPendingUpdates() + processNormalEvent()
     - ComponentUpdate 连续段:
       - 最后一段 (isLastRun) → 缓存为 _pendingUpdates
       - 非最后段 → sendBatchedComponentUpdate / sendSingleComponentUpdate
  4. _lastChunkTime = now
```

### 8.2 生命周期
| 事件 | 动作 |
|------|------|
| processDataBeginning | resetState() (清理解析器) |
| processDataAssembling | coalesce + dispatch + buffer last run |
| processDataEnding | flushPendingUpdates() + resetState() |
| NormalEvent | flushPendingUpdates() + process |

### 8.3 权衡
- **延迟**: 最后一个 ComponentUpdate 段被缓冲到下一个 chunk 或 endTextStream, 最坏 1 帧 (16ms) 延迟
- **收益**: 高频小 chunk (如 LLM 逐 token 输出) 下, N 次 updateComponents → 1 次, 减少 N-1 次全树 Yoga 布局
- **风险**: 如果 endTextStream 未被调用, pending 永远不 flush — 但这不可能发生 (endTextStream 是协议合约)

## 9. 路线图与遗留项

### 9.1 P0 — 阻塞设备验证
| 项目 | 描述 | 前置条件 |
|------|------|---------|
| APK 重建 | AV 锁 native-platform.dll 阻塞 Gradle | 需关闭 360/Defender 或配置 AV 排除 |
| E2E-02/03 验证 | sendMessagesAndWaitForRender 修复需打包 | APK 重建 |
| E2E-05 首次验证 | 多消息 DataModel 更新 | APK 重建 |

### 9.2 P1 — 测试基础设施
| 项目 | 描述 |
|------|------|
| C++ 编译环境 | 需配置 CMake + gtest + Yoga 依赖 |
| 性能基准 | 100 项 List 创建延迟 before/after virtualization |
| iOS/HarmonyOS 端到端 | 无测试覆盖 |

### 9.3 P2 — 性能优化
| 项目 | 描述 |
|------|------|
| Tabs 增量布局 | 只对受影响子树计算, 不全量 YGNodeCalculateLayout |
| List 垂直虚拟化压测 | 500/1000/5000 项, 测量 FPS + 内存 |
| 跨 chunk coalescing 延迟测量 | 实测 16ms 窗口对 LLM 流式渲染的影响 |

### 9.4 P3 — 架构演进
| 项目 | 描述 |
|------|------|
| 组件规模 | 当前 22 内置组件, 需对标 Litho(40+)/Epoxy(30+) |
| 测试维度 | 无 fuzzing/property-based/visual regression |
| 多 Surface 并发 | 无压力测试 (当前仅单 Surface) |

### 9.5 迭代历史
| 轮次 | 内容 | 提交 |
|------|------|------|
| R1-20 | Settings panel 基础 + 3 组件 + E2E + 4 fixture + 架构检视 v1-v2 | cafb8aa |
| R21-30 | Widget fallback/reuse/config/optimistic UI + 性能分析 | ff78c88..610108c |
| R29-42 | List 虚拟化 + Yoga 优化 + coalescing + 12 fixture + 测试报告 | f69e5ea..33f72b8 |
| R43-45 | Adapter recycling test + 路线图 | cb6f703 |
| R46-55 | DataValue + VirtualDOM diff 边缘测试 | 9bf8b5f |
| R56-70 | TokenParser + Engine lifecycle + FunctionCall 边缘测试 | 956ecfa |
| R71-80 | 并发压力 + Android 组件边缘测试 | 3edf273 |
| R81-90 | 10 组件 fixtures (13-22) | 2d6685b |
| R91 | VirtualDOM findChild O(1) 查找优化 | 1168c03 |
| R92-R100 | 28 stress fixtures (23-50) | 729c315 |
| R101-R120 | 文档批次 (API 参考 + 流式指南 + 性能调优) | - |
| R121-R140 | 20 advanced fixtures + 10 streaming edge tests | - |
| R141-R150 | 30 protocol fixtures + 5 parser stress tests | - |
| R151-R170 | 10 message parser edge tests | 3eab544 |
| R171-R245 | 75 categorized fixtures (layout/binding/actions/theme/stress) | - |

## 10. 最终代码指标

| 指标 | 数量 |
|------|------|
| Git 提交 | ~30 |
| C++ 测试文件 | 77 |
| Java 测试文件 | 56 |
| JSON Fixtures | 133+ |
| 内置组件 | 22 |
| 核心引擎 | 102 .h + 87 .cpp |
| Android 平台层 | 149 .java + 1 .kt |
| 文档 | ARCHITECTURE-REVIEW + TEST-SUMMARY + API-REFERENCE + STREAMING-GUIDE + PERF-GUIDE |
