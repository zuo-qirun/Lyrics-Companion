package com.zuoqirun.lyricscompanion;

/**
 * 面板内律动 / 频谱条的几何（issue #57）。
 *
 * <p>以前「高度」在布局（为歌词让位）与绘制（画条子）两处各写一遍同样的
 * `max(14dp, min(42dp, 面板高 × 15%))`，并各自算一次「控制按钮占位」；改一处忘另一处就会出现
 * 「预留的空间和画出来的条子不一样高」。这里统一成一份纯函数，两边都调它。
 *
 * <p>用户设了百分比时按面板高度的百分比算（并保留上下限），没设（{@link #UNSET}）时逐像素沿用
 * 原来的 14–42dp / 15% 规则。
 */
final class SpectrumLayoutMath {
    static final int UNSET = -1;
    static final int MAX_HEIGHT_PERCENT = 60;
    static final int MAX_GAP_DP = 60;
    /** 老规则：下限 14dp、上限 42dp 与面板高 15% 取小。 */
    static final float LEGACY_MIN_DP = 14f;
    static final float LEGACY_MAX_DP = 42f;
    static final float LEGACY_PANEL_RATIO = 0.15f;
    /** 播放控制按键占位：开着按钮时给 42dp，否则 5dp。 */
    static final float CONTROLS_RESERVE_DP = 42f;
    static final float NO_CONTROLS_RESERVE_DP = 5f;

    private SpectrumLayoutMath() { }

    /** 频谱条高度（像素）。 */
    static float heightPx(int heightPercent, float density, float panelHeightPx,
                          float minDp, float maxDp, float panelRatio) {
        float safeDensity = Math.max(0.01f, density);
        float minimum = Math.max(1f, minDp * safeDensity);
        if (heightPercent == UNSET) {
            return Math.max(minimum, Math.min(maxDp * safeDensity,
                    panelHeightPx * panelRatio));
        }
        float percent = Math.min(MAX_HEIGHT_PERCENT, Math.max(1, heightPercent)) / 100f;
        float cap = Math.max(minimum, maxDp * safeDensity);
        return Math.max(minimum, Math.min(cap, panelHeightPx * percent));
    }

    /** 频谱条底边离面板底部的距离（像素）：播放控制按键占位 + 用户额外留白。 */
    static float bottomInsetPx(float density, boolean controlsVisible, int gapDp) {
        float unit = Math.max(0.01f, density);
        // 3dp 的下限来自改动前写死的 max(3dp, ...)，低密度屏上也要保持原样。
        float base = Math.max(3f * unit,
                (controlsVisible ? CONTROLS_RESERVE_DP : NO_CONTROLS_RESERVE_DP) * unit);
        return base + Math.max(0, Math.min(MAX_GAP_DP, gapDp)) * unit;
    }

    static int normalizePercent(int value) {
        if (value < 0) return UNSET;
        return Math.min(MAX_HEIGHT_PERCENT, value);
    }
}
