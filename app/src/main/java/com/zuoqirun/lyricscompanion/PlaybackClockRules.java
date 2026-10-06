package com.zuoqirun.lyricscompanion;

/** Evidence for a clock that can survive a brief metadata-only update from the same player. */
final class PlaybackClockRules {
    static final long LAST_PLAYING_GRACE_MS = 30_000L;

    private PlaybackClockRules() { }

    static String reason(String title, boolean statePresent, int state, boolean progress,
                         boolean samePublisher, long explicitPlayingAgeMs) {
        if (statePresent && (state == MusicPlaybackData.STATE_STOPPED
                || state == MusicPlaybackData.STATE_ERROR)) {
            return "explicit_stop";
        }
        // Some car players report PAUSED while their sampled position continues moving.
        // Preserve that existing compatibility; a paused state without movement still stops.
        if (statePresent && state == MusicPlaybackData.STATE_PAUSED && !progress) return "explicit_stop";
        if (statePresent && (state == MusicPlaybackData.STATE_PLAYING
                || state == MusicPlaybackData.STATE_FAST_FORWARDING
                || state == MusicPlaybackData.STATE_REWINDING)) return "explicit_playing";
        if (progress) return "sampled_progress";
        if (statePresent && state == MusicPlaybackData.STATE_NONE
                && title != null && !title.trim().isEmpty()) return "state_none_with_title";
        if (!statePresent && samePublisher && explicitPlayingAgeMs >= 0L
                && explicitPlayingAgeMs <= LAST_PLAYING_GRACE_MS
                && title != null && !title.trim().isEmpty()) return "recent_explicit_playing";
        return "no_playing_evidence";
    }

    static boolean advances(String reason) {
        return !"explicit_stop".equals(reason) && !"no_playing_evidence".equals(reason);
    }
}
