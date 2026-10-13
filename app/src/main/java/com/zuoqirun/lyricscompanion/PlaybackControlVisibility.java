package com.zuoqirun.lyricscompanion;

/** Optional idle fade; buttons remain hit-testable and regain full opacity on interaction. */
final class PlaybackControlVisibility {
    private PlaybackControlVisibility() { }
    static float alpha(boolean enabled, boolean interacting, long idleMs, int delaySeconds) {
        if (!enabled || interacting) return 1;
        long delay = Math.max(1, Math.min(30, delaySeconds)) * 1000L;
        float progress = Math.max(0, Math.min(1, (idleMs - delay) / 400f));
        return 1 - progress * 0.8f;
    }
    static long nextFrameDelay(boolean enabled, boolean interacting, long idleMs, int delaySeconds) {
        if (!enabled || interacting) return Long.MAX_VALUE;
        long start = Math.max(1, Math.min(30, delaySeconds)) * 1000L;
        if (idleMs < start) return Math.max(1, start - idleMs);
        return idleMs < start + 400 ? 16 : Long.MAX_VALUE;
    }
}
