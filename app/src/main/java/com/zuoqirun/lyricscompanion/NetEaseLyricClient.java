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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds the current track on NetEase and caches its LRC/YRC payload for 30 days. */
final class NetEaseLyricClient {
    private static final String SEARCH_URL = "https://music.163.com/api/search/get/web";
    private static final String LYRIC_URL =
            "https://interface3.music.163.com/eapi/song/lyric/v1";
    private static final long CACHE_MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    /**
     * Matches a song id only when it is a standalone numeric token.  Automotive
     * MediaSession implementations commonly publish opaque hexadecimal ids such
     * as 497605AF857F4122A09B0FFDFE3471D5; extracting the leading digits from
     * those ids makes us load an unrelated NetEase song and stop before title
     * and artist search can run.
     */
    private static final Pattern STANDALONE_LONG_NUMBER =
            Pattern.compile("(?<![A-Za-z0-9])(\\d{4,})(?![A-Za-z0-9])");
    private static final Pattern ROMAJI_YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern ROMAJI_WORD_TIME = Pattern.compile("\\(\\d+,\\d+,\\d+\\)");
    private final File cacheDirectory;

    NetEaseLyricClient(Context context) {
        cacheDirectory = new File(context.getCacheDir(), "netease_lyrics_v3");
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
        if (response != null) {
            try {
                Result cached = parseLyrics(songId, response);
                if (!cached.timeline.isEmpty()) return cached;
            } catch (Exception ignored) {
                // A corrupt or lyric-free old cache entry must not block a fresh request.
            }
        }
        response = request("POST", LYRIC_URL,
                NetEaseEapi.formBody(songId, LyricSourceRules.netEaseSigningPath()));
        Result result = parseLyrics(songId, response);
        if (!result.timeline.isEmpty()) writeCache(songId, response);
        return result;
    }

    static Result parseLyrics(long songId, String response) throws Exception {
        JSONObject root = new JSONObject(response);
        int code = root.optInt("code", 200);
        if (code != 200) throw new IllegalStateException("网易云歌词接口返回状态 " + code);
        return new Result(songId, LrcTimeline.parse(
                lyricValue(root.optJSONObject("lrc")),
                lyricValue(root.optJSONObject("tlyric")),
                lyricValue(root.optJSONObject("yrc")),
                romanizedLines(lyricValue(root.optJSONObject("yromalrc")),
                        lyricValue(root.optJSONObject("romalrc")))));
    }

    static String romanizedLines(String wordTimed, String lineTimed) {
        if (wordTimed == null || wordTimed.isEmpty()) return lineTimed == null ? "" : lineTimed;
        StringBuilder converted = new StringBuilder();
        for (String rawLine : wordTimed.split("\\r?\\n")) {
            Matcher line = ROMAJI_YRC_LINE.matcher(rawLine.trim());
            if (!line.matches()) continue;
            long start = Long.parseLong(line.group(1));
            String text = ROMAJI_WORD_TIME.matcher(line.group(3)).replaceAll("").trim();
            if (text.isEmpty()) continue;
            converted.append(String.format(Locale.ROOT, "[%02d:%02d.%03d]%s%n",
                    start / 60_000L, start / 1_000L % 60L, start % 1_000L, text));
        }
        return converted.length() > 0 ? converted.toString()
                : lineTimed == null ? "" : lineTimed;
    }

    private long searchSong(String title, String artist, long durationMs) throws Exception {
        if (title == null || title.trim().isEmpty()) return -1L;
        String query = title.trim();
        if (artist != null && !artist.trim().isEmpty()) query += " " + artist.trim();
        long match = searchSongQuery(query, title, artist, durationMs);
        if (match <= 0L && artist != null && !artist.trim().isEmpty()) {
            match = searchSongQuery(title.trim(), title, artist, durationMs);
        }
        return match;
    }

    private long searchSongQuery(String query, String title, String artist,
                                 long durationMs) throws Exception {
        JSONObject response = new JSONObject(request("POST", SEARCH_URL,
                "s=" + encode(query) + "&type=1&limit=20&offset=0"));
        JSONObject result = response.optJSONObject("result");
        JSONArray songs = result == null ? null : result.optJSONArray("songs");
        if (songs == null) return -1L;
        int bestScore = Integer.MIN_VALUE;
        long bestId = -1L;
        for (int index = 0; index < songs.length(); index++) {
            JSONObject song = songs.optJSONObject(index);
            if (song == null) continue;
            String candidateTitle = song.optString("name", "");
            String candidateArtist = firstArtist(song.optJSONArray("artists"));
            long candidateDuration = song.optLong("duration", -1L);
            if (!plausibleMatch(title, artist, durationMs, candidateTitle,
                    candidateArtist, candidateDuration)) continue;
            int score = matchScore(title, artist, durationMs, candidateTitle,
                    candidateArtist, candidateDuration);
            if (score > bestScore) {
                bestScore = score;
                bestId = song.optLong("id", -1L);
            }
        }
        return bestScore >= 100 ? bestId : -1L;
    }

    static boolean plausibleMatch(String title, String artist, long durationMs,
                                  String candidateTitle, String candidateArtist,
                                  long candidateDurationMs) {
        String wantedTitle = normalize(title);
        String foundTitle = normalize(candidateTitle);
        if (wantedTitle.isEmpty() || foundTitle.isEmpty()) return false;
        String wantedArtist = normalize(artist);
        String foundArtist = normalize(candidateArtist);
        if (!wantedArtist.isEmpty() && !foundArtist.isEmpty()
                && !wantedArtist.contains(foundArtist) && !foundArtist.contains(wantedArtist)) {
            return false;
        }
        long durationDifference = durationMs > 0L && candidateDurationMs > 0L
                ? Math.abs(durationMs - candidateDurationMs) : -1L;
        if (durationDifference > 15_000L) return false;
        if (wantedTitle.equals(foundTitle)) return true;
        // A near-identical title can be a display suffix. Only accept it with matching artist,
        // close duration, and no extra live/remix/accompaniment version marker.
        return !wantedArtist.isEmpty() && !foundArtist.isEmpty()
                && durationDifference >= 0L && durationDifference <= 2_000L
                && (wantedTitle.contains(foundTitle) || foundTitle.contains(wantedTitle))
                && hasVersionNoise(title) == hasVersionNoise(candidateTitle);
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
        Matcher matcher = STANDALONE_LONG_NUMBER.matcher(value.trim());
        if (matcher.find()) {
            try { return Long.parseLong(matcher.group(1)); }
            catch (NumberFormatException ignored) { }
        }
        return -1L;
    }

    private String request(String method, String address, String body) throws Exception {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        headers.put("Origin", "https://music.163.com");
        return LyricHttp.request(method, address, "https://music.163.com/", body, headers);
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
