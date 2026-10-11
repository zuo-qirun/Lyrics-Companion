package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class CoverLyricColorRulesTest {
    @Test public void coverTintsRemainReadableEvenOnTheSameCoverColor() {
        int[] colors = {0xFF000000, 0xFFFFFFFF, 0xFF888888, 0xFFFF0000, 0xFF00FF00,
                0xFF0000FF, 0xFFFFCA66, 0xFF1A2130, 0xFFB8C5D8};
        for (int cover : colors) {
            for (int background : colors) {
                for (boolean current : new boolean[]{true, false}) {
                    int result = CoverLyricColorRules.color(cover, background, current);
                    assertEquals(255, result >>> 24);
                    assertTrue(CoverLyricColorRules.contrast(result, background) >= 4.5);
                }
            }
        }
    }

    @Test public void coloredCoversChangeBothSlotsAndInactiveIsSofter() {
        int background = 0xFF07111F;
        assertNotEquals(CoverLyricColorRules.color(0xFFFF8844, background, true),
                CoverLyricColorRules.color(0xFF4488FF, background, true));
        assertNotEquals(CoverLyricColorRules.color(0xFFFF8844, background, true),
                CoverLyricColorRules.color(0xFFFF8844, background, false));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE));
        assertTrue(AppPreferences.isStyleScopedKey(AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE));
    }
}
