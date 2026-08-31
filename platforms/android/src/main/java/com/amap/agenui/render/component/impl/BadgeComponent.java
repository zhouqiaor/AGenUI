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

import java.util.Map;

/**
 * Badge component — small numeric or status marker.
 * Properties: text (String), maxValue (Number), type (enum: error/warning/success/info)
 */
@SuppressLint("ViewConstructor")
public class BadgeComponent extends A2UIComponent {

    private static final int MIN_SIZE = 20;
    private static final int RADIUS = 10;

    private static final int COLOR_ERROR = 0xFFFF3B30;
    private static final int COLOR_WARNING = 0xFFFF9500;
    private static final int COLOR_SUCCESS = 0xFF34C759;
    private static final int COLOR_INFO = 0xFF007DFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private String text = "";
    private int maxValue = 99;
    private String type = "error";

    private TextView textView;
    private FrameLayout container;

    public BadgeComponent(Context context, String componentId, String componentType,
                          ComponentEventDispatcher eventDispatcher, ComponentState componentState) {
        super(context, componentId, componentType, eventDispatcher, componentState);
    }

    @Override
    protected android.view.View createView() {
        container = new FrameLayout(getContext());

        textView = new TextView(getContext());
        textView.setTextColor(COLOR_TEXT);
        textView.setTextSize(12);
        textView.setGravity(Gravity.CENTER);

        GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(RADIUS);
        drawable.setColor(getTypeColor(type));
        textView.setBackground(drawable);

        container.addView(textView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                MIN_SIZE, Gravity.CENTER));
        return container;
    }

    private int getTypeColor(String type) {
        switch (type) {
            case "warning": return COLOR_WARNING;
            case "success": return COLOR_SUCCESS;
            case "info": return COLOR_INFO;
            default: return COLOR_ERROR;
        }
    }

    private void updateBadge() {
        int value = 0;
        try { value = Integer.parseInt(text); }
        catch (Exception e) { /* text is not a number */ }

        if (value > maxValue) {
            textView.setText(maxValue + "+");
        } else if (text.isEmpty()) {
            textView.setText("");
            textView.setVisibility(android.view.View.GONE);
        } else {
            textView.setText(text);
            textView.setVisibility(android.view.View.VISIBLE);
        }
    }

    @Override
    public void setProperty(String key, Object value) {
        if (key == null || value == null) return;
        switch (key) {
            case "text":
                text = value.toString();
                updateBadge();
                break;
            case "maxValue":
                if (value instanceof Number) maxValue = ((Number) value).intValue();
                else try { maxValue = Integer.parseInt(value.toString()); } catch (Exception e) {}
                updateBadge();
                break;
            case "type":
                type = value.toString();
                if (textView.getBackground() instanceof GradientDrawable) {
                    ((GradientDrawable) textView.getBackground()).setColor(getTypeColor(type));
                }
                break;
            default:
                super.setProperty(key, value);
        }
    }
}
