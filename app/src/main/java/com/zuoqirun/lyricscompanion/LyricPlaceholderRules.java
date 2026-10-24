package com.zuoqirun.lyricscompanion;

/** Waiting, completed misses and real lyrics retain their own rules. */
final class LyricPlaceholderRules {
    private LyricPlaceholderRules() { }
    static boolean blankMatching(boolean enabled, boolean active, boolean loaded, boolean available) {
        return enabled && active && !loaded && !available;
    }

    /**
     * 间奏时是否改画上一句歌词（issue #147）。
     *
     * <p>只在「开启开关 + 正在间奏 + 歌词确实可用 + 有上一句」时保留上一句。等待和匹配中的
     * 占位行为不经过这里，{@link #blankMatching} 保持不变。
     */
    static boolean holdPreviousLine(boolean enabled, boolean interlude, boolean loaded,
                                    boolean available, String previousLyric) {
        if (!enabled || !interlude || !loaded || !available) return false;
        return previousLyric != null && !previousLyric.trim().isEmpty();
    }

    /**
     * 保留的上一句用的透明度：比正常歌词暗一截，但还能看清；再按当前句不透明度等比例衰减。
     */
    static int heldLineAlpha(int baseAlpha) {
        return Math.max(0, Math.min(255, Math.round(Math.max(0, baseAlpha) * 0.55f)));
    }
}
