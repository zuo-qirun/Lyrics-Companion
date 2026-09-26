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

    /** issue #54：灵动岛是一只小胶囊，整块都该能拖着走。 */
    @Test public void islandReservesSurfaceForWindowDrag() {
        assertTrue(OverlayStyleInteraction.reservesSurfaceForWindowDrag("island"));
        assertFalse(OverlayStyleInteraction.reservesSurfaceForWindowDrag("refined"));
    }
}
