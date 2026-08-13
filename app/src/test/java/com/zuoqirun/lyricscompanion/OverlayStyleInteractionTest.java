package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OverlayStyleInteractionTest {
    @Test public void pureAndCompactReserveTheirSurfaceForDraggingTheWindow() {
        assertTrue(OverlayStyleInteraction.reservesSurfaceForWindowDrag("pure"));
        assertTrue(OverlayStyleInteraction.reservesSurfaceForWindowDrag("compact"));
        assertFalse(OverlayStyleInteraction.reservesSurfaceForWindowDrag("default"));
    }
}
