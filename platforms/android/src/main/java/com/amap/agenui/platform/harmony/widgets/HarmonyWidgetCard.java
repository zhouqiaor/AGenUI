package com.amap.agenui.platform.harmony.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import com.amap.agenui.platform.harmony.tokens.HarmonyTokenResolver;


/**
 * HarmonyOS-style Widget card container for desktop widget rendering.
 *
 * Visual specs:
 * - Background: surface (#FFFFFF / #1F1F22)
 * - Corner radius: 16vp (radius_lg)
 * - Padding: 16vp (space_md)
 * - Shadow: card shadow (0px 2px 8px rgba(0,0,0,0.12))
 * - Responsive width: minimum 250x250, adaptive to widget size
 */
public class HarmonyWidgetCard extends FrameLayout {

    private Paint bgPaint;
    private Paint shadowPaint;
    private RectF cardRect;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private float cornerRadius;
    private int cardPadding;

    public HarmonyWidgetCard(Context context) {
        super(context);
        init();
    }

    public HarmonyWidgetCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        cornerRadius = vp(tokenResolver.radiusLg());
        cardPadding = vp(tokenResolver.spaceMd());

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(tokenResolver.surfacePrimaryColor());
        bgPaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x1E000000);
        shadowPaint.setStyle(Paint.Style.FILL);

        setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setClipChildren(true);
        setClipToPadding(true);

        setMinimumWidth(vp(250));
        setMinimumHeight(vp(120));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cardRect = new RectF(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (cardRect != null) {
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, shadowPaint);
            canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, bgPaint);
        }
        super.onDraw(canvas);
    }

    public void setWidgetBackgroundColor(int color) {
        bgPaint.setColor(color);
        invalidate();
    }

    public int getCardPadding() {
        return cardPadding;
    }

    public float getCornerRadius() {
        return cornerRadius;
    }

    private int vp(float vp) {
        return (int) (vp * density + 0.5f);
    }
}
