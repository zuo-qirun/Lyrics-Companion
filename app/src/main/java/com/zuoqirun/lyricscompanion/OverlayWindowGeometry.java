package com.zuoqirun.lyricscompanion;

import android.content.Context;

/** One set of edge rules for creation, finger dragging and joystick movement. */
final class OverlayWindowGeometry {
    private OverlayWindowGeometry() { }

    static int inset(Context context, boolean secondary, boolean horizontal, boolean start) {
        String key = horizontal ? (start ? AppPreferences.KEY_OVERLAY_SAFE_LEFT : AppPreferences.KEY_OVERLAY_SAFE_RIGHT)
                : (start ? AppPreferences.KEY_OVERLAY_SAFE_TOP : AppPreferences.KEY_OVERLAY_SAFE_BOTTOM);
        return Math.round(Math.max(0, Math.min(200, AppPreferences.displayInt(context, secondary, key, 0)))
                * context.getResources().getDisplayMetrics().density);
    }

    static int size(Context context, boolean secondary, int requested, int screen, boolean horizontal) {
        return OverlayDragMath.safePanelSize(requested, screen, inset(context, secondary, horizontal, true),
                inset(context, secondary, horizontal, false));
    }

    static int clamp(Context context, boolean secondary, int desired, int screen, int panel, boolean horizontal) {
        int limit = OverlayDragMath.offsetLimitPx(AppPreferences.overlayAllowOffscreen(context, secondary),
                AppPreferences.overlayOffscreenPercent(context, secondary), panel,
                context.getResources().getDisplayMetrics().density);
        return OverlayDragMath.clampSafeAxis(desired, screen, panel, limit,
                inset(context, secondary, horizontal, true), inset(context, secondary, horizontal, false));
    }
}
