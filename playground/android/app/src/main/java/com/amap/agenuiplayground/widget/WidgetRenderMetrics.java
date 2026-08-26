package com.amap.agenuiplayground.widget;

import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight performance metrics collector for widget rendering.
 *
 * <p>Records per-template render times and cache hit/miss ratios,
 * providing visibility into the rendering pipeline without adding
 * heavy dependencies.
 *
 * <p>Thread-safe: all methods are synchronized.
 */
public final class WidgetRenderMetrics {

    private static final String TAG = "WidgetRenderMetrics";

    // Aggregated stats per template
    private static final Map<String, RenderStats> sStats = new LinkedHashMap<>();
    private static volatile boolean sEnabled = true;

    private WidgetRenderMetrics() { } // utility class

    /**
     * Records a render result for the given template.
     *
     * @param template   Template name
     * @param durationMs Render duration in milliseconds
     * @param cacheHit   true if this was a cache hit (no rendering needed)
     */
    public static synchronized void recordRender(String template, long durationMs, boolean cacheHit) {
        if (!sEnabled) return;
        RenderStats stats = sStats.computeIfAbsent(template, k -> new RenderStats());
        stats.totalRenders++;
        if (cacheHit) {
            stats.cacheHits++;
        } else {
            stats.totalRenderTimeMs += durationMs;
            if (durationMs > stats.maxRenderTimeMs) {
                stats.maxRenderTimeMs = durationMs;
            }
            if (stats.minRenderTimeMs == 0 || durationMs < stats.minRenderTimeMs) {
                stats.minRenderTimeMs = durationMs;
            }
        }
    }

    /**
     * Returns a formatted summary of all recorded metrics.
     */
    public static synchronized String getSummary() {
        if (sStats.isEmpty()) {
            return "No renders recorded yet.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Widget Render Metrics:\n");
        sb.append(String.format("%-12s %5s %5s %7s %7s %7s %5s%n",
                "Template", "Total", "Hits", "Avg(ms)", "Min(ms)", "Max(ms)", "Hit%"));
        for (Map.Entry<String, RenderStats> entry : sStats.entrySet()) {
            RenderStats s = entry.getValue();
            int actualRenders = s.totalRenders - s.cacheHits;
            float avgMs = actualRenders > 0 ? (float) s.totalRenderTimeMs / actualRenders : 0;
            float hitRate = s.totalRenders > 0
                    ? (float) s.cacheHits / s.totalRenders * 100 : 0;
            sb.append(String.format("%-12s %5d %5d %7.1f %7d %7d %4.0f%%n",
                    entry.getKey(), s.totalRenders, s.cacheHits,
                    avgMs, s.minRenderTimeMs, s.maxRenderTimeMs, hitRate));
        }
        return sb.toString();
    }

    /**
     * Logs the current metrics summary to logcat.
     */
    public static synchronized void logSummary() {
        Log.i(TAG, "\n" + getSummary());
    }

    /**
     * Resets all collected metrics.
     */
    public static synchronized void reset() {
        sStats.clear();
    }

    /**
     * Enables or disables metrics collection.
     */
    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
    }

    /**
     * Immutable per-template render statistics.
     */
    private static class RenderStats {
        int totalRenders;
        int cacheHits;
        long totalRenderTimeMs;
        long minRenderTimeMs;
        long maxRenderTimeMs;
    }
}
