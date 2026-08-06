package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Anonymous online, feedback, reply and opt-in diagnostic transport. */
final class CommunityClient {
    private static final String API_BASE = "https://lyrics-companion.zuoqirun.top/api";
    private static final int MAX_RESPONSE_BYTES = 64 * 1024;
    private static final int MAX_TICKETS = 20;
    private static final int MAX_READ_REPLY_IDS = 100;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);

    private CommunityClient() {}

    static void heartbeatAsync(Context context, Callback<OnlineStatus> callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                JSONObject response = post("/online/heartbeat", baseBody(appContext));
                complete(callback, new OnlineStatus(Math.max(0, response.optInt("online", 0)), ""));
            } catch (Throwable error) {
                complete(callback, new OnlineStatus(-1, safeMessage(error)));
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
                String id = response.optString("id");
                String token = response.optString("replyToken");
                rememberTicket(appContext, id, token);
                AppPreferences.setLastFeedbackId(appContext, id);
                complete(callback, new FeedbackResult(true, id, ""));
            } catch (Throwable error) {
                complete(callback, new FeedbackResult(false, "", safeMessage(error)));
            }
        });
    }

    static void fetchFeedbackRepliesAsync(Context context, Callback<FeedbackRepliesResult> callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                JSONArray tickets = storedTickets(appContext);
                if (tickets.length() == 0) {
                    complete(callback, new FeedbackRepliesResult(true, new ArrayList<>(), ""));
                    return;
                }
                JSONObject body = baseBody(appContext);
                body.put("tickets", tickets);
                JSONObject response = post("/feedback/replies", body);
                List<FeedbackReply> replies = new ArrayList<>();
                JSONArray items = response.optJSONArray("replies");
                if (items != null) for (int index = 0; index < items.length(); index++) {
                    JSONObject item = items.optJSONObject(index);
                    if (item != null) replies.add(new FeedbackReply(item.optString("id"),
                            item.optString("feedbackId"), item.optString("createdAt"),
                            item.optString("message")));
                }
                complete(callback, new FeedbackRepliesResult(true, replies, ""));
            } catch (Throwable error) {
                complete(callback, new FeedbackRepliesResult(false, new ArrayList<>(), safeMessage(error)));
            }
        });
    }

    static List<FeedbackReply> unreadFeedbackReplies(Context context,
                                                      List<FeedbackReply> replies) {
        return unreadFeedbackReplies(replies, AppPreferences.get(context).getString(
                AppPreferences.KEY_FEEDBACK_READ_REPLY_IDS, ""));
    }

    static void markFeedbackRepliesRead(Context context, List<FeedbackReply> replies) {
        String existing = AppPreferences.get(context).getString(
                AppPreferences.KEY_FEEDBACK_READ_REPLY_IDS, "");
        AppPreferences.get(context).edit().putString(AppPreferences.KEY_FEEDBACK_READ_REPLY_IDS,
                markFeedbackRepliesRead(existing, replies)).apply();
    }

    static List<FeedbackReply> unreadFeedbackReplies(List<FeedbackReply> replies,
                                                      String readReplyIds) {
        Set<String> readIds = replyIds(readReplyIds);
        List<FeedbackReply> unread = new ArrayList<>();
        if (replies == null) return unread;
        for (FeedbackReply reply : replies) {
            if (reply != null && !reply.id.isEmpty() && !readIds.contains(reply.id)) {
                unread.add(reply);
            }
        }
        return unread;
    }

    static String markFeedbackRepliesRead(String readReplyIds, List<FeedbackReply> replies) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(replyIds(readReplyIds));
        if (replies != null) for (FeedbackReply reply : replies) {
            if (reply != null && !reply.id.isEmpty()) ids.add(reply.id);
        }
        while (ids.size() > MAX_READ_REPLY_IDS) ids.remove(ids.iterator().next());
        StringBuilder value = new StringBuilder();
        for (String id : ids) {
            if (value.length() > 0) value.append('\n');
            value.append(id);
        }
        return value.toString();
    }

    static void uploadSnapshotAsync(Context context, Callback<DiagnosticResult> callback) {
        uploadSnapshotAsync(context, "", callback);
    }

    static void uploadSnapshotAsync(Context context, String feedbackId,
                                    Callback<DiagnosticResult> callback) {
        uploadDiagnosticAsync(context, "/diagnostics", "snapshot", feedbackId,
                CrashReporter.snapshot(context), callback);
    }

    static void uploadPendingCrashAsync(Context context, Callback<DiagnosticResult> callback) {
        Context appContext = context.getApplicationContext();
        JSONObject pending = CrashReporter.pending(appContext);
        if (pending == null) {
            complete(callback, new DiagnosticResult(true, "", ""));
            return;
        }
        uploadDiagnosticAsync(appContext, "/diagnostics/crash", "crash", "", pending, result -> {
            if (result.success) CrashReporter.clearPending(appContext);
            complete(callback, result);
        });
    }

    private static void uploadDiagnosticAsync(Context context, String path, String kind,
                                              String feedbackId, JSONObject diagnostic,
                                              Callback<DiagnosticResult> callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            try {
                JSONObject body = baseBody(appContext);
                body.put("kind", kind);
                if (feedbackId != null && !feedbackId.trim().isEmpty()) {
                    body.put("feedbackId", feedbackId.trim());
                }
                body.put("summary", diagnostic.optString("summary"));
                body.put("details", diagnostic.optString("details"));
                JSONObject response = post(path, body);
                complete(callback, new DiagnosticResult(true, response.optString("id"), ""));
            } catch (Throwable error) {
                complete(callback, new DiagnosticResult(false, "", safeMessage(error)));
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
        String existing = AppPreferences.get(context).getString(AppPreferences.KEY_COMMUNITY_CLIENT_ID, "");
        if (existing != null && !existing.isEmpty()) return existing;
        String generated = UUID.randomUUID().toString();
        AppPreferences.get(context).edit().putString(AppPreferences.KEY_COMMUNITY_CLIENT_ID, generated).apply();
        return generated;
    }

    private static void rememberTicket(Context context, String id, String token) {
        if (id == null || id.isEmpty() || token == null || token.isEmpty()) return;
        JSONArray next = new JSONArray();
        try {
            JSONArray old = storedTickets(context);
            for (int index = 0; index < old.length() && next.length() < MAX_TICKETS - 1; index++) {
                JSONObject ticket = old.optJSONObject(index);
                if (ticket != null && !id.equals(ticket.optString("id"))) next.put(ticket);
            }
            JSONObject ticket = new JSONObject();
            ticket.put("id", id);
            ticket.put("token", token);
            JSONArray result = new JSONArray();
            result.put(ticket);
            for (int index = 0; index < next.length(); index++) result.put(next.optJSONObject(index));
            AppPreferences.get(context).edit().putString(AppPreferences.KEY_FEEDBACK_TICKETS,
                    result.toString()).apply();
        } catch (Throwable ignored) { }
    }

    private static JSONArray storedTickets(Context context) {
        try {
            JSONArray stored = new JSONArray(AppPreferences.get(context).getString(
                    AppPreferences.KEY_FEEDBACK_TICKETS, "[]"));
            JSONArray valid = new JSONArray();
            for (int index = 0; index < stored.length() && valid.length() < MAX_TICKETS; index++) {
                JSONObject ticket = stored.optJSONObject(index);
                if (ticket != null && !ticket.optString("id").isEmpty()
                        && !ticket.optString("token").isEmpty()) valid.put(ticket);
            }
            return valid;
        } catch (Throwable ignored) {
            return new JSONArray();
        }
    }

    private static Set<String> replyIds(String value) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (value == null || value.isEmpty()) return ids;
        for (String id : value.split("\\n")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) ids.add(trimmed);
        }
        return ids;
    }

    private static String appVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            return String.valueOf(info.versionName) + " (" + code + ")";
        } catch (Throwable ignored) { return "unknown"; }
    }

    private static JSONObject post(String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            byte[] encoded = body.toString().getBytes(StandardCharsets.UTF_8);
            connection = HttpCompat.open(API_BASE + path);
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(path.startsWith("/diagnostics") ? 30_000 : 7_000);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(encoded.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Lyrics-Companion/Android");
            try (OutputStream output = connection.getOutputStream()) { output.write(encoded); }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String content = readBounded(input);
            if (!content.trim().startsWith("{")) throw new IllegalStateException("服务暂不可用");
            JSONObject response = new JSONObject(content);
            if (status < 200 || status >= 300 || !response.optBoolean("ok", false)) {
                if (status == 429) throw new IllegalStateException("提交太频繁，请稍后再试");
                throw new IllegalStateException(response.optString("error", "服务器返回 " + status));
            }
            return response;
        } finally { if (connection != null) connection.disconnect(); }
    }

    private static String readBounded(InputStream input) throws Exception {
        if (input == null) return "";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4 * 1024]; int total = 0; int count;
            while ((count = stream.read(buffer)) >= 0) {
                if (count == 0) continue;
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("服务器响应过大");
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static <T> void complete(Callback<T> callback, T result) { if (callback != null) callback.complete(result); }
    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message.trim();
    }

    interface Callback<T> { void complete(T result); }
    static final class OnlineStatus { final int online; final String error; OnlineStatus(int online, String error) { this.online = online; this.error = error; } boolean available() { return online >= 0; } }
    static final class FeedbackResult { final boolean success; final String id; final String error; FeedbackResult(boolean success, String id, String error) { this.success = success; this.id = id; this.error = error; } }
    static final class FeedbackReply { final String id, feedbackId, createdAt, message; FeedbackReply(String id, String feedbackId, String createdAt, String message) { this.id = id; this.feedbackId = feedbackId; this.createdAt = createdAt; this.message = message; } }
    static final class FeedbackRepliesResult { final boolean success; final List<FeedbackReply> replies; final String error; FeedbackRepliesResult(boolean success, List<FeedbackReply> replies, String error) { this.success = success; this.replies = replies; this.error = error; } }
    static final class DiagnosticResult { final boolean success; final String id; final String error; DiagnosticResult(boolean success, String id, String error) { this.success = success; this.id = id; this.error = error; } }
}
