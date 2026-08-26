package com.amap.agenuiplayground.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.SizeF;

import java.util.List;

/**
 * Detects widget dimensions and determines the appropriate layout breakpoint.
 *
 * <p>Android 12+ supports responsive widget layouts via {@link AppWidgetManager#getWidgetSizes}.
 * On older APIs, we fall back to the default 300×400 dimensions.
 *
 * <p>Breakpoints (aligned with Material Design widget size categories):
 * <ul>
 *   <li>{@link #SIZE_SMALL} — width < 180dp: minimal layout, no template bar</li>
 *   <li>{@link #SIZE_MEDIUM} — 180–320dp: standard layout with template bar</li>
 *   <li>{@link #SIZE_LARGE} — width > 320dp: extended layout, more content visible</li>
 * </ul>
 */
public final class WidgetSizeDetector {

    private static final String TAG = "WidgetSizeDetector";

    /** Default widget dimensions (used when actual size is unavailable). */
    public static final int DEFAULT_WIDTH = 300;
    public static final int DEFAULT_HEIGHT = 400;

    // Breakpoints (in pixels at mdpi / dp)
    public static final int SIZE_SMALL = 180;
    public static final int SIZE_MEDIUM = 320;
    public static final int SIZE_LARGE = 320;

    public enum SizeCategory {
        SMALL, MEDIUM, LARGE
    }

    private WidgetSizeDetector() { } // utility class

    /**
     * Returns the actual widget size in pixels, or the default if unavailable.
     *
     * <p>On Android 12+ uses {@link AppWidgetManager#getWidgetSizes}.
     * On older APIs, returns {@code (DEFAULT_WIDTH, DEFAULT_HEIGHT)}.
     *
     * @return a [width, height] int array
     */
    public static int[] getWidgetSize(Context context, int appWidgetId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                List<SizeF> sizes = AppWidgetManager.getInstance(context)
                        .getWidgetSizes(appWidgetId);
                if (sizes != null && !sizes.isEmpty()) {
                    SizeF size = sizes.get(0);
                    int w = Math.round(size.getWidth());
                    int h = Math.round(size.getHeight());
                    if (w > 0 && h > 0) {
                        Log.d(TAG, "Widget size from API: " + w + "x" + h);
                        return new int[]{w, h};
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "getWidgetSizes failed, using default", e);
            }
        }
        return new int[]{DEFAULT_WIDTH, DEFAULT_HEIGHT};
    }

    /**
     * Determines the size category for the given dimensions.
     */
    public static SizeCategory categorize(int widthPx) {
        if (widthPx < SIZE_SMALL) return SizeCategory.SMALL;
        if (widthPx < SIZE_LARGE) return SizeCategory.MEDIUM;
        return SizeCategory.LARGE;
    }

    /**
     * Returns the appropriate widget dimensions for the given appWidgetId,
     * considering both the actual size and the size category.
     */
    public static WidgetDimensions resolve(Context context, int appWidgetId) {
        int[] size = getWidgetSize(context, appWidgetId);
        SizeCategory category = categorize(size[0]);
        return new WidgetDimensions(size[0], size[1], category);
    }

    /**
     * Immutable holder for resolved widget dimensions.
     */
    public static final class WidgetDimensions {
        public final int width;
        public final int height;
        public final SizeCategory category;

        WidgetDimensions(int width, int height, SizeCategory category) {
            this.width = width;
            this.height = height;
            this.category = category;
        }

        public boolean isSmall() { return category == SizeCategory.SMALL; }
        public boolean isMedium() { return category == SizeCategory.MEDIUM; }
        public boolean isLarge() { return category == SizeCategory.LARGE; }

        @Override
        public String toString() {
            return width + "x" + height + " (" + category + ")";
        }
    }
}
