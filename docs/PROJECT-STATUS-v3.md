# AGenUI 项目状态报告 v3

> 更新日期：2026-08-27
> 当前分支：main
> 总提交数：213+

---

## 一、里程碑总结

AGenUI 项目从技术验证到产品化打磨，经历了 Phase 1-4 + ITERATION-PLAN-v2 + 20 轮自迭代，完整覆盖了从 A2UI 协议到 Android 桌面小组件的全链路。

### 1.1 Phase 1-4 主线

| 阶段 | 目标 | 状态 |
|------|------|------|
| Phase 0 | Web 端原型验证（LLM → A2UI → Widget 预览） | ✅ 完成 |
| Phase 1 | Android Widget 骨架（Bitmap 渲染桥接链路验证） | ✅ 完成 |
| Phase 2 | 模板 + 输入交互（10 模板 + 文字/语音/文件三链路） | ✅ 完成 |
| Phase 3 | 性能优化（LRU 缓存 + Surface 池 + 预渲染） | ✅ 完成 |
| Phase 4 | Glance 迁移预研（评估结论：保持 RemoteViews） | ✅ 完成 |

### 1.2 ITERATION-PLAN-v2（16 轮 + 4 轮打磨）

| 轮次 | 主题 | 关键交付 |
|------|------|----------|
| 1-4 | Bitmap 缓存 + 预渲染 | LruCache、Surface 池、模板预热 |
| 5-8 | LLM 意图识别 + 多轮记忆 | 意图匹配、置信度评分、会话记忆 |
| 9-12 | NLU 提取 + 模板丰富 | 实体抽取、议程/投票/会议模板 |
| 13-16 | 教育场景 + 模板栏 | classroom、flashcard、模板切换栏 |
| 17 | 模板完整性校验脚本 | validate_templates.py + 修复 weather/todo |
| 18 | Widget 交互链路完整测试 | test_widget_interactions.sh |
| 19 | 性能基线 + 指标收集 | measure_performance.py + 基线报告 |
| 20 | 项目状态文档 + ROADMAP | PROJECT-STATUS-v3.md + ROADMAP-v2.md |

---

## 二、模板清单（10 个）

所有模板位于 `playground/android/app/src/main/assets/widget_templates/`，统一采用三段结构：`createSurface` + `updateComponents` + `updateDataModel`。

| 模板 | 场景 | 数据模型字段 | 视图模式 |
|------|------|-------------|----------|
| weather | 天气实况 | city, condition, temperature, humidity, wind, aqi | current / forecast |
| agenda | 今日议程 | view, weekMeetings[] | today / week |
| todo | 待办事项 | title, progress, view, items[] | pending / completed |
| calendar | 日历日程 | date, todayCount, events[] | — |
| poll | 投票 | question, options[], totalVotes, timeRemaining, leadingOption | — |
| note | 会议笔记 | title, session, summary, bullets[], todoCount | — |
| notecard | 错误卡片 | type, title, message, detail, actions[] | error |
| meeting | 会议倒计时 | meetingTitle, countdown, startTime, attendees[], agenda[] | — |
| classroom | 课堂笔记 | courseName, points[], tags[], timestamp | — |
| flashcard | 知识卡片 | subject, question, answer, cardIndex, totalCards | — |

### 2.1 组件类型覆盖

模板使用的 AGenUI 组件类型：Card、Column、Row、Container、Text、Divider、CheckBox，以及通过 Container + 百分比 width 实现的进度条。

---

## 三、架构概览

### 3.1 LLM 链路

```
用户输入（文字/语音/文件）
  ↓
WidgetInputActivity（透明 Activity，统一入口）
  ↓
WidgetIntentMatcher（意图匹配 + 同义词 + 模糊匹配）
  ↓
WidgetTemplateRecommender（置信度评分 + 模板推荐）
  ↓
WidgetNLUParser（实体提取，丰富模板数据）
  ↓
WidgetPromptBuilder（few-shot 构造 + 历史上下文）
  ↓
WidgetLLMClient（多模型 Failover + SSE 流式）
  ↓
WidgetPartialParser（增量 JSON 解析）
  ↓
WidgetProtocolValidator（三级校验 + 降级模板）
  ↓
AGenUIWidgetRenderService（渲染服务）
```

### 3.2 缓存层级

```
┌──────────────────────────────────────────┐
│  L1: WidgetProtocolCache (SharedPreferences)  │  持久化模板 JSON
├──────────────────────────────────────────┤
│  L2: WidgetBitmapCache (LruCache, 3MB)  │  内存 Bitmap 缓存
├──────────────────────────────────────────┤
│  L3: WidgetSurfacePool                  │  Surface 复用池
├──────────────────────────────────────────┤
│  L4: WidgetTemplatePreloader             │  模板预加载
└──────────────────────────────────────────┘
```

### 3.3 渲染管线

```
AGenUIWidgetRenderService.renderSync()
  │
  ├─ Step 0: Bitmap cache HIT? → 直接 push（最快路径）
  │
  ├─ Step 1: WidgetProtocolTemplates.loadTemplate() → 模板 JSON
  │
  ├─ Step 2: WidgetFallbackBuilder.convertToVersionFormat() → A2UI 信封
  │
  ├─ Step 3: AGenUI引擎 → SurfaceManager → Surface
  │
  ├─ Step 4: Surface.measure() + View.draw(Canvas) → Bitmap
  │
  ├─ Step 5: WidgetBitmapCache.put() → 缓存 Bitmap
  │
  └─ Step 6: RemoteViews.setImageViewBitmap() → updateAppWidget()
```

