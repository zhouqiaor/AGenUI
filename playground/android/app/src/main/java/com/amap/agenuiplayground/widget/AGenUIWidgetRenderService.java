package com.amap.agenuiplayground.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
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

import org.json.JSONArray;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Renders A2UI widget content directly in the Service (no Activity needed).
 *
 * Bypasses Android 12+ Background Activity Launch (BAL) restriction by
 * rendering AGenUI → Bitmap → RemoteViews directly, without launching
 * WidgetRenderActivity.
 *
 * Flow:
 * 1. Load template JSON from assets
 * 2. Initialize AGenUI engine + SurfaceManager (Context constructor)
 * 3. Stream template JSON chunks
 * 4. Wait for onCreateSurface callback
 * 5. On main thread: measure + layout + draw(Canvas) → Bitmap
 * 6. Push Bitmap to RemoteViews → updateAppWidget
 */
public class AGenUIWidgetRenderService extends JobIntentService {

    private static final String TAG = "AGenUIWidgetRenderSvc";
    private static final int JOB_ID = 1001;
    private static final long SURFACE_TIMEOUT_MS = 5000;
    private static final int WIDGET_WIDTH = 300;
    private static final int WIDGET_HEIGHT = 400;

    private static volatile HandlerThread sRenderThread;
    private static volatile Handler sRenderHandler;

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

    private static synchronized void ensureRenderThread() {
        if (sRenderThread == null || !sRenderThread.isAlive()) {
            sRenderThread = new HandlerThread("AGenUI-WidgetRender");
            sRenderThread.start();
            sRenderHandler = new Handler(sRenderThread.getLooper());
        }
    }

    /**
     * Synchronous render — runs on background thread, blocks until done.
     */
    private static void renderSync(Context context, int appWidgetId, String template) {
        if (template == null) template = WidgetProtocolTemplates.DEFAULT_TEMPLATE;
        Log.d(TAG, "renderSync: id=" + appWidgetId + ", template=" + template);

        // Step 1: Load template JSON
        String surfaceId = "widget_" + appWidgetId + "_" + System.currentTimeMillis();
        String templateJson = WidgetProtocolTemplates.loadTemplate(context, template, surfaceId);
        if (templateJson == null) {
            Log.e(TAG, "Template not found: " + template);
            pushErrorWidget(context, appWidgetId, "模板加载失败", template);
            return;
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

        // Step 3: Create SurfaceManager (Context constructor — no Activity needed)
        SurfaceManager surfaceManager = new SurfaceManager(context);
        final CountDownLatch surfaceCreated = new CountDownLatch(1);
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>(null);

        final CountDownLatch rootComponentReady = new CountDownLatch(1);

        surfaceManager.addListener(new ISurfaceManagerListener() {
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
            public void onRootComponentUpdate(Surface surface, Map<String, String> props) {
                Log.d(TAG, "onRootComponentUpdate: " + surface.getSurfaceId() + ", props=" + props);
                // Root component is mounted — wait a brief moment for child views to be created
                surfaceRef.set(surface);
                // Post to main thread to allow pending view-creation runnables to execute
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    rootComponentReady.countDown();
                }, 100);
            }

            @Override
            public void onError(Surface surface, int code, String message) {
                Log.e(TAG, "Surface error: code=" + code + ", msg=" + message);
                surfaceCreated.countDown();
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
                return new SurfaceSize(WIDGET_WIDTH, WIDGET_HEIGHT);
            }
        });

        try {
            surfaceManager.beginTextStream();
            for (String chunk : versionChunks) {
                surfaceManager.receiveTextChunk(chunk);
            }
            surfaceManager.endTextStream();
        } catch (Exception e) {
            Log.e(TAG, "Failed to stream protocol", e);
            pushErrorWidget(context, appWidgetId, "协议流失败", template);
            cleanup(surfaceManager);
            return;
        }

        // Step 4: Wait for surface creation + root component mount
        try {
            boolean created = surfaceCreated.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!created || surfaceRef.get() == null) {
                Log.e(TAG, "Surface creation timeout");
                pushErrorWidget(context, appWidgetId, "渲染超时", template);
                cleanup(surfaceManager);
                return;
            }

