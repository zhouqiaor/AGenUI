# Phase 2 开源项目复用深度分析

> 基于 2025-2026 年 GitHub 高星项目源码级拆解，针对 AGenUI 桌面 Widget Phase 2 开发。
>
> 制定日期：2026-08-25 | 状态：调研完成 | 补齐 PHASE2-PLAN.md 的代码级集成细节

---

## 一、Vosk + Silero VAD Android 集成代码示例

### 1.1 项目信息

| 项目 | GitHub | Stars | License | 技术栈 |
|------|--------|-------|---------|--------|
| vosk-api | alphacep/vosk-api | 14.5k | Apache-2.0 | Kotlin/JNA, 离线 ASR |
| android-vad | gkonovalov/android-vad | ~2k | Apache-2.0 | Silero VAD ONNX Runtime Mobile |
| Transcribro | soupslurpr/Transcribro | ~5k | GPL-3.0 | Whisper + Silero VAD + ONNX（参考） |
| speech-android | soniqo/speech-android | ~1k | Apache-2.0 | Parakeet TDT + Silero v5 + NNAPI（参考） |

### 1.2 依赖配置

```kotlin
dependencies {
    // Vosk 离线 STT（通过 JNA 调用 C++ 库）
    implementation("com.alphacephei:vosk-android:0.3.70")
    // Silero VAD（gkonovalov 封装，降低集成复杂度）
    implementation("com.github.gkonovalov.android-vad:silero:2.0.10")
    // 可选：WebRTC VAD 双级联用过滤噪音
    implementation("com.github.gkonovalov.android-vad:webrtc:2.0.10")
}
```

**踩坑点**：vosk-android 0.3.70 要求 JNA ≥ 5.18.1，与低版本 JNA 会冲突。推荐用官方 AAR 不自行编译。

### 1.3 AudioRecord 配置（16kHz mono 16-bit）

```kotlin
class AudioRecorder(
    private val context: Context,
    private val onAudioChunk: (ShortArray) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Vosk + Silero 统一要求：16kHz, mono, 16-bit PCM
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    // Silero VAD 16kHz 下支持 frame size: 512, 1024, 1536
    private val frameSize = 512  // 选 512 → 单次推理延迟最低
    private val bufferSize = maxOf(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        frameSize * 2 * 2  // double buffer
    )

    fun start() {
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,  // 或 VOICE_RECOGNITION
            sampleRate, channelConfig, audioFormat, bufferSize
        ).apply { startRecording() }

        isRecording = true
        scope.launch {
            val buffer = ShortArray(frameSize)
            while (isRecording && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val read = audioRecord?.read(buffer, 0, frameSize) ?: -1
                if (read > 0) onAudioChunk(buffer.copyOf(read))
            }
        }
    }

    fun stop() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
}
```

**踩坑点**：Android 14+（API 34）对前台录音限制更严——AudioRecord 必须在 foreground service 或 visible Activity 中启动，否则被系统静默拒绝。

### 1.4 Vosk Model 加载（assets + 动态下载双模式）

```kotlin
object VoskModelLoader {
    /**
     * 模式1：从 assets 解压到 filesDir（适合小模型 <50MB）
     */
    fun loadFromAssets(context: Context, assetPath: String = "vosk-model-cn"): String {
        val modelDir = File(context.filesDir, "vosk-model")
        if (!modelDir.exists()) {
            modelDir.mkdirs()
            // 解压 assets 下的 zip 到 filesDir
            context.assets.open(assetPath).use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(modelDir, entry.name)
                        outFile.parentFile?.mkdirs()
                        if (!entry.isDirectory) {
                            FileOutputStream(outFile).use { zis.copyTo(it) }
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        }
        return modelDir.absolutePath
    }

    /**
     * 模式2：从 URL 动态下载（42MB 中文小模型，首包不打包）
     */
    suspend fun downloadModel(
        context: Context,
        url: String = "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.15.zip",
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val targetDir = File(context.filesDir, "vosk-model-downloaded")
        if (targetDir.exists() && targetDir.listFiles()?.isNotEmpty() == true) {
            return@withContext targetDir.absolutePath
        }
        targetDir.mkdirs()

        val zipFile = File(context.cacheDir, "model.zip")
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            val body = response.body ?: throw IOException("Empty body")
            val totalBytes = body.contentLength()
            zipFile.outputStream().buffered().use { sink ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalRead = 0L
                while (body.byteStream().read(buffer).also { bytesRead = it } != -1) {
                    sink.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalBytes > 0) onProgress(totalRead.toFloat() / totalBytes)
                }
            }
        }

        // 解压
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                outFile.parentFile?.mkdirs()
                if (!entry.isDirectory) {
                    FileOutputStream(outFile).use { zis.copyTo(it) }
                }
                entry = zis.nextEntry
            }
        }
        zipFile.delete()
        targetDir.absolutePath
    }
}
```

