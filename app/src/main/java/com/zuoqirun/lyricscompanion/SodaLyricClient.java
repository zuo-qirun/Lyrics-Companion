package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Loads Soda Music's public share data and preserves its native word timing. */
final class SodaLyricClient {
    private static final String API_ROOT = "https://api.qishui.com/luna/pc/";
    private static final String MOBILE_SHARE_ROOT = "https://www.qishui.com/share/track?track_id=";
    private static final String CAR_SHARE_ROOT =
            "https://music.douyin.com/qishui/share/track?track_id=";
    private static final String MOBILE_PACKAGE = "com.luna.music";
    private static final String CAR_PACKAGE = "com.luna.music.car";
    private static final String REFERER = "https://www.qishui.com/";
    private static final String ROUTER_DATA = "_ROUTER_DATA";
    private static final Pattern TRACK_ID = Pattern.compile("(?:^|\\D)(\\d{6,})(?:$|\\D)");
    private static final Pattern TIMED_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern TIMED_WORD = Pattern.compile("<(\\d+),(\\d+),(\\d+)>");
    private final LyricCache cache;

    SodaLyricClient(Context context) {
        cache = new LyricCache(context, "soda");
    }

    LrcTimeline load(String sourcePackage, String mediaId, String title, String artist,
                     long durationMs)
            throws Exception {
        String directId = trackId(mediaId);
        if (!directId.isEmpty()) {
            LrcTimeline direct = loadById(sourcePackage, directId);
            if (!direct.isEmpty()) return direct;
        }
        String searchedId = search(title, artist, durationMs);
        if (searchedId.isEmpty() || searchedId.equals(directId)) return LrcTimeline.EMPTY;
        return loadById(sourcePackage, searchedId);
    }

