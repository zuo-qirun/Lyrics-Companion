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
}
