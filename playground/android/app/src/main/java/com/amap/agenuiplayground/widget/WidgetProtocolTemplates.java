package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads static A2UI protocol template JSONs from assets/widget_templates/.
 * Each template is a JSON array of 3 elements: [createSurface, updateComponents, updateDataModel]
 */
public class WidgetProtocolTemplates {

    private static final String TAG = "WidgetProtocolTemplates";
    private static final String TEMPLATES_DIR = "widget_templates";

    public static final String[] AVAILABLE_TEMPLATES = {"weather", "agenda", "todo"};
    public static final String DEFAULT_TEMPLATE = "weather";

    public static String loadTemplate(Context context, String template, String surfaceId) {
        String fileName = TEMPLATES_DIR + "/" + template + ".json";
        try {
            InputStream is = context.getAssets().open(fileName);
            byte[] buffer = new byte[is.available()];
            int bytesRead = is.read(buffer);
            is.close();
            String json = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            return json.replace("__SURFACE_ID__", surfaceId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to load template: " + fileName, e);
            return null;
        }
    }

    public static String getNextTemplate(String current) {
        for (int i = 0; i < AVAILABLE_TEMPLATES.length; i++) {
            if (AVAILABLE_TEMPLATES[i].equals(current)) {
                int next = (i + 1) % AVAILABLE_TEMPLATES.length;
                return AVAILABLE_TEMPLATES[next];
            }
        }
        return DEFAULT_TEMPLATE;
    }
}
