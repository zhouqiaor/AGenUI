package com.amap.agenui.render.component.impl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.FrameLayout;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.ComponentEventDispatcher;
import com.amap.agenui.render.component.ComponentState;

import java.util.Map;

/**
 * ProgressBar component — a linear progress indicator.
 * <p>
 * Properties:
 * <ul>
 *   <li>{@code progress} (Number 0-100) — progress percentage</li>
 *   <li>{@code indeterminate} (Boolean) — indeterminate animation mode</li>
 *   <li>{@code color} (String, hex) — indicator color (default #007DFF)</li>
 *   <li>{@code trackColor} (String, hex) — track background color (default #E0E0E0)</li>
 * </ul>
 * <p>
 * Size: height 6dp (fixed), width stretch.
 */
@SuppressLint("ViewConstructor")
public class ProgressBarComponent extends A2UIComponent {

    private static final int DEFAULT_HEIGHT = 6;
    private static final int DEFAULT_RADIUS = 3;

    private static final int COLOR_INDICATOR = 0xFF007DFF;
    private static final int COLOR_TRACK = 0xFFE0E0E0;

    private float progress = 0f;
    private boolean indeterminate = false;
    private int indicatorColor = COLOR_INDICATOR;
    private int trackColor = COLOR_TRACK;

    private View trackView;
    private View indicatorView;
    private FrameLayout container;

    public ProgressBarComponent(Context context, String componentId, String componentType,
                                ComponentEventDispatcher eventDispatcher,
                                ComponentState componentState) {
        super(context, componentId, componentType, eventDispatcher, componentState);
    }

    @Override
    protected View createView() {
        container = new FrameLayout(getContext());

        // Track (full width background)
        trackView = new View(getContext());
        GradientDrawable trackDrawable = new GradientDrawable();
        trackDrawable.setCornerRadius(DEFAULT_RADIUS);
        trackDrawable.setColor(trackColor);
        trackView.setBackground(trackDrawable);

        // Indicator (progress fill)
        indicatorView = new View(getContext());
        GradientDrawable indicatorDrawable = new GradientDrawable();
        indicatorDrawable.setCornerRadius(DEFAULT_RADIUS);
        indicatorDrawable.setColor(indicatorColor);
        indicatorView.setBackground(indicatorDrawable);

        container.addView(trackView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                DEFAULT_HEIGHT));

        // Indicator width set in updateProgress
        container.addView(indicatorView, new FrameLayout.LayoutParams(
                0, DEFAULT_HEIGHT));

        updateProgress();

        return container;
    }

    private void updateProgress() {
        float effectiveProgress = indeterminate ? 0.3f : Math.max(0f, Math.min(100f, progress));
        int parentWidth = container.getWidth();
        if (parentWidth <= 0) parentWidth = 200; // fallback
        int indicatorWidth = (int) (parentWidth * effectiveProgress / 100f);

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) indicatorView.getLayoutParams();
        lp.width = Math.max(0, indicatorWidth);
        indicatorView.setLayoutParams(lp);

        if (indeterminate) {
            // Simple indeterminate animation: translate
            indicatorView.animate()
                    .translationX(parentWidth)
                    .setDuration(1000)
                    .alpha(0.5f)
                    .withEndAction(() -> {
                        indicatorView.setTranslationX(0);
                        indicatorView.setAlpha(1f);
                    });
        }
    }

    @Override
    public void setProperty(String key, Object value) {
        if (key == null || value == null) return;

        switch (key) {
            case "progress":
                if (value instanceof Number) {
                    progress = ((Number) value).floatValue();
                } else if (value instanceof String) {
                    try { progress = Float.parseFloat((String) value); }
                    catch (Exception e) { progress = 0; }
                }
                updateProgress();
                break;
            case "indeterminate":
                indeterminate = parseBoolean(value, false);
                updateProgress();
                break;
            case "color":
                indicatorColor = parseColor(value, COLOR_INDICATOR);
                if (indicatorView.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) indicatorView.getBackground()).setColor(indicatorColor);
                }
                break;
            case "trackColor":
                trackColor = parseColor(value, COLOR_TRACK);
                if (trackView.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) trackView.getBackground()).setColor(trackColor);
                }
                break;
            default:
                super.setProperty(key, value);
        }
    }

    @Override
    public void onLayoutChanged(int x, int y, int width, int height) {
        super.onLayoutChanged(x, y, width, height);
        updateProgress();
    }

    private boolean parseBoolean(Object value, boolean defaultVal) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return defaultVal;
    }

    private int parseColor(Object value, int defaultVal) {
        if (value instanceof String) {
            try {
                String hex = (String) value;
                if (hex.startsWith("#")) hex = hex.substring(1);
                if (hex.length() == 6) return android.graphics.Color.parseColor("#FF" + hex);
                else if (hex.length() == 8) return android.graphics.Color.parseColor("#" + hex);
            } catch (Exception e) { /* fall through */ }
        }
        return defaultVal;
    }
}
