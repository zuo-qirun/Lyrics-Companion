package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class OverlaySafeGeometryTest {
    @Test public void safeEdgesWinOverOffscreenDragging() {
        assertEquals(70, OverlayDragMath.clampSafeAxis(-100, 1000, 400, 120, 70, 0));
        assertEquals(530, OverlayDragMath.clampSafeAxis(900, 1000, 400, 120, 0, 70));
        assertEquals(-120, OverlayDragMath.clampSafeAxis(-1000, 1000, 400, 120, 0, 0));
        assertEquals(720, OverlayDragMath.clampSafeAxis(1000, 1000, 400, 120, 0, 0));
    }
    @Test public void oversizedMarginsLeaveAValidWindow() {
        assertEquals(860, OverlayDragMath.safePanelSize(1000, 1000, 70, 70));
        assertEquals(1, OverlayDragMath.safePanelSize(1000, 100, 200, 200));
        assertEquals(99, OverlayDragMath.clampSafeAxis(0, 100, 1, 0, 200, 200));
    }
    @Test public void positionMemoriesHaveSeparateScreenStyleAxisAndNumber() {
        String a = AppPreferences.positionMemoryKey(true, "pure", true, 1);
        assertNotEquals(a, AppPreferences.positionMemoryKey(false, "pure", true, 1));
        assertNotEquals(a, AppPreferences.positionMemoryKey(true, "compact", true, 1));
        assertNotEquals(a, AppPreferences.positionMemoryKey(true, "pure", false, 1));
        assertNotEquals(a, AppPreferences.positionMemoryKey(true, "pure", true, 2));
    }
}
