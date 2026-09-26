package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 面板边缘阴影的强度换算（issue #56）。 */
public class PanelShadowMathTest {
    @Test public void unsetAndZeroDrawNothing() {
        assertFalse(PanelShadowMath.enabled(PanelShadowMath.UNSET));
        assertFalse(PanelShadowMath.enabled(0));
        assertEquals(0f, PanelShadowMath.blurRadiusPx(PanelShadowMath.UNSET, 2f), 0.0001f);
        assertEquals(0f, PanelShadowMath.blurRadiusPx(0, 2f), 0.0001f);
        assertEquals(0, PanelShadowMath.alpha(0));
    }

    @Test public void strengthScalesBlurAndAlphaMonotonically() {
        float weak = PanelShadowMath.blurRadiusPx(25, 1f);
        float strong = PanelShadowMath.blurRadiusPx(100, 1f);
        assertTrue(weak > 0f);
        assertTrue(strong > weak);
        // 上限是 12dp：再宽就压到文字上了
        assertEquals(PanelShadowMath.MAX_BLUR_DP, strong, 0.0001f);
        assertEquals(PanelShadowMath.MAX_ALPHA, PanelShadowMath.alpha(100));
        assertTrue(PanelShadowMath.alpha(50) > PanelShadowMath.alpha(25));
        assertTrue(PanelShadowMath.alpha(50) < PanelShadowMath.alpha(100));
    }

    @Test public void densityScalesTheRadiusButNotTheAlpha() {
        // 12dp 的柔化半径按密度换算：1x → 12px，2x → 24px
        assertEquals(12f, PanelShadowMath.blurRadiusPx(100, 1f), 0.0001f);
        assertEquals(24f, PanelShadowMath.blurRadiusPx(100, 2f), 0.0001f);
        assertEquals(PanelShadowMath.blurRadiusPx(100, 1f) * 2f,
                PanelShadowMath.blurRadiusPx(100, 2f), 0.0001f);
        // 不透明度只跟强度有关，与密度无关
        assertEquals(PanelShadowMath.alpha(100), PanelShadowMath.alpha(100));
    }

    @Test public void percentIsClampedToTheUsableBand() {
        assertEquals(PanelShadowMath.UNSET, PanelShadowMath.normalizePercent(-1));
        assertEquals(0, PanelShadowMath.normalizePercent(0));
        assertEquals(60, PanelShadowMath.normalizePercent(60));
        assertEquals(PanelShadowMath.MAX_PERCENT, PanelShadowMath.normalizePercent(9_999));
    }
}
