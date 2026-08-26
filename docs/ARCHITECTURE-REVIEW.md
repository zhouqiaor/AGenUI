# AGenUI Widget 模块架构检视报告

> 检视范围：`playground/android/app/src/main/java/com/amap/agenuiplayground/widget/`
> 检视时点：20 轮自迭代后（7 → 10 模板，新增 LRU 缓存 / 预加载 / Surface 池 / 预渲染 / 意图匹配 / NLU / 对话记忆）
> 分支：main

---

## 一、架构概览

### 1.1 模块分层与文件清单（31 个 Java 文件）

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Widget Module (31 files)                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  ┌──────────────┐       ┌──────────────────────────────────────────┐    │
│  │ Provider 层   │──────▶│  渲染服务（上帝类）                       │    │
│  │              │       │  AGenUIWidgetRenderService (598 行)      │    │
│  │ A2UIWidget   │       │  ┌─ renderSync (6 步管线)                │    │
│  │ Provider     │       │  ├─ drawSurfaceToBitmap / drawViewTree   │    │
│  │              │       │  ├─ pushBitmapToWidget / pushErrorWidget │    │
│  └──────────────┘       │  ├─ wireButtons (5 类按钮 + 模板栏)       │    │
│         ▲                │  ├─ filterAgendaComponents (业务过滤)     │    │
│         │                │  ├─ prerenderAll (预渲染)                │    │
│         │                │  └─ cleanup (Surface 池回收)              │    │
│         │                └──┬────┬────┬────┬────┬────┬────┬────┬──┘    │
│         │                   │    │    │    │    │    │    │    │       │
│  ┌──────┴──────┐   ┌───────┘    │    │    │    │    │    │    │       │
│  │ Activity 层  │   ▼       ▼   ▼    ▼    ▼    ▼    ▼    ▼    ▼       │
│  │              │ ┌─────┐ ┌─────┐ ┌──────┐ ┌──────┐ ┌─────────┐     │
│  │ InputActivity│ │Bitmap│ │Surface│ │Proto │ │Fallback│ │Protocol│     │
│  │ RenderActivity│ │Cache │ │Pool  │ │Tpl   │ │Builder│ │Cache   │     │
│  │ MeetingJoin  │ │(stat)│ │(stat)│ │(stat)│ │(stat)│ │(stat)  │     │
│  └──────────────┘ └─────┘ └─────┘ └──────┘ └──────┘ └─────────┘     │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐    │
│  │                    AI / NLU 层                                  │    │
│  │  ┌────────────┐  ┌──────────┐  ┌────────────────┐              │    │
│  │  │Intent      │  │NLU Parser│  │Conversation    │              │    │
│  │  │Matcher      │  │(static)  │  │Memory (实例)    │              │    │
│  │  │(static)     │  │          │  │                │              │    │
│  │  └─────┬──────┘  └──────────┘  └───────┬────────┘              │    │
│  │        │                                 │                      │    │
│  │        ▼                                 ▼                      │    │
│  │  ┌──────────────┐              ┌──────────────┐                  │    │
│  │  │Template      │◀─────────────│LLM Client    │                  │    │
│  │  │Recommender   │              │(实例)        │                  │    │
│  │  │(static)      │              └──────┬───────┘                  │    │
│  │  └──────────────┘                     │                          │    │
│  └───────────────────────────────────────┼──────────────────────────┘    │
│                                          │                               │
│                              ┌───────────▼──────────────┐                │
│  ┌──────────────────┐        │  输入与交互层             │                │
│  │  数据层            │        │  - InputActivity         │                │
│  │  - HistoryRepo    │        │  - VoiceHelper / VoskMgr  │                │
│  │  - HistoryDAO/DB   │        │  - PdfTextExtractor       │                │
│  │  - PollStats      │        │  - PromptBuilder          │                │
│  │  (全 static)      │        └───────────────────────────┘                │
│  └──────────────────┘                                                  │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘

