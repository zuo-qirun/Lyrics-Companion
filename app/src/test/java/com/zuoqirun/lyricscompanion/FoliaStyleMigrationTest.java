package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class FoliaStyleMigrationTest {
    private static final Set<String> KEYS = Collections.singleton(AppPreferences.KEY_TEXT_SCALE);

    @Test public void everySelectedLegacyModeAndItsAppearanceArePreserved() {
        for (String mode : FoliaModeCatalog.VALUES) {
            Map<String, Object> old = new HashMap<>();
            old.put("text_scale_main_folia_" + mode, 135);
            Map<String, Object> updates = FoliaStyleMigration.plan(old, Collections.emptyMap(),
                    KEYS, false, "folia_" + mode, AppPreferences.KEY_MAIN_OVERLAY_STYLE);
            assertEquals("folia", updates.get(AppPreferences.KEY_MAIN_OVERLAY_STYLE));
            assertEquals(mode, updates.get("folia_mode_main_folia"));
            assertEquals(135, updates.get("text_scale_main_folia"));
            assertEquals(135, old.get("text_scale_main_folia_" + mode));
        }
    }

    @Test public void secondaryUsesItsOwnFallbackWithoutCopyingMainValues() {
        Map<String, Object> shared = new HashMap<>();
        shared.put("text_scale_main_folia_monet", 110);
        shared.put("text_scale_secondary_folia_monet", 175);
        Map<String, Object> updates = FoliaStyleMigration.plan(Collections.emptyMap(), shared,
                KEYS, true, "folia_monet", AppPreferences.KEY_SECONDARY_OVERLAY_STYLE);
        assertEquals(175, updates.get("text_scale_secondary_folia"));
        assertEquals("monet", updates.get("folia_mode_secondary_folia"));
        assertFalse(updates.containsKey("text_scale_main_folia"));
    }

    @Test public void existingUnifiedSettingsWinAndRepeatingTheMigrationIsSafe() {
        Map<String, Object> old = new HashMap<>();
        old.put("text_scale_main_folia", 125);
        old.put("text_scale_main_folia_claddagh", 160);
        old.put("folia_mode_main_folia", "sonnet");
        Map<String, Object> updates = FoliaStyleMigration.plan(old, Collections.emptyMap(),
                KEYS, false, "folia_claddagh", AppPreferences.KEY_MAIN_OVERLAY_STYLE);
        assertFalse(updates.containsKey("text_scale_main_folia"));
        assertFalse(updates.containsKey("folia_mode_main_folia"));
        old.putAll(updates);
        assertEquals(125, old.get("text_scale_main_folia"));
        assertEquals("sonnet", old.get("folia_mode_main_folia"));
        assertTrue(FoliaStyleMigration.plan(old, Collections.emptyMap(), KEYS, false,
                (String) old.get(AppPreferences.KEY_MAIN_OVERLAY_STYLE),
                AppPreferences.KEY_MAIN_OVERLAY_STYLE).isEmpty());
    }

    @Test public void normalAndUnknownStylesDoNotCreateFoliaPreferences() {
        for (String style : new String[]{"refined", "folia", "folia_original", "folia_unknown", null}) {
            assertTrue(FoliaStyleMigration.plan(Collections.emptyMap(), Collections.emptyMap(),
                    KEYS, false, style, AppPreferences.KEY_MAIN_OVERLAY_STYLE).isEmpty());
        }
    }
}
