package com.zuoqirun.lyricscompanion;

/** Waiting, completed misses and real lyrics retain their own rules. */
final class LyricPlaceholderRules {
    private LyricPlaceholderRules() { }
    static boolean blankMatching(boolean enabled, boolean active, boolean loaded, boolean available) {
        return enabled && active && !loaded && !available;
    }
}
