package com.zuoqirun.lyricscompanion;

import java.util.Locale;

/** Shared rules for navigation, search, scope labels and selective appearance copying. */
final class SettingsCatalog {
    static final String TRACK_OFFSET = "current_track_offset";
    enum Category {
        COMMON("常用调整"), TEXT("文字与排版"), BACKGROUND("背景与封面"),
        COLORS("颜色与描边"), EFFECTS("动效与频谱"), INTERACTION("位置与交互"),
        MAINTENANCE("歌词校正与维护"), COLOR_LYRICS("歌词配色"),
        COLOR_OUTLINE("文字描边"), COLOR_THEME("日夜配色"),
        COLOR_METADATA("歌名与状态配色"), COLOR_BACKGROUND("背景与频谱配色");

        final String title;
        Category(String title) { this.title = title; }
    }

    interface Values {
        boolean enabled(String key);
        String choice(String key);
    }

    private SettingsCatalog() {}

    static String scope(String key) {
        if (AppPreferences.KEY_COMPACT_PANEL_WIDTH_DP.equals(key)
                || AppPreferences.KEY_COMPACT_PANEL_HEIGHT_DP.equals(key)
                || AppPreferences.KEY_COMPACT_SHOW_NEXT_LINE.equals(key)
                || AppPreferences.KEY_COMPACT_SHOW_COVER.equals(key)) return "本屏 · 紧凑样式";
        if (AppPreferences.KEY_AMLL_PANEL_WIDTH_DP.equals(key)
                || AppPreferences.KEY_AMLL_PANEL_HEIGHT_DP.equals(key)) return "本屏 · AMLL 样式";
        if (AppPreferences.KEY_PANEL_WIDTH_DP.equals(key)
                || AppPreferences.KEY_PANEL_HEIGHT_DP.equals(key)) return "本屏 · 共用窗口尺寸";
        if (TRACK_OFFSET.equals(key)) return "当前歌曲 · 所有屏幕";
        if (AppPreferences.KEY_LYRICS_FOLLOW_THEME.equals(key)) return "所有屏幕";
        if (key.startsWith("status_lyric_")) return "顶部歌词条";
        if (key.startsWith("lyric_cache") || AppPreferences.KEY_PLAYBACK_FADE.equals(key)
                || AppPreferences.KEY_PLAYBACK_FADE_SECONDS.equals(key)
                || AppPreferences.KEY_PLAYBACK_CONTROL_SCALE.equals(key)
                || AppPreferences.KEY_FULLSCREEN_CLOSE_MODE.equals(key)
                || AppPreferences.KEY_OVERLAY_CLOSE_MODE.equals(key)) return "所有屏幕";
        return AppPreferences.isStyleScopedKey(key) ? "本屏 · 当前样式" : "本屏 · 所有样式";
    }

    /** Empty means the setting can currently take effect. Never changes the stored value. */
    static String unavailableReason(String key, String style, Values values) {
        String unsupported = unsupportedReason(key, style);
        if (!unsupported.isEmpty()) return unsupported;
        return dependencyReason(key, style, values);
    }

