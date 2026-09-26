package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 灵动岛（胶囊）样式的几何（issue #54）。 */
public class IslandLayoutMathTest {
    @Test public void capsuleHeightHonoursTheFloorAndTheCap() {
        // 面板比 40dp 还矮：就用面板高度（不放不下也要能用）
        assertEquals(30f, IslandLayoutMath.capsuleHeightPx(30f, 1f), 0.0001f);
        // 40–72dp 之间：跟随面板高度
        assertEquals(56f, IslandLayoutMath.capsuleHeightPx(56f, 1f), 0.0001f);
        // 面板很高：封顶 72dp，不然就不像「岛」了
        assertEquals(IslandLayoutMath.MAX_HEIGHT_DP,
                IslandLayoutMath.capsuleHeightPx(400f, 1f), 0.0001f);
        // 密度跟着缩放
        assertEquals(IslandLayoutMath.MAX_HEIGHT_DP * 2f,
                IslandLayoutMath.capsuleHeightPx(1_000f, 2f), 0.0001f);
    }

    @Test public void radiusIsHalfTheHeightSoItIsReallyACapsule() {
        assertEquals(28f, IslandLayoutMath.capsuleRadiusPx(56f), 0.0001f);
        assertEquals(0f, IslandLayoutMath.capsuleRadiusPx(0f), 0.0001f);
    }

    @Test public void coverAndGapScaleWithTheCapsule() {
        float height = 60f;
        assertEquals(height * IslandLayoutMath.COVER_RATIO_OF_HEIGHT,
                IslandLayoutMath.coverSizePx(height), 0.0001f);
        assertTrue(IslandLayoutMath.coverSizePx(height) < height);
        assertEquals(height * IslandLayoutMath.COVER_GAP_RATIO,
                IslandLayoutMath.coverGapPx(height), 0.0001f);
        assertEquals(height * IslandLayoutMath.SIDE_PADDING_RATIO,
                IslandLayoutMath.sidePaddingPx(height), 0.0001f);
    }

    @Test public void textWidthSubtractsCoverAndPadding() {
        float height = 60f;
        float width = 420f;
        float expected = width - IslandLayoutMath.sidePaddingPx(height) * 2f
                - IslandLayoutMath.coverSizePx(height) - IslandLayoutMath.coverGapPx(height);
        assertEquals(expected, IslandLayoutMath.textWidthPx(width, height), 0.0001f);
        // 极窄面板也不会算出 0 或负数
        assertTrue(IslandLayoutMath.textWidthPx(40f, 72f) >= 1f);
    }

    @Test public void titleRowOnlyAppearsOnATallEnoughCapsule() {
        float density = 1f;
        // 矮胶囊（< 56dp）只显示歌词
        assertFalse(IslandLayoutMath.showsTitleRow(48f, 300f, density));
        // 够高但文字区太窄也放不下两行
        assertFalse(IslandLayoutMath.showsTitleRow(72f, 100f, density));
        // 够高 + 够宽：分两行
        assertTrue(IslandLayoutMath.showsTitleRow(72f, 320f, density));
    }

    @Test public void titleStaysSmallerThanTheLyric() {
        float height = 72f;
        float lyric = IslandLayoutMath.lyricSizePx(height);
        assertEquals(height * 0.34f, lyric, 0.0001f);
        assertTrue(IslandLayoutMath.titleSizePx(height, lyric) <= lyric);
    }
}
