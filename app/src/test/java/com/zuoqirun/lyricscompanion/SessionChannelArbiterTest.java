package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the AVRCP / MediaSession channel arbitration that stops CarPlay and Bluetooth from
 * stealing the active state from each other on every broadcast (the reported lyric flicker).
 */
public class SessionChannelArbiterTest {
    private static final String BLUETOOTH = "com.android.bluetooth";
    private static final String CARPLAY = "com.zijuan.carplay";
    private static final int NONE = MusicPlaybackData.STATE_NONE;
    private static final int PAUSED = MusicPlaybackData.STATE_PAUSED;
    private static final int PLAYING = MusicPlaybackData.STATE_PLAYING;

    @Test public void avrcpYieldsToAPlayingSessionThatHasValidProgress() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", 1_000L), 0L).accepted);

        // The AVRCP broadcast for the same playback must be dropped while the session keeps
        // writing: swapping the track key on every broadcast is what repaints the panel.
        assertFalse(arbiter.decide(bluetooth("夜曲", 1_000L), 400L).accepted);
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", 2_000L), 2_000L).accepted);
        assertFalse(arbiter.decide(bluetooth("夜曲", 2_000L), 2_400L).accepted);
        // The session is still refreshing, so even a paused AVRCP report cannot take the slot.
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", 20_000L), 20_000L).accepted);
        assertFalse(arbiter.decide(bluetooth("夜曲", PAUSED, true, 30_000L, 20_000L, 240_000L),
                20_400L).accepted);
    }

    @Test public void sessionWithoutProgressYieldsToAvrcp() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        // A notification-only session: a title but no PlaybackState and no position at all.
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", NONE, false,
                -1L, 0L, -1L), 0L).accepted);
        // Only AVRCP can drive the lyric clock here, so it takes the slot instead of waiting out
        // the debounce window.
        SessionChannelArbiter.Decision decision = arbiter.decide(bluetooth("夜曲", 300L), 300L);
        assertTrue(decision.accepted);
        assertTrue("active_unusable".equals(decision.reason));
    }

    @Test public void sessionWithoutMetadataYieldsToAvrcp() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        assertTrue(arbiter.decide(bluetooth("夜曲", 0L), 0L).accepted);
        // A car media center that reports PLAYING while publishing no metadata is not a session
        // that may replace a usable AVRCP track.
        assertFalse(arbiter.decide(media("media", CARPLAY, "", 400L), 400L).accepted);
        assertFalse(arbiter.decide(media("media", CARPLAY, "  ", 900L), 900L).accepted);
    }

    @Test public void keepsTheActiveChannelWhileTheTwoAlternate() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", 1_000L), 0L).accepted);

        // Both publishers write every 600 ms for nine seconds, the way the readers poll them. The
        // media session never loses the slot, so the panel is not rebuilt on every broadcast.
        boolean sawFlapLog = false;
        for (long now = 600L; now <= 9_000L; now += 600L) {
            // The active publisher keeps refreshing its own position.
            assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", now), now).accepted);
            SessionChannelArbiter.Decision decision = arbiter.decide(
                    bluetooth("夜曲", PLAYING, true, 30_000L, now, 240_000L), now);
            assertFalse("dropped at " + now, decision.accepted);
            sawFlapLog |= decision.startsFlap;
        }
        assertTrue("expected a flapping diagnostic", sawFlapLog);
    }

    @Test public void allowsTheSwitchOnceTheDebounceWindowHasPassed() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", 1_000L), 0L).accepted);
        // AVRCP asks twice while the media session is still writing; both writes are dropped.
        assertFalse(arbiter.decide(bluetooth("夜曲", 0L), 600L).accepted);
        assertFalse(arbiter.decide(bluetooth("夜曲", 1_200L), 1_200L).accepted);

        // Once the media session has been quiet for the debounce window the handover goes through.
        SessionChannelArbiter.Decision decision = arbiter.decide(bluetooth("夜曲", 4_000L), 4_000L);
        assertTrue(decision.accepted);
        assertTrue("hold_expired".equals(decision.reason));
    }

    @Test public void stalledSessionYieldsToAvrcp() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        // A retained snapshot last refreshed at t=0; nothing has fed it since.
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", PAUSED, true,
                30_000L, 1_000L, 240_000L), 0L).accepted);
        SessionChannelArbiter.Decision decision = arbiter.decide(bluetooth("夜曲", 10_000L),
                10_000L);
        assertTrue(decision.accepted);
        assertTrue("active_stalled".equals(decision.reason));
    }

    @Test public void neverDisturbsASinglePublisher() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        assertTrue(arbiter.decide(bluetooth("夜曲", 0L), 0L).accepted);
        // A real track change on the active channel is never delayed.
        assertTrue(arbiter.decide(bluetooth("晴天", 600L), 600L).accepted);
        assertTrue(arbiter.decide(bluetooth("晴天", 1_200L), 1_200L).accepted);
        // A metadata-less AVRCP broadcast still reaches MusicStateStore's partial-update merge.
        assertFalse(arbiter.decide(bluetooth("", NONE, false, -1L, 0L, -1L), 1_800L).accepted);
    }

    @Test public void twoSessionsInTheSameChannelStillGetDebounced() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", 1_000L), 0L).accepted);
        // A projection mirror registered as an unknown player resolves to "media" as well. Both
        // feeds are MediaSessions of equal quality, yet alternating between them churns the track
        // key just the same, so the same debounce has to cover them.
        SessionChannelArbiter.Signal mirror = matchScoreArguments("media", "com.wecarflow.card");
        assertFalse(arbiter.decide(mirror, 600L).accepted);
        assertFalse(arbiter.decide(mirror, 1_200L).accepted);
        // The projection feed refreshes at 1.5 s, so the mirror is still inside the window.
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", 1_500L), 1_500L).accepted);
        assertFalse(arbiter.decide(mirror, 4_000L).accepted);
        // Once that feed has been quiet for HOLD_MS (1.5 s + 3 s) the mirror does take over.
        assertTrue(arbiter.decide(mirror, 5_000L).accepted);
        // And the mirror now owns the slot, so it keeps it while it keeps refreshing.
        assertTrue(arbiter.decide(matchScoreArguments("media", "com.wecarflow.card"), 5_600L)
                .accepted);
        assertFalse(arbiter.decide(media("media", CARPLAY, "夜曲", 5_600L), 5_800L).accepted);
    }

    @Test public void rejectsNothingUntilAChannelExists() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        // The first publisher always wins, even without metadata: MusicStateStore still merges
        // such a write into the state it already has.
        assertTrue(arbiter.decide(bluetooth("", NONE, false, -1L, 0L, -1L), 0L).accepted);
        arbiter.reset();
        assertTrue(arbiter.decide(media("", CARPLAY, "", NONE, false, -1L, 0L, -1L), 1_000L)
                .accepted);
    }

    @Test public void probeAgreesWithTheDecisionWithoutChangingIt() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        assertTrue(arbiter.decide(media("media", CARPLAY, "夜曲", 1_000L), 0L).accepted);
        assertFalse(arbiter.wouldAccept(bluetooth("夜曲", 600L), 600L));
        // The read-only probe must not change the arbitration the real decision makes next.
        assertFalse(arbiter.decide(bluetooth("夜曲", 600L), 600L).accepted);
        assertFalse(arbiter.decide(bluetooth("夜曲", 1_200L), 1_200L).accepted);
        assertTrue(arbiter.wouldAccept(bluetooth("夜曲", 4_000L), 4_000L));
        assertTrue(arbiter.decide(bluetooth("夜曲", 4_000L), 4_000L).accepted);
    }

    /** issue #75：东风常驻会话（有标题、有进度、但没有位置时间戳）不该按死带时间戳的播放会话。 */
    @Test public void playingSessionWithAProgressClockTakesOverAFrozenVendorSlot() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        // 东风皓瀚：标题 + PLAYING，duration/位置时间戳都缺（AIDL 只给 NAME/TYPE/STATUS）。
        assertTrue(arbiter.decide(media("dftc_media", "com.dftc.media", "夜曲", PLAYING, true,
                0L, 0L, -1L), 0L).accepted);
        // 酷我带着位置时间戳、明确在播放：不必等满 3 秒防抖窗口就能接管。
        SessionChannelArbiter.Decision decision = arbiter.decide(
                media("kuwo", "cn.kuwo.player", "夜曲", PLAYING, true, 4_000L, 600L, 240_000L),
                600L);
        assertTrue(decision.accepted);
        assertTrue("active_no_position_evidence".equals(decision.reason));
    }

    /** 反过来：带时间戳的会话仍然不该被只有标题的会话抢走（防抖语义不变）。 */
    @Test public void frozenVendorSlotDoesNotStealFromAWorkingSession() {
        SessionChannelArbiter arbiter = new SessionChannelArbiter();
        assertTrue(arbiter.decide(media("kuwo", "cn.kuwo.player", "夜曲", PLAYING, true,
                4_000L, 0L, 240_000L), 0L).accepted);
        assertFalse(arbiter.decide(media("dftc_media", "com.dftc.media", "夜曲", PLAYING, true,
                0L, 0L, -1L), 400L).accepted);
    }

    private static SessionChannelArbiter.Signal bluetooth(String title, long positionUpdatedAt) {
        return bluetooth(title, PLAYING, true, 30_000L, positionUpdatedAt, 240_000L);
    }

    /** The same payload as {@link #bluetooth}, so only the publisher identity differs. */
    private static SessionChannelArbiter.Signal matchScoreArguments(String sourceId,
                                                                    String packageName) {
        return SessionChannelArbiter.Signal.of(sourceId, packageName,
                data("夜曲", PLAYING, true, 30_000L, 0L, 240_000L));
    }

    private static SessionChannelArbiter.Signal bluetooth(String title, int state,
                                                          boolean statePresent,
                                                          long positionMs,
                                                          long positionUpdatedAt,
                                                          long durationMs) {
        return SessionChannelArbiter.Signal.of("bluetooth", BLUETOOTH,
                data(title, state, statePresent, positionMs, positionUpdatedAt, durationMs));
    }

    private static SessionChannelArbiter.Signal media(String sourceId, String packageName,
                                                      String title, long positionUpdatedAt) {
        return media(sourceId, packageName, title, PLAYING, true, 30_000L,
                positionUpdatedAt, 240_000L);
    }

    private static SessionChannelArbiter.Signal media(String sourceId, String packageName,
                                                      String title, int state,
                                                      boolean statePresent, long positionMs,
                                                      long positionUpdatedAt, long durationMs) {
        return SessionChannelArbiter.Signal.of(sourceId, packageName,
                data(title, state, statePresent, positionMs, positionUpdatedAt, durationMs));
    }

    /** A session that publishes a PlaybackState; notification-only sessions do not. */
    private static MusicPlaybackData data(String title, int state, boolean statePresent,
                                          long positionMs, long positionUpdatedAtElapsedMs,
                                          long durationMs) {
        return new MusicPlaybackData("", title, "周杰伦", null, "", durationMs, statePresent,
                state, positionMs, positionUpdatedAtElapsedMs, state == PLAYING ? 1f : 0f);
    }
}
