package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 「间奏时保留上一句」的纯规则（issue #147）。 */
public final class LyricPlaceholderHoldTest {
    @Test public void defaultOffKeepsTheInterludePlaceholder() {
        assertFalse(LyricPlaceholderRules.holdPreviousLine(false, true, true, true, "上一句"));
    }

    @Test public void onlyARealInterludeWithAPreviousLineIsHeld() {
        assertTrue(LyricPlaceholderRules.holdPreviousLine(true, true, true, true, "上一句"));
        // 不是间奏（包括等待播放、正在匹配）时不改变原有占位行为。
        assertFalse(LyricPlaceholderRules.holdPreviousLine(true, false, true, true, "上一句"));
        // 歌词还没加载成功 / 不可用时保留现有占位文字，不画上一句。
        assertFalse(LyricPlaceholderRules.holdPreviousLine(true, true, false, false, "上一句"));
        assertFalse(LyricPlaceholderRules.holdPreviousLine(true, true, true, false, "上一句"));
        // 歌曲开头的间奏没有上一句可保留。
        assertFalse(LyricPlaceholderRules.holdPreviousLine(true, true, true, true, ""));
        assertFalse(LyricPlaceholderRules.holdPreviousLine(true, true, true, true, "   "));
        assertFalse(LyricPlaceholderRules.holdPreviousLine(true, true, true, true, null));
    }

    @Test public void heldLineIsDimmedButStillVisible() {
        // 默认 55% 透明度：比正常歌词暗一档，但不会看不见。
        assertEquals(140, LyricPlaceholderRules.heldLineAlpha(255));
        assertEquals(0, LyricPlaceholderRules.heldLineAlpha(0));
        assertEquals(0, LyricPlaceholderRules.heldLineAlpha(-20));
        assertEquals(255, LyricPlaceholderRules.heldLineAlpha(600));
        // 当前句描边/高亮不受影响，这里只压 alpha。
        assertTrue(LyricPlaceholderRules.heldLineAlpha(200) < 200);
        assertTrue(LyricPlaceholderRules.heldLineAlpha(200) > 0);
    }
}
