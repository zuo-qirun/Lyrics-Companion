package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small, validated route table fetched from the update service; built-in URLs always work. */
final class LyricSourceRules {
    private static final String CONFIG_URL =
            "https://lyrics-companion.zuoqirun.top/lyric-rules.json";
    private static final long REFRESH_MS = 6L * 60L * 60L * 1000L;
    private static final ExecutorService REFRESH = Executors.newSingleThreadExecutor();
    private static final Route[] ROUTES = {
            new Route("netease_search", "https://music.163.com/api/search/get/web", "163.com"),
            new Route("netease_lyric", "https://interface3.music.163.com/eapi/song/lyric/v1", "163.com"),
            new Route("qq_search", "https://c.y.qq.com/soso/fcgi-bin/client_search_cp", "qq.com"),
            new Route("qq_qrc", "https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg", "qq.com"),
            new Route("qq_lrc", "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg", "qq.com"),
            new Route("kugou_search", "https://songsearch.kugou.com/song_search_v2", "kugou.com"),
            new Route("kugou_lyric_search", "https://lyrics.kugou.com/search", "kugou.com"),
            new Route("kugou_download", "https://lyrics.kugou.com/download", "kugou.com"),
            new Route("kuwo_search", "https://search.kuwo.cn/r.s", "kuwo.cn"),
            new Route("kuwo_word", "http://mlyric.kuwo.cn/mobi.s", "kuwo.cn"),
            new Route("kuwo_web", "https://m.kuwo.cn/newh5/singles/songinfoandlrc", "kuwo.cn"),
            new Route("soda_search", "https://api.qishui.com/luna/pc/search/track", "qishui.com"),
            new Route("soda_mobile", "https://www.qishui.com/share/track", "qishui.com"),
            new Route("soda_car", "https://music.douyin.com/qishui/share/track", "douyin.com"),
            new Route("migu_search", "https://jadeite.migu.cn/music_search/v3/search/searchAll", "migu.cn"),
            new Route("migu_resource", "https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do", "migu.cn")
    };
    private static volatile JSONObject rules = new JSONObject();
    private static volatile long lastAttempt;
    private static volatile boolean initialized;
    private static Context appContext;

    private LyricSourceRules() {}

    static synchronized void initialize(Context context) {
        if (initialized) return;
        appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences("lyric_source_rules", 0);
        try { rules = validated(new JSONObject(prefs.getString("json", "{}"))); }
        catch (Exception ignored) { rules = new JSONObject(); }
        lastAttempt = prefs.getLong("last_attempt", 0L);
        initialized = true;
        refreshIfDue();
    }

    static String rewrite(String address) {
        if (address == null) return null;
        refreshIfDue();
        JSONObject snapshot = rules;
        for (Route route : ROUTES) {
            if (!address.startsWith(route.original)) continue;
            int after = route.original.length();
            if (after < address.length() && address.charAt(after) != '?'
                    && address.charAt(after) != '&') continue;
            JSONObject entry = snapshot.optJSONObject(route.key);
            if (entry == null) return address;
            String target = entry.optString("url", route.original);
            if (!safeTarget(route, target)) return address;
            return withParameters(target + address.substring(after), entry.optJSONObject("parameters"));
        }
        return address;
    }

    static String netEaseSigningPath() {
        JSONObject entry = rules.optJSONObject("netease_lyric");
        String target = entry == null ? "" : entry.optString("url", "");
        try {
            if (safeTarget(ROUTES[1], target)) {
                String path = new URI(target).getPath();
                if (path.startsWith("/eapi/")) return "/api/" + path.substring(6);
            }
        } catch (Exception ignored) { }
        return "/api/song/lyric/v1";
    }

    static boolean plainKuwoWordResponse() {
        JSONObject entry = rules.optJSONObject("kuwo_word");
        return entry != null && "plain".equals(entry.optString("codec"));
    }

    /** Literal alternatives only: enough for renamed HTML markers without arbitrary regex code. */
    static Pattern sodaRouterMarkerPattern() {
        JSONObject mobile = rules.optJSONObject("soda_mobile");
        JSONObject car = rules.optJSONObject("soda_car");
        String regex = mobile == null ? "" : mobile.optString("markerRegex", "");
        String carRegex = car == null ? "" : car.optString("markerRegex", "");
        if (regex.isEmpty()) regex = carRegex;
        else if (!carRegex.isEmpty() && !carRegex.equals(regex)) regex += "|" + carRegex;
        return safeMarkerRegex(regex) ? Pattern.compile(regex) : null;
    }

