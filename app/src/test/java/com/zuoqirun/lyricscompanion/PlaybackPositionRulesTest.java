package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the two car-player behaviours behind issue #76: a position carried over from the previous
 * track when the song changes, and a repeated stale position reported with a fresh timestamp while
 * the music is playing. Both used to freeze the lyric on one line until the user pressed pause.
 */
public class PlaybackPositionRulesTest {

    @Test public void positionCarriedOverFromThePreviousTrackIsStale() {
        // 切歌前 3:58，切歌后播放器还是报 3:58 —— 新曲目不可能"刚好"接着上一首的位置。
        assertTrue(PlaybackPositionRules.staleOnTrackChange(
                true, true, 238_000L, 238_000L, 260_000L));
        // 报告值比新曲目的时长还大：越界。
        assertTrue(PlaybackPositionRules.staleOnTrackChange(
                true, true, 12_000L, 265_000L, 260_000L));
    }

    @Test public void freshTrackStartingFromZeroIsNotStale() {
        assertFalse(PlaybackPositionRules.staleOnTrackChange(
                true, true, 238_000L, 0L, 260_000L));
        assertFalse(PlaybackPositionRules.staleOnTrackChange(
                false, true, 238_000L, 238_000L, 260_000L));
        // 第一次收到元数据（此前没有曲目）时不做任何锚点修正。
        assertFalse(PlaybackPositionRules.staleOnTrackChange(
                true, false, 0L, 238_000L, 260_000L));
        // 开头附近的值也不需要修正。
        assertFalse(PlaybackPositionRules.staleOnTrackChange(
                true, true, 2_000L, 2_000L, 260_000L));
    }

    @Test public void switchingPublishingChannelOfTheSameSongIsNotATrackChange() {
        // 同一首歌在蓝牙 AVRCP 与 MediaSession 之间切换：身份没变，位置是连续有效的，
        // 调用方传进来的就是「身份是否变了」，因此这里必须返回 false，不能把歌词打回开头。
        assertFalse(PlaybackPositionRules.staleOnTrackChange(
                false, true, 120_000L, 120_000L, 260_000L));
    }

    @Test public void aDifferentPositionAfterATrackChangeIsTrusted() {
        // 新曲目确实在播放中（位置推进到 4 秒），不该被当成残留值。
        assertFalse(PlaybackPositionRules.staleOnTrackChange(
                true, true, 238_000L, 4_000L, 260_000L));
    }

    @Test public void repeatedStaleReportKeepsOurOwnClock() {
        // 播放中、位置值没动、但时间戳是新鲜的：我们的估计已经领先 20 秒，必须继续用自己的时钟。
        assertTrue(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, false, true, false, 20_000L, 1_000L));
        // 旧值比我们的估计大（切歌残留被拒后从 0 开始的典型形态）：同样不能被它拉回去。
        assertTrue(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, false, true, false, 5_000L, 238_000L));
        // 只是差一点点（<1.5 秒）时不要夺锚点，避免把小抖动放大。
        assertFalse(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, false, true, false, 1_200L, 1_000L));
    }

    @Test public void existingTransientZeroAndMissingTimestampBehaviourIsPreserved() {
        // 导航提示把位置短暂报成 0：保留估计。
        assertTrue(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, true, true, true, 60_000L, 0L));
        // 播放器不给位置时间戳（老行为）：保留估计。
        assertTrue(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, false, false, false, 30_000L, 29_000L));
        // 位置真的变了、或已经不在播放：不保留。
        assertFalse(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, true, true, false, 30_000L, 29_000L));
        assertFalse(PlaybackPositionRules.keepMonotonicEstimate(
                false, false, false, true, false, 30_000L, 1_000L));
        // 换了曲目时锚点必须重建。
        assertFalse(PlaybackPositionRules.keepMonotonicEstimate(
                true, true, false, true, false, 30_000L, 1_000L));
    }
}
