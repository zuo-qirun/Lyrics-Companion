package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class RefinedInterludeAnimationTest {
    @Test public void threeDotsFillDuringTheirOwnThirdAndStayFilled() {
        RefinedInterludeAnimation.DotState first =
                RefinedInterludeAnimation.dotState(4_500L, 9_000L, 0);
        RefinedInterludeAnimation.DotState second =
                RefinedInterludeAnimation.dotState(4_500L, 9_000L, 1);
        RefinedInterludeAnimation.DotState third =
                RefinedInterludeAnimation.dotState(4_500L, 9_000L, 2);
        assertEquals(0.9f, first.opacity, 0.001f);
        assertEquals(0.55f, second.opacity, 0.001f);
        assertEquals(0.2f, third.opacity, 0.001f);

        for (int index = 0; index < 3; index++) {
            assertEquals(0.9f, RefinedInterludeAnimation
                    .dotState(12_000L, 9_000L, index).opacity, 0.001f);
        }
    }

    @Test public void groupBreathesOnIndependentTwoSecondLoop() {
        assertEquals(1f, RefinedInterludeAnimation.breathScale(0L), 0.001f);
        assertEquals(1.1f, RefinedInterludeAnimation.breathScale(1_000L), 0.001f);
        assertEquals(1f, RefinedInterludeAnimation.breathScale(2_000L), 0.001f);
        assertTrue(RefinedInterludeAnimation.breathScale(500L) > 1f);
    }
}