### 1.5 Silero VAD 实例化（ONNX Runtime）

基于 gkonovalov/android-vad 源码拆解（`VadSilero.kt`）：

```kotlin
class SileroVadManager(
    context: Context,
    private val onSpeechStart: () -> Unit,
    private val onSpeechEnd: () -> Unit
) : Closeable {

    private val vad: VadSilero = VadSilero(
        context,
        sampleRate = SampleRate.SAMPLE_RATE_16K,
        frameSize = FrameSize.FRAME_SIZE_512,
        mode = Mode.NORMAL,              // threshold = 0.5
        silenceDurationMs = 300,         // 静音 300ms 判定结束
        speechDurationMs = 50            // 语音 50ms 判定开始
    )

    private var isInSpeech = false

    fun processAudioChunk(audioData: ShortArray): Boolean {
        val isSpeech = vad.isSpeech(audioData)
        when {
            isSpeech && !isInSpeech -> {
                isInSpeech = true
                onSpeechStart()
            }
            !isSpeech && isInSpeech -> {
                isInSpeech = false
                onSpeechEnd()
            }
        }
        return isSpeech
    }

    override fun close() = vad.close()
}
```

**VadSilero 内部实现要点**（源码拆解）：
- ONNX Session：`setIntraOpNumThreads(1)` + `setInterOpNumThreads(1)` + `ALL_OPT` 优化
- 模型从 assets 加载：`context.assets.open("silero_vad.onnx").readBytes()`
- 输入 tensor：`input[1, frameSize]` + `sr[1]` + `h[2,1,64]` + `c[2,1,64]`（LSTM hidden/cell state）
- 输出：置信度 float + 更新后的 HN/CN
- 阈值：NORMAL=0.5 / AGGRESSIVE=0.8 / VERY_AGGRESSIVE=0.95
- 连续语音检测算法：`speechFramesCount` / `silenceFramesCount` 双计数器

### 1.6 VAD → Vosk 协同模式（核心代码）

```kotlin
class VoiceInputManager(
    private val context: Context,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit
) : Closeable {

    private lateinit var model: Model
    private lateinit var recognizer: Recognizer
    private var sileroVad: SileroVadManager? = null
    private val recorder = AudioRecorder(context) { chunk -> processChunk(chunk) }

    private val speechBuffer = ByteArrayOutputStream()
    private var isAccumulating = false

    fun init(modelPath: String) {
        model = Model(modelPath)
        recognizer = Recognizer(model, 16000.0f)
        sileroVad = SileroVadManager(
            context,
            onSpeechStart = {
                isAccumulating = true
                speechBuffer.reset()
            },
            onSpeechEnd = {
                isAccumulating = false
                submitToVosk()
            }
        )
    }

    private fun processChunk(audioData: ShortArray) {
        val isSpeech = sileroVad?.processAudioChunk(audioData) ?: false
        if (isAccumulating || isSpeech) {
            // ShortArray → ByteArray (little-endian 16-bit)
            val byteBuffer = ByteBuffer.allocate(audioData.size * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
            audioBuffer.forEach { byteBuffer.putShort(it) }
            speechBuffer.write(byteBuffer.array())

            // 实时喂给 Vosk 获取 partial result
            if (recognizer.acceptWaveForm(byteBuffer.array(), audioData.size * 2)) {
                val partial = JSONObject(recognizer.partialResult).optString("partial", "")
                if (partial.isNotEmpty()) onPartialResult(partial)
            }
        }
    }

    private fun submitToVosk() {
        val finalText = JSONObject(recognizer.result).optString("text", "")
        if (finalText.isNotEmpty()) onFinalResult(finalText)
        recognizer.reset()  // 清空内部状态，准备下一句
    }

    fun start() = recorder.start()
    fun stop() = recorder.stop()

    override fun close() {
        recorder.stop()
        sileroVad?.close()
        recognizer.close()
        model.close()
    }
}
```

