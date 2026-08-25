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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Transparent Activity that hosts SurfaceManager for widget rendering.
 * Creates SurfaceManager, streams A2UI protocol, draws to Bitmap, pushes to RemoteViews.
 */
public class WidgetRenderActivity extends Activity {

    private static final String TAG = "WidgetRenderActivity";
    private static final long SURFACE_TIMEOUT_MS = 5000;

    private SurfaceManager surfaceManager;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private String template;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        appWidgetId = intent.getIntExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        template = intent.getStringExtra(A2UIWidgetProvider.EXTRA_TEMPLATE);
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || template == null) {
            Log.e(TAG, "Missing appWidgetId or template");
            finish();
            return;
        }
        if (template.isEmpty()) template = WidgetProtocolTemplates.DEFAULT_TEMPLATE;

        Log.d(TAG, "onCreate: id=" + appWidgetId + ", template=" + template);

        AGenUI.getInstance().initialize(getApplicationContext());
        AGenUI.getInstance().setDebug(true);

        String surfaceId = "widget_" + appWidgetId + "_" + System.currentTimeMillis();
        String templateJson = WidgetProtocolTemplates.loadTemplate(this, template, surfaceId);
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

        final CountDownLatch surfaceCreated = new CountDownLatch(1);
        final AtomicReference<Surface> surfaceRef = new AtomicReference<>(null);

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
            public void onComponentAppeared(Surface surface, String parentComponentId, String parentType, Map<String, Object> properties) {}

            @Override
            public SurfaceSize surfaceSize(String surfaceId) {
                return new SurfaceSize(300, 0);
            }
        });

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
                        drawAndPush(surface);
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

    private void drawAndPush(Surface surface) {
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

        pushToWidget(bitmap);
    }

    private void pushToWidget(Bitmap bitmap) {
        AppWidgetManager awm = AppWidgetManager.getInstance(this);
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.a2ui_widget_content);

        views.setTextViewText(R.id.widgetTitle, "AGenUI · " + template);

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

        Intent refreshIntent = new Intent(this, A2UIWidgetProvider.class);
        refreshIntent.setAction(A2UIWidgetProvider.ACTION_REFRESH);
        refreshIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnRefresh,
                android.app.PendingIntent.getBroadcast(this, appWidgetId * 10 + 1, refreshIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));

        String nextTemplate = WidgetProtocolTemplates.getNextTemplate(template);
        Intent switchIntent = new Intent(this, A2UIWidgetProvider.class);
        switchIntent.setAction(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
        switchIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        switchIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, nextTemplate);
        views.setOnClickPendingIntent(R.id.btnSwitchTemplate,
                android.app.PendingIntent.getBroadcast(this, appWidgetId * 10 + 2, switchIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));

        String[] templates = WidgetProtocolTemplates.AVAILABLE_TEMPLATES;
        int[] buttonIds = {R.id.btnTemplateWeather, R.id.btnTemplateAgenda, R.id.btnTemplateTodo};
        for (int i = 0; i < templates.length; i++) {
            Intent tmplIntent = new Intent(this, A2UIWidgetProvider.class);
            tmplIntent.setAction(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
            tmplIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
            tmplIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, templates[i]);
            views.setOnClickPendingIntent(buttonIds[i],
                    android.app.PendingIntent.getBroadcast(this, appWidgetId * 10 + 3 + i, tmplIntent,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE));
            int color = templates[i].equals(template) ? 0xFF6200EE : 0xFF666666;
            views.setTextColor(buttonIds[i], color);
        }

        awm.updateAppWidget(appWidgetId, views);
        Log.d(TAG, "Widget updated successfully");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (surfaceManager != null) {
                surfaceManager.destroy();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to destroy surfaceManager", e);
        }
    }
}
