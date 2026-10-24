package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PureLyricLayoutTest {
    @Test public void windowKeepsTheCurrentLineOnTheSameRowThroughoutTheSong() {
        // 首句、中间、末句：本句始终落在窗口的第 (count-1)/2 个行位上，缺的行位留空（issue #31）。
        assertEquals(-3, PureLyricLayout.windowStart(0, 7));
        assertEquals(7, PureLyricLayout.windowStart(10, 7));
        assertEquals(16, PureLyricLayout.windowStart(19, 7));
        assertEquals(0, PureLyricLayout.windowStart(0, 1));
        assertEquals(-1, PureLyricLayout.windowStart(0, 3));
        assertEquals(1, PureLyricLayout.windowStart(2, 3));
    }

    @Test public void moreLinesReduceCurrentSizeToAvailableHeight() {
        float three = PureLyricLayout.constrainedCurrentSize(
                40f, 180f, 3, 0, false, 0.7f);
        float seven = PureLyricLayout.constrainedCurrentSize(
                40f, 180f, 7, 0, false, 0.7f);
        assertTrue(seven < three);
        assertTrue(seven > 0f);
    }

    @Test public void translationsReserveHeightAndIgnoreDuplicateText() {
        float originalOnly = PureLyricLayout.constrainedCurrentSize(
                40f, 180f, 3, 0, false, 0.7f);
        float translated = PureLyricLayout.constrainedCurrentSize(
                40f, 180f, 3, 3, true, 0.7f);
        assertTrue(translated < originalOnly);
        assertTrue(PureLyricLayout.hasDistinctTranslation("Hello", "你好"));
        assertTrue(!PureLyricLayout.hasDistinctTranslation("Hello", " Hello "));
        assertTrue(!PureLyricLayout.hasDistinctTranslation("Hello", ""));
    }

    @Test public void currentLineAnchorMovesTheWindowRow() {
        // issue #155：居中仍是默认；第二行 / 顶部把窗口起点往后挪，当前句落到选定行位。
        assertEquals(7, PureLyricLayout.windowStart(10, 7, ""));
        assertEquals(9, PureLyricLayout.windowStart(10, 7, "second"));
        assertEquals(10, PureLyricLayout.windowStart(10, 7, "top"));
        // 行数不足时第二行退化成第一行，和顶部一致。
        assertEquals(0, PureLyricLayout.windowStart(0, 1, "second"));
        assertEquals(0, PureLyricLayout.windowStart(0, 1, "top"));
        assertEquals(0, PureLyricLayout.windowStart(1, 3, "second"));
        // 默认空字符串必须与旧的两参数行为完全一致（3/7 行都是原来居中的摆法）。
        assertEquals(PureLyricLayout.windowStart(7, 7), PureLyricLayout.windowStart(7, 7, ""));
        assertEquals(PureLyricLayout.windowStart(0, 3), PureLyricLayout.windowStart(0, 3, ""));
    }

    @Test public void anchorRowFallsBackWhenTheWindowHasTooFewRows() {
        assertEquals(0, PureLyricLayout.anchorRow(1, "top"));
        assertEquals(0, PureLyricLayout.anchorRow(1, "second"));
        assertEquals(0, PureLyricLayout.anchorRow(1, ""));
        assertEquals(1, PureLyricLayout.anchorRow(2, "second"));
        assertEquals(0, PureLyricLayout.anchorRow(3, "top"));
        assertEquals(1, PureLyricLayout.anchorRow(3, "second"));
        assertEquals(1, PureLyricLayout.anchorRow(3, ""));
        assertEquals(3, PureLyricLayout.anchorRow(7, ""));
    }
}
