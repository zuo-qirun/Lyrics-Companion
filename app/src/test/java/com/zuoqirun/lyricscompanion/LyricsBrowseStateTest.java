package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LyricsBrowseStateTest {
    @Test public void secondDragContinuesFromPendingPreviewPosition() {
        assertEquals(82_000L, LyricsBrowseState.startingPosition(
                10_000L, 11_500L, 82_000L, 24_000L));
    }

    @Test public void expiredPreviewStartsFromLivePlaybackPosition() {
        assertEquals(24_000L, LyricsBrowseState.startingPosition(
                12_000L, 11_500L, 82_000L, 24_000L));
    }
}
