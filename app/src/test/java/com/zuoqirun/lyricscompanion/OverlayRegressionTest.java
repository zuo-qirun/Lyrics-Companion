package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class OverlayRegressionTest {
    @Test public void extraScreenAlonePreventsDefaultMainOverlay() {
        assertFalse(AppPreferences.needsDefaultOverlayTarget(false, false, false, false, 1));
        assertTrue(AppPreferences.needsDefaultOverlayTarget(false, false, false, false, 0));
        assertFalse(AppPreferences.needsDefaultOverlayTarget(false, true, false, false, 0));
    }

    @Test public void classicMetadataStaysFixedWhileLyricsMove() {
        assertEquals(50f, ClassicLayoutMath.rowBaseline(20f, 30f, true, -12f, 8f), 0.001f);
        assertEquals(46f, ClassicLayoutMath.rowBaseline(20f, 30f, false, -12f, 8f), 0.001f);
    }
}
