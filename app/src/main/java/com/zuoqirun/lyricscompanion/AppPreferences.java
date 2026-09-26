package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
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
    static final String KEY_PURE_SHOW_TRANSLATION = "pure_show_translation";
    static final String KEY_COMPONENT_LAYOUT = "component_layout";
    static final String KEY_TEXT_SCALE = "text_scale";
    static final String KEY_TITLE_SCALE = "title_scale";
    /** 歌手字号（占歌名字号的百分比；-1 = 沿用样式原比例，issue #63）。 */
    static final String KEY_ARTIST_SCALE = "artist_scale";
    /** 面板内律动/频谱条的高度百分比（-1 = 沿用原来的 14–42dp / 15%，issue #57）与额外底部留白。 */
    static final String KEY_SPECTRUM_HEIGHT_PERCENT = "spectrum_height_percent";
    static final String KEY_SPECTRUM_GAP_DP = "spectrum_gap_dp";
    /** 面板边缘阴影强度（0–100%，-1 = 各样式原样，issue #56）。 */
    static final String KEY_PANEL_SHADOW_PERCENT = "panel_shadow_percent";
    /** 背景遮罩档位（auto / off，issue #58）与背景亮度轴（-100..100）。 */
    static final String KEY_STYLE_MASK_MODE = "style_mask_mode";
    static final String KEY_STYLE_BRIGHTNESS = "style_brightness";
    /** 允许面板移出屏幕边缘（issue #49）：按屏存，含可探出的百分比与内容留白。 */
    static final String KEY_OVERLAY_ALLOW_OFFSCREEN = "overlay_allow_offscreen";
    static final String KEY_OVERLAY_OFFSCREEN_PERCENT = "overlay_offscreen_percent";
    static final String KEY_CONTENT_PADDING_PERCENT = "content_padding_percent";
    /** 长句显示方式（issue #47）：marquee（默认）/ shrink / wrap。 */
    static final String KEY_LONG_LINE_MODE = "long_line_mode";    static final String KEY_THEME_MODE = "theme_mode";
    static final String KEY_LYRICS_FOLLOW_THEME = "lyrics_follow_theme";
    static final String KEY_OPACITY = "opacity";
    static final String KEY_LYRIC_OFFSET = "lyric_offset";
    static final String KEY_LYRIC_SOURCE_OFFSET = "lyric_source_offset";
    static final String KEY_NEXT_LYRIC_SCALE = "next_lyric_scale";
    static final String KEY_NEXT_LYRIC_OPACITY = "next_lyric_opacity";
    static final String KEY_PREVIOUS_LYRIC_OPACITY = "previous_lyric_opacity";
    static final String KEY_PREVIOUS_LYRIC_SCALE = "previous_lyric_scale";
    static final String KEY_CONTENT_ALIGN = "content_align";
    /** 面板圆角占短边的百分比; -1 keeps each style's own radius (issue #37). */
    static final String KEY_CORNER_RADIUS_PERCENT = "corner_radius_percent";
    /** 圆形封面按播放进度旋转, and how many seconds one turn takes (issue #22). */
    static final String KEY_COVER_ROTATION = "cover_rotation";
    static final String KEY_COVER_ROTATION_PERIOD_SECONDS = "cover_rotation_period_seconds";
    /** 紧凑 / AMLL 的封面改成圆形（issue #22）；不设时保持样式原本的圆角方形。 */
    static final String KEY_ROUND_COVER = "round_cover";
    /** 可选的时间段配色开关；关掉时按主题模式（跟随系统 / 白天 / 夜晚）判断（issue #34）。 */
    static final String KEY_THEME_SCHEDULE_ENABLED = "theme_schedule_enabled";
    /** 「正在匹配歌词」以动画呈现, and how long a match may take before it starts (issue #45). */
    static final String KEY_MATCHING_ANIMATION = "matching_animation";
    static final String KEY_MATCHING_ANIMATION_DELAY = "matching_animation_delay_ms";
    /** 无逐字时间轴时按本句时长估算逐字进度（issue #21），默认关闭。 */
    static final String KEY_ESTIMATED_WORD_KARAOKE = "estimated_word_karaoke";
    /** Dark window of the scheduled theme, in minutes of the day (issue #34). */
    static final String KEY_THEME_SCHEDULE_START_MINUTE = "theme_schedule_start_minute";
    static final String KEY_THEME_SCHEDULE_END_MINUTE = "theme_schedule_end_minute";
    /** 「指定应用」规则的方向，每屏一个：true = 白名单（只在名单内的应用里显示，issue #43）。 */
    static final String KEY_APP_RULE_WHITELIST_MAIN = "app_rule_whitelist_main";
    static final String KEY_APP_RULE_WHITELIST_SECONDARY = "app_rule_whitelist_secondary";
    /** 忽略这些应用发布的媒体元数据（issue #35）。 */
    static final String KEY_IGNORED_PLAYER_PACKAGES = "ignored_player_packages";
    /** 匹配到的歌词长时间不滚动时，改用播放器实时歌词（issue #44）。 */
    static final String KEY_STUCK_LYRIC_FALLBACK = "stuck_lyric_fallback";
    static final String KEY_PREVIOUS_LYRIC_PARTICLES = "previous_lyric_particles";
    static final String KEY_PARTICLE_AMOUNT = "previous_lyric_particle_amount";
    static final String KEY_WORD_DISSOLVE = "word_dissolve";
    static final String KEY_SHOW_PLAYER_STATUS = "show_player_status";
    static final String KEY_SHOW_PROGRESS = "show_progress";
    static final String KEY_LYRIC_COLOR = "lyric_color";
    static final String KEY_CURRENT_LYRIC_COLOR = "current_lyric_color";
    static final String KEY_INACTIVE_LYRIC_COLOR = "inactive_lyric_color";
    static final String KEY_CURRENT_LYRIC_LIGHT_COLOR = "current_lyric_light_color";
    static final String KEY_CURRENT_LYRIC_DARK_COLOR = "current_lyric_dark_color";
    static final String KEY_INACTIVE_LYRIC_LIGHT_COLOR = "inactive_lyric_light_color";
    static final String KEY_INACTIVE_LYRIC_DARK_COLOR = "inactive_lyric_dark_color";
    static final String KEY_LYRIC_OUTLINE = "lyric_outline";
    static final String KEY_LYRIC_OUTLINE_COLOR = "lyric_outline_color";
    static final String KEY_LYRIC_OUTLINE_ALPHA = "lyric_outline_alpha";
    static final String KEY_LYRIC_OUTLINE_WIDTH = "lyric_outline_width";
    static final String KEY_CURRENT_LYRIC_OUTLINE = "current_lyric_outline";
    static final String KEY_CURRENT_LYRIC_OUTLINE_COLOR = "current_lyric_outline_color";
    static final String KEY_CURRENT_LYRIC_OUTLINE_ALPHA = "current_lyric_outline_alpha";
    static final String KEY_CURRENT_LYRIC_OUTLINE_WIDTH = "current_lyric_outline_width";
    static final String KEY_INACTIVE_LYRIC_OUTLINE = "inactive_lyric_outline";
    static final String KEY_INACTIVE_LYRIC_OUTLINE_COLOR = "inactive_lyric_outline_color";
    static final String KEY_INACTIVE_LYRIC_OUTLINE_ALPHA = "inactive_lyric_outline_alpha";
    static final String KEY_INACTIVE_LYRIC_OUTLINE_WIDTH = "inactive_lyric_outline_width";
    static final String KEY_TRAILING_ACCENT = "trailing_accent";
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
    /** 播放器不给封面时的行为（issue #50）：placeholder = 画占位，hide = 收起封面区域。 */
    static final String KEY_COVER_MISSING_MODE = "cover_missing_mode";
    /** 动态星空背景（issue #59）：背景类型里的 starfield 档，以及它的各项参数。 */
    static final String KEY_STARFIELD_DENSITY = "starfield_density";
    static final String KEY_STARFIELD_SPEED = "starfield_speed";
    static final String KEY_STARFIELD_SIZE = "starfield_size";
    static final String KEY_STARFIELD_FOLLOW_COVER = "starfield_follow_cover";
    static final String KEY_STARFIELD_STILL = "starfield_still";
    /** 星空渲染帧率（fps）：调低省电，调高更顺（issue #59）。 */
    static final String KEY_STARFIELD_FPS = "starfield_fps";
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
    static final String KEY_OVERLAY_POSITION_LOCKED = "overlay_position_locked";
    static final String KEY_HIDE_OVERLAYS_WHEN_NOT_PLAYING =
            "hide_overlays_when_not_playing";
    static final String KEY_HIDE_OVERLAYS_IN_PLAYER = "hide_overlays_in_player";
    static final String KEY_HIDE_OVERLAYS_IN_APPS = "hide_overlays_in_apps";
    static final String KEY_HIDE_SELECTED_APPS_ON_MAIN = "hide_selected_apps_on_main";
    static final String KEY_HIDE_SELECTED_APPS_ON_SECONDARY = "hide_selected_apps_on_secondary";
    /** 无歌词 / 纯音乐时自动隐藏（issue #67）：按屏存，含宽限期与「不显示占位文案」开关。 */
    static final String KEY_HIDE_WHEN_NO_LYRICS = "hide_when_no_lyrics";
    static final String KEY_HIDE_NO_LYRIC_PLACEHOLDER = "hide_no_lyric_placeholder";
    static final String KEY_NO_LYRIC_GRACE_MS = "no_lyric_grace_ms";
    static final String KEY_SHOW_PREVIOUS_BUTTON = "show_previous_button";
    /**
     * 东风车机会话仲裁（issue #75）：默认在东风会话长时间没有变化时把活跃位让给真正在播放的其它
     * 播放器；打开这个开关就回到老行为「东风只要还在报播放就一直按住」。
     */
    static final String KEY_DFTC_ALWAYS_PREFERRED = "dftc_always_preferred";
    /** 设置页分类栏位置（issue #70）：left（默认）/ right，以及额外的起始留白（dp）。 */
    static final String KEY_SETTINGS_NAV_SIDE = "settings_nav_side";
    static final String KEY_SETTINGS_NAV_INSET_DP = "settings_nav_inset_dp";
    /** 上一次读到的前台应用与时间（issue #61）：ROM 不返回前台事件时黑名单只能靠它。 */
    static final String KEY_LAST_FOREGROUND_PACKAGE = "last_foreground_package";
    static final String KEY_LAST_FOREGROUND_AT_MS = "last_foreground_at_ms";    static final String KEY_SHOW_PLAY_PAUSE_BUTTON = "show_play_pause_button";
    static final String KEY_SHOW_NEXT_BUTTON = "show_next_button";
    static final String KEY_PLAYBACK_CONTROL_SCALE = "playback_control_scale";
    static final String KEY_PLAYBACK_CONTROL_X = "playback_control_x";
    static final String KEY_PLAYBACK_CONTROL_Y = "playback_control_y";
    static final String KEY_SECONDARY_PLAYBACK_CONTROLS = "secondary_playback_controls";
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
    /** The strip's own 下一句字号; unset means "follow the main screen" (issue #19). */
    static final String KEY_TOP_LYRIC_NEXT_FONT_SCALE = "top_lyric_next_font_scale";
    static final String KEY_TOP_LYRIC_REGION_PERCENT = "top_lyric_region_percent";
    static final String KEY_TOP_LYRIC_OFFSET_X_DP = "top_lyric_offset_x_dp";
    static final String KEY_TOP_LYRIC_OFFSET_Y_DP = "top_lyric_offset_y_dp";
    /** Settings-page scale; deliberately independent from lyric rendering scale. */
    static final String KEY_SETTINGS_UI_SCALE = "settings_ui_scale";
    static final String KEY_MAIN_SETTINGS_MODE = "main_settings_mode";
    static final String KEY_TOP_LYRIC_SHOW_TRANSLATION = "top_lyric_show_translation";
    static final String KEY_TOP_LYRIC_BACKGROUND = "top_lyric_background";
    static final String KEY_TOP_LYRIC_SPECTRUM = "top_lyric_spectrum";
    /** The strip's dissolve family; unset means "follow the main screen", see the accessors. */
    static final String KEY_TOP_LYRIC_PARTICLES = "top_lyric_particles";
    static final String KEY_TOP_LYRIC_PARTICLE_AMOUNT = "top_lyric_particle_amount";
    static final String KEY_TOP_LYRIC_WORD_DISSOLVE = "top_lyric_word_dissolve";
    static final String KEY_TOP_LYRIC_PREVIOUS_OPACITY = "top_lyric_previous_opacity";
    /** The screen the position joystick is pointed at, remembered across restarts. */
    static final String KEY_JOYSTICK_SLOT = "joystick_slot";
    static final String KEY_BOTTOM_SPECTRUM = "bottom_spectrum";
    static final String KEY_BOTTOM_SPECTRUM_HEIGHT_DP = "bottom_spectrum_height_dp";
    static final String KEY_LOCAL_LYRIC_ENABLED = "local_lyric_enabled";
    static final String KEY_LOCAL_LYRIC_DIRECTORY_URI = "local_lyric_directory_uri";
    static final String KEY_LOCAL_LYRIC_DIRECTORY_PATH = "local_lyric_directory_path";
    static final String KEY_AVRCP_ENABLED = "avrcp_enabled";
    static final String KEY_COMPOSITE_IDENTITY_FROM_ARTIST = "composite_identity_from_artist";
    static final String KEY_LYRIC_ALIGN = "lyric_align";
    /** Ordered extra screens that show lyrics besides the main and the single secondary. */
    static final String KEY_EXTRA_DISPLAYS = "extra_displays";
    static final String KEY_LOCKSCREEN_LYRICS = "lockscreen_lyrics";
    static final String KEY_CUSTOM_FONT_FILE = "custom_font_file";
    static final String KEY_LYRIC_CACHE_POLICY = "lyric_cache_policy";
    static final String KEY_LYRIC_CACHE_LIMIT_MB = "lyric_cache_limit_mb";
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
        return showPreviousButton(context, false);
    }

    /**
     * 这一屏显示哪几个播放按键。主屏沿用原来的全局键；副屏没在「副屏播放控制」里单独调过时沿用主屏的
     * 值，调过之后只影响副屏——以前副屏只有总开关，想少画一个键只能去主屏关，主副屏一起变
     * （issue #72 / #30 剩下的那半截）。
     */
    static boolean showPreviousButton(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_SHOW_PREVIOUS_BUTTON,
                get(context).getBoolean(KEY_SHOW_PREVIOUS_BUTTON, true));
    }

    static boolean showPlayPauseButton(Context context) {
        return showPlayPauseButton(context, false);
    }

    static boolean showPlayPauseButton(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_SHOW_PLAY_PAUSE_BUTTON,
                get(context).getBoolean(KEY_SHOW_PLAY_PAUSE_BUTTON, true));
    }

    static boolean showNextButton(Context context) {
        return showNextButton(context, false);
    }

    static boolean showNextButton(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_SHOW_NEXT_BUTTON,
                get(context).getBoolean(KEY_SHOW_NEXT_BUTTON, true));
    }

    static int displayId(Context context) {
        return get(context).getInt(KEY_DISPLAY_ID, -1);
    }

    /** 设置页分类栏在左还是在右（issue #70）：车机左侧系统悬浮栏会压住它。 */
    static String settingsNavSide(Context context) {
        return get(context).getString(KEY_SETTINGS_NAV_SIDE, SettingsNavLayout.SIDE_LEFT);
    }

    static void setSettingsNavSide(Context context, String value) {
        get(context).edit().putString(KEY_SETTINGS_NAV_SIDE,
                SettingsNavLayout.SIDE_RIGHT.equals(value)
                        ? SettingsNavLayout.SIDE_RIGHT : SettingsNavLayout.SIDE_LEFT).apply();
    }

    /** 分类栏起始侧的额外留白（dp）：系统悬浮栏不报插边时靠它手动让开。 */
    static int settingsNavInsetDp(Context context) {
        return SettingsNavLayout.clampInsetDp(get(context).getInt(KEY_SETTINGS_NAV_INSET_DP, 0));
    }

    static void setSettingsNavInsetDp(Context context, int value) {
        get(context).edit()
                .putInt(KEY_SETTINGS_NAV_INSET_DP, SettingsNavLayout.clampInsetDp(value)).apply();
    }

    /** 上一次确实读到过的前台应用（issue #61）：车机 ROM 不返回前台事件时，黑名单只能靠它。 */
    static String lastForegroundPackage(Context context) {
        return get(context).getString(KEY_LAST_FOREGROUND_PACKAGE, "");
    }

    static void setLastForeground(Context context, String packageName, long wallTimeMs) {
        if (packageName == null || packageName.trim().isEmpty()) return;
        get(context).edit()
                .putString(KEY_LAST_FOREGROUND_PACKAGE, packageName.trim())
                .putLong(KEY_LAST_FOREGROUND_AT_MS, wallTimeMs).apply();
    }

    static long lastForegroundAtMs(Context context) {
        return get(context).getLong(KEY_LAST_FOREGROUND_AT_MS, 0L);
    }

    /** 东风会话是否「始终优先」（issue #75）：默认 false —— 它长时间没变化时让位给真在播放的播放器。 */
    static boolean dftcAlwaysPreferred(Context context) {
        return get(context).getBoolean(KEY_DFTC_ALWAYS_PREFERRED, false);
    }

    /** 无歌词 / 纯音乐时自动隐藏（issue #67）：默认关闭，按屏保存。 */
    static boolean hideWhenNoLyrics(Context context) { return hideWhenNoLyrics(context, false); }

    static boolean hideWhenNoLyrics(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_HIDE_WHEN_NO_LYRICS, false);
    }

    /** 即使不隐藏面板，也可以选择不显示「暂无匹配歌词」这类占位文案（issue #67）。 */
    static boolean hideNoLyricPlaceholder(Context context) {
        return hideNoLyricPlaceholder(context, false);
    }

    static boolean hideNoLyricPlaceholder(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_HIDE_NO_LYRIC_PLACEHOLDER, false);
    }

    /** 「无歌词」要持续多久才隐藏（毫秒，issue #67）：默认 8 秒。 */
    static int noLyricGraceMs(Context context) { return noLyricGraceMs(context, false); }

    static int noLyricGraceMs(Context context, boolean secondary) {
        return NoLyricVisibilityRules.normalizeGraceMs(displayInt(context, secondary,
                KEY_NO_LYRIC_GRACE_MS, NoLyricVisibilityRules.DEFAULT_GRACE_MS));
    }

    /**
     * Visual values are deliberately scoped per display.  The legacy unscoped key remains a
     * read-only migration fallback so existing installations retain their current appearance
     * until either display is adjusted for the first time.
     */
    private static String displayKey(String key, boolean secondary) {
        return key + (secondary ? "_secondary" : "_main");
    }

    /**
     * 可选歌词样式，与设置页的样式下拉一致（issue #39 的"应用到其它样式"用）。注意经典的 id 是
     * {@code default}，与 {@link #normalizeOverlayStyle(String)} 保持一致。
     */
    static final String[] OVERLAY_STYLES = {"default", "refined", "amll", "compact", "pip", "pure",
            "custom", "island"};

    /**
     * 这些"共用显示参数"按 <b>屏 × 样式</b> 两级保存（issue #39）：字号、颜色与描边、背景不透明度、
     * 歌词显示行数、粒子与逐字擦除、上一句 / 下一句的不透明度与字号、对齐与圆角、封面与匹配动画。
     *
     * <p>样式选择本身、位置锁定 / 触摸穿透、歌词时间校正、缓存与隐藏规则这些"行为"设置仍然只按屏
     * 共用：它们与样式无关，按样式各存一份只会让用户改了一处、别处不生效。
     */
    private static final Set<String> STYLE_SCOPED_KEYS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    KEY_TEXT_SCALE, KEY_TITLE_SCALE, KEY_NEXT_LYRIC_SCALE, KEY_PREVIOUS_LYRIC_SCALE,
                    KEY_NEXT_LYRIC_OPACITY, KEY_PREVIOUS_LYRIC_OPACITY,
                    KEY_OPACITY, KEY_STYLE_COVER_SIZE, KEY_STYLE_BLUR, KEY_STYLE_DIM,
                    KEY_CORNER_RADIUS_PERCENT, KEY_LYRIC_ALIGN, KEY_CONTENT_ALIGN,
                    KEY_STYLE_LYRIC_LINES,
                    KEY_PREVIOUS_LYRIC_PARTICLES, KEY_PARTICLE_AMOUNT, KEY_WORD_DISSOLVE,
                    KEY_ESTIMATED_WORD_KARAOKE, KEY_MATCHING_ANIMATION,
                    KEY_MATCHING_ANIMATION_DELAY,
                    KEY_ROUND_COVER, KEY_COVER_ROTATION, KEY_COVER_ROTATION_PERIOD_SECONDS,
                    KEY_LYRIC_COLOR, KEY_CURRENT_LYRIC_COLOR, KEY_INACTIVE_LYRIC_COLOR,
                    KEY_LYRIC_LIGHT_COLOR, KEY_LYRIC_DARK_COLOR,
                    KEY_CURRENT_LYRIC_LIGHT_COLOR, KEY_CURRENT_LYRIC_DARK_COLOR,
                    KEY_INACTIVE_LYRIC_LIGHT_COLOR, KEY_INACTIVE_LYRIC_DARK_COLOR,
                    KEY_LYRIC_OUTLINE, KEY_LYRIC_OUTLINE_COLOR, KEY_LYRIC_OUTLINE_ALPHA,
                    KEY_LYRIC_OUTLINE_WIDTH,
                    KEY_CURRENT_LYRIC_OUTLINE, KEY_CURRENT_LYRIC_OUTLINE_COLOR,
                    KEY_CURRENT_LYRIC_OUTLINE_ALPHA, KEY_CURRENT_LYRIC_OUTLINE_WIDTH,
                    KEY_INACTIVE_LYRIC_OUTLINE, KEY_INACTIVE_LYRIC_OUTLINE_COLOR,
                    KEY_INACTIVE_LYRIC_OUTLINE_ALPHA, KEY_INACTIVE_LYRIC_OUTLINE_WIDTH,
                    KEY_TITLE_COLOR, KEY_ARTIST_COLOR, KEY_PLAYER_COLOR, KEY_LYRIC_SOURCE_COLOR,
                    KEY_BACKGROUND_LIGHT_COLOR, KEY_BACKGROUND_DARK_COLOR,
                    KEY_TRAILING_ACCENT, KEY_REFINED_TEXT_EFFECT, KEY_ARTIST_SCALE,
                    KEY_SPECTRUM_HEIGHT_PERCENT, KEY_SPECTRUM_GAP_DP,
                    KEY_PANEL_SHADOW_PERCENT, KEY_STYLE_MASK_MODE, KEY_STYLE_BRIGHTNESS,
                    KEY_CONTENT_PADDING_PERCENT, KEY_LONG_LINE_MODE)));

    /** 屏 × 样式两级键；样式为空时按默认样式（经典 {@code default}）算。 */
    static String styleScopedKey(String key, boolean secondary, String style) {
        String suffix = style == null || style.trim().isEmpty() ? "default" : style.trim();
        return displayKey(key, secondary) + "_" + suffix;
    }

    /** 这个键是否按样式各存一份（issue #39）。 */
    static boolean isStyleScopedKey(String key) {
        return key != null && STYLE_SCOPED_KEYS.contains(key);
    }

    /** 这一屏正在用的样式，决定"共用显示参数"读写的层级。 */
    private static String displayStyle(Context context, boolean secondary) {
        String style = overlayStyle(context, secondary);
        return style == null || style.trim().isEmpty() ? "default" : style.trim();
    }

    /** 写入键：分样式的参数写 屏 × 样式，其余仍写屏。 */
    private static String displayWriteKey(Context context, boolean secondary, String key) {
        return isStyleScopedKey(key)
                ? styleScopedKey(key, secondary, displayStyle(context, secondary))
                : displayKey(key, secondary);
    }

    /**
     * 分样式的值优先，其次才是"这一屏共用的值"（老安装的现值），最后是历史遗留的裸键。于是升级后
     * 观感不变，只有用户在某个样式下改过某一项，那一项才会为该样式单独存一份（issue #39）。
     */
    static int displayInt(Context context, boolean secondary, String key, int fallback) {
        SharedPreferences slot = displaySlotStore(context, secondary);
        if (isStyleScopedKey(key)) {
            String styled = styleScopedKey(key, secondary, displayStyle(context, secondary));
            if (slot.contains(styled)) return slot.getInt(styled, fallback);
            SharedPreferences shared = get(context);
            if (shared != slot && shared.contains(styled)) return shared.getInt(styled, fallback);
        }
        String scoped = displayKey(key, secondary);
        if (slot.contains(scoped)) return slot.getInt(scoped, fallback);
        SharedPreferences shared = get(context);
        if (shared != slot && shared.contains(scoped)) return shared.getInt(scoped, fallback);
        return shared.getInt(key, fallback);
    }

    static boolean displayBoolean(Context context, boolean secondary, String key,
                                  boolean fallback) {
        SharedPreferences slot = displaySlotStore(context, secondary);
        if (isStyleScopedKey(key)) {
            String styled = styleScopedKey(key, secondary, displayStyle(context, secondary));
            if (slot.contains(styled)) return slot.getBoolean(styled, fallback);
            SharedPreferences shared = get(context);
            if (shared != slot && shared.contains(styled)) return shared.getBoolean(styled, fallback);
        }
        String scoped = displayKey(key, secondary);
        if (slot.contains(scoped)) return slot.getBoolean(scoped, fallback);
        SharedPreferences shared = get(context);
        if (shared != slot && shared.contains(scoped)) return shared.getBoolean(scoped, fallback);
        return shared.getBoolean(key, fallback);
    }

    static String displayString(Context context, boolean secondary, String key, String fallback) {
        SharedPreferences slot = displaySlotStore(context, secondary);
        if (isStyleScopedKey(key)) {
            String styled = styleScopedKey(key, secondary, displayStyle(context, secondary));
            if (slot.contains(styled)) return slot.getString(styled, fallback);
            SharedPreferences shared = get(context);
            if (shared != slot && shared.contains(styled)) return shared.getString(styled, fallback);
        }
        String scoped = displayKey(key, secondary);
        if (slot.contains(scoped)) return slot.getString(scoped, fallback);
        SharedPreferences shared = get(context);
        if (shared != slot && shared.contains(scoped)) return shared.getString(scoped, fallback);
        return shared.getString(key, fallback);
    }

    static void putDisplayInt(Context context, boolean secondary, String key, int value) {
        displaySlotStore(context, secondary).edit()
                .putInt(displayWriteKey(context, secondary, key), value).apply();
    }

    static void putDisplayBoolean(Context context, boolean secondary, String key, boolean value) {
        displaySlotStore(context, secondary).edit()
                .putBoolean(displayWriteKey(context, secondary, key), value).apply();
    }

    static void putDisplayString(Context context, boolean secondary, String key, String value) {
        displaySlotStore(context, secondary).edit()
                .putString(displayWriteKey(context, secondary, key), value).apply();
    }

    /**
     * 把当前样式下**用户改过**的共用显示参数复制到本屏的其它样式（issue #39 的批量入口）。
     *
     * <p>只复制显式存在于当前样式下的项：没调过的项继续继承本屏共用值，不至于一次性把所有样式
     * 都写死，之后改共用设置也不会失灵。
     *
     * @return 复制的项数
     */
    static int applyCurrentStyleToOtherStyles(Context context, boolean secondary) {
        String source = displayStyle(context, secondary);
        SharedPreferences slot = displaySlotStore(context, secondary);
        SharedPreferences.Editor editor = slot.edit();
        int copied = 0;
        for (String key : STYLE_SCOPED_KEYS) {
            String sourceKey = styleScopedKey(key, secondary, source);
            if (!slot.contains(sourceKey)) continue;
            Object value = slot.getAll().get(sourceKey);
            if (value == null) continue;
            for (String style : OVERLAY_STYLES) {
                if (style.equals(source)) continue;
                putPreference(editor, styleScopedKey(key, secondary, style), value);
                copied++;
            }
        }
        editor.apply();
        return copied;
    }

    private static void putPreference(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
        else if (value instanceof Integer) editor.putInt(key, (Integer) value);
        else if (value instanceof Float) editor.putFloat(key, (Float) value);
        else if (value instanceof Long) editor.putLong(key, (Long) value);
        else if (value instanceof String) editor.putString(key, (String) value);
    }

    /**
     * Where a display-scoped value lives for the window behind {@code context}.
     *
     * <p>The main overlay and the legacy secondary share the app's one preference file and are
     * told apart by the {@code _main}/{@code _secondary} suffix. An extra screen (the second
     * secondary display, a HUD, …) is a {@link DisplaySlotHost} with a file of its own, so the
     * same suffix lands in its private store. Both readers and writers go through here, which is
     * what makes every existing per-display accessor work unchanged for any number of screens.
     */
    private static SharedPreferences displaySlotStore(Context context, boolean secondary) {
        if (!secondary || !(context instanceof DisplaySlotHost)) return get(context);
        SharedPreferences slot = ((DisplaySlotHost) context).displaySlotPreferences();
        return slot == null ? get(context) : slot;
    }

    static float textScale(Context context) { return textScale(context, false); }

    static float textScale(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_TEXT_SCALE, 100) / 100f;
    }

    /**
     * Transport controls for one screen. The secondary used to read the bare key while the
     * settings page wrote the {@code _secondary} one, so the switch never reached the renderer
     * (issue #30); going through {@link #displayBoolean} keeps reader and writer aligned, and the
     * fallback still honours a legacy bare key from very old installs.
     */
    static boolean showPlaybackControls(Context context, boolean secondary) {
        return !secondary
                || displayBoolean(context, secondary, KEY_SECONDARY_PLAYBACK_CONTROLS, false);
    }

    static int titleScale(Context context, boolean secondary) {
        return Math.max(60, Math.min(180,
                displayInt(context, secondary, KEY_TITLE_SCALE, 100)));
    }

    /**
     * 歌手字号（歌名字号的百分比，issue #63）：{@link MetadataTypeScaleMath#UNSET} = 沿用各样式原本
     * 的比例（升级观感不变），100 = 与歌名同号。按「屏 × 样式」保存。
     */
    static int artistScale(Context context) { return artistScale(context, false); }

    static int artistScale(Context context, boolean secondary) {
        return MetadataTypeScaleMath.normalizePercent(displayInt(context, secondary,
                KEY_ARTIST_SCALE, MetadataTypeScaleMath.UNSET));
    }

    /**
     * 面板内律动 / 频谱条的高度（占面板高度的百分比，issue #57）：{@link SpectrumLayoutMath#UNSET}
     * 时沿用原来的 14–42dp / 15% 规则，老安装观感不变。按「屏 × 样式」保存。
     */
    static int spectrumHeightPercent(Context context) { return spectrumHeightPercent(context, false); }

    static int spectrumHeightPercent(Context context, boolean secondary) {
        return SpectrumLayoutMath.normalizePercent(displayInt(context, secondary,
                KEY_SPECTRUM_HEIGHT_PERCENT, SpectrumLayoutMath.UNSET));
    }

    /** 频谱条与面板底部的额外留白（dp，issue #57），用于把律动和歌词 / 底边拉开一点。 */
    static int spectrumGapDp(Context context) { return spectrumGapDp(context, false); }

    static int spectrumGapDp(Context context, boolean secondary) {
        return Math.max(0, Math.min(SpectrumLayoutMath.MAX_GAP_DP,
                displayInt(context, secondary, KEY_SPECTRUM_GAP_DP, 0)));
    }

    /**
     * 面板边缘阴影强度（百分比，issue #56）：{@link PanelShadowMath#UNSET} = 各样式原样（经典有写死的
     * 投影，其余没有），0 = 明确关掉，>0 = 按强度画向内柔化的边缘。按「屏 × 样式」保存。
     */
    static int panelShadowPercent(Context context) { return panelShadowPercent(context, false); }

    static int panelShadowPercent(Context context, boolean secondary) {
        return PanelShadowMath.normalizePercent(displayInt(context, secondary,
                KEY_PANEL_SHADOW_PERCENT, PanelShadowMath.UNSET));
    }

    /** 背景遮罩档位（issue #58）：auto = 沿用各样式原来的下限，off = 完全不画遮罩。 */
    static String styleMaskMode(Context context) { return styleMaskMode(context, false); }

    static String styleMaskMode(Context context, boolean secondary) {
        return ArtworkBackgroundMath.normalizeMode(displayString(context, secondary,
                KEY_STYLE_MASK_MODE, ArtworkBackgroundMath.MODE_AUTO));
    }

    /** 背景亮度（-100..100，issue #58）：只作用在「跟随封面」的背景图上。 */
    static int styleBrightness(Context context) { return styleBrightness(context, false); }

    static int styleBrightness(Context context, boolean secondary) {
        return ArtworkBackgroundMath.normalizeBrightness(displayInt(context, secondary,
                KEY_STYLE_BRIGHTNESS, 0));
    }

    /** 允许面板移出屏幕边缘（issue #49）：默认关闭，按屏保存。 */
    static boolean overlayAllowOffscreen(Context context) {
        return overlayAllowOffscreen(context, false);
    }

    static boolean overlayAllowOffscreen(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_OVERLAY_ALLOW_OFFSCREEN, false);
    }

    /** 允许探出屏幕的比例（相对面板尺寸，issue #49）：默认 30%，上限见 OverlayDragMath。 */
    static int overlayOffscreenPercent(Context context) {
        return overlayOffscreenPercent(context, false);
    }

    static int overlayOffscreenPercent(Context context, boolean secondary) {
        return OverlayDragMath.normalizePercent(displayInt(context, secondary,
                KEY_OVERLAY_OFFSCREEN_PERCENT, OverlayDragMath.DEFAULT_PERCENT));
    }

    /**
     * 内容留白（占样式原本内边距的百分比，issue #49）：{@link ContentPaddingMath#UNSET} = 沿用样式原样，
     * 0 = 文字尽量贴边。按「屏 × 样式」保存。
     */
    static int contentPaddingPercent(Context context) { return contentPaddingPercent(context, false); }

    static int contentPaddingPercent(Context context, boolean secondary) {
        return ContentPaddingMath.normalizePercent(displayInt(context, secondary,
                KEY_CONTENT_PADDING_PERCENT, ContentPaddingMath.UNSET));
    }

    /**
     * 长句显示方式（issue #47）：{@code marquee}（默认）/ {@code shrink} / {@code wrap}。
     * 按「屏 × 样式」保存——1 行的纯净与 3 行的经典对同一句的取舍本来就不一样。
     */
    static String longLineMode(Context context) { return longLineMode(context, false); }

    static String longLineMode(Context context, boolean secondary) {
        return LongLineLayout.normalizeMode(displayString(context, secondary,
                KEY_LONG_LINE_MODE, LongLineLayout.MODE_MARQUEE));
    }

    /**
     * Requested theme: {@code light}, {@code dark} or {@code auto} (follow the system). The
     * scheduled dark window is a separate switch; see {@link #themeScheduleEnabled}.
     */
    static String themeMode(Context context) {
        String value = get(context).getString(KEY_THEME_MODE, "auto");
        // "schedule" was briefly a mode of its own; it is now the switch below with mode auto.
        if ("schedule".equals(value)) return "auto";
        return "light".equals(value) || "dark".equals(value) ? value : "auto";
    }

    static void setThemeMode(Context context, String value) {
        String normalized = "light".equals(value) || "dark".equals(value) ? value : "auto";
        get(context).edit().putString(KEY_THEME_MODE, normalized).apply();
    }

    /**
     * 可选的时间段：打开后按下面的深色时段自动切换深浅色；关掉就按主题模式（跟随系统 / 白天 /
     * 夜晚）判断（issue #34）。
     */
    static boolean themeScheduleEnabled(Context context) {
        if (get(context).contains(KEY_THEME_SCHEDULE_ENABLED)) {
            return get(context).getBoolean(KEY_THEME_SCHEDULE_ENABLED, false);
        }
        // 旧版本把"按时间段"存在主题模式里：迁移成开关打开 + 模式跟随系统。
        return "schedule".equals(get(context).getString(KEY_THEME_MODE, "auto"));
    }

    static void setThemeScheduleEnabled(Context context, boolean enabled) {
        android.content.SharedPreferences.Editor editor = get(context).edit()
                .putBoolean(KEY_THEME_SCHEDULE_ENABLED, enabled);
        if (enabled && "schedule".equals(get(context).getString(KEY_THEME_MODE, "auto"))) {
            editor.putString(KEY_THEME_MODE, "auto");
        }
        editor.apply();
    }

    /** Minute of the day the scheduled dark theme starts; 19:00 by default. */
    static int themeScheduleStartMinute(Context context) {
        return Math.max(0, Math.min(24 * 60 - 1,
                get(context).getInt(KEY_THEME_SCHEDULE_START_MINUTE, 19 * 60)));
    }

    static void setThemeScheduleStartMinute(Context context, int value) {
        get(context).edit().putInt(KEY_THEME_SCHEDULE_START_MINUTE, value).apply();
    }

    /** Minute of the day the scheduled dark theme ends; 07:00 by default. */
    static int themeScheduleEndMinute(Context context) {
        return Math.max(0, Math.min(24 * 60 - 1,
                get(context).getInt(KEY_THEME_SCHEDULE_END_MINUTE, 7 * 60)));
    }

    static void setThemeScheduleEndMinute(Context context, int value) {
        get(context).edit().putInt(KEY_THEME_SCHEDULE_END_MINUTE, value).apply();
    }

    /**
     * The theme in effect right now. {@code auto} is left to the caller (it asks the system for
     * the night mode); when the optional scheduled window is switched on, its verdict wins over
     * the system so a head unit that never reports night mode can still go dark in the evening
     * (issue #34).
     */
    static String resolvedThemeMode(Context context) {
        String mode = themeMode(context);
        if ("light".equals(mode) || "dark".equals(mode)) return mode;
        if (!themeScheduleEnabled(context)) return "auto";
        int minute = DayNightSchedule.minuteOfDay(System.currentTimeMillis());
        return DayNightSchedule.isDarkMinute(minute, themeScheduleStartMinute(context),
                themeScheduleEndMinute(context)) ? "dark" : "light";
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
        String style = overlayStyle(context, secondary);
        if ("compact".equals(style)) {
            // Users park compact panels in tight HUD corners; keep only a readable floor.
            int floor = compactShowNextLine(context, secondary) ? 64 : 48;
            boolean transportButtons = showPreviousButton(context)
                    || showPlayPauseButton(context) || showNextButton(context);
            if (transportButtons) {
                // Reserve the bottom control row so the buttons stay tappable.
                floor = Math.max(floor, compactShowNextLine(context, secondary) ? 88 : 68);
            }
            return floor;
        }
        // 灵动岛是矮胶囊：允许压到 48dp（issue #54），其它样式保持原来的下限。
        if ("island".equals(style)) return 48;
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
        return displayInt(context, secondary, KEY_LYRIC_OFFSET, 0)
                + lyricTrackOffsetMs(context);
    }

    static int lyricTrackOffsetMs(Context context) {
        String key = MusicStateStore.activeLyricOffsetKey();
        return key.isEmpty() ? 0 : get(context).getInt("lyric_track_offset_" + key, 0);
    }

    static void putLyricTrackOffsetMs(Context context, int value) {
        String key = MusicStateStore.activeLyricOffsetKey();
        if (key.isEmpty()) return;
        get(context).edit().putInt("lyric_track_offset_" + key,
                Math.max(-5_000, Math.min(5_000, value))).apply();
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

    static int previousLyricOpacity(Context context, boolean secondary) {
        return Math.max(0, Math.min(100,
                displayInt(context, secondary, KEY_PREVIOUS_LYRIC_OPACITY, 100)));
    }

    /**
     * 上一句字号, as a share of the current line. The default reproduces the size the style used
     * to hard-code (12dp against the current line's 22dp), so existing installs look unchanged
     * (issue #38).
     */
    static int previousLyricScale(Context context, boolean secondary) {
        return Math.max(45, Math.min(160,
                displayInt(context, secondary, KEY_PREVIOUS_LYRIC_SCALE, 55)));
    }

    /**
     * 内容垂直对齐: {@code ""} keeps the style's own placement, otherwise "top", "center" or
     * "bottom" (issue #41). A panel dragged to the screen edge used to keep a strip of empty
     * space above the text because the rows were pinned to fixed baselines.
     */
    static String contentAlign(Context context, boolean secondary) {
        String value = displayString(context, secondary, KEY_CONTENT_ALIGN, "");
        return "top".equals(value) || "center".equals(value) || "bottom".equals(value)
                ? value : "";
    }

    /** 面板圆角占短边的百分比；{@code -1} 表示跟随样式原本的圆角（issue #37）。 */
    static int cornerRadiusPercent(Context context, boolean secondary) {
        return Math.max(-1, Math.min(50,
                displayInt(context, secondary, KEY_CORNER_RADIUS_PERCENT, -1)));
    }

    /** 圆形封面（紧凑 / AMLL 用）；Refined 有它自己的「方形专辑封面」开关（issue #22）。 */
    static boolean roundCover(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_ROUND_COVER, false);
    }

    /** 圆形封面按播放进度旋转，暂停即停（issue #22）。 */
    static boolean coverRotation(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_COVER_ROTATION, false);
    }

    /** 封面转一圈需要的秒数；20 秒 ≈ 3 转/分，接近黑胶。 */
    static int coverRotationPeriodSeconds(Context context, boolean secondary) {
        return Math.max(3, Math.min(60,
                displayInt(context, secondary, KEY_COVER_ROTATION_PERIOD_SECONDS, 20)));
    }

    /** 「正在匹配歌词」用动画而不是一行静止文字（issue #45）。 */
    static boolean matchingAnimation(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_MATCHING_ANIMATION, true);
    }

    /** 匹配耗时超过这么久才显示动画，0 表示立刻显示；默认 3 秒，避免瞬间匹配成功时闪一下。 */
    static int matchingAnimationDelayMs(Context context, boolean secondary) {
        return Math.max(0, Math.min(10_000,
                displayInt(context, secondary, KEY_MATCHING_ANIMATION_DELAY, 3_000)));
    }

    /**
     * 没有逐字时间轴时按本句时长估算逐字进度（issue #21）。默认关闭：估算在长音、拖腔与行内
     * 停顿上会偏，愿意接受这种偏差的用户可以打开，让普通 .lrc 也有逐字变色的观感。
     */
    static boolean estimatedWordKaraoke(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_ESTIMATED_WORD_KARAOKE, false);
    }

    static boolean previousLyricParticles(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_PREVIOUS_LYRIC_PARTICLES, true);
    }

    /** Erases each word of the current line as soon as it has been sung. */
    static boolean wordDissolve(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_WORD_DISSOLVE, false);
    }

    /** Dust density for the dissolve, as a percentage of the designed amount. */
    static int particleAmountPercent(Context context, boolean secondary) {
        return Math.max(20, Math.min(300,
                displayInt(context, secondary, KEY_PARTICLE_AMOUNT, 100)));
    }

    static boolean showPlayerStatus(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_SHOW_PLAYER_STATUS, true);
    }

    static boolean showProgress(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_SHOW_PROGRESS, true);
    }

    /** Zero means that the selected overlay style keeps controlling lyric colors. */
    static int lyricColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_LYRIC_COLOR, 0);
    }

    static void setLyricColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_LYRIC_COLOR,
                color == 0 ? 0 : (color | 0xFF000000));
    }

    static int currentLyricColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_CURRENT_LYRIC_COLOR, 0);
    }

    static void setCurrentLyricColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_CURRENT_LYRIC_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    static int inactiveLyricColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_INACTIVE_LYRIC_COLOR, 0);
    }

    static void setInactiveLyricColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_INACTIVE_LYRIC_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    /** Zero keeps the flat custom slot; a pair value wins only while theme tracking is on. */
    static int currentLyricLightColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_CURRENT_LYRIC_LIGHT_COLOR, 0);
    }

    static int currentLyricDarkColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_CURRENT_LYRIC_DARK_COLOR, 0);
    }

    static int inactiveLyricLightColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_INACTIVE_LYRIC_LIGHT_COLOR, 0);
    }

    static int inactiveLyricDarkColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_INACTIVE_LYRIC_DARK_COLOR, 0);
    }

    static void setCurrentLyricLightColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_CURRENT_LYRIC_LIGHT_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    static void setCurrentLyricDarkColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_CURRENT_LYRIC_DARK_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    static void setInactiveLyricLightColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_INACTIVE_LYRIC_LIGHT_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    static void setInactiveLyricDarkColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_INACTIVE_LYRIC_DARK_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    /**
     * Slot resolution shared with the panel: while theme tracking is on an explicit
     * light/dark variant wins; otherwise (or when unset) the flat custom color applies.
     */
    static int resolveThemedSlotColor(boolean followTheme, boolean environmentLight,
                                      int flat, int light, int dark) {
        if (!followTheme) return flat;
        int themed = environmentLight ? light : dark;
        return themed != 0 ? themed : flat;
    }

    /** Zero picks the contrast color against the rendered glyph automatically. */
    static int lyricOutlineColor(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_LYRIC_OUTLINE_COLOR, 0);
    }

    static void setLyricOutlineColor(Context context, boolean secondary, int color) {
        putDisplayInt(context, secondary, KEY_LYRIC_OUTLINE_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    /** Percent of full opacity applied to the outline stroke; default mirrors the old 225/255. */
    static int lyricOutlineAlphaPercent(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_LYRIC_OUTLINE_ALPHA, 88);
    }

    static void setLyricOutlineAlphaPercent(Context context, boolean secondary, int percent) {
        putDisplayInt(context, secondary, KEY_LYRIC_OUTLINE_ALPHA,
                Math.max(0, Math.min(100, percent)));
    }

    /** Outline stroke width as a percent of the lyric font size; default mirrors the old 7.5%. */
    static int lyricOutlineWidthPercent(Context context, boolean secondary) {
        return displayInt(context, secondary, KEY_LYRIC_OUTLINE_WIDTH, 8);
    }

    static void setLyricOutlineWidthPercent(Context context, boolean secondary, int percent) {
        putDisplayInt(context, secondary, KEY_LYRIC_OUTLINE_WIDTH,
                Math.max(1, Math.min(40, percent)));
    }

    static boolean lyricOutline(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_LYRIC_OUTLINE, false);
    }

    static boolean lyricOutline(Context context, boolean secondary, boolean current) {
        String key = current ? KEY_CURRENT_LYRIC_OUTLINE : KEY_INACTIVE_LYRIC_OUTLINE;
        return displayBoolean(context, secondary, key, lyricOutline(context, secondary));
    }

    static int lyricOutlineColor(Context context, boolean secondary, boolean current) {
        String key = current ? KEY_CURRENT_LYRIC_OUTLINE_COLOR : KEY_INACTIVE_LYRIC_OUTLINE_COLOR;
        return displayInt(context, secondary, key, lyricOutlineColor(context, secondary));
    }

    static int lyricOutlineAlphaPercent(Context context, boolean secondary, boolean current) {
        String key = current ? KEY_CURRENT_LYRIC_OUTLINE_ALPHA : KEY_INACTIVE_LYRIC_OUTLINE_ALPHA;
        return displayInt(context, secondary, key, lyricOutlineAlphaPercent(context, secondary));
    }

    static int lyricOutlineWidthPercent(Context context, boolean secondary, boolean current) {
        String key = current ? KEY_CURRENT_LYRIC_OUTLINE_WIDTH : KEY_INACTIVE_LYRIC_OUTLINE_WIDTH;
        return displayInt(context, secondary, key, lyricOutlineWidthPercent(context, secondary));
    }

    static void setLyricOutlineColor(Context context, boolean secondary, boolean current, int color) {
        putDisplayInt(context, secondary,
                current ? KEY_CURRENT_LYRIC_OUTLINE_COLOR : KEY_INACTIVE_LYRIC_OUTLINE_COLOR,
                color == 0 ? 0 : color | 0xFF000000);
    }

    static void setLyricOutlineAlphaPercent(Context context, boolean secondary, boolean current,
                                            int percent) {
        putDisplayInt(context, secondary,
                current ? KEY_CURRENT_LYRIC_OUTLINE_ALPHA : KEY_INACTIVE_LYRIC_OUTLINE_ALPHA,
                Math.max(0, Math.min(100, percent)));
    }

    static void setLyricOutlineWidthPercent(Context context, boolean secondary, boolean current,
                                            int percent) {
        putDisplayInt(context, secondary,
                current ? KEY_CURRENT_LYRIC_OUTLINE_WIDTH : KEY_INACTIVE_LYRIC_OUTLINE_WIDTH,
                Math.max(1, Math.min(40, percent)));
    }

    static boolean trailingAccent(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_TRAILING_ACCENT, true);
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
        return resolvePlayerLyricCatalog(sourceId, override, lyricCatalog(context));
    }

    /**
     * Resolves the rule for one concrete player application.  Package-specific rules deliberately
     * do not share a value with another application that happens to use the same provider type.
     */
    static String lyricCatalog(Context context, String sourceId, String packageName) {
        String override = get(context).getString(playerPackageLyricCatalogKey(packageName), "");
        return resolvePlayerLyricCatalog(sourceId, override, lyricCatalog(context));
    }

    static String resolvePlayerLyricCatalog(String sourceId, String override, String fallback) {
        return resolveLyricCatalog(override, "kuwo".equals(sourceId) ? "kuwo" : fallback);
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
                || "soda".equals(value) || "migu".equals(value)
                || "auto".equals(value)) return value;
        return "auto";
    }

    static boolean playerCatalogFallback(Context context) {
        return get(context).getBoolean(KEY_PLAYER_CATALOG_FALLBACK, true);
    }

    static String overlayStyle(Context context) {
        return overlayStyle(context, false);
    }

    static String overlayStyle(Context context, boolean secondary) {
        SharedPreferences slot = displaySlotStore(context, secondary);
        String key = secondary ? KEY_SECONDARY_OVERLAY_STYLE : KEY_MAIN_OVERLAY_STYLE;
        if (slot.contains(key)) return normalizeOverlayStyle(slot.getString(key, "refined"));
        // A brand new extra screen starts from whatever the secondary already looks like, then
        // diverges as soon as it is adjusted.
        SharedPreferences shared = get(context);
        String fallback = shared.contains(KEY_OVERLAY_STYLE)
                ? shared.getString(KEY_OVERLAY_STYLE, "refined")
                : secondary ? "compact" : "refined";
        return normalizeOverlayStyle(shared.getString(key, fallback));
    }

    static void setOverlayStyle(Context context, boolean secondary, String style) {
        displaySlotStore(context, secondary).edit()
                .putString(secondary ? KEY_SECONDARY_OVERLAY_STYLE
                        : KEY_MAIN_OVERLAY_STYLE, normalizeOverlayStyle(style)).apply();
    }

    private static String normalizeOverlayStyle(String style) {
        if ("default".equals(style) || "refined".equals(style)
                || "compact".equals(style) || "pip".equals(style)
                || "custom".equals(style) || "amll".equals(style) || "pure".equals(style)
                || "island".equals(style)) {
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
        return Math.max(1, Math.min(7,
                displayInt(context, secondary, KEY_STYLE_LYRIC_LINES, 3)));
    }

    static boolean pureShowTranslation(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_PURE_SHOW_TRANSLATION, true);
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

    /**
     * 播放器不给封面时的行为（issue #50）：{@code placeholder} 保持原样画一块占位，
     * {@code hide} 把封面区域整个收起来，版面跟着内收。
     */
    static String coverMissingMode(Context context) { return coverMissingMode(context, false); }

    static String coverMissingMode(Context context, boolean secondary) {
        return displayString(context, secondary, KEY_COVER_MISSING_MODE, "placeholder");
    }

    static boolean hideCoverWithoutArt(Context context) { return hideCoverWithoutArt(context, false); }

    static boolean hideCoverWithoutArt(Context context, boolean secondary) {
        return "hide".equals(coverMissingMode(context, secondary));
    }

    /** 星空背景的密度（星点数百分比）、速度、星点大小（issue #59）。 */
    static int starfieldDensityPercent(Context context) {
        return starfieldDensityPercent(context, false);
    }

    static int starfieldDensityPercent(Context context, boolean secondary) {
        return Math.max(10, Math.min(200,
                displayInt(context, secondary, KEY_STARFIELD_DENSITY, 60)));
    }

    static int starfieldSpeedPercent(Context context) { return starfieldSpeedPercent(context, false); }

    static int starfieldSpeedPercent(Context context, boolean secondary) {
        return Math.max(0, Math.min(200,
                displayInt(context, secondary, KEY_STARFIELD_SPEED, 100)));
    }

    static int starfieldSizePercent(Context context) { return starfieldSizePercent(context, false); }

    static int starfieldSizePercent(Context context, boolean secondary) {
        return Math.max(30, Math.min(250,
                displayInt(context, secondary, KEY_STARFIELD_SIZE, 100)));
    }

    static boolean starfieldFollowCover(Context context) {
        return starfieldFollowCover(context, false);
    }

    static boolean starfieldFollowCover(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_STARFIELD_FOLLOW_COVER, true);
    }

    /** 省电档：星点静止、不闪烁，也不要求连续帧（issue #59）。 */
    static boolean starfieldStill(Context context) { return starfieldStill(context, false); }

    static boolean starfieldStill(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_STARFIELD_STILL, false);
    }

    /** 星空渲染帧率：5 fps 最省电，60 fps 与屏幕刷新对齐（issue #59）。 */
    static int starfieldFps(Context context) { return starfieldFps(context, false); }

    static int starfieldFps(Context context, boolean secondary) {
        return Math.max(StarfieldField.MIN_FPS, Math.min(StarfieldField.MAX_FPS,
                displayInt(context, secondary, KEY_STARFIELD_FPS, StarfieldField.DEFAULT_FPS)));
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

    /** 播放按钮偏移的上限（百分比，issue #73）：原来 ±40% 在带鱼屏上够不到面板两端。 */
    static final int PLAYBACK_CONTROL_OFFSET_LIMIT = 100;

    static int playbackControlX(Context context) {
        return playbackControlX(context, false);
    }

    /**
     * 播放按钮水平偏移（面板宽度的百分比）。按屏保存：副屏没单独调过时沿用主屏（老裸键），调过只
     * 影响这一屏（issue #73；写法同 {@link #showPreviousButton(Context, boolean)}）。
     */
    static int playbackControlX(Context context, boolean secondary) {
        return clampPlaybackControlOffset(displayInt(context, secondary, KEY_PLAYBACK_CONTROL_X,
                get(context).getInt(KEY_PLAYBACK_CONTROL_X, 0)));
    }

    static int playbackControlY(Context context) {
        return playbackControlY(context, false);
    }

    static int playbackControlY(Context context, boolean secondary) {
        return clampPlaybackControlOffset(displayInt(context, secondary, KEY_PLAYBACK_CONTROL_Y,
                get(context).getInt(KEY_PLAYBACK_CONTROL_Y, 0)));
    }

    /** 纯函数：偏移量收口到 ±{@link #PLAYBACK_CONTROL_OFFSET_LIMIT}。 */
    static int clampPlaybackControlOffset(int value) {
        return Math.max(-PLAYBACK_CONTROL_OFFSET_LIMIT,
                Math.min(PLAYBACK_CONTROL_OFFSET_LIMIT, value));
    }

    static String fullscreenCloseMode(Context context) {
        String value = get(context).getString(KEY_FULLSCREEN_CLOSE_MODE, "fade");
        return "always".equals(value) || "hidden".equals(value) ? value : "fade";
    }

    static String overlayCloseMode(Context context) {
        String value = get(context).getString(KEY_OVERLAY_CLOSE_MODE, "fade");
        return "always".equals(value) || "hidden".equals(value)
                || "auto_fade".equals(value) || "auto_hide".equals(value) ? value : "fade";
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

    static String localLyricDirectoryPath(Context context) {
        return get(context).getString(KEY_LOCAL_LYRIC_DIRECTORY_PATH, "");
    }

    static boolean avrcpEnabled(Context context) {
        return get(context).getBoolean(KEY_AVRCP_ENABLED, true);
    }

    /**
     * Some phone music apps hand the car a composite artist slot ("歌名 - 歌手") and put the live
     * lyric line in the title, which makes the title useless as a track identity. With this on,
     * the artist slot is parsed for the identity and the raw title becomes the live lyric.
     */
    static boolean compositeIdentityFromArtist(Context context) {
        return get(context).getBoolean(KEY_COMPOSITE_IDENTITY_FROM_ARTIST, false);
    }

    /**
     * Horizontal alignment of the lyric rows: {@code "left"}, {@code "center"}, or {@code ""} to
     * keep each style's own default. The classic, compact and pure layouts centre their lyrics;
     * Refined, AMLL and PiP are two-column layouts that are left-aligned by construction.
     */
    static String lyricAlign(Context context, boolean secondary) {
        String value = displayString(context, secondary, KEY_LYRIC_ALIGN, "");
        return "left".equals(value) || "center".equals(value) ? value : "";
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
        // Each extra screen keeps its parameters in a file of its own; a reset has to reach those
        // too, otherwise a 恢复默认设置 would leave the HUD looking the way it did before.
        for (int index = 0; index < DisplaySlotRegistry.entries(context).size(); index++) {
            SharedPreferences slot = context.getSharedPreferences(
                    DisplaySlotContext.fileName(DisplaySlotRegistry.slotFor(index)),
                    Context.MODE_PRIVATE);
            removed += slot.getAll().size();
            slot.edit().clear().commit();
        }
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
        return displaySlotStore(context, secondary).getBoolean(secondary
                ? KEY_SECONDARY_OVERLAY_TOUCH_THROUGH : KEY_MAIN_OVERLAY_TOUCH_THROUGH, false);
    }

    static void putOverlayTouchThrough(Context context, boolean secondary, boolean enabled) {
        displaySlotStore(context, secondary).edit().putBoolean(secondary
                ? KEY_SECONDARY_OVERLAY_TOUCH_THROUGH : KEY_MAIN_OVERLAY_TOUCH_THROUGH, enabled)
                .apply();
    }

    /**
     * Locks the window and lets every non-control touch fall through to what is underneath; the
     * playback buttons keep a touchable surface of their own while they are shown.
     */
    static boolean overlayPositionLocked(Context context, boolean secondary) {
        return displayBoolean(context, secondary, KEY_OVERLAY_POSITION_LOCKED, false);
    }

    /**
     * Positions are style-scoped as well as display-scoped. A 16:9 PIP card and a wide AMLL
     * panel cannot share a useful top-left coordinate, which was the source of the visible jump
     * when switching modes. Legacy per-display positions remain the first-run fallback.
     */
    static String overlayPositionKey(boolean secondary, String style, boolean horizontal) {
        String safeStyle = style == null || style.trim().isEmpty() ? "classic" : style.trim();
        return (secondary ? "secondary" : "main") + "_" + safeStyle + "_"
                + (horizontal ? "x" : "y");
    }

    static int overlayPosition(Context context, boolean secondary, String style,
                               boolean horizontal, int fallback) {
        SharedPreferences preferences = displaySlotStore(context, secondary);
        String scoped = overlayPositionKey(secondary, style, horizontal);
        if (preferences.contains(scoped)) return preferences.getInt(scoped, fallback);
        String legacy = secondary ? (horizontal ? KEY_SECONDARY_X : KEY_SECONDARY_Y)
                : (horizontal ? KEY_MAIN_X : KEY_MAIN_Y);
        return get(context).getInt(legacy, fallback);
    }

    /** Stores a dragged overlay position in the same store the window reads it back from. */
    static void putOverlayPosition(Context context, boolean secondary, String key, int value) {
        displaySlotStore(context, secondary).edit().putInt(key, value).apply();
    }

    static boolean hideOverlaysWhenNotPlaying(Context context) {
        return get(context).getBoolean(KEY_HIDE_OVERLAYS_WHEN_NOT_PLAYING, false);
    }

    static boolean hideOverlaysInPlayer(Context context) {
        return get(context).getBoolean(KEY_HIDE_OVERLAYS_IN_PLAYER, false);
    }

    /** 匹配到的歌词长时间不滚动时改用播放器实时歌词；默认开启（issue #44）。 */
    static boolean stuckLyricFallback(Context context) {
        return get(context).getBoolean(KEY_STUCK_LYRIC_FALLBACK, true);
    }

    static void setStuckLyricFallback(Context context, boolean enabled) {
        get(context).edit().putBoolean(KEY_STUCK_LYRIC_FALLBACK, enabled).apply();
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

    static boolean hideSelectedAppsOnMain(Context context) {
        return get(context).getBoolean(KEY_HIDE_SELECTED_APPS_ON_MAIN, true);
    }

    static boolean hideSelectedAppsOnSecondary(Context context) {
        return get(context).getBoolean(KEY_HIDE_SELECTED_APPS_ON_SECONDARY, true);
    }

    /**
     * 「指定应用」规则的方向（issue #43）：{@code false} = 黑名单（名单内的应用里隐藏），
     * {@code true} = 白名单（只在名单内的应用里显示）。白名单每屏独立，因为主屏与副屏想在
     * 哪些应用里显示往往不一样。
     */
    static boolean appRuleWhitelist(Context context, boolean secondary) {
        return get(context).getBoolean(secondary
                ? KEY_APP_RULE_WHITELIST_SECONDARY : KEY_APP_RULE_WHITELIST_MAIN, false);
    }

    static void setAppRuleWhitelist(Context context, boolean secondary, boolean value) {
        get(context).edit().putBoolean(secondary
                ? KEY_APP_RULE_WHITELIST_SECONDARY : KEY_APP_RULE_WHITELIST_MAIN, value).apply();
    }

    /**
     * 这些应用发布的媒体元数据会被忽略（issue #35）。车机自带的媒体中心、导航或蓝牙通道常常
     * 抢走歌词，把它们列进来就不会再顶掉正在播放的播放器。
     */
    static Set<String> ignoredPlayerPackages(Context context) {
        Set<String> stored = get(context).getStringSet(KEY_IGNORED_PLAYER_PACKAGES,
                Collections.emptySet());
        return stored == null ? new LinkedHashSet<>() : new LinkedHashSet<>(stored);
    }

    static void setIgnoredPlayerPackages(Context context, Set<String> packages) {
        get(context).edit().putStringSet(KEY_IGNORED_PLAYER_PACKAGES,
                packages == null ? Collections.emptySet() : new LinkedHashSet<>(packages)).apply();
    }

    static boolean ignoredPlayerPackage(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) return false;
        return ignoredPlayerPackages(context).contains(packageName.trim());
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
        int maximum = topLyricMaxOffsetDp(context);
        return Math.max(-maximum, Math.min(maximum,
                get(context).getInt(KEY_TOP_LYRIC_OFFSET_X_DP, 0)));
    }

    /** The horizontal adjustment follows the actual display instead of a fixed dp cap. */
    static int topLyricMaxOffsetDp(Context context) {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float density = Math.max(0.1f, metrics.density);
        return Math.max(240, Math.round(metrics.widthPixels / density));
    }

    static int settingsUiScale(Context context) {
        int value = get(context).getInt(KEY_SETTINGS_UI_SCALE, 100);
        return value == 150 || value == 200 ? value : 100;
    }

    static void setSettingsUiScale(Context context, int percent) {
        int safe = percent == 150 || percent == 200 ? percent : 100;
        get(context).edit().putInt(KEY_SETTINGS_UI_SCALE, safe).apply();
    }

    static boolean conciseSettingsMode(Context context) {
        return "concise".equals(get(context).getString(KEY_MAIN_SETTINGS_MODE, "concise"));
    }

    static void setConciseSettingsMode(Context context, boolean concise) {
        get(context).edit().putString(KEY_MAIN_SETTINGS_MODE,
                concise ? "concise" : "complete").apply();
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

    /**
     * The top strip's own dissolve settings.
     *
     * <p>The strip sits next to the status bar rather than on the main screen, and reading the
     * main display's values made its particles, dust amount and word erase change whenever the
     * main overlay was adjusted. Each of these follows the main screen until the strip's own page
     * writes it once, so an installation that never opened that page keeps the look it had.
     */
    static boolean topLyricParticles(Context context) {
        return get(context).contains(KEY_TOP_LYRIC_PARTICLES)
                ? get(context).getBoolean(KEY_TOP_LYRIC_PARTICLES, true)
                : previousLyricParticles(context, false);
    }

    static void setTopLyricParticles(Context context, boolean value) {
        get(context).edit().putBoolean(KEY_TOP_LYRIC_PARTICLES, value).apply();
    }

    static int topLyricParticleAmount(Context context) {
        return get(context).contains(KEY_TOP_LYRIC_PARTICLE_AMOUNT)
                ? Math.max(20, Math.min(300,
                get(context).getInt(KEY_TOP_LYRIC_PARTICLE_AMOUNT, 100)))
                : particleAmountPercent(context, false);
    }

    static void setTopLyricParticleAmount(Context context, int value) {
        setTopLyricInt(context, KEY_TOP_LYRIC_PARTICLE_AMOUNT, value);
    }

    static boolean topLyricWordDissolve(Context context) {
        return get(context).contains(KEY_TOP_LYRIC_WORD_DISSOLVE)
                ? get(context).getBoolean(KEY_TOP_LYRIC_WORD_DISSOLVE, false)
                : wordDissolve(context, false);
    }

    static void setTopLyricWordDissolve(Context context, boolean value) {
        get(context).edit().putBoolean(KEY_TOP_LYRIC_WORD_DISSOLVE, value).apply();
    }

    static int topLyricPreviousOpacity(Context context) {
        return get(context).contains(KEY_TOP_LYRIC_PREVIOUS_OPACITY)
                ? Math.max(0, Math.min(100,
                get(context).getInt(KEY_TOP_LYRIC_PREVIOUS_OPACITY, 100)))
                : previousLyricOpacity(context, false);
    }

    static void setTopLyricPreviousOpacity(Context context, int value) {
        setTopLyricInt(context, KEY_TOP_LYRIC_PREVIOUS_OPACITY, value);
    }

    /**
     * 顶部歌词条的「下一句字号」。The strip used to read the main screen's value, so tuning the
     * strip's own next line also changed the main overlay (issue #19). Unset follows the main
     * screen, so an installation that never touched this page looks exactly as before.
     */
    static int topLyricNextFontScale(Context context) {
        return get(context).contains(KEY_TOP_LYRIC_NEXT_FONT_SCALE)
                ? Math.max(45, Math.min(160,
                get(context).getInt(KEY_TOP_LYRIC_NEXT_FONT_SCALE, 100)))
                : nextLyricScale(context, false);
    }

    static void setTopLyricNextFontScale(Context context, int value) {
        setTopLyricInt(context, KEY_TOP_LYRIC_NEXT_FONT_SCALE, value);
    }

    static void setTopLyricInt(Context context, String key, int value) {
        get(context).edit().putInt(key, value).apply();
    }

    /** Which screen the position joystick moves while several of them show lyrics. */
    static int joystickSlot(Context context) {
        return get(context).getInt(KEY_JOYSTICK_SLOT, DisplaySlotRegistry.SECONDARY_SLOT);
    }

    static void setJoystickSlot(Context context, int slot) {
        get(context).edit().putInt(KEY_JOYSTICK_SLOT, slot).apply();
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

    static String lyricCachePolicy(Context context) {
        String value = get(context).getString(KEY_LYRIC_CACHE_POLICY, "capacity");
        return "forever".equals(value) || "capacity".equals(value) ? value : "30d";
    }

    static int lyricCacheLimitMb(Context context) {
        return Math.max(16, Math.min(512, get(context).getInt(KEY_LYRIC_CACHE_LIMIT_MB, 128)));
    }

    private static int defaultPanelWidthDp(String style) {
        if ("refined".equals(style)) return 560;
        if ("compact".equals(style)) return 320;
        if ("amll".equals(style)) return 620;
        if ("pip".equals(style)) return 440;
        if ("custom".equals(style)) return 460;
        // 灵动岛：一颗胶囊，横向偏长、竖向很矮（issue #54）。
        if ("island".equals(style)) return 420;
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
        // 灵动岛：胶囊高度（issue #54），可以上下留白，后面按面板高度自适应当胶囊高。
        if ("island".equals(style)) return 72;
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
