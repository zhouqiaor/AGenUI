package com.amap.agenui.render.component.impl;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.amap.agenui.render.component.A2UIComponent;
import com.amap.agenui.render.component.ComponentEventDispatcher;
import com.amap.agenui.render.component.ComponentState;

import java.util.List;

/**
 * StepIndicator component — multi-step progress indicator.
 * Properties: current (Number), total (Number), labels (Array<String>)
 */
@SuppressLint("ViewConstructor")
public class StepIndicatorComponent extends A2UIComponent {

    private static final int COLOR_COMPLETED = 0xFF007DFF;
    private static final int COLOR_CURRENT = 0xFF007DFF;
    private static final int COLOR_UPCOMING = 0xFFE0E0E0;
    private static final int COLOR_TEXT_DARK = 0xFF666666;
    private static final int COLOR_TEXT_LIGHT = 0xFFFFFFFF;
    private static final int CIRCLE_SIZE = 24;
    private static final int LINE_WIDTH = 2;
    private static final int HEIGHT = 48;
    private static final int RADIUS = 12;

    private int current = 0;
    private int total = 3;
    private List<String> labels;

    private LinearLayout container;

    public StepIndicatorComponent(Context context, String componentId, String componentType,
                                  ComponentEventDispatcher eventDispatcher, ComponentState componentState) {
        super(context, componentId, componentType, eventDispatcher, componentState);
    }

    @Override
    protected android.view.View createView() {
        container = new LinearLayout(getContext());
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        rebuildSteps();
        return container;
    }

    private void rebuildSteps() {
        container.removeAllViews();

        int stepCount = Math.max(total, 1);
        for (int i = 0; i < stepCount; i++) {
            // Circle with number
            TextView circle = new TextView(getContext());
            circle.setGravity(Gravity.CENTER);
            circle.setTextSize(12);
            circle.setText(String.valueOf(i + 1));

            GradientDrawable circleDrawable = new GradientDrawable();
            circleDrawable.setShape(GradientDrawable.OVAL);

            if (i < current) {
                circleDrawable.setColor(COLOR_COMPLETED);
                circle.setTextColor(COLOR_TEXT_LIGHT);
            } else if (i == current) {
                circleDrawable.setColor(COLOR_CURRENT);
                circle.setTextColor(COLOR_TEXT_LIGHT);
            } else {
                circleDrawable.setColor(COLOR_UPCOMING);
                circle.setTextColor(COLOR_TEXT_DARK);
            }

            circle.setBackground(circleDrawable);
            LinearLayout.LayoutParams circleLp = new LinearLayout.LayoutParams(CIRCLE_SIZE, CIRCLE_SIZE);
            circleLp.setMargins(0, 0, 4, 0);
            container.addView(circle, circleLp);

            // Connector line (except last step)
            if (i < stepCount - 1) {
                android.view.View line = new android.view.View(getContext());
                GradientDrawable lineDrawable = new GradientDrawable();
                lineDrawable.setCornerRadius(1);
                lineDrawable.setColor(i < current ? COLOR_COMPLETED : COLOR_UPCOMING);
                line.setBackground(lineDrawable);
                LinearLayout.LayoutParams lineLp = new LinearLayout.LayoutParams(0, LINE_WIDTH, 1);
                lineLp.setMargins(4, 0, 4, 0);
                container.addView(line, lineLp);
            }

            // Label (optional)
            if (labels != null && i < labels.size()) {
                TextView label = new TextView(getContext());
                label.setTextSize(12);
                label.setTextColor(i <= current ? COLOR_COMPLETED : COLOR_TEXT_DARK);
                label.setText(labels.get(i));
                LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                labelLp.setMargins(2, 0, 8, 0);
                container.addView(label, labelLp);
            }
        }
    }

    @Override
    public void setProperty(String key, Object value) {
        if (key == null || value == null) return;
        switch (key) {
            case "current":
                if (value instanceof Number) current = ((Number) value).intValue();
                else try { current = Integer.parseInt(value.toString()); } catch (Exception e) {}
                rebuildSteps();
                break;
            case "total":
                if (value instanceof Number) total = ((Number) value).intValue();
                else try { total = Integer.parseInt(value.toString()); } catch (Exception e) {}
                rebuildSteps();
                break;
            case "labels":
                if (value instanceof List) {
                    labels = (List<String>) value;
                    rebuildSteps();
                }
                break;
            default:
                super.setProperty(key, value);
        }
    }
}
