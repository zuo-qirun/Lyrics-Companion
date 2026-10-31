package com.zuoqirun.lyricscompanion;

/**
 * 设置页分类栏的摆放（issue #70）。
 *
 * <p>哈弗 H6 横屏时，车机自己的左侧系统悬浮快捷栏正好压在竖排分类栏上，「显示 / 歌词 / 高级」点不到。
 * 这类悬浮栏是系统 / 桌面层的窗口，{@code WindowInsets} 通常根本不报它，所以除了读系统插边之外，还要
 * 让用户能自己把分类栏挪到右边、或补一段起始留白。纯函数放在这里，便于 JVM 测试。
 */
final class SettingsNavLayout {
    static final String SIDE_LEFT = "left";
    static final String SIDE_RIGHT = "right";
    /** 手动留白上限（dp）：再大就把内容挤没了。 */
    static final int MAX_MANUAL_INSET_DP = 240;

    private SettingsNavLayout() { }

    /** 分类栏是否放到右侧；未知 / 空值一律当作左侧（默认）。 */
    static boolean navOnRight(String preference) {
        return SIDE_RIGHT.equals(preference);
    }

    /** 只有横屏才是竖排分类栏；竖屏是底部一行，保持不动。 */
    static boolean reordersNav(boolean landscape) {
        return landscape;
    }

    /**
     * 分类栏起始侧要留的额外留白（像素）：系统插边与手动留白取较大者。分类栏在右侧时看右插边
     * （左侧那条系统栏离它很远，不该再撑开它）。
     */
    static int startPaddingPx(boolean onRight, int insetLeftPx, int insetRightPx,
                              int manualInsetPx) {
        int system = Math.max(0, onRight ? insetRightPx : insetLeftPx);
        return Math.max(system, Math.max(0, manualInsetPx));
    }

    static int clampInsetDp(int value) {
        return Math.max(0, Math.min(MAX_MANUAL_INSET_DP, value));
    }
}
