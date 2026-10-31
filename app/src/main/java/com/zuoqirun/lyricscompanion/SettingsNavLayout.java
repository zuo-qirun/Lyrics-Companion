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
    /** 页面左边距上限（dp，issue #164）：横竖屏都生效的整页让位。 */
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
     * 页面左边距（issue #164）：手动值与系统左侧插边取较大者，让整页内容一起让开。
     */
    static int pageInsetPx(int manualInsetPx, int systemInsetPx) {
        return Math.max(Math.max(0, manualInsetPx), Math.max(0, systemInsetPx));
    }

    static int clampPageInsetDp(int value) {
        return Math.max(0, Math.min(MAX_PAGE_INSET_DP, value));
    }

    /**
     * 分类栏外侧 margin 的上限（issue #163）：分类栏自身保持列宽，margin 只把整列推出去；
     * 但可用空间不足时（窄屏 / 超大手动留白）要收窄，给正文留下最小可读宽度。
     * 布局尚未完成（shellWidth 或 navWidth 为 0）时按原值返回，等布局完成后再算一次。
     */
    static int clampNavMarginPx(int desiredPx, int shellWidthPx, int navWidthPx, int minContentPx) {
        int desired = Math.max(0, desiredPx);
        if (shellWidthPx <= 0 || navWidthPx <= 0) return desired;
        int max = Math.max(0, shellWidthPx - navWidthPx - Math.max(0, minContentPx));
        return Math.min(desired, max);
    }
}