外部依赖：
  ┌─────────────────┐   ┌───────────────┐   ┌─────────────────┐
  │  AGenUI 引擎     │   │ Android 系统   │   │  LLM API (SSE)  │
  │  (单例 getInstance)│ │  AppWidgetMgr │   │  qwen / doubao  │
  │  SurfaceManager  │   │  RemoteViews  │   └─────────────────┘
  └─────────────────┘   └───────────────┘
```

### 1.2 核心数据流

```
用户操作                     Widget 系统                     AGenUI 引擎
─────────                    ──────────                    ───────────
onUpdate ──────────────▶ prerenderAll ──▶ renderSync ──▶ SurfaceManager
                           (10 模板)      (Bitmap 缓存命中?)    │
                                              │ 否            │
                                              ▼               │
btnRefresh ──▶ onReceive ─▶ renderWidget ─▶ renderAsync ─▶ renderSync
                                              │               │
btnSwitchTpl ─▶ onReceive ─▶ saveTemplate ─▶ renderWidget     │
                               (SP)            │               │
                                              ▼               ▼
btnAiInput ──▶ onReceive ─▶ InputActivity ─▶ RenderActivity  │
                              (键盘/语音/文件)   (LLM stream)   │
                                                 │           │
                                                 ▼           ▼
                                          drawSurfaceToBitmap
                                                 │
                                                 ▼
                                          WidgetBitmapCache.put
                                                 │
                                                 ▼
                                          pushBitmapToWidget
                                          (RemoteViews → updateAppWidget)
