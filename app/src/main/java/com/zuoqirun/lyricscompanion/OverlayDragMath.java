package com.zuoqirun.lyricscompanion;

/**
 * 悬浮窗位置的夹取（issue #49）。
 *
 * <p>反馈者要的是「无界拖动 / 贴边」：现在面板被夹在屏幕内，四周边缘永远贴不到（文字又受样式内边距
 * 限制）。默认行为不变——仍然完全夹在屏幕内；只有用户明确打开「允许移出屏幕边缘」时，才允许按面板
 * 尺寸的百分比探出屏幕，便于把文字推到真正的边缘。同时保留一个硬上限，避免面板被拖到找不回来。
 */
final class OverlayDragMath {
    /** 允许探出屏幕的比例上限（相对面板自身尺寸）：再多就有整块丢掉的风险。 */
    static final int PERCENT_LIMIT = 50;
    static final int DEFAULT_PERCENT = 30;
    /** 至少留在屏幕内的触碰宽度（dp）：保证还能摸到面板把它拖回来。 */
    static final int MIN_VISIBLE_DP = 24;

    private OverlayDragMath() { }

    /** 当前设置允许探出多少像素；不允许时返回 0（= 完全夹在屏幕内）。 */
    static int offsetLimitPx(boolean allowOffscreen, int percent, int panelSizePx, float density) {
        if (!allowOffscreen || panelSizePx <= 0) return 0;
        int safePercent = Math.max(0, Math.min(PERCENT_LIMIT, percent));
        if (safePercent <= 0) return 0;
        int byPercent = Math.round(panelSizePx * safePercent / 100f);
        int byHardLimit = Math.max(0, panelSizePx
                - Math.round(MIN_VISIBLE_DP * Math.max(0.01f, density)));
        return Math.min(byPercent, byHardLimit);
    }

    /**
     * 把一轴位置夹到允许范围：{@code [-limit, screenSize - panelSize + limit]}。
     *
     * <p>limit = 0 时就是原来的 {@code [0, screenSize - panelSize]}；屏幕比面板还小时退化为 0..负值
     * 区间时取 0，避免把窗口推到屏幕外。
     */
    static int clampAxis(int desired, int screenSize, int panelSize, int limitPx) {
        int limit = Math.max(0, limitPx);
        int upper = screenSize - panelSize + limit;
        if (upper < -limit) upper = -limit;
        return Math.max(-limit, Math.min(upper, desired));
    }

    static int normalizePercent(int value) {
        return Math.max(0, Math.min(PERCENT_LIMIT, value));
    }
}
