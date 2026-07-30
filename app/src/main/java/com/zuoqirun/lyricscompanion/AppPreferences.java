package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPreferences {
    static final String FILE = "lyrics_companion";
    static final String KEY_MAIN_OVERLAY = "main_overlay";
    static final String KEY_SECONDARY_OVERLAY = "secondary_overlay";
    static final String KEY_DISPLAY_ID = "display_id";
    static final String KEY_PANEL_SCALE = "panel_scale";
    static final String KEY_PANEL_WIDTH_DP = "panel_width_dp";
    static final String KEY_PANEL_HEIGHT_DP = "panel_height_dp";
    static final String KEY_COMPACT_PANEL_WIDTH_DP = "compact_panel_width_dp";
    static final String KEY_COMPACT_PANEL_HEIGHT_DP = "compact_panel_height_dp";
    static final String KEY_OVERLAY_STYLE = "overlay_style";
    static final String KEY_STYLE_BLUR = "style_blur";
    static final String KEY_STYLE_DIM = "style_dim";
    static final String KEY_STYLE_COVER_SIZE = "style_cover_size";
    static final String KEY_STYLE_LYRIC_LINES = "style_lyric_lines";
    static final String KEY_COMPONENT_LAYOUT = "component_layout";
    static final String KEY_TEXT_SCALE = "text_scale";
    static final String KEY_OPACITY = "opacity";
    static final String KEY_LYRIC_OFFSET = "lyric_offset";
    static final String KEY_LYRIC_CATALOG = "lyric_catalog";
    static final String KEY_PLAYER_CATALOG_FALLBACK = "player_catalog_fallback";
    static final String KEY_MAIN_X = "main_x";
    static final String KEY_MAIN_Y = "main_y";
    static final String KEY_SECONDARY_X = "secondary_x";
    static final String KEY_SECONDARY_Y = "secondary_y";
    static final String KEY_REFINED_DISPLAY_MODE = "refined_display_mode";
    static final String KEY_REFINED_COLOR_SCHEME = "refined_color_scheme";
    static final String KEY_REFINED_ACCENT_VARIANT = "refined_accent_variant";
    static final String KEY_REFINED_TEXT_EFFECT = "refined_text_effect";
    static final String KEY_REFINED_PROGRESS_BOTTOM = "refined_progress_bottom";
    static final String KEY_REFINED_COVER_HORIZONTAL = "refined_cover_horizontal";
    static final String KEY_REFINED_COVER_VERTICAL = "refined_cover_vertical";
    static final String KEY_REFINED_RECTANGLE_COVER = "refined_rectangle_cover";
    static final String KEY_REFINED_COVER_SHADOW = "refined_cover_shadow";
    static final String KEY_REFINED_BACKGROUND_TYPE = "refined_background_type";
    static final String KEY_REFINED_STATIC_FLUID = "refined_static_fluid";
    static final String KEY_REFINED_DYNAMIC_GRADIENT = "refined_dynamic_gradient";
    static final String KEY_REFINED_LYRIC_FONT_SIZE = "refined_lyric_font_size";
    static final String KEY_REFINED_ORIGINAL_BOLD = "refined_original_bold";
    static final String KEY_REFINED_LYRIC_FADE = "refined_lyric_fade";
    static final String KEY_REFINED_LYRIC_ZOOM = "refined_lyric_zoom";
    static final String KEY_REFINED_LYRIC_BLUR = "refined_lyric_blur";
    static final String KEY_REFINED_LYRIC_ROTATE = "refined_lyric_rotate";
    static final String KEY_REFINED_ROTATE_CURVATURE = "refined_rotate_curvature";
    static final String KEY_REFINED_KARAOKE_ANIMATION = "refined_karaoke_animation";
    static final String KEY_REFINED_CURRENT_ALIGN = "refined_current_align";
    static final String KEY_REFINED_SHOW_TRANSLATION = "refined_show_translation";
    static final String KEY_REFINED_LYRIC_GLOW = "refined_lyric_glow";
    static final String KEY_CUSTOM_FONT_FILE = "custom_font_file";
    static final String KEY_COMMUNITY_CLIENT_ID = "community_client_id";

    private AppPreferences() {}

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean mainEnabled(Context context) {
        return get(context).getBoolean(KEY_MAIN_OVERLAY, false);
    }

    static boolean secondaryEnabled(Context context) {
        return get(context).getBoolean(KEY_SECONDARY_OVERLAY, false);
    }

    static int displayId(Context context) {
        return get(context).getInt(KEY_DISPLAY_ID, -1);
    }

    static float textScale(Context context) {
        return get(context).getInt(KEY_TEXT_SCALE, 100) / 100f;
    }

    static float panelScale(Context context) {
        return get(context).getInt(KEY_PANEL_SCALE, 100) / 100f;
    }

    static int panelWidthDp(Context context) {
        String style = overlayStyle(context);
        return Math.max(minimumPanelWidthDp(context), Math.min(900, get(context).getInt(
                panelWidthKey(style), defaultPanelWidthDp(style))));
    }

    static int panelHeightDp(Context context) {
        String style = overlayStyle(context);
        return Math.max(minimumPanelHeightDp(context), Math.min(600, get(context).getInt(
                panelHeightKey(style), defaultPanelHeightDp(style))));
    }

    static int minimumPanelWidthDp(Context context) {
        return "compact".equals(overlayStyle(context)) ? 220 : 240;
    }

    static int minimumPanelHeightDp(Context context) {
        return "compact".equals(overlayStyle(context)) ? 72 : 140;
    }

    static void setPanelWidthDp(Context context, int value) {
        get(context).edit().putInt(panelWidthKey(overlayStyle(context)), value).apply();
    }

    static void setPanelHeightDp(Context context, int value) {
        get(context).edit().putInt(panelHeightKey(overlayStyle(context)), value).apply();
    }

    static int opacity(Context context) {
        return get(context).getInt(KEY_OPACITY, 88);
    }

    static int lyricOffsetMs(Context context) {
        return get(context).getInt(KEY_LYRIC_OFFSET, 0);
    }

    static String lyricCatalog(Context context) {
        String value = get(context).getString(KEY_LYRIC_CATALOG, "auto");
        if ("netease".equals(value) || "qqmusic".equals(value)
                || "kugou".equals(value) || "kuwo".equals(value)
                || "soda".equals(value)) return value;
        return "auto";
    }

    static boolean playerCatalogFallback(Context context) {
        return get(context).getBoolean(KEY_PLAYER_CATALOG_FALLBACK, true);
    }

    static String overlayStyle(Context context) {
        return get(context).getString(KEY_OVERLAY_STYLE, "default");
    }

    static int styleBlur(Context context) {
        return get(context).getInt(KEY_STYLE_BLUR, 72);
    }

    static int styleDim(Context context) {
        return get(context).getInt(KEY_STYLE_DIM, 38);
    }

    static float styleCoverScale(Context context) {
        return get(context).getInt(KEY_STYLE_COVER_SIZE, 100) / 100f;
    }

    static int styleLyricLines(Context context) {
        return Math.max(1, Math.min(3, get(context).getInt(KEY_STYLE_LYRIC_LINES, 3)));
    }

    static String refinedDisplayMode(Context context) {
        return get(context).getString(KEY_REFINED_DISPLAY_MODE, "all");
    }

    static String refinedColorScheme(Context context) {
        return get(context).getString(KEY_REFINED_COLOR_SCHEME, "auto");
    }

    static String refinedAccentVariant(Context context) {
        return get(context).getString(KEY_REFINED_ACCENT_VARIANT, "primary");
    }

    static String refinedTextEffect(Context context) {
        return get(context).getString(KEY_REFINED_TEXT_EFFECT, "none");
    }

    static boolean refinedProgressBottom(Context context) {
        return get(context).getBoolean(KEY_REFINED_PROGRESS_BOTTOM, false);
    }

    static String refinedCoverHorizontal(Context context) {
        return get(context).getString(KEY_REFINED_COVER_HORIZONTAL, "left");
    }

    static String refinedCoverVertical(Context context) {
        return get(context).getString(KEY_REFINED_COVER_VERTICAL, "bottom");
    }

    static boolean refinedRectangleCover(Context context) {
        return get(context).getBoolean(KEY_REFINED_RECTANGLE_COVER, true);
    }

    static boolean refinedCoverShadow(Context context) {
        return get(context).getBoolean(KEY_REFINED_COVER_SHADOW, true);
    }

    static String refinedBackgroundType(Context context) {
        return get(context).getString(KEY_REFINED_BACKGROUND_TYPE, "blur");
    }

    static boolean refinedStaticFluid(Context context) {
        return get(context).getBoolean(KEY_REFINED_STATIC_FLUID, false);
    }

    static boolean refinedDynamicGradient(Context context) {
        return get(context).getBoolean(KEY_REFINED_DYNAMIC_GRADIENT, true);
    }

    static int refinedLyricFontSize(Context context) {
        return Math.max(16, Math.min(64,
                get(context).getInt(KEY_REFINED_LYRIC_FONT_SIZE, 32)));
    }

    static String customFontFile(Context context) {
        return get(context).getString(KEY_CUSTOM_FONT_FILE, "");
    }

    static void setCustomFontFile(Context context, String fileName) {
        get(context).edit().putString(KEY_CUSTOM_FONT_FILE,
                fileName == null ? "" : fileName).apply();
    }

    static boolean refinedOriginalBold(Context context) {
        return get(context).getBoolean(KEY_REFINED_ORIGINAL_BOLD, true);
    }

    static boolean refinedLyricFade(Context context) {
        return get(context).getBoolean(KEY_REFINED_LYRIC_FADE, false);
    }

    static boolean refinedLyricZoom(Context context) {
        return get(context).getBoolean(KEY_REFINED_LYRIC_ZOOM, false);
    }

    static boolean refinedLyricBlur(Context context) {
        return get(context).getBoolean(KEY_REFINED_LYRIC_BLUR, false);
    }

    static boolean refinedLyricRotate(Context context) {
        return get(context).getBoolean(KEY_REFINED_LYRIC_ROTATE, false);
    }

    static int refinedRotateCurvature(Context context) {
        return Math.max(10, Math.min(80,
                get(context).getInt(KEY_REFINED_ROTATE_CURVATURE, 30)));
    }

    static String refinedKaraokeAnimation(Context context) {
        return get(context).getString(KEY_REFINED_KARAOKE_ANIMATION, "float");
    }

    static int refinedCurrentAlign(Context context) {
        int value;
        try {
            value = Integer.parseInt(get(context).getString(KEY_REFINED_CURRENT_ALIGN, "50"));
        } catch (Exception ignored) {
            value = 50;
        }
        return value <= 30 ? 30 : 50;
    }

    static boolean refinedShowTranslation(Context context) {
        return get(context).getBoolean(KEY_REFINED_SHOW_TRANSLATION, true);
    }

    static boolean refinedLyricGlow(Context context) {
        return get(context).getBoolean(KEY_REFINED_LYRIC_GLOW, true);
    }

    private static int defaultPanelWidthDp(String style) {
        if ("refined".equals(style)) return 560;
        if ("compact".equals(style)) return 320;
        if ("pip".equals(style)) return 440;
        if ("custom".equals(style)) return 460;
        return 390;
    }

    private static int defaultPanelHeightDp(String style) {
        if ("refined".equals(style)) return 300;
        // This leaves room for the optional translation and the compact playback bars while
        // remaining a small horizontal overlay.
        if ("compact".equals(style)) return 104;
        if ("pip".equals(style)) return 220;
        if ("custom".equals(style)) return 260;
        return 226;
    }

    private static String panelWidthKey(String style) {
        return "compact".equals(style) ? KEY_COMPACT_PANEL_WIDTH_DP : KEY_PANEL_WIDTH_DP;
    }

    private static String panelHeightKey(String style) {
        return "compact".equals(style) ? KEY_COMPACT_PANEL_HEIGHT_DP : KEY_PANEL_HEIGHT_DP;
    }

    static void changed(Context context) {
        LyricsDisplayService.startOrRefresh(context);
    }
}
