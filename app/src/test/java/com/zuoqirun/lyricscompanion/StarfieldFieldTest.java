package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class StarfieldFieldTest {
    @Test public void positionsAreDeterministicAndInsideThePanel() {
        for (int index = 0; index < StarfieldField.MAX_STARS; index++) {
            float x = StarfieldField.starX(index, 0x5EED1234);
            float y = StarfieldField.starY(index, 0x5EED1234);
            assertTrue("x 越界: " + x, x >= 0f && x < 1f);
            assertTrue("y 越界: " + y, y >= 0f && y < 1f);
            // 同一颗星每帧都落在同一处，否则星空会「抖」。
            assertEquals(x, StarfieldField.starX(index, 0x5EED1234), 0f);
            assertEquals(y, StarfieldField.starY(index, 0x5EED1234), 0f);
        }
        assertNotEquals(StarfieldField.starX(0, 0x5EED1234),
                StarfieldField.starX(1, 0x5EED1234), 0.0001f);
        assertNotEquals(StarfieldField.starX(7, 1), StarfieldField.starX(7, 2), 0.0001f);
    }

    @Test public void densityAndSpeedStayInRange() {
        assertEquals(StarfieldField.MAX_STARS, StarfieldField.starCount(1000));
        assertTrue(StarfieldField.starCount(0) >= 4);
        assertTrue(StarfieldField.starCount(200) > StarfieldField.starCount(50));
        assertEquals(0f, StarfieldField.driftX(90_000L, 0, false), 0f);
        assertEquals(0f, StarfieldField.driftY(90_000L, 100, true), 0f);
        for (long elapsed = 0L; elapsed < 900_000L; elapsed += 7_000L) {
            float x = StarfieldField.driftX(elapsed, 200, false);
            float y = StarfieldField.driftY(elapsed, 200, false);
            assertTrue(x >= 0f && x < 1f);
            assertTrue(y >= 0f && y < 1f);
        }
        // 漂移加上位置之后仍然要落在面板里（星星从另一侧回来）。
        assertEquals(0.95f, StarfieldField.positioned(0.9f, 0.05f), 0.0001f);
        assertEquals(0.05f, StarfieldField.positioned(0.95f, 0.10f), 0.0001f);
    }

    @Test public void twinkleStaysVisibleAndFreezesInTheStillMode() {
        for (int index = 0; index < 40; index++) {
            for (long elapsed = 0L; elapsed < 20_000L; elapsed += 250L) {
                float value = StarfieldField.twinkle(index, 0x5EED1234, elapsed, false);
                assertTrue("闪烁系数越界: " + value, value >= 0.29f && value <= 1.001f);
            }
        }
        assertEquals(StarfieldField.twinkle(3, 0x5EED1234, 0L, true),
                StarfieldField.twinkle(3, 0x5EED1234, 123_456L, true), 0f);
    }

    @Test public void radiusStaysWithinTheConfiguredBand() {
        for (int index = 0; index < 50; index++) {
            float radius = StarfieldField.starRadius(index, 0x5EED1234);
            assertTrue(radius >= 0.35f && radius <= 1.5f);
        }
    }

    @Test public void driftStaysSmoothAfterTheDeviceHasBeenUpForWeeks() {
        // 位移一顿一顿的根因：elapsedMs 是开机以来的毫秒数，先转 float 再除只剩 ~2ms 精度，
        // 取小数部分后一次跳屏宽的 0.2%（1080p 上 2~3 像素）。这里用「开机 28 天」复现（issue #59）。
        long uptimeMs = 2_400_000_000L;
        float previousX = StarfieldField.driftX(uptimeMs, 100, false);
        float previousY = StarfieldField.driftY(uptimeMs, 100, false);
        for (int frame = 1; frame <= 900; frame++) {           // 15 秒 @60fps
            float currentX = StarfieldField.driftX(uptimeMs + frame * 16L, 100, false);
            float currentY = StarfieldField.driftY(uptimeMs + frame * 16L, 100, false);
            float deltaX = Math.abs(currentX - previousX);
            float deltaY = Math.abs(currentY - previousY);
            // 每帧 16ms ≈ 屏宽的 0.013%；环绕一次会跳回 0，那一次不算。
            if (deltaX < 0.5f) {
                assertTrue("开机多天后横向位移仍在跳变: " + deltaX + " 屏宽", deltaX < 0.0005f);
            }
            if (deltaY < 0.5f) {
                assertTrue("开机多天后纵向位移仍在跳变: " + deltaY + " 屏宽", deltaY < 0.0005f);
            }
            previousX = currentX;
            previousY = currentY;
        }
    }

    @Test public void frameRateIsAdjustableAndStopsAtTheRefreshRate() {
        // 「星空帧率」可调：默认 60 最流畅，越低越省电；高到 60 fps 也只能跟屏幕刷新对齐（issue #59）。
        assertEquals(StarfieldField.DEFAULT_FPS, 60);
        assertEquals(33L, StarfieldField.frameDelayMs(30));
        assertEquals(200L, StarfieldField.frameDelayMs(5));
        assertEquals(100L, StarfieldField.frameDelayMs(10));
        // 60 fps ≈ 一个刷新周期（17 ms），面板按「跟屏幕刷新对齐」处理，不再走固定延时。
        assertEquals(17L, StarfieldField.frameDelayMs(60));
        // 越界值按上下限收口，60 fps 以上不再缩短间隔。
        assertEquals(200L, StarfieldField.frameDelayMs(0));
        assertEquals(200L, StarfieldField.frameDelayMs(-3));
        assertEquals(17L, StarfieldField.frameDelayMs(144));
        for (int fps = StarfieldField.MIN_FPS; fps <= StarfieldField.MAX_FPS; fps++) {
            long delay = StarfieldField.frameDelayMs(fps);
            assertTrue(delay >= 16L && delay <= 200L);
        }
    }
}