    static String unsupportedReason(String key, String style) {
        if (key.startsWith("folia_") && !StyleSettingCapabilities.supportsFoliaParameter(style, key)) {
            return "仅 folia 样式支持";
        }
        if (key.startsWith("island_") && !"island".equals(style)) return "仅灵动岛样式支持";
        if (key.startsWith("pure_show_") && !"pure".equals(style)) return "仅纯净样式支持";
        if (key.startsWith("classic_") && !"default".equals(style)) return "仅经典样式支持";
        if (AppPreferences.KEY_STYLE_LYRIC_LINES.equals(key)
                && !oneOf(style, "default", "pure")) return "仅经典、纯净样式支持自定义行数";
        if (AppPreferences.KEY_PREVIOUS_LYRIC_SCALE.equals(key)
                && !"default".equals(style)) return "仅经典样式支持独立上一句字号";
        if (AppPreferences.KEY_PREVIOUS_LYRIC_OPACITY.equals(key)
                && oneOf(style, "compact", "island")) return "当前样式没有上一句歌词行";
        if (AppPreferences.KEY_CONTENT_ALIGN.equals(key)
                && !oneOf(style, "default", "pure")) return "仅经典、纯净样式支持垂直对齐";
        // issue #155：当前句行位只对经典（整套行位）与纯净（行位窗口）样式有意义。
        if (AppPreferences.KEY_LYRIC_ANCHOR.equals(key)
                && !oneOf(style, "default", "pure")) return "仅经典、纯净样式支持当前句行位";
        // issue #138：行间距先覆盖经典、紧凑、Refined 三种会排多行的样式。
        if (AppPreferences.KEY_LYRIC_LINE_SPACING.equals(key)
                && !oneOf(style, "default", "compact", "refined")) {
            return "仅经典、紧凑、Refined 样式支持行间距";
        }
        // issue #147：folia 与自定义样式没有「♪ · · ·」间奏占位，不做保留上一句。
        if (AppPreferences.KEY_HOLD_PREVIOUS_LYRIC.equals(key)
                && (FoliaModeCatalog.isStyle(style) || "custom".equals(style))) {
            return "当前样式没有可保留的间奏占位";
        }
        // issue #134：封面与歌词上下排列只给紧凑 / AMLL 两种「左图右词」样式。
        if (AppPreferences.KEY_COVER_STACKED.equals(key)
                && !oneOf(style, "compact", "amll")) {
            return "仅紧凑、AMLL 样式支持封面与歌词上下排列";
        }
        if (AppPreferences.KEY_LONG_LINE_MODE.equals(key)
                && !oneOf(style, "compact", "pure")) return "仅紧凑、纯净样式使用此长句设置";
        if (AppPreferences.KEY_ROUND_COVER.equals(key)
                && !oneOf(style, "compact", "amll")) return "仅紧凑、AMLL 样式支持此圆形封面开关";
        if (AppPreferences.KEY_REFINED_BACKGROUND_TYPE.equals(key)
                && !oneOf(style, "refined", "compact", "folia", "folia_original")) return "当前样式使用自身背景";
        if ((key.startsWith("starfield_") || key.startsWith("cover_fade_"))
                && !oneOf(style, "refined", "compact", "folia", "folia_original")) return "当前样式使用自身背景";
        return "";
    }