```

---

## 二、问题清单（按严重程度分级）

### P0 — 严重问题（影响稳定性 / 可维护性）

#### P0-1 `AGenUIWidgetRenderService` 是上帝类（598 行，9 个职责）

**文件**：`AGenUIWidgetRenderService.java`

**现状**：该类承担了至少 9 个本应由独立类承担的职责：

| 职责 | 方法 | 行数 |
|------|------|------|
| 渲染线程管理 | `ensureRenderThread` / `sRenderThread` / `sRenderHandler` | 58-83 |
| 渲染管线编排 | `renderSync`（6 步：缓存检查→加载→转换→初始化→流式→绘制→推送） | 88-271 |
| Bitmap 绘制 | `drawSurfaceToBitmap` / `drawViewTree` | 282-365 |
| View 层级调试 | `dumpViewHierarchy` | 370-385 |
| RemoteViews 推送 | `pushBitmapToWidget` / `pushErrorWidget` | 390-436 |
| 按钮接线 | `wireButtons`（5 类按钮 + 模板栏 7 按钮） | 441-500 |
| Agenda 业务过滤 | `filterAgendaComponents`（遍历 JSON 树删除节点） | 514-552 |
| 预渲染 | `prerenderAll` | 567-584 |
| Surface 池管理 | `cleanup` | 558-560 |

**影响**：
- 单个方法 `renderSync` 长 183 行（88-271），包含 6 步流程、2 个 try-catch、3 个 CountDownLatch
- `wireButtons` 方法 60 行，硬编码 5 种 PendingIntent 的构造逻辑
- `filterAgendaComponents` 直接操作 JSON 树结构，与渲染职责无关
- 修改任何一个职责都需要触碰这个类，违反 SRP

#### P0-2 全静态方法架构，无法单元测试

**现状**：31 个类中，除 `WidgetConversationMemory` / `WidgetLLMClient` / `WidgetLLMConfig` / `WidgetInputActivity` 等 Activity/Service 外，其余 20+ 个类全部是 `static` 方法工具类：

| 类 | 实例化方式 | 可 Mock? |
|----|-----------|---------|
| `AGenUIWidgetRenderService` | 全 static | 否 |
| `WidgetBitmapCache` | 全 static | 否 |
| `WidgetSurfacePool` | 全 static | 否 |
| `WidgetTemplatePreloader` | 全 static | 否 |
| `WidgetProtocolCache` | 全 static | 否 |
| `WidgetIntentMatcher` | 全 static | 否 |
| `WidgetNLUParser` | 全 static | 否 |
| `WidgetPollStats` | 全 static | 否 |
| `WidgetFallbackBuilder` | 全 static | 否 |
| `WidgetProtocolTemplates` | 全 static | 否 |
| `WidgetTemplateRecommender` | 全 static | 否 |

**影响**：
- 无法对 `renderSync` 的超时处理、错误恢复逻辑做单元测试
- 无法验证 `WidgetIntentMatcher.match` 在不同输入下的行为（必须通过实际调用）
- 无法对 LLM 失败降级链做集成测试
- `renderSync` 中直接调用 `AGenUI.getInstance().initialize()` 和 `new SurfaceManager()`，无法注入 Mock

#### P0-3 Bitmap use-after-recycle 风险

**文件**：`AGenUIWidgetRenderService.java:399-409` + `WidgetBitmapCache.java:46-54`

**现状**：
```java
// pushBitmapToWidget (399-409)
if (bitmap.getByteCount() > 800_000) {
    // 压缩成 JPEG → decode 成 smaller
    views.setImageViewBitmap(R.id.widgetImageView, smaller);
    // smaller 未被 recycle，也未被缓存 → 每次压缩都泄漏一个 Bitmap
} else {
    views.setImageViewBitmap(R.id.widgetImageView, bitmap);
}
```

```java
// WidgetBitmapCache.entryRemoved (46-54)
if (evicted && oldValue != null && !oldValue.isRecycled()) {
    oldValue.recycle();  // 被驱逐时 recycle
}
```

**风险场景**：
1. 线程 A：`renderSync` 调用 `pushBitmapToWidget(bitmap)` — bitmap 同时被 `WidgetBitmapCache.put` 缓存
2. 线程 B：`prerenderAll` 放入第 11 个模板 bitmap → LRU 驱逐线程 A 的 bitmap → `recycle()`
3. 线程 A：`pushBitmapToWidget` 仍在执行 `bitmap.compress(...)` 或 `views.setImageViewBitmap(bitmap)` → **use-after-recycle 崩溃**

**注**：虽然当前 `renderSync` 和 `prerenderAll` 都在同一个 `sRenderHandler` 线程上串行执行，实际并发风险较低。但一旦未来改为多线程渲染，此问题会立即暴露。

#### P0-4 新增模板需修改 5+ 处，违反开闭原则（OCP）

**现状**：新增一个模板（如 `flashcard`）需要同步修改：

| 修改点 | 文件 | 说明 |
|--------|------|------|
| 1 | `WidgetProtocolTemplates.AVAILABLE_TEMPLATES` | 添加模板名到数组 |
| 2 | `WidgetProtocolTemplates.TEMPLATE_BUTTON_IDS` | 添加布局 R.id（需同时改布局 XML） |
| 3 | `WidgetIntentMatcher.INTENT_KEYWORDS` | 添加意图关键词词典 |
| 4 | `WidgetIntentMatcher.INTENT_FUZZY` | 添加拼音/变体 |
| 5 | `WidgetTemplateRecommender.DEFAULT_ORDER` | 添加到默认推荐列表 |
| 6 | `assets/widget_templates/xxx.json` | 创建模板文件 |
| 7 | 布局 XML `a2ui_widget_content.xml` | 添加按钮（如果模板栏要显示） |

**影响**：20 轮迭代中模板从 7 个增长到 10 个，每新增一个都需要改动 5+ 个文件，极易遗漏（事实上 `DEFAULT_ORDER` 已经遗漏了 4 个模板 — 见 P1-2）。

#### P0-5 紧耦合网 — RenderService 直接依赖 8+ 静态类

**现状**：`AGenUIWidgetRenderService.renderSync` 和 `wireButtons` 直接调用：

```
WidgetBitmapCache.get/put        (缓存)
WidgetSurfacePool.acquire/release (池化)
WidgetProtocolTemplates.load*    (模板)
WidgetFallbackBuilder.convert*   (转换)
WidgetProtocolCache.get*         (持久化)
WidgetPollStats.getTotalVotes    (投票统计)
A2UIWidgetProvider.EXTRA_*       (常量)
AGenUI.getInstance()             (引擎单例)
```

**影响**：
- 任何一个依赖类的签名变化都会导致 RenderService 编译失败
- 无法替换缓存策略、池化策略进行 A/B 测试
- `wireButtons` 中直接 `new RemoteViews` + 5 种 `PendingIntent`，UI 层逻辑硬编码在渲染服务中

---

### P1 — 较高问题（影响扩展性 / 可靠性）

#### P1-1 `TEMPLATE_BUTTON_IDS` 与 `AVAILABLE_TEMPLATES` 长度不匹配

**文件**：`WidgetProtocolTemplates.java:18-39`

**现状**：
```java
public static final String[] AVAILABLE_TEMPLATES = {"weather", "agenda", "todo",
    "calendar", "poll", "note", "notecard", "meeting", "classroom", "flashcard"};  // 10 个

