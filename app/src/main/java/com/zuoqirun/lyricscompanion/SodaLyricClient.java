package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Searches Soda Music's web catalog and preserves its native word timing and translation. */
final class SodaLyricClient {
    private static final String API_ROOT = "https://api.qishui.com/luna/pc/";
    private static final String REFERER = "https://www.qishui.com/";
    private static final Pattern TRACK_ID = Pattern.compile("(?:^|\\D)(\\d{6,})(?:$|\\D)");
    private static final Pattern TIMED_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern TIMED_WORD = Pattern.compile("<(\\d+),(\\d+),(\\d+)>");
    private final LyricCache cache;

    SodaLyricClient(Context context) {
        cache = new LyricCache(context, "soda");
    }

    LrcTimeline load(String mediaId, String title, String artist, long durationMs)
            throws Exception {
        String directId = trackId(mediaId);
        if (!directId.isEmpty()) {
            LrcTimeline direct = loadById(directId);
            if (!direct.isEmpty()) return direct;
        }
        String searchedId = search(title, artist, durationMs);
        if (searchedId.isEmpty() || searchedId.equals(directId)) return LrcTimeline.EMPTY;
        return loadById(searchedId);
    }

    private String search(String title, String artist, long durationMs) throws Exception {
        if (title == null || title.trim().isEmpty()) return "";
        String keyword = title.trim();
        if (artist != null && !artist.trim().isEmpty()) keyword += " " + artist.trim();
        JSONObject root = new JSONObject(LyricHttp.get(API_ROOT + "search/track?q="
                + LyricHttp.encode(keyword)
                + "&cursor=0&search_method=input&aid=386088&device_platform=web"
                + "&channel=pc_web", REFERER));
        JSONArray groups = root.optJSONArray("result_groups");
        int bestScore = Integer.MIN_VALUE;
        String bestId = "";
        if (groups == null) return bestId;
        for (int groupIndex = 0; groupIndex < groups.length(); groupIndex++) {
            JSONObject group = groups.optJSONObject(groupIndex);
            JSONArray data = group == null ? null : group.optJSONArray("data");
            if (data == null) continue;
            for (int index = 0; index < data.length(); index++) {
                JSONObject item = data.optJSONObject(index);
                JSONObject entity = item == null ? null : item.optJSONObject("entity");
                JSONObject track = entity == null ? null : entity.optJSONObject("track");
                if (track == null) continue;
                String candidateId = track.optString("id", "");
                int score = NetEaseLyricClient.matchScore(title, artist, durationMs,
                        track.optString("name", ""), artists(track.optJSONArray("artists")),
                        track.optLong("duration", -1L));
                if (!candidateId.isEmpty() && score > bestScore) {
                    bestScore = score;
                    bestId = candidateId;
                }
            }
        }
        return bestScore >= 100 ? bestId : "";
    }

    private LrcTimeline loadById(String trackId) throws Exception {
        String original = cache.read(trackId + "_original");
        String translated = cache.read(trackId + "_translated");
        String enhanced = cache.read(trackId + "_enhanced");
        boolean checked = cache.read(trackId + "_checked") != null;
        if (!checked) {
            JSONObject root = new JSONObject(LyricHttp.get(API_ROOT + "track_v2?track_id="
                    + LyricHttp.encode(trackId)
                    + "&media_type=track&aid=386088&device_platform=web&channel=pc_web",
                    REFERER));
            JSONObject lyric = root.optJSONObject("lyric");
            String content = lyric == null ? "" : lyric.optString("content", "");
            enhanced = toEnhancedTiming(content);
            original = toPlainLrc(content);
            translated = translation(lyric == null ? null : lyric.optJSONObject("translations"));
            cache.write(trackId + "_enhanced", enhanced);
            cache.write(trackId + "_original", original);
            cache.write(trackId + "_translated", translated);
            cache.write(trackId + "_checked", "1");
        }
        return LrcTimeline.parse(value(original), value(translated), value(enhanced));
    }

    static String toEnhancedTiming(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String rawLine : raw.split("\\r?\\n")) {
            Matcher line = TIMED_LINE.matcher(rawLine.trim());
            if (!line.matches()) continue;
            long lineStart = parseLong(line.group(1));
            String content = line.group(3);
            Matcher word = TIMED_WORD.matcher(content);
            StringBuffer converted = new StringBuffer();
            while (word.find()) {
                long absoluteStart = lineStart + parseLong(word.group(1));
                String marker = "(" + absoluteStart + "," + word.group(2)
                        + "," + word.group(3) + ")";
                word.appendReplacement(converted, Matcher.quoteReplacement(marker));
            }
            word.appendTail(converted);
            if (converted.length() == 0) continue;
            result.append('[').append(line.group(1)).append(',').append(line.group(2))
                    .append(']').append(converted).append('\n');
        }
        return result.toString();
    }

    static String toPlainLrc(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String rawLine : raw.split("\\r?\\n")) {
            Matcher line = TIMED_LINE.matcher(rawLine.trim());
            if (!line.matches()) continue;
            long start = parseLong(line.group(1));
            String text = TIMED_WORD.matcher(line.group(3)).replaceAll("").trim();
            if (text.isEmpty()) continue;
            long minutes = start / 60_000L;
            long seconds = start % 60_000L / 1_000L;
            long millis = start % 1_000L;
            result.append(String.format(Locale.ROOT, "[%02d:%02d.%03d]%s%n",
                    minutes, seconds, millis, text));
        }
        return result.toString();
    }

    private static String translation(JSONObject translations) {
        if (translations == null) return "";
        for (String language : new String[]{"cn", "zh-Hans", "zh_CN", "zh"}) {
            String value = translations.optString(language, "");
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static String artists(JSONArray artists) {
        if (artists == null) return "";
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < artists.length(); index++) {
            JSONObject artist = artists.optJSONObject(index);
            String name = artist == null ? "" : artist.optString("name", "").trim();
            if (name.isEmpty()) continue;
            if (result.length() > 0) result.append(", ");
            result.append(name);
        }
        return result.toString();
    }

    static String trackId(String value) {
        Matcher matcher = TRACK_ID.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static long parseLong(String value) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { return 0L; }
    }

    private static String value(String value) { return value == null ? "" : value; }
}
