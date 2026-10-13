package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class PlaybackPreferenceMigrationTest {
    @Test public void legacyValuesAreCopiedToBothScreensAndExplicitOverridesSurvive() {
        Map<String, Object> original = new LinkedHashMap<>();
        original.put("show_previous_button", false);
        original.put("show_previous_button_secondary", true);
        original.put("show_next_button_main", false);
        Map<String, Boolean> changes = PlaybackPreferenceMigration.changes(original);
        assertEquals(Boolean.FALSE, changes.get("show_previous_button_main"));
        assertFalse(changes.containsKey("show_previous_button_secondary"));
        assertFalse(changes.containsKey("show_next_button_main"));
        assertEquals(Boolean.TRUE, changes.get("show_next_button_secondary"));
        assertEquals(Boolean.TRUE, changes.get("show_play_pause_button_main"));
        assertEquals(Boolean.TRUE, changes.get(PlaybackPreferenceMigration.MARKER));
    }

    @Test public void subsequentMainOrLegacyChangesCannotRewriteTheSecondarySnapshot() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.putAll(PlaybackPreferenceMigration.changes(values));
        values.put("show_previous_button_main", false);
        values.put("show_previous_button", false);
        assertTrue(PlaybackPreferenceMigration.changes(values).isEmpty());
        assertEquals(Boolean.TRUE, values.get("show_previous_button_secondary"));
    }

    @Test public void oldBackupRestoresBothScreensWhileNewBackupPreservesSeparateValues() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("show_next_button", false);
        assertEquals(Boolean.FALSE, PlaybackPreferenceMigration.legacyImportChanges(values).get("show_next_button_secondary"));
        values.put("show_next_button_secondary", true);
        Map<String, Boolean> changes = PlaybackPreferenceMigration.legacyImportChanges(values);
        assertEquals(Boolean.FALSE, changes.get("show_next_button_main"));
        assertFalse(changes.containsKey("show_next_button_secondary"));
    }
}
