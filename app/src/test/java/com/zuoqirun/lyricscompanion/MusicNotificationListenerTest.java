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
}
