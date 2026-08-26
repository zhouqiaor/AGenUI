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
 * HarmonyOS-style glass panel with blur effect simulation.
 *
 * Visual specs:
 * - Background: surface at 70% opacity (simulating BlurView)
 * - Corner radius: 16vp (radius_lg)
 * - Border: 1vp, 10% white for glass edge highlight
 * - Content area with 16vp padding
 *
 * Note: On Android 12+ use RenderEffect.createBlurEffect for real blur.
 * Pre-12: falls back to semi-transparent surface.
 */
public class HarmonyGlassPanel extends FrameLayout {

    private Paint bgPaint;
    private Paint borderPaint;
    private Paint shadowPaint;
    private RectF panelRect;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private float cornerRadius;

    public HarmonyGlassPanel(Context context) {
        super(context);
        init();
    }

    public HarmonyGlassPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        cornerRadius = vp(tokenResolver.radiusLg());

        int surface = tokenResolver.surfacePrimaryColor();
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.argb(178, Color.red(surface), Color.green(surface), Color.blue(surface)));
        bgPaint.setStyle(Paint.Style.FILL);

        borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.argb(26, 255, 255, 255));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(vp(1));

        shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x14000000);
        shadowPaint.setStyle(Paint.Style.FILL);

        int padding = (int) vp(tokenResolver.spaceMd());
        setPadding(padding, padding, padding, padding);
        setWillNotDraw(false);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        panelRect = new RectF(0, 0, w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (panelRect != null) {
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, shadowPaint);
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, bgPaint);
            canvas.drawRoundRect(panelRect, cornerRadius, cornerRadius, borderPaint);
        }
        super.onDraw(canvas);
    }

    private float vp(float vp) {
        return vp * density + 0.5f;
    }
}
