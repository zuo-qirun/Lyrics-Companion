package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
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
        // 同一首歌在蓝牙 AVRCP 与 MediaSession 之间交接：歌名没变。调用方必须传「歌名是不是真的
        // 换了」（TrackIdentityRules.isDifferentTrackTitle），而不是含来源通道 / 词库设置的
        // lyricTrackKey —— 否则 12 万毫秒处这个连续有效的位置会被判成残留、歌词被打回开头
        // （review #80 / Codex P2）。
        assertFalse(PlaybackPositionRules.staleOnTrackChange(
                TrackIdentityRules.isDifferentTrackTitle("地下铁", "地下铁"),
                true, 120_000L, 120_000L, 260_000L));
        // 歌名真的换了，才照旧判残留。
        assertTrue(PlaybackPositionRules.staleOnTrackChange(
                TrackIdentityRules.isDifferentTrackTitle("地下铁", "红蜻蜓"),
                true, 120_000L, 120_000L, 260_000L));
    }

    @Test public void aDifferentPositionAfterATrackChangeIsTrusted() {
        // 新曲目确实在播放中（位置推进到 4 秒），不该被当成残留值。
        assertFalse(PlaybackPositionRules.staleOnTrackChange(
                true, true, 238_000L, 4_000L, 260_000L));
    }

    @Test public void repeatedStaleReportKeepsOurOwnClock() {
        // 播放中、位置值没动、时间戳是新鲜的，而这份上报已经被判为陈旧：继续用自己的时钟。
        assertTrue(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, false, true, false, true));
        // 位置值刚变过（还没到阈值、也没被判过残留）：先采信这一次上报，避免把小抖动放大。
        assertFalse(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, false, true, false, false));
    }

    @Test public void repeatedReportsOfTheSamePositionGoStaleRegardlessOfCadence() {
        // 车机每 500ms 报一次同一个位置。旧判据拿「我们自己的估计领先它多少」来比，而采信一次就会把
        // 锚点的值和时刻一起刷成这次上报，领先量每轮都从零开始长 —— 这么密的节奏下永远到不了 1.5 秒，
        // 歌词就冻在那句上。按「位置值距上次变化多久」判断才不会漏（review #80 / Codex P2）。
        long unchangedForMs = 0L;
        int firstStalePoll = -1;
        for (int poll = 1; poll <= 6; poll++) {
            unchangedForMs += 500L;          // 调用方只在位置真的变了时才清零
            if (firstStalePoll < 0
                    && PlaybackPositionRules.isStaleReport(false, false, unchangedForMs)) {
                firstStalePoll = poll;
            }
        }
        assertEquals(4, firstStalePoll);     // 500ms × 4 = 2000ms > 1500ms
        // 位置真的变了就必须重新采信。
        assertFalse(PlaybackPositionRules.isStaleReport(true, false, 60_000L));
    }

    @Test public void aRejectedTrackChangeReportStaysUntrustedUntilItChanges() {
        // 切歌那一轮被判为残留之后，下一轮值没变还得继续用自己的时钟：否则新曲目的锚点（0）立刻被
        // 旧值写回去，等于没修。
        assertTrue(PlaybackPositionRules.isStaleReport(false, true, 0L));
        // 值真的变了就恢复采信。
        assertFalse(PlaybackPositionRules.isStaleReport(true, true, 0L));
    }

    @Test public void existingTransientZeroAndMissingTimestampBehaviourIsPreserved() {
        // 导航提示把位置短暂报成 0：保留估计。
        assertTrue(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, true, true, true, false));
        // 播放器不给位置时间戳（老行为）：保留估计。
        assertTrue(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, false, false, false, false));
        // 位置真的变了、或已经不在播放：不保留。
        assertFalse(PlaybackPositionRules.keepMonotonicEstimate(
                false, true, true, true, false, true));
        assertFalse(PlaybackPositionRules.keepMonotonicEstimate(
                false, false, false, true, false, true));
        // 换了曲目时锚点必须重建。
        assertFalse(PlaybackPositionRules.keepMonotonicEstimate(
                true, true, false, true, false, true));
    }
}
