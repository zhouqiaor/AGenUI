package com.amap.agenui.platform.harmony.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;
import androidx.viewpager2.widget.ViewPager2;

import com.amap.agenui.platform.harmony.tokens.HarmonyTokenResolver;


/**
 * HarmonyOS-style TabRow with brand underline indicator.
 *
 * Visual specs:
 * - Height: 48vp
 * - Selected: brand color text + 2vp brand underline
 * - Unselected: text_secondary color, no underline
 * - Label: 14fp (font_caption)
 * - Indicator: full-width of selected tab label
 */
public class HarmonyTabRow extends LinearLayout {

    private Paint indicatorPaint;
    private Paint dividerPaint;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private int barHeight;
    private int indicatorHeight;
    private int selectedPosition = 0;
    private float indicatorLeft = 0;
    private float indicatorRight = 0;
    private OnTabSelectedListener listener;
    private String[] tabLabels;

    public interface OnTabSelectedListener {
        void onTabSelected(int position);
    }

    public HarmonyTabRow(Context context) {
        super(context);
        init(context);
    }

    public HarmonyTabRow(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        barHeight = vp(tokenResolver.spaceXl());
        indicatorHeight = vp(2);

        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setColor(tokenResolver.brandColor());
        indicatorPaint.setStyle(Paint.Style.FILL);

        dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(tokenResolver.dividerColor());
        dividerPaint.setStyle(Paint.Style.FILL);

        setOrientation(HORIZONTAL);
        setMinimumHeight(barHeight);
        setWillNotDraw(false);
    }

    public void setTabs(String[] labels, OnTabSelectedListener listener) {
        this.tabLabels = labels;
        this.listener = listener;
        removeAllViews();

        for (int i = 0; i < labels.length; i++) {
            TextView tab = new TextView(getContext());
            tab.setText(labels[i]);
            tab.setTextColor(i == selectedPosition
                ? tokenResolver.brandColor()
                : tokenResolver.textSecondaryColor());
            tab.setTextSize(vp(tokenResolver.fontCaptionSize()));
            tab.setGravity(android.view.Gravity.CENTER);
            tab.setPadding(vp(tokenResolver.spaceMd()), 0,
                           vp(tokenResolver.spaceMd()), 0);
            tab.setClickable(true);
            final int pos = i;
            tab.setOnClickListener(v -> selectTab(pos));

            LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            );
            lp.gravity = android.view.Gravity.CENTER_VERTICAL;
            addView(tab, lp);
        }

        updateIndicator();
    }

    public void selectTab(int position) {
        if (position < 0 || position >= getChildCount()) return;
        selectedPosition = position;
        for (int i = 0; i < getChildCount(); i++) {
            TextView tab = (TextView) getChildAt(i);
            tab.setTextColor(i == selectedPosition
                ? tokenResolver.brandColor()
                : tokenResolver.textSecondaryColor());
        }
        updateIndicator();
        if (listener != null) {
            listener.onTabSelected(position);
        }
    }

    private void updateIndicator() {
        if (selectedPosition < getChildCount()) {
            View tab = getChildAt(selectedPosition);
            if (tab != null) {
                indicatorLeft = tab.getLeft();
                indicatorRight = tab.getRight();
                invalidate();
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int h = getHeight();
        float dividerY = h - vp(1);
        canvas.drawRect(0, dividerY, getWidth(), h, dividerPaint);
        float indY = h - indicatorHeight;
        canvas.drawRect(indicatorLeft, indY, indicatorRight, h, indicatorPaint);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        updateIndicator();
    }

    private int vp(float vp) {
        return (int) (vp * density + 0.5f);
    }
}
