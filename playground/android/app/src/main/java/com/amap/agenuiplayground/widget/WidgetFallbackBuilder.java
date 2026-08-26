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
                        // Convert component field names: type → component (Phase 1 → v0.9)
                        // Flatten nested children into a flat components array with ID references
                        JSONArray components = item.optJSONArray("components");
                        if (components != null) {
                            JSONArray flatComponents = new JSONArray();
                            for (int j = 0; j < components.length(); j++) {
                                JSONObject comp = components.optJSONObject(j);
                                if (comp != null) {
                                    flattenComponent(comp, flatComponents);
                                }
                            }
                            updateComponents.put("components", flatComponents);
                        }
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
     * Flattens a nested component tree into a flat array of component objects.
     * Converts "type" → "component" field name and replaces inline child objects
     * with string ID references.
     *
     * Phase 1 format: {"id":"root","type":"Card","children":[{"id":"c0","type":"Column",...}]}
     * v0.9 format:    {"id":"root","component":"Card","children":["c0"]}
     *                 + separate entry: {"id":"c0","component":"Column",...}
     *
     * @param comp The component object (may have nested children)
     * @param outFlat The flat output array to append to
     */
    private static void flattenComponent(JSONObject comp, JSONArray outFlat) {
        try {
            JSONObject flat = new JSONObject();

            // Copy all fields except "type" and "children"
            JSONArray keys = comp.names();
            if (keys != null) {
                for (int i = 0; i < keys.length(); i++) {
                    String key = keys.getString(i);
                    if ("type".equals(key)) {
                        // Rename type → component
                        flat.put("component", comp.getString("type"));
                    } else if ("children".equals(key)) {
                        // Handle children separately below
                    } else {
                        flat.put(key, comp.get(key));
                    }
                }
            }

            // If no "component" but has "type" already renamed, just use existing
            if (!flat.has("component") && comp.has("component")) {
                flat.put("component", comp.getString("component"));
            }

            // Process children: replace inline objects with ID strings, flatten child objects
            JSONArray children = comp.optJSONArray("children");
            if (children != null) {
                JSONArray childIds = new JSONArray();
                for (int i = 0; i < children.length(); i++) {
                    Object child = children.get(i);
                    if (child instanceof JSONObject) {
                        JSONObject childObj = (JSONObject) child;
                        String childId = childObj.optString("id", "child_" + System.nanoTime());
                        childIds.put(childId);
                        // Recursively flatten this child
                        flattenComponent(childObj, outFlat);
                    } else {
                        // Already a string ID reference
                        childIds.put(child);
                    }
                }
                flat.put("children", childIds);
            }

            outFlat.put(flat);
        } catch (Exception e) {
            Log.e(TAG, "Failed to flatten component", e);
            // Fallback: just add the original
            outFlat.put(comp);
        }
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
