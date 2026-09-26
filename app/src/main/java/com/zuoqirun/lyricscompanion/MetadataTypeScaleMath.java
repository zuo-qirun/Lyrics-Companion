package com.zuoqirun.lyricscompanion;

/**
 * 歌名与歌手的字号关系（issue #63）。
 *
 * <p>以前只有「歌名与歌手字号」一项，歌手字号在各样式里是写死的比例（AMLL 72%、紧凑 8.5/10.5、
 * Refined 42%、Refined 全屏按 16dp 单独算），副屏上「歌名大、歌手小」没法调。这里抽成纯函数：
 * 用户没调过（{@link #UNSET}）时逐像素沿用原比例，调过之后按歌名字号的百分比算——100 就是
 * 「与歌名同号」，一个键同时满足「单独调」与「同号」两个诉求。
 */
final class MetadataTypeScaleMath {
    /** 未设置：沿用各样式原本的比例。注意不能用 0 表示未设置（0% 也应该有意义）。 */
    static final int UNSET = -1;
    static final int MAX_PERCENT = 200;

    private MetadataTypeScaleMath() { }

    static float artistSize(float titleSize, float legacyRatio, int artistPercent) {
        if (artistPercent < 0) return titleSize * legacyRatio;
        return titleSize * Math.min(MAX_PERCENT, artistPercent) / 100f;
    }

    /**
     * 各样式原本的「歌手 / 歌名」比例；返回 {@link #UNSET} 表示该样式没有固定比例（Refined 全屏的
     * 歌名字号由面板高度推导，歌手固定 16dp），调用方保持自己的写法。
     */
    static float legacyArtistRatio(String style, boolean fullscreen) {
        if ("amll".equals(style)) return 0.72f;
        if ("compact".equals(style)) return 8.5f / 10.5f;
        if ("refined".equals(style)) return fullscreen ? UNSET : 0.42f;
        return UNSET;
    }

    /** 用户设置的百分比是否可用（-1 或超出范围都按未设置处理）。 */
    static int normalizePercent(int artistPercent) {
        if (artistPercent < 0) return UNSET;
        return Math.min(MAX_PERCENT, artistPercent);
    }
}
