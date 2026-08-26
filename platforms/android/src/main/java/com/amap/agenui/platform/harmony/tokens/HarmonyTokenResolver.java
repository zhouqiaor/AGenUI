package com.amap.agenui.platform.harmony.tokens;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;

/**
 * Java-compatible accessor for HarmonyOS design tokens.
 *
 * Reads from auto-generated Android XML resources (harmony_colors.xml, harmony_dimens.xml).
 * Use this in pure Java/Android View code that doesn't depend on Jetpack Compose.
 *
 * For Compose code, use HarmonyColorTokens / HarmonyDimenTokens directly.
 */
public final class HarmonyTokenResolver {

    private final Resources res;

    public HarmonyTokenResolver(Context context) {
        this.res = context.getResources();
    }

    // ===== Colors (light mode — values-night auto-applies dark) =====

    public int brandColor() {
        return res.getColor(android.R.color.transparent, null) != 0
            ? getColor("harmony_brand_color")
            : Color.parseColor("#007DFF");
    }

    public int brandPressedColor() {
        return getColor("harmony_brand_pressed");
    }

    public int brandSurfaceColor() {
        return getColor("harmony_brand_surface");
    }

    public int successColor() {
        return getColor("harmony_success");
    }

    public int warningColor() {
        return getColor("harmony_warning");
    }

    public int dangerColor() {
        return getColor("harmony_danger");
    }

    public int textPrimaryColor() {
        return getColor("harmony_text_primary");
    }

    public int textSecondaryColor() {
        return getColor("harmony_text_secondary");
    }

    public int textTertiaryColor() {
        return getColor("harmony_text_tertiary");
    }

    public int textInverseColor() {
        return getColor("harmony_text_inverse");
    }

    public int surfacePrimaryColor() {
        return getColor("harmony_surface_primary");
    }

    public int surfaceMutedColor() {
        return getColor("harmony_surface_muted");
    }

    public int dividerColor() {
        return getColor("harmony_divider");
    }

    // ===== Dimensions =====

    public float fontDisplaySize() {
        return getDimen("harmony_font_display_size");
    }

    public float fontTitleSize() {
        return getDimen("harmony_font_title_size");
    }

    public float fontSubtitleSize() {
        return getDimen("harmony_font_subtitle_size");
    }

    public float fontBodySize() {
        return getDimen("harmony_font_body_size");
    }

    public float fontCaptionSize() {
        return getDimen("harmony_font_caption_size");
    }

    public float fontOverlineSize() {
        return getDimen("harmony_font_overline_size");
    }

    public float space2xs() {
        return getDimen("harmony_space_2xs");
    }

    public float spaceXs() {
        return getDimen("harmony_space_xs");
    }

    public float spaceSm() {
        return getDimen("harmony_space_sm");
    }

    public float spaceMd() {
        return getDimen("harmony_space_md");
    }

    public float spaceLg() {
        return getDimen("harmony_space_lg");
    }

    public float spaceXl() {
        return getDimen("harmony_space_xl");
    }

    public float radiusSm() {
        return getDimen("harmony_radius_sm");
    }

    public float radiusMd() {
        return getDimen("harmony_radius_md");
    }

    public float radiusLg() {
        return getDimen("harmony_radius_lg");
    }

    public float radiusFull() {
        return getDimen("harmony_radius_full");
    }

    // ===== Internal helpers =====

    private int getColor(String name) {
        int id = res.getIdentifier(name, "color", res.getResourcePackageName(android.R.color.black));
        if (id != 0) {
            return res.getColor(id, null);
        }
        return Color.TRANSPARENT;
    }

    private float getDimen(String name) {
        int id = res.getIdentifier(name, "dimen", res.getResourcePackageName(android.R.color.black));
        if (id != 0) {
            return res.getDimension(id);
        }
        return 0f;
    }
}
