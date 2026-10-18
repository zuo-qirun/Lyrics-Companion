package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.*;

public class FoliaStyleCatalogTest {
    @Test public void existingIdsStayLightweightAndOriginalHasItsOwnId() {
        assertEquals("低配版folia", FoliaModeCatalog.labelForStyle("folia"));
        for (String mode : FoliaModeCatalog.VALUES) {
            String legacy = "folia_" + mode;
            assertEquals("folia", FoliaModeCatalog.normalizeStyle(legacy));
            assertEquals("低配版folia", FoliaModeCatalog.labelForStyle(legacy));
        }
        assertTrue(FoliaModeCatalog.isStyle("folia_original"));
        assertFalse(FoliaModeCatalog.isLegacyStyle("folia_original"));
        assertEquals("folia_original", FoliaModeCatalog.normalizeStyle("folia_original"));
        assertEquals("还原版folia", FoliaModeCatalog.labelForStyle("folia_original"));
        assertArrayEquals(new String[]{"经典", "低配版folia", "还原版folia"},
                FoliaModeCatalog.append(new String[]{"经典"}, true));
    }

    @Test public void bothFoliaVersionsKeepMainAndSecondarySettingsShortcuts() {
        for (String style : new String[]{"folia", "folia_original"}) {
            assertTrue(FoliaModeCatalog.settingsVisible(0, style));
            assertTrue(FoliaModeCatalog.settingsVisible(1, style));
            assertFalse(FoliaModeCatalog.settingsVisible(-1, style));
        }
    }

    @Test public void originalOffersIndependentNativeAnimationAndPlaybackControls() {
        for (String key : new String[]{AppPreferences.KEY_FOLIA_MODE, AppPreferences.KEY_FOLIA_FPS,
                AppPreferences.KEY_FOLIA_RANDOM, AppPreferences.KEY_FOLIA_RANDOM_POOL,
                AppPreferences.KEY_FOLIA_PANEL_WIDTH_DP, AppPreferences.KEY_FOLIA_PANEL_HEIGHT_DP,
                AppPreferences.KEY_REFINED_SHOW_TRANSLATION, AppPreferences.KEY_SHOW_PROGRESS,
                AppPreferences.KEY_SPECTRUM_ENABLED, AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM}) {
            assertEquals(key, "", SettingsCatalog.unsupportedReason(key, "folia_original"));
        }
        for (String key : new String[]{AppPreferences.KEY_FOLIA_FOCUS, AppPreferences.KEY_FOLIA_RADIUS,
                AppPreferences.KEY_FOLIA_TILT, AppPreferences.KEY_FOLIA_AXIS,
                AppPreferences.KEY_FOLIA_TRACKING, AppPreferences.KEY_FOLIA_STRENGTH,
                AppPreferences.KEY_FOLIA_SHOW_NEXT, AppPreferences.KEY_FOLIA_NEXT_SCALE}) {
            assertEquals(key, "", SettingsCatalog.unsupportedReason(key, "folia"));
            assertEquals(key, "", SettingsCatalog.unsupportedReason(key, "folia_original"));
        }
    }

    @Test public void originalKeepsNativeTypographyColorAndBackgroundControls() {
        for (String key : new String[]{AppPreferences.KEY_TEXT_SCALE, AppPreferences.KEY_OPACITY,
                AppPreferences.KEY_LYRIC_COLOR, AppPreferences.KEY_STYLE_BLUR,
                AppPreferences.KEY_REFINED_BACKGROUND_TYPE, AppPreferences.KEY_STARFIELD_FPS,
                AppPreferences.KEY_SPECTRUM_STYLE}) {
            assertEquals(key, "", SettingsCatalog.unsupportedReason(key, "folia_original"));
        }
        assertFalse(SettingsCatalog.unsupportedReason(
                AppPreferences.KEY_STYLE_LYRIC_LINES, "folia_original").isEmpty());
    }
}
