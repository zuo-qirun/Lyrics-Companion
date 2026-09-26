package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 面板内律动 / 频谱条的几何（issue #57）。 */
public class SpectrumLayoutMathTest {
    private static final float DENSITY = 1f;
    private static final float LEGACY_MIN = SpectrumLayoutMath.LEGACY_MIN_DP;
    private static final float LEGACY_MAX = SpectrumLayoutMath.LEGACY_MAX_DP;
    private static final float LEGACY_RATIO = SpectrumLayoutMath.LEGACY_PANEL_RATIO;

    private static float legacy(int percent, float panelHeight) {
        return SpectrumLayoutMath.heightPx(percent, DENSITY, panelHeight,
                LEGACY_MIN, LEGACY_MAX, LEGACY_RATIO);
    }

    @Test public void unsetKeepsTheLegacyFourteenToFortyTwoDp() {
        // 226dp 的面板：15% = 33.9dp，落在 14–42 之间
        assertEquals(33.9f, legacy(SpectrumLayoutMath.UNSET, 226f), 0.01f);
        // 很矮的面板取 14dp 下限，很高的面板取 42dp 上限（原来车机上「偏高」就是这么来的）
        assertEquals(14f, legacy(SpectrumLayoutMath.UNSET, 60f), 0.01f);
        assertEquals(42f, legacy(SpectrumLayoutMath.UNSET, 1_080f), 0.01f);
    }

    @Test public void percentOverridesThePanelRatioWithinTheLegacyBand() {
        assertEquals(22.6f, legacy(10, 226f), 0.01f);
        assertEquals(14f, legacy(1, 226f), 0.01f);
        // 上限之外按 42dp 收口，不会把歌词挤没
        assertEquals(42f, legacy(60, 226f), 0.01f);
        assertEquals(42f, legacy(60, 1_080f), 0.01f);
    }

    @Test public void percentIsNormalisedAndBounded() {
        assertEquals(SpectrumLayoutMath.UNSET, SpectrumLayoutMath.normalizePercent(-1));
        assertEquals(0, SpectrumLayoutMath.normalizePercent(0));
        assertEquals(35, SpectrumLayoutMath.normalizePercent(35));
        assertEquals(SpectrumLayoutMath.MAX_HEIGHT_PERCENT,
                SpectrumLayoutMath.normalizePercent(500));
    }

    @Test public void bottomInsetReproducesTheControlsReserve() {
        // 没有额外留白时与改动前一致：开按钮 42dp / 关按钮 5dp（低密度屏上还有 3dp 下限）
        assertEquals(42f, SpectrumLayoutMath.bottomInsetPx(1f, true, 0), 0.001f);
        assertEquals(5f, SpectrumLayoutMath.bottomInsetPx(1f, false, 0), 0.001f);
        // 低密度屏按 dp 等比缩放（原来写死的 max(3dp, …) 就是这个结果）
        assertEquals(2.5f, SpectrumLayoutMath.bottomInsetPx(0.5f, false, 0), 0.001f);
    }

    @Test public void gapShiftsTheSpectrumUpAndIsClamped() {
        assertTrue(SpectrumLayoutMath.bottomInsetPx(1f, false, 12)
                > SpectrumLayoutMath.bottomInsetPx(1f, false, 0));
        assertEquals(SpectrumLayoutMath.MAX_GAP_DP + 5f,
                SpectrumLayoutMath.bottomInsetPx(1f, false, 9_999), 0.001f);
        assertEquals(5f, SpectrumLayoutMath.bottomInsetPx(1f, false, -20), 0.001f);
    }
}
