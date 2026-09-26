package com.zuoqirun.lyricscompanion;

/**
 * 内容留白（内边距）的可调比例（issue #49）。
 *
 * <p>「无界拖动」的另一半：面板能贴到屏幕边之后，各样式写死的内边距（经典 18dp、Refined 12dp 或宽度的
 * 3.5%、紧凑 7dp…）还会把文字往回缩一截，所以留白也要能调。{@link #UNSET} = 沿用样式原本写死的值，
 * 0% = 不留白、文字尽量贴边，100% = 原样，上限 {@link #MAX_PERCENT} 供大屏放大留白。
 */
final class ContentPaddingMath {
    static final int UNSET = -1;
    static final int MAX_PERCENT = 200;

    private ContentPaddingMath() { }

    /** 按比例缩放样式原本的内边距；未设置时原样返回（逐像素不变）。 */
    static float padPx(float legacyPadPx, int percent) {
        if (percent < 0) return legacyPadPx;
        return Math.max(0f, legacyPadPx) * Math.min(MAX_PERCENT, percent) / 100f;
    }

    static int normalizePercent(int value) {
        if (value < 0) return UNSET;
        return Math.min(MAX_PERCENT, value);
    }
}
