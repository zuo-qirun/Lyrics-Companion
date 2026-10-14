package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ResourceProfileTest {
    @Test public void lowProfileCapsAnimationButDoesNotWakeStaticOrSleepingSurfaces() {
        assertEquals(84, ResourceProfile.frameDelay(true, 16));
        assertEquals(84, ResourceProfile.frameDelay(true, 33));
        assertEquals(400, ResourceProfile.frameDelay(true, 400));
        assertEquals(Long.MAX_VALUE, ResourceProfile.frameDelay(true, Long.MAX_VALUE));
        assertEquals(16, ResourceProfile.frameDelay(false, 16));
    }
    @Test public void coverPixelAndCacheBudgetsShrinkWhileNormalBudgetsStayAvailable() {
        assertEquals(ResourceProfile.artSide(false) / 2, ResourceProfile.artSide(true));
        assertEquals(ResourceProfile.artCacheKiB(false) / 4, ResourceProfile.artCacheKiB(true));
        assertTrue(ResourceProfile.maskCacheBytes(true) <= ResourceProfile.maskCacheBytes(false) / 4);
    }
}
