package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 「背景跟随封面」的遮罩与亮度（issue #58）。 */
public class ArtworkBackgroundMathTest {
    @Test public void zeroDimSkipsTheForcedDarkeningEverywhere() {
        // 反馈的核心：遮罩 0 不该再被下限强制压暗（三条路径口径一致）
        assertEquals(0, ArtworkBackgroundMath.pipMaskAlpha(0, true, ArtworkBackgroundMath.MODE_AUTO));
        assertEquals(0, ArtworkBackgroundMath.pipMaskAlpha(0, false, ArtworkBackgroundMath.MODE_AUTO));
        assertEquals(0, ArtworkBackgroundMath.amllMaskAlpha(0, ArtworkBackgroundMath.MODE_AUTO));
        assertEquals(0, ArtworkBackgroundMath.refinedMaskAlpha(0, ArtworkBackgroundMath.MODE_AUTO));
    }

    @Test public void defaultDimKeepsTheLegacyFloors() {
        // 默认 38% 时逐像素沿用改动前：PiP 深色 max(84,70)=84、浅色 min(190,84+65)=149
        assertEquals(84, ArtworkBackgroundMath.pipMaskAlpha(38, true, ArtworkBackgroundMath.MODE_AUTO));
        assertEquals(149, ArtworkBackgroundMath.pipMaskAlpha(38, false,
                ArtworkBackgroundMath.MODE_AUTO));
        assertEquals(0x95EEE2D0, ArtworkBackgroundMath.pipMaskColor(38, false,
                ArtworkBackgroundMath.MODE_AUTO));
        assertEquals(0x5404070C, ArtworkBackgroundMath.pipMaskColor(38, true,
                ArtworkBackgroundMath.MODE_AUTO));
        // AMLL 原来是恒 ≥105（遮罩 0 的那种情况见上一个用例：现在真的不画了）
        assertEquals(105, ArtworkBackgroundMath.amllMaskAlpha(1, ArtworkBackgroundMath.MODE_AUTO));
        assertEquals(105, ArtworkBackgroundMath.amllMaskAlpha(38, ArtworkBackgroundMath.MODE_AUTO));
        // Refined 原本就尊重 0
        assertEquals(97, ArtworkBackgroundMath.refinedMaskAlpha(38,
                ArtworkBackgroundMath.MODE_AUTO));
    }

    @Test public void maskOffSkipsEvenAtTheDefaultDim() {
        assertEquals(0, ArtworkBackgroundMath.pipMaskAlpha(38, true, ArtworkBackgroundMath.MODE_OFF));
        assertEquals(0, ArtworkBackgroundMath.amllMaskAlpha(60, ArtworkBackgroundMath.MODE_OFF));
        assertEquals(0, ArtworkBackgroundMath.refinedMaskAlpha(80, ArtworkBackgroundMath.MODE_OFF));
        assertEquals(0, ArtworkBackgroundMath.pipMaskColor(38, false,
                ArtworkBackgroundMath.MODE_OFF));
    }

    @Test public void maskGrowsWithTheSetting() {
        assertTrue(ArtworkBackgroundMath.refinedMaskAlpha(10, ArtworkBackgroundMath.MODE_AUTO)
                < ArtworkBackgroundMath.refinedMaskAlpha(60, ArtworkBackgroundMath.MODE_AUTO));
        assertEquals(255, ArtworkBackgroundMath.refinedMaskAlpha(100,
                ArtworkBackgroundMath.MODE_AUTO));
        // 未知档位按默认处理，不会误关
        assertEquals(ArtworkBackgroundMath.MODE_AUTO,
                ArtworkBackgroundMath.normalizeMode("whatever"));
        assertEquals(ArtworkBackgroundMath.MODE_OFF, ArtworkBackgroundMath.normalizeMode("off"));
    }

    @Test public void brightnessZeroIsIdentityAndTheEndsAreExtremes() {
        assertEquals(0, ArtworkBackgroundMath.normalizeBrightness(0));
        assertFalse(ArtworkBackgroundMath.hasBrightness(0));
        float[] identity = ArtworkBackgroundMath.brightnessMatrixParts(0);
        assertEquals(1f, identity[0], 0.0001f);
        assertEquals(0f, identity[1], 0.0001f);
        // 拉满 → 全白；拉到底 → 全黑
        float[] up = ArtworkBackgroundMath.brightnessMatrixParts(100);
        assertEquals(0f, up[0], 0.0001f);
        assertEquals(255f, up[1], 0.0001f);
        float[] down = ArtworkBackgroundMath.brightnessMatrixParts(-100);
        assertEquals(0f, down[0], 0.0001f);
        assertEquals(0f, down[1], 0.0001f);
        // 中间值单调
        assertTrue(ArtworkBackgroundMath.brightnessMatrixParts(50)[1]
                > ArtworkBackgroundMath.brightnessMatrixParts(20)[1]);
        assertEquals(ArtworkBackgroundMath.MAX_BRIGHTNESS,
                ArtworkBackgroundMath.normalizeBrightness(9_999));
        assertEquals(-ArtworkBackgroundMath.MAX_BRIGHTNESS,
                ArtworkBackgroundMath.normalizeBrightness(-9_999));
    }
}
