package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LrcTimelineTest {
    @Test public void parsesLrcAndClosestTranslation() {
        LrcTimeline timeline = LrcTimeline.parse(
                "[00:01.00]第一句\n[00:04.500]第二句",
                "[00:01.20]First\n[00:04.50]Second");
        LrcTimeline.At at = timeline.at(4_800L);
        assertEquals("第一句", at.previousLyric);
        assertEquals("第二句", at.lyric);
        assertEquals("Second", at.translatedLyric);
    }

    @Test public void yrcTracksCompletedTextAndCurrentWordProgress() {
        LrcTimeline timeline = LrcTimeline.parse("", "",
                "[1000,2000](1000,500,0)你(1500,500,0)好(2000,1000,0)世界");
        LrcTimeline.At at = timeline.at(1_750L);
        assertEquals("你", at.completedLyric);
        assertEquals("好", at.currentWord);
        assertEquals(500, at.wordProgressPermille);
    }

    @Test public void yrcTranslationUsesMatchingLrcLineWhenFirstWordStartsLate() {
        LrcTimeline timeline = LrcTimeline.parse(
                "[00:01.00]Catch my breath\n[00:04.00]Next line",
                "[00:01.00]停下来缓缓气\n[00:04.00]下一句",
                "[3100,600](3100,600,0)Catch my breath\n"
                        + "[4200,600](4200,600,0)Next line");
        assertEquals("停下来缓缓气", timeline.at(3_300L).translatedLyric);
        assertEquals("下一句", timeline.at(4_300L).translatedLyric);
    }

    @Test public void yrcDoesNotBorrowTranslationForMatchingUntranslatedAdlib() {
        LrcTimeline timeline = LrcTimeline.parse(
                "[00:01.00]Main line\n[00:02.50]Oh",
                "[00:01.00]主句",
                "[1000,800](1000,800,0)Main line\n[2500,500](2500,500,0)Oh");
        assertEquals("", timeline.at(2_600L).translatedLyric);
    }

    @Test public void longUntimedGapBecomesInterlude() {
        LrcTimeline timeline = LrcTimeline.parse(
                "[00:01.00]开场\n[00:20.00]下一句", "");
        LrcTimeline.At gap = timeline.at(10_000L);
        assertTrue(gap.interlude);
        assertEquals("下一句", gap.nextLyric);
        assertFalse(timeline.isEmpty());
    }

    @Test public void plainLrcNeverPretendsToHaveWordTiming() {
        LrcTimeline timeline = LrcTimeline.parse("[00:01.00]整句歌词", "");
        LrcTimeline.At at = timeline.at(1_500L);
        assertEquals("整句歌词", at.lyric);
        assertFalse(at.wordTimed);
        assertEquals("", at.completedLyric);
        assertEquals("", at.currentWord);
    }

    @Test public void revealQuantizationCountsWholeUnicodeCodePoints() {
        String value = "你😀好";
        assertEquals(0, LrcTimeline.revealedCodePointCount(value, 0));
        assertEquals(1, LrcTimeline.revealedCodePointCount(value, 1));
        assertEquals(1, LrcTimeline.revealedCodePointCount(value, 333));
        assertEquals(2, LrcTimeline.revealedCodePointCount(value, 334));
        assertEquals(3, LrcTimeline.revealedCodePointCount(value, 1000));
    }

    @Test public void exposesNearbyLinesForRefinedScrollingLayout() {
        LrcTimeline timeline = LrcTimeline.parse(
                "[00:01.00]一\n[00:02.00]二\n[00:03.00]三\n[00:04.00]四\n[00:05.00]五",
                "[00:03.00]Three");
        LrcTimeline.At at = timeline.at(3_200L);
        assertEquals(5, at.nearbyLines.size());
        assertEquals(-2, at.nearbyLines.get(0).offset);
        assertEquals(0, at.nearbyLines.get(2).offset);
        assertEquals("三", at.nearbyLines.get(2).text);
        assertEquals("Three", at.nearbyLines.get(2).translated);
        assertEquals(2, at.nearbyLines.get(4).offset);
    }

    @Test public void enhancedGapCreatesARealInterludeLine() {
        LrcTimeline timeline = LrcTimeline.parse("", "",
                "[1000,1000](1000,1000,0)前句\n[8000,1000](8000,1000,0)后句");
        LrcTimeline.At at = timeline.at(4_000L);
        assertTrue(at.interlude);
        assertEquals(3, at.nearbyLines.size());
        assertTrue(at.nearbyLines.get(1).interlude);
        assertEquals(0, at.nearbyLines.get(1).offset);
    }

    @Test public void leadingGapOfFiveSecondsCreatesInterludeFromZero() {
        LrcTimeline timeline = LrcTimeline.parse("[00:05.00]第一句", "");
        LrcTimeline.At at = timeline.at(0L);
        assertTrue(at.interlude);
        assertEquals(0L, at.lineStartMs);
        assertEquals(5_000L, at.lineDurationMs);
        assertTrue(at.nearbyLines.get(0).interlude);
    }

    @Test public void browseMovesBetweenTimedLyricLines() {
        LrcTimeline timeline = LrcTimeline.parse(
                "[00:01.00]一\n[00:02.00]二\n[00:03.00]三", "");
        assertEquals(3_000L, timeline.shiftedPosition(2_200L, 1));
        assertEquals(1_000L, timeline.shiftedPosition(2_200L, -1));
    }
}
