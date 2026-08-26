package com.amap.agenuiplayground.widget;

import android.view.View;
import android.widget.RemoteViews;

import com.amap.agenuiplayground.R;

/**
 * Manages widget UI state transitions (content → loading → empty → error).
 *
 * <p>Provides a clean API for {@link AGenUIWidgetRenderService} to toggle
 * RemoteViews view visibility based on render lifecycle state, instead of
 * scattering setViewVisibility calls across the renderer.
 *
 * <p>States:
 * <ul>
 *   <li>{@link #STATE_CONTENT} — show ImageView, hide others</li>
 *   <li>{@link #STATE_LOADING} — show ProgressBar, hide others</li>
 *   <li>{@link #STATE_EMPTY} — show empty TextView, hide others</li>
 *   <li>{@link #STATE_ERROR} — show error TextView, hide others</li>
 * </ul>
 */
public final class WidgetStateController {

    public static final int STATE_CONTENT = 0;
    public static final int STATE_LOADING = 1;
    public static final int STATE_EMPTY = 2;
    public static final int STATE_ERROR = 3;

    private WidgetStateController() { } // utility class

    /**
     * Sets the widget to the given UI state by toggling view visibility.
     *
     * @param views  RemoteViews to update
     * @param state  One of STATE_CONTENT, STATE_LOADING, STATE_EMPTY, STATE_ERROR
     */
    public static void setState(RemoteViews views, int state) {
        int imageViewVis = View.GONE;
        int loadingVis = View.GONE;
        int emptyVis = View.GONE;
        int errorVis = View.GONE;

        switch (state) {
            case STATE_CONTENT:
                imageViewVis = View.VISIBLE;
                break;
            case STATE_LOADING:
                loadingVis = View.VISIBLE;
                break;
            case STATE_EMPTY:
                emptyVis = View.VISIBLE;
                break;
            case STATE_ERROR:
                errorVis = View.VISIBLE;
                break;
        }

        views.setViewVisibility(R.id.widgetImageView, imageViewVis);
        views.setViewVisibility(R.id.widgetLoading, loadingVis);
        views.setViewVisibility(R.id.widgetEmpty, emptyVis);
        views.setViewVisibility(R.id.widgetError, errorVis);
    }

    /**
     * Convenience: set error state with a custom message.
     */
    public static void setError(RemoteViews views, String message) {
        setState(views, STATE_ERROR);
        views.setTextViewText(R.id.widgetError, message);
    }

    /**
     * Convenience: set empty state with a custom message.
     */
    public static void setEmpty(RemoteViews views, String message) {
        setState(views, STATE_EMPTY);
        views.setTextViewText(R.id.widgetEmpty, message);
    }
}
