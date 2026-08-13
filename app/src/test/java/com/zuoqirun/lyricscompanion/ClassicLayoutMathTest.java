package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClassicLayoutMathTest {
    @Test public void wideningFixedHeightDoesNotIncreaseClassicScalePastHeight() {
        float normal = ClassicLayoutMath.contentScale(390f, 226f, 1f);
        float wide = ClassicLayoutMath.contentScale(780f, 226f, 1f);
        assertEquals(1f, normal, 0.001f);
        assertEquals(normal, wide, 0.001f);
    }

    @Test public void textScaleShrinksWhenNextLineHasLittleVerticalRoom() {
        float roomy = ClassicLayoutMath.constrainedTextScale(1.8f, 1f, 1f,
                1f, 0.7f, 33f, 57f, 84f, 116f, 140f, 190f, true);
        float tight = ClassicLayoutMath.constrainedTextScale(1.8f, 1f, 1f,
                1f, 0.7f, 33f, 57f, 84f, 116f, 140f, 154f, true);
        assertTrue(roomy < 1.8f);
        assertTrue(tight < roomy);
        assertTrue(tight >= 0.45f);
    }
}
