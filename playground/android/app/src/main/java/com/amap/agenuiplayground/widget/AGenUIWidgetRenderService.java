package com.amap.agenuiplayground.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.JobIntentService;

/**
 * Orchestrates widget rendering by launching WidgetRenderActivity via PendingIntent.
 *
 * Uses PendingIntent.send() to bypass Android 10+ Background Activity Launch (BAL) restriction.
 */
public class AGenUIWidgetRenderService extends JobIntentService {

    private static final String TAG = "AGenUIWidgetRenderSvc";
    private static final int JOB_ID = 1001;

    public static void startRender(Context context, int appWidgetId, String template) {
        Intent intent = new Intent(context, AGenUIWidgetRenderService.class);
        intent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        intent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, template);
        enqueueWork(context, AGenUIWidgetRenderService.class, JOB_ID, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        int appWidgetId = intent.getIntExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, -1);
        String template = intent.getStringExtra(A2UIWidgetProvider.EXTRA_TEMPLATE);
        if (appWidgetId < 0) {
            Log.w(TAG, "Invalid appWidgetId, skipping");
            return;
        }
        if (template == null) template = WidgetProtocolTemplates.DEFAULT_TEMPLATE;

        Log.d(TAG, "onHandleWork: id=" + appWidgetId + ", template=" + template);

        Intent renderIntent = new Intent(this, WidgetRenderActivity.class);
        renderIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        renderIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        renderIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, template);

        try {
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(this, appWidgetId, renderIntent, flags);
            pi.send();
            Log.d(TAG, "Launched WidgetRenderActivity via PendingIntent for widget " + appWidgetId);
        } catch (PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed to launch WidgetRenderActivity", e);
        }
    }
}