### 1.7 Android 14+ 前台录音限制处理

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

```kotlin
class ForegroundRecordingService : Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 必须在 5 秒内调用 startForeground
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AGenUI 正在监听")
            .setSmallIcon(R.drawable.ic_mic)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        return START_NOT_STICKY
    }
}
```

**关键风险**：Android 14 对 `FOREGROUND_SERVICE_TYPE_MICROPHONE` 有硬性要求，未声明 type 会被系统 kill。参考：https://developer.android.com/about/versions/14/changes/fg-service-types

---

## 二、XPopup + BlurView 透明 Activity 集成

### 2.1 项目信息

| 项目 | GitHub | Stars | License | 最新版本 |
|------|--------|-------|---------|----------|
| XPopup | li-xiaojun/XPopup | ~5.5k | Apache-2.0 | 2.10.0 |
| BlurView | Dimezis/BlurView | ~4.5k | Apache-2.0 | 3.2.0 |

### 2.2 透明 Activity 主题

```xml
<!-- res/values/themes.xml -->
<style name="Theme.AGenUI.Transparent" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="android:windowIsTranslucent">true</item>
    <item name="android:windowBackground">@android:color/transparent</item>
    <item name="android:windowContentOverlay">@null</item>
    <item name="android:windowNoTitle">true</item>
    <item name="android:windowIsFloating">false</item>
    <item name="android:backgroundDimEnabled">false</item>
    <item name="android:colorBackgroundCacheHint">@null</item>
    <item name="android:windowAnimationStyle">@style/Animation.AGenUI.BottomSheet</item>
</style>

<style name="Animation.AGenUI.BottomSheet" parent="@android:style/Animation.Activity">
    <item name="android:windowEnterAnimation">@anim/a2ui_slide_in_bottom</item>
    <item name="android:windowExitAnimation">@anim/a2ui_slide_out_bottom</item>
</style>
```

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".ui.InputActivity"
    android:exported="false"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:theme="@style/Theme.AGenUI.Transparent"
    android:configChanges="orientation|screenSize|keyboardHidden" />
