package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackClockRulesTest {
    @Test public void metadataOnlyUpdateKeepsRecentClockOnlyForSamePublisher() {
        assertEquals("recent_explicit_playing", PlaybackClockRules.reason("下一首", false, 0, false, true, 500));
        assertFalse(PlaybackClockRules.advances(PlaybackClockRules.reason("下一首", false, 0, false, false, 500)));
        assertFalse(PlaybackClockRules.advances(PlaybackClockRules.reason("下一首", false, 0, false, true, 30001)));
        assertFalse(PlaybackClockRules.advances(PlaybackClockRules.reason("", false, 0, false, true, 500)));
    }

    @Test public void stopAndErrorOverrideMovementAndPauseNeedsActualProgress() {
        for (int state : new int[]{MusicPlaybackData.STATE_STOPPED,
                MusicPlaybackData.STATE_ERROR}) {
            assertFalse(PlaybackClockRules.advances(PlaybackClockRules.reason("歌", true, state, true, true, 100)));
        }
        assertFalse(PlaybackClockRules.advances(PlaybackClockRules.reason("歌", true,
                MusicPlaybackData.STATE_PAUSED, false, true, 100)));
        assertTrue(PlaybackClockRules.advances(PlaybackClockRules.reason("歌", true,
                MusicPlaybackData.STATE_PAUSED, true, true, 100)));
    }

    @Test public void missingStateDoesNotInventPlaybackWithoutEvidence() {
        assertFalse(PlaybackClockRules.advances(PlaybackClockRules.reason("歌", false, 3, false, true, -1)));
        assertTrue(PlaybackClockRules.advances(PlaybackClockRules.reason("歌", true, 3, false, false, -1)));
        assertTrue(PlaybackClockRules.advances(PlaybackClockRules.reason("歌", false, 0, true, false, -1)));
    }
}
