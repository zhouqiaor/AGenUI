package com.amap.agenuiplayground.widget;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds fallback A2UI JSON in version format (v0.9) for the stream-mode degradation chain.
 *
 * Stream mode uses {"version":"v0.9","createSurface":{...}} / {"version":"v0.9","updateComponents":{...}}
 * format, but the static templates in assets/widget_templates/ use the Phase 1 {"type":"createSurface",...}
 * format. This class converts between the two so that fallback templates work in stream mode.
 */
public class WidgetFallbackBuilder {

    private static final String TAG = "WidgetFallbackBuilder";
    private static final String VERSION = "v0.9";

    /**
     * Converts a Phase 1 template JSON array (type format) into version-format stream chunks.
     *
     * @param templateJson Phase 1 template: [{"type":"createSurface",...}, {"type":"updateComponents",...}, ...]
     * @param surfaceId Target surface ID
     * @return List of version-format JSON strings suitable for surfaceManager.receiveTextChunk()
     */
    public static List<String> convertToVersionFormat(String templateJson, String surfaceId) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(templateJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;

                String type = item.optString("type", "");
                JSONObject converted = new JSONObject();
                converted.put("version", VERSION);

                switch (type) {
                    case "createSurface": {
                        JSONObject createSurface = new JSONObject();
                        createSurface.put("surfaceId", surfaceId);
                        createSurface.put("catalogId", item.optString("catalogId",
                                "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"));
                        createSurface.put("width", item.optInt("width", 300));
                        converted.put("createSurface", createSurface);
                        result.add(converted.toString());
                        break;
                    }
                    case "updateComponents": {
                        JSONObject updateComponents = new JSONObject();
                        updateComponents.put("surfaceId", surfaceId);
                        updateComponents.put("components", item.optJSONArray("components"));
                        converted.put("updateComponents", updateComponents);
                        result.add(converted.toString());
                        break;
                    }
                    case "updateDataModel": {
                        // Skip empty data model
                        JSONObject value = item.optJSONObject("value");
                        if (value != null && value.length() > 0) {
                            JSONObject updateDataModel = new JSONObject();
                            updateDataModel.put("surfaceId", surfaceId);
                            updateDataModel.put("value", value);
                            converted.put("updateDataModel", updateDataModel);
                            result.add(converted.toString());
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to convert template to version format", e);
        }
        return result;
    }

    /**
     * Builds a simple notecard fallback in version format directly (no template file needed).
     *
     * @param surfaceId Target surface ID
     * @param title     Card title
     * @param message   Card message
     * @return List of version-format JSON chunks
     */
    public static List<String> buildNoteCard(String surfaceId, String title, String message) {
        List<String> result = new ArrayList<>();
        try {
            // createSurface
            JSONObject createSurface = new JSONObject();
            createSurface.put("surfaceId", surfaceId);
            createSurface.put("catalogId",
                    "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json");
            createSurface.put("width", 300);

            JSONObject cs = new JSONObject();
            cs.put("version", VERSION);
            cs.put("createSurface", createSurface);
            result.add(cs.toString());

            // updateComponents — simple Card > Column > [Text, Text]
            JSONObject text1 = new JSONObject();
            text1.put("id", "title");
            text1.put("component", "Text");
            text1.put("text", title);
            text1.put("styles", new JSONObject()
                    .put("font-size", "18px")
                    .put("font-weight", "bold")
                    .put("color", "#CC3333"));

            JSONObject text2 = new JSONObject();
            text2.put("id", "message");
            text2.put("component", "Text");
            text2.put("text", message);
            text2.put("styles", new JSONObject()
                    .put("font-size", "14px")
                    .put("color", "#999999"));

            JSONObject column = new JSONObject();
            column.put("id", "content");
            column.put("component", "Column");
            column.put("children", new org.json.JSONArray().put("title").put("message"));
            column.put("styles", new JSONObject()
                    .put("crossAxisAlignment", "center")
                    .put("padding", new JSONObject().put("all", 16)));

            JSONObject root = new JSONObject();
            root.put("id", "root");
            root.put("component", "Card");
            root.put("children", new org.json.JSONArray().put("content"));
            root.put("styles", new JSONObject().put("margin", new JSONObject().put("all", 8)));

            JSONObject updateComponents = new JSONObject();
            updateComponents.put("surfaceId", surfaceId);
            updateComponents.put("components", new org.json.JSONArray()
                    .put(root).put(column).put(text1).put(text2));

            JSONObject uc = new JSONObject();
            uc.put("version", VERSION);
            uc.put("updateComponents", updateComponents);
            result.add(uc.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to build notecard", e);
        }
        return result;
    }
}