            // Wait for root component to be fully mounted (including child views)
            boolean mounted = rootComponentReady.await(SURFACE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            if (!mounted) {
                Log.w(TAG, "Root component mount timeout — drawing with whatever is available");
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "Interrupted waiting for surface", e);
            pushErrorWidget(context, appWidgetId, "渲染中断", template);
            cleanup(surfaceManager);
            return;
        }

        Log.d(TAG, "Surface ready, proceeding to draw");

        // Step 5: Draw on main thread → Bitmap
        final Surface surface = surfaceRef.get();
        final Bitmap[] bitmapResult = new Bitmap[1];
        final CountDownLatch drawDone = new CountDownLatch(1);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                bitmapResult[0] = drawSurfaceToBitmap(context, surface);
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

        // Step 6: Push to widget
        Bitmap bitmap = bitmapResult[0];
        if (bitmap != null) {
            String title = "AGenUI · " + template;
            pushBitmapToWidget(context, appWidgetId, bitmap, title, template);
            Log.d(TAG, "Widget updated: " + title);
        } else {
            pushErrorWidget(context, appWidgetId, "截图失败", template);
        }

        cleanup(surfaceManager);
    }

    /**
     * Draws the Surface's container View to a Bitmap via Canvas.
     * Must be called on the main thread.
     *
     * In Service context the View is never attached to a window, so we must:
     * 1. Manually measure + layout the container and all descendants
     * 2. Set up the View's drawing state (attach to a mock window)
     * 3. Use View.draw(Canvas) which traverses the full tree
     */
    private static Bitmap drawSurfaceToBitmap(Context context, Surface surface) {
        View container = surface.getContainer();
        if (container == null) {
            Log.e(TAG, "Surface container is null");
            return null;
        }

        // Force layout by invalidating
        container.forceLayout();

        int widthSpec = View.MeasureSpec.makeMeasureSpec(WIDGET_WIDTH, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(WIDGET_HEIGHT, View.MeasureSpec.AT_MOST);
        container.measure(widthSpec, heightSpec);

        int w = container.getMeasuredWidth();
        int h = container.getMeasuredHeight();
        if (h <= 0) h = WIDGET_HEIGHT;
        Log.d(TAG, "Measured: " + w + "x" + h);

        container.layout(0, 0, w, h);

        // Create bitmap and draw
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(android.graphics.Color.WHITE);

        // Manually draw each child since the container might not traverse
        // properly when not attached to a window
        drawViewTree(container, canvas);
        Log.d(TAG, "Bitmap: " + w + "x" + h + ", bytes=" + bitmap.getByteCount());

        // Debug: save bitmap to file for visual verification
        try {
            java.io.File outFile = new java.io.File(context.getExternalFilesDir(null), "widget_render_debug.png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            Log.d(TAG, "Debug bitmap saved: " + outFile.getAbsolutePath());

            // Dump view hierarchy for debugging
            String hierarchy = dumpViewHierarchy(container, 0);
            Log.d(TAG, "View hierarchy:\n" + hierarchy);
        } catch (Exception e) {
            Log.w(TAG, "Failed to save debug bitmap", e);
        }

        return bitmap;
    }

    /**
     * Recursively draws a View tree to a canvas.
     * Bypasses View's internal "skip draw if not attached" logic by calling
     * draw(Canvas) directly on each view.
     */
    private static void drawViewTree(View view, Canvas canvas) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return;
        }

        // Save canvas state
        int saveCount = canvas.save();

        // Translate to the view's position
        canvas.translate(view.getLeft() - (view.getParent() instanceof android.view.ViewGroup
                ? ((android.view.ViewGroup) view.getParent()).getScrollX() : 0),
                view.getTop() - (view.getParent() instanceof android.view.ViewGroup
                        ? ((android.view.ViewGroup) view.getParent()).getScrollY() : 0));

        // Clip to view bounds
        canvas.clipRect(0, 0, view.getWidth(), view.getHeight());

        // Draw this view
        view.draw(canvas);

        // Draw children if ViewGroup
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                drawViewTree(vg.getChildAt(i), canvas);
            }
        }

        canvas.restoreToCount(saveCount);
    }

    /**
     * Dumps view hierarchy for debugging blank-bitmap issue.
     */
    private static String dumpViewHierarchy(View view, int depth) {
        StringBuilder sb = new StringBuilder();
        String indent = "  ".repeat(depth);
        sb.append(indent).append(view.getClass().getSimpleName())
                .append(" [").append(view.getWidth()).append("x").append(view.getHeight()).append("]")
                .append(" measured=[").append(view.getMeasuredWidth()).append("x").append(view.getMeasuredHeight()).append("]")
                .append(" vis=").append(view.getVisibility())
                .append(" tag=").append(view.getTag());
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup vg = (android.view.ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                sb.append("\n").append(dumpViewHierarchy(vg.getChildAt(i), depth + 1));
            }
        }
        return sb.toString();
    }

    /**
     * Pushes a Bitmap to the widget via RemoteViews with full button wiring.
     */
    private static void pushBitmapToWidget(Context context, int appWidgetId,
                                           Bitmap bitmap, String title, String currentTemplate) {
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.a2ui_widget_content);

        views.setTextViewText(R.id.widgetTitle, title);

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

        wireButtons(context, views, appWidgetId, currentTemplate);
        awm.updateAppWidget(appWidgetId, views);
    }

    /**
     * Pushes an error state to the widget.
     */
    private static void pushErrorWidget(Context context, int appWidgetId,
                                        String errorMessage, String currentTemplate) {
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.a2ui_widget_content);
        views.setTextViewText(R.id.widgetTitle, "AGenUI · " + errorMessage);
        // For ImageView, use setImageViewResource or leave blank — setTextViewText won't work on ImageView
        views.setTextViewText(R.id.widgetImageView, null);

        // Wire refresh button even on error
        Intent refreshIntent = new Intent(context, A2UIWidgetProvider.class);
        refreshIntent.setAction(A2UIWidgetProvider.ACTION_REFRESH);
        refreshIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnRefresh,
                PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        awm.updateAppWidget(appWidgetId, views);
    }

    /**
     * Wires all widget buttons (refresh, switch template, template bar, AI input).
     */
    private static void wireButtons(Context context, RemoteViews views,
                                    int appWidgetId, String currentTemplate) {
        // Refresh button
        Intent refreshIntent = new Intent(context, A2UIWidgetProvider.class);
        refreshIntent.setAction(A2UIWidgetProvider.ACTION_REFRESH);
        refreshIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnRefresh,
                PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Switch template button
        String nextTemplate = WidgetProtocolTemplates.getNextTemplate(currentTemplate);
        Intent switchIntent = new Intent(context, A2UIWidgetProvider.class);
        switchIntent.setAction(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
        switchIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        switchIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, nextTemplate);
        views.setOnClickPendingIntent(R.id.btnSwitchTemplate,
                PendingIntent.getBroadcast(context, appWidgetId * 10 + 2, switchIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        // Template bar buttons — only wire as many buttons as the layout provides
        String[] templates = WidgetProtocolTemplates.AVAILABLE_TEMPLATES;
        int[] buttonIds = {R.id.btnTemplateWeather, R.id.btnTemplateAgenda, R.id.btnTemplateTodo};
        for (int i = 0; i < buttonIds.length && i < templates.length; i++) {
            Intent tmplIntent = new Intent(context, A2UIWidgetProvider.class);
            tmplIntent.setAction(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
            tmplIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
            tmplIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, templates[i]);
            views.setOnClickPendingIntent(buttonIds[i],
                    PendingIntent.getBroadcast(context, appWidgetId * 10 + 3 + i, tmplIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            int color = templates[i].equals(currentTemplate) ? 0xFF007DFF : 0xFF999999;
            views.setTextColor(buttonIds[i], color);
        }

        // AI input button
        Intent aiInputIntent = new Intent(context, A2UIWidgetProvider.class);
        aiInputIntent.setAction(A2UIWidgetProvider.ACTION_AI_INPUT);
        aiInputIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnAiInput,
                PendingIntent.getBroadcast(context, appWidgetId * 10 + 4, aiInputIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static void cleanup(SurfaceManager surfaceManager) {
        try {
            if (surfaceManager != null) {
                surfaceManager.destroy();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to destroy surfaceManager", e);
        }
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
}
