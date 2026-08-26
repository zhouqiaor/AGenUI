package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Filters the agenda component tree based on the current view mode.
 *
 * <p>When the agenda view is {@code "week"}, the weekly meeting blocks
 * ({@code root_c10}, {@code root_c11}, {@code root_c12}) are included;
 * otherwise they are omitted from the rendered tree.
 *
 * <p>Extracted from {@link AGenUIWidgetRenderService} to isolate JSON tree
 * manipulation logic from rendering.
 */
public final class WidgetComponentFilter {

    private static final String TAG = "WidgetComponentFilter";

    /** IDs of weekly meeting blocks that are only shown in "week" view. */
    private static final String[] WEEKLY_BLOCK_IDS = {"root_c10", "root_c11", "root_c12"};

    private WidgetComponentFilter() { } // utility class

    /**
     * Filters the agenda component tree based on the current view mode.
     *
     * @param context      Application context
     * @param appWidgetId  Widget id, used to look up the persisted view mode
     * @param templateJson Raw agenda template JSON
     * @return The (possibly trimmed) template JSON, or the original on any error
     */
    public static String filterAgenda(Context context, int appWidgetId, String templateJson) {
        String view = WidgetProtocolCache.getAgendaView(context, appWidgetId);
        boolean showWeek = "week".equalsIgnoreCase(view);
        if (showWeek) return templateJson; // keep everything

        try {
            JSONArray arr = new JSONArray(templateJson);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                if (!"updateComponents".equals(item.optString("type", ""))) continue;

                JSONArray components = item.optJSONArray("components");
                if (components == null || components.length() == 0) continue;

                JSONObject root = components.optJSONObject(0);
                if (root == null) continue;
                JSONObject column = root.optJSONArray("children") != null
                        ? root.optJSONArray("children").optJSONObject(0) : null;
                if (column == null) continue;
                JSONArray columnChildren = column.optJSONArray("children");
                if (columnChildren == null) continue;

                // Remove weekly meeting blocks in-place (reverse order)
                for (int j = columnChildren.length() - 1; j >= 0; j--) {
                    JSONObject child = columnChildren.optJSONObject(j);
                    if (child == null) continue;
                    String id = child.optString("id", "");
                    for (String blockId : WEEKLY_BLOCK_IDS) {
                        if (blockId.equals(id)) {
                            columnChildren.remove(j);
                            break;
                        }
                    }
                }
            }
            return arr.toString();
        } catch (Exception e) {
            Log.w(TAG, "filterAgenda failed — returning original", e);
            return templateJson;
        }
    }
}
