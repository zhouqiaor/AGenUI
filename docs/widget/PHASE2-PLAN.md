# Phase 2 迭代计划 — LLM 集成 + 输入交互

> 基于 Phase 1 已验证的 Bitmap 渲染管线，打通"用户输入 → LLM 生成 A2UI → 流式渐进渲染 → Widget 动态更新"端到端链路。
>
> 制定日期：2026-08-25 | 状态：初版 | 作者：WorkBuddy（基于三轮业界开源项目调研）

---

## 一、Phase 1 回顾（已完成）

Phase 1 已验证 Bitmap 渲染桥接核心链路：

```
A2UI 协议模板 → SurfaceManager → onCreateSurface 回调 →
measure(300px) + layout → View.draw(Canvas) → Bitmap(300×270, 324KB) →
RemoteViews.setImageViewBitmap → AppWidgetManager.updateAppWidget
```

**已解决风险**：
- R1: SurfaceManager 需 Activity Context → 透明 Activity 中转 ✅
- R2: Bitmap 跨进程 1MB 限制 → 当前 240-324KB，远低于限制 ✅
- R3: 线程同步 → CountDownLatch(5s) + Handler.post(MainLooper) ✅

**关键经验**：
- `windowNoDisplay=true` 禁用于异步 Activity（强制 onResume 前 finish → 崩溃）
- SurfaceManager 必须用 Activity Context（Service Context 不可用）
- Gradle 8.11.1 最低要求（AGenUI SDK 依赖）
- measure 用 `MeasureSpec.UNSPECIFIED` + width=300 EXACTLY → 自适应高度 200-270px

---

## 二、Phase 2 目标与范围

### 2.1 总目标

打通三链路统一输入入口 → LLM 流式生成 → Widget 渐进渲染完整端到端链路。

### 2.2 子阶段拆分

| 子阶段 | 目标 | 工期估 | 依赖 |
|--------|------|--------|------|
| **P2.1** | LLM Client + 文字输入 + 流式渐进渲染 | 5 天 | Phase 1 ✅ |
| **P2.2** | 语音输入链路（Vosk + VAD） | 3 天 | P2.1 ✅ |
| **P2.3** | 文件导入链路（SAF + 解析） | 2 天 | P2.1 ✅ |
| **P2.4** | 统一输入面板 UI（三 Tab + 大屏适配） | 2 天 | P2.1-P2.3 ✅ |
| **P2.5** | 稳定性 + 降级链路 + 历史记录 | 3 天 | P2.1-P2.4 ✅ |

**总工期估**：15 工作日（3 周）

### 2.3 非目标范围

- Jetpack Glance 迁移（Phase 4 评估）
- SurfaceControlViewHost 跨进程 View 嵌入（Phase 4 评估）
- 后台持续监听语音（Android 14+ 系统禁止）
- 多 Widget 实例并发渲染队列优化（Phase 3）
- LLM 评估后台 / A/B 测试平台（Phase 3）

---

## 三、技术选型（基于三轮业界调研）

### 3.1 LLM 调用 + SSE 流式

| 依赖 | 版本 | 用途 | 体积 |
|------|------|------|------|
| `com.squareup.okhttp3:okhttp` | 4.12.0 | HTTP 客户端 + SSE 流式 | ~4MB（含 Kotlin） |
| `com.squareup.okhttp3:okhttp-sse` | 4.12.0 | SSE EventSource 封装 | ~200KB |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.9.0 | 协程异步 | ~2MB |

**LLM 端点**（复用 POC 已验证）：
- 主模型：阿里百炼 `qwen3.7-plus`（OpenAI 兼容格式，支持 `stream:true`）
- 备模型：火山方舟 `doubao-1.5-pro`（豆包 OpenAI 兼容）
- 三级 Failover：主 → 备 → Mock 模板

**关键选型决策**：
- **不引入第三方 LLM SDK**，直接用 OkHttp + OpenAI 兼容格式调用（最轻量、最通用）
- **不引入独立增量 JSON 解析器**，AGenUI `receiveTextChunk` 已内置 A2UI 协议增量解析
- **国内 LLM 全部支持 OpenAI 兼容格式 + stream:true**，无需厂商专属 SDK

### 3.2 语音输入