public static final int[] TEMPLATE_BUTTON_IDS = {
    R.id.btnTemplateWeather, R.id.btnTemplateAgenda, R.id.btnTemplateTodo,
    R.id.btnTemplateMeeting, R.id.btnTemplatePoll, R.id.btnTemplateClassroom,
    R.id.btnTemplateFlashcard  // 7 个
};
```

**影响**：
- `wireButtons` 中 `for (int i = 0; i < buttonIds.length && i < templates.length; i++)` 只布线前 7 个
- `note` / `notecard` / `calendar` 无法通过模板栏切换（只能通过 `btnSwitchTemplate` 循环切换）
- 两个数组顺序也不一致（AVAILABLE_TEMPLATES[3]=calendar，TEMPLATE_BUTTON_IDS[3]=Meeting）

#### P1-2 `WidgetTemplateRecommender.DEFAULT_ORDER` 与 `AVAILABLE_TEMPLATES` 不同步

**文件**：`WidgetTemplateRecommender.java:71-73`

**现状**：
```java
private static final List<String> DEFAULT_ORDER = Arrays.asList(
    "weather", "todo", "agenda", "note", "calendar", "poll"  // 6 个，缺少 4 个
);
```

**影响**：`notecard` / `meeting` / `classroom` / `flashcard` 不在默认推荐列表中，意图匹配失败时不会推荐这些模板。

#### P1-3 `pushBitmapToWidget` 中 smaller bitmap 未 recycle

**文件**：`AGenUIWidgetRenderService.java:399-409`

**现状**：当 bitmap > 800KB 时，压缩成 JPEG 再 decode 成 `smaller`，但 `smaller` 推送到 RemoteViews 后未被 recycle，也未被缓存。

**影响**：每次大 bitmap 渲染都泄漏一个 Bitmap 的 native 内存（直到 GC，但 native 内存不受 GC 直接管控）。

#### P1-4 `WidgetBitmapCache` 3MB 预算对 10 个模板不够

**文件**：`WidgetBitmapCache.java:26`

**现状**：
```java
private static final int BYTE_BUDGET = 3 * 1024 * 1024;  // 3MB
// 注释：sized for the 7 built-in templates (each ~300KB)
```

**影响**：
- 10 个模板 × 300KB = 3MB，正好满 — 无余量给 view mode 变体（如 `agenda_today` + `agenda_week`）
- 频繁驱逐 + recycle 导致缓存命中率下降，性能退化
- 注释仍写 "7 built-in templates"，与实际不符

#### P1-5 关键词词典 / 城市名 / 时间关键词硬编码

**文件**：`WidgetIntentMatcher.java:43-90` / `WidgetNLUParser.java:37-56`

**现状**：
- `INTENT_KEYWORDS` / `INTENT_FUZZY` 硬编码在 static 块中
- `CITIES`（36 个城市）/ `TIME_KEYWORDS`（35 个词）硬编码为 `Arrays.asList`

**影响**：
- 新增意图关键词需重新编译
- 无法动态配置（如从服务端下发关键词词典）
- 无法 A/B 测试不同关键词集的匹配效果

#### P1-6 `WidgetIntentMatcher` match / matchWithScore 逻辑重复

**文件**：`WidgetIntentMatcher.java:137-186` vs `203-277`

**现状**：`match()` 和 `matchWithScore()` 都包含相同的「遍历 INTENT_KEYWORDS → 统计命中数 → 模糊匹配」流程，但一个返回 String，一个返回 IntentMatch。

**影响**：维护时需同步修改两处，易遗漏。

#### P1-7 无接口抽象 — SurfaceManager / AGenUI 直接 new / 单例

**现状**：
```java
// renderSync
AGenUI.getInstance().initialize(context.getApplicationContext());  // 单例
SurfaceManager surfaceManager = WidgetSurfacePool.acquire(template);  // 直接 new
if (!reused) {
    surfaceManager = new SurfaceManager(context);  // 直接 new
}
```

**影响**：
- 无法注入 Mock SurfaceManager 测试「surface 创建超时」「root 组件挂载超时」等场景
- 无法替换 AGenUI 引擎实现进行测试

---

### P2 — 一般问题（代码质量 / 潜在隐患）

#### P2-1 `WidgetProtocolCache` 方法重复

**文件**：`WidgetProtocolCache.java`

**现状**：每个方法都独立调用 `context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)`，6 个方法重复 6 次。

**建议**：提取 `prefs(Context)` 私有方法（参考 `WidgetPollStats` 的做法）。

#### P2-2 `WidgetBitmapCache.clear` 中遍历 snapshot + get 可能触发 entryRemoved

**文件**：`WidgetBitmapCache.java:98-116`

**现状**：
```java
for (String key : sCache.snapshot().keySet()) {
    Bitmap b = sCache.get(key);  // get 会触发 LRU 的 hit 逻辑
    if (b != null && !b.isRecycled()) { b.recycle(); }
}
sCache.evictAll();  // evictAll 会再次触发 entryRemoved → recycle 已 recycle 的 bitmap
```

**影响**：`evictAll` 会调用 `entryRemoved`，此时 bitmap 已被 recycle，`recycle()` 再次调用会抛异常（虽被 try-catch 吞掉，但不规范）。

#### P2-3 `WidgetNLUParser.extractLocation` 注释与实现不符

**文件**：`WidgetNLUParser.java:246-257`

**现状**：注释写「优先匹配最长的城市名」，但实际返回 `candidates.get(0)`（列表第一个匹配，非最长）。`CITIES` 列表顺序是硬编码的，非按长度排序。

#### P2-4 多个类中重复 `truncate` 工具方法

**现状**：`WidgetIntentMatcher` / `WidgetNLUParser` / `WidgetConversationMemory` / `WidgetTemplateRecommender` 都有完全相同的 `truncate(String, int)` 方法。

**建议**：提取到公共工具类。

#### P2-5 `WidgetSurfacePool.MAX_POOL_SIZE = 2` 对 10 个模板可能不够

**文件**：`WidgetSurfacePool.java:35`

**现状**：每个模板最多缓存 2 个 SurfaceManager，但 10 个模板 × 2 = 20 个 SurfaceManager 实例可能占用较多内存。

**影响**：模板切换时池命中率低（每次切换到不同模板都需重新创建 SurfaceManager）。

#### P2-6 `prerenderAll` 在 onUpdate 中每次调用但未检查是否已完成

**文件**：`AGenUIWidgetRenderService.java:567-584` / `A2UIWidgetProvider.java:34-36`

**现状**：
```java
// A2UIWidgetProvider.onUpdate
if (appWidgetIds.length > 0) {
    AGenUIWidgetRenderService.prerenderAll(context);  // 每次 onUpdate 都调用
}
```

**影响**：`prerenderAll` 内部 `sRenderHandler.post(...)` 提交任务，但没有检查是否已有预渲染任务在执行或已完成，可能重复提交（虽然 `WidgetBitmapCache.get(key) != null` 会跳过已缓存的，但仍有调度开销）。

#### P2-7 `WidgetTemplatePreloader.preload` 中 `sCache.size()` 在 synchronized 块外

**文件**：`WidgetTemplatePreloader.java:60`

**现状**：
```java
new Thread(() -> {
    for (String name : ...) {
        synchronized (sCache) { sCache.put(name, json); }
    }
    sDone.set(true);
    Log.d(TAG, "Preload complete: " + sCache.size() + " templates");  // 未同步
}, ...).start();
```

**影响**：`sCache.size()` 虽然不会抛 `ConcurrentModificationException`（HashMap 不抛），但在多线程下值可能不准确。低风险。

#### P2-8 `WidgetConversationMemory` 的 `getEntries` / `getHistoryJson` 浅拷贝

**文件**：`WidgetConversationMemory.java:79-81`

**现状**：`getEntries()` 返回 `new ArrayList<>(cache)`，是浅拷贝。`Entry` 对象共享引用。但 `Entry` 字段是 `final`，不可变，所以安全。

---

## 三、重构建议

### 3.1 拆分 `AGenUIWidgetRenderService` 上帝类（P0-1, P0-5）

```
AGenUIWidgetRenderService (拆分后)
├── WidgetRenderOrchestrator     — 渲染管线编排（renderSync 6 步）
├── WidgetBitmapRenderer          — Bitmap 绘制（drawSurfaceToBitmap / drawViewTree）
├── WidgetRemoteViewsBuilder      — RemoteViews 构建与推送（pushBitmapToWidget / pushErrorWidget）
├── WidgetButtonWiring            — 按钮接线（wireButtons）
├── WidgetPrerenderManager        — 预渲染管理（prerenderAll）
├── WidgetRenderThreadManager     — 渲染线程管理（ensureRenderThread）
└── AgendaComponentFilter         — Agenda 过滤（filterAgendaComponents）
```

**收益**：
- 每个类 50-100 行，职责单一
- `WidgetRemoteViewsBuilder` / `WidgetButtonWiring` 可独立测试
- `AgendaComponentFilter` 可独立演进，不影响渲染逻辑

### 3.2 引入接口抽象，支持依赖注入与单元测试（P0-2, P1-7）

```java
// 缓存抽象
public interface WidgetBitmapCacheInterface {
    Bitmap get(String key);
    void put(String key, Bitmap bitmap);
    void clear();
}

