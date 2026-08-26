package com.amap.agenuiplayground.widget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;

import com.amap.agenui.AGenUI;
import com.amap.agenui.render.surface.ISurfaceManagerListener;
import com.amap.agenui.render.surface.Surface;
import com.amap.agenui.render.surface.SurfaceManager;
import com.amap.agenui.render.surface.SurfaceSize;
import com.amap.agenuiplayground.R;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Transparent Activity that hosts SurfaceManager for widget rendering.
 *
 * Two modes:
 * - MODE_TEMPLATE (Phase 1): load static template JSON → stream → draw → push
 * - MODE_STREAM (Phase 2.1): user text → LLM stream → SurfaceManager incremental build
 *   → throttled measure+draw → final push → finish
 *
 * Stream mode fallback: LLM failure → keyword-matched template → notecard template.
 */
public class WidgetRenderActivity extends Activity {

    private static final String TAG = "WidgetRenderActivity";
    private static final long SURFACE_TIMEOUT_MS = 5000;
    private static final long REFRESH_THROTTLE_MS = 500;

    // Mode constants
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_STREAM = "stream";
    public static final String MODE_TEMPLATE = "template";
    public static final String EXTRA_USER_TEXT = "userText";

    private SurfaceManager surfaceManager;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String template;
    private String mode = MODE_TEMPLATE;
    private String userText;

    // Stream mode state
    private String surfaceId;
    private volatile Surface streamSurface;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private long lastRefreshMs = 0;
    private final Runnable refreshRunnable = this::doRefresh;
    private volatile boolean streamFinished = false;
    private WidgetPartialParser partialParser;
    private long streamStartTimeMs = 0;
    private WidgetHistoryRepository historyRepository;

