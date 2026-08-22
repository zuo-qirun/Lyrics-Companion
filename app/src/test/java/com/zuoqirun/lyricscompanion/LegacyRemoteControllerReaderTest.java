package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;

import android.media.RemoteControlClient;

import org.junit.Test;

public class LegacyRemoteControllerReaderTest {
    @Test public void mapsLegacyPlaybackStatesToApiNeutralStates() {
        assertEquals(MusicPlaybackData.STATE_PLAYING,
                LegacyRemoteControllerReader.normalizeState(
                        RemoteControlClient.PLAYSTATE_PLAYING));
        assertEquals(MusicPlaybackData.STATE_PAUSED,
                LegacyRemoteControllerReader.normalizeState(
                        RemoteControlClient.PLAYSTATE_PAUSED));
        assertEquals(MusicPlaybackData.STATE_BUFFERING,
                LegacyRemoteControllerReader.normalizeState(
                        RemoteControlClient.PLAYSTATE_BUFFERING));
        assertEquals(MusicPlaybackData.STATE_SKIPPING_TO_NEXT,
                LegacyRemoteControllerReader.normalizeState(
                        RemoteControlClient.PLAYSTATE_SKIPPING_FORWARDS));
        assertEquals(MusicPlaybackData.STATE_SKIPPING_TO_PREVIOUS,
                LegacyRemoteControllerReader.normalizeState(
                        RemoteControlClient.PLAYSTATE_SKIPPING_BACKWARDS));
    }

    @Test public void projectsPositionWhilePlayingAndHoldsItWhilePaused() {
        long anchor = 1_000_000L;
        // Playing at 60s with speed 1: after 30s the projected position must advance.
        assertEquals(90_000L, LegacyRemoteControllerReader.projectPosition(
                60_000L, anchor, 1f, true, anchor + 30_000L));
        // Paused playback never advances regardless of elapsed time.
        assertEquals(60_000L, LegacyRemoteControllerReader.projectPosition(
                60_000L, anchor, 0f, false, anchor + 30_000L));
        // Fractional speeds extrapolate proportionally.
        assertEquals(75_000L, LegacyRemoteControllerReader.projectPosition(
                60_000L, anchor, 0.5f, true, anchor + 30_000L));
    }

    @Test public void stateOnlyProjectionGuardsAnchorEdges() {
        // No anchor yet (before the first position report) -> caller keeps the raw value.
        assertEquals(-1L, LegacyRemoteControllerReader.projectPosition(
                12_000L, 0L, 1f, true, 5_000L));
        // A clock regression (negative gap) keeps the base instead of extrapolating backwards.
        assertEquals(30_000L, LegacyRemoteControllerReader.projectPosition(
                30_000L, 2_000_000L, 1f, true, 2_000_000L - 4_000L));
        // Projection never goes below zero.
        assertEquals(0L, LegacyRemoteControllerReader.projectPosition(
                500L, 1_000_000L, 1f, false, 1_001_000L));
    }
}
