package com.amap.agenuiplayground.widget;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.amap.agenuiplayground.R;

/**
 * Wires all widget button PendingIntents (refresh, switch template, template
 * bar, AI input, quick join, view toggles).
 *
 * <p>Extracted from {@link AGenUIWidgetRenderService} to isolate RemoteViews
 * event-binding logic from the rendering pipeline.
 */
public final class WidgetButtonWiring {

    private static final String TAG = "WidgetButtonWiring";

    private WidgetButtonWiring() { } // utility class

    /**
     * Wires all widget buttons for the given RemoteViews.
     *
     * @param context        Application context
     * @param views          RemoteViews to wire buttons into
     * @param appWidgetId    Widget ID
     * @param currentTemplate Currently active template name
     */
    public static void wireAll(Context context, RemoteViews views,
                                int appWidgetId, String currentTemplate) {
        wireRefresh(context, views, appWidgetId);
        wireSwitchTemplate(context, views, appWidgetId, currentTemplate);
        wireTemplateBar(context, views, appWidgetId, currentTemplate);
        wireAiInput(context, views, appWidgetId);
        wireQuickJoin(context, views, appWidgetId, currentTemplate);
    }

    /**
     * Wires only the refresh button (used for error states).
     */
    public static void wireRefreshOnly(Context context, RemoteViews views, int appWidgetId) {
        wireRefresh(context, views, appWidgetId);
    }

    // ---- Individual button wiring methods ----

    private static void wireRefresh(Context context, RemoteViews views, int appWidgetId) {
        Intent refreshIntent = new Intent(context, A2UIWidgetProvider.class);
        refreshIntent.setAction(A2UIWidgetProvider.ACTION_REFRESH);
        refreshIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnRefresh,
                PendingIntent.getBroadcast(context, appWidgetId * 10 + 1, refreshIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static void wireSwitchTemplate(Context context, RemoteViews views,
                                            int appWidgetId, String currentTemplate) {
        // Launch the template list picker Activity instead of cycling to next.
        // This gives the user direct random access to all 10 templates.
        Intent listIntent = new Intent(context, WidgetTemplateListActivity.class);
        listIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        listIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnSwitchTemplate,
                PendingIntent.getActivity(context, appWidgetId * 10 + 2, listIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static void wireTemplateBar(Context context, RemoteViews views,
                                         int appWidgetId, String currentTemplate) {
        String[] templates = WidgetProtocolTemplates.AVAILABLE_TEMPLATES;
        int[] buttonIds = WidgetProtocolTemplates.TEMPLATE_BUTTON_IDS;
        for (int i = 0; i < buttonIds.length && i < templates.length; i++) {
            Intent tmplIntent = new Intent(context, A2UIWidgetProvider.class);
            tmplIntent.setAction(A2UIWidgetProvider.ACTION_SWITCH_TEMPLATE);
            tmplIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
            tmplIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, templates[i]);
            views.setOnClickPendingIntent(buttonIds[i],
                    PendingIntent.getBroadcast(context, appWidgetId * 10 + 3 + i, tmplIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            int color = templates[i].equals(currentTemplate)
                    ? context.getColor(R.color.widget_template_active)
                    : context.getColor(R.color.widget_template_inactive);
            views.setTextColor(buttonIds[i], color);
            if ("poll".equals(templates[i])) {
                int totalVotes = WidgetPollStats.getTotalVotes(context);
                String badge = context.getString(R.string.widget_template_poll) + " · " + totalVotes;
                views.setTextViewText(buttonIds[i], badge);
            }
        }
    }

    private static void wireAiInput(Context context, RemoteViews views, int appWidgetId) {
        Intent aiInputIntent = new Intent(context, A2UIWidgetProvider.class);
        aiInputIntent.setAction(A2UIWidgetProvider.ACTION_AI_INPUT);
        aiInputIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        views.setOnClickPendingIntent(R.id.btnAiInput,
                PendingIntent.getBroadcast(context, appWidgetId * 10 + 4, aiInputIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }

    private static void wireQuickJoin(Context context, RemoteViews views,
                                       int appWidgetId, String currentTemplate) {
        Intent quickJoinIntent = new Intent(context, A2UIWidgetProvider.class);
        quickJoinIntent.setAction(A2UIWidgetProvider.ACTION_QUICK_JOIN);
        quickJoinIntent.putExtra(A2UIWidgetProvider.EXTRA_APPWIDGET_ID, appWidgetId);
        quickJoinIntent.putExtra(A2UIWidgetProvider.EXTRA_TEMPLATE, currentTemplate);
        views.setOnClickPendingIntent(R.id.btnQuickJoin,
                PendingIntent.getBroadcast(context, appWidgetId * 10 + 5, quickJoinIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}
