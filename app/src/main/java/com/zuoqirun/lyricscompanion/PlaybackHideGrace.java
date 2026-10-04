package com.zuoqirun.lyricscompanion;

/** Keeps transient player gaps and track handoffs from hiding the overlays. */
final class PlaybackHideGrace {
    private long pausedSince = -1L;
    private String track = "";

    boolean shouldHide(boolean enabled, boolean playing, String trackKey,
                       long now, long graceMs) {
        if (!enabled || playing) {
            pausedSince = -1L;
        } else if (pausedSince < 0L || now < pausedSince
                || (!trackKey.isEmpty() && !trackKey.equals(track))) {
            pausedSince = now;
        }
        // Missing sessions must not erase the last track or repeatedly restart the grace period.
        if (!trackKey.isEmpty()) track = trackKey;
        return enabled && !playing && pausedSince >= 0L && now - pausedSince >= graceMs;
    }
}
