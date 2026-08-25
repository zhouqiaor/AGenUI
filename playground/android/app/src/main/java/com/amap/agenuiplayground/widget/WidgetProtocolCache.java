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
}
