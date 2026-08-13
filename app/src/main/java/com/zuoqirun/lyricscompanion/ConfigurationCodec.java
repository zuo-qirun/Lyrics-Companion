package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Typed preference export with an explicit privacy boundary. */
final class ConfigurationCodec {
    private ConfigurationCodec() { }

    static JSONObject exportConfiguration(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("schemaVersion", 1);
        JSONObject settings = new JSONObject();
        for (Map.Entry<String, ?> entry : AppPreferences.get(context).getAll().entrySet()) {
            if (!shareableKey(entry.getKey())) continue;
            JSONObject encoded = encode(entry.getValue());
            if (encoded != null) settings.put(entry.getKey(), encoded);
        }
        root.put("settings", settings);
        return root;
    }

    static int importConfiguration(Context context, JSONObject root) throws Exception {
        if (root == null || root.optInt("schemaVersion", 0) != 1) {
            throw new IllegalArgumentException("不支持的配置版本");
        }
        JSONObject settings = root.optJSONObject("settings");
        if (settings == null) throw new IllegalArgumentException("配置内容为空");
        SharedPreferences.Editor editor = AppPreferences.get(context).edit();
        int imported = 0;
        java.util.Iterator<String> keys = settings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!shareableKey(key)) continue;
            JSONObject encoded = settings.optJSONObject(key);
            if (encoded == null) continue;
            String type = encoded.optString("type");
            switch (type) {
                case "boolean": editor.putBoolean(key, encoded.optBoolean("value")); break;
                case "int": editor.putInt(key, encoded.optInt("value")); break;
                case "long": editor.putLong(key, encoded.optLong("value")); break;
                case "float": editor.putFloat(key, (float) encoded.optDouble("value")); break;
                case "string": editor.putString(key, encoded.optString("value")); break;
                case "set":
                    JSONArray values = encoded.optJSONArray("value");
                    if (values == null) continue;
                    Set<String> set = new HashSet<>();
                    for (int index = 0; index < values.length(); index++) {
                        String value = values.optString(index, "").trim();
                        if (!value.isEmpty()) set.add(value);
                    }
                    editor.putStringSet(key, set);
                    break;
                default: continue;
            }
            imported++;
        }
        editor.apply();
        return imported;
    }

    static boolean shareableKey(String key) {
        if (key == null || key.isEmpty()) return false;
        return !key.startsWith("community_")
                && !key.startsWith("feedback_")
                && !key.startsWith("faq_")
                && !key.startsWith("diagnostic_")
                && !key.startsWith("observed_")
                && !key.startsWith("player_package_")
                && !key.startsWith("hide_overlays_in_apps")
                && !key.equals(AppPreferences.KEY_LOCAL_LYRIC_DIRECTORY_URI)
                && !key.equals(AppPreferences.KEY_CUSTOM_FONT_FILE)
                && !key.equals(AppPreferences.KEY_LAUNCH_OVERLAY_LAST_AT)
                && !key.equals(AppPreferences.KEY_SERVICE_STOPPED_BY_USER)
                && !key.equals(AppPreferences.KEY_SAFETY_NOTICE_SEEN);
    }

    private static JSONObject encode(Object value) throws Exception {
        JSONObject encoded = new JSONObject();
        if (value instanceof Boolean) { encoded.put("type", "boolean"); encoded.put("value", value); }
        else if (value instanceof Integer) { encoded.put("type", "int"); encoded.put("value", value); }
        else if (value instanceof Long) { encoded.put("type", "long"); encoded.put("value", value); }
        else if (value instanceof Float) { encoded.put("type", "float"); encoded.put("value", value); }
        else if (value instanceof String) { encoded.put("type", "string"); encoded.put("value", value); }
        else if (value instanceof Set) {
            encoded.put("type", "set");
            JSONArray array = new JSONArray();
            for (Object item : (Set<?>) value) if (item instanceof String) array.put(item);
            encoded.put("value", array);
        } else return null;
        return encoded;
    }
}
