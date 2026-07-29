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
        String original = cache.read(bestHash + "_original");
        String enhanced = cache.read(bestHash + "_krc");
        String translated = cache.read(bestHash + "_translated");
        boolean translationChecked = translated != null
                || cache.read(bestHash + "_translation_checked") != null;
        if (original != null && enhanced != null && translationChecked) {
            return LrcTimeline.parse(original, translated == null ? "" : translated, enhanced);
        }
        JSONObject search = new JSONObject(LyricHttp.get(
                "https://lyrics.kugou.com/search?ver=1&man=yes&client=pc&hash="
                        + LyricHttp.encode(bestHash), REFERER));
        JSONArray candidates = search.optJSONArray("candidates");
        JSONObject lyricCandidate = candidates == null ? null : candidates.optJSONObject(0);
        if (lyricCandidate == null) return LrcTimeline.EMPTY;
        String downloadBase = "https://lyrics.kugou.com/download?ver=1&client=pc&id="
                + LyricHttp.encode(lyricCandidate.optString("id", ""))
                + "&accesskey=" + LyricHttp.encode(lyricCandidate.optString("accesskey", ""));
        if (enhanced == null || !translationChecked) {
            try {
                JSONObject krcDownload = new JSONObject(LyricHttp.get(
                        downloadBase + "&fmt=krc&charset=utf8", REFERER));
                String encoded = krcDownload.optString("content", "");
                if (!encoded.isEmpty()) {
                    String decrypted = KrcLyricCodec.decrypt(
                            Base64.decode(encoded, Base64.DEFAULT));
                    if (enhanced == null) {
                        enhanced = KrcLyricCodec.toEnhancedTimeline(decrypted);
                        if (!enhanced.isEmpty()) cache.write(bestHash + "_krc", enhanced);
                    }
                    if (!translationChecked) {
                        String language = KrcLyricCodec.encodedLanguage(decrypted);
                        if (!language.isEmpty()) {
                            String languageJson = new String(Base64.decode(language, Base64.DEFAULT),
                                    StandardCharsets.UTF_8);
                            translated = KrcLyricCodec.toTranslationLrc(decrypted, languageJson);
                            if (!translated.isEmpty()) {
                                cache.write(bestHash + "_translated", translated);
                            }
                        }
                        cache.write(bestHash + "_translation_checked", "1");
                        translationChecked = true;
                    }
                }
            } catch (Throwable ignored) { }
        }
        if (original == null) {
            JSONObject lrcDownload = new JSONObject(LyricHttp.get(
                    downloadBase + "&fmt=lrc&charset=utf8", REFERER));
            String encoded = lrcDownload.optString("content", "");
            if (!encoded.isEmpty()) {
                original = new String(Base64.decode(encoded, Base64.DEFAULT),
                        StandardCharsets.UTF_8).replace("\uFEFF", "");
                cache.write(bestHash + "_original", original);
            }
        }
        return LrcTimeline.parse(original == null ? "" : original,
                translated == null ? "" : translated,
                enhanced == null ? "" : enhanced);
    }
}
