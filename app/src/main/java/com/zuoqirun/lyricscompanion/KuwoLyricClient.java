package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class KuwoLyricClient {
    private static final String REFERER = "https://www.kuwo.cn/";
    private static final Pattern SEARCH_ITEM = Pattern.compile(
            "'ARTIST':'([^']*)'.*?'DURATION':'(\\d+)'.*?"
                    + "'MUSICRID':'MUSIC_(\\d+)'.*?'NAME':'([^']*)'",
            Pattern.DOTALL);
    private final LyricCache cache;

    KuwoLyricClient(Context context) { cache = new LyricCache(context, "kuwo"); }

    LrcTimeline load(String title, String artist, long durationMs) throws Exception {
        String query = LyricHttp.encode(title + (artist == null || artist.isEmpty() ? "" : " " + artist));
        String response = LyricHttp.get("https://search.kuwo.cn/r.s?all=" + query
                + "&ft=music&itemset=web_2013&client=kt&pn=0&rn=10&rformat=json&encoding=utf8",
                REFERER);
        Matcher matcher = SEARCH_ITEM.matcher(response);
        int bestScore = Integer.MIN_VALUE;
        String bestId = "";
        while (matcher.find()) {
            String candidateTitle = decodeLegacy(matcher.group(4));
            String candidateArtist = decodeLegacy(matcher.group(1));
            long candidateDuration = Long.parseLong(matcher.group(2)) * 1000L;
            int score = NetEaseLyricClient.matchScore(title, artist, durationMs,
                    candidateTitle, candidateArtist, candidateDuration);
            if (score > bestScore) {
                bestScore = score;
                bestId = matcher.group(3);
            }
        }
        if (bestScore < 100 || bestId.isEmpty()) return LrcTimeline.EMPTY;
        String cached = cache.read(bestId);
        if (cached != null) return LrcTimeline.parse(cached, "");
        JSONObject root = new JSONObject(LyricHttp.get(
                "https://m.kuwo.cn/newh5/singles/songinfoandlrc?musicId="
                        + LyricHttp.encode(bestId), REFERER));
        JSONObject data = root.optJSONObject("data");
        JSONArray lines = data == null ? null : data.optJSONArray("lrclist");
        if (lines == null) return LrcTimeline.EMPTY;
        StringBuilder lrc = new StringBuilder();
        for (int i = 0; i < lines.length(); i++) {
            JSONObject line = lines.optJSONObject(i);
            if (line == null) continue;
            double seconds = line.optDouble("time", -1d);
            String text = line.optString("lineLyric", "").trim();
            if (seconds < 0d || text.isEmpty()) continue;
            long centiseconds = Math.round(seconds * 100d);
            lrc.append('[').append(String.format(java.util.Locale.ROOT, "%02d:%02d.%02d",
                    centiseconds / 6000L, centiseconds / 100L % 60L, centiseconds % 100L))
                    .append(']').append(text).append('\n');
        }
        String result = lrc.toString();
        cache.write(bestId, result);
        return LrcTimeline.parse(result, "");
    }

    private static String decodeLegacy(String value) {
        return value.replace("&nbsp;", " ").replace("\\\\u0026", "&")
                .replace("\\u0026", "&").trim();
    }
}
