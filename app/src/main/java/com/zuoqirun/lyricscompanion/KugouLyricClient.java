package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

final class KugouLyricClient {
    private static final String REFERER = "https://www.kugou.com/";
    private final LyricCache cache;

    KugouLyricClient(Context context) { cache = new LyricCache(context, "kugou"); }

    LrcTimeline load(String title, String artist, long durationMs) throws Exception {
        String query = LyricHttp.encode(title + (artist == null || artist.isEmpty() ? "" : " " + artist));
        JSONObject root = new JSONObject(LyricHttp.get(
                "https://songsearch.kugou.com/song_search_v2?keyword=" + query
                        + "&page=1&pagesize=10&userid=-1&clientver=&platform=WebFilter", REFERER));
        JSONObject data = root.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("lists");
        if (list == null) return LrcTimeline.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        String bestHash = "";
        for (int i = 0; i < list.length(); i++) {
            JSONObject candidate = list.optJSONObject(i);
            if (candidate == null) continue;
            int score = NetEaseLyricClient.matchScore(title, artist, durationMs,
                    candidate.optString("SongName", ""), candidate.optString("SingerName", ""),
                    candidate.optLong("Duration", -1L) * 1000L);
            if (score > bestScore) {
                bestScore = score;
                bestHash = candidate.optString("FileHash", "");
            }
        }
        if (bestScore < 100 || bestHash.isEmpty()) return LrcTimeline.EMPTY;
        String cached = cache.read(bestHash);
        if (cached != null) return LrcTimeline.parse(cached, "");
        JSONObject search = new JSONObject(LyricHttp.get(
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash="
                        + LyricHttp.encode(bestHash), REFERER));
        JSONArray candidates = search.optJSONArray("candidates");
        JSONObject lyricCandidate = candidates == null ? null : candidates.optJSONObject(0);
        if (lyricCandidate == null) return LrcTimeline.EMPTY;
        JSONObject download = new JSONObject(LyricHttp.get(
                "https://lyrics.kugou.com/download?ver=1&client=pc&id="
                        + LyricHttp.encode(lyricCandidate.optString("id", ""))
                        + "&accesskey=" + LyricHttp.encode(lyricCandidate.optString("accesskey", ""))
                        + "&fmt=lrc&charset=utf8", REFERER));
        String encoded = download.optString("content", "");
        if (encoded.isEmpty()) return LrcTimeline.EMPTY;
        String lrc = new String(Base64.decode(encoded, Base64.DEFAULT), StandardCharsets.UTF_8)
                .replace("\uFEFF", "");
        cache.write(bestHash, lrc);
        return LrcTimeline.parse(lrc, "");
    }
}
