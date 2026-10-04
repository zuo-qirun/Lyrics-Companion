package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackHideGraceTest {
    @Test public void temporaryPauseAndMissingSessionDoNotHide() {
        PlaybackHideGrace grace = new PlaybackHideGrace();
        assertFalse(grace.shouldHide(true, true, "old", 1000, 3000));
        assertFalse(grace.shouldHide(true, false, "", 1100, 3000));
        assertFalse(grace.shouldHide(true, false, "new", 1800, 3000));
        assertFalse(grace.shouldHide(true, true, "new", 2200, 3000));
    }

    @Test public void sustainedPauseHidesAtDeadlineEvenWithoutSession() {
        PlaybackHideGrace grace = new PlaybackHideGrace();
        assertFalse(grace.shouldHide(true, false, "song", 1000, 3000));
        assertFalse(grace.shouldHide(true, false, "", 3999, 3000));
        assertTrue(grace.shouldHide(true, false, "", 4000, 3000));
        assertFalse(grace.shouldHide(true, true, "song", 4100, 3000));
    }

    @Test public void disabledRuleResetsAndZeroGraceHidesImmediately() {
        PlaybackHideGrace grace = new PlaybackHideGrace();
        assertFalse(grace.shouldHide(false, false, "song", 1000, 3000));
        assertFalse(grace.shouldHide(true, false, "song", 5000, 3000));
        assertTrue(grace.shouldHide(true, false, "song", 5000, 0));
    }
}
