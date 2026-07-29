package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

final class QQMusicLyricClient {
    private static final String REFERER = "https://y.qq.com/";
    private static final String QRC_URL =
            "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg";
    private final LyricCache cache;

    QQMusicLyricClient(Context context) { cache = new LyricCache(context, "qq"); }

    LrcTimeline load(String title, String artist, long durationMs) throws Exception {
        String query = LyricHttp.encode(title + (artist == null || artist.isEmpty() ? "" : " " + artist));
        JSONObject root = new JSONObject(LyricHttp.get(
                "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=10&w="
                        + query + "&format=json", REFERER));
        JSONObject data = root.optJSONObject("data");
        JSONObject song = data == null ? null : data.optJSONObject("song");
        JSONArray list = song == null ? null : song.optJSONArray("list");
        if (list == null) return LrcTimeline.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        String bestMid = "";
        long bestId = -1L;
        for (int i = 0; i < list.length(); i++) {
            JSONObject candidate = list.optJSONObject(i);
            if (candidate == null) continue;
            JSONArray singers = candidate.optJSONArray("singer");
            JSONObject singer = singers == null ? null : singers.optJSONObject(0);
            int score = NetEaseLyricClient.matchScore(title, artist, durationMs,
                    candidate.optString("songname", ""),
                    singer == null ? "" : singer.optString("name", ""),
                    candidate.optLong("interval", -1L) * 1000L);
            if (score > bestScore) {
                bestScore = score;
                bestMid = candidate.optString("songmid", "");
                bestId = candidate.optLong("songid", -1L);
            }
        }
        if (bestScore < 100 || bestMid.isEmpty()) return LrcTimeline.EMPTY;
        String original = cache.read(bestMid + "_original");
        String translated = cache.read(bestMid + "_translated");
        String enhanced = cache.read(bestMid + "_qrc");
        boolean translationChecked = translated != null
                || cache.read(bestMid + "_translation_checked") != null;
        if ((enhanced == null || !translationChecked) && bestId > 0L) {
            try {
                String response = LyricHttp.request("POST", QRC_URL, REFERER,
                        "version=15&miniversion=100&lrctype=4&musicid=" + bestId);
                if (enhanced == null) {
                    enhanced = QrcLyricCodec.decryptToTimedLyric(
                            QrcLyricCodec.encryptedContent(response, "content"));
                    if (!enhanced.isEmpty()) cache.write(bestMid + "_qrc", enhanced);
                }
                if (!translationChecked) {
                    translated = QrcLyricCodec.decryptToLrc(
                            QrcLyricCodec.encryptedContent(response, "contentts"));
                    if (!translated.isEmpty()) cache.write(bestMid + "_translated", translated);
                }
            } catch (Throwable ignored) { }
        }
        if (original == null || translated == null && !translationChecked) {
            JSONObject lyric = new JSONObject(LyricHttp.get(
                    "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                            + LyricHttp.encode(bestMid) + "&format=json&nobase64=1", REFERER));
            if (original == null) {
                original = decodeEntities(lyric.optString("lyric", ""));
                if (!original.isEmpty()) cache.write(bestMid + "_original", original);
            }
            if (translated == null || translated.isEmpty()) {
                translated = decodeEntities(lyric.optString("trans", ""));
                if (!translated.isEmpty()) cache.write(bestMid + "_translated", translated);
            }
            cache.write(bestMid + "_translation_checked", "1");
        }
        return LrcTimeline.parse(original == null ? "" : original,
                translated == null ? "" : translated, enhanced == null ? "" : enhanced);
    }

    private static String decodeEntities(String value) {
        return value.replace("&#58;", ":").replace("&#46;", ".")
                .replace("&apos;", "'").replace("&quot;", "\"")
                .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
