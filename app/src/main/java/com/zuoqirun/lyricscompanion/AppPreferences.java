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
    static final String KEY_MAIN_OVERLAY_STYLE = "main_overlay_style";
    static final String KEY_SECONDARY_OVERLAY_STYLE = "secondary_overlay_style";
    static final String KEY_STYLE_BLUR = "style_blur";
    static final String KEY_STYLE_DIM = "style_dim";
    static final String KEY_STYLE_COVER_SIZE = "style_cover_size";
    static final String KEY_STYLE_LYRIC_LINES = "style_lyric_lines";
    static final String KEY_COMPONENT_LAYOUT = "component_layout";
    static final String KEY_TEXT_SCALE = "text_scale";
    static final String KEY_OPACITY = "opacity";
    static final String KEY_LYRIC_OFFSET = "lyric_offset";
    static final String KEY_LYRIC_SOURCE_OFFSET = "lyric_source_offset";
    static final String KEY_NEXT_LYRIC_SCALE = "next_lyric_scale";
    static final String KEY_NEXT_LYRIC_OPACITY = "next_lyric_opacity";
    static final String KEY_LYRIC_COLOR = "lyric_color";
    static final String KEY_SMOOTH_LYRIC_SCROLL = "smooth_lyric_scroll";
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
    static final String KEY_COMPACT_SHOW_COVER = "compact_show_cover";
    static final String KEY_COMPACT_SHOW_BARS = "compact_show_bars";
    static final String KEY_SHOW_PREVIOUS_BUTTON = "show_previous_button";
    static final String KEY_SHOW_PLAY_PAUSE_BUTTON = "show_play_pause_button";
    static final String KEY_SHOW_NEXT_BUTTON = "show_next_button";
    static final String KEY_CUSTOM_FONT_FILE = "custom_font_file";
    static final String KEY_COMMUNITY_CLIENT_ID = "community_client_id";
    static final String KEY_FEEDBACK_TICKETS = "feedback_tickets";
    static final String KEY_FEEDBACK_READ_REPLY_IDS = "feedback_read_reply_ids";
    static final String KEY_DIAGNOSTIC_UPLOAD_ENABLED = "diagnostic_upload_enabled";

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

    static boolean showPreviousButton(Context context) {
        return get(context).getBoolean(KEY_SHOW_PREVIOUS_BUTTON, true);
    }

    static boolean showPlayPauseButton(Context context) {
        return get(context).getBoolean(KEY_SHOW_PLAY_PAUSE_BUTTON, true);
    }

    static boolean showNextButton(Context context) {
        return get(context).getBoolean(KEY_SHOW_NEXT_BUTTON, true);
    }

    static int displayId(Context context) {
        return get(context).getInt(KEY_DISPLAY_ID, -1);
    }

    /**
     * Visual values are deliberately scoped per display.  The legacy unscoped key remains a
     * read-only migration fallback so existing installations retain their current appearance
     * until either display is adjusted for the first time.
     */
    private static String displayKey(String key, boolean secondary) {
        return key + (secondary ? "_secondary" : "_main");
    }

    static int displayInt(Context context, boolean secondary, String key, int fallback) {
        SharedPreferences preferences = get(context);
        String scoped = displayKey(key, secondary);
        return preferences.contains(scoped) ? preferences.getInt(scoped, fallback)
                : preferences.getInt(key, fallback);
    }

    static boolean displayBoolean(Context context, boolean secondary, String key,
                                  boolean fallback) {
        SharedPreferences preferences = get(context);
        String scoped = displayKey(key, secondary);
        return preferences.contains(scoped) ? preferences.getBoolean(scoped, fallback)
                : preferences.getBoolean(key, fallback);
    }

    static String displayString(Context context, boolean secondary, String key, String fallback) {
        SharedPreferences preferences = get(context);
        String scoped = displayKey(key, secondary);
        return preferences.contains(scoped) ? preferences.getString(scoped, fallback)
                : preferences.getString(key, fallback);
    }

    static void putDisplayInt(Context context, boolean secondary, String key, int value) {
        get(context).edit().putInt(displayKey(key, secondary), value).apply();
    }

    static void putDisplayBoolean(Context context, boolean secondary, String key, boolean value) {
        get(context).edit().putBoolean(displayKey(key, secondary), value).apply();
    }

    static void putDisplayString(Context context, boolean secondary, String key, String value) {
        get(context).edit().putString(displayKey(key, secondary), value).apply();
    }

    static float textScale(Context context) { return textScale(context, false); }

    static float textScale(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_TEXT_SCALE, 100) / 100f;
    }

    static float panelScale(Context context) {
        return get(context).getInt(KEY_PANEL_SCALE, 100) / 100f;
    }

    static int panelWidthDp(Context context) {
        return panelWidthDp(context, false);
    }

    static int panelWidthDp(Context context, boolean secondary) {
        String style = overlayStyle(context, secondary);
        return Math.max(minimumPanelWidthDp(context, secondary), Math.min(900,
                displayInt(context, secondary, panelWidthKey(style), defaultPanelWidthDp(style))));
    }

    static int panelHeightDp(Context context) {
        return panelHeightDp(context, false);
    }

    static int panelHeightDp(Context context, boolean secondary) {
        String style = overlayStyle(context, secondary);
        return Math.max(minimumPanelHeightDp(context, secondary), Math.min(600,
                displayInt(context, secondary, panelHeightKey(style), defaultPanelHeightDp(style))));
    }

    static int minimumPanelWidthDp(Context context) {
        return minimumPanelWidthDp(context, false);
    }

    static int minimumPanelWidthDp(Context context, boolean secondary) {
        return "compact".equals(overlayStyle(context, secondary)) ? 220 : 240;
    }

    static int minimumPanelHeightDp(Context context) {
        return minimumPanelHeightDp(context, false);
    }

    static int minimumPanelHeightDp(Context context, boolean secondary) {
        // Leave a dedicated bottom row for main-display transport controls while keeping
        // the lyric lines readable on both displays.
        return "compact".equals(overlayStyle(context, secondary)) ? 72 : 176;
    }

    static void setPanelWidthDp(Context context, int value) {
        setPanelWidthDp(context, false, value);
    }

    static void setPanelWidthDp(Context context, boolean secondary, int value) {
        putDisplayInt(context, secondary, panelWidthKey(overlayStyle(context, secondary)), value);
    }

    static void setPanelHeightDp(Context context, int value) {
        setPanelHeightDp(context, false, value);
    }

    static void setPanelHeightDp(Context context, boolean secondary, int value) {
        putDisplayInt(context, secondary, panelHeightKey(overlayStyle(context, secondary)), value);
    }

    static int opacity(Context context) { return opacity(context, false); }

    static int opacity(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_OPACITY, 88);
    }

    static int lyricOffsetMs(Context context) { return lyricOffsetMs(context, false); }

    static int lyricOffsetMs(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_LYRIC_OFFSET, 0);
    }

    /** The global correction remains the baseline; a player profile is an additive trim. */
    static int lyricOffsetMs(Context context, boolean secondary, String sourceId) {
        return lyricOffsetMs(context, secondary)
                + lyricSourceOffsetMs(context, secondary, sourceId);
    }

    static int lyricSourceOffsetMs(Context context, boolean secondary, String sourceId) {
        return displayInt(context, secondary, lyricSourceOffsetKey(sourceId), 0);
    }

    static void putLyricSourceOffsetMs(Context context, boolean secondary, String sourceId,
                                       int value) {
        putDisplayInt(context, secondary, lyricSourceOffsetKey(sourceId), value);
    }

    static int nextLyricScale(Context context, boolean secondary) {
        return Math.max(45, Math.min(120,
                displayInt(context, secondary, KEY_NEXT_LYRIC_SCALE, 70)));
    }

    static int nextLyricOpacity(Context context, boolean secondary) {
        return Math.max(20, Math.min(100,
                displayInt(context, secondary, KEY_NEXT_LYRIC_OPACITY, 100)));
    }

    /** Zero means that the selected overlay style keeps controlling lyric colors. */
    static int lyricColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_LYRIC_COLOR, 0);
    }

    static void setLyricColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_LYRIC_COLOR,
                color == 0 ? 0 : (color | 0xFF000000));
    }

    static boolean smoothLyricScroll(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_SMOOTH_LYRIC_SCROLL, true);
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
        return overlayStyle(context, false);
    }

    static String overlayStyle(Context context, boolean secondary) {
        SharedPreferences preferences = get(context);
        String key = secondary ? KEY_SECONDARY_OVERLAY_STYLE : KEY_MAIN_OVERLAY_STYLE;
        // A fresh install starts with the former Refined presentation. Existing users who had
        // explicitly picked the former classic card keep that "default" preference instead.
        String fallback = preferences.contains(KEY_OVERLAY_STYLE)
                ? preferences.getString(KEY_OVERLAY_STYLE, "refined")
                : secondary ? "compact" : "refined";
        return normalizeOverlayStyle(preferences.getString(key, fallback));
    }

    static void setOverlayStyle(Context context, boolean secondary, String style) {
        get(context).edit().putString(secondary ? KEY_SECONDARY_OVERLAY_STYLE
                : KEY_MAIN_OVERLAY_STYLE, normalizeOverlayStyle(style)).apply();
    }

    private static String normalizeOverlayStyle(String style) {
        if ("default".equals(style) || "refined".equals(style)
                || "compact".equals(style) || "pip".equals(style) || "custom".equals(style)) {
            return style;
        }
        return "refined";
    }

    static int styleBlur(Context context) { return styleBlur(context, false); }

    static int styleBlur(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_STYLE_BLUR, 128);
    }

    static int styleDim(Context context) { return styleDim(context, false); }

    static int styleDim(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_STYLE_DIM, 38);
    }

    static float styleCoverScale(Context context) { return styleCoverScale(context, false); }

    static float styleCoverScale(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_STYLE_COVER_SIZE, 100) / 100f;
    }

    static int styleLyricLines(Context context) { return styleLyricLines(context, false); }

    static int styleLyricLines(Context context, boolean secondary) {
        return Math.max(1, Math.min(3,
                displayInt(context, secondary, KEY_STYLE_LYRIC_LINES, 3)));
    }

    static String refinedDisplayMode(Context context) { return refinedDisplayMode(context, false); }
    static String refinedDisplayMode(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_REFINED_DISPLAY_MODE, "all");
    }

    static String refinedColorScheme(Context context) { return refinedColorScheme(context, false); }
    static String refinedColorScheme(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_REFINED_COLOR_SCHEME, "auto");
    }

    static String refinedAccentVariant(Context context) { return refinedAccentVariant(context, false); }
    static String refinedAccentVariant(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_REFINED_ACCENT_VARIANT, "primary");
    }

    static String refinedTextEffect(Context context) { return refinedTextEffect(context, false); }
    static String refinedTextEffect(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_REFINED_TEXT_EFFECT, "none");
    }

    static boolean refinedProgressBottom(Context context) { return refinedProgressBottom(context, false); }
    static boolean refinedProgressBottom(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_PROGRESS_BOTTOM, true);
    }

    static String refinedCoverHorizontal(Context context) { return refinedCoverHorizontal(context, false); }
    static String refinedCoverHorizontal(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_REFINED_COVER_HORIZONTAL, "left");
    }

    static String refinedCoverVertical(Context context) { return refinedCoverVertical(context, false); }
    static String refinedCoverVertical(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_REFINED_COVER_VERTICAL, "bottom");
    }

    static boolean refinedRectangleCover(Context context) { return refinedRectangleCover(context, false); }
    static boolean refinedRectangleCover(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_RECTANGLE_COVER, true);
    }

    static boolean refinedCoverShadow(Context context) { return refinedCoverShadow(context, false); }
    static boolean refinedCoverShadow(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_COVER_SHADOW, false);
    }

    static String refinedBackgroundType(Context context) { return refinedBackgroundType(context, false); }
    static String refinedBackgroundType(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_REFINED_BACKGROUND_TYPE, "blur");
    }

    static boolean refinedStaticFluid(Context context) { return refinedStaticFluid(context, false); }
    static boolean refinedStaticFluid(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_STATIC_FLUID, false);
    }

    static boolean refinedDynamicGradient(Context context) { return refinedDynamicGradient(context, false); }
    static boolean refinedDynamicGradient(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_DYNAMIC_GRADIENT, true);
    }

    static int refinedLyricFontSize(Context context) { return refinedLyricFontSize(context, false); }
    static int refinedLyricFontSize(Context context, boolean secondary) {
        return Math.max(16, Math.min(64,
                displayInt(context, secondary, KEY_REFINED_LYRIC_FONT_SIZE, 16)));
    }

    static String customFontFile(Context context) {
        return get(context).getString(KEY_CUSTOM_FONT_FILE, "");
    }

    static void setCustomFontFile(Context context, String fileName) {
        get(context).edit().putString(KEY_CUSTOM_FONT_FILE,
                fileName == null ? "" : fileName).apply();
    }

    static boolean refinedOriginalBold(Context context) { return refinedOriginalBold(context, false); }
    static boolean refinedOriginalBold(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_ORIGINAL_BOLD, true);
    }

    static boolean refinedLyricFade(Context context) { return refinedLyricFade(context, false); }
    static boolean refinedLyricFade(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_LYRIC_FADE, false);
    }

    static boolean refinedLyricZoom(Context context) { return refinedLyricZoom(context, false); }
    static boolean refinedLyricZoom(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_LYRIC_ZOOM, false);
    }

    static boolean refinedLyricBlur(Context context) { return refinedLyricBlur(context, false); }
    static boolean refinedLyricBlur(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_LYRIC_BLUR, false);
    }

    static boolean refinedLyricRotate(Context context) { return refinedLyricRotate(context, false); }
    static boolean refinedLyricRotate(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_LYRIC_ROTATE, true);
    }

    static int refinedRotateCurvature(Context context) { return refinedRotateCurvature(context, false); }
    static int refinedRotateCurvature(Context context, boolean secondary) {
        return Math.max(10, Math.min(80,
                displayInt(context, secondary, KEY_REFINED_ROTATE_CURVATURE, 10)));
    }

    static String refinedKaraokeAnimation(Context context) { return refinedKaraokeAnimation(context, false); }
    static String refinedKaraokeAnimation(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_REFINED_KARAOKE_ANIMATION, "float");
    }

    static int refinedCurrentAlign(Context context) { return refinedCurrentAlign(context, false); }
    static int refinedCurrentAlign(Context context, boolean secondary) {
        int value;
        try {
            value = Integer.parseInt(displayString(context, secondary,
                    KEY_REFINED_CURRENT_ALIGN, "50"));
        } catch (Exception ignored) {
            value = 50;
        }
        return value <= 30 ? 30 : 50;
    }

    static boolean refinedShowTranslation(Context context) { return refinedShowTranslation(context, false); }
    static boolean refinedShowTranslation(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_SHOW_TRANSLATION, true);
    }

    static boolean refinedLyricGlow(Context context) { return refinedLyricGlow(context, false); }
    static boolean refinedLyricGlow(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_REFINED_LYRIC_GLOW, true);
    }

    static boolean compactShowCover(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_COMPACT_SHOW_COVER, true);
    }

    static boolean compactShowBars(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_COMPACT_SHOW_BARS, true);
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

    private static String lyricSourceOffsetKey(String sourceId) {
        String safe = sourceId == null ? "media" : sourceId.trim().toLowerCase();
        if (!safe.matches("[a-z0-9_]+")) safe = "media";
        return KEY_LYRIC_SOURCE_OFFSET + "_" + safe;
    }
}
