package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class SettingsCatalogTest {
    private static final class Values implements SettingsCatalog.Values {
        final Map<String, String> entries = new HashMap<>();
        @Override public boolean enabled(String key) { return "true".equals(entries.get(key)); }
        @Override public String choice(String key) { return entries.get(key); }
    }

    @Test public void searchAcceptsUserVocabularyAndMultipleTerms() {
        assertTrue(SettingsCatalog.matches("背景不透明度", "常用调整", "透明度"));
        assertTrue(SettingsCatalog.matches("长句显示方式（换行）", "文字与排版", "回车"));
        assertTrue(SettingsCatalog.matches("字号", "常用调整", "字体大小"));
        assertTrue(SettingsCatalog.matches("封面背景柔化", "背景与封面", "封面 背景"));
        assertTrue(SettingsCatalog.matches("Folia 帧率", "动效", "FOLIA"));
        assertFalse(SettingsCatalog.matches("背景不透明度", "常用调整", "封面 透明度"));
    }

    @Test public void scopeLabelsSeparateGlobalScreenAndStyleSettings() {
        assertEquals("所有屏幕", SettingsCatalog.scope(AppPreferences.KEY_PLAYBACK_FADE));
        assertEquals("所有屏幕", SettingsCatalog.scope(AppPreferences.KEY_LYRIC_CACHE_LIMIT_MB));
        assertEquals("本屏 · 所有样式", SettingsCatalog.scope(AppPreferences.KEY_LYRIC_OFFSET));
        assertEquals("本屏 · 当前样式", SettingsCatalog.scope(AppPreferences.KEY_TEXT_SCALE));
        assertEquals("本屏 · 紧凑样式", SettingsCatalog.scope(AppPreferences.KEY_COMPACT_PANEL_WIDTH_DP));
        assertEquals("当前歌曲 · 所有屏幕", SettingsCatalog.scope(SettingsCatalog.TRACK_OFFSET));
    }

    @Test public void customRowCountIsOnlyOfferedToRenderersThatUseIt() {
        assertEquals("", SettingsCatalog.unsupportedReason(AppPreferences.KEY_STYLE_LYRIC_LINES, "default"));
        assertEquals("", SettingsCatalog.unsupportedReason(AppPreferences.KEY_STYLE_LYRIC_LINES, "pure"));
        for (String style : new String[]{"compact", "refined", "amll", "pip", "island", "custom", "folia"}) {
            assertFalse(style, SettingsCatalog.unsupportedReason(AppPreferences.KEY_STYLE_LYRIC_LINES, style).isEmpty());
        }
    }

    @Test public void previousLineScaleAndOpacityFollowSeparateRendererCapabilities() {
        assertFalse(SettingsCatalog.unsupportedReason(AppPreferences.KEY_PREVIOUS_LYRIC_SCALE, "pure").isEmpty());
        assertEquals("", SettingsCatalog.unsupportedReason(AppPreferences.KEY_PREVIOUS_LYRIC_OPACITY, "pure"));
        assertEquals("", SettingsCatalog.unsupportedReason(AppPreferences.KEY_PREVIOUS_LYRIC_OPACITY, "amll"));
        assertFalse(SettingsCatalog.unsupportedReason(AppPreferences.KEY_PREVIOUS_LYRIC_OPACITY, "compact").isEmpty());
    }

    @Test public void changingBackgroundRevealsOnlyItsOwnParameters() {
        Values values = new Values();
        values.entries.put(AppPreferences.KEY_REFINED_BACKGROUND_TYPE, "solid");
        assertFalse(SettingsCatalog.unavailableReason(AppPreferences.KEY_STARFIELD_FPS, "compact", values).isEmpty());
        assertFalse(SettingsCatalog.unavailableReason(AppPreferences.KEY_COVER_FADE_LENGTH, "compact", values).isEmpty());
        assertFalse(SettingsCatalog.unavailableReason(AppPreferences.KEY_STYLE_BLUR, "compact", values).isEmpty());
        values.entries.put(AppPreferences.KEY_REFINED_BACKGROUND_TYPE, "starfield");
        assertEquals("", SettingsCatalog.unavailableReason(AppPreferences.KEY_STARFIELD_FPS, "compact", values));
        values.entries.put(AppPreferences.KEY_REFINED_BACKGROUND_TYPE, "cover_fade");
        assertEquals("", SettingsCatalog.unavailableReason(AppPreferences.KEY_COVER_FADE_LENGTH, "compact", values));
        assertFalse(SettingsCatalog.unavailableReason(AppPreferences.KEY_COVER_FADE_LENGTH, "island", values).isEmpty());
    }

    @Test public void disabledFeaturesHideParametersWithoutAlteringValues() {
        Values values = new Values();
        String key = AppPreferences.KEY_LIVE_LYRIC_ANIMATION_DURATION;
        assertFalse(SettingsCatalog.unavailableReason(key, "compact", values).isEmpty());
        values.entries.put(AppPreferences.KEY_LIVE_LYRIC_ANIMATION, "true");
        assertEquals("", SettingsCatalog.unavailableReason(key, "compact", values));
        values.entries.put(AppPreferences.KEY_MATCHING_ANIMATION, "true");
        assertEquals("", SettingsCatalog.unavailableReason(AppPreferences.KEY_MATCHING_ANIMATION_DELAY, "compact", values));
        values.entries.put(AppPreferences.KEY_HIDE_MATCHING_PLACEHOLDER, "true");
        assertFalse(SettingsCatalog.unavailableReason(AppPreferences.KEY_MATCHING_ANIMATION_DELAY, "compact", values).isEmpty());
        assertEquals(3, values.entries.size());
    }

    @Test public void cacheLimitFollowsTheActualEvictionPolicy() {
        Values values = new Values();
        for (String policy : new String[]{"30d", "forever", "capacity"}) {
            values.entries.put(AppPreferences.KEY_LYRIC_CACHE_POLICY, policy);
            assertEquals("capacity".equals(policy), SettingsCatalog.unavailableReason(
                    AppPreferences.KEY_LYRIC_CACHE_LIMIT_MB, "pure", values).isEmpty());
        }
    }
}
