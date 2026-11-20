package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 规则市场 HTTP 客户端：规则编辑器页与规则市场页共用（spec 4.1 的稳定签名）。
 *
 * <p>所有网络方法都在一个单线程 Executor 上执行，回调也在该线程触发；调用方负责把
 * 结果切回主线程（Activity 里用 {@code runOnUiThread}）。失败时 {@link Result#message}
 * 永远是可直接展示的中文。</p>
 *
 * <p>「我的提交」本地记录写在 {@code AppPreferences.get(context)} 的
 * {@code match_my_submissions} 键里，是 JSON 数组，最多 100 条，按提交时间倒序。
 * 市场页与编辑器页读同一个键。</p>
 */
final class MatchRulesClient {
    private static final String API_BASE = "https://lyrics-companion.zuoqirun.top/api/match-rules";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int CONNECT_TIMEOUT_MS = 7_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    /** 目录 / 规则包响应上限；规则包本身服务端限 64KB，目录留足余量。 */
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private static final String KEY_MY_SUBMISSIONS = "match_my_submissions";
    private static final int MAX_SUBMISSIONS = 100;
    private static final Object SUBMISSIONS_LOCK = new Object();
    /** 与 spec 1 的规则包 id 约束一致（3–64 字符）。 */
    private static final String PACK_ID_PATTERN = "^[a-z0-9][a-z0-9._-]{2,63}$";

    private MatchRulesClient() { }

    // ── 回调与结果 ────────────────────────────────────────────────

    interface Callback<T> {
        void complete(Result<T> result);
    }

    static final class Result<T> {
        final boolean ok;
        final T value;
        final String message;

        Result(boolean ok, T value, String message) {
            this.ok = ok;
            this.value = value;
            this.message = message == null ? "" : message;
        }

        static <T> Result<T> success(T value) {
            return new Result<>(true, value, "");
        }

        static <T> Result<T> failure(String message) {
            return new Result<>(false, null, message == null || message.trim().isEmpty()
                    ? "操作失败，请稍后重试" : message);
        }
    }

    // ── 数据模型（spec 4.1）─────────────────────────────────────

    static final class CatalogItem {
        final String id;
        final String name;
        final String author;
        final String description;
        final String sha256;
        final int version;
        final int downloads;
        final long size;

        CatalogItem(String id, String name, String author, String description, String sha256,
                    int version, int downloads, long size) {
            this.id = id;
            this.name = name;
            this.author = author;
            this.description = description;
            this.sha256 = sha256;
            this.version = version;
            this.downloads = downloads;
            this.size = size;
        }
    }

    static final class SubmitResult {
        final String id;
        final String token;
        final String status;

        SubmitResult(String id, String token, String status) {
            this.id = id;
            this.token = token;
            this.status = status;
        }
    }

    static final class StatusResult {
        final String status;
        final String note;

        StatusResult(String status, String note) {
            this.status = status;
            this.note = note;
        }
    }

    static final class Submission {
        final String id;
        final String token;
        final String name;
        final String status;
        final long submittedAt;

        Submission(String id, String token, String name, String status, long submittedAt) {
            this.id = id;
            this.token = token;
            this.name = name;
            this.status = status;
            this.submittedAt = submittedAt;
        }
    }

    // ── 网络接口 ─────────────────────────────────────────────────

    /** 拉取市场目录（GET /catalog.json）。ETag 忽略，每次刷新都拿完整列表。 */
    static void fetchCatalog(final Callback<List<CatalogItem>> callback) {
        EXECUTOR.execute(() -> {
            try {
                JSONObject json = request("GET", "/catalog.json", null).json;
                JSONArray rules = json.optJSONArray("rules");
                List<CatalogItem> items = new ArrayList<>();
                if (rules != null) {
                    for (int index = 0; index < rules.length(); index++) {
                        JSONObject rule = rules.optJSONObject(index);
                        if (rule == null) continue;
                        String id = trimmed(rule.optString("id", ""));
                        if (id.isEmpty()) continue;
                        String name = trimmed(rule.optString("name", ""));
                        if (name.isEmpty()) name = id;
                        items.add(new CatalogItem(id, name,
                                trimmed(rule.optString("author", "")),
                                trimmed(rule.optString("description", "")),
                                trimmed(rule.optString("sha256", "")),
                                rule.optInt("version", 0),
                                rule.optInt("downloads", 0),
                                rule.optLong("size", 0L)));
                    }
                }
                callback.complete(Result.success(items));
            } catch (Throwable error) {
                callback.complete(Result.failure(message(error)));
            }
        });
    }

    /**
     * 下载并解析一个市场规则包（GET /packs/{id}/{version}.json），
     * 校验响应里的 id/version 与 rule 文本的 SHA-256，再交给
     * {@link MatchRulePack#parse(String)} 做完整校验。
     */
    static void downloadPack(final String id, final int version,
                             final Callback<MatchRulePack> callback) {
        EXECUTOR.execute(() -> {
            try {
                String safeId = trimmed(id);
                if (!safeId.matches(PACK_ID_PATTERN)) {
                    callback.complete(Result.failure("规则 ID 无效，无法下载"));
                    return;
                }
                if (version < 1 || version > 9999) {
                    callback.complete(Result.failure("规则版本号无效，无法下载"));
                    return;
                }
                Response response = request("GET",
                        "/packs/" + safeId + "/" + version + ".json", null);
                JSONObject json = response.json;
                String responseId = trimmed(json.optString("id", ""));
                int responseVersion = json.optInt("version", -1);
                if (!safeId.equals(responseId) || responseVersion != version) {
                    callback.complete(Result.failure("下载的规则包与市场列表不一致（id 或版本不匹配）"));
                    return;
                }
                JSONObject ruleObject = json.optJSONObject("rule");
                if (ruleObject == null) {
                    callback.complete(Result.failure("服务器返回的规则包内容缺失"));
                    return;
                }
                String expectedSha = trimmed(json.optString("sha256", ""))
                        .toLowerCase(Locale.US);
                if (expectedSha.isEmpty()) {
                    callback.complete(Result.failure("服务器未返回规则校验值，已放弃安装"));
                    return;
                }
                // 优先按响应里“原样”的 rule JSON 文本算摘要（服务端对保存的规则串取
                // sha256，重新序列化在 API 19 上可能改变键顺序）；对不上时再试解析后的
                // 紧凑文本，兼容服务端按重新序列化结果计算的实现。
                String rawRule = extractJsonObject(response.content, "rule");
                String actualSha = sha256(rawRule == null ? ruleObject.toString() : rawRule);
                if (!expectedSha.equals(actualSha)) {
                    rawRule = ruleObject.toString();
                    if (!expectedSha.equals(sha256(rawRule))) {
                        callback.complete(Result.failure("规则包 SHA-256 校验失败，已放弃安装"));
                        return;
                    }
                }
                MatchRulePack pack;
                try {
                    pack = MatchRulePack.parse(rawRule);
                } catch (IllegalArgumentException error) {
                    callback.complete(Result.failure(message(error)));
                    return;
                } catch (Throwable error) {
                    callback.complete(Result.failure("规则包解析失败：" + message(error)));
                    return;
                }
                callback.complete(Result.success(pack));
            } catch (Throwable error) {
                callback.complete(Result.failure(message(error)));
            }
        });
    }

    /**
     * 提交规则包到市场（POST /submit，body {@code {rule, description, author}}）。
     * 编辑器拿到 {@link SubmitResult} 后应调用
     * {@link #rememberSubmission(Context, String, String, String)} 记录 id/token。
     */
    static void submit(final MatchRulePack pack, final String description, final String author,
                       final Callback<SubmitResult> callback) {
        EXECUTOR.execute(() -> {
            try {
                if (pack == null) {
                    callback.complete(Result.failure("没有可提交的规则包"));
                    return;
                }
                // spec 2.1 保证 toJsonString() 存在；用它构造 JSONObject 再放进 body，
                // 避免依赖 MatchRulePack 是否额外暴露 toJson()。
                String ruleText = pack.toJsonString();
                if (ruleText == null || ruleText.trim().isEmpty()) {
                    callback.complete(Result.failure("规则包内容为空，无法提交"));
                    return;
                }
                JSONObject body = new JSONObject();
                body.put("rule", new JSONObject(ruleText));
                body.put("description", limit(description, 200));
                body.put("author", limit(author, 40));
                JSONObject json = request("POST", "/submit", body).json;
                String submitId = trimmed(json.optString("id", ""));
                if (submitId.isEmpty()) {
                    callback.complete(Result.failure("服务器没有返回提交编号，请稍后重试"));
                    return;
                }
                callback.complete(Result.success(new SubmitResult(submitId,
                        trimmed(json.optString("token", "")),
                        trimmed(json.optString("status", "pending")))));
            } catch (Throwable error) {
                callback.complete(Result.failure(message(error)));
            }
        });
    }

    /** 查询一条提交的审核状态（GET /status?id=..&token=..）。token 不匹配时服务器返回 403。 */
    static void status(final String id, final String token, final Callback<StatusResult> callback) {
        EXECUTOR.execute(() -> {
            try {
                String safeId = trimmed(id);
                String safeToken = trimmed(token);
                if (safeId.isEmpty() || safeToken.isEmpty()) {
                    callback.complete(Result.failure("提交记录缺少编号或查询令牌，无法刷新状态"));
                    return;
                }
                String path = "/status?id=" + encode(safeId) + "&token=" + encode(safeToken);
                JSONObject json = request("GET", path, null).json;
                callback.complete(Result.success(new StatusResult(
                        trimmed(json.optString("status", "")),
                        trimmed(json.optString("note", "")))));
            } catch (Throwable error) {
                callback.complete(Result.failure(message(error)));
            }
        });
    }

    /** 举报一条市场规则（POST /report，reason ≤200 字符）。 */
    static void report(final String id, final String reason, final Callback<Boolean> callback) {
        EXECUTOR.execute(() -> {
            try {
                String safeId = trimmed(id);
                if (safeId.isEmpty()) {
                    callback.complete(Result.failure("缺少要举报的规则编号"));
                    return;
                }
                JSONObject body = new JSONObject();
                body.put("id", safeId);
                body.put("reason", limit(reason, 200));
                request("POST", "/report", body);
                callback.complete(Result.success(Boolean.TRUE));
            } catch (Throwable error) {
                callback.complete(Result.failure(message(error)));
            }
        });
    }

    // ── 本地提交记录（match_my_submissions）──────────────────────

    /** 记录一条刚提交的规则；同一 id 重复提交会覆盖旧记录。 */
    static void rememberSubmission(Context context, String id, String token, String name) {
        if (context == null) return;
        String safeId = trimmed(id);
        if (safeId.isEmpty()) return;
        Context app = context.getApplicationContext();
        synchronized (SUBMISSIONS_LOCK) {
            JSONArray previous = readSubmissionsArray(app);
            JSONArray next = new JSONArray();
            try {
                JSONObject entry = new JSONObject();
                entry.put("id", safeId);
                entry.put("token", trimmed(token));
                entry.put("name", trimmed(name));
                entry.put("status", "pending");
                entry.put("submittedAt", System.currentTimeMillis());
                next.put(entry);
                for (int index = 0; index < previous.length()
                        && next.length() < MAX_SUBMISSIONS; index++) {
                    JSONObject old = previous.optJSONObject(index);
                    if (old == null) continue;
                    if (safeId.equals(trimmed(old.optString("id", "")))) continue;
                    next.put(old);
                }
            } catch (JSONException error) {
                // 构造失败时保持旧数据不动，不让本地记录变空。
                return;
            }
            saveSubmissions(app, next);
        }
    }

    /** 本地提交记录，按时间倒序（最新在前）。 */
    static List<Submission> submissions(Context context) {
        List<Submission> result = new ArrayList<>();
        if (context == null) return result;
        JSONArray array = readSubmissionsArray(context.getApplicationContext());
        for (int index = 0; index < array.length(); index++) {
            JSONObject item = array.optJSONObject(index);
            if (item == null) continue;
            String id = trimmed(item.optString("id", ""));
            if (id.isEmpty()) continue;
            result.add(new Submission(id,
                    trimmed(item.optString("token", "")),
                    trimmed(item.optString("name", "")),
                    trimmed(item.optString("status", "pending")),
                    item.optLong("submittedAt", 0L)));
        }
        Collections.sort(result, new Comparator<Submission>() {
            @Override public int compare(Submission first, Submission second) {
                if (first.submittedAt == second.submittedAt) return 0;
                return first.submittedAt < second.submittedAt ? 1 : -1;
            }
        });
        return result;
    }

    /** 把服务器返回的审核状态写回本地记录；记录不存在时静默忽略。 */
    static void updateSubmissionStatus(Context context, String id, String status) {
        if (context == null) return;
        String safeId = trimmed(id);
        String safeStatus = trimmed(status);
        if (safeId.isEmpty() || safeStatus.isEmpty()) return;
        Context app = context.getApplicationContext();
        synchronized (SUBMISSIONS_LOCK) {
            JSONArray array = readSubmissionsArray(app);
            boolean changed = false;
            for (int index = 0; index < array.length(); index++) {
                JSONObject item = array.optJSONObject(index);
                if (item == null) continue;
                if (!safeId.equals(trimmed(item.optString("id", "")))) continue;
                try {
                    item.put("status", safeStatus);
                    changed = true;
                } catch (JSONException ignored) { }
            }
            if (changed) saveSubmissions(app, array);
        }
    }

    private static JSONArray readSubmissionsArray(Context context) {
        try {
            SharedPreferences preferences = AppPreferences.get(context);
            String raw = preferences.getString(KEY_MY_SUBMISSIONS, "[]");
            if (raw == null || raw.trim().isEmpty()) return new JSONArray();
            return new JSONArray(raw);
        } catch (Throwable ignored) {
            // 本地记录损坏时按“没有记录”处理，不影响市场页打开。
            return new JSONArray();
        }
    }

    private static void saveSubmissions(Context context, JSONArray array) {
        AppPreferences.get(context).edit()
                .putString(KEY_MY_SUBMISSIONS, array.toString()).apply();
    }

    // ── HTTP 传输层（写法对齐 ConfigurationShareClient）──────────

    private static final class Response {
        final JSONObject json;
        final String content;

        Response(JSONObject json, String content) {
            this.json = json;
            this.content = content;
        }
    }

    private static Response request(String method, String path, JSONObject body) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = HttpCompat.open(API_BASE + path);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            byte[] encoded = null;
            if (body != null) {
                encoded = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(encoded.length);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(encoded);
                }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String content = read(input);
            JSONObject json = null;
            try {
                json = new JSONObject(content);
            } catch (Throwable ignored) {
                // 非 JSON 响应（例如代理错误页）走下面的统一错误文案。
            }
            if (status < 200 || status >= 300 || json == null
                    || !json.optBoolean("ok", false)) {
                String detail = json == null ? "" : trimmed(json.optString("error", ""));
                throw new IllegalStateException(describeHttpFailure(status, detail));
            }
            return new Response(json, content);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String read(InputStream input) throws Exception {
        if (input == null) return "{}";
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) >= 0) {
                if (output.size() + count > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("服务器响应过大，已停止读取");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    /** 从响应原文里抽出某个对象的原始 JSON 文本（按花括号配对，跳过字符串）。 */
    private static String extractJsonObject(String content, String key) {
        if (content == null || key == null) return null;
        int keyIndex = content.indexOf('"' + key + '"');
        if (keyIndex < 0) return null;
        int start = -1;
        for (int index = keyIndex + key.length() + 2; index < content.length(); index++) {
            char character = content.charAt(index);
            if (character == '{') {
                start = index;
                break;
            }
            if (character == ' ' || character == '\n' || character == '\r'
                    || character == '\t' || character == ':') {
                continue;
            }
            return null;
        }
        if (start < 0) return null;
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int index = start; index < content.length(); index++) {
            char character = content.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }
            if (character == '"') {
                inString = true;
            } else if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
                if (depth == 0) return content.substring(start, index + 1);
            }
        }
        return null;
    }

    private static String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder text = new StringBuilder(hash.length * 2);
        for (byte part : hash) text.append(String.format(Locale.US, "%02x", part & 0xff));
        return text.toString();
    }

    private static String describeHttpFailure(int status, String detail) {
        String mapped;
        if (status == 400) {
            mapped = "规则校验未通过";
        } else if (status == 401 || status == 403) {
            mapped = "没有权限执行此操作";
        } else if (status == 404) {
            mapped = "规则不存在或已下架";
        } else if (status == 409) {
            mapped = "规则版本冲突，请刷新市场后重试";
        } else if (status == 429) {
            mapped = "操作过于频繁，请稍后再试";
        } else if (status >= 500) {
            mapped = "规则市场服务器暂时不可用，请稍后再试";
        } else if (status >= 200 && status < 300) {
            mapped = "服务器返回的数据不完整";
        } else {
            mapped = "服务器返回错误（HTTP " + status + "）";
        }
        return detail.isEmpty() ? mapped : mapped + "：" + detail;
    }

    private static String message(Throwable error) {
        if (error == null) return "操作失败，请稍后重试";
        if (error instanceof SocketTimeoutException) {
            return "连接规则市场超时，请检查网络后重试";
        }
        if (error instanceof UnknownHostException || error instanceof ConnectException) {
            return "无法连接规则市场服务器，请检查网络后重试";
        }
        if (error instanceof JSONException) return "服务器返回的数据格式不正确";
        if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
            String text = error.getMessage();
            return text == null || text.trim().isEmpty() ? "规则市场操作失败" : text;
        }
        if (error instanceof java.io.IOException) {
            return "网络请求失败，请检查网络后重试";
        }
        String text = error.getMessage();
        if (text != null && !text.trim().isEmpty()) return "规则市场操作失败：" + text;
        return "规则市场操作失败（" + error.getClass().getSimpleName() + "）";
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    /** 按字数截断（服务端限制 description ≤200、author ≤40、reason ≤200）。 */
    private static String limit(String value, int limit) {
        String text = trimmed(value);
        return text.length() <= limit ? text : text.substring(0, limit);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException ignored) {
            return value;
        }
    }
}
