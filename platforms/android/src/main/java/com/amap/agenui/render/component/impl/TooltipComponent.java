package com.amap.agenui.render.component.impl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.ComponentEventDispatcher;
import com.amap.agenui.render.component.ComponentState;

/**
 * Tooltip component — short floating text hint.
 * Properties: text (String), visible (Boolean), position (enum: top/bottom/left/right)
 */
@SuppressLint("ViewConstructor")
public class TooltipComponent extends A2UIComponent {

    private static final int COLOR_BG = 0xFF333333;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int PADDING_H = 12;
    private static final int PADDING_V = 8;
    private static final int RADIUS = 8;
    private static final int MAX_WIDTH = 320;

    private String text = "";
    private boolean visible = true;
    private String position = "top";

    private TextView textView;
    private FrameLayout container;

    public TooltipComponent(Context context, String componentId, String componentType,
                            ComponentEventDispatcher eventDispatcher, ComponentState componentState) {
        super(context, componentId, componentType, eventDispatcher, componentState);
    }

    @Override
    protected android.view.View createView() {
        container = new FrameLayout(getContext());

        textView = new TextView(getContext());
        textView.setTextColor(COLOR_TEXT);
        textView.setTextSize(14);
        textView.setMaxWidth(MAX_WIDTH);
        textView.setGravity(Gravity.CENTER);
        textView.setPadding(PADDING_H, PADDING_V, PADDING_H, PADDING_V);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(RADIUS);
        drawable.setColor(COLOR_BG);
        textView.setBackground(drawable);

        container.addView(textView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        updateTooltip();
        return container;
    }

    private void updateTooltip() {
        textView.setText(text);
        container.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    @Override
    public void setProperty(String key, Object value) {
        if (key == null || value == null) return;
        switch (key) {
            case "text":
                text = value.toString();
                updateTooltip();
                break;
            case "visible":
                visible = parseBoolean(value, true);
                updateTooltip();
                break;
            case "position":
                position = value.toString();
                break;
            default:
                super.setProperty(key, value);
        }
    }

    private boolean parseBoolean(Object value, boolean def) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        return def;
    }
}
