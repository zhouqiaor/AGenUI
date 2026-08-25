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
 * HarmonyOS-style Card with radius_md=12 and soft shadow.
 *
 * Visual specs:
 * - Background: surface (#FFFFFF light / #1F1F22 dark)
 * - Corner radius: 12vp (radius_md)
 * - Shadow: 0px 2px 16px rgba(0,0,0,0.06)
 * - Default padding: 16vp (space_md)
 */
public class HarmonyCard extends FrameLayout {

    private Paint bgPaint;
    private Paint shadowPaint;
    private RectF cardRect;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private float cornerRadius;
    private float shadowRadius;
    private float shadowDx, shadowDy;
    private int padding;

    public HarmonyCard(Context context) {
        super(context);
        init();
    }

    public HarmonyCard(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        cornerRadius = vp(tokenResolver.radiusMd());
        shadowRadius = vp(16);
        shadowDx = 0;
        shadowDy = vp(2);
        padding = vp(tokenResolver.spaceMd());

        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(tokenResolver.surfacePrimaryColor());
        bgPaint.setStyle(Paint.Style.FILL);

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x0F000000);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setShadowLayer(shadowRadius, shadowDx, shadowDy, 0x0F000000);

        setPadding(padding, padding, padding, padding);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);

        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
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

    public void setCardBackgroundColor(int color) {
        bgPaint.setColor(color);
        invalidate();
    }

    private float vp(float vp) {
        return vp * density + 0.5f;
    }
}
