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
        return shouldHide(hideWhenNotPlaying, playing, hideInPlayer, playerInForeground,
                hiddenAppInForeground, false, false);
    }

    /**
     * 加上「无歌词 / 纯音乐自动隐藏」（issue #67）。
     *
     * <p>{@code hideWhenNoLyrics} 是用户开关；{@code lyricsUnavailableLongEnough} 由调用方算好——
     * 它已经把「确实在播放」「这一首确实没有可用歌词」和「过了宽限期」都折进去了。分开传是为了让
     * 宽限计时器放在服务里只推进一次，判定本身保持纯函数。
     */
    static boolean shouldHide(boolean hideWhenNotPlaying, boolean playing,
                              boolean hideInPlayer, boolean playerInForeground,
                              boolean hiddenAppInForeground, boolean hideWhenNoLyrics,
                              boolean lyricsUnavailableLongEnough) {
        return (hideWhenNotPlaying && !playing) || (hideInPlayer && playerInForeground)
                || hiddenAppInForeground
                || (hideWhenNoLyrics && lyricsUnavailableLongEnough);
    }
}