    // F4: reusable bitmap for streaming refresh — only reallocated on size change.
    private Bitmap streamBitmap;
    // F3: cached widget target width in pixels (0 until first measured).
    private int widgetTargetWidthPx = 0;
    // F3: min/max widget width in a2ui virtual pixels (vp) — clamped after density conversion.
    private static final int WIDGET_MIN_WIDTH_PX = 280;
    private static final int WIDGET_MAX_WIDTH_PX = 400;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        appWidgetId = intent.getIntExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        mode = intent.getStringExtra(EXTRA_MODE);
        if (mode == null) mode = MODE_TEMPLATE;
        template = intent.getStringExtra(A2UIWidgetProvider.EXTRA_TEMPLATE);
        userText = intent.getStringExtra(EXTRA_USER_TEXT);

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "Missing appWidgetId");
            finish();
            return;
        }

        Log.d(TAG, "onCreate: mode=" + mode + ", id=" + appWidgetId
                + (template != null ? ", template=" + template : "")
                + (userText != null ? ", userText=" + userText : ""));

        AGenUI.getInstance().initialize(getApplicationContext());
        AGenUI.getInstance().setDebug(true);

        if (MODE_STREAM.equals(mode)) {
            startStreamMode();
        } else {
            startTemplateMode();
        }
    }

    // ===== Template mode (Phase 1 backward compatible) =====

    private void startTemplateMode() {
        if (template == null) template = WidgetProtocolTemplates.DEFAULT_TEMPLATE;
        if (template.isEmpty()) template = WidgetProtocolTemplates.DEFAULT_TEMPLATE;

        String templateJson = WidgetProtocolTemplates.loadTemplate(this, template,
                "widget_" + appWidgetId + "_" + System.currentTimeMillis());
        if (templateJson == null) {
            Log.e(TAG, "Template not found: " + template);
            finish();
            return;
        }

        String createSurfaceJson, updateComponentsJson, updateDataModelJson;
        try {
            JSONArray arr = new JSONArray(templateJson);
            createSurfaceJson = arr.optJSONObject(0) != null ? arr.optJSONObject(0).toString() : null;
            updateComponentsJson = arr.optJSONObject(1) != null ? arr.optJSONObject(1).toString() : null;
            updateDataModelJson = (arr.optJSONObject(2) != null) ? arr.optJSONObject(2).toString() : null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse template JSON", e);
            finish();
            return;
        }

        surfaceManager = new SurfaceManager(this);
        surfaceId = "widget_" + appWidgetId + "_" + System.currentTimeMillis();

        final CountDownLatch surfaceCreated = new CountDownLatch(1);
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>(null);

        registerSurfaceListener(surfaceManager, surfaceRef, surfaceCreated);

        try {
            surfaceManager.beginTextStream();
            if (createSurfaceJson != null) surfaceManager.receiveTextChunk(createSurfaceJson);
            if (updateComponentsJson != null) surfaceManager.receiveTextChunk(updateComponentsJson);
            if (updateDataModelJson != null && !updateDataModelJson.contains("\"value\":{}"))
                surfaceManager.receiveTextChunk(updateDataModelJson);
            surfaceManager.endTextStream();
        } catch (Exception e) {
            Log.e(TAG, "Failed to stream protocol", e);
            finish();
            return;
        }

        new Thread(() -> {
            try {
                boolean created = surfaceCreated.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!created || surfaceRef.get() == null) {
                    Log.e(TAG, "Surface creation timeout");
                    runOnUiThread(this::finish);
                    return;
                }

                final Surface surface = surfaceRef.get();
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        drawAndPush(surface, "AGenUI · " + template, false);
                    } catch (Exception e) {
                        Log.e(TAG, "Draw failed", e);
                    } finally {
                        finish();
                    }
                });
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted", e);
                runOnUiThread(this::finish);
            }
        }).start();
    }

    // ===== Stream mode (Phase 2.1) =====

    private void startStreamMode() {
        if (userText == null || userText.isEmpty()) {
            Log.e(TAG, "Stream mode: missing userText");
            finish();
            return;
        }

        historyRepository = new WidgetHistoryRepository(this);
        streamStartTimeMs = System.currentTimeMillis();
        surfaceId = "ai-generated";
        surfaceManager = new SurfaceManager(this);
        partialParser = new WidgetPartialParser();

        final CountDownLatch surfaceCreated = new CountDownLatch(1);
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>(null);

        registerSurfaceListener(surfaceManager, surfaceRef, surfaceCreated);

        // Build createSurface JSON (version format — matches LLM output style)
        String createSurfaceJson = "{\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\""
                + surfaceId + "\",\"catalogId\":\"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json\",\"width\":300}}";

        try {
            surfaceManager.beginTextStream();
            surfaceManager.receiveTextChunk(createSurfaceJson);
            // Don't endTextStream yet — LLM chunks will follow
        } catch (Exception e) {
            Log.e(TAG, "Failed to init stream", e);
            finish();
            return;
        }

        // Wait for surface creation on a background thread, then start LLM stream
        new Thread(() -> {
            try {
                boolean created = surfaceCreated.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!created || surfaceRef.get() == null) {
                    Log.e(TAG, "Stream: surface creation timeout");
                    runOnUiThread(this::finish);
                    return;
                }

                streamSurface = surfaceRef.get();
                Log.d(TAG, "Stream: surface ready, starting LLM");

                // Start LLM streaming
                startLLMStream();

            } catch (InterruptedException e) {
                Log.e(TAG, "Stream: interrupted", e);
                runOnUiThread(this::finish);
            }
        }).start();
    }

    private void startLLMStream() {
        WidgetLLMClient client = new WidgetLLMClient(this);
        final StringBuilder fullContent = new StringBuilder();

        // Phase 3A — F5: build messages with dynamic few-shot from history.
        String messagesJson = WidgetPromptBuilder.buildMessagesWithHistory(
                WidgetPromptBuilder.SYSTEM_PROMPT, userText, historyRepository);

        client.streamChat(WidgetPromptBuilder.SYSTEM_PROMPT, userText,
                messagesJson,
                new WidgetLLMClient.StreamCallback() {
                    @Override
                    public void onChunk(String delta) {
                        if (delta == null || delta.isEmpty()) return;
                        fullContent.append(delta);
                        try {
                            // 1. Feed the chunk to the top-level parser — this
                            //    returns a complete JSON only when the entire
                            //    top-level object closes (the final LLM output).
                            List<String> jsonObjects = partialParser.feed(delta);
                            for (String json : jsonObjects) {
                                surfaceManager.receiveTextChunk(json);
                            }

                            // 2. Progressive render: even before the top-level
                            //    object closes, extract any fully-closed
                            //    component objects from the "components" array
                            //    and push them as an updateComponents chunk so
                            //    the user sees incremental UI during streaming.
                            String progressive = partialParser.extractCompletedComponents();
                            if (progressive != null) {
                                surfaceManager.receiveTextChunk(progressive);
                                Log.d(TAG, "Progressive update pushed: " + progressive.length() + " chars");
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "receiveTextChunk failed for chunk", e);
                        }
                        scheduleRefresh();
                    }

                    @Override
                    public void onComplete(String content) {
                        Log.d(TAG, "LLM complete: " + content.length() + " chars");
                        try {
                            surfaceManager.endTextStream();
                        } catch (Exception e) {
                            Log.w(TAG, "endTextStream failed", e);
                        }

                        // Final refresh
                        refreshHandler.removeCallbacks(refreshRunnable);
                        refreshHandler.post(() -> onStreamComplete(content));
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "LLM error", e);
                        runOnUiThread(() -> onStreamError(e));
                    }
                });
    }

    private void onStreamComplete(String content) {
        if (streamFinished) return;
        streamFinished = true;

        long latencyMs = System.currentTimeMillis() - streamStartTimeMs;

        // Extract and validate A2UI JSON
        String a2uiJson = WidgetProtocolValidator.extractA2UIJson(content);
        boolean valid = false;
        if (a2uiJson != null) {
            WidgetProtocolValidator.ValidationResult result =
                    WidgetProtocolValidator.validate(a2uiJson);
            valid = result.valid;
            if (!valid) {
                Log.w(TAG, "Validation failed: " + result.error + ", attempting repair");
                String repaired = WidgetProtocolValidator.repair(a2uiJson);
                WidgetProtocolValidator.ValidationResult repairedResult =
                        WidgetProtocolValidator.validate(repaired);
                if (repairedResult.valid) {
                    Log.d(TAG, "Repair successful");
                    valid = true;
                }
            }
        }

        // Record to history
        if (historyRepository != null) {
            historyRepository.record(userText, content != null ? content : "", latencyMs, valid);
        }

        if (streamSurface == null) {
            Log.e(TAG, "Stream surface is null on complete");
            finish();
            return;
        }

        String title;
        if (valid) {
            title = "AGenUI · AI生成";
        } else {
            title = "AGenUI · AI生成（降级）";
        }

        // Final draw and push
        try {
            drawAndPush(streamSurface, title, false);
        } catch (Exception e) {
            Log.e(TAG, "Final draw failed", e);
            // Try fallback
            loadFallback();
        } finally {
            finish();
        }
    }

    private void onStreamError(Exception e) {
        if (streamFinished) return;
        streamFinished = true;
        long latencyMs = System.currentTimeMillis() - streamStartTimeMs;
        Log.e(TAG, "Stream error, falling back", e);

        // Record failure to history
        if (historyRepository != null) {
            historyRepository.record(userText, "", latencyMs, false);
        }

        loadFallback();
    }

    /**
     * Fallback 链路(优先级):
     * 1. 从历史取上次成功的 A2UI JSON(断网缓存)
     * 2. 关键词匹配的 Phase 1 模板
     * 3. notecard 通用降级卡片
     *
     * 用 WidgetFallbackBuilder 发送 version-format JSON 到新的 SurfaceManager。
     */
    private void loadFallback() {
        Log.d(TAG, "Loading fallback template");

        final String cachedJson = historyRepository != null
                ? historyRepository.getLastSuccessfulJson() : null;

        if (cachedJson != null && !cachedJson.isEmpty()) {
            // 优先使用断网缓存:上次成功渲染的 A2UI JSON
            Log.d(TAG, "Using offline cached JSON");
            new Thread(() -> {
                try {
                    pushCachedFallback(cachedJson, "AGenUI · 离线缓存");
                } catch (Exception e) {
                    Log.e(TAG, "Offline cache fallback failed", e);
                    runOnUiThread(this::loadKeywordFallback);
                }
            }).start();
            return;
        }

        loadKeywordFallback();
    }

    /**
     * 关键词模板 + notecard 降级链路(原 loadFallback 的主体逻辑)。
     */
    private void loadKeywordFallback() {
        String matchedTemplate = matchKeywordTemplate(userText);
        boolean matched = matchedTemplate != null;
        if (!matched) {
            matchedTemplate = "notecard";
        }

        // Build fallback JSON chunks in version format
        List<String> fallbackChunks;
        if (matched) {
            // Load Phase 1 template and convert to version format
            String templateJson = WidgetProtocolTemplates.loadTemplate(this, matchedTemplate,
                    "ai-generated");
            if (templateJson == null) {
                // Ultimate fallback: build notecard directly
                fallbackChunks = WidgetFallbackBuilder.buildNoteCard("ai-generated",
                        "生成失败", "请重试或更换描述");
            } else {
                fallbackChunks = WidgetFallbackBuilder.convertToVersionFormat(
                        templateJson, "ai-generated");
            }
        } else {
            // No keyword match: build notecard directly
            fallbackChunks = WidgetFallbackBuilder.buildNoteCard("ai-generated",
                    "生成失败", "请重试或更换描述");
        }

        if (fallbackChunks.isEmpty()) {
            pushErrorWidget();
            finish();
            return;
        }

        // Reuse existing surfaceManager — destroy old surface, create new with same ID
        try {
            if (surfaceManager != null) {
                surfaceManager.destroy();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to destroy old surfaceManager", e);
        }

        surfaceManager = new SurfaceManager(this);
        final CountDownLatch surfaceCreated = new CountDownLatch(1);
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>(null);
        registerSurfaceListener(surfaceManager, surfaceRef, surfaceCreated);

        try {
            surfaceManager.beginTextStream();
            for (String chunk : fallbackChunks) {
                surfaceManager.receiveTextChunk(chunk);
            }
            surfaceManager.endTextStream();
        } catch (Exception e) {
            Log.e(TAG, "Fallback stream failed", e);
            pushErrorWidget();
            finish();
            return;
        }

        final String fallbackTitle = "AGenUI · AI生成（降级）";
        new Thread(() -> {
            try {
                boolean created = surfaceCreated.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!created || surfaceRef.get() == null) {
                    runOnUiThread(() -> {
                        pushErrorWidget();
                        finish();
                    });
                    return;
                }

                final Surface surface = surfaceRef.get();
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        drawAndPush(surface, fallbackTitle, true);
                    } catch (Exception e) {
                        Log.e(TAG, "Fallback draw failed", e);
                        pushErrorWidget();
                    } finally {
                        finish();
                    }
                });
            } catch (InterruptedException e) {
                runOnUiThread(this::finish);
            }
        }).start();
    }

    /**
     * 用缓存的 A2UI JSON 推送降级渲染。
     * 缓存可能是 LLM 原始输出(含 markdown 代码块),需先用 WidgetProtocolValidator 提取。
     */
    private void pushCachedFallback(String cachedContent, String title) {
        // 提取并校验
        String a2uiJson = WidgetProtocolValidator.extractA2UIJson(cachedContent);
        if (a2uiJson == null) {
            // 可能本身就是裸 JSON
            a2uiJson = cachedContent;
        }

        // 转成 version-format chunks
        List<String> chunks;
        try {
            // 尝试作为完整 A2UI 模板数组解析
            chunks = WidgetFallbackBuilder.convertToVersionFormat(a2uiJson, "ai-generated");
        } catch (Exception e) {
            chunks = new ArrayList<>();
        }
        if (chunks == null || chunks.isEmpty()) {
            // 无法转换,走 notecard
            chunks = WidgetFallbackBuilder.buildNoteCard("ai-generated",
                    "离线缓存", "缓存内容解析失败");
        }

        // 销毁旧 surfaceManager,新建并推送
        try {
            if (surfaceManager != null) {
                surfaceManager.destroy();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to destroy old surfaceManager", e);
        }

        surfaceManager = new SurfaceManager(this);
        final CountDownLatch surfaceCreated = new CountDownLatch(1);
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>(null);
        registerSurfaceListener(surfaceManager, surfaceRef, surfaceCreated);

        try {
            surfaceManager.beginTextStream();
            for (String chunk : chunks) {
                surfaceManager.receiveTextChunk(chunk);
            }
            surfaceManager.endTextStream();
        } catch (Exception e) {
            Log.e(TAG, "Cached fallback stream failed", e);
            runOnUiThread(this::loadKeywordFallback);
            return;
        }

        new Thread(() -> {
            try {
                boolean created = surfaceCreated.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!created || surfaceRef.get() == null) {
                    runOnUiThread(this::loadKeywordFallback);
                    return;
                }

                final Surface surface = surfaceRef.get();
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        drawAndPush(surface, title, true);
                    } catch (Exception e) {
                        Log.e(TAG, "Cached fallback draw failed", e);
                        pushErrorWidget();
                    } finally {
                        finish();
                    }
                });
            } catch (InterruptedException e) {
                runOnUiThread(this::finish);
            }
        }).start();
    }

    /**
     * Matches user text keywords to a known template.
     * @return template name, or null if no match.
     */
    private String matchKeywordTemplate(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase();
        if (lower.contains("天气") || lower.contains("weather") || lower.contains("气温")) {
            return "weather";
        }
        if (lower.contains("议程") || lower.contains("日程") || lower.contains("agenda")
                || lower.contains("schedule")) {
            return "agenda";
        }
        if (lower.contains("待办") || lower.contains("todo") || lower.contains("清单")
                || lower.contains("任务")) {
            return "todo";
        }
        return null;
    }

    private void pushErrorWidget() {
        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.a2ui_widget_content);
        views.setTextViewText(R.id.widgetTitle, "AGenUI · 生成失败");
        views.setTextViewText(R.id.widgetImageView, "生成失败，请重试");
        awm.updateAppWidget(appWidgetId, views);
    }

    // ===== Throttled refresh =====

    private void scheduleRefresh() {
        long now = System.currentTimeMillis();
        long delay = Math.max(0, REFRESH_THROTTLE_MS - (now - lastRefreshMs));
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, delay);
    }

    private void doRefresh() {
        lastRefreshMs = System.currentTimeMillis();
        if (streamSurface == null) {
            Log.d(TAG, "doRefresh: streamSurface is null, skipping");
            return;
        }
        try {
            drawOnly(streamSurface);
            Log.d(TAG, "doRefresh: components drawn, bitmap pushed");
        } catch (Exception e) {
            Log.w(TAG, "doRefresh draw failed", e);
        }
    }

    /**
     * Draws the surface to a bitmap and pushes to widget (no title update).
     * Used during streaming refresh.
     *
     * Phase 3A: reuses {@link #streamBitmap} across calls — only allocates a
     * new Bitmap when the measured size changes. This avoids per-chunk
     * Bitmap allocation during streaming (F4).
     */
    private void drawOnly(Surface surface) {
        View container = surface.getContainer();
        if (container == null) {
            Log.w(TAG, "drawOnly: container is null");
            return;
        }

        int targetWidth = getWidgetTargetWidthPx();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        container.measure(widthSpec, heightSpec);

        int w = container.getMeasuredWidth();
        int h = container.getMeasuredHeight();
        if (h <= 0) h = 200;

        container.layout(0, 0, w, h);

        // F4: reuse streamBitmap if size matches; otherwise reallocate.
        if (streamBitmap == null
                || streamBitmap.getWidth() != w
                || streamBitmap.getHeight() != h
                || streamBitmap.isRecycled()) {
            if (streamBitmap != null && !streamBitmap.isRecycled()) {
                streamBitmap.recycle();
            }
            streamBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Log.d(TAG, "drawOnly: allocated new bitmap " + w + "x" + h);
        } else {
            // Reuse — clear the canvas to white before redrawing.
            streamBitmap.eraseColor(android.graphics.Color.WHITE);
        }

        Canvas canvas = new Canvas(streamBitmap);
        canvas.drawColor(android.graphics.Color.WHITE);
        container.draw(canvas);

        pushBitmapToWidget(streamBitmap, "AGenUI · 生成中...");
    }

    // ===== Common draw + push =====

    private void drawAndPush(Surface surface, String title, boolean isFallback) {
        View container = surface.getContainer();
        if (container == null) {
            Log.e(TAG, "Surface container is null");
            return;
        }

        int targetWidth = getWidgetTargetWidthPx();
        int widthSpec = View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        container.measure(widthSpec, heightSpec);

        int w = container.getMeasuredWidth();
        int h = container.getMeasuredHeight();
        if (h <= 0) h = 200;
        Log.d(TAG, "Measured: " + w + "x" + h);

        container.layout(0, 0, w, h);

        // F4: reuse streamBitmap across drawAndPush and drawOnly — only
        // reallocate when size changes. After final draw this bitmap is
        // handed to RemoteViews (which copies it into a PendingIntent for
        // widget update), so we can keep using it for subsequent draws.
        if (streamBitmap == null
                || streamBitmap.getWidth() != w
                || streamBitmap.getHeight() != h
                || streamBitmap.isRecycled()) {
            if (streamBitmap != null && !streamBitmap.isRecycled()) {
                streamBitmap.recycle();
            }
            streamBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Log.d(TAG, "drawAndPush: allocated new bitmap " + w + "x" + h);
        } else {
            streamBitmap.eraseColor(android.graphics.Color.WHITE);
        }

        Canvas canvas = new Canvas(streamBitmap);
        canvas.drawColor(android.graphics.Color.WHITE);
        container.draw(canvas);
        Log.d(TAG, "Bitmap: " + w + "x" + h + ", bytes=" + streamBitmap.getByteCount());

        pushBitmapToWidget(streamBitmap, title);
    }

    private void pushBitmapToWidget(Bitmap bitmap, String title) {
        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.a2ui_widget_content);

        views.setTextViewText(R.id.widgetTitle, title);

        if (bitmap.getByteCount() > 800_000) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
            byte[] compressed = baos.toByteArray();
            Bitmap smaller = android.graphics.BitmapFactory.decodeByteArray(compressed, 0, compressed.length);
            if (smaller != null) {
                views.setImageViewBitmap(R.id.widgetImageView, smaller);
            }
        } else {
            views.setImageViewBitmap(R.id.widgetImageView, bitmap);
        }

        // Refresh button
        Intent refreshIntent = new Intent(this, A2UIWidgetProvider.class);
        refreshIntent.setAction(A2UIWidgetProvider.ACTION_REFRESH);
        refreshIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnRefresh,
                android.app.PendingIntent.getBroadcast(this, appWidgetId * 10 + 1, refreshIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                | android.app.PendingIntent.FLAG_IMMUTABLE));

        // Switch template button
        String currentTemplate = template != null ? template : WidgetProtocolTemplates.DEFAULT_TEMPLATE;
        String nextTemplate = WidgetProtocolTemplates.getNextTemplate(currentTemplate);
        Intent switchIntent = new Intent(this, A2UIWidgetProvider.class);
        switchIntent.setAction(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
        switchIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        switchIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, nextTemplate);
        views.setOnClickPendingIntent(R.id.btnSwitchTemplate,
                android.app.PendingIntent.getBroadcast(this, appWidgetId * 10 + 2, switchIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                | android.app.PendingIntent.FLAG_IMMUTABLE));

        // Template buttons
        String[] templates = WidgetProtocolTemplates.AVAILABLE_TEMPLATES;
        int[] buttonIds = {R.id.btnTemplateWeather, R.id.btnTemplateAgenda, R.id.btnTemplateTodo};
        for (int i = 0; i < templates.length; i++) {
            Intent tmplIntent = new Intent(this, A2UIWidgetProvider.class);
            tmplIntent.setAction(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
            tmplIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
            tmplIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, templates[i]);
            views.setOnClickPendingIntent(buttonIds[i],
                    android.app.PendingIntent.getBroadcast(this, appWidgetId * 10 + 3 + i, tmplIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                    | android.app.PendingIntent.FLAG_IMMUTABLE));
            int color = templates[i].equals(currentTemplate) ? 0xFF6200EE : 0xFF666666;
            views.setTextColor(buttonIds[i], color);
        }

        // AI input button
        Intent aiInputIntent = new Intent(this, A2UIWidgetProvider.class);
        aiInputIntent.setAction(A2UIWidgetProvider.ACTION_AI_INPUT);
        aiInputIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnAiInput,
                android.app.PendingIntent.getBroadcast(this, appWidgetId * 10 + 4, aiInputIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT
                                | android.app.PendingIntent.FLAG_IMMUTABLE));

        awm.updateAppWidget(appWidgetId, views);
        Log.d(TAG, "Widget updated: " + title);
    }

    // ===== Surface listener =====

    private void registerSurfaceListener(SurfaceManager sm,
                                         final AtomicReference<Surface> surfaceRef,
                                         final CountDownLatch surfaceCreated) {
        sm.addListener(new ISurfaceManagerListener() {
            @Override
            public void onCreateSurface(Surface surface) {
                Log.d(TAG, "onCreateSurface: " + surface.getSurfaceId());
                surfaceRef.set(surface);
                surfaceCreated.countDown();
            }

            @Override
            public void onDeleteSurface(Surface surface) {}

            @Override
            public void onReceiveActionEvent(String event) {}

            @Override
            public void onRootComponentUpdate(Surface surface, Map<String, String> props) {}

            @Override
            public void onError(Surface surface, int code, String message) {
                Log.e(TAG, "Surface error: code=" + code + ", msg=" + message);
                surfaceCreated.countDown();
            }

            @Override
            public void onBlankCheckResult(Surface surface, boolean isBlank) {
                Log.d(TAG, "Blank check: " + isBlank);
            }

            @Override
            public void onComponentAppeared(Surface surface, String parentComponentId,
                                             String parentType, Map<String, Object> properties) {}

            @Override
            public SurfaceSize surfaceSize(String sid) {
                // F3: return widget's actual size in pixels — SurfaceSize
                // constructor will convert px → a2ui vp internally via
                // StyleHelper.pxToA2ui (px / density * 2).
                int[] wh = getWidgetSizePx();
                int widthPx = wh[0];
                int heightPx = wh[1];
                Log.d(TAG, "surfaceSize: " + sid + " → " + widthPx + "x" + heightPx + "px");
                return new SurfaceSize(widthPx, heightPx);
            }
        });
    }

    /**
     * F3: Returns the widget's actual size in screen pixels using
     * {@link AppWidgetManager#getAppWidgetOptions(int)}.
     *
     * The returned Bundle has portrait/landscape keys depending on orientation:
     * - OPTION_APPWIDGET_MIN_WIDTH / OPTION_APPWIDGET_MAX_WIDTH
     * - OPTION_APPWIDGET_MIN_HEIGHT / OPTION_APPWIDGET_MAX_HEIGHT
     *
     * We use the smaller of portrait width vs. landscape width as the
     * target width (since the widget host cell is generally the limiting
     * dimension), clamped to [280, 400].
     *
     * @return int[2] = {widthPx, heightPx}. Falls back to {300, 0} if the
     *         AppWidgetManager / options are unavailable.
     */
    private int[] getWidgetSizePx() {
        int widthPx = 300;  // sensible fallback
        int heightPx = 0;   // 0 = no constraint on height
        try {
            AppWidgetManager awm = AppWidgetManager.getInstance(this);
            if (awm == null) return new int[]{widthPx, heightPx};
            android.os.Bundle options = awm.getAppWidgetOptions(appWidgetId);
            if (options == null) return new int[]{widthPx, heightPx};

            int portraitMinW = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
            int portraitMaxW = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
            int portraitMinH = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            int portraitMaxH = options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);

            // The portrait min width is the most common "current width"
            // reported by the host. Use it; fall back to max if absent.
            int candidateW = portraitMinW > 0 ? portraitMinW
                    : (portraitMaxW > 0 ? portraitMaxW : widthPx);
            int candidateH = portraitMinH > 0 ? portraitMinH
                    : (portraitMaxH > 0 ? portraitMaxH : heightPx);

            // Clamp width to [min, max]
            if (candidateW < WIDGET_MIN_WIDTH_PX) candidateW = WIDGET_MIN_WIDTH_PX;
            if (candidateW > WIDGET_MAX_WIDTH_PX) candidateW = WIDGET_MAX_WIDTH_PX;

            widthPx = candidateW;
            heightPx = candidateH;
        } catch (Exception e) {
            Log.w(TAG, "getWidgetSizePx failed, using fallback 300x0", e);
        }
        return new int[]{widthPx, heightPx};
    }

    /**
     * F3: Returns the target width in screen pixels for measuring the surface
     * container. Cached after the first call within an Activity instance.
     */
    private int getWidgetTargetWidthPx() {
        if (widgetTargetWidthPx <= 0) {
            widgetTargetWidthPx = getWidgetSizePx()[0];
        }
        return widgetTargetWidthPx;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(refreshRunnable);
        // F4: recycle the reusable bitmap to avoid OOM on repeated generations.
        try {
            if (streamBitmap != null && !streamBitmap.isRecycled()) {
                streamBitmap.recycle();
                streamBitmap = null;
                Log.d(TAG, "onDestroy: streamBitmap recycled");
            }
        } catch (Exception e) {
            Log.w(TAG, "onDestroy: bitmap recycle failed", e);
        }
        try {
            if (surfaceManager != null) {
                surfaceManager.destroy();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to destroy surfaceManager", e);
        }
    }
}
