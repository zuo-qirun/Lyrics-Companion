package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Covers the shared color-slot resolution and outline width rules used by every style. */
public class LyricColorSlotTest {
    @Test public void flatSlotAppliesWhenThemeFollowingOff() {
        assertEquals(0xFF112233, AppPreferences.resolveThemedSlotColor(
                false, true, 0xFF112233, 0xFF000000, 0xFFFFFFFF));
    }

    @Test public void environmentPairWinsWhileThemeFollowingOn() {
        assertEquals(0xFF000000, AppPreferences.resolveThemedSlotColor(
                true, true, 0xFF112233, 0xFF000000, 0xFFFFFFFF));
        assertEquals(0xFFFFFFFF, AppPreferences.resolveThemedSlotColor(
                true, false, 0xFF112233, 0xFF000000, 0xFFFFFFFF));
    }

    @Test public void unsetPairFallsBackToFlatCustom() {
        // Users who only set the flat slot keep it even with theme tracking on (0 = unset).
        assertEquals(0xFF112233, AppPreferences.resolveThemedSlotColor(
                true, true, 0xFF112233, 0, 0));
        assertEquals(0xFF112233, AppPreferences.resolveThemedSlotColor(
                true, false, 0xFF112233, 0xFF000000, 0));
    }

    @Test public void zeroFlatFallsThroughToCallerDefault() {
        assertEquals(0, AppPreferences.resolveThemedSlotColor(false, true, 0, 0, 0));
        assertEquals(0, AppPreferences.resolveThemedSlotColor(true, true, 0, 0, 0));
    }

    @Test public void outlineWidthScalesWithFontSizeAndClamps() {
        // Default 8% of a 40px glyph.
        assertEquals(3.2f, LyricsPanelView.outlineStrokeWidth(40f, 8), 0.001f);
        // Never thinner than one pixel.
        assertEquals(1f, LyricsPanelView.outlineStrokeWidth(10f, 1), 0.001f);
        // Extreme percents clamp into the sane band.
        assertEquals(8f, LyricsPanelView.outlineStrokeWidth(20f, 40), 0.001f);
        assertEquals(8f, LyricsPanelView.outlineStrokeWidth(20f, 400), 0.001f);
        assertEquals(1f, LyricsPanelView.outlineStrokeWidth(20f, 0), 0.001f);
    }
}
