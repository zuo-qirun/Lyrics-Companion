package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PureLyricLayoutTest {
    @Test public void sevenLineWindowFillsAtBeginningMiddleAndEnd() {
        assertEquals(0, PureLyricLayout.windowStart(20, 0, 7));
        assertEquals(7, PureLyricLayout.windowStart(20, 10, 7));
        assertEquals(13, PureLyricLayout.windowStart(20, 19, 7));
    }

    @Test public void moreLinesReduceCurrentSizeToAvailableHeight() {
        float three = PureLyricLayout.constrainedCurrentSize(40f, 180f, 3, 0.7f, 1f);
        float seven = PureLyricLayout.constrainedCurrentSize(40f, 180f, 7, 0.7f, 1f);
        assertTrue(seven < three);
        assertTrue(seven > 0f);
    }
}
