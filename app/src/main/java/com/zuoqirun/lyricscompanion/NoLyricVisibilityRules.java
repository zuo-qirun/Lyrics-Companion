package com.zuoqirun.lyricscompanion;

/**
 * 「无歌词 / 纯音乐时自动隐藏」的宽限判定（issue #67）。
 *
 * <p>切歌时歌词要先匹配，这段时间面板本来就是空的；不给宽限就会「切一首藏一下」。所以规则是：
 * 确实在播放、这一首在当前时刻没有可用歌词、并且这种状态持续超过宽限期，才隐藏。时间由调用方传入
 * （服务每 500ms 轮询一次），这里保持纯函数便于测试。
 */
final class NoLyricVisibilityRules {
    /** 默认宽限：给匹配歌词留 8 秒。 */
    static final int DEFAULT_GRACE_MS = 8_000;
    static final int MAX_GRACE_MS = 60_000;
    /** 「还没开始计时」。 */
    static final long NO_CLOCK = -1L;

    private NoLyricVisibilityRules() { }

    /**
     * 推进「无歌词从什么时候开始」的计时器。
     *
     * <p>有可用歌词 → 清空；没有歌词时：同一首歌沿用原来的时刻（这样宽限期不会被每 500ms 的轮询
     * 重置），换了歌则重新计时。
     */
    static long nextUnavailableSince(boolean lyricAvailable, String trackKey,
                                     String lastTrackKey, long nowElapsedMs,
                                     long unavailableSinceElapsedMs) {
        if (lyricAvailable) return NO_CLOCK;
        String current = trackKey == null ? "" : trackKey;
        boolean sameTrack = unavailableSinceElapsedMs >= 0L && current.equals(
                lastTrackKey == null ? "" : lastTrackKey);
        return sameTrack ? unavailableSinceElapsedMs : nowElapsedMs;
    }

    /** 宽限期是否已经过完。计时器还没开始（{@link #NO_CLOCK}）时永不隐藏。 */
    static boolean graceElapsed(long nowElapsedMs, long unavailableSinceElapsedMs,
                                int graceMs) {
        if (unavailableSinceElapsedMs < 0L) return false;
        int grace = Math.max(0, Math.min(MAX_GRACE_MS, graceMs));
        return nowElapsedMs - unavailableSinceElapsedMs >= grace;
    }

    static int normalizeGraceMs(int value) {
        return Math.max(0, Math.min(MAX_GRACE_MS, value));
    }
}
