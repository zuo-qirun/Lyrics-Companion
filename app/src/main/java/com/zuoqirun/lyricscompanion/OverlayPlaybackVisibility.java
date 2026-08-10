package com.zuoqirun.lyricscompanion;

final class OverlayPlaybackVisibility {
    private OverlayPlaybackVisibility() {}

    static boolean shouldHide(boolean hideWhenNotPlaying, boolean playing,
                              boolean hideInPlayer, boolean playerInForeground) {
        return shouldHide(hideWhenNotPlaying, playing, hideInPlayer, playerInForeground, false);
    }

    static boolean shouldHide(boolean hideWhenNotPlaying, boolean playing,
                              boolean hideInPlayer, boolean playerInForeground,
                              boolean hiddenAppInForeground) {
        return (hideWhenNotPlaying && !playing) || (hideInPlayer && playerInForeground)
                || hiddenAppInForeground;
    }
}
