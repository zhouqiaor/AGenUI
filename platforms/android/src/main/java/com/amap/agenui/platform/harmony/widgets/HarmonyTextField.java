package com.amap.agenui.platform.harmony.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;

import com.amap.agenui.platform.harmony.tokens.HarmonyTokenResolver;


/**
 * HarmonyOS-style TextField with surface_muted background.
 *
 * Visual specs:
 * - Background: surface_muted (#F5F6F7 light / #2A2A2E dark)
 * - Corner radius: 8vp (radius_sm)
 * - Border: 1vp divider color (focused: brand)
 * - Padding: 16vp horizontal, 12vp vertical
 * - Text: 16fp Regular, text_primary
 * - Hint: text_tertiary
 */
public class HarmonyTextField extends FrameLayout {

    private GradientDrawable bgDrawable;
    private EditText editText;
    private float density;
    private HarmonyTokenResolver tokenResolver;
    private int cornerRadius;
    private int paddingH;
    private int paddingV;
    private boolean isFocused = false;

    public HarmonyTextField(Context context) {
        super(context);
        init(context);
    }

    public HarmonyTextField(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        density = getResources().getDisplayMetrics().density;
        tokenResolver = new HarmonyTokenResolver(getContext());
        cornerRadius = vp(tokenResolver.radiusSm());
        paddingH = vp(tokenResolver.spaceMd());
        paddingV = vp(tokenResolver.spaceSm());

        bgDrawable = new GradientDrawable();
        bgDrawable.setCornerRadius(cornerRadius);
        applyBgState(false);
        setBackground(bgDrawable);

        editText = new EditText(context);
        editText.setTextColor(tokenResolver.textPrimaryColor());
        editText.setHintTextColor(tokenResolver.textTertiaryColor());
        editText.setTextSize(vp(tokenResolver.fontBodySize()));
        editText.setBackgroundColor(Color.TRANSPARENT);
        editText.setPadding(paddingH, paddingV, paddingH, paddingV);
        editText.setGravity(Gravity.CENTER_VERTICAL);

        LayoutParams lp = new LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT
        );
        addView(editText, lp);

        editText.setOnFocusChangeListener((v, hasFocus) -> {
            isFocused = hasFocus;
            applyBgState(hasFocus);
        });
    }

    private void applyBgState(boolean focused) {
        int bg = tokenResolver.surfaceMutedColor();
        int border = tokenResolver.dividerColor();
        if (focused) {
            border = tokenResolver.brandColor();
            bgDrawable.setStroke(vp(1), border);
        } else {
            bgDrawable.setStroke(vp(1), border);
        }
        bgDrawable.setColor(bg);
    }

    public EditText getEditText() {
        return editText;
    }

    public void setHint(String hint) {
        editText.setHint(hint);
    }

    public void setText(String text) {
        editText.setText(text);
    }

    public String getText() {
        return editText.getText() != null ? editText.getText().toString() : "";
    }

    private int vp(float vp) {
        return (int) (vp * density + 0.5f);
    }
}
