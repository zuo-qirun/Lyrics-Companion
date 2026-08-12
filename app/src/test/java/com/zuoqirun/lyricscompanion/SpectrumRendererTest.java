package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SpectrumRendererTest {
    @Test public void rainbowUsesDifferentHslColorsAcrossBands() {
        int first = SpectrumRenderer.colorAt(0, 12, "rainbow", 0xFF123456, 0, null);
        int middle = SpectrumRenderer.colorAt(6, 12, "rainbow", 0xFF123456, 0, null);
        org.junit.Assert.assertNotEquals(first, middle);
    }

    @Test public void lyricAndCustomModesResolveExpectedColor() {
        assertEquals(0xFF123456,
                SpectrumRenderer.colorAt(2, 12, "lyric", 0xFF123456, 0xFFABCDEF, null));
        assertEquals(0xFFABCDEF,
                SpectrumRenderer.colorAt(2, 12, "custom", 0xFF123456, 0xFFABCDEF, null));
    }

    @Test public void artworkModeUsesPaletteBuckets() {
        int[] palette = {0xFF111111, 0xFF222222, 0xFF333333};
        assertEquals(0xFF111111,
                SpectrumRenderer.colorAt(0, 12, "artwork", 0, 0, palette));
        assertEquals(0xFF333333,
                SpectrumRenderer.colorAt(11, 12, "artwork", 0, 0, palette));
    }
}
