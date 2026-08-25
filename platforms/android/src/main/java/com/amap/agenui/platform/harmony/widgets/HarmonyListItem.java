package com.amap.agenui.platform.harmony.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.amap.agenui.platform.harmony.tokens.HarmonyTokenResolver;


/**
 * HarmonyOS-style ListItem with left-indented divider.
 *
 * Layout: [icon?] title (text_primary) / subtitle (text_secondary)
 * Divider: 1vp height, divider color, left-indented by space_md=16vp
 */
public class HarmonyListItem extends LinearLayout {

    private Paint dividerPaint;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private float dividerLeftIndent;
    private boolean showDivider = true;

    public HarmonyListItem(Context context) {
        super(context);
        init();
    }

    public HarmonyListItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        dividerLeftIndent = vp(tokenResolver.spaceMd());

        dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dividerPaint.setColor(tokenResolver.dividerColor());
        dividerPaint.setStyle(Paint.Style.FILL);

        setOrientation(VERTICAL);
        int padding = vp(tokenResolver.spaceSm());
        setPadding(padding, padding, padding, padding);
        setWillNotDraw(false);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (showDivider) {
            int height = getHeight();
            float dividerY = height - vp(1);
            canvas.drawRect(
                dividerLeftIndent, dividerY,
                getWidth(), height,
                dividerPaint
            );
        }
    }

    public void setShowDivider(boolean show) {
        this.showDivider = show;
        invalidate();
    }

    private int vp(float vp) {
        return (int) (vp * density + 0.5f);
    }
}
