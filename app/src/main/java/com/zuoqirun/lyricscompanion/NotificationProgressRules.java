package com.zuoqirun.lyricscompanion;

/** A notification progress bar has arbitrary units. Accept only a duration-scaled clock. */
final class NotificationProgressRules {
    private NotificationProgressRules() { }
    static long position(int progress, int maximum, boolean indeterminate, long durationMs) {
        if (indeterminate || durationMs <= 0L || maximum <= 0 || progress < 0 || progress > maximum) return -1L;
        boolean milliseconds = maximum == durationMs;
        boolean seconds = maximum >= 10 && Math.abs(maximum * 1000L - durationMs) < 1000L;
        if (!milliseconds && !seconds) return -1L;
        return Math.min(durationMs, Math.round(durationMs * (progress / (double) maximum)));
    }
}
