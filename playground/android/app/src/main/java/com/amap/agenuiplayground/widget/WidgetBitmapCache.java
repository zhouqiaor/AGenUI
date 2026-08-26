package com.amap.agenuiplayground.widget;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.Nullable;

/**
 * In-memory LRU cache for widget rendering Bitmaps.
 *
 * <p>Each cache entry is a rendered widget Bitmap keyed by {@code template + "_" + viewMode}
 * (e.g. {@code weather_current}, {@code weather_forecast}, {@code agenda_today}).
 * On a cache hit the renderer can skip the full AGenUI pipeline (template load →
 * engine init → Surface → draw) and push the cached Bitmap directly to RemoteViews,
 * reducing widget update latency from ~1-2s to a few milliseconds.
 *
 * <p>Capacity is 3MB, sized for the 7 built-in templates (each ~300KB).
 * The actual byte budget is derived from {@link Bitmap#getByteCount()} so
 * ARGB_8888 bitmaps are accounted correctly.
 */
public final class WidgetBitmapCache {

    private static final String TAG = "WidgetBitmapCache";

    /** Byte budget for the LRU cache (3MB). */
    private static final int BYTE_BUDGET = 3 * 1024 * 1024;

    private static volatile LruCache<String, Bitmap> sCache;

    private WidgetBitmapCache() {
        // No instances.
    }

    private static LruCache<String, Bitmap> ensureCache() {
        if (sCache == null) {
            synchronized (WidgetBitmapCache.class) {
                if (sCache == null) {
                    sCache = new LruCache<String, Bitmap>(BYTE_BUDGET) {
                        @Override
                        protected int sizeOf(String key, Bitmap value) {
                            int bytes = value != null ? value.getByteCount() : 0;
                            return bytes <= 0 ? 1 : bytes;
                        }

                        @Override
                        protected void entryRemoved(boolean evicted, String key,
                                                    Bitmap oldValue, Bitmap newValue) {
                            if (evicted && oldValue != null && !oldValue.isRecycled()) {
                                try {
                                    oldValue.recycle();
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    };
                }
            }
        }
        return sCache;
    }

    /**
     * Builds the cache key for a template + view mode.
     *
     * <p>For templates without a view mode pass {@code null} — the key
     * degrades to {@code template_default}.
     */
    public static String buildKey(String template, @Nullable String viewMode) {
        if (viewMode == null || viewMode.isEmpty()) {
            viewMode = "default";
        }
        return template + "_" + viewMode;
    }

    /**
     * Returns the cached Bitmap for the key, or {@code null} on miss.
     *
     * <p><b>Bitmap lifecycle safety (P0 fix):</b> If the cached bitmap has been
     * recycled (by the LRU eviction callback or by the GC), it is removed from
     * the cache and {@code null} is returned. This prevents use-after-recycle
     * crashes when the widget update code tries to push a recycled bitmap to
     * RemoteViews.
     *
     * <p>Callers should <b>not</b> recycle the returned bitmap — it is still
     * owned by the cache and may be reused on subsequent renders.
     */
    @Nullable
    public static Bitmap get(String key) {
        if (key == null) {
            return null;
        }
        Bitmap b = ensureCache().get(key);
        if (b == null) {
            return null; // cache miss
        }
        // P0 safety: check recycled state after retrieving from cache.
        // The LRU eviction callback may have recycled the bitmap in a race.
        if (b.isRecycled()) {
            Log.w(TAG, "Cache hit but bitmap was recycled, removing: " + key);
            ensureCache().remove(key);
            return null;
        }
        return b;
    }

    /**
     * Stores a bitmap under the given key.
     *
     * <p><b>Bitmap lifecycle safety (P0 fix):</b> Recycled bitmaps are rejected
     * to prevent storing invalid references. If a bitmap with the same key
     * already exists and is not recycled, it is <b>not</b> recycled by this
     * method — the LRU cache will handle eviction + recycling via
     * {@link #entryRemoved}. This prevents double-recycle crashes.
     */
    public static void put(String key, @Nullable Bitmap bitmap) {
        if (key == null || bitmap == null) {
            return;
        }
        // P0 safety: reject recycled bitmaps
        if (bitmap.isRecycled()) {
            Log.w(TAG, "Attempted to cache a recycled bitmap, rejecting: " + key);
            return;
        }
        ensureCache().put(key, bitmap);
    }

    /**
     * Removes and recycles a specific bitmap from the cache.
     * Safe to call even if the key is not present.
     */
    public static void remove(String key) {
        if (key == null || sCache == null) return;
        Bitmap b = sCache.remove(key);
        if (b != null && !b.isRecycled()) {
            try {
                b.recycle();
            } catch (Exception ignored) {
            }
        }
    }

    /** Clears all cached bitmaps and recycles them. */
    public static void clear() {
        if (sCache == null) {
            return;
        }
        synchronized (WidgetBitmapCache.class) {
            if (sCache != null) {
                for (String key : sCache.snapshot().keySet()) {
                    Bitmap b = sCache.get(key);
                    if (b != null && !b.isRecycled()) {
                        try {
                            b.recycle();
                        } catch (Exception ignored) {
                        }
                    }
                }
                sCache.evictAll();
            }
        }
    }

    /** Returns the current number of cached entries. */
    public static int size() {
        return sCache == null ? 0 : sCache.size();
    }
}
