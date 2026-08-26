package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences-based persistence for widget template and protocol data.
 */
public class WidgetProtocolCache {

    private static final String PREFS_NAME = "a2ui_widget_prefs";

    public static String getTemplate(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("template_" + appWidgetId, WidgetProtocolTemplates.DEFAULT_TEMPLATE);
    }

    public static void saveTemplate(Context context, int appWidgetId, String template) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("template_" + appWidgetId, template).apply();
    }

    public static String getProtocol(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("protocol_" + appWidgetId, null);
    }

    public static void saveProtocol(Context context, int appWidgetId, String protocolJson) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("protocol_" + appWidgetId, protocolJson).apply();
    }

    /**
     * Returns the agenda view mode ("today" by default, or "week" if the user
     * has switched to the weekly meeting cards view).
     *
     * <p>When {@code "week"} is returned, the renderer includes the
     * {@code root_c10}~{@code root_c12} weekly meeting blocks.
     */
    public static String getAgendaView(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString("agenda_view_" + appWidgetId, "today");
    }

    /** Persists the agenda view mode for the given widget. */
    public static void saveAgendaView(Context context, int appWidgetId, String view) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("agenda_view_" + appWidgetId, view).apply();
    }
}
