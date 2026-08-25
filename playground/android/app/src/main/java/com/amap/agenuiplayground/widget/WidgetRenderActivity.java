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

        client.streamChat(WidgetPromptBuilder.SYSTEM_PROMPT, userText,
                new WidgetLLMClient.StreamCallback() {
                    @Override
                    public void onChunk(String delta) {
                        if (delta == null || delta.isEmpty()) return;
                        fullContent.append(delta);
                        try {
                            List<String> jsonObjects = partialParser.feed(delta);
                            for (String json : jsonObjects) {
                                surfaceManager.receiveTextChunk(json);
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
     * Fallback: keyword-matched template or notecard.
     * Uses WidgetFallbackBuilder to send version-format JSON to the existing SurfaceManager.
     */
    private void loadFallback() {
        Log.d(TAG, "Loading fallback template");

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
        if (streamSurface == null) return;
        try {
            drawOnly(streamSurface);
        } catch (Exception e) {
            Log.w(TAG, "doRefresh draw failed", e);
        }
    }

    /**
     * Draws the surface to a bitmap and pushes to widget (no title update).
     * Used during streaming refresh.
     */
    private void drawOnly(Surface surface) {
        View container = surface.getContainer();
        if (container == null) return;

        int widthSpec = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        container.measure(widthSpec, heightSpec);

        int w = container.getMeasuredWidth();
        int h = container.getMeasuredHeight();
        if (h <= 0) h = 200;

        container.layout(0, 0, w, h);

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.WHITE);
        container.draw(canvas);

        pushBitmapToWidget(bitmap, "AGenUI · 生成中...");
    }

    // ===== Common draw + push =====

    private void drawAndPush(Surface surface, String title, boolean isFallback) {
        View container = surface.getContainer();
        if (container == null) {
            Log.e(TAG, "Surface container is null");
            return;
        }

        int widthSpec = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        container.measure(widthSpec, heightSpec);

        int w = container.getMeasuredWidth();
        int h = container.getMeasuredHeight();
        if (h <= 0) h = 200;
        Log.d(TAG, "Measured: " + w + "x" + h);

        container.layout(0, 0, w, h);

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.WHITE);
        container.draw(canvas);
        Log.d(TAG, "Bitmap: " + w + "x" + h + ", bytes=" + bitmap.getByteCount());

        pushBitmapToWidget(bitmap, title);
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
                return new SurfaceSize(300, 0);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        refreshHandler.removeCallbacks(refreshRunnable);
        try {
            if (surfaceManager != null) {
                surfaceManager.destroy();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to destroy surfaceManager", e);
        }
    }
}
