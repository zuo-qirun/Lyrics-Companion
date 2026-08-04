package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AmllStyleMotionTest {
    @Test public void currentLineKeepsFullScaleAndOpacity() {
        assertEquals(1f, AmllStyleMotion.lineScale(0), 0.001f);
        assertEquals(1f, AmllStyleMotion.lineOpacity(0, true, false), 0.001f);
        assertEquals(0f, AmllStyleMotion.lineBlur(0, true, false), 0.001f);
    }

    @Test public void passedLinesFadeMoreWhilePlaying() {
        assertTrue(AmllStyleMotion.lineOpacity(-2, true, false)
                < AmllStyleMotion.lineOpacity(2, true, false));
        assertTrue(AmllStyleMotion.lineBlur(-2, true, false)
                > AmllStyleMotion.lineBlur(2, true, false));
    }

    @Test public void scrollSpringSettlesAndWordMotionPeaksInMiddle() {
        assertEquals(1f, AmllStyleMotion.scrollRemainder(0L, 520L), 0.001f);
        assertEquals(0f, AmllStyleMotion.scrollRemainder(520L, 520L), 0.001f);
        assertTrue(AmllStyleMotion.wordLift(0.5f) > AmllStyleMotion.wordLift(0f));
    }
}
