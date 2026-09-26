package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 「共用显示参数」按 屏 × 样式 两级键存放（issue #39）。 */
public class DisplayStyleScopeTest {
    @Test public void aScopedKeyKeepsTheScreenAndTheStyle() {
        assertEquals("lyric_color_main_default",
                AppPreferences.styleScopedKey("lyric_color", false, "default"));
        assertEquals("lyric_color_secondary_refined",
                AppPreferences.styleScopedKey("lyric_color", true, "refined"));
        // 头部歌词条等"没有样式"的调用点按默认样式算，避免出现空后缀的野键。
        assertEquals("opacity_main_default",
                AppPreferences.styleScopedKey("opacity", false, ""));
        assertEquals("opacity_main_default",
                AppPreferences.styleScopedKey("opacity", false, null));
    }

    @Test public void appearanceParametersAreScopedPerStyle() {
        // issue #39 点名要拆开的那几类：字号、颜色与描边、不透明度、行数、粒子/逐字、上下句。
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_TEXT_SCALE));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_TITLE_SCALE));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_NEXT_LYRIC_SCALE));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_PREVIOUS_LYRIC_SCALE));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_LYRIC_COLOR));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_CURRENT_LYRIC_COLOR));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_INACTIVE_LYRIC_COLOR));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_LYRIC_OUTLINE_COLOR));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_OPACITY));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_STYLE_LYRIC_LINES));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_PARTICLE_AMOUNT));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_WORD_DISSOLVE));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_PREVIOUS_LYRIC_OPACITY));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_NEXT_LYRIC_OPACITY));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_LYRIC_ALIGN));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_CORNER_RADIUS_PERCENT));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_ROUND_COVER));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_COVER_ROTATION));
        // 「文字效果」现在是通用外观项（issue #59）：按屏 × 样式各存一份，经典/纯净也吃这个设置。
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_REFINED_TEXT_EFFECT));
    }

    @Test public void behaviourSettingsStayPerScreenOnly() {
        // 这些跟样式无关：按样式各存一份会让用户改了一处、另一处不生效。
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_OVERLAY_STYLE));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_MAIN_OVERLAY_STYLE));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_SECONDARY_OVERLAY_STYLE));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_OVERLAY_POSITION_LOCKED));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_MAIN_OVERLAY_TOUCH_THROUGH));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_LYRIC_OFFSET));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_HIDE_OVERLAYS_IN_APPS));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_LYRIC_CACHE_POLICY));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_MAIN_X));
        assertFalse(AppPreferences.isStyleScopedKey(null));
    }

    @Test public void everyKnownStyleGetsItsOwnSuffix() {
        for (String style : AppPreferences.OVERLAY_STYLES) {
            String key = AppPreferences.styleScopedKey(AppPreferences.KEY_TEXT_SCALE, false, style);
            assertEquals("text_scale_main_" + style, key);
        }
        // 经典 / Refined / AMLL / 紧凑 / 极简 / 纯净 / 自定义 / 灵动岛（issue #54）
        assertEquals(8, AppPreferences.OVERLAY_STYLES.length);
    }
}
