package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Lightweight persistence for poll vote counts shown as a live badge on the
 * poll template-bar button. The badge value is surfaced via
 * {@link android.widget.RemoteViews#setTextViewText} so it updates together
 * with the rest of the widget RemoteViews tree.
 *
 * <p>The default total (42) mirrors the seed value embedded in
 * {@code widget_templates/poll.json} so the badge is meaningful before any
 * local vote is cast.
 */
public final class WidgetPollStats {

    private static final String PREFS_NAME = "agenui_poll_stats";
    private static final String KEY_TOTAL_VOTES = "total_votes";
    private static final int DEFAULT_TOTAL_VOTES = 42;

    private WidgetPollStats() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the current total vote count, defaulting to 42. */
    public static int getTotalVotes(Context context) {
        return prefs(context).getInt(KEY_TOTAL_VOTES, DEFAULT_TOTAL_VOTES);
    }

    /** Persists an updated total vote count for the next render pass. */
    public static void setTotalVotes(Context context, int total) {
        prefs(context).edit().putInt(KEY_TOTAL_VOTES, total).apply();
    }
}
