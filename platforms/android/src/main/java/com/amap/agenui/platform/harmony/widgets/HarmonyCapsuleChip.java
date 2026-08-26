package com.amap.agenui.platform.harmony.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.amap.agenui.platform.harmony.tokens.HarmonyTokenResolver;


/**
 * HarmonyOS-style capsule chip.
 *
 * Visual specs:
 * - Corner radius: 999vp (radius_full = capsule shape)
 * - Background: brand_surface (#E8F3FF / #123A5C)
 * - Text: 12fp (overline), brand color
 * - Padding: 8vp horizontal, 4vp vertical
 */
public class HarmonyCapsuleChip extends FrameLayout {

    private GradientDrawable bgDrawable;
    private TextView textView;
    private float density;
    private HarmonyTokenResolver tokenResolver;

    public HarmonyCapsuleChip(Context context) {
        super(context);
        init(context);
    }

    public HarmonyCapsuleChip(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());

        bgDrawable = new GradientDrawable();
        bgDrawable.setShape(GradientDrawable.RECTANGLE);
        bgDrawable.setCornerRadius(vp(tokenResolver.radiusFull()));
        bgDrawable.setColor(tokenResolver.brandSurfaceColor());
        setBackground(bgDrawable);

        int padH = vp(tokenResolver.spaceXs());
        int padV = vp(tokenResolver.space2xs());
        setPadding(padH, padV, padH, padV);

        textView = new TextView(context);
        textView.setTextColor(tokenResolver.brandColor());
        textView.setTextSize(vp(tokenResolver.fontOverlineSize()));
        textView.setGravity(Gravity.CENTER);

        LayoutParams lp = new LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER;
        addView(textView, lp);
    }

    public void setText(String text) {
        textView.setText(text);
    }

    public void setChipColor(int bgColor, int textColor) {
        bgDrawable.setColor(bgColor);
        setBackground(bgDrawable);
        textView.setTextColor(textColor);
    }

    private int vp(float vp) {
        return (int) (vp * density + 0.5f);
    }
}
