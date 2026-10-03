package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoverFadeLayoutTest {
    @Test public void coverAndFadeScaleWithPanelAndZeroFadeIsAHardCut() {
        assertEquals(110f, CoverFadeLayout.coverBottom(200f, 55), 0.001f);
        assertEquals(70f, CoverFadeLayout.fadeStart(200f, 55, 20), 0.001f);
        assertEquals(110f, CoverFadeLayout.fadeStart(200f, 55, 0), 0.001f);
        assertEquals(0f, CoverFadeLayout.fadeStart(200f, 40, 40), 0.001f);
    }

    @Test public void capsuleOpacityWorksWithoutACustomColor() {
        assertEquals(0, CoverFadeLayout.capsuleAlpha(true, 0));
        assertEquals(0, CoverFadeLayout.capsuleAlpha(false, -1));
        assertEquals(217, CoverFadeLayout.capsuleAlpha(true, 100));
        assertEquals(166, CoverFadeLayout.capsuleAlpha(false, 100));
        assertEquals(109, CoverFadeLayout.capsuleAlpha(true, 50));
        assertEquals(217, CoverFadeLayout.capsuleAlpha(true, 150));
    }

    @Test public void titleAndSevenTranslatedRowsFitWithinThePurePanel() {
        float size = PureLyricLayout.constrainedCurrentSize(42f, 180f, 7, 7, true, 0.8f, 3);
        float group = size * (1f + 6 * 0.8f + 6 * PureLyricLayout.entryGapRatio(7)
                + (1f + 6 * 0.8f) * (PureLyricLayout.TRANSLATION_SCALE
                + PureLyricLayout.TRANSLATION_GAP_RATIO) + 3 * 1.22f + 0.3f);
        assertTrue(group <= 180.001f);
        assertTrue(size > 0f);
        assertEquals(PureLyricLayout.constrainedCurrentSize(42f, 180f, 7, 7, true, 0.8f),
                PureLyricLayout.constrainedCurrentSize(42f, 180f, 7, 7, true, 0.8f, 0), 0f);
    }
}
