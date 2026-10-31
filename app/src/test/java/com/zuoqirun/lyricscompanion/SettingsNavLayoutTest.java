package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 设置页分类栏的摆放（issue #70）。 */
public class SettingsNavLayoutTest {
    @Test public void manualInsetIsUsedWhenTheSystemReportsNone() {
        // 车机左侧系统悬浮栏对 WindowInsets 不可见（哈弗 H6），只能靠手动留白
        assertEquals(24, SettingsNavLayout.startPaddingPx(false, 0, 0, 24));
    }

    @Test public void largerSystemInsetWins() {
        assertEquals(40, SettingsNavLayout.startPaddingPx(false, 40, 0, 12));
        assertEquals(12, SettingsNavLayout.startPaddingPx(false, 8, 0, 12));
    }

    @Test public void rightSideIgnoresTheLeftSystemInset() {
        // 分类栏在右边时，左侧那条系统栏离它很远，不该再撑开它
        assertEquals(0, SettingsNavLayout.startPaddingPx(true, 40, 0, 0));
        assertEquals(30, SettingsNavLayout.startPaddingPx(true, 40, 30, 0));
        assertEquals(50, SettingsNavLayout.startPaddingPx(true, 40, 30, 50));
    }

    @Test public void unknownPreferenceFallsBackToLeft() {
        assertFalse(SettingsNavLayout.navOnRight(null));
        assertFalse(SettingsNavLayout.navOnRight(""));
        assertFalse(SettingsNavLayout.navOnRight("abc"));
        assertTrue(SettingsNavLayout.navOnRight("right"));
    }

    @Test public void portraitKeepsTheBottomRow() {
        // 竖屏是底部一行，重排只对横屏的竖排分类栏有意义
        assertFalse(SettingsNavLayout.reordersNav(false));
        assertTrue(SettingsNavLayout.reordersNav(true));
    }

    @Test public void manualInsetIsClampedToSomethingUsable() {
        assertEquals(0, SettingsNavLayout.clampInsetDp(-20));
        assertEquals(0, SettingsNavLayout.clampInsetDp(0));
        assertEquals(120, SettingsNavLayout.clampInsetDp(120));
        assertEquals(SettingsNavLayout.MAX_MANUAL_INSET_DP,
                SettingsNavLayout.clampInsetDp(9_999));
    }

    @Test public void pageInsetTakesTheLargerOfManualAndSystemInset() {
        // issue #164：整页左边距与系统左插边取较大者
        assertEquals(0, SettingsNavLayout.pageInsetPx(0, 0));
        assertEquals(24, SettingsNavLayout.pageInsetPx(24, 0));
        assertEquals(40, SettingsNavLayout.pageInsetPx(24, 40));
        assertEquals(60, SettingsNavLayout.pageInsetPx(60, 40));
        assertEquals(0, SettingsNavLayout.pageInsetPx(-8, -4));
    }

    @Test public void pageInsetIsBounded() {
        assertEquals(0, SettingsNavLayout.clampPageInsetDp(-20));
        assertEquals(120, SettingsNavLayout.clampPageInsetDp(120));
        assertEquals(SettingsNavLayout.MAX_PAGE_INSET_DP,
                SettingsNavLayout.clampPageInsetDp(9_999));
    }

    @Test public void navMarginNeverSqueezesTheContentOutOfView() {
        // issue #163：留白再大也要给正文留最小可读宽度（shell - nav - 正文）
        assertEquals(300, SettingsNavLayout.clampNavMarginPx(544, 1198, 354, 544));
        assertEquals(240, SettingsNavLayout.clampNavMarginPx(240, 3200, 354, 544));
        assertEquals(0, SettingsNavLayout.clampNavMarginPx(544, 400, 354, 544));
    }

    @Test public void navMarginWaitsForTheRealLayoutBeforeClamping() {
        // 布局尚未完成（宽度为 0）时先按原值应用，post 后再按真实宽度收窄
        assertEquals(240, SettingsNavLayout.clampNavMarginPx(240, 0, 0, 544));
    }
}
