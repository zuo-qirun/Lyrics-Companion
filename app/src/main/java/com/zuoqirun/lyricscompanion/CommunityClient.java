package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Anonymous online heartbeat and feedback transport shared by Activity and overlay service. */
final class CommunityClient {
    private static final String API_BASE = "https://lyrics-companion.zuoqirun.top/api";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private CommunityClient() {}

    static void heartbeatAsync(Context context, Callback<OnlineStatus> callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                JSONObject body = baseBody(appContext);
                JSONObject response = post("/online/heartbeat", body);
                int online = Math.max(0, response.optInt("online", 0));
                if (callback != null) callback.complete(new OnlineStatus(online, ""));
            } catch (Throwable error) {
                if (callback != null) callback.complete(new OnlineStatus(-1, safeMessage(error)));
            }
        });
    }

    static void submitFeedbackAsync(Context context, String message, String contact,
                                    Callback<FeedbackResult> callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                JSONObject body = baseBody(appContext);
                body.put("message", message == null ? "" : message.trim());
                body.put("contact", contact == null ? "" : contact.trim());
                JSONObject response = post("/feedback", body);
                if (callback != null) {
                    callback.complete(new FeedbackResult(true, response.optString("id"), ""));
                }
            } catch (Throwable error) {
                if (callback != null) {
                    callback.complete(new FeedbackResult(false, "", safeMessage(error)));
                }
            }
        });
    }

    private static JSONObject baseBody(Context context) throws Exception {
        JSONObject body = new JSONObject();
        body.put("clientId", clientId(context));
        body.put("appVersion", appVersion(context));
        return body;
    }

    private static synchronized String clientId(Context context) {
        String existing = AppPreferences.get(context).getString(
                AppPreferences.KEY_COMMUNITY_CLIENT_ID, "");
        if (existing != null && !existing.isEmpty()) return existing;
        String generated = UUID.randomUUID().toString();
        AppPreferences.get(context).edit()
                .putString(AppPreferences.KEY_COMMUNITY_CLIENT_ID, generated).apply();
        return generated;
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            return String.valueOf(info.versionName) + " (" + code + ")";
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static JSONObject post(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            byte[] encoded = body.toString().getBytes(StandardCharsets.UTF_8);
            connection = HttpCompat.open(API_BASE + path);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(7_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(encoded.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Lyrics-Companion/Android");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(encoded);
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String content = readBounded(input);
            if (!content.trim().startsWith("{")) {
                throw new IllegalStateException("服务暂不可用");
            }
            JSONObject response = new JSONObject(content);
            if (status < 200 || status >= 300 || !response.optBoolean("ok", false)) {
                if (status == 429) throw new IllegalStateException("提交太频繁，请稍后再试");
                String detail = response.optString("error", "服务器返回 " + status);
                throw new IllegalStateException(detail);
            }
            return response;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBounded(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4 * 1024];
            int total = 0;
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                if (count == 0) continue;
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("服务器响应过大");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty()
                ? error.getClass().getSimpleName() : message.trim();
    }

    interface Callback<T> { void complete(T result); }

    static final class OnlineStatus {
        final int online;
        final String error;

        OnlineStatus(int online, String error) {
            this.online = online;
            this.error = error;
        }

        boolean available() { return online >= 0; }
    }

    static final class FeedbackResult {
        final boolean success;
        final String id;
        final String error;

        FeedbackResult(boolean success, String id, String error) {
            this.success = success;
            this.id = id;
            this.error = error;
        }
    }
}