| 依赖 | 版本 | 用途 | 体积 |
|------|------|------|------|
| `com.alphacephei:vosk-android` | 0.3.70 | 离线中文 STT | ~5MB lib + 42MB 模型 |
| `com.github.gkonovalov.android-vad:silero` | 2.0.10 | 语音活动检测（精确） | ~2MB + ONNX |
| `com.github.gkonovalov.android-vad:webrtc` | 2.0.10 | VAD 轻量过滤（双级联用） | ~158KB |

**VAD + STT 协同模式**：
```
AudioRecord → WebRTC VAD(快速过滤) → Silero VAD(精确判断)
  ↓ 检测到语音
  → Vosk.acceptWaveForm() 启动识别
  ↓ 检测到静音 300ms（Silero silenceDurationMs=300）
  → Vosk final result → 提交 LLM
```

**关键选型决策**：
- Vosk（非 Whisper）：流式 partial、首字延迟 <300ms、Maven 一行集成
- Silero VAD（非单独 WebRTC）：准确率高、2MB 模型、API 21+
- WebRTC VAD 双级联用：过滤明显噪音，减少 Silero 误触发
- 模型动态下载策略：首包不打包模型，首次使用时按需下载（避免 APK 膨胀 42MB）

### 3.3 文件导入 + 文档解析

| 依赖 | 版本 | 用途 | 体积 |
|------|------|------|------|
| Android SAF（系统 API） | — | 文件选择，零依赖 | 0 |
| `com.tom-roush:pdfbox-android` | 2.0.27.0 | PDF 文本提取 | ~5MB |
| `com.github.SUPERCILEX.poi-android:poi` | 3.17 | Word/Excel/PPT 全格式 | ~12.9MB |

**关键选型决策**：
- **不使用 DocumentReader（Asutosh11）**：2023-01 停更，依赖旧版 PdfBox 1.8.10.1
- **PdfBox-Android 2.0.27.0**：活跃维护、全功能、文本提取稳定
- **Apache POI 3.17**：仅在需要 Word/Excel 时按需引入（避免 APK 膨胀 13MB）
- **大文件策略**：PDF 按页提取 → 每页文本 → 超过 4000 字分块提交 LLM

### 3.4 统一输入面板 UI

| 依赖 | 版本 | 用途 | 体积 |
|------|------|------|------|
| `com.lxj:xpopup` | 2.10.0 | 弹窗/抽屉/BottomSheet | ~500KB |
| `com.github.Dimezis:BlurView` | 3.2.0 | 玻璃拟态背景（API 31+ RenderEffect） | ~200KB |
| `com.valentinilk.shimmer:compose-shimmer` | 1.3.3 | 骨架屏占位（LLM 等待态） | ~100KB |

**关键选型决策**：
- XPopup：竖屏底部 BottomSheet、横屏右侧 Drawer（同一框架双模式）
- BlurView：API 31+ 用 RenderEffect 硬件加速，<31 降级 RenderScript
- GetStream `stream-chat-android-ai` 组件作 UI 设计参考，**不直接依赖**（绑定 Stream Chat 后端）

### 3.5 稳定性 + 历史记录

| 依赖 | 版本 | 用途 |
|------|------|------|
| `androidx.work:work-runtime-ktx` | 2.10.0 | WorkManager（持久化任务） |
| `androidx.room:room-runtime` | 2.6.1 | 历史记录数据库 |
| `androidx.room:room-ktx` | 2.6.1 | Flow 响应式查询 |
| `com.squareup.moshi` | 1.15.1 | JSON 序列化（替代 Gson，更轻量） |

**关键选型决策**：
- WorkManager（非 JobIntentService）：LLM 生成可能 10-30s，需 `setForeground` 提升优先级避免系统杀死
- Room：存储 LLM 生成历史（prompt、A2UI JSON、预览图、时间戳）
- Moshi：比 Gson 更省内存、Kotlin 友好、编译期生成代码

---

## 四、模块设计

### 4.1 模块清单

