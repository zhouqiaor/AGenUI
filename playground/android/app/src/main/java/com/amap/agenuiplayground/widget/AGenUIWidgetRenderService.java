package com.amap.agenuiplayground.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

import com.amap.agenui.AGenUI;
import com.amap.agenui.render.surface.ISurfaceManagerListener;
import com.amap.agenui.render.surface.Surface;
import com.amap.agenui.render.surface.SurfaceManager;
import com.amap.agenui.render.surface.SurfaceSize;
import com.amap.agenuiplayground.R;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core rendering orchestrator for A2UI widget content.
 *
 * <p>This class coordinates the rendering pipeline:
 * <ol>
 *   <li>Load template JSON from assets (via {@link WidgetProtocolTemplates})</li>
 *   <li>Initialize AGenUI engine + {@link SurfaceManager}</li>
 *   <li>Stream template JSON chunks</li>
 *   <li>Wait for surface creation + root component mount</li>
 *   <li>Draw to Bitmap (via {@link WidgetBitmapRenderer})</li>
 *   <li>Push to widget via RemoteViews (with button wiring via
 *       {@link WidgetButtonWiring})</li>
 * </ol>
 *
 * <p>Extracted concerns:
 * <ul>
 *   <li>{@link WidgetBitmapRenderer} — View → Bitmap drawing pipeline</li>
 *   <li>{@link WidgetButtonWiring} — RemoteViews PendingIntent wiring</li>
 *   <li>{@link WidgetComponentFilter} — Agenda component tree filtering</li>
 *   <li>{@link WidgetBitmapCache} — LRU bitmap cache</li>
 *   <li>{@link WidgetSurfacePool} — SurfaceManager pool</li>
 * </ul>
 *
 * <p>Bypasses Android 12+ Background Activity Launch (BAL) restriction by
 * rendering AGenUI → Bitmap → RemoteViews directly, without launching
 * WidgetRenderActivity.
 */
public class AGenUIWidgetRenderService extends JobIntentService {

    private static final String TAG = "AGenUIWidgetRenderSvc";
    private static final int JOB_ID = 1001;
    private static final long SURFACE_TIMEOUT_MS = 5000;

    private static volatile HandlerThread sRenderThread;
    private static volatile Handler sRenderHandler;

    // ===== Public API =====

