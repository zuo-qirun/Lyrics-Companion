package com.zuoqirun.lyricscompanion;

import java.util.LinkedHashMap;
import java.util.Map;

/** Snapshot legacy shared buttons once, preserving both screens' existing overrides. */
final class PlaybackPreferenceMigration {
    static final String MARKER = "playback_buttons_split_v1";
    static final String[] KEYS = {"show_previous_button", "show_play_pause_button", "show_next_button"};
    private PlaybackPreferenceMigration() { }

    static Map<String, Boolean> changes(Map<String, ?> values) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(values.get(MARKER))) return result;
        for (String key : KEYS) {
            boolean legacy = !(values.get(key) instanceof Boolean) || Boolean.TRUE.equals(values.get(key));
            for (String suffix : new String[]{"_main", "_secondary"}) {
                if (!values.containsKey(key + suffix)) result.put(key + suffix, legacy);
            }
        }
        result.put(MARKER, true);
        return result;
    }

    static Map<String, Boolean> legacyImportChanges(Map<String, Boolean> imported) {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String key : KEYS) if (imported.containsKey(key)) {
            for (String suffix : new String[]{"_main", "_secondary"}) {
                if (!imported.containsKey(key + suffix)) result.put(key + suffix, imported.get(key));
            }
        }
        return result;
    }
}
