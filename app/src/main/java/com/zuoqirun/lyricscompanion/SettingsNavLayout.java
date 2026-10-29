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
    /** 设置页整页左边距上限（dp，issue #164）。 */
    static final int MAX_PAGE_INSET_DP = 200;

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

    /**
     * 整页左边距（issue #164）：手动值与系统左插边取较大者。车机把设置页整体往右让开系统悬浮栏时，
     * 分类栏和正文要一起移动；系统能报插边时就不必再手动补一样的距离。
     */
    static int pageInsetPx(int manualInsetPx, int systemInsetPx) {
        return Math.max(Math.max(0, manualInsetPx), Math.max(0, systemInsetPx));
    }

    static int clampPageInsetDp(int value) {
        return Math.max(0, Math.min(MAX_PAGE_INSET_DP, value));
    }

    /**
     * 「页面左边距」应用时的上限（像素，issue #164）：整页让位也不能把正文挤到读不了。availablePx 为
     * 分类栏、正文留白和最小正文宽度都扣掉之后剩下的可让位空间；调用方只在布局已测量时使用。
     */
    static int clampPageStartPx(int desiredPx, int availablePx) {
        int desired = Math.max(0, desiredPx);
        if (desired == 0) return 0;
        return Math.max(0, Math.min(desired, availablePx));
    }

    /**
     * 分类栏外侧 margin 的上限（像素，issue #163）：留白再大也要给正文留下最小可点宽度，
     * 否则分类栏自己会被挤出可视区、连滑杆都点不到。布局尺寸未知（<=0）时按原值返回，等布局完成再重算。
     */
    static int clampNavMarginPx(int desiredPx, int shellWidthPx, int navWidthPx, int minContentPx) {
        int desired = Math.max(0, desiredPx);
        if (desired == 0) return 0;
        if (shellWidthPx <= 0 || navWidthPx <= 0) return desired;
        int max = shellWidthPx - navWidthPx - Math.max(0, minContentPx);
        return Math.max(0, Math.min(desired, max));
    }
}