    private static String dependencyReason(String key, String style, Values values) {
        if ((AppPreferences.KEY_SPECTRUM_STYLE.equals(key)
                || AppPreferences.KEY_SPECTRUM_COLOR_MODE.equals(key)
                || AppPreferences.KEY_SPECTRUM_HEIGHT_PERCENT.equals(key)
                || AppPreferences.KEY_SPECTRUM_GAP_DP.equals(key))
                && !values.enabled(AppPreferences.KEY_SPECTRUM_ENABLED)) return "开启律动与频谱后可调整";
        if (key.startsWith("current_lyric_outline_")
                && !values.enabled(AppPreferences.KEY_CURRENT_LYRIC_OUTLINE)) return "开启当前歌词描边后可调整";
        if (key.startsWith("inactive_lyric_outline_")
                && !values.enabled(AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE)) return "开启非当前歌词描边后可调整";
        if (AppPreferences.KEY_ISLAND_MAIN_ROW_PERCENT.equals(key)
                && values.enabled(AppPreferences.KEY_ISLAND_AUTO_ROWS)) return "关闭双行高度自动分配后可调整";
        if (key.startsWith("starfield_") || key.startsWith("cover_fade_")) {
            if (!oneOf(style, "refined", "compact", "folia", "folia_original")) return "当前样式使用自身背景";
            String required = key.startsWith("starfield_") ? "starfield" : "cover_fade";
            if (!required.equals(values.choice(AppPreferences.KEY_REFINED_BACKGROUND_TYPE))) {
                return "选择“" + ("starfield".equals(required) ? "星空 / 星尘" : "封面渐隐") + "”背景后可调整";
            }
        }
        if (AppPreferences.KEY_LIVE_LYRIC_ANIMATION_DURATION.equals(key)
                && !values.enabled(AppPreferences.KEY_LIVE_LYRIC_ANIMATION)) return "开启实时歌词切句动画后可调整";
        if (AppPreferences.KEY_OVERLAY_OFFSCREEN_PERCENT.equals(key)
                && !values.enabled(AppPreferences.KEY_OVERLAY_ALLOW_OFFSCREEN)) return "允许面板移出屏幕边缘后可调整";
        if ((AppPreferences.KEY_PARTICLE_AMOUNT.equals(key) || AppPreferences.KEY_WORD_DISSOLVE.equals(key))
                && !values.enabled(AppPreferences.KEY_PREVIOUS_LYRIC_PARTICLES)) return "开启本句结束粒子消散后可调整";
        if (AppPreferences.KEY_COVER_ROTATION_PERIOD_SECONDS.equals(key)
                && !values.enabled(AppPreferences.KEY_COVER_ROTATION)) return "开启圆形封面碟片旋转后可调整";
        if (AppPreferences.KEY_MATCHING_ANIMATION_DELAY.equals(key)
                && (!values.enabled(AppPreferences.KEY_MATCHING_ANIMATION)
                || values.enabled(AppPreferences.KEY_HIDE_MATCHING_PLACEHOLDER))) return "显示匹配动画且未开启留白时可调整";
        if (AppPreferences.KEY_PLAYBACK_FADE_SECONDS.equals(key)
                && !values.enabled(AppPreferences.KEY_PLAYBACK_FADE)) return "开启播放按钮自动淡化后可调整";
        if (AppPreferences.KEY_LYRIC_CACHE_LIMIT_MB.equals(key)
                && !"capacity".equals(values.choice(AppPreferences.KEY_LYRIC_CACHE_POLICY))) return "选择按容量自动淘汰后可调整";
        if (AppPreferences.KEY_STYLE_BLUR.equals(key)
                && oneOf(style, "refined", "compact", "folia", "folia_original")
                && !oneOf(values.choice(AppPreferences.KEY_REFINED_BACKGROUND_TYPE), "fluid", "blur")) {
            return "选择流体或模糊背景后可调整";
        }
        if (AppPreferences.KEY_REFINED_STATIC_FLUID.equals(key)
                && !"fluid".equals(values.choice(AppPreferences.KEY_REFINED_BACKGROUND_TYPE))) return "选择流体背景后可调整";
        if (AppPreferences.KEY_REFINED_DYNAMIC_GRADIENT.equals(key)
                && !"gradient".equals(values.choice(AppPreferences.KEY_REFINED_BACKGROUND_TYPE))) return "选择渐变背景后可调整";
        if (key.startsWith("lyric_effect_")
                && "none".equals(values.choice(AppPreferences.KEY_LYRIC_EFFECT))) return "选择歌词特效后可调整";
        if ((AppPreferences.KEY_LYRIC_EFFECT_FPS.equals(key) || AppPreferences.KEY_LYRIC_EFFECT_SPEED.equals(key))
                && !LyricEffectCatalog.find(values.choice(AppPreferences.KEY_LYRIC_EFFECT)).animated) return "当前特效没有动画参数";
        if (AppPreferences.KEY_LYRIC_EFFECT_SPEED.equals(key)
                && "karaoke".equals(values.choice(AppPreferences.KEY_LYRIC_EFFECT))) return "卡拉 OK 速度跟随歌词时间轴";
        return "";
    }

    static Category copyCategory(String key) {
        if (key.contains("color") || key.contains("outline")) return Category.COLORS;
        if (key.startsWith("lyric_effect") || key.startsWith("previous_lyric_particle")
                || key.startsWith("live_lyric_animation") || key.startsWith("matching_animation")
                || key.startsWith("spectrum_") || key.startsWith("folia_")
                || AppPreferences.KEY_WORD_DISSOLVE.equals(key)
                || AppPreferences.KEY_ESTIMATED_WORD_KARAOKE.equals(key)
                || AppPreferences.KEY_TRAILING_ACCENT.equals(key)
                || AppPreferences.KEY_REFINED_TEXT_EFFECT.equals(key)) return Category.EFFECTS;
        if (key.contains("cover") || key.startsWith("style_") || key.startsWith("island_background")
                || AppPreferences.KEY_OPACITY.equals(key) || key.contains("corner_radius")
                || key.contains("shadow") || key.contains("brightness")) {
            // The line count is typography even though its historical key starts with style_.
            return AppPreferences.KEY_STYLE_LYRIC_LINES.equals(key) ? Category.TEXT : Category.BACKGROUND;
        }
        return Category.TEXT;
    }

    static boolean matches(String title, String category, String query) {
        String haystack = normalize(title + " " + category);
        for (String word : normalize(query).split("\\s+")) {
            if (!haystack.contains(word)) return false;
        }
        return true;
    }

    private static String normalize(String text) {
        return (text == null ? "" : text).toLowerCase(Locale.ROOT)
                .replace("不透明度", "透明度").replace("透明度", "透明")
                .replace("回车", "换行").replace("字体大小", "字号")
                .replace("字体尺寸", "字号").trim();
    }

    private static boolean oneOf(String value, String... options) {
        for (String option : options) if (option.equals(value)) return true;
        return false;
    }
}
