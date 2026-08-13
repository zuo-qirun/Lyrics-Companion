package com.zuoqirun.lyricscompanion;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ConfigurationShareClient {
    private static final String API_BASE = "https://lyrics-companion.zuoqirun.top/api/config";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private ConfigurationShareClient() { }

    static void share(Context context, String description, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("description", description == null ? "" : description.trim());
                body.put("config", ConfigurationCodec.exportConfiguration(app));
                callback.complete(new Result(true, post("/share", body), ""));
            } catch (Throwable error) { callback.complete(new Result(false, null, message(error))); }
        });
    }

    static void fetch(String code, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("code", code == null ? "" : code.trim());
                callback.complete(new Result(true, post("/import", body), ""));
            } catch (Throwable error) { callback.complete(new Result(false, null, message(error))); }
        });
    }

    private static JSONObject post(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            byte[] encoded = body.toString().getBytes(StandardCharsets.UTF_8);
            connection = HttpCompat.open(API_BASE + path);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(7_000);
            connection.setReadTimeout(10_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(encoded.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) { output.write(encoded); }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String content = read(input);
            JSONObject result = new JSONObject(content);
            if (status < 200 || status >= 300 || !result.optBoolean("ok")) {
                throw new IllegalStateException(status == 404 ? "分享码不存在或已过期"
                        : result.optString("error", "服务器返回 " + status));
            }
            return result;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "{}";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) >= 0) {
                if (output.size() + count > 96 * 1024) throw new IllegalStateException("配置响应过大");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String message(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
    interface Callback { void complete(Result result); }
    static final class Result {
        final boolean success; final JSONObject value; final String error;
        Result(boolean success, JSONObject value, String error) {
            this.success = success; this.value = value; this.error = error;
        }
    }
}
