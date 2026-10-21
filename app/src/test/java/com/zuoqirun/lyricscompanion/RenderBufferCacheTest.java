package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class RenderBufferCacheTest {
    @Test public void oversizedMaskIsStillUsableByItsDrawingOwner() {
        RenderBufferCache<int[]> cache = new RenderBufferCache<>(8, false);
        int[] frameMask = {1, 2, 3};
        cache.put("mask", frameMask, 12);
        assertNull(cache.get("mask"));
        assertEquals(0, cache.sizeBytes());
        assertArrayEquals(new int[]{1, 2, 3}, frameMask);
    }

    @Test public void oneOversizedCompositionSurvivesItsOwnInsertion() {
        RenderBufferCache<Object> cache = new RenderBufferCache<>(4, true);
        Object result = new Object();
        cache.put("old", new Object(), 4);
        cache.put("full-panel", result, 6);
        assertSame(result, cache.get("full-panel"));
        assertNull(cache.get("old"));
        assertEquals(6, cache.sizeBytes());
        cache.put("next-shot", new Object(), 7);
        assertNull(cache.get("full-panel"));
        assertEquals(7, cache.sizeBytes());
    }

    @Test public void evictionUsesRecencyAndClearingReleasesEveryReference() {
        RenderBufferCache<Object> cache = new RenderBufferCache<>(8, false);
        Object first = new Object(), second = new Object();
        cache.put("first", first, 4);
        cache.put("second", second, 4);
        assertSame(first, cache.get("first"));
        cache.put("third", new Object(), 4);
        assertNull(cache.get("second"));
        assertSame(first, cache.get("first"));
        cache.trimToSize(4);
        assertEquals(4, cache.sizeBytes());
        cache.evictAll();
        assertNull(cache.get("first"));
        assertEquals(0, cache.sizeBytes());
    }
}