```

### 2.3 XPopup BottomPopupView（竖屏）vs DrawerPopupView（横屏）

```kotlin
class A2UIInputPopup(
    context: Context,
    private val onInputComplete: (String, InputMode) -> Unit
) : BottomPopupView(context) {

    enum class InputMode { KEYBOARD, VOICE, FILE }

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var blurView: BlurView

    override fun getImplLayoutId() = R.layout.popup_a2ui_input

    override fun onCreate() {
        super.onCreate()
        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)
        blurView = findViewById(R.id.blur_view)
        setupBlur()
        setupTabs()
        setupViewPager()
    }

    private fun setupBlur() {
        val radius = 12f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ 使用 RenderEffect（硬件加速）
            blurView.setRenderEffect(
                RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR)
            )
        } else {
            // <31 降级 RenderScript
            blurView.setBlurRadius(radius)
                .setBlurAlgorithm(RenderScriptBlur(context))
                .setHasFixedTransformationMatrix(true)
        }
    }

    fun submitInput(text: String, mode: InputMode) {
        onInputComplete(text, mode)
        dismiss()
    }

    override fun getMaxHeight() = (XPopupUtils.getWindowHeight(context) * 0.85f).toInt()
}
```

### 2.4 横屏切换 + 生命周期

```kotlin
class InputActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val orientation = resources.configuration.orientation
        val popup = if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            A2UIInputPopup(this) { text, mode -> finishWithResult(text, mode) }
        } else {
            A2UIDrawerPopup(this) { text, mode -> finishWithResult(text, mode) }
        }
        XPopup.Builder(this)
            .autoOpenSoftInput(true)
            .asCustom(popup)
            .show()
    }

    private fun finishWithResult(text: String, mode: A2UIInputPopup.InputMode) {
        val resultIntent = Intent().apply {
            putExtra("input_text", text)
            putExtra("input_mode", mode.name)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
}
```

**踩坑点**：
- XPopup 的 `BottomPopupView` 本质是自定义 View，不是 Fragment。需要 Compose 内容时用 `ComposeView` 嵌入。
- BlurView 的 RenderEffect 在 API 31+ 性能最佳，<31 时 RenderScript 已 deprecated，建议对低版本降级为半透明遮罩。

---

## 三、GetStream stream-chat-android-ai 源码结构

### 3.1 项目信息

| 项目 | GitHub | Stars | License | 包名 |
|------|--------|-------|---------|------|
| stream-chat-android-ai | GetStream/stream-chat-android-ai | ~200+ | **Stream License**（非 OSI 开源） | io.getstream.chat.android.ai.compose |

**关键风险**：Stream License 不是 Apache 2.0 或 MIT，商业使用需仔细阅读条款。

### 3.2 四个 Compose 组件

| 组件 | 完整类名 | 源码路径 |
|------|----------|----------|
| StreamingText | `io.getstream.chat.android.ai.compose.ui.component.StreamingText` | `stream-chat-android-ai-compose/.../ui/component/StreamingText.kt` |
| AITypingIndicator | `io.getstream.chat.android.ai.compose.ui.component.AITypingIndicator` | `.../ui/component/AITypingIndicator.kt` |
| ChatComposer | `io.getstream.chat.android.ai.compose.ui.component.ChatComposer` | `.../ui/component/ChatComposer.kt` |
| SpeechToTextButton | `io.getstream.chat.android.ai.compose.ui.component.SpeechToTextButton` | `.../ui/component/SpeechToTextButton.kt` |

### 3.3 StreamingText 逐字流式显示

**核心原理**：按单词分块（word-by-word）+ 延迟揭示 + Markdown 渲染

```kotlin
@Composable
fun AssistantMessage(text: String, isGenerating: Boolean) {
    StreamingText(
        text = text,
        animate = isGenerating,
        chunkDelayMs = 30  // 默认 30ms
    ) { displayedText ->
        Text(text = displayedText, style = MaterialTheme.typography.bodyLarge)
    }
}
```

**行为逻辑**：
1. `animate=true` → 按 `chunkDelayMs` 间隔逐块揭示
2. 续接处理：新文本以旧文本开头 → 从当前位置继续；完全不同 → 重置
3. `animate=false` → 立即显示全文

### 3.4 SpeechToTextButton 机制

```kotlin
@Composable
fun MyComposer() {
    var text by remember { mutableStateOf("") }
    val speechState = rememberSpeechToTextButtonState(
        onPartialResult = { partialText -> text = partialText },
        onFinalResult = { finalText -> text = finalText }
    )
    SpeechToTextButton(state = speechState)
}
```

**封装机制**：
- 内部使用 Android `SpeechRecognizer`（非 Vosk，需联网 Google 服务）
- 自动请求 `RECORD_AUDIO` 权限
- `recordingContent` lambda 接收 `rmsdb: Float`（0-10 范围音频电平），驱动波形动画
- UI 状态自动切换：idle（麦克风图标）→ recording（动画条）

### 3.5 AITypingIndicator 三态动画

```kotlin
AITypingIndicator(
    label = { Text("Thinking") },
    indicator = { CircularProgressIndicator() }
)
```

三态通过 `label` 参数切换文案：`"Thinking"` / `"Processing..."` / `"Checking external sources"`。默认 `AnimatedDots` 实现三个圆点依次高亮动画。

### 3.6 耦合度分析

**结论**：UI 组件可独立使用，但功能受限

```kotlin
dependencies {
    implementation("io.getstream:stream-chat-android-ai-compose:$version")
    // 不需要 stream-chat-android-client / stream-chat-android-ui
}
```

**建议**：StreamingText 和 AITypingIndicator 复用价值最高，SpeechToTextButton 因依赖系统 SpeechRecognizer（需联网 Google 服务），AGenUI 场景应替换为 Vosk。

---

## 四、companion-widget-LLM 架构拆解

### 4.1 项目信息

| 项目 | GitHub | Stars | License | 最后更新 |
|------|--------|-------|---------|----------|
| companion-widget-LLM | TaAnhQuan/companion-widget-LLM | ~100+ | **教育/个人使用**（非开源许可证） | 2025-12-21 |

**关键风险**：License 是 "educational and personal use"，**不可直接商用复用代码**。仅可参考架构设计。

### 4.2 项目结构

```
companion-widget-LLM/
├── app/src/main/java/com/taanhquan/companionwidget/
│   ├── service/
│   │   ├── FloatingBubbleService.java     ← 前台 Service + WindowManager
│   │   └── ChatService.java               ← 聊天会话管理
│   ├── llm/
│   │   ├── LLMProvider.java               ← 接口（策略模式）
│   │   ├── ClaudeProvider.java
│   │   ├── GPTProvider.java
│   │   ├── GeminiProvider.java
│   │   └── OllamaProvider.java
│   ├── ui/
│   │   ├── FloatingChatView.java          ← 悬浮聊天窗口
│   │   └── KnowledgeGraphActivity.java
│   ├── screenshot/
│   │   └── ScreenshotManager.java         ← MediaProjection 截图
│   ├── voice/
│   │   └── VoiceInputManager.java         ← STT 模块
│   └── knowledge/
│       ├── EntityExtractor.java           ← ML Kit 实体提取
│       └── KnowledgeGraphManager.java
```

### 4.3 多 LLM 后端策略模式

```java
public interface LLMProvider {
    void sendMessage(String message, LLMCallback callback);
    String getModelName();
}

public class LLMManager {
    private LLMProvider currentProvider;
    private Map<String, LLMProvider> providers = new HashMap<>();

    public void init() {
        providers.put("claude-sonnet-4", new ClaudeProvider(apiKey));
        providers.put("gpt-4o", new GPTProvider(apiKey));
        providers.put("gemini-2.5-pro", new GeminiProvider(apiKey));
        providers.put("ollama", new OllamaProvider("http://localhost:11434"));
    }

    public void switchModel(String modelId) {
        currentProvider = providers.get(modelId);
    }
}
```

**API 通信**：Retrofit + OkHttp，各 Provider 独立实现 API 接口。

### 4.4 截图模块（MediaProjection）

```java
public class ScreenshotManager {
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;

    public void capture(Rect bounds, ScreenshotCallback callback) {
        ImageReader imageReader = ImageReader.newInstance(
            bounds.width(), bounds.height(),
            PixelFormat.RGBA_8888, 2
        );
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "Screenshot", bounds.width(), bounds.height(),
            densityDpi, DISPLAY_FLAGS, imageReader.getSurface(), null, null
        );
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            // 处理 image → Bitmap
        }, null);
    }
}
```

### 4.5 可拆分复用模块

1. ✅ `LLMProvider` 策略模式接口设计 → 可直接参考
2. ✅ `FloatingBubbleService` WindowManager 悬浮球模式 → 可参考
3. ✅ `ScreenshotManager` MediaProjection 实现 → 可复用
4. ❌ ObjectBox 知识图谱 → AGenUI 不需要
5. ❌ Voice Input → 用系统 SpeechRecognizer，AGenUI 应替换为 Vosk

---

## 五、Jetpack Glance 1.1.1 Stable 代码示例

### 5.1 依赖配置

```kotlin
dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
}
```

### 5.2 actionRunCallback 在 Widget 进程内执行协程

```kotlin
class A2UIWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(180.dp, 180.dp), DpSize(300.dp, 270.dp))
    )

    @Composable
    override fun Content() {
        val prefs = currentState<Preferences>()
        val prompt = prefs[promptKey] ?: ""
        val isGenerating = prefs[isGeneratingKey] ?: false

        Column(
            modifier = GlanceModifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (prompt.isEmpty()) "点击输入" else prompt,
                modifier = GlanceModifier.clickable(
                    onClick = actionStartActivity<InputActivity>()
                )
            )
            Button(
                text = if (isGenerating) "生成中..." else "重新生成",
                onClick = actionRunCallback<GenerateActionCallback>()
            )
        }
    }

    companion object {
        val promptKey = stringPreferencesKey("prompt")
        val isGeneratingKey = booleanPreferencesKey("isGenerating")
    }
}

class GenerateActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Widget 进程内执行协程，但时间限制 ~10s
        // 长任务必须委派 WorkManager
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[isGeneratingKey] = true
        }
        A2UIWidget().update(context, glanceId)

        val workRequest = OneTimeWorkRequestBuilder<LLMGenerateWorker>()
            .setInputData(workDataOf("glance_id" to glanceId.toString()))
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
```

### 5.3 StateFlow + collectAsState 响应式更新

```kotlin
class A2UIWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = A2UIWidget()
    private val scope = MainScope()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scope.launch {
            val glanceId = GlanceAppWidgetManager(context)
                .getGlanceIds(A2UIWidget::class.java).firstOrNull() ?: return@launch

            // 观察 Room Flow，自动更新 Widget
            val db = A2UIDatabase.getDatabase(context)
            db.generationHistoryDao().observeRecent()
                .map { it.firstOrNull() }
                .collect { latest ->
                    if (latest != null) {
                        updateAppWidgetState(context, glanceId) { prefs ->
                            prefs[promptKey] = latest.prompt
                            prefs[isGeneratingKey] = !latest.success
                        }
                        A2UIWidget().update(context, glanceId)
                    }
                }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        scope.cancel()
    }
}
```

### 5.4 @Preview 可预览性

```kotlin
@Preview(widthDp = 180, heightDp = 180)
@Preview(widthDp = 300, heightDp = 270)
@Composable
fun A2UIWidgetPreview() {
    A2UIWidget().Content()
}
```

**限制**：Glance 的 `@Preview` 在 Android Studio 中支持有限，`currentState()` 在预览时返回空状态。

---

## 六、Room + Flow Widget 场景应用

### 6.1 Entity 设计

```kotlin
@Entity(tableName = "generation_history")
data class GenerationHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "prompt") val prompt: String,
    @ColumnInfo(name = "a2ui_json") val a2uiJson: String,
    @ColumnInfo(name = "preview_bitmap_path") val previewBitmapPath: String?,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "success") val success: Boolean,
    @ColumnInfo(name = "latency_ms") val latency: Long,
    @ColumnInfo(name = "llm_model") val llmModel: String
)
```

### 6.2 DAO 接口

```kotlin
@Dao
interface GenerationHistoryDao {
    @Insert
    suspend fun insert(history: GenerationHistory): Long

