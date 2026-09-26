package com.zuoqirun.lyricscompanion;

/**
 * 悬浮面板的边缘阴影（issue #56）。
 *
 * <p>硬件加速画布在 API 28 以下不认「形状」的 {@code setShadowLayer}（只有文字支持），而回到
 * {@code LAYER_TYPE_SOFTWARE} 就意味着整块面板走 CPU（#59 刚把这条路封掉）。所以阴影改成一次性位图
 * 柔化：把圆角矩形描边画进离屏位图再贴回来，面板仍是硬件渲染。
 *
 * <p>另一个现实约束：悬浮窗大小就等于面板大小（只剩 1px 余量），向外扩散的光晕会被窗口裁掉，
 * 因此这里是**向内**的柔和边缘——观感上是面板从背景里浮起来，而不是在外侧多一圈。
 */
final class PanelShadowMath {
    /** 未设置（沿用各样式原样：经典有写死的投影，其余样式没有）。 */
    static final int UNSET = -1;
    static final int MAX_PERCENT = 100;
    /** 最大柔化半径（dp）：再宽就压到歌词文字上了。 */
    static final float MAX_BLUR_DP = 12f;
    /** 100% 时的边缘不透明度。 */
    static final int MAX_ALPHA = 168;

    private PanelShadowMath() { }

    static int normalizePercent(int value) {
        if (value < 0) return UNSET;
        return Math.min(MAX_PERCENT, Math.max(0, value));
    }

    static boolean enabled(int percent) {
        return normalizePercent(percent) > 0;
    }

    /** 柔化半径（像素）；0% 时为 0。 */
    static float blurRadiusPx(int percent, float density) {
        int value = normalizePercent(percent);
        if (value <= 0) return 0f;
        return Math.max(0.01f, density) * MAX_BLUR_DP * value / MAX_PERCENT;
    }

    /** 边缘阴影的不透明度（0–255），随强度线性增长。 */
    static int alpha(int percent) {
        int value = normalizePercent(percent);
        if (value <= 0) return 0;
        return Math.round(MAX_ALPHA * value / (float) MAX_PERCENT);
    }
}
