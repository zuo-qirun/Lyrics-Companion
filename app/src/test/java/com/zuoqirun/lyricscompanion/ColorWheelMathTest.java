package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/** 圆形调色盘的取色数学与亮度轴（issue #60）。 */
public class ColorWheelMathTest {
    @Test public void centreIsWhiteAtFullValue() {
        assertEquals(0xFFFFFFFF, ColorWheelMath.rgbFor(0f, 0f, 1f));
        assertEquals(0xFFFFFFFF, ColorWheelMath.rgbFor(210f, 0f, 1f));
    }

    @Test public void anyHueAtZeroValueIsPureBlack() {
        assertEquals(0xFF000000, ColorWheelMath.rgbFor(0f, 1f, 0f));
        assertEquals(0xFF000000, ColorWheelMath.rgbFor(210f, 1f, 0f));
        // 半亮度就是深色（用户要的正是这个）
        int dark = ColorWheelMath.rgbFor(0f, 1f, 0.25f);
        assertEquals(0xFF400000, dark);
    }

    @Test public void fullSaturationFullValueIsThePureHue() {
        assertEquals(0xFFFF0000, ColorWheelMath.rgbFor(0f, 1f, 1f));
        assertEquals(0xFF00FF00, ColorWheelMath.rgbFor(120f, 1f, 1f));
        assertEquals(0xFF0000FF, ColorWheelMath.rgbFor(240f, 1f, 1f));
    }

    @Test public void saturationGrowsWithDistanceAndClampsAtTheRim() {
        float radius = 40f;
        assertEquals(0f, ColorWheelMath.saturationFor(0f, 0f, radius), 0.0001f);
        assertEquals(1f, ColorWheelMath.saturationFor(radius, 0f, radius), 0.0001f);
        assertEquals(1f, ColorWheelMath.saturationFor(radius * 2f, 0f, radius), 0.0001f);
        assertTrue(ColorWheelMath.saturationFor(radius * 0.5f, 0f, radius) > 0f);
    }

    @Test public void hueMatchesTheWheelDrawingDirection() {
        float radius = 10f;
        assertEquals(0f, ColorWheelMath.hueFor(radius, 0f), 0.0001f);
        assertEquals(90f, ColorWheelMath.hueFor(0f, -radius), 0.0001f);
        assertEquals(180f, ColorWheelMath.hueFor(-radius, 0f), 0.0001f);
        assertEquals(270f, ColorWheelMath.hueFor(0f, radius), 0.0001f);
    }

    @Test public void valueComesFromTheBrightestChannel() {
        assertEquals(0f, ColorWheelMath.valueFromColorComponents(0, 0, 0), 0.0001f);
        assertEquals(128f / 255f,
                ColorWheelMath.valueFromColorComponents(128, 64, 0), 0.0001f);
        assertEquals(1f, ColorWheelMath.valueFromColorComponents(10, 255, 20), 0.0001f);
    }

    @Test public void pureBlackEntryIsNotTheAutomaticSentinel() {
        // 0 = 「自动」（跟随样式），纯黑必须存成 0xFF000000，两者不能混。
        assertNotEquals(0, 0xFF000000);
        assertEquals(0xFF000000, ColorWheelMath.rgbFor(0f, 1f,
                ColorWheelMath.normalizeValue(0)));
    }

    @Test public void colorAtCombinesHueSaturationAndTheBrightnessAxis() {
        float radius = 10f;
        // 圆心 + 半亮度 = 灰
        int grey = ColorWheelMath.colorAt(0f, 0f, radius, 0.5f);
        assertEquals(0xFF808080, grey);
        // 边缘 + 满亮度 = 纯色
        assertEquals(0xFFFF0000, ColorWheelMath.colorAt(radius, 0f, radius, 1f));
    }

    @Test public void valuePercentIsClamped() {
        assertEquals(0f, ColorWheelMath.normalizeValue(-20), 0.0001f);
        assertEquals(0.35f, ColorWheelMath.normalizeValue(35), 0.0001f);
        assertEquals(1f, ColorWheelMath.normalizeValue(200), 0.0001f);
    }
}