    @Query("SELECT * FROM generation_history ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<GenerationHistory>>

    @Query("SELECT * FROM generation_history WHERE prompt LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun search(query: String): Flow<List<GenerationHistory>>

    @Query("SELECT * FROM generation_history WHERE id = :id")
    suspend fun getById(id: Long): GenerationHistory?

    @Query("DELETE FROM generation_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM generation_history WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}
```

### 6.3 预览图存储方案对比

| 方案 | 实现 | 优点 | 缺点 | AGenUI 推荐 |
|------|------|------|------|-------------|
| **Blob 存数据库** | `@ColumnInfo(typeName="BLOB") val preview: ByteArray` | 事务一致性 | 数据库膨胀，>1MB Room 性能下降 | ❌ |
| **文件系统** | 存 `filesDir/preview_{id}.png`，DB 存 path | 数据库小 | 需手动管理文件生命周期 | ✅ |
| **ContentResolver URI** | 存 MediaStore URI | 系统图库可见 | 需权限，Widget 跨域问题 | ❌ |

```kotlin
object PreviewBitmapStorage {
    suspend fun save(context: Context, bitmap: Bitmap, id: Long): String =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "preview_$id.png")
            FileOutputStream(file).use { fos ->
                if (bitmap.byteCount > 800_000) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                } else {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                }
            }
            file.absolutePath
        }

    fun load(path: String): Bitmap? {
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(path)
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }
}
```

### 6.4 Database 单例

```kotlin
@Database(entities = [GenerationHistory::class], version = 1, exportSchema = false)
abstract class A2UIDatabase : RoomDatabase() {
    abstract fun generationHistoryDao(): GenerationHistoryDao