| 模块 | 职责 | 子阶段 |
|------|------|--------|
| `WidgetLLMClient` | OkHttp SSE 流式调用 LLM，多模型 Failover | P2.1 |
| `WidgetPromptBuilder` | 构建 System Prompt（含组件 Catalog + few-shot） | P2.1 |
| `WidgetProtocolValidator` | 三级校验：JSON 语法 → 协议结构 → 组件白名单 | P2.1 |
| `WidgetStreamRenderer` | SSE → receiveTextChunk 增量构建 → 周期性 measure+draw → Bitmap | P2.1 |
| `WidgetInputActivity` | 透明 Activity，三 Tab 统一入口 | P2.4 |
| `WidgetVoiceManager` | Vosk + Silero VAD 语音识别 | P2.2 |
| `WidgetFileImporter` | SAF 选择 + PdfBox/POI 解析 | P2.3 |
| `WidgetHistoryRepository` | Room 数据库，生成历史 + 预览图 | P2.5 |
| `WidgetDegradationChain` | 三级降级：JSON 修复 → 关键词模板 → 默认模板 | P2.5 |

### 4.2 数据流（端到端链路）

```
┌────────────────────────────────────────────────────────────┐
│  桌面 Widget (RemoteViews)                                  │
│  ┌─────────┬─────────────────────┬────────────────────┐    │
│  │ 标题栏   │  ImageView(Bitmap)   │ 3 圆形按钮(刷新/模板/AI)│   │
│  └─────────┴─────────────────────┴────────────────────┘    │
└──────────────────────┬──────────────────────────────────────┘
                       │ PendingIntent (ACTION_AI_INPUT)
                       ▼
┌────────────────────────────────────────────────────────────┐
│  WidgetInputActivity (透明 Activity)                         │
│  ┌─────────────┬─────────────────┬────────────────────┐    │
│  │ 键盘 Tab     │  语音 Tab        │  文件 Tab           │    │
│  │ EditText    │  VAD + Vosk      │  SAF + PdfBox/POI  │    │
│  │ +快捷 chips │  +波形动画        │  +解析预览         │    │
│  └─────────────┴─────────────────┴────────────────────┘    │
└──────────────────────┬──────────────────────────────────────┘
                       │ prompt (text)
                       ▼
┌────────────────────────────────────────────────────────────┐
│  WidgetStreamRenderer (Foreground Service + Worker)         │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  1. WidgetPromptBuilder.build(text)                 │   │
│  │     → System Prompt (Catalog + few-shot + 用户输入)    │   │
│  │  2. WidgetLLMClient.streamChat(prompt)               │   │
│  │     → SSE 流式 chunks                                 │   │
│  │  3. 增量 JSON 提取（partial parser）                   │   │
│  │  4. surfaceManager.receiveTextChunk(jsonChunk)        │   │
│  │     → AGenUI 引擎增量构建组件树                         │   │
│  │  5. 每 500ms-1s measure + draw → Bitmap              │   │
│  │  6. AppWidgetManager.updateAppWidget(bitmap)          │   │
│  │     → 桌面 Widget 渐进式刷新                            │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  降级链路：                                                  │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  LLM 失败 / JSON 不合法                               │   │
│  │   → WidgetProtocolValidator.tryRepair(json)          │   │
│  │   → 关键词匹配模板（weather/agenda/todo/...）          │   │
│  │   → 默认模板（NoteCard 占位）                           │   │
│  │  → Widget 显示降级内容 + 错误提示                       │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  持久化：                                                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  WidgetHistoryRepository.insert(                     │   │
│  │    prompt, a2uiJson, previewBitmap, timestamp       │   │
│  │  )                                                   │   │
│  └──────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

### 4.3 关键时序

**文字输入 → 首组件渲染**：
```
T+0ms     用户点击 AI 按钮 → 启动 WidgetInputActivity
T+50ms    Activity onCreate → 显示输入面板（XPopup BottomSheet）
T+500ms   用户输入文字 + 点击发送
T+550ms   启动 WidgetStreamRenderer Service
T+600ms   WidgetPromptBuilder 构建完成（System Prompt ~2K tokens）
T+650ms   WidgetLLMClient 发起 SSE 请求
T+1150ms  首个 SSE chunk 到达（500ms 网络延迟）
T+1200ms  增量 JSON parser 提取首个完整组件
T+1250ms  surfaceManager.receiveTextChunk → 组件树更新
T+1300ms  measure + draw → Bitmap → updateAppWidget
          ↑ 首组件渲染完成（~800ms 首延迟，满足 <1s 目标）
