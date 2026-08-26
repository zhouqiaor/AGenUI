package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads static A2UI protocol template JSONs from assets/widget_templates/.
 * Each template is a JSON array of 3 elements: [createSurface, updateComponents, updateDataModel]
 *
 * <p>Template list and button IDs are now delegated to
 * {@link WidgetTemplateRegistry} — the single source of truth.
 * To add a new template, add one entry to the registry.
 */
public class WidgetProtocolTemplates {

    private static final String TAG = "WidgetProtocolTemplates";
    private static final String TEMPLATES_DIR = "widget_templates";

    // ===== Delegated to WidgetTemplateRegistry =====

    /**
     * All available template names. Delegates to {@link WidgetTemplateRegistry}.
     */
    public static final String[] AVAILABLE_TEMPLATES = WidgetTemplateRegistry.getTemplateNames();

    /**
     * The default template to render when no template is specified.
     */
    public static final String DEFAULT_TEMPLATE = WidgetTemplateRegistry.getDefaultTemplate();

    /**
     * Layout button ids for the template bar. Delegates to
     * {@link WidgetTemplateRegistry}.
     *
     * <p>Note: only templates with a non-zero buttonId are included here.
     * The order matches the order in which templates are registered.
     */
    public static final int[] TEMPLATE_BUTTON_IDS = WidgetTemplateRegistry.getButtonIds();

    // ===== Template loading =====

    public static String loadTemplate(Context context, String template, String surfaceId) {
        // Check preloaded cache first — avoids re-reading from assets on every render.
        String preloaded = WidgetTemplatePreloader.get(template);
        if (preloaded != null) {
            return preloaded.replace("__SURFACE_ID__", surfaceId);
        }
        String raw = loadTemplateFromAssets(context, template);
        if (raw == null) return null;
        return raw.replace("__SURFACE_ID__", surfaceId);
    }

    /**
     * Loads a raw template JSON from assets, without surface-id replacement.
     * Exposed so {@link WidgetTemplatePreloader} can preload templates.
     */
    public static String loadTemplateFromAssets(Context context, String template) {
        String fileName = TEMPLATES_DIR + "/" + template + ".json";
        try {
            InputStream is = context.getAssets().open(fileName);
            byte[] buffer = new byte[is.available()];
            int bytesRead = is.read(buffer);
            is.close();
            return new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load template: " + fileName, e);
            return null;
        }
    }

    /**
     * Returns the next template name in the registry order.
     * Delegates to {@link WidgetTemplateRegistry}.
     */
    public static String getNextTemplate(String current) {
        return WidgetTemplateRegistry.getNextTemplate(current);
    }
}
