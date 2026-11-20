package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户手动修正的匹配结果。
 *
 * <p>键为播放器包名 + 原始标题 + 原始歌手，值为用户选择的标题/歌手；最多保留
 * {@link #MAX_ENTRIES} 条，最近使用（写入）的排在最前。只影响歌词匹配，
 * 界面显示仍使用播放器上报的原始元数据。</p>
 */
final class MatchCorrectionStore {

    static final String KEY = "match_corrections";
    static final int MAX_ENTRIES = 200;

    private static final Object LOCK = new Object();
    private static List<Entry> cache;
    private static boolean loaded;

    private MatchCorrectionStore() {
    }

    /** 一次已保存的修正。 */
    static final class Correction {

        final String title;
        final String artist;

        Correction(String title, String artist) {
            this.title = title == null ? "" : title;
            this.artist = artist == null ? "" : artist;
        }
    }

    /** 查找匹配的修正；不存在返回 null。 */
    static Correction find(Context context, String sourcePackage, String rawTitle, String rawArtist) {
        String packageName = safe(sourcePackage);
        String title = safe(rawTitle);
        String artist = safe(rawArtist);
        if (title.isEmpty()) {
            return null;
        }
        synchronized (LOCK) {
            for (Entry entry : entriesLocked(context)) {
                if (entry.matches(packageName, title, artist)) {
                    return new Correction(entry.fixedTitle, entry.fixedArtist);
                }
            }
        }
        return null;
    }

    /** 记录一次修正；相同键覆盖并移到最前。 */
    static void remember(Context context, String sourcePackage, String rawTitle, String rawArtist,
                         String fixedTitle, String fixedArtist) {
        String packageName = safe(sourcePackage);
        String title = safe(rawTitle);
        String artist = safe(rawArtist);
        String correctedTitle = safe(fixedTitle);
        String correctedArtist = safe(fixedArtist);
        if (title.isEmpty() || correctedTitle.isEmpty()) {
            return;
        }
        if (title.equals(correctedTitle) && artist.equals(correctedArtist)) {
            return;
        }
        synchronized (LOCK) {
            List<Entry> entries = new ArrayList<Entry>(entriesLocked(context));
            removeMatching(entries, packageName, title, artist);
            entries.add(0, new Entry(packageName, title, artist, correctedTitle, correctedArtist));
            while (entries.size() > MAX_ENTRIES) {
                entries.remove(entries.size() - 1);
            }
            cache = entries;
            loaded = true;
            persistLocked(context);
        }
    }

    /** 清空全部修正。 */
    static void clearAll(Context context) {
        synchronized (LOCK) {
            cache = new ArrayList<Entry>();
            loaded = true;
            persistLocked(context);
        }
    }

    /** 当前保存的修正条数。 */
    static int count(Context context) {
        synchronized (LOCK) {
            return entriesLocked(context).size();
        }
    }

    // ---- 内部实现 ----

    private static void removeMatching(List<Entry> entries, String packageName, String title, String artist) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i).matches(packageName, title, artist)) {
                entries.remove(i);
            }
        }
    }

    private static List<Entry> entriesLocked(Context context) {
        if (!loaded) {
            cache = read(context);
            loaded = true;
        }
        return cache;
    }

    private static List<Entry> read(Context context) {
        List<Entry> entries = new ArrayList<Entry>();
        if (context == null) {
            return entries;
        }
        String json = prefs(context).getString(KEY, "");
        if (json == null || json.trim().isEmpty()) {
            return entries;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length() && entries.size() < MAX_ENTRIES; i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                Entry entry = Entry.fromJson(object);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (JSONException e) {
            return new ArrayList<Entry>();
        }
        return entries;
    }

    private static void persistLocked(Context context) {
        if (context == null) {
            return;
        }
        JSONArray array = new JSONArray();
        for (Entry entry : cache) {
            array.put(entry.toJson());
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return AppPreferences.get(context);
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class Entry {

        final String packageName;
        final String rawTitle;
        final String rawArtist;
        final String fixedTitle;
        final String fixedArtist;

        Entry(String packageName, String rawTitle, String rawArtist, String fixedTitle, String fixedArtist) {
            this.packageName = packageName;
            this.rawTitle = rawTitle;
            this.rawArtist = rawArtist;
            this.fixedTitle = fixedTitle;
            this.fixedArtist = fixedArtist;
        }

        boolean matches(String packageName, String rawTitle, String rawArtist) {
            return this.packageName.equals(packageName)
                    && this.rawTitle.equals(rawTitle)
                    && this.rawArtist.equals(rawArtist);
        }

        JSONObject toJson() {
            JSONObject object = new JSONObject();
            try {
                object.put("p", packageName);
                object.put("t", rawTitle);
                object.put("a", rawArtist);
                object.put("ft", fixedTitle);
                object.put("fa", fixedArtist);
            } catch (JSONException e) {
                // JSONObject.put(String, Object) 对字符串不会失败，这里保持静默。
            }
            return object;
        }

        static Entry fromJson(JSONObject object) {
            String fixedTitle = object.optString("ft", "").trim();
            if (fixedTitle.isEmpty()) {
                return null;
            }
            return new Entry(object.optString("p", "").trim(),
                    object.optString("t", "").trim(),
                    object.optString("a", "").trim(),
                    fixedTitle,
                    object.optString("fa", "").trim());
        }
    }
}
