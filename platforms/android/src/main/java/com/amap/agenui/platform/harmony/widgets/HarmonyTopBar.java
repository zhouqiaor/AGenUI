package com.amap.agenui.platform.harmony.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.amap.agenui.platform.harmony.tokens.HarmonyTokenResolver;


/**
 * HarmonyOS-style TopBar with blur background and centered title.
 *
 * Visual specs:
 * - Height: 48vp
 * - Background: surface with 85% opacity (simulating blur)
 * - Title: centered, 18fp Medium, text_primary
 * - Bottom divider: 1vp, divider color
 */
public class HarmonyTopBar extends FrameLayout {

    private Paint bgPaint;
    private Paint dividerPaint;
    private TextView titleView;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private int barHeight;

    public HarmonyTopBar(Context context) {
        super(context);
        init(context);
    }

    public HarmonyTopBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        barHeight = vp(tokenResolver.spaceXl());

        int surfaceColor = tokenResolver.surfacePrimaryColor();
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.argb(217, Color.red(surfaceColor), Color.green(surfaceColor), Color.blue(surfaceColor)));
        bgPaint.setStyle(Paint.Style.FILL);

        dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(tokenResolver.dividerColor());
        dividerPaint.setStyle(Paint.Style.FILL);

        titleView = new TextView(context);
        titleView.setTextColor(tokenResolver.textPrimaryColor());
        titleView.setGravity(Gravity.CENTER);
        titleView.setTextSize(vp(tokenResolver.fontSubtitleSize()));
        titleView.setTypeface(titleView.getTypeface(), android.graphics.Typeface.NORMAL);
        int titleStyle = android.graphics.Typeface.NORMAL;
        FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT
        );
        titleLp.gravity = Gravity.CENTER;
        addView(titleView, titleLp);

        setMinimumHeight(barHeight);
        setWillNotDraw(false);
    }

    public void setTitle(String title) {
        titleView.setText(title);
    }

    public void setTitle(String title, int color, float sizeFp) {
        titleView.setText(title);
        titleView.setTextColor(color);
        titleView.setTextSize(vp(sizeFp));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, bgPaint);
        float dividerY = h - vp(1);
        canvas.drawRect(0, dividerY, w, h, dividerPaint);
        super.onDraw(canvas);
    }

    private int vp(float vp) {
        return (int) (vp * density + 0.5f);
    }
}