// 模板加载抽象
public interface WidgetTemplateLoader {
    String loadTemplate(String template, String surfaceId);
    List<String> getAvailableTemplates();
}

// 引擎抽象
public interface AGenUIEngine {
    void initialize(Context context);
    SurfaceManager createSurfaceManager(Context context);
}

// 渲染编排器 — 通过构造函数注入
public class WidgetRenderOrchestrator {
    private final WidgetBitmapCacheInterface cache;
    private final WidgetTemplateLoader loader;
    private final AGenUIEngine engine;
    // ...
}
```

**收益**：
- 单元测试可注入 Mock 实现，验证渲染管线逻辑
- 可替换不同缓存策略（如 DiskLruCache）、不同模板源（如网络下载）

### 3.3 模板注册表模式 — 统一模板配置（P0-4, P1-1, P1-2, P1-5）

```java
// 单一配置源
public class WidgetTemplateRegistry {
    private static final List<TemplateConfig> TEMPLATES = Arrays.asList(
        new TemplateConfig("weather", R.id.btnTemplateWeather,
            Arrays.asList("天气", "weather", "气温", ...),  // keywords
            Arrays.asList("tianqi", "tian qi"),              // fuzzy
            "weather_default.json"),
        new TemplateConfig("agenda", R.id.btnTemplateAgenda, ...),
        // ...
    );
}

