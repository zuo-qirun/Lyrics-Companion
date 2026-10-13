package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public final class PlaybackControlVisibilityTest {
    @Test public void fadeStartsAfterTheChosenDelayAndStopsAtTwentyPercent() {
        assertEquals(1, PlaybackControlVisibility.alpha(true, false, 2999, 3), 0);
        assertEquals(1, PlaybackControlVisibility.alpha(true, false, 3000, 3), 0);
        assertEquals(0.6f, PlaybackControlVisibility.alpha(true, false, 3200, 3), 0.0001f);
        assertEquals(0.2f, PlaybackControlVisibility.alpha(true, false, 3400, 3), 0.0001f);
        assertEquals(0.2f, PlaybackControlVisibility.alpha(true, false, 90000, 3), 0.0001f);
    }

    @Test public void touchHoverOrDisabledFadeAlwaysRestoreFullOpacity() {
        assertEquals(1, PlaybackControlVisibility.alpha(true, true, 90000, 3), 0);
        assertEquals(1, PlaybackControlVisibility.alpha(false, false, 90000, 3), 0);
        assertEquals(1, PlaybackControlVisibility.alpha(true, false, -20, 3), 0);
    }

    @Test public void staticPausedViewsWakeAtDelayAnimateBrieflyThenStopWaking() {
        assertEquals(2800, PlaybackControlVisibility.nextFrameDelay(true, false, 200, 3));
        assertEquals(16, PlaybackControlVisibility.nextFrameDelay(true, false, 3000, 3));
        assertEquals(16, PlaybackControlVisibility.nextFrameDelay(true, false, 3399, 3));
        assertEquals(Long.MAX_VALUE, PlaybackControlVisibility.nextFrameDelay(true, false, 3400, 3));
        assertEquals(Long.MAX_VALUE, PlaybackControlVisibility.nextFrameDelay(true, true, 0, 3));
        assertEquals(Long.MAX_VALUE, PlaybackControlVisibility.nextFrameDelay(false, false, 0, 3));
    }

    @Test public void importedDelaysAreClampedToTheSupportedRange() {
        assertEquals(1, PlaybackControlVisibility.alpha(true, false, 999, -1), 0);
        assertEquals(0.2f, PlaybackControlVisibility.alpha(true, false, 30400, 999), 0.0001f);
    }
}