T+2500ms  第二个组件渲染（间隔 ~1.2s）
T+4000ms  生成完成 → 最终 Bitmap 推送
T+4100ms  存入历史记录 → Activity finish
```

---

## 五、子阶段详细设计

### 5.1 P2.1 — LLM Client + 文字输入 + 流式渐进渲染（5 天）

#### 5.1.1 WidgetLLMClient

```kotlin
class WidgetLLMClient(
    private val client: OkHttpClient,
    private val config: LLMConfig
) {
    suspend fun streamChat(
        prompt: String,
        userText: String,
        onChunk: (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val messages = buildMessages(prompt, userText)
        val body = JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("stream", true)
            put("temperature", 0.2)  // 偏低，保证 JSON 输出稳定
        }

        val request = Request.Builder()
            .url(config.baseUrl)
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody(JSON))
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("HTTP ${response.code}"))
            }

            val source = response.body!!.source()
            val fullBuf = StringBuilder()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                try {
                    val json = JSONObject(data)
                    val delta = json.optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content", "")
                    if (delta.isNotEmpty()) {
                        fullBuf.append(delta)
                        onChunk(delta)  // 推送到 WidgetStreamRenderer
                    }
                } catch (e: Exception) { /* 忽略解析失败 */ }
            }
            Result.success(fullBuf.toString())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

#### 5.1.2 WidgetStreamRenderer（核心）

```kotlin
class WidgetStreamRenderer : CoroutineWorker(
    appContext, params
) {
    override suspend fun doWork(): Result {
        setForeground(buildForegroundInfo("AI 生成中..."))

        val prompt = getInputData().getString("prompt") ?: return Result.failure()
        val widgetId = getInputData().getInt("widgetId", INVALID_ID)

        // 1. 启动透明 Activity（持有 SurfaceManager）
        val renderActivityIntent = Intent(context, WidgetRenderActivity::class.java).apply {
            putExtra("mode", "stream")
            putExtra("widgetId", widgetId)
            addFlags(FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(renderActivityIntent)

        // 2. 等待 Activity 准备好 SurfaceManager
        val surfaceReady = waitForSurfaceReady(widgetId, timeoutMs = 3000)

        // 3. 启动 LLM 流式调用
        val llmClient = WidgetLLMClient(okHttpClient, loadLLMConfig())
        val renderer = WidgetBitmapRenderer(surfaceReady.surfaceManager, widgetId)

        val result = llmClient.streamChat(
            prompt = WidgetPromptBuilder.build(prompt),
            userText = prompt,
            onChunk = { delta ->
                // 喂给 AGenUI 引擎
                surfaceReady.surfaceManager.receiveTextChunk(delta)
                // 节流刷新（500ms 最小间隔）
                renderer.scheduleRefresh()
            }
        )

        // 4. 最终渲染 + 推送
        renderer.finalRefresh()
        return if (result.isSuccess) Result.success() else Result.failure()
    }
}
```

#### 5.1.3 WidgetBitmapRenderer（节流刷新）

```kotlin
class WidgetBitmapRenderer(
    private val surfaceManager: SurfaceManager,
    private val widgetId: Int
) {
    private var lastRefreshMs = 0L
    private val refreshHandler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { doRefresh() }

    fun scheduleRefresh() {
        val now = System.currentTimeMillis()
        val delay = maxOf(0, 500 - (now - lastRefreshMs))
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.postDelayed(refreshRunnable, delay)
    }

    fun finalRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable)
        refreshHandler.post { doRefresh() }
    }

    private fun doRefresh() {
        lastRefreshMs = System.currentTimeMillis()
        val surface = surfaceManager.getSurface() ?: return
        val container = surface.container ?: return

        // measure + layout + draw（复用 Phase 1 逻辑）
        container.measure(
            makeMeasureSpec(300, EXACTLY),
            makeMeasureSpec(0, UNSPECIFIED)
        )
        val w = container.measuredWidth
        val h = container.measuredHeight.coerceAtLeast(200)
        container.layout(0, 0, w, h)

        val bitmap = Bitmap.createBitmap(w, h, ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            container.draw(this)
        }

        // 推送到 Widget
        pushToWidget(bitmap)
    }

    private fun pushToWidget(bitmap: Bitmap) {
        val awm = AppWidgetManager.getInstance(context)
        val views = RemoteViews(context.packageName, R.layout.a2ui_widget_content)
        views.setImageViewBitmap(R.id.widgetImageView, bitmap)
        awm.updateAppWidget(widgetId, views)
    }
}
```

#### 5.1.4 验收标准

