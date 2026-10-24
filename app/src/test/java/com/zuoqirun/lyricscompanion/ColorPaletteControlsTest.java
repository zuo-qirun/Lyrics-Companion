package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/** JVM tests for the color parsing/formatting and preset table behind ColorPaletteControls. */
public class ColorPaletteControlsTest {
    @Test public void parsesSixDigitHexAsOpaque() {
        assertEquals(0xFFFFCA66, ColorPaletteControls.parseColor("#FFCA66"));
        assertEquals(0xFFFFCA66, ColorPaletteControls.parseColor("ffca66"));
        assertEquals(0xFF000000, ColorPaletteControls.parseColor("#000000"));
    }

    @Test public void parsesEightDigitHexAndKeepsAlpha() {
        assertEquals(0x80FFCA66, ColorPaletteControls.parseColor("#80FFCA66"));
        assertEquals(0x80FFCA66, ColorPaletteControls.parseColor("80ffca66"));
        assertEquals(0x00FF0000, ColorPaletteControls.parseColor("#00FF0000"));
        assertEquals(0xFFFFFFFF, ColorPaletteControls.parseColor("#FFFFFFFF"));
    }

    @Test public void parsesRgbTriplets() {
        assertEquals(0xFFFF8000, ColorPaletteControls.parseColor("255, 128, 0"));
        assertEquals(0xFF000000, ColorPaletteControls.parseColor("0,0,0"));
        assertEquals(0xFF00FF00, ColorPaletteControls.parseColor(" 0 , 255 , 0 "));
    }

    @Test public void malformedInputStillReturnsZero() {
        assertEquals(0, ColorPaletteControls.parseColor(null));
        assertEquals(0, ColorPaletteControls.parseColor(""));
        assertEquals(0, ColorPaletteControls.parseColor("#12345"));
        assertEquals(0, ColorPaletteControls.parseColor("#1234567"));
        assertEquals(0, ColorPaletteControls.parseColor("#GGGGGG"));
        assertEquals(0, ColorPaletteControls.parseColor("256, 0, 0"));
        assertEquals(0, ColorPaletteControls.parseColor("1, 2"));
        assertEquals(0, ColorPaletteControls.parseColor("1, 2, 3, 4"));
        assertEquals(0, ColorPaletteControls.parseColor("1, -2, 3"));
    }

    @Test public void transparentBlackIsEightDigitButParsesToZero() {
        assertEquals(0, ColorPaletteControls.parseColor("#00000000"));
        assertTrue(ColorPaletteControls.isValidAlphaColorText("#00000000"));
        assertTrue(ColorPaletteControls.isValidAlphaColorText("00000000"));
        assertFalse(ColorPaletteControls.isValidAlphaColorText("#000000"));
        assertFalse(ColorPaletteControls.isValidAlphaColorText(null));
    }

    @Test public void formatsOpaqueAndTranslucentColors() {
        assertEquals("#FFCA66", ColorPaletteControls.formatColor(0xFFFFCA66));
        assertEquals("#000000", ColorPaletteControls.formatColor(0xFF000000));
        assertEquals("#80FFCA66", ColorPaletteControls.formatColor(0x80FFCA66));
        assertEquals("#01000000", ColorPaletteControls.formatColor(0x01000000));
    }

    @Test public void formatAndParseRoundTrip() {
        assertEquals("#80FFCA66",
                ColorPaletteControls.formatColor(ColorPaletteControls.parseColor("#80FFCA66")));
        assertEquals(0xFFFFCA66,
                ColorPaletteControls.parseColor(ColorPaletteControls.formatColor(0xFFFFCA66)));
        assertEquals(0xFF000000,
                ColorPaletteControls.parseColor(ColorPaletteControls.formatColor(0xFF000000)));
    }

