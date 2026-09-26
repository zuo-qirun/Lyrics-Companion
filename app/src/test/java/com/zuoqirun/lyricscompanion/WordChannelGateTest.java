package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 酷我逐字通道的负缓存（issue #74）：连不上就别让每首歌都先等一次超时。 */
public class WordChannelGateTest {
    @Test public void channelStartsOpenAndClosesAfterRepeatedTransportFailures() {
        assertTrue(WordChannelGate.isOpen(0L, 0L));
        assertTrue("单次抖动不该关掉通道",
                WordChannelGate.isOpen(1_000L, WordChannelGate.blockedUntil(1_000L, 1)));
        int failures = WordChannelGate.failuresAfterFailure(0);
        assertEquals(1, failures);
        long blocked = WordChannelGate.blockedUntil(1_000L, failures);
        assertEquals(0L, blocked);
        failures = WordChannelGate.failuresAfterFailure(failures);
        assertEquals(WordChannelGate.FAILURES_BEFORE_BACKOFF, failures);
        blocked = WordChannelGate.blockedUntil(1_000L, failures);
        assertEquals(1_000L + WordChannelGate.BACKOFF_MS, blocked);
        // 退避期内跳过，到期后重新可用
        assertFalse(WordChannelGate.isOpen(1_000L, blocked));
        assertFalse(WordChannelGate.isOpen(blocked - 1L, blocked));
        assertTrue(WordChannelGate.isOpen(blocked, blocked));
    }

    @Test public void remainingTimeCountsDownAndNeverGoesNegative() {
        assertEquals(0L, WordChannelGate.remainingMs(500L, 0L));
        assertEquals(0L, WordChannelGate.remainingMs(5_000L, 1_000L));
        assertEquals(1_000L, WordChannelGate.remainingMs(1_000L, 2_000L));
    }

    @Test public void failuresAreBoundedAndNeverShortenAnExistingBackoff() {
        int failures = 0;
        for (int i = 0; i < 10; i++) failures = WordChannelGate.failuresAfterFailure(failures);
        assertEquals(WordChannelGate.FAILURES_BEFORE_BACKOFF, failures);
        // 负数按 0 处理，于是这次失败就是第 1 次
        assertEquals(1, WordChannelGate.failuresAfterFailure(-3));
    }

    @Test public void wordChannelUsesShortTimeoutsAndNoRetry() {
        // 默认那组是给主接口的；逐字通道必须快速失败（3 秒连接 / 4 秒读取 / 只试一次）。
        assertEquals(7_000, LyricHttp.Timeouts.DEFAULT.connectMs);
        assertEquals(10_000, LyricHttp.Timeouts.DEFAULT.readMs);
        assertEquals(3, LyricHttp.Timeouts.DEFAULT.attempts);
        assertEquals(3_000, LyricHttp.Timeouts.WORD_CHANNEL.connectMs);
        assertEquals(4_000, LyricHttp.Timeouts.WORD_CHANNEL.readMs);
        assertEquals(1, LyricHttp.Timeouts.WORD_CHANNEL.attempts);
        // 最坏耗时从 ~51 秒降到 4 秒级别
        assertTrue(LyricHttp.Timeouts.WORD_CHANNEL.readMs
                < LyricHttp.Timeouts.DEFAULT.readMs * LyricHttp.Timeouts.DEFAULT.attempts);
    }
}
