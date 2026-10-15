package com.zuoqirun.lyricscompanion;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Builds reviewable writes without mutating preferences or materializing inherited defaults. */
final class SettingsCopyPlan {
    private SettingsCopyPlan() {}

    static Map<String, Object> create(Map<String, ?> source, boolean sourceSecondary,
                                     String sourceStyle, boolean targetSecondary, String targetStyle,
                                     Set<SettingsCatalog.Category> categories) {
        Map<String, Object> writes = new LinkedHashMap<>();
        for (String key : AppPreferences.styleScopedKeys()) {
            if (key.endsWith("_width_dp") || key.endsWith("_height_dp")) continue;
            if (!categories.contains(SettingsCatalog.copyCategory(key))
                    || !SettingsCatalog.unsupportedReason(key, targetStyle).isEmpty()) continue;
            String sourceKey = AppPreferences.styleScopedKey(key, sourceSecondary, sourceStyle);
            Object value = source.get(sourceKey);
            if (value == null) continue;
            writes.put(AppPreferences.styleScopedKey(key, targetSecondary, targetStyle), value);
        }
        return writes;
    }
}