- [ ] 文字输入 → LLM 首次响应 <3s（网络正常）
- [ ] 首组件渲染 <1s（从用户点击发送到 Widget 出现第一个组件）
- [ ] 流式渐进渲染：每 500ms-1s 刷新一次，肉眼可见渐进式更新
- [ ] LLM 生成合法 A2UI JSON 概率 >80%（20 次测试统计）
- [ ] LLM 失败/超时 → 自动降级到关键词匹配模板
- [ ] 生成完成 → 存入 Room 数据库历史记录
- [ ] 透明 Activity 生命周期正确（finish 后 SurfaceManager.destroy）

---

### 5.2 P2.2 — 语音输入链路（3 天）

#### 5.2.1 模块设计

```
WidgetVoiceManager
├── startListening()
│   ├── 创建 AudioRecord（16kHz, mono, 16-bit）
│   ├── 启动 WebRTC VAD（快速过滤）
│   ├── 启动 Silero VAD（精确判断）
│   └── 启动 VoskRecognizer（acceptWaveForm）
├── onSpeechDetected() → Vosk.start()
├── onSilence(ms=300) → Vosk.stop() → finalResult
├── onPartialResult(text) → 更新 UI（实时预览）
└── onFinalResult(text) → 提交 LLM → finish Activity
```

#### 5.2.2 关键代码模式

```kotlin
class WidgetVoiceManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var voskRecognizer: SpeechRecognizer? = null
    private var webRtcVad: Vad? = null
    private var sileroVad: Vad? = null

    fun startListening(onPartial: (String) -> Unit, onFinal: (String) -> Unit) {
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate, MONO, PCM_16BIT
        )

        audioRecord = AudioRecord(
            MEDIA_DEFAULT, sampleRate, MONO, PCM_16BIT, bufferSize
        ).also { it.startRecording() }

        // Vosk
        val model = Model(loadVoskModelPath())
        voskRecognizer = SpeechRecognizer(model).apply {
            start()
            addListener(object : RecognitionListener {
                override fun onPartialResult(text: String) = onPartial(text)
                override fun onFinalResult(text: String) = onFinal(text)
                override fun onError(e: Exception) {}
            })
        }

        // 双级 VAD
        webRtcVad = Vad.builder()
            .setModelType(VadModelType.WEB_RTC)
            .setSilenceDurationMs(300)
            .build()
        sileroVad = Vad.builder()
            .setModelType(VadModelType.SILERO)
            .setSilenceDurationMs(300)
            .build()

        // 读取音频流
        coroutineScope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize)
            while (isActive) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (read <= 0) continue

                // 双级 VAD 判断
                val isSpeech = sileroVad.isSpeech(buffer, sampleRate)
                    .also { /* WebRTC 先过滤，再 Silero 精判 */ }

                if (isSpeech) {
                    voskRecognizer?.acceptWaveForm(buffer, read)
                } else if (sileroVad.isInSpeech()) {
                    // 静音 300ms → 触发 final
                    voskRecognizer?.stop()
                }
            }
        }
    }

    fun stop() {
        audioRecord?.stop()
        audioRecord?.release()
        voskRecognizer?.stop()
        voskRecognizer?.release()
    }
}
```

#### 5.2.3 Vosk 模型动态下载

```kotlin
object VoskModelLoader {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.15.zip"
    private const val MODEL_DIR = "vosk-model-small-cn-0.15"

    suspend fun ensureModel(context: Context): File = withContext(Dispatchers.IO) {
        val target = File(context.filesDir, MODEL_DIR)
        if (target.exists()) return@withContext target

        // 首次使用时下载
        val zipFile = File(context.cacheDir, "vosk-model.zip")
        downloadFile(MODEL_URL, zipFile)
        unzip(zipFile, target)
        target
    }
}
```

#### 5.2.4 验收标准

- [ ] 点击语音按钮 → 权限申请 → 开始录音
- [ ] VAD 自动检测说话端点（无需手动点击开始/停止）
- [ ] Vosk 流式 partial：首字延迟 <500ms
- [ ] 静音 300ms → 自动触发 final result
- [ ] 识别结果实时显示在 UI（输入框预览）
- [ ] 识别完成 → 自动提交 LLM → 关闭 Activity
- [ ] Vosk 模型首次使用时动态下载（不打包到 APK）

---

