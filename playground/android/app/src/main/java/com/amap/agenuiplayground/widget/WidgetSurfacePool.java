package com.amap.agenuiplayground.widget;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.amap.agenui.render.surface.SurfaceManager;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * A pool that reuses {@link SurfaceManager} instances across widget renders.
 *
 * <p>Creating a new SurfaceManager for every render is expensive (AGenUI engine
 * init + Surface creation). This pool caches at most {@value #MAX_POOL_SIZE}
 * idle SurfaceManager instances keyed by template name, so a subsequent render
 * of the same template can skip engine init and surface creation and only
 * update the component tree + redraw.
 *
 * <p>Typical flow:
 * <pre>
 *   SurfaceManager sm = WidgetSurfacePool.acquire(template);  // may be null
 *   if (sm == null) sm = new SurfaceManager(context);
 *   // ... render ...
 *   WidgetSurfacePool.release(template, sm);  // return to pool instead of destroy()
 * </pre>
 */
public final class WidgetSurfacePool {

    private static final String TAG = "WidgetSurfacePool";

    /** Max number of idle SurfaceManager instances to keep. */
    private static final int MAX_POOL_SIZE = 2;

    private static final HashMap<String, LinkedList<SurfaceManager>> sPool = new HashMap<>();

    private WidgetSurfacePool() {
        // No instances.
    }

    /**
     * Acquires a cached SurfaceManager for the given template, or {@code null}
     * if none is available.
     */
    @Nullable
    public static synchronized SurfaceManager acquire(@NonNull String template) {
        LinkedList<SurfaceManager> list = sPool.get(template);
        if (list == null || list.isEmpty()) {
            return null;
        }
        SurfaceManager sm = list.removeFirst();
        Log.d(TAG, "Acquired cached SurfaceManager for template=" + template);
        return sm;
    }

    /**
     * Releases a SurfaceManager back to the pool for later reuse.
     *
     * <p>If the pool is full for this template, the SurfaceManager is destroyed
     * instead of being cached.
     */
    public static synchronized void release(@NonNull String template, @Nullable SurfaceManager sm) {
        if (sm == null) {
            return;
        }
        LinkedList<SurfaceManager> list = sPool.get(template);
        if (list == null) {
            list = new LinkedList<>();
            sPool.put(template, list);
        }
        if (list.size() >= MAX_POOL_SIZE) {
            SurfaceManager oldest = list.removeFirst();
            safeDestroy(oldest);
        }
        list.addLast(sm);
        Log.d(TAG, "Released SurfaceManager for template=" + template + ", pool size=" + list.size());
    }

    /** Clears the pool and destroys all cached SurfaceManagers. */
    public static synchronized void clear() {
        for (HashMap.Entry<String, LinkedList<SurfaceManager>> entry : sPool.entrySet()) {
            for (SurfaceManager sm : entry.getValue()) {
                safeDestroy(sm);
            }
            entry.getValue().clear();
        }
        sPool.clear();
    }

    /** Returns the total number of cached SurfaceManager instances. */
    public static synchronized int size() {
        int total = 0;
        for (LinkedList<SurfaceManager> list : sPool.values()) {
            total += list.size();
        }
        return total;
    }

    private static void safeDestroy(SurfaceManager sm) {
        try {
            if (sm != null) {
                sm.destroy();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to destroy SurfaceManager", e);
        }
    }
}
