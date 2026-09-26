package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Covers the arbitration that stops transient system cards from stealing the DFTC slot. */
public class MusicNotificationListenerTest {
    private static final String DFTC = "com.dftc.media";
    private static final String GHOST = "com.wecarflow.card";
    private static final String NETEASE = "com.netease.cloudmusic";

    @Test public void yieldsPausedOrGhostSessionsWhileVendorPlayerOwnsTheSlot() {
        assertTrue(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, true, GHOST, false));
        assertTrue(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, false, GHOST, false));
        assertTrue(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, true, NETEASE, false));
    }

    @Test public void staysStickyWhenAnIncomingSessionAlsoClaimsPlaying() {
        // WecarFlow mirrors can publish a playing state; flipping sources per poll is what
        // caused the reported main/secondary screen flicker.
        assertTrue(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, true, GHOST, true));
    }

    @Test public void allowsRealTakeoverOnceVendorSessionStopsReportingPlaying() {
        assertFalse(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, false, NETEASE, true));
    }

    @Test public void inactiveWithoutActiveVendorSlotOrUsableRetainedSession() {
        assertFalse(MusicNotificationListener.shouldYieldToActiveDftcSession(
                "", true, true, GHOST, true));
        assertFalse(MusicNotificationListener.shouldYieldToActiveDftcSession(
                NETEASE, true, true, GHOST, true));
        assertFalse(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, false, true, GHOST, true));
    }

    @Test public void neverBlocksTheVendorPlayerItself() {
        assertFalse(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, true, DFTC, false));
        assertFalse(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, false, DFTC, true));
    }

    /** issue #75：东风会话常驻却不再变化时，正在播放的其它播放器必须能接管。 */
    @Test public void staleVendorSessionStopsHoldingAPlayingPlayer() {
        assertTrue("东风还在推进时继续按住", MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, true, NETEASE, true, true, false));
        assertFalse("东风 60 秒没变化就让位给正在播放的酷我",
                MusicNotificationListener.shouldYieldToActiveDftcSession(
                        DFTC, true, true, NETEASE, true, false, false));
        // 进来的一路没在播放时照旧让位（防抖语义不变）
        assertTrue(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, true, GHOST, false, false, false));
        // 用户选了「始终优先东风」时回到老行为
        assertTrue(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, true, true, NETEASE, true, false, true));
        // 与东风无关的分支不受影响
        assertFalse(MusicNotificationListener.shouldYieldToActiveDftcSession(
                NETEASE, true, true, NETEASE, true, false, false));
        assertFalse(MusicNotificationListener.shouldYieldToActiveDftcSession(
                DFTC, false, true, NETEASE, true, false, false));
    }
}