// 每个模板的完整配置
public class TemplateConfig {
    public final String name;
    public final int buttonId;           // 0 if no button
    public final List<String> keywords;
    public final List<String> fuzzyVariants;
    public final String templateFile;
}
```

**收益**：
- 新增模板只需在 `TEMPLATES` 中添加一项 + 创建 JSON 文件
- `AVAILABLE_TEMPLATES` / `TEMPLATE_BUTTON_IDS` / `INTENT_KEYWORDS` / `DEFAULT_ORDER` 全部从 `TEMPLATES` 派生
- 消除数组长度不匹配风险
- 关键词词典可从 JSON / 远程配置加载

### 3.4 Bitmap 生命周期安全（P0-3, P1-3）

```java
// pushBitmapToWidget 修复
private static void pushBitmapToWidget(Context context, int appWidgetId,
                                       Bitmap bitmap, String title, String template) {
    // ...
    Bitmap toPush = bitmap;
    if (bitmap.getByteCount() > 800_000) {
        Bitmap smaller = compressBitmap(bitmap);  // 压缩
        toPush = smaller;
        // smaller 由 RemoteViews 序列化后立即 recycle（RemoteViews 会 copy 到 ashmem）
        // 但需确保序列化完成后再 recycle — 实际上 setImageViewBitmap 是同步序列化的
        // 所以 push 后立即 recycle smaller 是安全的
    }
    views.setImageViewBitmap(R.id.widgetImageView, toPush);
    // ...
    if (toPush != bitmap) toPush.recycle();  // 回收 smaller，原始 bitmap 仍由缓存管理
}

