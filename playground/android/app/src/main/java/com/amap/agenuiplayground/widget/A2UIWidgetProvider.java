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
    public static final String EXTRA_APPWIDGET_ID = "appWidgetId";
    public static final String EXTRA_TEMPLATE = "template";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate: " + appWidgetIds.length + " widgets");
        for (int appWidgetId : appWidgetIds) {
            String template = WidgetProtocolCache.getTemplate(context, appWidgetId);
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
                String template = WidgetProtocolCache.getTemplate(context, appWidgetId);
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
        }
    }

    private void launchInputActivity(Context context, int appWidgetId) {
        Log.d(TAG, "launchInputActivity: id=" + appWidgetId);
        // Android 10+ BAL 限制:不能从 broadcast receiver 直接启动 Activity。
        // 改用 ForegroundService 中介 — Service 中 startActivity 不受 BAL 限制。
        Intent svcIntent = new Intent(context, WidgetInputLaunchService.class);
        svcIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId);
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(svcIntent);
            } else {
                context.startService(svcIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WidgetInputLaunchService, fallback to direct start", e);
            // 降级:直接 start Activity(低版本可用)
            Intent inputIntent = new Intent(context, WidgetInputActivity.class);
            inputIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            inputIntent.putExtra(EXTRA_APPWIDGET_ID, appWidgetId);
            try {
                context.startActivity(inputIntent);
            } catch (Exception ex) {
                Log.e(TAG, "Direct startActivity also failed", ex);
            }
        }
    }

    private void renderWidget(Context context, int appWidgetId, String template) {
        Log.d(TAG, "renderWidget: id=" + appWidgetId + ", template=" + template);
        AppWidgetManager awm = AppWidgetManager.getInstance(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.a2ui_widget_placeholder);
        awm.updateAppWidget(appWidgetId, views);
        AGenUIWidgetRenderService.startRender(context, appWidgetId, template);
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
