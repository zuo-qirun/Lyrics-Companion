package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;

/** Cross-catalog artwork fallback for players that omit MediaMetadata cover fields. */
final class CoverArtSearchClient {
    private CoverArtSearchClient() { }

    static String find(String title, String artist, long durationMs) {
        try {
            String qq = findOnQq(title, artist, durationMs);
            if (!qq.isEmpty()) return qq;
        } catch (Throwable ignored) { }
        try {
            return findOnKugou(title, artist, durationMs);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String findOnQq(String title, String artist, long durationMs) throws Exception {
        String query = LyricHttp.encode(title + (artist == null || artist.isEmpty()
                ? "" : " " + artist));
        JSONObject root = new JSONObject(LyricHttp.get(
                "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=10&w="
                        + query + "&format=json", "https://y.qq.com/"));
        JSONObject data = root.optJSONObject("data");
        JSONObject songs = data == null ? null : data.optJSONObject("song");
        JSONArray list = songs == null ? null : songs.optJSONArray("list");
        if (list == null) return "";
        int bestScore = Integer.MIN_VALUE;
        String albumMid = "";
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
                albumMid = candidate.optString("albummid", "");
            }
        }
        if (bestScore < 100 || albumMid.isEmpty()) return "";
        return "https://y.gtimg.cn/music/photo_new/T002R500x500M000" + albumMid + ".jpg";
    }

    private static String findOnKugou(String title, String artist, long durationMs)
            throws Exception {
        String query = LyricHttp.encode(title + (artist == null || artist.isEmpty()
                ? "" : " " + artist));
        JSONObject root = new JSONObject(LyricHttp.get(
                "https://songsearch.kugou.com/song_search_v2?keyword=" + query
                        + "&page=1&pagesize=10&userid=-1&clientver=&platform=WebFilter",
                "https://www.kugou.com/"));
        JSONObject data = root.optJSONObject("data");
        JSONArray list = data == null ? null : data.optJSONArray("lists");
        if (list == null) return "";
        int bestScore = Integer.MIN_VALUE;
        String image = "";
        for (int i = 0; i < list.length(); i++) {
            JSONObject candidate = list.optJSONObject(i);
            if (candidate == null) continue;
            int score = NetEaseLyricClient.matchScore(title, artist, durationMs,
                    candidate.optString("SongName", ""),
                    candidate.optString("SingerName", ""),
                    candidate.optLong("Duration", -1L) * 1000L);
            if (score > bestScore) {
                bestScore = score;
                image = candidate.optString("Image", "");
            }
        }
        if (bestScore < 100 || image.isEmpty()) return "";
        return image.replace("{size}", "500");
    }
}
