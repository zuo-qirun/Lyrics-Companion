package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

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
    static final String KEY_AMLL_PANEL_WIDTH_DP = "amll_panel_width_dp";
    static final String KEY_AMLL_PANEL_HEIGHT_DP = "amll_panel_height_dp";
    static final String KEY_OVERLAY_STYLE = "overlay_style";
    static final String KEY_MAIN_OVERLAY_STYLE = "main_overlay_style";
    static final String KEY_SECONDARY_OVERLAY_STYLE = "secondary_overlay_style";
    static final String KEY_STYLE_BLUR = "style_blur";
    static final String KEY_STYLE_DIM = "style_dim";
    static final String KEY_STYLE_COVER_SIZE = "style_cover_size";
    static final String KEY_STYLE_LYRIC_LINES = "style_lyric_lines";
    static final String KEY_COMPONENT_LAYOUT = "component_layout";
    static final String KEY_TEXT_SCALE = "text_scale";
    static final String KEY_TITLE_SCALE = "title_scale";
    static final String KEY_THEME_MODE = "theme_mode";
    static final String KEY_LYRICS_FOLLOW_THEME = "lyrics_follow_theme";
    static final String KEY_OPACITY = "opacity";
    static final String KEY_LYRIC_OFFSET = "lyric_offset";
    static final String KEY_LYRIC_SOURCE_OFFSET = "lyric_source_offset";
    static final String KEY_NEXT_LYRIC_SCALE = "next_lyric_scale";
    static final String KEY_NEXT_LYRIC_OPACITY = "next_lyric_opacity";
    static final String KEY_LYRIC_COLOR = "lyric_color";
    static final String KEY_LYRIC_LIGHT_COLOR = "lyric_light_color";
    static final String KEY_LYRIC_DARK_COLOR = "lyric_dark_color";
    static final String KEY_TITLE_COLOR = "title_color";
    static final String KEY_ARTIST_COLOR = "artist_color";
    static final String KEY_PLAYER_COLOR = "player_color";
    static final String KEY_LYRIC_SOURCE_COLOR = "lyric_source_color";
    static final String KEY_BACKGROUND_LIGHT_COLOR = "background_light_color";
    static final String KEY_BACKGROUND_DARK_COLOR = "background_dark_color";
    static final String KEY_SMOOTH_LYRIC_SCROLL = "smooth_lyric_scroll";
    static final String KEY_LYRIC_CATALOG = "lyric_catalog";
    /** Optional per-player-category override kept for backward compatibility. */
    static final String KEY_PLAYER_LYRIC_CATALOG = "player_lyric_catalog";
    /** Exact package names that have published a playable MediaSession on this device. */
    static final String KEY_OBSERVED_PLAYER_PACKAGES = "observed_player_packages";
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
    static final String KEY_COMPACT_SHOW_NEXT_LINE = "compact_show_next_line";
    static final String KEY_COMPACT_USE_REAL_SPECTRUM = "compact_use_real_spectrum";
    static final String KEY_COMPACT_SPECTRUM_COLOR = "compact_spectrum_color";
    static final String KEY_SPECTRUM_ENABLED = "spectrum_enabled";
    static final String KEY_SPECTRUM_STYLE = "spectrum_style";
    static final String KEY_SPECTRUM_COLOR_MODE = "spectrum_color_mode";
    static final String KEY_REAL_SPECTRUM_CAPTURE_RATE = "real_spectrum_capture_rate";
    static final String KEY_TAP_OVERLAY_RETURNS_TO_PLAYER = "tap_overlay_returns_to_player";
    static final String KEY_LAUNCH_OVERLAY_ON_ICON = "launch_overlay_on_icon";
    static final String KEY_LAUNCH_OVERLAY_LAST_AT = "launch_overlay_last_at";
    static final String KEY_AUTO_START_OVERLAYS = "auto_start_overlays";
    static final String KEY_SERVICE_STOPPED_BY_USER = "service_stopped_by_user";
    static final String KEY_MAIN_OVERLAY_TOUCH_THROUGH = "main_overlay_touch_through";
    static final String KEY_SECONDARY_OVERLAY_TOUCH_THROUGH =
            "secondary_overlay_touch_through";
    static final String KEY_HIDE_OVERLAYS_WHEN_NOT_PLAYING =
            "hide_overlays_when_not_playing";
    static final String KEY_HIDE_OVERLAYS_IN_PLAYER = "hide_overlays_in_player";
    static final String KEY_HIDE_OVERLAYS_IN_APPS = "hide_overlays_in_apps";
    static final String KEY_SHOW_PREVIOUS_BUTTON = "show_previous_button";
    static final String KEY_SHOW_PLAY_PAUSE_BUTTON = "show_play_pause_button";
    static final String KEY_SHOW_NEXT_BUTTON = "show_next_button";
    static final String KEY_PLAYBACK_CONTROL_SCALE = "playback_control_scale";
    static final String KEY_PLAYBACK_CONTROL_X = "playback_control_x";
    static final String KEY_PLAYBACK_CONTROL_Y = "playback_control_y";
    static final String KEY_FULLSCREEN_CLOSE_MODE = "fullscreen_close_mode";
    static final String KEY_OVERLAY_CLOSE_MODE = "overlay_close_mode";
    static final String KEY_NOTIFICATION_LYRICS = "notification_lyrics";
    static final String KEY_TOP_LYRIC_STRIP = "top_lyric_strip";
    /** Zero keeps the top lyric strip white so it stays legible over most wallpapers. */
    static final String KEY_STATUS_LYRIC_COLOR = "status_lyric_color";
    static final String KEY_STATUS_LYRIC_FOLLOW_THEME = "status_lyric_follow_theme";
    static final String KEY_STATUS_LYRIC_LIGHT_COLOR = "status_lyric_light_color";
    static final String KEY_STATUS_LYRIC_DARK_COLOR = "status_lyric_dark_color";
    static final String KEY_TOP_LYRIC_FONT_SCALE = "top_lyric_font_scale";
    static final String KEY_TOP_LYRIC_REGION_PERCENT = "top_lyric_region_percent";
    static final String KEY_TOP_LYRIC_OFFSET_X_DP = "top_lyric_offset_x_dp";
    static final String KEY_TOP_LYRIC_OFFSET_Y_DP = "top_lyric_offset_y_dp";
    static final String KEY_TOP_LYRIC_SHOW_TRANSLATION = "top_lyric_show_translation";
    static final String KEY_TOP_LYRIC_BACKGROUND = "top_lyric_background";
    static final String KEY_TOP_LYRIC_SPECTRUM = "top_lyric_spectrum";
    static final String KEY_BOTTOM_SPECTRUM = "bottom_spectrum";
    static final String KEY_BOTTOM_SPECTRUM_HEIGHT_DP = "bottom_spectrum_height_dp";
    static final String KEY_LOCAL_LYRIC_ENABLED = "local_lyric_enabled";
    static final String KEY_LOCAL_LYRIC_DIRECTORY_URI = "local_lyric_directory_uri";
    static final String KEY_AVRCP_ENABLED = "avrcp_enabled";
    static final String KEY_LOCKSCREEN_LYRICS = "lockscreen_lyrics";
    static final String KEY_CUSTOM_FONT_FILE = "custom_font_file";
    static final String KEY_COMMUNITY_CLIENT_ID = "community_client_id";
    static final String KEY_FEEDBACK_TICKETS = "feedback_tickets";
    static final String KEY_LAST_FEEDBACK_ID = "last_feedback_id";
    static final String KEY_FEEDBACK_READ_REPLY_IDS = "feedback_read_reply_ids";
    static final String KEY_FAQ_CACHE = "faq_cache";
    static final String KEY_DIAGNOSTIC_UPLOAD_ENABLED = "diagnostic_upload_enabled";
    static final String KEY_COMMUNITY_ANNOUNCEMENT_DISMISSED =
            "community_announcement_dismissed";
    static final String KEY_SAFETY_NOTICE_SEEN = "safety_notice_seen";

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

    static int titleScale(Context context, boolean secondary) {
        return Math.max(60, Math.min(180,
                displayInt(context, secondary, KEY_TITLE_SCALE, 100)));
    }

    static String themeMode(Context context) {
        String value = get(context).getString(KEY_THEME_MODE, "auto");
        return "light".equals(value) || "dark".equals(value) ? value : "auto";
    }

    static void setThemeMode(Context context, String value) {
        String normalized = "light".equals(value) || "dark".equals(value) ? value : "auto";
        get(context).edit().putString(KEY_THEME_MODE, normalized).apply();
    }

    /** Disabled by default so switching the settings theme does not recolor overlays. */
    static boolean lyricsFollowTheme(Context context) {
        return get(context).getBoolean(KEY_LYRICS_FOLLOW_THEME, false);
    }

    static void setLyricsFollowTheme(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_LYRICS_FOLLOW_THEME, enabled).apply();
    }

    static float panelScale(Context context) {
        return get(context).getInt(KEY_PANEL_SCALE, 100) / 100f;
    }

    static int panelWidthDp(Context context) {
        return panelWidthDp(context, false);
    }

    static int panelWidthDp(Context context, boolean secondary) {
        String style = overlayStyle(context, secondary);
        return Math.max(minimumPanelWidthDp(context, secondary),
                displayInt(context, secondary, panelWidthKey(style), defaultPanelWidthDp(style)));
    }

    static int panelHeightDp(Context context) {
        return panelHeightDp(context, false);
    }

    static int panelHeightDp(Context context, boolean secondary) {
        String style = overlayStyle(context, secondary);
        return Math.max(minimumPanelHeightDp(context, secondary),
                displayInt(context, secondary, panelHeightKey(style), defaultPanelHeightDp(style)));
    }

    static int minimumPanelWidthDp(Context context) {
        return minimumPanelWidthDp(context, false);
    }

    static int minimumPanelWidthDp(Context context, boolean secondary) {
        String style = overlayStyle(context, secondary);
        return "compact".equals(style) ? 220 : "amll".equals(style) ? 360 : 240;
    }

    static int minimumPanelHeightDp(Context context) {
        return minimumPanelHeightDp(context, false);
    }

    static int minimumPanelHeightDp(Context context, boolean secondary) {
        // Leave a dedicated bottom row for main-display transport controls while keeping
        // the lyric lines readable on both displays.
        String style = overlayStyle(context, secondary);
        if ("compact".equals(style)) {
            return compactShowNextLine(context, secondary) ? 96 : 72;
        }
        return "amll".equals(style) ? 210 : 176;
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
        return Math.max(45, Math.min(160,
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

    static int lyricLightColor(Context context, boolean secondary) {
        int legacy = lyricColor(context, secondary);
        return displayInt(context, secondary, KEY_LYRIC_LIGHT_COLOR,
                legacy == 0 ? 0xFF17212E : legacy);
    }

    static int lyricDarkColor(Context context, boolean secondary) {
        int legacy = lyricColor(context, secondary);
        return displayInt(context, secondary, KEY_LYRIC_DARK_COLOR,
                legacy == 0 ? 0xFFF5F8FF : legacy);
    }

    static void setLyricLightColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_LYRIC_LIGHT_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    static void setLyricDarkColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_LYRIC_DARK_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    /** Zero leaves the active style in control of metadata colors. */
    static int titleColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_TITLE_COLOR, 0);
    }

    static int artistColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_ARTIST_COLOR, 0);
    }

    static int playerColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_PLAYER_COLOR, 0);
    }

    static int lyricSourceColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_LYRIC_SOURCE_COLOR, 0);
    }

    static void setMetadataColor(Context context, boolean secondary, String key, int color) {
        putDisplayInt(context, secondary, key, color == 0 ? 0 : color | 0xFF000000);
    }

    static int backgroundLightColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_BACKGROUND_LIGHT_COLOR, 0);
    }

    static int backgroundDarkColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_BACKGROUND_DARK_COLOR, 0);
    }

    static void setBackgroundColor(Context context, boolean secondary, boolean light, int color) {
        putDisplayInt(context, secondary,
                light ? KEY_BACKGROUND_LIGHT_COLOR : KEY_BACKGROUND_DARK_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    static boolean smoothLyricScroll(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_SMOOTH_LYRIC_SCROLL, true);
    }

    static String lyricCatalog(Context context) {
        return normalizeLyricCatalog(get(context).getString(KEY_LYRIC_CATALOG, "auto"));
    }

    /** Resolves a player-specific rule first, then retains the existing global default. */
    static String lyricCatalog(Context context, String sourceId) {
        String override = get(context).getString(playerLyricCatalogKey(sourceId), "");
        return resolveLyricCatalog(override, lyricCatalog(context));
    }

    /**
     * Resolves the rule for one concrete player application.  Package-specific rules deliberately
     * do not share a value with another application that happens to use the same provider type.
     */
    static String lyricCatalog(Context context, String sourceId, String packageName) {
        String override = get(context).getString(playerPackageLyricCatalogKey(packageName), "");
        return resolveLyricCatalog(override, lyricCatalog(context));
    }

    static String resolveLyricCatalog(String playerOverride, String fallback) {
        return playerOverride == null || playerOverride.trim().isEmpty()
                ? normalizeLyricCatalog(fallback) : normalizeLyricCatalog(playerOverride);
    }

    /** Empty removes the override so this player follows the global default again. */
    static void putPlayerLyricCatalog(Context context, String sourceId, String catalog) {
        SharedPreferences.Editor editor = get(context).edit();
        if (catalog == null || catalog.trim().isEmpty()) {
            editor.remove(playerLyricCatalogKey(sourceId));
        } else {
            editor.putString(playerLyricCatalogKey(sourceId), normalizeLyricCatalog(catalog));
        }
        editor.apply();
    }

    static String playerLyricCatalogOverride(Context context, String sourceId) {
        String value = get(context).getString(playerLyricCatalogKey(sourceId), "");
        return value == null || value.trim().isEmpty() ? "" : normalizeLyricCatalog(value);
    }

    static void putPlayerPackageLyricCatalog(Context context, String packageName, String catalog) {
        SharedPreferences.Editor editor = get(context).edit();
        if (catalog == null || catalog.trim().isEmpty()) {
            editor.remove(playerPackageLyricCatalogKey(packageName));
        } else {
            editor.putString(playerPackageLyricCatalogKey(packageName), normalizeLyricCatalog(catalog));
        }
        editor.apply();
    }

    static String playerPackageLyricCatalogOverride(Context context, String packageName) {
        String value = get(context).getString(playerPackageLyricCatalogKey(packageName), "");
        return value == null || value.trim().isEmpty() ? "" : normalizeLyricCatalog(value);
    }

    /** A package selected from the catalog-centred app list must not fall through to another catalog. */
    static boolean hasForcedPlayerPackageCatalog(Context context, String packageName) {
        return !playerPackageLyricCatalogOverride(context, packageName).isEmpty();
    }

    static void rememberPlayerPackage(Context context, String packageName) {
        String normalized = normalizePackageName(packageName);
        if (normalized.isEmpty()) return;
        Set<String> current = get(context).getStringSet(KEY_OBSERVED_PLAYER_PACKAGES,
                Collections.emptySet());
        if (current.contains(normalized)) return;
        Set<String> updated = new LinkedHashSet<>(current);
        updated.add(normalized);
        get(context).edit().putStringSet(KEY_OBSERVED_PLAYER_PACKAGES, updated).apply();
    }

    static Set<String> observedPlayerPackages(Context context) {
        return new LinkedHashSet<>(get(context).getStringSet(KEY_OBSERVED_PLAYER_PACKAGES,
                Collections.emptySet()));
    }

    private static String normalizeLyricCatalog(String value) {
        if ("netease".equals(value) || "qqmusic".equals(value)
                || "kugou".equals(value) || "kuwo".equals(value)
                || "soda".equals(value) || "auto".equals(value)) return value;
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
                || "compact".equals(style) || "pip".equals(style)
                || "custom".equals(style) || "amll".equals(style) || "pure".equals(style)) {
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
        return Math.max(16, Math.min(96,
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

    static boolean compactShowNextLine(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_COMPACT_SHOW_NEXT_LINE, true);
    }

    static boolean compactUseRealSpectrum(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_COMPACT_USE_REAL_SPECTRUM, false);
    }

    /** Zero keeps the spectrum bars following the current lyric color. */
    static int compactSpectrumColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_COMPACT_SPECTRUM_COLOR, 0);
    }

    static void setCompactSpectrumColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_COMPACT_SPECTRUM_COLOR,
                color == 0 ? 0 : (color | 0xFF000000));
    }

    static boolean spectrumEnabled(Context context, boolean secondary) {
        String style = overlayStyle(context, secondary);
        boolean legacy = "compact".equals(style) && compactShowBars(context, secondary);
        return displayBoolean(context, secondary, KEY_SPECTRUM_ENABLED, legacy);
    }

    static String spectrumStyle(Context context, boolean secondary) {
        String value = displayString(context, secondary, KEY_SPECTRUM_STYLE, "bars");
        return "mirror".equals(value) || "capsule".equals(value)
                || "dots".equals(value) || "wave".equals(value) ? value : "bars";
    }

    static String spectrumColorMode(Context context, boolean secondary) {
        String value = displayString(context, secondary, KEY_SPECTRUM_COLOR_MODE, "lyric");
        return "custom".equals(value) || "rainbow".equals(value)
                || "artwork".equals(value) ? value : "lyric";
    }

    static String realSpectrumCaptureRate(Context context) {
        String value = get(context).getString(KEY_REAL_SPECTRUM_CAPTURE_RATE, "low");
        return "high".equals(value) ? "high" : "low";
    }

    static int playbackControlScale(Context context) {
        return Math.max(60, Math.min(160, get(context).getInt(KEY_PLAYBACK_CONTROL_SCALE, 100)));
    }

    static int playbackControlX(Context context) {
        return Math.max(-40, Math.min(40, get(context).getInt(KEY_PLAYBACK_CONTROL_X, 0)));
    }

    static int playbackControlY(Context context) {
        return Math.max(-40, Math.min(40, get(context).getInt(KEY_PLAYBACK_CONTROL_Y, 0)));
    }

    static String fullscreenCloseMode(Context context) {
        String value = get(context).getString(KEY_FULLSCREEN_CLOSE_MODE, "fade");
        return "always".equals(value) || "hidden".equals(value) ? value : "fade";
    }

    static String overlayCloseMode(Context context) {
        String value = get(context).getString(KEY_OVERLAY_CLOSE_MODE, "fade");
        return "always".equals(value) || "hidden".equals(value) ? value : "fade";
    }

    static boolean tapOverlayReturnsToPlayer(Context context) {
        return get(context).getBoolean(KEY_TAP_OVERLAY_RETURNS_TO_PLAYER, false);
    }

    static boolean launchOverlayOnIcon(Context context) {
        return get(context).getBoolean(KEY_LAUNCH_OVERLAY_ON_ICON, false);
    }

    static boolean autoStartOverlays(Context context) {
        return get(context).getBoolean(KEY_AUTO_START_OVERLAYS, false);
    }

    static boolean bottomSpectrum(Context context) {
        return get(context).getBoolean(KEY_BOTTOM_SPECTRUM, false);
    }

    static int bottomSpectrumHeightDp(Context context) {
        return Math.max(24, Math.min(120,
                get(context).getInt(KEY_BOTTOM_SPECTRUM_HEIGHT_DP, 54)));
    }

    static boolean localLyricEnabled(Context context) {
        return get(context).getBoolean(KEY_LOCAL_LYRIC_ENABLED, true);
    }

    static String localLyricDirectoryUri(Context context) {
        return get(context).getString(KEY_LOCAL_LYRIC_DIRECTORY_URI, "");
    }

    static boolean avrcpEnabled(Context context) {
        return get(context).getBoolean(KEY_AVRCP_ENABLED, true);
    }

    /** Restores product settings while retaining the anonymous support identity and replies. */
    static int resetUserSettings(Context context) {
        Set<String> preserved = new HashSet<>();
        preserved.add(KEY_COMMUNITY_CLIENT_ID);
        preserved.add(KEY_FEEDBACK_TICKETS);
        preserved.add(KEY_LAST_FEEDBACK_ID);
        preserved.add(KEY_FEEDBACK_READ_REPLY_IDS);
        preserved.add(KEY_FAQ_CACHE);
        preserved.add(KEY_COMMUNITY_ANNOUNCEMENT_DISMISSED);
        preserved.add(KEY_SAFETY_NOTICE_SEEN);
        SharedPreferences preferences = get(context);
        SharedPreferences.Editor editor = preferences.edit();
        int removed = 0;
        for (String key : preferences.getAll().keySet()) {
            if (preserved.contains(key)) continue;
            editor.remove(key);
            removed++;
        }
        editor.commit();
        return removed;
    }

    /** An enabled auto-start option must always have a visible target to restore. */
    static boolean ensureAutoStartOverlayTarget(Context context) {
        if (mainEnabled(context) || secondaryEnabled(context) || topLyricStrip(context)
                || bottomSpectrum(context)) {
            return false;
        }
        get(context).edit().putBoolean(KEY_MAIN_OVERLAY, true).apply();
        return true;
    }

    static boolean serviceStoppedByUser(Context context) {
        return get(context).getBoolean(KEY_SERVICE_STOPPED_BY_USER, false);
    }

    static void setServiceStoppedByUser(Context context, boolean stopped) {
        get(context).edit().putBoolean(KEY_SERVICE_STOPPED_BY_USER, stopped).apply();
    }

    static boolean overlayTouchThrough(Context context, boolean secondary) {
        return get(context).getBoolean(secondary
                ? KEY_SECONDARY_OVERLAY_TOUCH_THROUGH : KEY_MAIN_OVERLAY_TOUCH_THROUGH, false);
    }

    static boolean hideOverlaysWhenNotPlaying(Context context) {
        return get(context).getBoolean(KEY_HIDE_OVERLAYS_WHEN_NOT_PLAYING, false);
    }

    static boolean hideOverlaysInPlayer(Context context) {
        return get(context).getBoolean(KEY_HIDE_OVERLAYS_IN_PLAYER, false);
    }

    static Set<String> hiddenOverlayApps(Context context) {
        Set<String> stored = get(context).getStringSet(KEY_HIDE_OVERLAYS_IN_APPS,
                Collections.emptySet());
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    static void setHiddenOverlayApps(Context context, Set<String> packages) {
        get(context).edit().putStringSet(KEY_HIDE_OVERLAYS_IN_APPS,
                packages == null ? Collections.emptySet() : new HashSet<>(packages)).apply();
    }

    static boolean notificationLyrics(Context context) {
        return get(context).getBoolean(KEY_NOTIFICATION_LYRICS, false);
    }

    static boolean topLyricStrip(Context context) {
        return get(context).getBoolean(KEY_TOP_LYRIC_STRIP, false);
    }

    static int statusLyricColor(Context context) {
        return get(context).getInt(KEY_STATUS_LYRIC_COLOR, 0);
    }

    static void setStatusLyricColor(Context context, int color) {
        get(context).edit().putInt(KEY_STATUS_LYRIC_COLOR,
                color == 0 ? 0 : (color | 0xFF000000)).apply();
    }

    static boolean statusLyricFollowTheme(Context context) {
        return get(context).getBoolean(KEY_STATUS_LYRIC_FOLLOW_THEME, false);
    }

    static int statusLyricLightColor(Context context) {
        int legacy = statusLyricColor(context);
        return get(context).getInt(KEY_STATUS_LYRIC_LIGHT_COLOR,
                legacy == 0 ? 0xFF17212E : legacy);
    }

    static int statusLyricDarkColor(Context context) {
        int legacy = statusLyricColor(context);
        return get(context).getInt(KEY_STATUS_LYRIC_DARK_COLOR,
                legacy == 0 ? 0xFFF5F8FF : legacy);
    }

    static void setStatusLyricLightColor(Context context, int color) {
        get(context).edit().putInt(KEY_STATUS_LYRIC_LIGHT_COLOR,
                color == 0 ? 0 : (color | 0xFF000000)).apply();
    }

    static void setStatusLyricDarkColor(Context context, int color) {
        get(context).edit().putInt(KEY_STATUS_LYRIC_DARK_COLOR,
                color == 0 ? 0 : (color | 0xFF000000)).apply();
    }

    static int topLyricFontScale(Context context) {
        return Math.max(60, Math.min(200,
                get(context).getInt(KEY_TOP_LYRIC_FONT_SCALE, 100)));
    }

    static int topLyricRegionPercent(Context context) {
        return Math.max(45, Math.min(100,
                get(context).getInt(KEY_TOP_LYRIC_REGION_PERCENT, 100)));
    }

    static int topLyricOffsetXDp(Context context) {
        return Math.max(-240, Math.min(240,
                get(context).getInt(KEY_TOP_LYRIC_OFFSET_X_DP, 0)));
    }

    static int topLyricOffsetYDp(Context context) {
        return Math.max(-240, Math.min(240,
                get(context).getInt(KEY_TOP_LYRIC_OFFSET_Y_DP, 0)));
    }

    static boolean topLyricShowTranslation(Context context) {
        return get(context).getBoolean(KEY_TOP_LYRIC_SHOW_TRANSLATION, false);
    }

    static String topLyricBackground(Context context) {
        String value = get(context).getString(KEY_TOP_LYRIC_BACKGROUND, "transparent");
        // Old "glass" was cover-based rather than a real blurred window. Preserve its
        // appearance under the renamed compact-card option; "blur" is the real material.
        if ("glass".equals(value)) return "compact";
        return "blur".equals(value) || "compact".equals(value) ? value : "transparent";
    }

    static boolean topLyricSpectrum(Context context) {
        return get(context).getBoolean(KEY_TOP_LYRIC_SPECTRUM, false);
    }

    static void setTopLyricInt(Context context, String key, int value) {
        get(context).edit().putInt(key, value).apply();
    }

    static String lastFeedbackId(Context context) {
        return get(context).getString(KEY_LAST_FEEDBACK_ID, "");
    }

    static void setLastFeedbackId(Context context, String feedbackId) {
        get(context).edit().putString(KEY_LAST_FEEDBACK_ID,
                feedbackId == null ? "" : feedbackId.trim()).apply();
    }

    static boolean lockscreenLyrics(Context context) {
        return get(context).getBoolean(KEY_LOCKSCREEN_LYRICS, false);
    }

    private static int defaultPanelWidthDp(String style) {
        if ("refined".equals(style)) return 560;
        if ("compact".equals(style)) return 320;
        if ("amll".equals(style)) return 620;
        if ("pip".equals(style)) return 440;
        if ("custom".equals(style)) return 460;
        return 390;
    }

    private static int defaultPanelHeightDp(String style) {
        if ("refined".equals(style)) return 300;
        // This leaves room for the optional translation and the compact playback bars while
        // remaining a small horizontal overlay.
        if ("compact".equals(style)) return 104;
        if ("amll".equals(style)) return 350;
        if ("pip".equals(style)) return 220;
        if ("custom".equals(style)) return 260;
        return 226;
    }

    private static String panelWidthKey(String style) {
        if ("compact".equals(style)) return KEY_COMPACT_PANEL_WIDTH_DP;
        if ("amll".equals(style)) return KEY_AMLL_PANEL_WIDTH_DP;
        return KEY_PANEL_WIDTH_DP;
    }

    private static String panelHeightKey(String style) {
        if ("compact".equals(style)) return KEY_COMPACT_PANEL_HEIGHT_DP;
        if ("amll".equals(style)) return KEY_AMLL_PANEL_HEIGHT_DP;
        return KEY_PANEL_HEIGHT_DP;
    }

    static void changed(Context context) {
        LyricsDisplayService.startOrRefresh(context);
    }

    private static String lyricSourceOffsetKey(String sourceId) {
        String safe = sourceId == null ? "media" : sourceId.trim().toLowerCase();
        if (!safe.matches("[a-z0-9_]+")) safe = "media";
        return KEY_LYRIC_SOURCE_OFFSET + "_" + safe;
    }

    private static String playerLyricCatalogKey(String sourceId) {
        String safe = sourceId == null ? "media" : sourceId.trim().toLowerCase();
        if (!safe.matches("[a-z0-9_]+")) safe = "media";
        return KEY_PLAYER_LYRIC_CATALOG + "_" + safe;
    }

    private static String playerPackageLyricCatalogKey(String packageName) {
        String safe = normalizePackageName(packageName).replace('.', '_');
        if (safe.isEmpty()) safe = "unknown";
        return KEY_PLAYER_LYRIC_CATALOG + "_app_" + safe;
    }

    private static String normalizePackageName(String packageName) {
        String normalized = packageName == null ? "" : packageName.trim().toLowerCase();
        return normalized.matches("[a-z0-9_.]+") ? normalized : "";
    }
}
