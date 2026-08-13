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
        float three = PureLyricLayout.constrainedCurrentSize(
                40f, 180f, 3, 0, false, 0.7f);
        float seven = PureLyricLayout.constrainedCurrentSize(
                40f, 180f, 7, 0, false, 0.7f);
        assertTrue(seven < three);
        assertTrue(seven > 0f);
    }

    @Test public void translationsReserveHeightAndIgnoreDuplicateText() {
        float originalOnly = PureLyricLayout.constrainedCurrentSize(
                40f, 180f, 3, 0, false, 0.7f);
        float translated = PureLyricLayout.constrainedCurrentSize(
                40f, 180f, 3, 3, true, 0.7f);
        assertTrue(translated < originalOnly);
        assertTrue(PureLyricLayout.hasDistinctTranslation("Hello", "你好"));
        assertTrue(!PureLyricLayout.hasDistinctTranslation("Hello", " Hello "));
        assertTrue(!PureLyricLayout.hasDistinctTranslation("Hello", ""));
    }
}