// WidgetBitmapCache 增加「引用计数」或「使用中」标记
// 避免 cache 时正在被 RemoteViews 使用
```

### 3.5 缓存容量自适应（P1-4）

```java
// 根据模板数量动态计算预算
private static final int BYTES_PER_TEMPLATE = 400 * 1024;  // 400KB
private static final int BYTE_BUDGET =
    WidgetTemplateRegistry.getTemplates().size() * BYTES_PER_TEMPLATE * 2;  // 2x 余量
```

### 3.6 意图匹配策略模式（P1-6）

```java
public interface IntentMatchStrategy {
    IntentMatch match(String text);
}

public class KeywordMatchStrategy implements IntentMatchStrategy { ... }
public class FuzzyMatchStrategy implements IntentMatchStrategy { ... }
public class CompositeIntentMatcher implements IntentMatchStrategy {
    private final List<IntentMatchStrategy> strategies;
    // ...
}
```

**收益**：`match()` 和 `matchWithScore()` 共用同一个匹配流程，消除重复。

---

## 四、技术债务清单

| # | 债务项 | 严重程度 | 对应问题 | 建议处理时机 |
|---|--------|---------|---------|-------------|
| D-01 | `AGenUIWidgetRenderService` 上帝类（598 行 / 9 职责） | P0 | P0-1 | 下次迭代拆分 |
| D-02 | 全静态方法架构，零可测试性 | P0 | P0-2 | 引入 DI 框架（Hilt/Dagger）后重构 |
| D-03 | Bitmap use-after-recycle 风险 | P0 | P0-3 | 立即修复（加引用计数） |
| D-04 | 新增模板需改 5+ 文件（违反 OCP） | P0 | P0-4 | 引入 `WidgetTemplateRegistry` |
| D-05 | RenderService 紧耦合 8+ 静态类 | P0 | P0-5 | 随 D-01 拆分一并解决 |
| D-06 | `TEMPLATE_BUTTON_IDS` 与 `AVAILABLE_TEMPLATES` 不匹配 | P1 | P1-1 | 随 D-04 一并解决 |
| D-07 | `DEFAULT_ORDER` 缺 4 个模板 | P1 | P1-2 | 随 D-04 一并解决 |
| D-08 | smaller bitmap 未 recycle | P1 | P1-3 | 立即修复 |
| D-09 | Bitmap 缓存 3MB 对 10 模板不足 | P1 | P1-4 | 动态计算预算 |
| D-10 | 关键词词典 / 城市名硬编码 | P1 | P1-5 | 改为 JSON 配置 |
| D-11 | match / matchWithScore 逻辑重复 | P1 | P1-6 | 策略模式重构 |
| D-12 | 无接口抽象（SurfaceManager / AGenUI） | P1 | P1-7 | 引入接口 + DI |
| D-13 | `WidgetProtocolCache` 方法重复 | P2 | P2-1 | 提取 `prefs()` 方法 |
| D-14 | `WidgetBitmapCache.clear` 重复 recycle | P2 | P2-2 | 先 `evictAll` 再遍历 recycle |
| D-15 | `extractLocation` 注释与实现不符 | P2 | P2-3 | 修复注释或实现 |
| D-16 | 4 处重复 `truncate` 方法 | P2 | P2-4 | 提取工具类 |
| D-17 | SurfacePool 容量 2 对 10 模板不足 | P2 | P2-5 | 按模板数动态调整 |
| D-18 | `prerenderAll` 未做幂等检查 | P2 | P2-6 | 加 `AtomicBoolean sPrerendering` |
| D-19 | `WidgetTemplatePreloader` size 未同步 | P2 | P2-7 | 加 synchronized 块 |
| D-20 | 无单元测试覆盖 | P0 | P0-2 | 随 D-02 重构后补充 |

---

## 五、总结

### 5.1 架构成熟度评估

| 维度 | 评分（1-5） | 说明 |
|------|-----------|------|
| 职责分离 | 2 | RenderService 上帝类，其余类职责尚可 |
| 耦合度 | 2 | 8+ 静态类紧耦合，无接口抽象 |
| 可测试性 | 1 | 全静态架构，几乎无法单元测试 |
| 线程安全 | 3 | 基本正确（volatile + synchronized），但有 use-after-recycle 隐患 |
| 内存管理 | 3 | Bitmap/Surface 基本回收，但有 smaller 泄漏和 recycle 时机风险 |
| 扩展性 | 2 | 新增模板需改 5+ 文件，违反 OCP |

### 5.2 优先行动项

1. **立即修复**（P0-3 / D-03 / D-08）：Bitmap recycle 时机与 smaller 泄漏 — 风险最高，可能导致线上崩溃
2. **短期重构**（P0-4 / D-04）：引入 `WidgetTemplateRegistry` 统一配置 — 投入产出比最高
3. **中期拆分**（P0-1 / D-01）：拆分 `AGenUIWidgetRenderService` — 改善可维护性
4. **长期演进**（P0-2 / D-02）：引入接口抽象 + DI 框架 — 从根本上解决可测试性

### 5.3 积极面

- **缓存分层设计合理**：Bitmap LRU → Surface 池 → 模板预加载，三层缓存各司其职
- **降级链完整**：LLM 多 tier 故障转移 + 意图匹配 + NLU 实体提取 + 对话记忆，AI 链路设计成熟
- `WidgetConversationMemory` 是唯一的实例类，有正确的 synchronized 同步，是架构中的亮点
- `WidgetBitmapCache` 的 DCL 初始化 + entryRemoved recycle 机制方向正确（执行细节需优化）
- 意图匹配的三级策略（精确 → 同义词 → 模糊）设计合理，参考了 Coze/Dify 的成熟方案
