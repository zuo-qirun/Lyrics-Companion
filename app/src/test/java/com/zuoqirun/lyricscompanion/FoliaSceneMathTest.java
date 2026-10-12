package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public final class FoliaSceneMathTest {
    @Test public void cadenzaGlowFollowsUpstreamPlateauAndQuadraticTail() {
        assertEquals(0, FoliaSceneMath.cadenzaGlow(999, 1000, 2000), 0);
        assertEquals(1, FoliaSceneMath.cadenzaGlow(1500, 1000, 2000), 0);
        assertEquals(0.9, FoliaSceneMath.cadenzaGlow(2000, 1000, 2000), 0.0001);
        assertEquals(0.729, FoliaSceneMath.cadenzaGlow(2090, 1000, 2000), 0.0001);
        assertEquals(0, FoliaSceneMath.cadenzaGlow(2900, 1000, 2000), 0);
    }
    @Test public void sonnetEmphasisIgnoresTimestampRoundingOfEqualDurationWords() {
        assertEquals(FoliaSceneMath.sonnetHeroScore(2, 666), FoliaSceneMath.sonnetHeroScore(2, 667), 0);
        assertTrue(FoliaSceneMath.sonnetHeroScore(4, 1000) > FoliaSceneMath.sonnetHeroScore(2, 667));
    }
    @Test public void partitaMatchesValuesExportedFromUpstreamAtSixSeconds() {
        java.util.List<FoliaSceneMath.PartitaChunk> chunks = FoliaSceneMath.partitaChunks(9, 600, 6);
        assertEquals(3, chunks.size());
        assertEquals(4, chunks.get(0).end);
        assertEquals(7, chunks.get(1).end);
        assertEquals(9, chunks.get(2).end);
        assertEquals(-66.5972987, chunks.get(0).x, 0.0001);
        assertEquals(0.96636718, chunks.get(0).scale, 0.0001);
        assertEquals(83.1112885, chunks.get(1).x, 0.0001);
        assertEquals(1.40333144, chunks.get(2).scale, 0.0001);
        assertTrue(FoliaSceneMath.partitaChunks(0, 600, 6).isEmpty());
    }
    @Test public void viewportFontClampsMatchUpstreamCss() {
        assertEquals(48, FoliaSceneMath.clampFont(800, 36, 6, 72, 1), 0.001);
        assertEquals(36, FoliaSceneMath.clampFont(320, 36, 6, 72, 1), 0.001);
        assertEquals(144, FoliaSceneMath.clampFont(2400, 36, 6, 72, 2), 0.001);
        assertEquals(55, FoliaSceneMath.clampFont(800, 50, 6.875f, 90, 1), 0.001);
    }

    @Test public void pendoloSpringOvershootsAndSettlesIndependentlyOfRing() {
        assertEquals(0, FoliaSceneMath.spring(0, 360, 20, 0.8), 0);
        assertTrue(FoliaSceneMath.spring(0.18, 360, 20, 0.8) > 1);
        assertEquals(1, FoliaSceneMath.spring(1.4, 360, 20, 0.8), 0.0001);
        assertTrue(Double.isFinite(FoliaSceneMath.spring(1, 4, 4, 1)));
        assertTrue(Double.isFinite(FoliaSceneMath.spring(1, 4, 8, 1)));
    }

    @Test public void seededTiltLayoutIsStableAcrossSeeks() {
        assertEquals(2, FoliaSceneMath.tiltRowCount(9, 6000));
        int row = FoliaSceneMath.tiltAccentRow(2, 6000);
        assertEquals(row, FoliaSceneMath.tiltAccentRow(2, 6000));
        assertTrue(row >= -1 && row < 2);
        assertTrue(FoliaSceneMath.tiltRowCount(100, 1234) <= 4);
    }

    @Test public void cadenzaLongLyricsUseOriginalDensityPenalty() {
        assertEquals(77.056, FoliaSceneMath.cadenzaFont(800, 9, 4, 1), 0.001);
        assertTrue(FoliaSceneMath.cadenzaFont(800, 30, 20, 1)
                < FoliaSceneMath.cadenzaFont(800, 9, 4, 1));
        assertTrue(FoliaSceneMath.cadenzaFont(160, 80, 80, 1) >= 24);
    }

    @Test public void wrappingPreservesGraphemesAndLatinWords() {
        assertArrayEquals(new int[]{2, 4}, FoliaSceneMath.wrap(new String[]{"你", "好", "世", "界"},
                new float[]{0, 10, 20, 30, 40}, 21));
        assertArrayEquals(new int[]{3, 7}, FoliaSceneMath.wrap(new String[]{"h", "i", " ", "w", "o", "r", "d"},
                new float[]{0, 10, 20, 24, 34, 44, 54, 64}, 55));
        assertArrayEquals(new int[0], FoliaSceneMath.wrap(new String[0], new float[]{0}, 50));
    }

    @Test public void chatSendersAreSeededAndForceEveryFifthMessageRight() {
        assertEquals(0x4f9f2cabL, FoliaSceneMath.hash("hello"));
        assertArrayEquals(new boolean[0], FoliaSceneMath.cappellaSides(Collections.emptyList()));
        java.util.List<LrcTimeline.Line> lines = Arrays.asList(new LrcTimeline.Line(0, 1000, "a"),
                new LrcTimeline.Line(1000, 1000, "b"), new LrcTimeline.Line(2000, 1000, "c"),
                new LrcTimeline.Line(3000, 1000, "d"), new LrcTimeline.Line(4000, 1000, "e"),
                new LrcTimeline.Line(5000, 1000, "f"));
        boolean[] sides = FoliaSceneMath.cappellaSides(lines);
        assertTrue(sides[4]);
        assertArrayEquals(sides, FoliaSceneMath.cappellaSides(lines));
    }
}
