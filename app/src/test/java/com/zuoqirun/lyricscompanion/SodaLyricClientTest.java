package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SodaLyricClientTest {
    private static final String RAW =
            "[6700,4030]<0,350,0>Time <350,480,0>slows <960,350,0>down\n"
                    + "[11780,4110]<0,340,0>I <340,330,0>can <960,350,0>feel";

    @Test public void convertsRelativeSodaWordsToAbsoluteEnhancedTiming() {
        String converted = SodaLyricClient.toEnhancedTiming(RAW);
        assertTrue(converted.contains("[6700,4030](6700,350,0)Time "));
        assertTrue(converted.contains("(7050,480,0)slows "));
        assertTrue(converted.contains("[11780,4110](11780,340,0)I "));
        assertTrue(converted.contains("(12120,330,0)can "));
    }

    @Test public void convertsSodaContentToMillisecondLrc() {
        String converted = SodaLyricClient.toPlainLrc(RAW);
        assertTrue(converted.contains("[00:06.700]Time slows down"));
        assertTrue(converted.contains("[00:11.780]I can feel"));
        assertFalse(converted.contains("<0,350,0>"));
    }

    @Test public void convertedTimelineRetainsWordTiming() {
        LrcTimeline timeline = LrcTimeline.parse(SodaLyricClient.toPlainLrc(RAW),
                "[00:06.70]时间变慢", SodaLyricClient.toEnhancedTiming(RAW));
        LrcTimeline.At at = timeline.at(7_100L);
        assertTrue(at.wordTimed);
        assertEquals("Time ", at.completedLyric);
        assertEquals("slows ", at.currentWord);
        assertEquals("时间变慢", at.translatedLyric);
    }
}
