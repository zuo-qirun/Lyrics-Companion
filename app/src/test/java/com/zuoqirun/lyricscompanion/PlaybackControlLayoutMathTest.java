package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 播放控制按钮只显示 1~2 个时的排布与底板宽度（issue #72），以及偏移范围与夹取（issue #73）。 */
public class PlaybackControlLayoutMathTest {
    @Test public void threeButtonsKeepTheOriginalSpread() {
        assertEquals(3, PlaybackControlLayoutMath.visibleCount(true, true, true));
        float spacing = 24f;
        assertEquals(-spacing, PlaybackControlLayoutMath.slotOffset(0, 3, spacing), 0.0001f);
        assertEquals(0f, PlaybackControlLayoutMath.slotOffset(1, 3, spacing), 0.0001f);
        assertEquals(spacing, PlaybackControlLayoutMath.slotOffset(2, 3, spacing), 0.0001f);
        // 底板半宽必须等于改动前写死的 spacing + radius * 1.65。
        float radius = 10f;
        assertEquals(spacing + radius * 1.65f,
                PlaybackControlLayoutMath.backdropHalfWidth(spacing + radius, radius), 0.0001f);
    }

    @Test public void twoButtonsAreCentredAndKeepOneFullSpacing() {
        assertEquals(2, PlaybackControlLayoutMath.visibleCount(true, true, false));
        float spacing = 24f;
        float left = PlaybackControlLayoutMath.slotOffset(0, 2, spacing);
        float right = PlaybackControlLayoutMath.slotOffset(1, 2, spacing);
        assertEquals(-spacing * 0.5f, left, 0.0001f);
        assertEquals(spacing * 0.5f, right, 0.0001f);
        // 两个按钮之间仍是整整一个 spacing：不会比三键时更挤。
        assertEquals(spacing, right - left, 0.0001f);
    }

    @Test public void oneButtonSitsOnTheCentre() {
        assertEquals(1, PlaybackControlLayoutMath.visibleCount(false, true, false));
        assertEquals(0f, PlaybackControlLayoutMath.slotOffset(0, 1, 24f), 0.0001f);
        assertEquals(0f, PlaybackControlLayoutMath.slotOffset(0, 0, 24f), 0.0001f);
    }

    @Test public void backdropShrinksToTheVisibleButtons() {
        float radius = 10f;
        float spacing = 24f;
        // 只有播放/暂停时：底板只需包住它自己（半径 1.12r）
        float playPauseOnly = PlaybackControlLayoutMath.backdropHalfWidth(radius * 1.12f, radius);
        assertEquals(radius * 1.12f + radius * 0.65f, playPauseOnly, 0.0001f);
        // 上一首 + 播放/暂停：半宽跟着最外侧那个（播放/暂停在 +0.5 spacing 处）走
        float maxExtent = spacing * 0.5f + radius * 1.12f;
        assertEquals(maxExtent + radius * 0.65f,
                PlaybackControlLayoutMath.backdropHalfWidth(maxExtent, radius), 0.0001f);
        // 一个按钮都没有时底板宽度为 0（调用方据此跳过绘制）
        assertEquals(0f, PlaybackControlLayoutMath.backdropHalfWidth(0f, 0f), 0.0001f);
    }

    /** issue #73：偏移范围放到 ±100% 之后，面板两端要真的摆得到。 */
    @Test public void fullOffsetReachesBothPanelEdges() {
        float width = 1000f;
        float inset = 30f;
        // 紧凑样式的基准是 0.38w：拉到 -100% 会越过左边缘，由夹取拉回 inset 处
        assertEquals(inset, PlaybackControlLayoutMath.clampCenter(
                PlaybackControlLayoutMath.resolvedCenter(width * 0.38f, width, -100f),
                width, inset), 0.0001f);
        assertEquals(width - inset, PlaybackControlLayoutMath.clampCenter(
                PlaybackControlLayoutMath.resolvedCenter(width * 0.38f, width, 100f),
                width, inset), 0.0001f);
        // AMLL 的基准是 0.225w，同样要能到两边
        assertEquals(inset, PlaybackControlLayoutMath.clampCenter(
                PlaybackControlLayoutMath.resolvedCenter(width * 0.225f, width, -100f),
                width, inset), 0.0001f);
        assertEquals(width - inset, PlaybackControlLayoutMath.clampCenter(
                PlaybackControlLayoutMath.resolvedCenter(width * 0.225f, width, 100f),
                width, inset), 0.0001f);
    }

    @Test public void zeroOffsetKeepsTheStyleBasisAndLegacyValues() {
        assertEquals(380f, PlaybackControlLayoutMath.resolvedCenter(380f, 1000f, 0f), 0.0001f);
        assertEquals(500f, PlaybackControlLayoutMath.clampCenter(500f, 1000f, 30f), 0.0001f);
        // 面板比按钮组还窄时居中，而不是把按钮钉到左缘
        float tiny = PlaybackControlLayoutMath.clampCenter(500f, 40f, 30f);
        assertTrue(tiny >= 0f && tiny <= 40f);
    }

    @Test public void offsetIsPanelRelative() {
        assertEquals(900f, PlaybackControlLayoutMath.resolvedCenter(400f, 1000f, 50f), 0.0001f);
    }
}