### 5.3 P2.3 — 文件导入链路（2 天）

#### 5.3.1 模块设计

```
WidgetFileImporter
├── launchPicker()
│   └── Intent.ACTION_OPEN_DOCUMENT → SAF 系统文件选择
├── onFilePicked(uri)
│   ├── takePersistableUriPermission(uri)
│   ├── detectMimeType(uri)
│   └── parseByMime(uri)
│       ├── application/pdf → PdfBox-Android.extractText()
│       ├── application/vnd.openxmlformats... → POI.extractText()
│       ├── text/plain → 直接读取
│       └── 其他 → Toast "不支持的格式"
├── onTextExtracted(text)
│   ├── 超过 4000 字 → 分块（按段落）
│   ├── 显示解析预览（前 500 字）
│   └── 用户确认 → 提交 LLM
└── cleanup()
```

#### 5.3.2 PdfBox-Android 集成

```kotlin
object PdfTextExtractor {
    fun extractText(context: Context, uri: Uri): String {
        // 必须先初始化（Application.onCreate 或首次使用）
        if (!PDFBoxResourceLoader.isInit()) {
            PDFBoxResourceLoader.init(context)
        }

        context.contentResolver.openInputStream(uri).use { stream ->
            val document = PDDocument.load(stream)
            try {
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                    // 防止超大 PDF 拖慢，只取前 50 页
                    startPage = 1
                    endPage = minOf(document.numberOfPages, 50)
                }
                return stripper.getText(document)
            } finally {
                document.close()
            }
        }
    }
}
```

#### 5.3.3 验收标准

- [ ] 点击文件按钮 → 启动 SAF 系统文件选择器
- [ ] 支持 PDF / DOCX / TXT 三种格式（POI 按需引入）
- [ ] PDF 文本提取：10 页文档 <3s
- [ ] 文本超 4000 字 → 分块提交（按段落分割）
- [ ] 解析预览显示前 500 字 + "提交给 AI" 按钮
- [ ] 不支持的格式 → Toast 提示

---

### 5.4 P2.4 — 统一输入面板 UI（2 天）

#### 5.4.1 布局策略

| 设备形态 | 布局 | 触发 |
|----------|------|------|
| 竖屏手机 | XPopup BottomSheet | 底部弹出 |
| 横屏大屏 | DrawerLayout 右侧抽屉 | 右侧滑入 |

```kotlin
class WidgetInputActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_input)

        val orientation = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 大屏横屏：DrawerLayout 右侧抽屉
            showDrawerInputPanel()
        } else {
            // 竖屏：XPopup BottomSheet
            showBottomSheetInputPanel()
        }
    }
}
```

#### 5.4.2 三 Tab 切换

```
输入面板
├── 顶部：3 Tab 切换（键盘/语音/文件）
├── 内容区（随 Tab 切换）
│   ├── 键盘 Tab
│   │   ├── EditText（输入框）
│   │   ├── 快捷建议 chips（天气/待办/投票/议程）
│   │   └── 发送按钮（蓝色主色 #007DFF）
│   ├── 语音 Tab
│   │   ├── 深色波形区（#1C1C1E）
│   │   ├── Vosk 实时识别预览
│   │   ├── 96dp 蓝色麦克风按钮
│   │   └── VAD 状态指示器
│   └── 文件 Tab
│       ├── "选择文件" 按钮
│       ├── 已选文件卡片（文件名/大小/类型）
│       ├── DocumentReader 解析预览（前 500 字）
│       ├── 补充指令输入框
│       └── 发送按钮
└── 底部：投喂感应区（小艺模式，拖拽文件）
```

#### 5.4.3 验收标准

- [ ] 竖屏：底部弹窗，三 Tab 可切换
- [ ] 横屏：右侧抽屉，三 Tab 可切换
- [ ] Tab 切换时已输入内容保留（不丢失）
- [ ] 发送后 Activity 立即 finish（用户感知不到延迟）
- [ ] 鸿蒙风格：圆角 16vp、品牌色 #007DFF、阴影柔和

---

### 5.5 P2.5 — 稳定性 + 降级 + 历史记录（3 天）

#### 5.5.1 三级降级链路

