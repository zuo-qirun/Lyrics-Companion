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
        // issue #164：整页左边距自动叠加系统左插边；手动值更大时以手动值为准。
        assertEquals(0, SettingsNavLayout.pageInsetPx(0, 0));
        assertEquals(40, SettingsNavLayout.pageInsetPx(0, 40));
        assertEquals(40, SettingsNavLayout.pageInsetPx(40, 0));
        assertEquals(60, SettingsNavLayout.pageInsetPx(60, 40));
        assertEquals(60, SettingsNavLayout.pageInsetPx(40, 60));
        assertEquals(40, SettingsNavLayout.pageInsetPx(-30, 40));
    }

    @Test public void pageInsetIsBounded() {
        assertEquals(0, SettingsNavLayout.clampPageInsetDp(-5));
        assertEquals(120, SettingsNavLayout.clampPageInsetDp(120));
        assertEquals(SettingsNavLayout.MAX_PAGE_INSET_DP,
                SettingsNavLayout.clampPageInsetDp(500));
    }

    @Test public void navMarginNeverSqueezesTheContentOutOfView() {
        // issue #163：3200px 宽的 3.4 倍车机上，240dp 留白会把分类栏挤没；这里保证正文至少 160dp。
        int nav = 354;        // 104dp @ 3.4x
        int minContent = 544; // 160dp @ 3.4x
        int tightShell = nav + minContent + 300;
        assertEquals(300, SettingsNavLayout.clampNavMarginPx(500, tightShell, nav, minContent));
        assertEquals(100, SettingsNavLayout.clampNavMarginPx(100, 3200, nav, minContent));
        assertEquals(3200 - nav - minContent,
                SettingsNavLayout.clampNavMarginPx(9_999, 3200, nav, minContent));
    }

    @Test public void navMarginWaitsForTheRealLayoutBeforeClamping() {
        // 首次应用时视图还没测量，先按用户设置生效；布局完成后 post 再收紧。
        assertEquals(240, SettingsNavLayout.clampNavMarginPx(240, 0, 0, 544));
        assertEquals(0, SettingsNavLayout.clampNavMarginPx(0, 0, 0, 544));
    }

    @Test public void pageInsetAlsoYieldsToTheMinimumContentWidth() {
        // issue #164：整页让位同样不能把正文压没；窄屏 + 240dp 分类栏留白时页面留白要收窄。
        assertEquals(525, SettingsNavLayout.clampPageStartPx(525, 700));
        assertEquals(383, SettingsNavLayout.clampPageStartPx(525, 383));
        assertEquals(0, SettingsNavLayout.clampPageStartPx(525, -200));
        assertEquals(0, SettingsNavLayout.clampPageStartPx(0, 700));
        assertEquals(0, SettingsNavLayout.clampPageStartPx(-40, 700));
    }
}
