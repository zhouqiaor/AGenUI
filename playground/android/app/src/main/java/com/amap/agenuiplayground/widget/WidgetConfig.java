package com.amap.agenuiplayground.widget;

import android.content.Context;

/**
 * Per-instance widget configuration.
 *
 * <p>Each widget instance (identified by appWidgetId) can have its own
 * configuration: which template to show, what view mode (e.g. current/forecast
 * for weather, today/week for agenda), and optional user preferences.
 *
 * <p>Configuration is persisted via {@link WidgetProtocolCache} (SharedPreferences).
 *
 * <p>This addresses P0 architecture issue: "multi-instance widget support" —
 * users can place multiple AGenUI widgets on their home screen, each showing
 * a different template.
 */
public final class WidgetConfig {

    private WidgetConfig() { } // utility class

    /**
     * Gets the configured template for a widget instance.
     *
     * @param context     Application context
     * @param appWidgetId Widget instance ID
     * @return Template name, or default if not configured
     */
    public static String getTemplate(Context context, int appWidgetId) {
        String template = WidgetProtocolCache.getTemplate(context, appWidgetId);
        if (template == null || template.isEmpty()) {
            return WidgetProtocolTemplates.DEFAULT_TEMPLATE;
        }
        // Verify the template is still registered
        if (WidgetTemplateRegistry.getEntry(template) == null) {
            return WidgetProtocolTemplates.DEFAULT_TEMPLATE;
        }
        return template;
    }

    /**
     * Sets the template for a widget instance.
     */
    public static void setTemplate(Context context, int appWidgetId, String template) {
        WidgetProtocolCache.saveTemplate(context, appWidgetId, template);
    }

    /**
     * Gets the weather view mode for a widget instance.
     *
     * @return "current" or "forecast"
     */
    public static String getWeatherView(Context context, int appWidgetId) {
        return WidgetProtocolCache.getWeatherView(context, appWidgetId);
    }

    /**
     * Gets the agenda view mode for a widget instance.
     *
     * @return "today" or "week"
     */
    public static String getAgendaView(Context context, int appWidgetId) {
        return WidgetProtocolCache.getAgendaView(context, appWidgetId);
    }

    /**
     * Gets the todo view mode for a widget instance.
     *
     * @return "pending" or "done"
     */
    public static String getTodoView(Context context, int appWidgetId) {
        return WidgetProtocolCache.getTodoView(context, appWidgetId);
    }
}