    companion object {
        @Volatile private var INSTANCE: A2UIDatabase? = null

        fun getDatabase(context: Context): A2UIDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    A2UIDatabase::class.java,
                    "a2ui_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
```

---

## 七、WorkManager setForeground 长任务

### 7.1 LLM 生成 Worker 完整实现

```kotlin
class LLMGenerateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prompt = inputData.getString("prompt") ?: return Result.failure()
        val glanceIdStr = inputData.getString("glance_id") ?: return Result.failure()

        // 1. 标记前台服务
        setForeground(createForegroundInfo("正在生成小组件...", 0))

        try {
            // 2. 调用 LLM API（流式）
            val llmClient = LLMClientFactory.create()
            var fullText = ""
            var chunkIndex = 0

            llmClient.streamGenerate(prompt) { chunk ->
                fullText += chunk
                chunkIndex++
                if (chunkIndex % 5 == 0) {
                    setForeground(createForegroundInfo(
                        "生成中: ${fullText.length} 字符",
                        (chunkIndex * 100 / 50).coerceAtMost(90)
                    ))
                }
            }

            // 3. 解析 A2UI JSON
            val a2uiJson = parseA2UIResponse(fullText)

            // 4. 渲染预览图 + 存历史
            val bitmap = renderWidgetPreview(a2uiJson)
            val db = A2UIDatabase.getDatabase(applicationContext)
            val id = db.generationHistoryDao().insert(
                GenerationHistory(
                    prompt = prompt,
                    a2uiJson = a2uiJson,
                    previewBitmapPath = null,
                    success = true,
                    latency = 0,
                    llmModel = llmClient.modelName
                )
            )
            val path = PreviewBitmapStorage.save(applicationContext, bitmap, id)
            db.generationHistoryDao().updatePreviewPath(id, path)

            // 5. 更新 Widget
            val glanceId = parseGlanceId(glanceIdStr)
            updateWidgetState(applicationContext, glanceId) { prefs ->
                prefs[promptKey] = prompt
                prefs[isGeneratingKey] = false
            }
            A2UIWidget().update(applicationContext, glanceId)

            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    private fun createForegroundInfo(progress: String, percent: Int): ForegroundInfo {
        val channelId = "a2ui_generation"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "小组件生成", NotificationManager.IMPORTANCE_LOW
            )
            applicationContext.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("AGenUI")
            .setContentText(progress)
            .setProgress(100, percent, percent == 0)
            .setSmallIcon(R.drawable.ic_a2ui_notification)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "取消", cancelIntent)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object { const val NOTIFICATION_ID = 1001 }
}
```

### 7.2 重试策略（指数退避）

```kotlin
val workRequest = OneTimeWorkRequestBuilder<LLMGenerateWorker>()
    .setBackoffCriteria(
        BackoffPolicy.EXPONENTIAL,
        2,                          // 初始延迟 2 秒
        TimeUnit.SECONDS
    )
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    )
    .build()
```

在 Worker 中返回 `Result.retry()` 触发指数退避重试：2s → 4s → 8s → 16s → 32s。

**踩坑点**：`setExpedited()` 在 Android 12+ 有配额限制，超配额会降级为普通 WorkRequest。LLM 生成场景建议用普通模式 + `setForeground()`。

---

## 八、Bitmap 内存管理

### 8.1 BitmapPool 复用池设计

```kotlin
class A2UIBitmapPool(maxSize: Int = 4 * 1024 * 1024) {  // 4MB 默认
    private val pool = object : LruCache<String, Bitmap>(maxSize) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap? {
        val key = "${width}x${height}_${config}"
        val reusable = pool.get(key)
        if (reusable != null && !reusable.isRecycled) return reusable
        return null
    }

    fun put(bitmap: Bitmap) {
        if (bitmap.isRecycled) return
        val key = "${bitmap.width}x${bitmap.height}_${bitmap.config}"
        pool.put(key, bitmap)
    }

    fun clear() {
        // 不要在这里 recycle，Bitmap 可能正在被 RemoteViews 使用
        pool.evictAll()
    }
}

// BitmapFactory.Options.inBitmap 复用
fun decodeWithReuse(pool: A2UIBitmapPool, data: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, options)
    options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
    options.inJustDecodeBounds = false
    options.inMutable = true  // 必须设为 true 才能复用
    pool.get(reqWidth, reqHeight)?.let { reusable ->
        options.inBitmap = reusable
    }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)
}
```

### 8.2 Bitmap.recycle 时机

```kotlin
class WidgetBitmapManager {
    private val activeBitmaps = mutableMapOf<String, WeakReference<Bitmap>>()

    fun bindBitmapToWidget(remoteViews: RemoteViews, viewId: Int, bitmap: Bitmap, key: String) {
        remoteViews.setImageViewBitmap(viewId, bitmap)
        // RemoteViews 会内部序列化 Bitmap → Binder 传输
        activeBitmaps[key] = WeakReference(bitmap)
    }

    fun cleanupAfterUpdate(key: String) {
        activeBitmaps[key]?.get()?.let { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        activeBitmaps.remove(key)
    }
}
```

**踩坑点**：`setImageViewBitmap()` 后立即 `recycle()` 在某些设备上会导致 Widget 显示空白。安全做法是在下一次 Widget 更新时 recycle 上一次的 Bitmap。

### 8.3 大 Widget Bitmap >1MB JPEG 降级

```kotlin
object WidgetBitmapScaler {
    private const val MAX_SAFE_BINDER_SIZE = 800_000

