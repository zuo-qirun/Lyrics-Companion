package com.zuoqirun.lyricscompanion;

import android.view.WindowManager;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OverlayTouchGeometryTest {
    @Test public void touchWindowsUseTheFullScreenWithoutDisablingTouches() {
        int flags = OverlayTouchGeometry.windowFlags();
        assertTrue((flags & WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0);
        assertTrue((flags & WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0);
        assertEquals(0, flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
    }

    @Test public void systemInsetUsesActualWindowPositions() {
        // A stale touch window is 48px below the panel's button. It must not interpret its
        // local y=20 as a hit at panel y=240; moving it to the button restores the mapping.
        assertEquals(288f, OverlayTouchGeometry.panelCoordinate(20f, 468, 200), 0f);
        assertEquals(240f, OverlayTouchGeometry.panelCoordinate(20f, 420, 200), 0f);
    }

    @Test public void movingThePanelDoesNotReuseTheOldButtonOrigin() {
        assertEquals(240f, OverlayTouchGeometry.panelCoordinate(20f, 420, 200), 0f);
        assertEquals(240f, OverlayTouchGeometry.panelCoordinate(20f, 520, 300), 0f);
    }

    @Test public void offscreenAndSecondaryDisplayOriginsRemainInPanelCoordinates() {
        assertEquals(35f, OverlayTouchGeometry.panelCoordinate(5f, 0, -30), 0f);
        assertEquals(75f, OverlayTouchGeometry.panelCoordinate(5f, 1070, 1000), 0f);
    }
}