```kotlin
class WidgetDegradationChain {
    suspend fun generateWithDegradation(
        prompt: String,
        widgetId: Int
    ): Result<String> {
        // 一级：LLM 直接生成
        val llmResult = llmClient.streamChat(prompt)
        if (llmResult.isSuccess) {
            val json = llmResult.getOrThrow()
            val validation = WidgetProtocolValidator.validate(json)
            if (validation.valid) return Result.success(json)

            // 二级：尝试修复 JSON
            val repaired = WidgetProtocolValidator.tryRepair(json)
            if (repaired != null) return Result.success(repaired)
        }

        // 三级：关键词匹配模板
        val keywordTemplate = matchKeywordTemplate(prompt)
        if (keywordTemplate != null) {
            return Result.success(keywordTemplate)
        }

        // 兜底：默认 NoteCard 模板
        return Result.success(WidgetProtocolTemplates.DEFAULT_FALLBACK)
    }

    private fun matchKeywordTemplate(text: String): String? {
        val keywords = mapOf(
            "天气" to "weather",
            "议程" to "agenda",
            "待办" to "todo",
            "投票" to "poll",
            "会议" to "meeting"
        )
        return keywords.entries.find { text.contains(it.key) }?.value
    }
}
```

#### 5.5.2 Room 历史记录

```kotlin
@Entity(tableName = "widget_history")
data class WidgetHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val a2uiJson: String,
    val previewBitmapPath: String?,  // 文件路径，不存 Blob（避免 DB 膨胀）
    val widgetId: Int,
    val timestamp: Long,
    val success: Boolean,
    val latency: Long
)

@Dao
interface WidgetHistoryDao {
    @Insert
    suspend fun insert(entity: WidgetHistoryEntity): Long

    @Query("SELECT * FROM widget_history ORDER BY timestamp DESC LIMIT 50")
    fun observeRecent(): Flow<List<WidgetHistoryEntity>>

    @Query("SELECT * FROM widget_history WHERE prompt LIKE :keyword ORDER BY timestamp DESC")
    suspend fun search(keyword: String): List<WidgetHistoryEntity>
}
```

#### 5.5.3 验收标准

- [ ] LLM 网络失败 → 降级到关键词匹配模板（<1s）
- [ ] LLM 返回 JSON 不合法 → 尝试修复（去 trailing comma、补全括号）
- [ ] 所有 LLM 请求记录到 Room 数据库（prompt + JSON + 时间戳 + 延迟）
- [ ] 历史记录可在设置页查看（最近 50 条）
- [ ] 断网情况下 Widget 显示上次成功渲染结果（缓存）

---

## 六、关键风险与缓解

| 风险 | 等级 | 缓解方案 | 验证时机 |
|------|------|----------|---------|
| R4: LLM 输出 JSON 不合法 | 中 | 三级降级 + JSON 修复 + few-shot 优化 | P2.1 |
| R5: 流式中断（网络断开） | 中 | 中断时基于已收到的部分 JSON 渲染 + Toast 提示 | P2.1 |
| R6: Vosk 模型 42MB 体积 | 中 | 动态下载策略，首次使用提示下载 | P2.2 |
| R7: Android 14+ 后台录音限制 | 高 | 必须用透明 Activity 前台录音 | P2.2 |
| R8: PdfBox 大文件 OOM | 中 | 流式加载 + 逐页处理 + 50 页上限 | P2.3 |
| R9: AGenUI C++ core 线程安全 | 高 | 所有 SurfaceManager 调用主线程 + 单实例 | P2.1 |
| R10: Binder 1MB 限制（大 Widget） | 中 | >800KB 自动 JPEG 压缩（复用 Phase 1 逻辑） | P2.5 |
| R11: WorkManager 任务被杀 | 中 | setForeground + 用户可见通知 | P2.1 |

---

## 七、依赖清单

### 新增依赖（Phase 2）

```gradle
// LLM + 网络
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
implementation 'com.squareup.okhttp3:okhttp-sse:4.12.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0'

// 语音
implementation 'com.alphacephei:vosk-android:0.3.70'
implementation 'com.github.gkonovalov.android-vad:silero:2.0.10'
implementation 'com.github.gkonovalov.android-vad:webrtc:2.0.10'

// 文件解析
implementation 'com.tom-roush:pdfbox-android:2.0.27.0'
// Apache POI 按需引入（仅在支持 Word/Excel 时）
// implementation 'com.github.SUPERCILEX.poi-android:poi:3.17'

// UI
implementation 'com.lxj:xpopup:2.10.0'
implementation 'com.github.Dimezis:BlurView:3.2.0'
implementation 'com.valentinilk.shimmer:compose-shimmer:1.3.3'

// 持久化
implementation 'androidx.work:work-runtime-ktx:2.10.0'
implementation 'androidx.room:room-runtime:2.6.1'
implementation 'androidx.room:room-ktx:2.6.1'
ksp 'androidx.room:room-compiler:2.6.1'
implementation 'com.squareup.moshi:moshi:1.15.1'
```

