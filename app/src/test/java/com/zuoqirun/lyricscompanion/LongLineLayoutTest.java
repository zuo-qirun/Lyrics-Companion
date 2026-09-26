package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 长句显示方式（issue #47）：跑马灯默认，另加缩小字号与换行。 */
public class LongLineLayoutTest {
    @Test public void defaultAndUnknownModesFallBackToTheMarquee() {
        assertEquals(LongLineLayout.MODE_MARQUEE,
                LongLineLayout.normalizeMode(LongLineLayout.MODE_MARQUEE));
        assertEquals(LongLineLayout.MODE_MARQUEE, LongLineLayout.normalizeMode("whatever"));
        assertEquals(LongLineLayout.MODE_MARQUEE, LongLineLayout.normalizeMode(null));
        assertEquals(LongLineLayout.MODE_MARQUEE, LongLineLayout.normalizeMode(""));
        assertEquals(LongLineLayout.MODE_SHRINK, LongLineLayout.normalizeMode("shrink"));
        assertEquals(LongLineLayout.MODE_WRAP, LongLineLayout.normalizeMode("wrap"));
    }

    @Test public void topStripAlwaysKeepsTheMarquee() {
        // 顶部歌词条是固定高度的双行条，换行 / 缩小都会把它撑坏（issue #47 的建议）
        assertEquals(LongLineLayout.MODE_MARQUEE,
                LongLineLayout.resolveMode(LongLineLayout.MODE_WRAP, true));
        assertEquals(LongLineLayout.MODE_MARQUEE,
                LongLineLayout.resolveMode(LongLineLayout.MODE_SHRINK, true));
        assertEquals(LongLineLayout.MODE_WRAP,
                LongLineLayout.resolveMode(LongLineLayout.MODE_WRAP, false));
    }

    @Test public void shrinkNeverGoesBelowTheFloorAndNeverEnlarges() {
        // 宽度够：不放大
        assertEquals(30f, LongLineLayout.shrinkSize(30f, 100f, 200f), 0.0001f);
        // 超出一点：按比例缩（200 → 100 需要 50%，但在 62% 下限内，所以收到 62%）
        assertEquals(30f * LongLineLayout.SHRINK_FLOOR_RATIO,
                LongLineLayout.shrinkSize(30f, 200f, 100f), 0.0001f);
        // 150 → 100 只需 67%，高于下限，按比例走
        assertEquals(30f * (100f / 150f), LongLineLayout.shrinkSize(30f, 150f, 100f), 0.0001f);
        // 超出很多：还是收在 62% 下限（再小就交给换行档）
        assertEquals(30f * LongLineLayout.SHRINK_FLOOR_RATIO,
                LongLineLayout.shrinkSize(30f, 1_000f, 100f), 0.0001f);
        // 退化输入不炸
        assertEquals(30f, LongLineLayout.shrinkSize(30f, 100f, 0f), 0.0001f);
        assertEquals(0f, LongLineLayout.shrinkSize(0f, 100f, 50f), 0.0001f);
    }

    @Test public void wrapLineCountFollowsTheAvailableHeight() {
        // 行高 30：可用 100 只能排 3 行（上限 3），可用 65 排 2 行，可用 20 至少 1 行
        assertEquals(3, LongLineLayout.wrapMaxLines(100f, 30f, 3));
        assertEquals(2, LongLineLayout.wrapMaxLines(65f, 30f, 3));
        assertEquals(1, LongLineLayout.wrapMaxLines(20f, 30f, 3));
        // 上限被调用方或常量收口
        assertEquals(2, LongLineLayout.wrapMaxLines(1_000f, 30f, 2));
        assertEquals(LongLineLayout.MAX_WRAP_LINES,
                LongLineLayout.wrapMaxLines(1_000f, 10f, 99));
        // 没有可用高度信息时按单行处理
        assertEquals(1, LongLineLayout.wrapMaxLines(0f, 30f, 3));
        assertEquals(1, LongLineLayout.wrapMaxLines(200f, 0f, 3));
    }

    @Test public void wrapIsBoundedSoItCannotEatTheWholePanel() {
        assertTrue(LongLineLayout.MAX_WRAP_LINES <= 3);
    }
}
