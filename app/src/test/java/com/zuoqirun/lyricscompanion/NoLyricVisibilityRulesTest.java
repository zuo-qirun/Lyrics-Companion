package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 「无歌词多久后隐藏」的宽限计时（issue #67）。 */
public class NoLyricVisibilityRulesTest {
    @Test public void absentLyricsStartTheClockOnce() {
        assertEquals(500L, NoLyricVisibilityRules.nextUnavailableSince(
                false, "a", "", 500L, NoLyricVisibilityRules.NO_CLOCK));
        // 同一首歌后续每 500ms 的轮询不会重置计时器
        assertEquals(500L, NoLyricVisibilityRules.nextUnavailableSince(
                false, "a", "a", 9_000L, 500L));
    }

    @Test public void availableLyricsClearTheClock() {
        assertEquals(NoLyricVisibilityRules.NO_CLOCK, NoLyricVisibilityRules.nextUnavailableSince(
                true, "a", "a", 9_000L, 500L));
    }

    @Test public void newTrackRestartsTheGracePeriod() {
        assertEquals(9_000L, NoLyricVisibilityRules.nextUnavailableSince(
                false, "b", "a", 9_000L, 500L));
    }

    @Test public void graceElapsedBoundaries() {
        assertTrue(NoLyricVisibilityRules.graceElapsed(500L, 500L, 0));
        assertFalse(NoLyricVisibilityRules.graceElapsed(7_999L, 0L, 8_000));
        assertTrue(NoLyricVisibilityRules.graceElapsed(8_000L, 0L, 8_000));
        // 计时器还没开始（本轮或上一轮有歌词）时永不隐藏
        assertFalse(NoLyricVisibilityRules.graceElapsed(9_000L,
                NoLyricVisibilityRules.NO_CLOCK, 8_000));
    }

    @Test public void graceIsClampedToSomethingUsable() {
        assertEquals(0, NoLyricVisibilityRules.normalizeGraceMs(-5));
        assertEquals(8_000, NoLyricVisibilityRules.normalizeGraceMs(8_000));
        assertEquals(NoLyricVisibilityRules.MAX_GRACE_MS,
                NoLyricVisibilityRules.normalizeGraceMs(999_999));
        assertEquals(NoLyricVisibilityRules.DEFAULT_GRACE_MS, 8_000);
    }
}
