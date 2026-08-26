package com.amap.agenui.platform.harmony.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.amap.agenui.platform.harmony.tokens.HarmonyTokenResolver;


/**
 * HarmonyOS-style bottom Sheet with top-rounded corners and drag indicator.
 *
 * Visual specs:
 * - Top corner radius: 16vp (radius_lg)
 * - Background: surface (#FFFFFF / #1F1F22)
 * - Drag indicator: 36x4vp, centered, divider color, 8vp from top
 * - Shadow: elevated shadow on top edge
 */
public class HarmonySheet extends FrameLayout {

    private Paint bgPaint;
    private Paint indicatorPaint;
    private Paint shadowPaint;
    private RectF sheetRect;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private float cornerRadius;
    private float indicatorWidth;
    private float indicatorHeight;
    private float indicatorTopMargin;

    public HarmonySheet(Context context) {
        super(context);
        init();
    }

    public HarmonySheet(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        cornerRadius = vp(tokenResolver.radiusLg());
        indicatorWidth = vp(36);
        indicatorHeight = vp(4);
        indicatorTopMargin = vp(tokenResolver.spaceXs());

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(tokenResolver.surfacePrimaryColor());
        bgPaint.setStyle(Paint.Style.FILL);

        indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        indicatorPaint.setColor(tokenResolver.dividerColor());
        indicatorPaint.setStyle(Paint.Style.FILL);
        indicatorPaint.setAntiAlias(true);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x14000000);
        shadowPaint.setStyle(Paint.Style.FILL);

        int contentPadding = (int) vp(tokenResolver.spaceMd());
        setPadding(contentPadding,
                   (int)(indicatorTopMargin + indicatorHeight + indicatorTopMargin),
                   contentPadding,
                   contentPadding);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        sheetRect = new RectF(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (sheetRect != null) {
            canvas.drawRoundRect(sheetRect, cornerRadius, cornerRadius, shadowPaint);
            canvas.drawRoundRect(sheetRect, cornerRadius, cornerRadius, bgPaint);

            float cx = getWidth() / 2f;
            float indLeft = cx - indicatorWidth / 2f;
            float indTop = indicatorTopMargin;
            float indRight = cx + indicatorWidth / 2f;
            float indBottom = indTop + indicatorHeight;

            RectF indicatorRect = new RectF(indLeft, indTop, indRight, indBottom);
            canvas.drawRoundRect(indicatorRect, indicatorHeight / 2f, indicatorHeight / 2f, indicatorPaint);
        }
        super.onDraw(canvas);
    }

    public void setSheetBackgroundColor(int color) {
        bgPaint.setColor(color);
        invalidate();
    }

    private float vp(float vp) {
        return vp * density + 0.5f;
    }
}
