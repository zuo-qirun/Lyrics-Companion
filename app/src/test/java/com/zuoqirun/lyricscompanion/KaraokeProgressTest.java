package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KaraokeProgressTest {
    @Test public void movesContinuouslyInsideEachCodePoint() {
        KaraokeProgress.Boundary boundary = KaraokeProgress.boundary("abc", 500);

        assertEquals(1, boundary.completeEnd);
        assertEquals(2, boundary.partialEnd);
        assertEquals(0.5f, boundary.partialFraction, 0.0001f);
    }

    @Test public void neverSplitsASurrogatePair() {
        String value = "A\uD83D\uDE00B";
        KaraokeProgress.Boundary boundary = KaraokeProgress.boundary(value, 500);

        assertEquals(1, boundary.completeEnd);
        assertEquals(3, boundary.partialEnd);
        assertEquals(0.5f, boundary.partialFraction, 0.0001f);
    }

    @Test public void completesAtTheEndOfTheWord() {
        KaraokeProgress.Boundary boundary = KaraokeProgress.boundary("smooth", 1000);

        assertEquals(6, boundary.completeEnd);
        assertEquals(6, boundary.partialEnd);
        assertEquals(0f, boundary.partialFraction, 0f);
    }
}
