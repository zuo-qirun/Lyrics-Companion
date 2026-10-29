package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * issue #165：紧凑样式双行模式下，最后一句没有下一句时当前句字号不能从 78% 跳回 100%，
 * 第二行行位要保留（留空），基线 / 垂直居中不能重新计算。
 */
public class CompactRowMetricsTest {
    // 相当于某个面板里 78% 当前句与 70% 下一句的 Paint ascent / descent 量级。
    private static final float CURRENT_ASCENT = -34.5f;
    private static final float CURRENT_DESCENT = 9.2f;
    private static final float SECOND_ASCENT = -24.2f;
    private static final float SECOND_DESCENT = 6.5f;
    private static final float LINE_GAP = 9.9f;
    private static final float LYRIC_TOP = 12f;
    private static final float STAGE_HEIGHT = 96f;
    private static final float REQUESTED_SIZE = 44f;
    private static final float EPSILON = 0.0001f;

    @Test public void pinnedSecondRowKeepsSizeAndPositionWithoutNextLine() {
        boolean pinned = CompactRowMetrics.fixedSecondRow(false, true, false);
        boolean withNextLine = CompactRowMetrics.secondRow(pinned, false, true);
        boolean withoutNextLine = CompactRowMetrics.secondRow(pinned, false, false);
        assertTrue(withNextLine);
        assertTrue(withoutNextLine);

        // 字号因子在有无下一句时一致，且固定为 78%。
        assertEquals(CompactRowMetrics.DOUBLE_ROW_SIZE_FACTOR,
                CompactRowMetrics.lyricSizeFactor(withNextLine), EPSILON);
        assertEquals(CompactRowMetrics.lyricSizeFactor(withNextLine),
                CompactRowMetrics.lyricSizeFactor(withoutNextLine), EPSILON);
        assertEquals(REQUESTED_SIZE * CompactRowMetrics.DOUBLE_ROW_SIZE_FACTOR,
                REQUESTED_SIZE * CompactRowMetrics.lyricSizeFactor(withoutNextLine), EPSILON);

        // 组高、垂直居中、两条基线都不随有无下一句变化。
        float heightWith = groupHeight(withNextLine);
        float heightWithout = groupHeight(withoutNextLine);
        assertEquals(heightWith, heightWithout, EPSILON);
        float topWith = CompactRowMetrics.groupTop(LYRIC_TOP, STAGE_HEIGHT, heightWith);
        float topWithout = CompactRowMetrics.groupTop(LYRIC_TOP, STAGE_HEIGHT, heightWithout);
        assertEquals(topWith, topWithout, EPSILON);
        assertEquals(CompactRowMetrics.baseline(topWith, CURRENT_ASCENT),
                CompactRowMetrics.baseline(topWithout, CURRENT_ASCENT), EPSILON);
        assertEquals(secondaryBaseline(topWith, heightWith),
                secondaryBaseline(topWithout, heightWithout), EPSILON);
    }

    @Test public void pinnedSecondRowWithoutNextLineStillReservesTwoRowHeight() {
        boolean withoutNextLine = CompactRowMetrics.secondRow(
                CompactRowMetrics.fixedSecondRow(false, true, false), false, false);
        float singleRowHeight = CURRENT_DESCENT - CURRENT_ASCENT;
        float twoRowHeight = singleRowHeight + LINE_GAP + SECOND_DESCENT - SECOND_ASCENT;
        // 空行位按一整行算：与真的画了下一句时一样高，所以居中不会跳。
        assertEquals(twoRowHeight, groupHeight(withoutNextLine), EPSILON);
        assertTrue(groupHeight(withoutNextLine) > singleRowHeight);
        // 第二行基线在空白行位上仍然存在，且位于当前句下方。
        float groupTop = CompactRowMetrics.groupTop(LYRIC_TOP, STAGE_HEIGHT,
                groupHeight(withoutNextLine));
        float baseline = CompactRowMetrics.baseline(groupTop, CURRENT_ASCENT);
        float secondBaseline = CompactRowMetrics.secondaryBaseline(
                baseline, CURRENT_DESCENT, LINE_GAP, SECOND_ASCENT);
        assertEquals(baseline + CURRENT_DESCENT + LINE_GAP - SECOND_ASCENT,
                secondBaseline, EPSILON);
        assertTrue(secondBaseline > baseline);
    }

