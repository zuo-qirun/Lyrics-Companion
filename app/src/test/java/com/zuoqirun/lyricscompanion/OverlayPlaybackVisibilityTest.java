package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class OverlayPlaybackVisibilityTest {
    @Test public void disabledOptionKeepsOverlayVisibleWhenNotPlaying() {
        assertFalse(OverlayPlaybackVisibility.shouldHide(false, false, false, false));
    }

    @Test public void enabledOptionKeepsOverlayVisibleDuringPlayback() {
        assertFalse(OverlayPlaybackVisibility.shouldHide(true, true, false, false));
    }

    @Test public void enabledOptionHidesOverlayWhenPausedOrStopped() {
        assertTrue(OverlayPlaybackVisibility.shouldHide(true, false, false, false));
    }

    @Test public void enabledPlayerRuleHidesOverlayInsidePlayer() {
        assertTrue(OverlayPlaybackVisibility.shouldHide(false, true, true, true));
    }

    @Test public void enabledPlayerRuleKeepsOverlayVisibleOutsidePlayer() {
        assertFalse(OverlayPlaybackVisibility.shouldHide(false, true, true, false));
    }
}
