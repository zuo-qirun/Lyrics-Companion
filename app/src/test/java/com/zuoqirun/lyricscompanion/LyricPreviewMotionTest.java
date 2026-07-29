package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LyricPreviewMotionTest {
    @Test public void upwardFlickProjectsForwardLyrics() {
        assertTrue(LyricPreviewMotion.project(-8f, -900f) < -80f);
        assertTrue(LyricPreviewMotion.projectedLineDelta(-8f, -900f, 42f) > 0);
    }

    @Test public void rubberBandKeepsMovingWithProgressiveResistance() {
        float small = LyricPreviewMotion.rubberBand(20f, 100f);
        float large = LyricPreviewMotion.rubberBand(80f, 100f);
        assertTrue(small > 0f);
        assertTrue(large > small);
        assertTrue(large < 80f);
    }

    @Test public void criticalSpringSettlesWithoutFixedDuration() {
        float position = 70f;
        float velocity = -240f;
        boolean settled = false;
        for (int frame = 0; frame < 120; frame++) {
            LyricPreviewMotion.SpringState state = LyricPreviewMotion.stepCritical(
                    position, velocity, 1f / 60f, 0.38f);
            position = state.position;
            velocity = state.velocity;
            settled = state.settled;
            if (settled) break;
        }
        assertTrue(settled);
        assertEquals(0f, position, 0.001f);
        assertEquals(0f, velocity, 0.001f);
    }
}