### 3.4 Widget 模块文件清单

`playground/android/app/src/main/java/com/amap/agenuiplayground/widget/` 下共 31 个 Java 文件，核心职责：

| 类 | 职责 |
|----|------|
| A2UIWidgetProvider | AppWidgetProvider，RemoteViews 构建 |
| AGenUIWidgetRenderService | JobIntentService，渲染调度 |
| WidgetRenderActivity | 透明 Activity，Surface + Bitmap |
| WidgetProtocolTemplates | assets 模板加载 + surfaceId 替换 |
| WidgetProtocolCache | SharedPreferences 协议缓存 |
| WidgetProtocolValidator | A2UI 协议校验 |
| WidgetFallbackBuilder | 降级模板 + 版本转换 |
| WidgetBitmapCache | LruCache Bitmap 缓存 |
| WidgetSurfacePool | Surface 复用池 |
| WidgetTemplatePreloader | 模板预加载 |
| WidgetIntentMatcher | 意图匹配（同义词 + 模糊） |
| WidgetTemplateRecommender | 置信度评分推荐 |
| WidgetNLUParser | NLU 实体提取 |
| WidgetLLMClient | LLM 调用（多模型 Failover） |
| WidgetLLMConfig | LLM 配置 |
| WidgetPromptBuilder | Prompt 构造（few-shot） |
| WidgetPartialParser | 增量 JSON 解析 |
| WidgetConversationMemory | 多轮会话记忆 |
| WidgetInputActivity | 输入统一入口 |
| WidgetInputLaunchService | 输入启动服务 |
| WidgetVoiceHelper | 语音辅助 |
| WidgetVoskManager | Vosk 离线语音 |
| VoskModelLoader | Vosk 模型加载 |
| PdfTextExtractor | PDF 文本提取 |
| MeetingJoinActivity | 一键入会 |
| WidgetPollStats | 投票统计 |
| WidgetHistoryDao/Database/Entity/Repository | 历史记录 Room 持久化 |

---

## 四、测试覆盖率

### 4.1 测试脚本

| 脚本 | 覆盖范围 |
|------|----------|
| `scripts/validate_templates.py` | 10 模板完整性校验（JSON/三段结构/id唯一/绑定/引用） |
| `scripts/test_widget_interactions.sh` | 10 模板 autoWidgetPreview + 天气/agenda/todo 切换 |
| `scripts/widget_adb_e2e.sh` | Widget 刷新/切换广播 + 截图 |
| `scripts/measure_performance.py` | 渲染时间 + 缓存命中率 + 预渲染效果 |
| `tests/run-all-separated.sh` | 分离式 SDK 测试 |
| `tests/run-all-single-instrument.sh` | 单 instrumentation 测试 |

### 4.2 模板校验结果

```
$ python scripts/validate_templates.py
总计: 10 模板 | 10 通过 | 0 错误 | 0 警告
```

校验项：
- ✅ JSON 格式有效
- ✅ createSurface / updateComponents / updateDataModel 三段齐全
- ✅ 组件 id 全局唯一
- ✅ 数据绑定 path 引用字段存在
- ✅ 无悬空子组件引用

### 4.3 性能基线

详见 `docs/PERFORMANCE-BASELINE.md`。目标：

| 指标 | 目标 |
|------|------|
| 首次渲染平均时间 | ≤ 500 ms |
| 缓存命中后渲染时间 | ≤ 50 ms |
| prerenderAll 总耗时 | ≤ 3000 ms |

---

## 五、已知问题和限制

### 5.1 功能限制

1. **端侧推理未集成**：当前 LLM 调用依赖云端 API（多模型 Failover），未集成端侧 TFLite/MNN 模型
2. **单 Widget 实例**：当前仅支持单个 Widget 实例渲染，多实例调度未实现
3. **跨设备同步缺失**：无云同步能力，模板配置仅本地持久化
4. **Glance 未迁移**：Phase 4 评估结论保持 RemoteViews 方案，未迁移到 Glance（因 Bitmap 桥接已稳定）
5. **数据绑定静态化**：模板中组件文本仍为静态值，未与 dataModel 字段动态绑定（dataModel 作为数据快照存在）

### 5.2 性能限制

1. **Bitmap 3MB 上限**：LruCache 3MB 预算，大尺寸模板可能被淘汰
2. **RemoteViews 1MB 限制**：跨进程 Bitmap 传输有 1MB 限制，当前 240-324KB 未触发
3. **Widget 更新频率**：系统 30min 限制，代码主动调用 `updateAppWidget()` 无限制

### 5.3 测试限制

1. **性能基线为空**：当前无连接设备，`PERFORMANCE-BASELINE.md` 数据为 N/A，需真机测量
2. **交互测试需真机**：`test_widget_interactions.sh` 依赖 adb + 真机
3. **UI 自动化测试缺失**：无 Espresso/UI Automator 自动化测试

### 5.4 架构债务

1. **geometric-repack 警告**：git 仓库存在 `refs/heads/settings-panel` 失效引用，不影响提交但需清理
2. **gradle 输出残留**：`playground/android/gradle_out*.txt` 多个构建日志未清理
3. **glance 目录残留**：`widget/glance/` 目录有 Glance 预研代码，未整合到主流程
