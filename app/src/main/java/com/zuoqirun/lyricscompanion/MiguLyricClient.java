package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/** Migu song search/resource lookup with MRC word timing. */
final class MiguLyricClient {
    private static final String REFERER = "https://app.c.nf.migu.cn/";
    private static final String DEVICE = "963B7AA0D21511ED807EE5846EC87D20";
    private final LyricCache cache;

    MiguLyricClient(Context context) { cache = new LyricCache(context, "migu"); }

    LrcTimeline load(String mediaId, String title, String artist, long durationMs) throws Exception {
        String directId = validId(mediaId);
        if (!directId.isEmpty()) {
            try {
                LrcTimeline direct = byResourceId(directId);
                if (!direct.isEmpty()) return direct;
            } catch (InterruptedException error) { throw error; }
            catch (Exception ignored) { }
        }
        if (title == null || title.trim().isEmpty()) return LrcTimeline.EMPTY;
        String query = title.trim() + (artist == null || artist.trim().isEmpty()
                ? "" : " " + artist.trim());
        long now = System.currentTimeMillis();
        String sign = hex(MessageDigest.getInstance("MD5").digest((query
                + "6cdc72a439cef99a3418d2a78aa28c73"
                + "yyapp2d16148780a1dcc7408e06336b98cfd50" + DEVICE + now)
                .getBytes(StandardCharsets.UTF_8)));
        Map<String, String> headers = new HashMap<>();
        headers.put("uiVersion", "A_music_3.6.1");
        headers.put("deviceId", DEVICE);
        headers.put("timestamp", String.valueOf(now));
        headers.put("sign", sign);
        headers.put("channel", "0146921");
        String url = "https://jadeite.migu.cn/music_search/v3/search/searchAll"
                + "?isCorrect=0&isCopyright=1&searchSwitch="
                + LyricHttp.encode("{\"song\":1,\"album\":0,\"singer\":0,\"tagSong\":1,"
                + "\"mv\":0,\"bestShow\":1,\"songlist\":0,\"lyricSong\":0}")
                + "&pageSize=20&text=" + LyricHttp.encode(query) + "&pageNo=1&sort=0&sid=USS";
        JSONObject root = new JSONObject(LyricHttp.get(url, REFERER, headers));
        if (!"000000".equals(root.optString("code"))) return LrcTimeline.EMPTY;
        JSONObject results = root.optJSONObject("songResultData");
        JSONArray groups = results == null ? null : results.optJSONArray("resultList");
        if (groups == null) return LrcTimeline.EMPTY;
        int best = Integer.MIN_VALUE;
        String bestId = "";
        for (int group = 0; group < groups.length(); group++) {
            JSONArray songs = groups.optJSONArray(group);
            if (songs == null) continue;
            for (int index = 0; index < songs.length(); index++) {
                JSONObject song = songs.optJSONObject(index);
                if (song == null) continue;
                String id = validId(song.optString("copyrightId"));
                if (id.isEmpty()) continue;
                String candidateTitle = song.optString("name", "");
                String candidateArtist = firstSinger(song.optJSONArray("singerList"));
                long candidateDuration = song.optLong("duration", -1L);
                if (candidateDuration > 0 && candidateDuration < 20_000L) candidateDuration *= 1000L;
                if (!NetEaseLyricClient.plausibleMatch(title, artist, durationMs,
                        candidateTitle, candidateArtist, candidateDuration)) continue;
                int score = NetEaseLyricClient.matchScore(title, artist, durationMs,
                        candidateTitle, candidateArtist, candidateDuration);
                if (score > best) { best = score; bestId = id; }
            }
        }
        return best >= 100 ? byResourceId(bestId) : LrcTimeline.EMPTY;
    }

    private LrcTimeline byResourceId(String id) throws Exception {
        String cached = cache.read(id + "_mrc_v1");
        if (cached != null) {
            LrcTimeline found = LrcTimeline.parse("", "", cached);
            if (!found.isEmpty()) return found;
        }
        JSONObject root = new JSONObject(LyricHttp.request("POST",
                "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do?resourceType=2",
                REFERER, "resourceId=" + LyricHttp.encode(id)));
        JSONArray resources = root.optJSONArray("resource");
        JSONObject song = resources == null ? null : resources.optJSONObject(0);
        if (song == null) return LrcTimeline.EMPTY;
        String mrc = song.optString("mrcUrl", "");
        String lrc = song.optString("lrcUrl", "");
        String translated = song.optString("trcUrl", "");
        if (mrc.isEmpty() && lrc.isEmpty()) return LrcTimeline.EMPTY;
        String translation = "";
        if (safeUrl(translated)) {
            try { translation = LyricHttp.get(translated, REFERER); }
            catch (InterruptedException error) { throw error; }
            catch (Exception ignored) { }
        }
        if (safeUrl(mrc)) {
            try {
                String enhanced = MiguMrcCodec.toEnhancedTimeline(
                        MiguMrcCodec.decrypt(LyricHttp.get(mrc, REFERER)));
                LrcTimeline found = LrcTimeline.parse("", translation, enhanced);
                if (!found.isEmpty()) {
                    cache.write(id + "_mrc_v1", enhanced);
                    return found;
                }
            } catch (InterruptedException error) { throw error; }
            catch (Exception ignored) { }
        }
        return safeUrl(lrc) ? LrcTimeline.parse(LyricHttp.get(lrc, REFERER), translation)
                : LrcTimeline.EMPTY;
    }

    private static boolean safeUrl(String address) {
        try {
            URI url = new URI(address);
            String host = url.getHost();
            return ("https".equalsIgnoreCase(url.getScheme())
                    || "http".equalsIgnoreCase(url.getScheme())) && host != null
                    && (host.equals("migu.cn") || host.endsWith(".migu.cn"));
        } catch (Exception error) { return false; }
    }

    private static String validId(String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        return trimmed.matches("[A-Za-z0-9]{4,48}") ? trimmed : "";
    }

    private static String firstSinger(JSONArray singers) {
        if (singers == null || singers.length() == 0) return "";
        JSONObject first = singers.optJSONObject(0);
        return first == null ? "" : first.optString("name", "");
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT,
                "%02x", value & 255));
        return result.toString();
    }
}
