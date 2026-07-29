package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the current track on NetEase and caches its LRC/YRC payload for 30 days. */
final class NetEaseLyricClient {
    private static final String SEARCH_URL = "https://music.163.com/api/search/get/web";
    private static final String LYRIC_URL = "https://music.163.com/api/song/lyric";
    private static final long CACHE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    private static final Pattern LONG_NUMBER = Pattern.compile("(\\d{4,})");
    private final File cacheDirectory;

    NetEaseLyricClient(Context context) {
        cacheDirectory = new File(context.getCacheDir(), "netease_lyrics_v2");
    }

    Result load(String mediaId, String title, String artist, long durationMs) throws Exception {
        long directId = parseSongId(mediaId);
        if (directId > 0L) {
            Result direct = loadById(directId);
            if (!direct.timeline.isEmpty()) return direct;
        }
        long searchedId = searchSong(title, artist, durationMs);
        if (searchedId <= 0L || searchedId == directId) {
            return new Result(directId, LrcTimeline.EMPTY);
        }
        return loadById(searchedId);
    }

    private Result loadById(long songId) throws Exception {
        String response = readCache(songId);
        if (response == null) {
            response = request("GET", LYRIC_URL + "?id=" + songId
                    + "&lv=-1&kv=-1&tv=-1&yv=-1&rv=-1", null);
            writeCache(songId, response);
        }
        JSONObject root = new JSONObject(response);
        return new Result(songId, LrcTimeline.parse(
                lyricValue(root.optJSONObject("lrc")),
                lyricValue(root.optJSONObject("tlyric")),
                lyricValue(root.optJSONObject("yrc"))));
    }

    private long searchSong(String title, String artist, long durationMs) throws Exception {
        if (title == null || title.trim().isEmpty()) return -1L;
        String query = title.trim();
        if (artist != null && !artist.trim().isEmpty()) query += " " + artist.trim();
        JSONObject response = new JSONObject(request("POST", SEARCH_URL,
                "s=" + encode(query) + "&type=1&limit=10&offset=0"));
        JSONObject result = response.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        if (songs == null) return -1L;
        int bestScore = Integer.MIN_VALUE;
        long bestId = -1L;
        for (int index = 0; index < songs.length(); index++) {
            JSONObject song = songs.optJSONObject(index);
            if (song == null) continue;
            int score = matchScore(title, artist, durationMs, song.optString("name", ""),
                    firstArtist(song.optJSONArray("artists")), song.optLong("duration", -1L));
            if (score > bestScore) {
                bestScore = score;
                bestId = song.optLong("id", -1L);
            }
        }
        return bestScore >= 100 ? bestId : -1L;
    }

    static int matchScore(String title, String artist, long durationMs,
                          String candidateTitle, String candidateArtist,
                          long candidateDurationMs) {
        String wantedTitle = normalize(title);
        String foundTitle = normalize(candidateTitle);
        if (wantedTitle.isEmpty() || foundTitle.isEmpty()) return Integer.MIN_VALUE;
        int score = wantedTitle.equals(foundTitle) ? 100
                : wantedTitle.contains(foundTitle) || foundTitle.contains(wantedTitle) ? 55 : -80;
        String wantedArtist = normalize(artist);
        String foundArtist = normalize(candidateArtist);
        if (!wantedArtist.isEmpty() && !foundArtist.isEmpty()) {
            if (wantedArtist.equals(foundArtist)) score += 70;
            else if (wantedArtist.contains(foundArtist) || foundArtist.contains(wantedArtist)) {
                score += 45;
            } else score -= 45;
        }
        if (durationMs > 0L && candidateDurationMs > 0L) {
            long difference = Math.abs(durationMs - candidateDurationMs);
            score += difference <= 2_000L ? 35 : difference <= 5_000L ? 25
                    : difference <= 15_000L ? 5 : -20;
        }
        if (!hasVersionNoise(title) && hasVersionNoise(candidateTitle)) {
            score -= 60;
        }
        return score;
    }

    static long parseSongId(String value) {
        if (value == null) return -1L;
        Matcher matcher = LONG_NUMBER.matcher(value);
        if (matcher.find()) {
            try { return Long.parseLong(matcher.group(1)); }
            catch (NumberFormatException ignored) { }
        }
        return -1L;
    }

    private String request(String method, String address, String body) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(8_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 Lyrics-Companion/1.0");
            connection.setRequestProperty("Referer", "https://music.163.com/");
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            String response = readAll(status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("网易云接口返回 HTTP " + status);
            }
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private String readCache(long songId) {
        File file = cacheFile(songId);
        if (!file.isFile() || System.currentTimeMillis() - file.lastModified() > CACHE_MAX_AGE_MS) {
            return null;
        }
        try (InputStream input = new FileInputStream(file)) { return readAll(input); }
        catch (Exception ignored) { return null; }
    }

    private void writeCache(long songId, String response) {
        try {
            if ((!cacheDirectory.isDirectory() && !cacheDirectory.mkdirs()) || response == null) {
                return;
            }
            try (FileOutputStream output = new FileOutputStream(cacheFile(songId))) {
                output.write(response.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }

    private File cacheFile(long songId) { return new File(cacheDirectory, songId + ".json"); }

    private static String readAll(InputStream input) throws Exception {
        if (input == null) return "";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line).append('\n');
        }
        return result.toString();
    }

    private static String lyricValue(JSONObject object) {
        return object == null ? "" : object.optString("lyric", "");
    }

    private static String firstArtist(JSONArray artists) {
        JSONObject artist = artists == null ? null : artists.optJSONObject(0);
        return artist == null ? "" : artist.optString("name", "");
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }

    private static boolean hasVersionNoise(String value) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return normalized.matches(".*(伴奏|翻唱|现场|升调|降调|live|remix|dj|sped|slowed|ktv).*");
    }

    static final class Result {
        final long songId;
        final LrcTimeline timeline;

        Result(long songId, LrcTimeline timeline) {
            this.songId = songId;
            this.timeline = timeline == null ? LrcTimeline.EMPTY : timeline;
        }
    }
}
