package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * issue #136：副屏位置数值微调的单轴锁定与手输解析。纯逻辑测试，不需要 Android 运行时。
 */
public final class PositionStepMathTest {
    @Test public void bothAxesPassThrough() {
        assertEquals(7, SecondaryPositionJoystickView.PositionStepMath.horizontalDelta(
                SecondaryPositionJoystickView.PositionStepMath.AXIS_BOTH, 7));
        assertEquals(-3, SecondaryPositionJoystickView.PositionStepMath.verticalDelta(
                SecondaryPositionJoystickView.PositionStepMath.AXIS_BOTH, -3));
    }

    @Test public void horizontalLockZeroesVerticalMovement() {
        assertEquals(7, SecondaryPositionJoystickView.PositionStepMath.horizontalDelta(
                SecondaryPositionJoystickView.PositionStepMath.AXIS_HORIZONTAL, 7));
        assertEquals(0, SecondaryPositionJoystickView.PositionStepMath.verticalDelta(
                SecondaryPositionJoystickView.PositionStepMath.AXIS_HORIZONTAL, 7));
    }

    @Test public void verticalLockZeroesHorizontalMovement() {
        assertEquals(0, SecondaryPositionJoystickView.PositionStepMath.horizontalDelta(
                SecondaryPositionJoystickView.PositionStepMath.AXIS_VERTICAL, 7));
        assertEquals(-2, SecondaryPositionJoystickView.PositionStepMath.verticalDelta(
                SecondaryPositionJoystickView.PositionStepMath.AXIS_VERTICAL, -2));
    }

    @Test public void parsesSignedOffsetsAndTrimsSpaces() {
        assertEquals(12, SecondaryPositionJoystickView.PositionStepMath.parseInputOffset(" 12 "));
        assertEquals(-12, SecondaryPositionJoystickView.PositionStepMath.parseInputOffset("-12"));
        assertEquals(0, SecondaryPositionJoystickView.PositionStepMath.parseInputOffset("0"));
    }

    @Test public void clampsOffsetsToTheSingleStepLimit() {
        assertEquals(SecondaryPositionJoystickView.PositionStepMath.MAX_INPUT_OFFSET,
                SecondaryPositionJoystickView.PositionStepMath.parseInputOffset("99999"));
        assertEquals(-SecondaryPositionJoystickView.PositionStepMath.MAX_INPUT_OFFSET,
                SecondaryPositionJoystickView.PositionStepMath.parseInputOffset("-99999"));
    }

    @Test public void rejectsEmptyOrNonNumericInput() {
        assertParsingFails("");
        assertParsingFails("   ");
        assertParsingFails("abc");
        assertParsingFails("1.5");
    }

    private static void assertParsingFails(String text) {
        try {
            SecondaryPositionJoystickView.PositionStepMath.parseInputOffset(text);
            fail("应当拒绝非法位移: " + text);
        } catch (NumberFormatException expected) {
            // 预期行为。
        }
    }
}
