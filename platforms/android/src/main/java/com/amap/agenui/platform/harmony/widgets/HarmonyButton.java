package com.amap.agenui.platform.harmony.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;

import com.amap.agenui.platform.harmony.tokens.HarmonyTokenResolver;


/**
 * HarmonyOS-style Button with 4 types and press-scale animation.
 *
 * Variant types:
 * - FILLED:   brand background, white text (default)
 * - OUTLINED: transparent bg, 1vp brand border, brand text
 * - TEXT:     transparent bg, brand text, no border
 * - CAPSULE:  radius_full bg, brand_surface fill, brand text
 *
 * Motion: 100ms scale to 0.95 on press, release back to 1.0
 *
 * Design tokens: brand #007DFF, brand_surface #E8F3FF, radius 8/12/999
 */
public class HarmonyButton extends FrameLayout {

    public enum Variant {
        FILLED,
        OUTLINED,
        TEXT,
        CAPSULE
    }

    private Variant variant = Variant.FILLED;
    private GradientDrawable backgroundDrawable;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private int animDuration = 100;

    public HarmonyButton(Context context) {
        super(context);
        init();
    }

    public HarmonyButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        backgroundDrawable = new GradientDrawable();
        applyVariant(variant);

        setClickable(true);
        setFocusable(true);

        setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    animateScale(v, 0.95f, animDuration);
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    animateScale(v, 1.0f, animDuration);
                    break;
            }
            return false;
        });

        setPadding(
            vp(tokenResolver.spaceMd()),
            vp(tokenResolver.spaceXs()),
            vp(tokenResolver.spaceMd()),
            vp(tokenResolver.spaceXs())
        );
    }

    public void setVariant(Variant variant) {
        this.variant = variant;
        applyVariant(variant);
    }

    private void applyVariant(Variant variant) {
        int brandColor = tokenResolver.brandColor();
        int brandSurfaceColor = tokenResolver.brandSurfaceColor();
        int inverseColor = tokenResolver.textInverseColor();
        int dividerColor = tokenResolver.dividerColor();

        switch (variant) {
            case FILLED:
                backgroundDrawable.setColor(brandColor);
                backgroundDrawable.setCornerRadius(vp(tokenResolver.radiusSm()));
                setBackground(backgroundDrawable);
                break;
            case OUTLINED:
                backgroundDrawable.setColor(Color.TRANSPARENT);
                backgroundDrawable.setCornerRadius(vp(tokenResolver.radiusSm()));
                backgroundDrawable.setStroke(vp(1), brandColor);
                setBackground(backgroundDrawable);
                break;
            case TEXT:
                backgroundDrawable.setColor(Color.TRANSPARENT);
                setBackground(backgroundDrawable);
                break;
            case CAPSULE:
                backgroundDrawable.setColor(brandSurfaceColor);
                backgroundDrawable.setCornerRadius(vp(tokenResolver.radiusFull()));
                setBackground(backgroundDrawable);
                break;
        }
    }

    private void animateScale(View view, float targetScale, int durationMs) {
        ScaleAnimation anim = new ScaleAnimation(
            view.getScaleX(), targetScale,
            view.getScaleY(), targetScale,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        );
        anim.setDuration(durationMs);
        anim.setFillAfter(true);
        view.startAnimation(anim);
    }

    private int vp(float dp) {
        return (int) (dp * density + 0.5f);
    }
}
