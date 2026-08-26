package com.amap.agenuiplayground.widget;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Preloads all widget template JSONs into an in-memory HashMap on a background
 * thread at app launch, so that {@link WidgetProtocolTemplates#loadTemplate}
 * can read from memory instead of hitting the asset stream on every render.
 *
 * <p>Templates are keyed by name ({@code "weather"}, {@code "agenda"}, etc.)
 * and stored <b>before</b> {@code __SURFACE_ID__} replacement. The caller
 * must still substitute the surface id at lookup time.
 */
public final class WidgetTemplatePreloader {

    private static final String TAG = "WidgetTemplatePreloader";

    private static final HashMap<String, String> sCache = new HashMap<>();
    private static final AtomicBoolean sStarted = new AtomicBoolean(false);
    private static final AtomicBoolean sDone = new AtomicBoolean(false);

    private WidgetTemplatePreloader() {
        // No instances.
    }

    /**
     * Loads all templates from {@link WidgetProtocolTemplates#AVAILABLE_TEMPLATES}
     * into the in-memory cache on a background thread.
     *
     * <p>Safe to call multiple times — subsequent calls are no-ops once the
     * first load has completed.
     */
    public static void preload(@NonNull Context context) {
        if (sStarted.getAndSet(true)) {
            return;
        }
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            for (String name : WidgetProtocolTemplates.AVAILABLE_TEMPLATES) {
                try {
                    String json = WidgetProtocolTemplates.loadTemplateFromAssets(app, name);
                    if (json != null) {
                        synchronized (sCache) {
                            sCache.put(name, json);
                        }
                        Log.d(TAG, "Preloaded template: " + name);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to preload template: " + name, e);
                }
            }
            sDone.set(true);
            Log.d(TAG, "Preload complete: " + sCache.size() + " templates");
        }, "WidgetTemplatePreloader").start();
    }

    /**
     * Returns the preloaded raw template JSON (before surface-id replacement),
     * or {@code null} if not preloaded yet.
     */
    @Nullable
    public static String get(@NonNull String templateName) {
        synchronized (sCache) {
            return sCache.get(templateName);
        }
    }

    /** Returns true if preload has completed. */
    public static boolean isDone() {
        return sDone.get();
    }

    /** Clears the preloaded cache. For testing. */
    public static void clear() {
        synchronized (sCache) {
            sCache.clear();
        }
        sStarted.set(false);
        sDone.set(false);
    }
}
