package com.zuoqirun.lyricscompanion;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded lyric HTTP with transient retries and disconnectable active calls. */
final class LyricHttp {
    private static final int MAX_BYTES = 12 * 1024 * 1024;
    private static final ConcurrentHashMap<Thread, HttpURLConnection> ACTIVE =
            new ConcurrentHashMap<>();
    private LyricHttp() {}

    static String get(String address, String referer) throws Exception {
        return request("GET", address, referer, null);
    }

    static String get(String address, String referer, Map<String, String> headers)
            throws Exception {
        return new String(execute("GET", address, referer, null, headers, Timeouts.DEFAULT),
                StandardCharsets.UTF_8);
    }

    static byte[] getBytes(String address, String referer) throws Exception {
        return execute("GET", address, referer, null, null, Timeouts.DEFAULT);
    }

    /** 让某条通道用自己的超时与重试次数（酷我逐字通道要快速失败，issue #74）。 */
    static byte[] getBytes(String address, String referer, Timeouts timeouts) throws Exception {
        return execute("GET", address, referer, null, null, timeouts);
    }

    /**
     * 一次请求的超时与重试策略。默认那组是给「一定要拿到」的主接口用的；锦上添花的通道应该用更短的
     * 一组，免得每首歌都先等一次连接超时。
     */
    static final class Timeouts {
        static final Timeouts DEFAULT = new Timeouts(7_000, 10_000, 3);
        /** 逐字通道：连接 3 秒 / 读取 4 秒、只试一次（issue #74）。 */
        static final Timeouts WORD_CHANNEL = new Timeouts(
                WordChannelGate.WORD_CHANNEL_CONNECT_TIMEOUT_MS,
                WordChannelGate.WORD_CHANNEL_READ_TIMEOUT_MS,
                WordChannelGate.WORD_CHANNEL_ATTEMPTS);

        final int connectMs;
        final int readMs;
        final int attempts;

        Timeouts(int connectMs, int readMs, int attempts) {
            this.connectMs = Math.max(1_000, connectMs);
            this.readMs = Math.max(1_000, readMs);
            this.attempts = Math.max(1, attempts);
        }
    }

    static String request(String method, String address, String referer, String body)
            throws Exception {
        return new String(execute(method, address, referer, body, null, Timeouts.DEFAULT),
                StandardCharsets.UTF_8);
    }

    static String request(String method, String address, String referer, String body,
                          Map<String, String> headers) throws Exception {
        return new String(execute(method, address, referer, body, headers, Timeouts.DEFAULT),
                StandardCharsets.UTF_8);
    }

    static void cancel(Thread thread) {
        HttpURLConnection connection = ACTIVE.get(thread);
        if (connection != null) connection.disconnect();
    }

    private static byte[] execute(String method, String address, String referer, String body,
                                  Map<String, String> headers, Timeouts timeouts)
            throws Exception {
        int attempts = Math.max(1, timeouts.attempts);
        for (int attempt = 0; attempt < attempts; attempt++) {
            checkInterrupted();
            try {
                return executeOnce(method, address, referer, body, headers, timeouts);
            } catch (HttpStatusException error) {
                if (error.status < 500 || attempt == attempts - 1) throw error;
            } catch (IOException error) {
                checkInterrupted();
                if (attempt == attempts - 1) throw error;
            }
            Thread.sleep(attempt == 0 ? 250L : 650L);
        }
        throw new IOException("Lyric HTTP retry limit");
    }

    private static byte[] executeOnce(String method, String address, String referer, String body,
                                      Map<String, String> headers, Timeouts timeouts)
            throws Exception {
        HttpURLConnection connection = HttpCompat.open(LyricSourceRules.rewrite(address));
        ACTIVE.put(Thread.currentThread(), connection);
        try {
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeouts.connectMs);
            connection.setReadTimeout(timeouts.readMs);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 Lyrics-Companion/1.0");
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            if (referer != null && !referer.isEmpty()) connection.setRequestProperty("Referer", referer);
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }
            }
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded; charset=UTF-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(bytes);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) throw new HttpStatusException(status);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    checkInterrupted();
                    if (count > 0) {
                        if (output.size() + count > MAX_BYTES) {
                            throw new IOException("Lyric response exceeds size limit");
                        }
                        output.write(buffer, 0, count);
                    }
                }
                return output.toByteArray();
            }
        } finally {
            ACTIVE.remove(Thread.currentThread(), connection);
            connection.disconnect();
        }
    }

    static String encode(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.name());
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }

    private static final class HttpStatusException extends IOException {
        final int status;
        HttpStatusException(int status) { super("HTTP " + status); this.status = status; }
    }
}
