package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public final class FoliaTextSpacingTest {
    @Test public void firstGapEqualsLaterGapsAndFinalGlyphHasNoTrailingGap() {
        float[] offsets = new float[4];
        FoliaTextSpacing.apply(new float[]{0, 10, 20, 30}, 3, offsets);
        assertArrayEquals(new float[]{0, 13, 26, 36}, offsets, 0);
        assertEquals(10, FoliaTextSpacing.extent(offsets, 0, 1, 3), 0);
        assertEquals(23, FoliaTextSpacing.extent(offsets, 0, 2, 3), 0);
        assertEquals(23, FoliaTextSpacing.extent(offsets, 1, 3, 3), 0);
        assertEquals(36, FoliaTextSpacing.extent(offsets, 0, 3, 3), 0);
    }

    @Test public void emptyAndSingleGraphemeDoNotAcquirePadding() {
        float[] empty = new float[1], single = new float[2];
        FoliaTextSpacing.apply(new float[]{0}, 30, empty);
        FoliaTextSpacing.apply(new float[]{0, 17}, 30, single);
        assertArrayEquals(new float[]{0}, empty, 0);
        assertEquals(17, single[1], 0);
        assertEquals(0, FoliaTextSpacing.extent(single, 1, 1, 30), 0);
    }

    @Test public void wrappingMeasuresRowsWithoutTheirFollowingGap() {
        float[] offsets = new float[5];
        FoliaTextSpacing.apply(new float[]{0, 10, 20, 30, 40}, 4, offsets);
        assertArrayEquals(new int[]{2, 4}, FoliaSceneMath.wrap(new String[]{"一", "二", "三", "四"}, offsets, 24, 4));
        assertArrayEquals(new int[]{1, 2, 3, 4}, FoliaSceneMath.wrap(new String[]{"一", "二", "三", "四"}, offsets, 23, 4));
    }

    @Test public void measuredKerningAndMixedGraphemeWidthsArePreserved() {
        float[] offsets = new float[4];
        FoliaTextSpacing.apply(new float[]{0, 12, 21, 41}, 2, offsets);
        assertArrayEquals(new float[]{0, 14, 25, 45}, offsets, 0);
        assertEquals(9, FoliaTextSpacing.extent(offsets, 1, 2, 2), 0);
        assertEquals(20, FoliaTextSpacing.extent(offsets, 2, 3, 2), 0);
    }
}