    public static void startRender(Context context, int appWidgetId, String template) {
        Intent intent = new Intent(context, AGenUIWidgetRenderService.class);
        intent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, template);
        enqueueWork(context, AGenUIWidgetRenderService.class, JOB_ID, intent);
    }

    /**
     * Direct render entry point — can be called from A2UIWidgetProvider.onReceive()
     * using a background HandlerThread, bypassing JobScheduler delays entirely.
     */
    public static void renderAsync(Context context, int appWidgetId, String template) {
        ensureRenderThread();
        sRenderHandler.post(() -> renderSync(context, appWidgetId, template));
    }

    /**
     * Prerenders all available widget templates (default view) into the bitmap
     * cache on a background thread. Called on first widget bind so that
     * subsequent renders are instant cache hits.
     */
    public static void prerenderAll(Context context) {
        ensureRenderThread();
        final Context app = context.getApplicationContext();
        sRenderHandler.post(() -> {
            Log.d(TAG, "prerenderAll: starting for "
                    + WidgetProtocolTemplates.AVAILABLE_TEMPLATES.length + " templates");
            for (String tpl : WidgetProtocolTemplates.AVAILABLE_TEMPLATES) {
                String key = WidgetBitmapCache.buildKey(tpl, "default");
                if (WidgetBitmapCache.get(key) != null) continue;
                try {
                    renderSync(app, -1, tpl);
                } catch (Exception e) {
                    Log.w(TAG, "prerender failed for " + tpl, e);
                }
            }
            Log.d(TAG, "prerenderAll: done, cache size=" + WidgetBitmapCache.size());
        });
    }

    // ===== Rendering pipeline =====

    /**
     * Synchronous render — runs on background thread, blocks until done.
     */
    private static void renderSync(Context context, int appWidgetId, String template) {
        if (template == null) template = WidgetProtocolTemplates.DEFAULT_TEMPLATE;
        long renderStart = System.currentTimeMillis();
        Log.d(TAG, "renderSync: id=" + appWidgetId + ", template=" + template);

        // Resolve actual widget dimensions (Android 12+ API or default)
        WidgetSizeDetector.WidgetDimensions dims = WidgetSizeDetector.resolve(context, appWidgetId);
        Log.d(TAG, "Widget dimensions: " + dims);
        int renderWidth = dims.width;
        int renderHeight = dims.height;

        // Step 0: Bitmap cache hit → skip the full render pipeline and push directly.
        // Include dimensions in cache key so different-sized widgets don't share bitmaps
        String cacheKey = WidgetBitmapCache.buildKey(template, "default") + "_" + dims.width + "x" + dims.height;
        Bitmap cached = WidgetBitmapCache.get(cacheKey);
        if (cached != null) {
            Log.d(TAG, "Bitmap cache HIT: " + cacheKey + " → pushing directly");
            String title = "AGenUI · " + template;
            pushBitmapToWidget(context, appWidgetId, cached, title, template);
            WidgetRenderMetrics.recordRender(template, System.currentTimeMillis() - renderStart, true);
            return;
        }

        // Step 1: Load template JSON
        String surfaceId = "widget_" + appWidgetId + "_" + System.currentTimeMillis();
        String templateJson = WidgetProtocolTemplates.loadTemplate(context, template, surfaceId);
        if (templateJson == null) {
            Log.e(TAG, "Template not found: " + template);
            pushErrorWidget(context, appWidgetId, "模板加载失败", template);
            return;
        }

        // Validate template JSON structure before feeding to engine
        WidgetTemplateValidator.ValidationResult validation =
                WidgetTemplateValidator.validate(templateJson);
        if (!validation.valid) {
            Log.e(TAG, "Template validation failed for " + template + ": " + validation.error);
            pushErrorWidget(context, appWidgetId, "模板格式错误", template);
            return;
        }

        // For the agenda template, optionally trim the weekly meeting blocks
        // based on the persisted view mode.
        if ("agenda".equals(template) && appWidgetId >= 0) {
            templateJson = WidgetComponentFilter.filterAgenda(context, appWidgetId, templateJson);
        }

        // Convert Phase 1 template (type format) to version format (v0.9) for AGenUI engine
        List<String> versionChunks = WidgetFallbackBuilder.convertToVersionFormat(templateJson, surfaceId);
        if (versionChunks.isEmpty()) {
            Log.e(TAG, "Template conversion failed: " + template);
            pushErrorWidget(context, appWidgetId, "模板转换失败", template);
            return;
        }
        Log.d(TAG, "Converted " + versionChunks.size() + " chunks for template: " + template);

        // Step 2: Initialize AGenUI engine
        try {
            AGenUI.getInstance().initialize(context.getApplicationContext());
            AGenUI.getInstance().setDebug(true);
        } catch (Exception e) {
            Log.e(TAG, "AGenUI init failed", e);
            pushErrorWidget(context, appWidgetId, "引擎初始化失败", template);
            return;
        }

        // Step 3: Acquire a SurfaceManager — prefer the pool so we can reuse
        // an already-initialized SM (skip surface creation) for this template.
        SurfaceManager surfaceManager = WidgetSurfacePool.acquire(template);
        final boolean reused = (surfaceManager != null);
        if (!reused) {
            surfaceManager = new SurfaceManager(context);
        }
        Log.d(TAG, reused ? "Reusing SurfaceManager from pool"
                : "Created new SurfaceManager");

        final SurfaceRenderResult result = renderSurface(
                context, appWidgetId, template, surfaceManager, reused, versionChunks);

        if (result.bitmap != null) {
            // P0 safety: remove any stale entry before putting the new bitmap
            WidgetBitmapCache.remove(cacheKey);
            WidgetBitmapCache.put(cacheKey, result.bitmap);
            String title = "AGenUI · " + template;
            pushBitmapToWidget(context, appWidgetId, result.bitmap, title, template);
            Log.d(TAG, "Widget updated: " + title);
        } else {
            pushErrorWidget(context, appWidgetId, result.errorMessage != null
                    ? result.errorMessage : "截图失败", template);
        }

        cleanup(surfaceManager, template);
        WidgetRenderMetrics.recordRender(template, System.currentTimeMillis() - renderStart, false);
    }

    /**
     * Executes the surface creation + drawing phase.
     *
     * @return A result object containing either a Bitmap or an error message.
     */
    private static SurfaceRenderResult renderSurface(
            Context context, int appWidgetId, String template,
            SurfaceManager surfaceManager, boolean reused,
            List<String> versionChunks) {

        final CountDownLatch surfaceCreated = reused ? null : new CountDownLatch(1);
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>(null);
        final CountDownLatch rootComponentReady = new CountDownLatch(1);

        surfaceManager.addListener(new ISurfaceManagerListener() {
            @Override
            public void onCreateSurface(Surface surface) {
                Log.d(TAG, "onCreateSurface: " + surface.getSurfaceId());
                surfaceRef.set(surface);
                if (surfaceCreated != null) surfaceCreated.countDown();
            }

            @Override
            public void onDeleteSurface(Surface surface) {}

            @Override
            public void onReceiveActionEvent(String event) {}

            @Override
            public void onRootComponentUpdate(Surface surface, Map<String, String> props) {
                Log.d(TAG, "onRootComponentUpdate: " + surface.getSurfaceId() + ", props=" + props);
                surfaceRef.set(surface);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    rootComponentReady.countDown();
                }, 100);
            }

            @Override
            public void onError(Surface surface, int code, String message) {
                Log.e(TAG, "Surface error: code=" + code + ", msg=" + message);
                if (surfaceCreated != null) surfaceCreated.countDown();
                rootComponentReady.countDown();
            }

            @Override
            public void onBlankCheckResult(Surface surface, boolean isBlank) {}

            @Override
            public void onComponentAppeared(Surface surface, String parentComponentId,
                                             String parentType, Map<String, Object> properties) {
                Log.d(TAG, "onComponentAppeared: parent=" + parentComponentId
                        + ", type=" + parentType);
            }

            @Override
            public SurfaceSize surfaceSize(String sid) {
                return new SurfaceSize(renderWidth, renderHeight);
            }
        });

        // Stream protocol chunks
        try {
            surfaceManager.beginTextStream();
            for (String chunk : versionChunks) {
                surfaceManager.receiveTextChunk(chunk);
            }
            surfaceManager.endTextStream();
        } catch (Exception e) {
            Log.e(TAG, "Failed to stream protocol", e);
            return SurfaceRenderResult.error("协议流失败");
        }

        // Wait for surface creation + root component mount
        try {
            if (!reused) {
                boolean created = surfaceCreated.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (!created || surfaceRef.get() == null) {
                    Log.e(TAG, "Surface creation timeout");
                    return SurfaceRenderResult.error("渲染超时");
                }
            }

            boolean mounted = rootComponentReady.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!mounted) {
                Log.w(TAG, "Root component mount timeout — drawing with whatever is available");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for surface", e);
            return SurfaceRenderResult.error("渲染中断");
        }

        Log.d(TAG, "Surface ready, proceeding to draw");

        // Draw on main thread → Bitmap
        final Surface surface = surfaceRef.get();
        final Bitmap[] bitmapResult = new Bitmap[1];
        final CountDownLatch drawDone = new CountDownLatch(1);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                bitmapResult[0] = WidgetBitmapRenderer.drawSurfaceToBitmap(
                        context, surface, renderWidth, renderHeight);
            } catch (Exception e) {
                Log.e(TAG, "Draw failed", e);
            } finally {
                drawDone.countDown();
            }
        });

        try {
            drawDone.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for draw", e);
        }

        if (bitmapResult[0] != null) {
            return SurfaceRenderResult.success(bitmapResult[0]);
        }
        return SurfaceRenderResult.error("截图失败");
    }

    // ===== Widget push helpers =====

    /**
     * Pushes a Bitmap to the widget via RemoteViews with full button wiring.
     */
    private static void pushBitmapToWidget(Context context, int appWidgetId,
                                           Bitmap bitmap, String title, String currentTemplate) {
        if (appWidgetId < 0) return; // prerender pass — no widget to push to
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        RemoteViews views = WidgetRemoteViewsPool.obtainWidgetLayout(context);

        views.setTextViewText(R.id.widgetTitle, title);
        WidgetStateController.setState(views, WidgetStateController.STATE_CONTENT);

        // Compress if too large for Binder transaction (800KB limit)
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

        WidgetButtonWiring.wireAll(context, views, appWidgetId, currentTemplate);
        awm.updateAppWidget(appWidgetId, views);
    }

    /**
     * Pushes an error state to the widget.
     *
     * <p><b>Last-good fallback (industry best practice):</b> Before showing the error,
     * checks if a cached bitmap exists for this template+dimensions. If found, shows
     * the last successful render with a subtle error indicator, rather than replacing
     * useful content with a blank error page.
     */
    private static void pushErrorWidget(Context context, int appWidgetId,
                                        String errorMessage, String currentTemplate) {
        if (appWidgetId < 0) return; // prerender pass — no widget to push to

        // Try to show last successful bitmap instead of blank error
        WidgetSizeDetector.WidgetDimensions dims = WidgetSizeDetector.resolve(context, appWidgetId);
        String cacheKey = WidgetBitmapCache.buildKey(currentTemplate, "default")
                + "_" + dims.width + "x" + dims.height;
        Bitmap lastGood = WidgetBitmapCache.get(cacheKey);
        if (lastGood != null) {
            Log.d(TAG, "Render failed but showing last good bitmap from cache: " + cacheKey);
            String title = "AGenUI · " + currentTemplate + " · ⚠";
            pushBitmapToWidget(context, appWidgetId, lastGood, title, currentTemplate);
            return;
        }

        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        RemoteViews views = WidgetRemoteViewsPool.obtainWidgetLayout(context);
        views.setTextViewText(R.id.widgetTitle, "AGenUI · " + errorMessage);
        WidgetStateController.setError(views, errorMessage);

        WidgetButtonWiring.wireRefreshOnly(context, views, appWidgetId);

        awm.updateAppWidget(appWidgetId, views);
    }

    // ===== Thread management =====

    private static synchronized void ensureRenderThread() {
        if (sRenderThread == null || !sRenderThread.isAlive()) {
            sRenderThread = new HandlerThread("AGenUI-WidgetRender");
            sRenderThread.start();
            sRenderHandler = new Handler(sRenderThread.getLooper());
        }
    }

    /**
     * Returns a SurfaceManager to the pool (for later reuse) instead of destroying it.
     * Falls back to destroy if the pool is full.
     */
    private static void cleanup(SurfaceManager surfaceManager, String template) {
        WidgetSurfacePool.release(template, surfaceManager);
    }

    // ===== JobIntentService entry (for backward compat) =====

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        int appWidgetId = intent.getIntExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, -1);
        String template = intent.getStringExtra(A2UIWidgetProvider.EXTRA_TEMPLATE);
        if (appWidgetId < 0) {
            Log.w(TAG, "Invalid appWidgetId, skipping");
            return;
        }
        renderSync(this, appWidgetId, template);
    }

    // ===== Internal result type =====

    /**
     * Immutable result of the surface rendering phase.
     * Contains either a successful Bitmap or an error message.
     */
    private static final class SurfaceRenderResult {
        final Bitmap bitmap;
        final String errorMessage;

        private SurfaceRenderResult(Bitmap bitmap, String errorMessage) {
            this.bitmap = bitmap;
            this.errorMessage = errorMessage;
        }

        static SurfaceRenderResult success(Bitmap bitmap) {
            return new SurfaceRenderResult(bitmap, null);
        }

        static SurfaceRenderResult error(String message) {
            return new SurfaceRenderResult(null, message);
        }
    }
}