    @Test public void singleRowModeKeepsFullSizeAndSingleRowHeight() {
        // 「显示下一句（双行）」关闭：保持原来的单行 100% 行为。
        boolean pinned = CompactRowMetrics.fixedSecondRow(false, false, false);
        boolean secondRow = CompactRowMetrics.secondRow(pinned, false, false);
        assertFalse(pinned);
        assertFalse(secondRow);
        assertEquals(1f, CompactRowMetrics.lyricSizeFactor(secondRow), EPSILON);
        assertEquals(REQUESTED_SIZE,
                REQUESTED_SIZE * CompactRowMetrics.lyricSizeFactor(secondRow), EPSILON);
        float singleRowHeight = CURRENT_DESCENT - CURRENT_ASCENT;
        assertEquals(singleRowHeight, groupHeight(secondRow), EPSILON);
        assertEquals(LYRIC_TOP + (STAGE_HEIGHT - singleRowHeight) * 0.5f,
                CompactRowMetrics.groupTop(LYRIC_TOP, STAGE_HEIGHT, singleRowHeight), EPSILON);
    }

    @Test public void translationKeepsTheExistingSecondRowBehaviour() {
        // 翻译行仍占第二行，字号仍是 78%，不因为行位固定再缩小一次。
        boolean secondRow = CompactRowMetrics.secondRow(
                CompactRowMetrics.fixedSecondRow(false, false, false), true, false);
        assertTrue(secondRow);
        assertEquals(CompactRowMetrics.DOUBLE_ROW_SIZE_FACTOR,
                CompactRowMetrics.lyricSizeFactor(secondRow), EPSILON);
        float twoRowHeight = CURRENT_DESCENT - CURRENT_ASCENT + LINE_GAP
                + SECOND_DESCENT - SECOND_ASCENT;
        assertEquals(twoRowHeight, groupHeight(secondRow), EPSILON);
        // 双行模式打开且同时有翻译时，行位与字号都保持同一套结果。
        boolean pinnedAndTranslated = CompactRowMetrics.secondRow(
                CompactRowMetrics.fixedSecondRow(false, true, false), true, false);
        assertEquals(CompactRowMetrics.lyricSizeFactor(secondRow),
                CompactRowMetrics.lyricSizeFactor(pinnedAndTranslated), EPSILON);
    }

    @Test public void rowModeComesFromTheViewThatOwnsTheRowSetting() {
        // 顶部歌词条：只有「双行」固定占两行，「单行 / 自动」不因 compactShowNextLine 改变。
        assertTrue(CompactRowMetrics.fixedSecondRow(true, false, true));
        assertFalse(CompactRowMetrics.fixedSecondRow(true, true, false));
        // 紧凑样式悬浮窗：看自己的「显示下一句」开关，不受顶部条行数影响。
        assertTrue(CompactRowMetrics.fixedSecondRow(false, true, false));
        assertFalse(CompactRowMetrics.fixedSecondRow(false, false, true));
    }

    @Test public void centeringSticksToTheTopWhenBothRowsDoNotFit() {
        // 面板太矮时整组贴顶，不能出现负偏移把当前句画到歌词区外面。
        float tallGroup = STAGE_HEIGHT + 20f;
        assertEquals(LYRIC_TOP,
                CompactRowMetrics.groupTop(LYRIC_TOP, STAGE_HEIGHT, tallGroup), EPSILON);
    }

    private static float groupHeight(boolean secondRow) {
        return CompactRowMetrics.groupHeight(secondRow, CURRENT_ASCENT, CURRENT_DESCENT,
                LINE_GAP, SECOND_ASCENT, SECOND_DESCENT);
    }

    private static float secondaryBaseline(float groupTop, float groupHeight) {
        float baseline = CompactRowMetrics.baseline(groupTop, CURRENT_ASCENT);
        return CompactRowMetrics.secondaryBaseline(baseline, CURRENT_DESCENT, LINE_GAP,
                SECOND_ASCENT);
    }
}
