package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class FoliaOriginalClockworkMotionTest {
    @Test public void secondsGearUsesSpringRatherThanInstantToothJump() {
        FoliaOriginalClockworkMotion motion = new FoliaOriginalClockworkMotion();
        for (int i = 0; i < 62; i++) motion.step(1 / 60.0, 0, false, 0.5f);
        assertEquals(1, motion.secondGearStep);
        assertTrue(motion.secondGearAngle > 0);
        assertTrue(motion.secondGearAngle < Math.PI * 2 / 15);
    }

    @Test public void pausedMechanismAndBassFreeze() {
        FoliaOriginalClockworkMotion motion = new FoliaOriginalClockworkMotion();
        motion.step(0.016, 0.8, false, 0.5f);
        double phase = motion.phase, swing = motion.swing, bass = motion.smoothedBass;
        motion.step(5, 0, true, 0.5f);
        assertEquals(phase, motion.phase, 0); assertEquals(swing, motion.swing, 0);
        assertEquals(bass, motion.smoothedBass, 0);
    }

    @Test public void resetReleasesTrackSimulationState() {
        FoliaOriginalClockworkMotion motion = new FoliaOriginalClockworkMotion();
        for (int i = 0; i < 180; i++) motion.step(1 / 60.0, 0.5, false, 0.5f);
        motion.clear();
        assertEquals(0, motion.secondGearStep); assertEquals(0, motion.phase, 0);
        assertEquals(0, motion.secondGearAngle, 0);
    }
}
