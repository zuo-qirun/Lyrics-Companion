package com.zuoqirun.lyricscompanion;

final class OverlayPlaybackVisibility {
    private OverlayPlaybackVisibility() {}

    static boolean shouldHide(boolean hideWhenNotPlaying, boolean playing,
                              boolean hideInPlayer, boolean playerInForeground) {
        return (hideWhenNotPlaying && !playing) || (hideInPlayer && playerInForeground);
    }
}