### 复用已有依赖（Phase 1）

- AGenUI SDK（源码依赖 `../../platforms/android`）
- OkHttp（已在 Playground 中）
- AndroidX Core / AppCompat / Material Components

### 体积估算

| 组件 | 体积 |
|------|------|
| OkHttp + SSE | ~4MB（复用已有） |
| Vosk lib + 模型 | ~47MB（模型动态下载，不计入 APK） |
| VAD Silero + ONNX | ~6MB |
| PdfBox-Android | ~5MB |
| XPopup + BlurView + shimmer | ~800KB |
| Room + WorkManager + Moshi | ~3MB |
| **APK 净增** | ~15MB（不含 Vosk 模型） |

---

## 八、验收里程碑

| 里程碑 | 内容 | 验收方式 | 预计完成 |
|--------|------|----------|---------|
| M2.1 | LLM + 文字输入 + 流式渲染 | 真机文字输入 → Widget 渐进式更新 | P2.1 完成 |
| M2.2 | 语音输入 | 真机语音输入 → 识别 → 提交 LLM | P2.2 完成 |
| M2.3 | 文件导入 | 真机选择 PDF → 解析 → 提交 LLM | P2.3 完成 |
| M2.4 | 统一输入面板 UI | 竖屏 BottomSheet + 横屏 Drawer，三 Tab 切换 | P2.4 完成 |
| M2.5 | 稳定性 + 降级 + 历史记录 | 断网测试 + LLM 失败降级 + 历史记录查看 | P2.5 完成 |

---

## 九、开源项目参考清单

### LLM 调用 + SSE
- **OkHttp**（Square，45k★，Apache-2.0）：HTTP/SSE 标准库
- **OpenAI Java SDK**（openai-java，2k★，MIT）：官方 SDK，参考其 SSE 处理
- **LangChain4j**（2k★，Apache-2.0）：JVM LLM 框架，参考其多模型 Failover

### 语音输入
- **Vosk Android**（alphacephei，15k★，Apache-2.0）：离线 STT 首选
- **whisper.cpp**（ggml-org，极高★，MIT）：高准确率备选
- **android-vad**（gkonovalov，中★，MIT）：VAD 三模型合一
- **FUTO Voice Input**（futo-org）：whisper.cpp + ACFT 微调，短指令优化参考

### 文件导入
- **Android SAF**（系统 API）：零依赖文件选择
- **PdfBox-Android**（TomRoush，高★，Apache-2.0）：PDF 专项
- **Apache POI Android**（SUPERCILEX fork，Apache-2.0）：全 Office 格式

### UI 组件
- **XPopup**（junixapp，8k★，Apache-2.0）：弹窗/抽屉框架
- **BlurView**（Dimezis，6k★，Apache-2.0）：玻璃拟态
- **compose-shimmer**（valentinilk，2k★，Apache-2.0）：骨架屏
- **GetStream stream-chat-android-ai**（GetStream，Apache-2.0）：AI 输入栏设计参考

### 持久化 + 稳定性
- **Room**（AndroidX，官方）：SQLite ORM
- **WorkManager**（AndroidX，官方）：持久化任务
- **Resilience4J**（3k★，Apache-2.0）：重试/熔断（可选，JVM 库兼容）

### Widget 工程实践
- **Google user-interface-samples**（4k★，Apache-2.0）：AppWidget 官方示例
- **android-architecture-components**（Google，官方）：Room + Flow 最佳实践
- **Material Components for Android**（MDC，16k★，Apache-2.0）：M3 Design Token

---

## 十、下一步

完成本计划评审后，按顺序开始：
1. 创建 `feature/widget-phase2-llm` 分支
2. 编写 `.handoff/TASK.md`（基于本计划拆分 P2.1-P2.5 任务书）
3. Maker 按 TASK.md 开发，Checker 按子阶段审核
