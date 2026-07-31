package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class LyricsLayoutConfig {
    static final String COVER = "cover";
    static final String SOURCE = "source";
    static final String TITLE = "title";
    static final String ARTIST = "artist";
    static final String PREVIOUS = "previous";
    static final String CURRENT = "current";
    static final String TRANSLATION = "translation";
    static final String NEXT = "next";
    static final String PROGRESS = "progress";

    private final List<Item> items;

    private LyricsLayoutConfig(List<Item> items) { this.items = items; }

    static LyricsLayoutConfig defaults() {
        List<Item> result = new ArrayList<>();
        result.add(new Item(COVER, "封面", true, 0.05f, 0.18f));
        result.add(new Item(SOURCE, "播放器 / 歌词源", true, 0.05f, 0.06f));
        result.add(new Item(TITLE, "歌曲名", true, 0.05f, 0.69f));
        result.add(new Item(ARTIST, "歌手", true, 0.05f, 0.82f));
        result.add(new Item(PREVIOUS, "上一句", true, 0.48f, 0.18f));
        result.add(new Item(CURRENT, "当前歌词", true, 0.48f, 0.39f));
        result.add(new Item(TRANSLATION, "翻译", true, 0.48f, 0.55f));
        result.add(new Item(NEXT, "下一句", true, 0.48f, 0.72f));
        result.add(new Item(PROGRESS, "播放进度", true, 0.05f, 0.93f));
        return new LyricsLayoutConfig(result);
    }

    static LyricsLayoutConfig load(Context context) { return load(context, false); }

    static LyricsLayoutConfig load(Context context, boolean secondary) {
        String raw = AppPreferences.displayString(context, secondary,
                AppPreferences.KEY_COMPONENT_LAYOUT, "");
        if (raw == null || raw.isEmpty()) return defaults();
        LyricsLayoutConfig defaults = defaults();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) continue;
                Item item = defaults.find(object.optString("id", ""));
                if (item == null) continue;
                item.enabled = object.optBoolean("enabled", item.enabled);
                item.x = clamp((float) object.optDouble("x", item.x));
                item.y = clamp((float) object.optDouble("y", item.y));
            }
            return defaults;
        } catch (Exception ignored) {
            return defaults();
        }
    }

    void save(Context context) { save(context, false); }

    void save(Context context, boolean secondary) {
        JSONArray array = new JSONArray();
        for (Item item : items) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id);
                object.put("enabled", item.enabled);
                object.put("x", item.x);
                object.put("y", item.y);
                array.put(object);
            } catch (Exception ignored) { }
        }
        AppPreferences.putDisplayString(context, secondary, AppPreferences.KEY_COMPONENT_LAYOUT,
                array.toString());
    }

    List<Item> items() { return Collections.unmodifiableList(items); }

    Item find(String id) {
        for (Item item : items) if (item.id.equals(id)) return item;
        return null;
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    static final class Item {
        final String id;
        final String label;
        boolean enabled;
        float x;
        float y;

        Item(String id, String label, boolean enabled, float x, float y) {
            this.id = id;
            this.label = label;
            this.enabled = enabled;
            this.x = x;
            this.y = y;
        }
    }
}
