package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class FoliaCameraMotionTest {
    @Test public void ringSpringMatchesOriginalAnalyticResponseAtEveryFrameRate() {
        for (int fps : new int[]{24, 30, 60}) {
            FoliaCameraMotion.Spring spring = new FoliaCameraMotion.Spring();
            spring.snap(0);
            for (int i = 0; i < fps; i++) spring.step(1, 1.0 / fps, 55, 14, 0.9);
            assertEquals(FoliaRingMath.spring(1), spring.value, 1e-10);
        }
    }

    @Test public void retargetRetainsPositionAndVelocityUntilNextFrame() {
        FoliaCameraMotion.Spring spring = new FoliaCameraMotion.Spring();
        spring.snap(0);
        spring.step(1, 0.1, 55, 14, 0.9);
        double value = spring.value, velocity = spring.velocity;
        spring.step(2, 0, 55, 14, 0.9);
        assertEquals(value, spring.value, 0);
        assertEquals(velocity, spring.velocity, 0);
        assertTrue(spring.step(2, 0.016, 55, 14, 0.9) > value);
    }

    @Test public void clockFreezesPauseAndDoesNotAccumulateResumeGap() {
        FoliaCameraMotion.Clock clock = new FoliaCameraMotion.Clock();
        clock.step(1000, 1000, true);
        clock.step(1016, 1016, true);
        assertEquals(16, clock.animationMs);
        clock.step(1016, 1017, false);
        clock.step(1016, 9017, false);
        clock.step(1016, 9020, true);
        assertEquals(16, clock.animationMs);
        clock.step(1032, 9036, true);
        assertEquals(32, clock.animationMs);
    }

    @Test public void seekResetsSampleDeltaWithoutFastForwardingSimulation() {
        FoliaCameraMotion.Clock clock = new FoliaCameraMotion.Clock();
        clock.step(1000, 1000, true);
        clock.step(20000, 1016, true);
        assertTrue(clock.seek);
        assertEquals(0, clock.deltaMs);
        clock.step(20016, 1032, true);
        assertFalse(clock.seek);
        assertEquals(16, clock.deltaMs);
    }

    @Test public void firstPauseRedrawAfterLongWallGapIsNotASeek() {
        FoliaCameraMotion.Clock clock = new FoliaCameraMotion.Clock();
        clock.step(6700, 6700, true);
        clock.step(6700, 11700, false);
        assertFalse(clock.seek);
        assertEquals(0, clock.deltaMs);
    }

    @Test public void articleCameraMovesContinuouslyAndFreezesExactly() {
        FoliaCameraMotion.Article camera = new FoliaCameraMotion.Article();
        camera.snap(0, 0, 1);
        camera.step(0, 400, 100, 1.8, 400, 100, 200, 100, 0.5,
                800, 600, 0, 0.016, true, false, 0.5f);
        assertTrue(camera.x < 400);
        for (int i = 1; i <= 120; i++) camera.step(0, 400, 100, 1.8, 400, 100,
                200, 100, 0.5, 800, 600, i * 0.016, 0.016, true, false, 0.5f);
        assertTrue(camera.x > 380 && camera.x < 420);
        double x = camera.x, y = camera.y, scale = camera.scale;
        camera.step(0, 500, 200, 2, 500, 200, 200, 100, 0.5,
                800, 600, 8, 0, false, false, 0.5f);
        assertEquals(x, camera.x, 0); assertEquals(y, camera.y, 0); assertEquals(scale, camera.scale, 0);
    }

    @Test public void longArticleFlightRemainsFiniteAndWithinScaleBounds() {
        FoliaCameraMotion.Article camera = new FoliaCameraMotion.Article();
        camera.snap(20, 10, 2);
        for (int i = 0; i < 240; i++) {
            camera.step(1, 12000, 8000, 1.5, 12000, 8000, 6000, 4000, 0.22,
                    800, 600, i / 24.0, 1 / 24.0, true, false, 0.5f);
            assertFalse(Double.isNaN(camera.x));
            assertTrue(camera.scale >= 0.22 && camera.scale <= 2.24);
        }
        assertTrue(Math.abs(camera.x - 12000) < 30);
    }
}
