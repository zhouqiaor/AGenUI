package com.amap.agenui.render.component.impl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;

import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.ComponentEventDispatcher;
import com.amap.agenui.render.component.ComponentState;

import java.util.Map;

/**
 * Switch component — a two-state toggle (on/off).
 * <p>
 * Properties:
 * <ul>
 *   <li>{@code checked} (Boolean) — whether the switch is on</li>
 *   <li>{@code color} (String, hex) — accent color when checked (default #007DFF)</li>
 *   <li>{@code disabled} (Boolean) — whether interaction is disabled</li>
 *   <li>{@code onToggle} (Action) — callback when toggled</li>
 * </ul>
 * <p>
 * Size: 52×32 dp (fixed), border-radius 16dp.
 */
@SuppressLint("ViewConstructor")
public class SwitchComponent extends A2UIComponent {

    private static final String TAG = "SwitchComponent";

    private static final int DEFAULT_WIDTH = 52;
    private static final int DEFAULT_HEIGHT = 32;
    private static final int DEFAULT_RADIUS = 16;
    private static final int THUMB_SIZE = 28;
    private static final int THUMB_MARGIN = 2;

    private static final int COLOR_ON_DEFAULT = 0xFF007DFF;
    private static final int COLOR_OFF_DEFAULT = 0xFFE0E0E0;
    private static final int COLOR_THUMB = 0xFFFFFFFF;
    private static final int COLOR_DISABLED = 0xFFBDBDBD;
    private static final int COLOR_DISABLED_THUMB = 0xFFF5F5F5;

    private boolean isChecked = false;
    private boolean isDisabled = false;
    private int onColor = COLOR_ON_DEFAULT;

    private View trackView;
    private View thumbView;
    private LinearLayout container;

    public SwitchComponent(Context context, String componentId, String componentType,
                           ComponentEventDispatcher eventDispatcher, ComponentState componentState) {
        super(context, componentId, componentType, eventDispatcher, componentState);
    }

    @Override
    protected View createView() {
        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);

        trackView = new View(getContext());
        GradientDrawable trackDrawable = new GradientDrawable();
        trackDrawable.setCornerRadius(DEFAULT_RADIUS);
        trackView.setBackground(trackDrawable);

        thumbView = new View(getContext());
        GradientDrawable thumbDrawable = new GradientDrawable();
        thumbDrawable.setShape(GradientDrawable.OVAL);
        thumbView.setBackground(thumbDrawable);

        container.addView(trackView, new LinearLayout.LayoutParams(
                DEFAULT_WIDTH, DEFAULT_HEIGHT));
        // Thumb positioned via padding/margin
        // Use absolute layout approach
        container.setPadding(THUMB_MARGIN, THUMB_MARGIN, THUMB_MARGIN, THUMB_MARGIN);

        updateVisualState();

        container.setOnClickListener(v -> {
            if (isDisabled) return;
            isChecked = !isChecked;
            updateVisualState();
            // Dispatch toggle action
            if (eventDispatcher != null) {
                eventDispatcher.dispatchAction(getComponentId(), "onToggle",
                        isChecked ? "true" : "false");
            }
        });

        return container;
    }

    private void updateVisualState() {
        int bgColor;
        int thumbColor;

        if (isDisabled) {
            bgColor = COLOR_DISABLED;
            thumbColor = COLOR_DISABLED_THUMB;
        } else if (isChecked) {
            bgColor = onColor;
            thumbColor = COLOR_THUMB;
        } else {
            bgColor = COLOR_OFF_DEFAULT;
            thumbColor = COLOR_THUMB;
        }

        // Update track
        if (trackView.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) trackView.getBackground()).setColor(bgColor);
        }

        // Update thumb
        if (thumbView.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) thumbView.getBackground()).setColor(thumbColor);
        }

        // Animate thumb position (simple version: translate)
        float thumbX = isChecked
                ? (DEFAULT_WIDTH - THUMB_SIZE - THUMB_MARGIN)
                : THUMB_MARGIN;
        thumbView.setTranslationX(thumbX);
    }

    @Override
    public void setProperty(String key, Object value) {
        if (key == null || value == null) return;

        switch (key) {
            case "checked":
                isChecked = parseBoolean(value, false);
                updateVisualState();
                break;
            case "color":
                onColor = parseColor(value, COLOR_ON_DEFAULT);
                updateVisualState();
                break;
            case "disabled":
                isDisabled = parseBoolean(value, false);
                container.setEnabled(!isDisabled);
                updateVisualState();
                break;
            default:
                super.setProperty(key, value);
        }
    }

    @Override
    public void setProperties(Map<String, Object> properties) {
        super.setProperties(properties);
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
                if (hex.length() == 6) {
                    return Color.parseColor("#FF" + hex);
                } else if (hex.length() == 8) {
                    return Color.parseColor("#" + hex);
                }
            } catch (Exception e) {
                // fall through
            }
        }
        return defaultVal;
    }
}
