package com.zuoqirun.lyricscompanion;

/** Touch-routing rules that do not depend on Android view state. */
final class OverlayStyleInteraction {
    private OverlayStyleInteraction() { }

    static boolean reservesSurfaceForWindowDrag(String style) {
        return "compact".equals(style) || "pure".equals(style);
    }
}