    private String search(String title, String artist, long durationMs) throws Exception {
        if (title == null || title.trim().isEmpty()) return "";
        String keyword = title.trim();
        if (artist != null && !artist.trim().isEmpty()) keyword += " " + artist.trim();
        String response = LyricHttp.get(API_ROOT + "search/track?q="
                + LyricHttp.encode(keyword)
                + "&cursor=0&search_method=input&aid=386088&device_platform=web"
                + "&channel=pc_web", REFERER);
        if (response == null || response.trim().isEmpty()) return "";
        JSONObject root = new JSONObject(response);
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

    private LrcTimeline loadById(String sourcePackage, String trackId) throws Exception {
        String cachePrefix = trackId + "_share_v1";
        String original = cache.read(cachePrefix + "_original");
        String translated = cache.read(cachePrefix + "_translated");
        String enhanced = cache.read(cachePrefix + "_enhanced");
        boolean checked = cache.read(cachePrefix + "_checked") != null;
        if (!checked) {
            String address = shareAddress(sourcePackage, trackId);
            ShareLyrics parsed = parseSharePage(LyricHttp.get(address, shareReferer(sourcePackage)));
            enhanced = parsed.enhanced;
            original = parsed.original;
            translated = parsed.translated;
            cache.write(cachePrefix + "_enhanced", enhanced);
            cache.write(cachePrefix + "_original", original);
            cache.write(cachePrefix + "_translated", translated);
            cache.write(cachePrefix + "_checked", "1");
        }
        return LrcTimeline.parse(value(original), value(translated), value(enhanced));
    }

    static String shareAddress(String sourcePackage, String trackId) throws Exception {
        String root = CAR_PACKAGE.equals(sourcePackage) ? CAR_SHARE_ROOT : MOBILE_SHARE_ROOT;
        return root + LyricHttp.encode(trackId);
    }

    private static String shareReferer(String sourcePackage) {
        return CAR_PACKAGE.equals(sourcePackage)
                ? "https://music.douyin.com/" : REFERER;
    }

    static ShareLyrics parseSharePage(String html) throws Exception {
        JSONObject router = routerData(html);
        JSONObject loader = router.optJSONObject("loaderData");
        JSONObject page = loader == null ? null : loader.optJSONObject("track_page");
        JSONObject audio = page == null ? null : page.optJSONObject("audioWithLyricsOption");
        JSONObject lyrics = audio == null ? null : audio.optJSONObject("lyrics");
        JSONArray sentences = lyrics == null ? null : lyrics.optJSONArray("sentences");
        if (sentences == null) return ShareLyrics.EMPTY;

        StringBuilder original = new StringBuilder();
        StringBuilder translated = new StringBuilder();
        StringBuilder enhanced = new StringBuilder();
        for (int index = 0; index < sentences.length(); index++) {
            JSONObject sentence = sentences.optJSONObject(index);
            if (sentence == null) continue;
            long rawStart = sentence.optLong("startMs", -1L);
            if (rawStart < 0L) continue;
            long start = rawStart;
            long end = Math.max(start, sentence.optLong("endMs", start));
            JSONArray words = sentence.optJSONArray("words");
            String text = sentence.optString("text", "").trim();
            if (text.isEmpty()) text = wordsText(words).trim();
            if (text.isEmpty()) continue;

            appendLrc(original, start, text);
            String translatedText = firstNonEmpty(sentence.optString("translation", ""),
                    sentence.optString("translatedText", ""));
            if (!translatedText.isEmpty()) appendLrc(translated, start, translatedText);

            String wordTiming = enhancedWords(words, start, end);
            if (!wordTiming.isEmpty()) {
                enhanced.append('[').append(start).append(',').append(Math.max(0L, end - start))
                        .append(']').append(wordTiming).append('\n');
            }
        }
        return new ShareLyrics(original.toString(), translated.toString(), enhanced.toString());
    }

    private static JSONObject routerData(String html) throws Exception {
        if (html == null || html.trim().isEmpty()) {
            throw new IllegalStateException("Soda share page returned an empty response");
        }
        int marker = html.indexOf(ROUTER_DATA);
        int equals = marker < 0 ? -1 : html.indexOf('=', marker + ROUTER_DATA.length());
        int start = equals < 0 ? -1 : html.indexOf('{', equals + 1);
        if (start < 0) throw new IllegalStateException("Soda share data was not found");
        int end = jsonObjectEnd(html, start);
        if (end < 0) throw new IllegalStateException("Soda share data was incomplete");
        return new JSONObject(html.substring(start, end + 1));
    }

    private static int jsonObjectEnd(String value, int start) {
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < value.length(); index++) {
            char current = value.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == '"') quoted = false;
                continue;
            }
            if (current == '"') quoted = true;
            else if (current == '{') depth++;
            else if (current == '}' && --depth == 0) return index;
        }
        return -1;
    }

    private static String enhancedWords(JSONArray words, long lineStart, long lineEnd) {
        if (words == null || words.length() == 0) return "";
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < words.length(); index++) {
            JSONObject word = words.optJSONObject(index);
            if (word == null) continue;
            String text = word.optString("text", "");
            if (text.isEmpty()) continue;
            long start = Math.max(lineStart, word.optLong("startMs", lineStart));
            long end = Math.max(start, word.optLong("endMs", lineEnd));
            result.append('(').append(start).append(',').append(Math.max(0L, end - start))
                    .append(",0)").append(text);
        }
        return result.toString();
    }

    private static String wordsText(JSONArray words) {
        if (words == null) return "";
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < words.length(); index++) {
            JSONObject word = words.optJSONObject(index);
            if (word != null) result.append(word.optString("text", ""));
        }
        return result.toString();
    }

    private static void appendLrc(StringBuilder output, long start, String text) {
        long minutes = start / 60_000L;
        long seconds = start % 60_000L / 1_000L;
        long millis = start % 1_000L;
        output.append(String.format(Locale.ROOT, "[%02d:%02d.%03d]%s%n",
                minutes, seconds, millis, text));
    }

    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim().isEmpty()) return first.trim();
        return second == null ? "" : second.trim();
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

    static final class ShareLyrics {
        static final ShareLyrics EMPTY = new ShareLyrics("", "", "");
        final String original;
        final String translated;
        final String enhanced;

        ShareLyrics(String original, String translated, String enhanced) {
            this.original = value(original);
            this.translated = value(translated);
            this.enhanced = value(enhanced);
        }
    }
}
