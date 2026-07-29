package com.zuoqirun.lyricscompanion;

/** Small, clock-independent decisions for continuing a manual lyric browse session. */
final class LyricsBrowseState {
    private LyricsBrowseState() {}

    static long startingPosition(long nowElapsedMs, long previewUntilElapsedMs,
                                 long previewPositionMs, long livePositionMs) {
        return previewUntilElapsedMs > nowElapsedMs
                ? Math.max(0L, previewPositionMs) : Math.max(0L, livePositionMs);
    }
}