    fun scaleForWidget(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val scaled = if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        } else bitmap

        val rawSize = scaled.width * scaled.height * 4
        if (rawSize <= MAX_SAFE_BINDER_SIZE) return scaled

        // 降级为 RGB_565（减半内存）
        val reduced = scaled.copy(Bitmap.Config.RGB_565, true)
        val reducedSize = reduced.width * reduced.height * 2
        if (reducedSize <= MAX_SAFE_BINDER_SIZE) return reduced

        // JPEG 压缩
        val baos = ByteArrayOutputStream()
        var quality = 90
        do {
            baos.reset()
            reduced.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            quality -= 10
        } while (baos.size() > MAX_SAFE_BINDER_SIZE && quality > 30)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    /**
     * 替代方案：setImageViewUri 避免序列化 Bitmap 到 Binder
     */
    fun saveAndGetUri(context: Context, bitmap: Bitmap, id: Long): Uri {
        val file = File(context.filesDir, "widget_image_$id.png")
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        file.setReadable(true, false)  // world-readable
        return Uri.fromFile(file)
    }
}
```

### 8.4 生命周期 + 弱引用

```kotlin
class LifecycleAwareBitmapLoader : DefaultLifecycleObserver {
    private val bitmapPool = A2UIBitmapPool()
    private val activeBitmaps = mutableSetOf<WeakReference<Bitmap>>()

    fun loadForWidget(widgetId: Int, data: ByteArray, width: Int, height: Int): Bitmap {
        val reusable = bitmapPool.get(width, height)
        val bitmap = if (reusable != null) {
            decodeWithReuse(bitmapPool, data, width, height)
        } else {
            BitmapFactory.decodeByteArray(data, 0, data.size)
        }
        activeBitmaps.add(WeakReference(bitmap))
        return bitmap
    }

    override fun onStop(owner: LifecycleOwner) {
        activeBitmaps.forEach { ref ->
            ref.get()?.let { bitmap ->
                if (!bitmap.isRecycled) bitmapPool.put(bitmap)
            }
        }
        activeBitmaps.clear()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        bitmapPool.clear()
    }
}
```

---

## 九、综合风险矩阵

| 风险项 | 等级 | 影响范围 | 缓解方案 |
|--------|------|----------|----------|
| Stream License 非开源 | 🔴 高 | GetStream 组件商业使用 | 仅参考源码，自行实现 StreamingText |
| companion-widget-LLM 教育许可 | 🔴 高 | 架构参考 | 不复用代码，仅学习设计 |
| Android 14 前台录音 type | 🟡 中 | Vosk 集成 | 声明 FOREGROUND_SERVICE_MICROPHONE |
| Binder 1MB Bitmap 限制 | 🟡 中 | Widget 预览图 | RGB_565 降级 + JPEG 压缩 + setImageViewUri |
| Vosk JNA 版本冲突 | 🟡 中 | 依赖管理 | 使用官方 AAR，排除传递依赖的旧 JNA |
| Glance ActionCallback 10s 限制 | 🟡 中 | Widget 交互 | 长任务委派 WorkManager |
| Silero VAD assets 加载 | 🟢 低 | 模型部署 | 确保 silero_vad.onnx 在 assets/ 根目录 |
| XPopup 横屏 Drawer | 🟢 低 | UI 适配 | 按 orientation 切换弹窗类型 |

---

## 十、集成优先级建议

1. **P0 立即集成**：Vosk + Silero VAD（第一章）→ 离线语音输入是 AGenUI 核心差异化
2. **P0 立即集成**：Room + Flow（第六章）→ 历史记录是基础体验
3. **P1 尽快集成**：WorkManager setForeground（第七章）→ LLM 长任务必需
4. **P1 尽快集成**：Glance 1.1.1（第五章）→ Widget 交互层
5. **P2 参考实现**：XPopup + BlurView（第二章）→ 输入 UI
6. **P2 参考源码**：GetStream 组件（第三章）→ StreamingText 逐字显示可复用
7. **P2 架构学习**：companion-widget-LLM（第四章）→ LLM Provider 策略模式
8. **P3 持续优化**：Bitmap 内存管理（第八章）→ 性能优化阶段