    @Test public void alphaSliderNeverStoresZero() {
        assertEquals(1, ColorPaletteControls.storedAlpha(0));
        assertEquals(1, ColorPaletteControls.storedAlpha(1));
        assertEquals(128, ColorPaletteControls.storedAlpha(128));
        assertEquals(255, ColorPaletteControls.storedAlpha(255));
        assertEquals(255, ColorPaletteControls.storedAlpha(999));
        assertEquals(1, ColorPaletteControls.storedAlpha(-5));
    }

    @Test public void storableColorKeepsAlphaAndClampsChannels() {
        assertEquals(0x80FFCA66, ColorPaletteControls.storableColor(128, 255, 202, 102));
        assertEquals(0x01000000, ColorPaletteControls.storableColor(0, 0, 0, 0));
        assertEquals(0xFFFFFF00, ColorPaletteControls.storableColor(255, 300, 255, -1));
        assertEquals(0x01FF8000, ColorPaletteControls.storableColor(0, 255, 128, 0));
    }

    @Test public void alphaPercentRoundsAndClamps() {
        assertEquals(100, ColorPaletteControls.alphaPercent(255));
        assertEquals(50, ColorPaletteControls.alphaPercent(128));
        assertEquals(0, ColorPaletteControls.alphaPercent(0));
        assertEquals(0, ColorPaletteControls.alphaPercent(1));
        assertEquals(100, ColorPaletteControls.alphaPercent(300));
        assertEquals(0, ColorPaletteControls.alphaPercent(-1));
    }

    @Test public void colorLabelMentionsAlphaOnlyWhenTranslucent() {
        assertEquals("当前：#FFCA66", ColorPaletteControls.colorLabel(0xFFFFCA66));
        assertEquals("当前：#80FFCA66（不透明度 50%）",
                ColorPaletteControls.colorLabel(0x80FFCA66));
        assertEquals("128（50%）", ColorPaletteControls.alphaLabel(128));
    }

    @Test public void presetSchemesAreCompleteAndDistinct() {
        ColorPaletteControls.Scheme[] schemes = ColorPaletteControls.schemes();
        assertEquals(5, schemes.length);
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (ColorPaletteControls.Scheme scheme : schemes) {
            assertNotNull(scheme);
            assertTrue("scheme id must be a simple lowercase key: " + scheme.id,
                    scheme.id.matches("[a-z]+"));
            assertTrue("duplicate scheme id: " + scheme.id, ids.add(scheme.id));
            assertFalse("scheme name must not be empty", scheme.name.isEmpty());
            assertTrue("duplicate scheme name: " + scheme.name, names.add(scheme.name));
            assertOpaque("current", scheme.current);
            assertOpaque("inactive", scheme.inactive);
            assertOpaque("outline", scheme.outline);
            assertOpaque("backgroundLight", scheme.backgroundLight);
            assertOpaque("backgroundDark", scheme.backgroundDark);
            assertOpaque("spectrum", scheme.spectrum);
        }
    }

    @Test public void presetSchemeTableIsDefensiveCopy() {
        ColorPaletteControls.Scheme[] first = ColorPaletteControls.schemes();
        first[0] = null;
        assertNotNull(ColorPaletteControls.schemes()[0]);
    }

    @Test public void presetSchemeLookupNormalizesStoredName() {
        ColorPaletteControls.Scheme warm = ColorPaletteControls.schemeFor(" WARM ");
        assertNotNull(warm);
        assertEquals("warm", warm.id);
        assertEquals("暖阳", warm.name);
        assertEquals("deep", ColorPaletteControls.schemeFor("Deep").id);
        assertNull(ColorPaletteControls.schemeFor(null));
        assertNull(ColorPaletteControls.schemeFor(""));
        assertNull(ColorPaletteControls.schemeFor("   "));
        assertNull(ColorPaletteControls.schemeFor("unknown"));
    }

    private static void assertOpaque(String field, int color) {
        assertTrue(field + " must be opaque: " + Integer.toHexString(color),
                (color >>> 24) == 0xFF);
    }
}
