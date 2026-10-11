package com.zuoqirun.lyricscompanion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Plans an idempotent merge of the selected legacy Folia style without deleting old values. */
final class FoliaStyleMigration {
    private FoliaStyleMigration() { }

    static Map<String, Object> plan(Map<String, ?> slot, Map<String, ?> fallback,
                                    Set<String> styleKeys, boolean secondary,
                                    String legacyStyle, String selectorKey) {
        Map<String, Object> updates = new LinkedHashMap<>();
        if (!FoliaModeCatalog.isLegacyStyle(legacyStyle)) return updates;
        for (String key : styleKeys) {
            String target = AppPreferences.styleScopedKey(key, secondary, "folia");
            if (slot.containsKey(target)) continue;
            String old = AppPreferences.styleScopedKey(key, secondary, legacyStyle);
            Object value = slot.containsKey(old) ? slot.get(old) : fallback.get(old);
            if (value != null) updates.put(target, value);
        }
        String modeKey = AppPreferences.styleScopedKey(AppPreferences.KEY_FOLIA_MODE, secondary, "folia");
        if (!slot.containsKey(modeKey)) updates.put(modeKey, FoliaModeCatalog.modeForStyle(legacyStyle));
        updates.put(selectorKey, "folia");
        return updates;
    }
}
