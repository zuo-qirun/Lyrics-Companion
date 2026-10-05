package com.zuoqirun.lyricscompanion;

import android.view.WindowManager;

/** Shared screen coordinates for the lyric panel and its separate touch windows (#99). */
final class OverlayTouchGeometry {
    private OverlayTouchGeometry() { }

    static int windowFlags() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
    }

    static float panelCoordinate(float touchCoordinate, int touchWindowOrigin, int panelOrigin) {
        return touchCoordinate + touchWindowOrigin - panelOrigin;
    }
}
