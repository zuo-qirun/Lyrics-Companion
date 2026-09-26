package com.zuoqirun.lyricscompanion;

/**
 * 「背景跟随封面」的遮罩与亮度（issue #58）。
 *
 * <p>反馈者要的是参考图那种轻透效果：整块面板就是封面放大模糊、几乎不压暗。但三条绘制路径以前各有
 * 自己的下限——PiP 深色恒 ≥70、浅色恒 ≥65 且固定暖米色，AMLL 恒 ≥105，只有 Refined 老实按 0 算。
 * 于是「遮罩 = 0」在不同样式下效果完全不同，永远压不暗也永远亮不起来。
 *
 * <p>这里统一成一份纯函数：<b>遮罩为 0 就不画</b>（三处一致），其它取值沿用各自原来的下限，所以
 * 默认值 38 的老安装逐像素不变；{@code "off"} 档无论遮罩多少都不画。
 */
final class ArtworkBackgroundMath {
    /** 遮罩档位：auto = 沿用各样式原有下限，off = 完全不画遮罩。 */
    static final String MODE_AUTO = "auto";
    static final String MODE_OFF = "off";
    static final int MAX_BRIGHTNESS = 100;

    private ArtworkBackgroundMath() { }

    static String normalizeMode(String mode) {
        return MODE_OFF.equals(mode) ? MODE_OFF : MODE_AUTO;
    }

    /** PiP 样式的遮罩不透明度：深色下限 70、浅色 +65（上限 190），遮罩 0 或 off 时不画。 */
    static int pipMaskAlpha(int dimPercent, boolean dark, String mode) {
        if (MODE_OFF.equals(normalizeMode(mode))) return 0;
        int percent = clampPercent(dimPercent);
        if (percent <= 0) return 0;
        int dim = Math.round(percent / 100f * 220f);
        return dark ? Math.max(dim, 70) : Math.min(190, dim + 65);
    }

    /** PiP 浅色时的暖米色 (238,226,208)：只在真的画遮罩时才有意义。 */
    static int pipMaskColor(int dimPercent, boolean dark, String mode) {
        int alpha = pipMaskAlpha(dimPercent, dark, mode);
        if (alpha <= 0) return 0;
        return dark ? (alpha << 24) | 0x0004070C : (alpha << 24) | 0x00EEE2D0;
    }

    /** AMLL 的遮罩：原来的下限是 105，遮罩 0 或 off 时不画。 */
    static int amllMaskAlpha(int dimPercent, String mode) {
        if (MODE_OFF.equals(normalizeMode(mode))) return 0;
        int percent = clampPercent(dimPercent);
        if (percent <= 0) return 0;
        return Math.max(105, Math.round(percent / 100f * 210f));
    }

    /** Refined 的遮罩：原来就尊重 0，这里只是让三处口径一致。 */
    static int refinedMaskAlpha(int dimPercent, String mode) {
        if (MODE_OFF.equals(normalizeMode(mode))) return 0;
        return Math.max(0, Math.round(clampPercent(dimPercent) / 100f * 255f));
    }

    /** 亮度轴（-100..100）：0 = 不变，用于背景图，不碰文字与不透明度。 */
    static int normalizeBrightness(int value) {
        return Math.max(-MAX_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, value));
    }

    static boolean hasBrightness(int value) {
        return normalizeBrightness(value) != 0;
    }

    /**
     * 亮度 → ColorMatrix 的缩放系数：正数往白靠（缩放 + 偏移），负数直接缩放。
     * 返回 {@code [scale, translate]}，两值都是 0..255 域上的量。
     */
    static float[] brightnessMatrixParts(int value) {
        int percent = normalizeBrightness(value);
        if (percent > 0) {
            float scale = 1f - percent / 100f;
            return new float[]{scale, percent / 100f * 255f};
        }
        return new float[]{1f + percent / 100f, 0f};
    }

    private static int clampPercent(int dimPercent) {
        return Math.max(0, Math.min(100, dimPercent));
    }
}
