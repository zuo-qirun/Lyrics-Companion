package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RefinedLyricCurveTest {
    @Test
    public void focusedLineIsIdentity() {
        RefinedLyricCurve.Transform transform = RefinedLyricCurve.calculate(
                0f, 64f, 600f, 2f, 30f);

        assertEquals(0f, transform.translationX, 0.001f);
        assertEquals(0f, transform.translationY, 0.001f);
        assertEquals(0f, transform.rotationDegrees, 0.001f);
        assertEquals(1f, transform.opacity, 0.001f);
    }

    @Test
    public void lineMovesAlongCurveInsteadOfOnlyRotating() {
        RefinedLyricCurve.Transform transform = RefinedLyricCurve.calculate(
                -100f, 64f, 600f, 2f, 30f);

        assertTrue(Math.abs(transform.translationX) > 0.1f);
        assertTrue(Math.abs(transform.translationY) > 0.1f);
        assertTrue(transform.rotationDegrees > 0f);
        assertTrue(transform.opacity < 1f);
    }

    @Test
    public void automaticTransitionHasContinuousIntermediateTransform() {
        RefinedLyricCurve.Transform start = RefinedLyricCurve.calculate(
                -100f, 64f, 600f, 2f, 30f);
        RefinedLyricCurve.Transform middle = RefinedLyricCurve.calculate(
                -50f, 64f, 600f, 2f, 30f);
        RefinedLyricCurve.Transform end = RefinedLyricCurve.calculate(
                0f, 64f, 600f, 2f, 30f);

        assertTrue(middle.rotationDegrees < start.rotationDegrees);
        assertTrue(middle.rotationDegrees > end.rotationDegrees);
        assertTrue(Math.abs(middle.translationX) < Math.abs(start.translationX));
        assertTrue(Math.abs(middle.translationX) > Math.abs(end.translationX));
    }

    @Test
    public void curvatureSettingChangesMotionPath() {
        RefinedLyricCurve.Transform gentle = RefinedLyricCurve.calculate(
                -100f, 64f, 600f, 2f, 10f);
        RefinedLyricCurve.Transform strong = RefinedLyricCurve.calculate(
                -100f, 64f, 600f, 2f, 80f);

        assertTrue(strong.rotationDegrees > gentle.rotationDegrees);
        assertTrue(Math.abs(strong.translationX - gentle.translationX) > 0.1f);
    }
}
