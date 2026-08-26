package com.amap.agenuiplayground.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.amap.agenuiplayground.R;

/**
 * A2UI Desktop Widget Provider.
 * Receives widget lifecycle callbacks and dispatches rendering to AGenUIWidgetRenderService.
 *
 * Actions: ACTION_REFRESH, ACTION_SWITCH_TEMPLATE
 */
public class A2UIWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "A2UIWidgetProvider";

    public static final String ACTION_REFRESH = "com.amap.agenuiplayground.widget.ACTION_REFRESH";
    public static final String ACTION_SWITCH_TEMPLATE = "com.amap.agenuiplayground.widget.ACTION_SWITCH_TEMPLATE";
    public static final String ACTION_AI_INPUT = "com.amap.agenuiplayground.widget.ACTION_AI_INPUT";
    public static final String ACTION_QUICK_JOIN = "com.amap.agenuiplayground.widget.ACTION_QUICK_JOIN";
    public static final String EXTRA_APPWIDGET_ID = "appWidgetId";
    public static final String EXTRA_TEMPLATE = "template";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate: " + appWidgetIds.length + " widgets");
        // Warm the bitmap cache on first bind so subsequent renders are instant.
        if (appWidgetIds.length > 0) {
            AGenUIWidgetRenderService.prerenderAll(context);
        }
        for (int appWidgetId : appWidgetIds) {
            String template = WidgetConfig.getTemplate(context, appWidgetId);
            renderWidget(context, appWidgetId, template);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        Log.d(TAG, "onReceive: action=" + action);

        if (ACTION_REFRESH.equals(action)) {
            int appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                String template = WidgetConfig.getTemplate(context, appWidgetId);
                renderWidget(context, appWidgetId, template);
            }
        } else if (ACTION_SWITCH_TEMPLATE.equals(action)) {
            int appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            String template = intent.getStringExtra(EXTRA_TEMPLATE);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && template != null) {
                WidgetProtocolCache.saveTemplate(context, appWidgetId, template);
                renderWidget(context, appWidgetId, template);
            }
        } else if (ACTION_AI_INPUT.equals(action)) {
            int appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                launchInputActivity(context, appWidgetId);
            }
        } else if (ACTION_QUICK_JOIN.equals(action)) {
            int appWidgetId = intent.getIntExtra(EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            String template = intent.getStringExtra(EXTRA_TEMPLATE);
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                launchMeetingJoinActivity(context, appWidgetId, template);
            }
        }
    }

    private void launchInputActivity(Context context, int appWidgetId) {
        Log.d(TAG, "launchInputActivity: id=" + appWidgetId);
        Intent inputIntent = new Intent(context, WidgetInputActivity.class);
        inputIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        inputIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId);

        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                | android.app.PendingIntent.FLAG_IMMUTABLE;
        try {
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                    context, appWidgetId, inputIntent, flags);
            pi.send();
        } catch (android.app.PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed to launch WidgetInputActivity", e);
            // Fallback: direct start (may fail on Android 10+ from background)
            inputIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(inputIntent);
        }
    }

    /**
     * Launches the MeetingJoinActivity to simulate joining the meeting
     * associated with the given widget.
     */
    private void launchMeetingJoinActivity(Context context, int appWidgetId, String template) {
        Log.d(TAG, "launchMeetingJoinActivity: id=" + appWidgetId + ", template=" + template);
        Intent joinIntent = new Intent(context, MeetingJoinActivity.class);
        joinIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        joinIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId);
        if (template != null) joinIntent.putExtra(EXTRA_TEMPLATE, template);

        int flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT
                | android.app.PendingIntent.FLAG_IMMUTABLE;
        try {
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                    context, appWidgetId, joinIntent, flags);
            pi.send();
        } catch (android.app.PendingIntent.CanceledException e) {
            Log.e(TAG, "Failed to launch MeetingJoinActivity", e);
            joinIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(joinIntent);
        }
    }

    private void renderWidget(Context context, int appWidgetId, String template) {
        Log.d(TAG, "renderWidget: id=" + appWidgetId + ", template=" + template);
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.a2ui_widget_placeholder);
        awm.updateAppWidget(appWidgetId, views);
        // Direct async render — bypasses JobScheduler delays and BAL restriction
        AGenUIWidgetRenderService.renderAsync(context, appWidgetId, template);
    }

    public static void updateAllWidgets(Context context) {
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, A2UIWidgetProvider.class);
        int[] ids = awm.getAppWidgetIds(provider);
        if (ids.length > 0) {
            Intent intent = new Intent(context, A2UIWidgetProvider.class);
            intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            context.sendBroadcast(intent);
        }
    }
}
