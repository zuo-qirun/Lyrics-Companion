package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class FoliaCadenzaLayoutTest {
    @Test public void placementsMatchUpstreamWithItsMeasuredFixtureAdvances() {
        String value = "所有微光 汇成星河";
        List<LrcTimeline.Word> words = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) words.add(new LrcTimeline.Word(6000 + i * 333, 333, value.substring(i, i + 1)));
        LrcTimeline.Line source = new LrcTimeline.Line(6000, 3000, value, "", words);
        float[] offsets = new float[value.length() + 1];
        for (int i = 0; i < value.length(); i++) offsets[i + 1] = offsets[i] + (i == 4 ? 18.350525f : 73.690002f);
        List<FoliaCadenzaLayout.Placement> actual = FoliaCadenzaLayout.build(source,
                new FoliaGlyphTimeline(source, 9000), offsets, new int[]{7, 9}, 73.696f, 90, 529.92f);
        assertEquals(9, actual.size());
        assertEquals(3, actual.get(0).word);
        assertEquals(1.4746, actual.get(0).scale, 0.0001);
        assertEquals(-230.24527, actual.get(1).x, 0.001);
        assertEquals(-16.304, actual.get(1).y, 0.001);
        assertEquals(-164.59729, actual.get(2).x, 0.001);
        assertEquals(93.8478, actual.get(2).y, 0.001);
        assertEquals(-30.92489, actual.get(3).x, 0.001);
        assertEquals(113.15866, actual.get(3).y, 0.001);
    }

    @Test public void untimedEnglishLinesKeepWholeWordsAsFragments() {
        String value = "Until the morning comes";
        LrcTimeline.Line source = new LrcTimeline.Line(0, 3000, value, "",
                java.util.Collections.emptyList());
        float[] offsets = new float[value.length() + 1];
        for (int i = 0; i < value.length(); i++) offsets[i + 1] = offsets[i] + (value.charAt(i) == ' ' ? 8 : 12);
        List<FoliaCadenzaLayout.Placement> actual = FoliaCadenzaLayout.build(source,
                new FoliaGlyphTimeline(source, 3000), offsets, new int[]{value.length()}, 20, 24, 400);
        assertEquals(4, actual.size());
        int[] fragments = new int[actual.size()];
        for (FoliaCadenzaLayout.Placement p : actual) fragments[p.word]++;
        assertEquals(1, fragments[0]);
        assertEquals(1, fragments[1]);
        assertEquals(1, fragments[2]);
        assertEquals(1, fragments[3]);
        int letters = 0;
        for (FoliaCadenzaLayout.Placement p : actual) letters += p.end - p.first;
        assertEquals(20, letters); // five, three, seven, five letters; spaces are not fragments
    }

    @Test public void perLetterTimedEnglishWordsStillRenderAsWholeWords() {
        String value = "Until the morning comes";
        List<LrcTimeline.Word> words = new ArrayList<>();
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ' ') continue;
            words.add(new LrcTimeline.Word(i * 100, 100, value.substring(i, i + 1)));
        }
        LrcTimeline.Line source = new LrcTimeline.Line(0, 3000, value, "", words);
        float[] offsets = new float[value.length() + 1];
        for (int i = 0; i < value.length(); i++) offsets[i + 1] = offsets[i] + (value.charAt(i) == ' ' ? 8 : 12);
        List<FoliaCadenzaLayout.Placement> actual = FoliaCadenzaLayout.build(source,
                new FoliaGlyphTimeline(source, 3000), offsets, new int[]{value.length()}, 20, 24, 400);
        assertEquals(4, actual.size());
        int letters = 0;
        for (FoliaCadenzaLayout.Placement p : actual) letters += p.end - p.first;
        assertEquals(20, letters);
    }

    @Test public void aWordSplitAcrossRowsCannotBecomeTheCenteredHeroTwice() {
        LrcTimeline.Line source = new LrcTimeline.Line(0, 1000, "abcdef", "",
                java.util.Collections.singletonList(new LrcTimeline.Word(0, 1000, "abcdef")));
        List<FoliaCadenzaLayout.Placement> actual = FoliaCadenzaLayout.build(source,
                new FoliaGlyphTimeline(source, 1000), new float[]{0, 10, 20, 30, 40, 50, 60},
                new int[]{3, 6}, 10, 12, 32);
        assertEquals(2, actual.size());
        for (FoliaCadenzaLayout.Placement p : actual) assertEquals(1, p.emphasis, 0);
        assertTrue(actual.get(0).y != actual.get(1).y || actual.get(0).x != actual.get(1).x);
    }
}