    private static synchronized void refreshIfDue() {
        if (!initialized || System.currentTimeMillis() - lastAttempt < REFRESH_MS) return;
        lastAttempt = System.currentTimeMillis();
        REFRESH.execute(() -> {
            try {
                HttpURLConnection connection = HttpCompat.open(CONFIG_URL);
                try {
                    connection.setConnectTimeout(5_000);
                    connection.setReadTimeout(5_000);
                    if (connection.getResponseCode() != 200) return;
                    try (InputStream input = connection.getInputStream();
                         ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096];
                        int count;
                        while ((count = input.read(buffer)) != -1) {
                            if (output.size() + count > 64 * 1024) return;
                            output.write(buffer, 0, count);
                        }
                        JSONObject accepted = validated(new JSONObject(new String(output.toByteArray(),
                                StandardCharsets.UTF_8)));
                        rules = accepted;
                        appContext.getSharedPreferences("lyric_source_rules", 0).edit()
                                .putString("json", accepted.toString())
                                .putLong("last_attempt", lastAttempt).apply();
                    }
                } finally { connection.disconnect(); }
            } catch (Exception ignored) { }
        });
    }

    private static JSONObject validated(JSONObject document) throws Exception {
        JSONObject accepted = new JSONObject();
        if (document.optInt("schemaVersion", 1) != 1) return accepted;
        JSONObject entries = document.optJSONObject("routes");
        if (entries == null) return accepted;
        for (Route route : ROUTES) {
            JSONObject entry = entries.optJSONObject(route.key);
            if (entry == null || !safeTarget(route, entry.optString("url"))) continue;
            JSONObject parameters = entry.optJSONObject("parameters");
            if (!safeParameters(parameters)) continue;
            String codec = entry.optString("codec", "");
            if (!codec.isEmpty() && !("kuwo_word".equals(route.key)
                    && ("plain".equals(codec) || "xor".equals(codec)))) continue;
            if (!entry.optString("markerRegex", "").isEmpty()
                    && (!("soda_mobile".equals(route.key) || "soda_car".equals(route.key))
                    || !safeMarkerRegex(entry.optString("markerRegex")))) continue;
            accepted.put(route.key, entry);
        }
        return accepted;
    }

    private static boolean safeTarget(Route route, String target) {
        if (target == null || target.length() > 240) return false;
        try {
            URI uri = new URI(target);
            String host = uri.getHost();
            return host != null && (host.equals(route.domain) || host.endsWith("." + route.domain))
                    && ("https".equalsIgnoreCase(uri.getScheme())
                    || "kuwo_word".equals(route.key) && "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getUserInfo() == null && uri.getQuery() == null
                    && uri.getFragment() == null && uri.getPort() == -1;
        } catch (Exception error) { return false; }
    }

    private static boolean safeParameters(JSONObject parameters) {
        if (parameters == null) return true;
        if (parameters.length() > 16) return false;
        Iterator<String> keys = parameters.keys();
        while (keys.hasNext()) {
            String key = keys.next(), value = parameters.optString(key, "");
            if (!key.matches("[A-Za-z0-9_-]{1,32}") || value.length() > 128
                    || !value.matches("[A-Za-z0-9._~-]*")) return false;
        }
        return true;
    }

    private static boolean safeMarkerRegex(String regex) {
        if (regex == null || regex.isEmpty() || regex.length() > 96
                || !regex.matches("[A-Za-z0-9_|()]+")
                || regex.startsWith("|") || regex.endsWith("|")) return false;
        try { Pattern.compile(regex); return true; }
        catch (Exception error) { return false; }
    }

    private static String withParameters(String address, JSONObject changes) {
        if (changes == null || changes.length() == 0) return address;
        int question = address.indexOf('?');
        StringBuilder result = new StringBuilder(question < 0 ? address : address.substring(0, question));
        String[] original = question < 0 ? new String[0] : address.substring(question + 1).split("&");
        JSONObject remaining = new JSONObject();
        Iterator<String> keys = changes.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            try { remaining.put(key, changes.optString(key)); } catch (Exception ignored) { }
        }
        for (String pair : original) {
            if (pair.isEmpty()) continue;
            String key = pair.split("=", 2)[0];
            if (remaining.has(key)) {
                appendParameter(result, key, remaining.optString(key));
                remaining.remove(key);
            } else result.append(result.indexOf("?") < 0 ? '?' : '&').append(pair);
        }
        keys = remaining.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            appendParameter(result, key, remaining.optString(key));
        }
        return result.toString();
    }

    private static void appendParameter(StringBuilder into, String key, String value) {
        try {
            into.append(into.indexOf("?") < 0 ? '?' : '&').append(key).append('=')
                    .append(URLEncoder.encode(value, "UTF-8"));
        } catch (Exception ignored) { }
    }

    private static final class Route {
        final String key, original, domain;
        Route(String key, String original, String domain) {
            this.key = key; this.original = original; this.domain = domain;
        }
    }
}
