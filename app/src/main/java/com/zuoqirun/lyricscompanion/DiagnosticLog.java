package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small app-private rolling event log for opt-in diagnostic snapshots. */
final class DiagnosticLog {
    private static final Object LOCK = new Object();
    private static final String DIRECTORY = "diagnostics";
    private static final String FILE_NAME = "events.log";
    private static final String PREVIOUS_FILE_NAME = "events.previous.log";
    private static final int MAX_FILE_BYTES = 512 * 1024;
    private static final int MAX_ENTRY_CHARS = 8_000;

    private DiagnosticLog() {}

    static void record(Context context, String tag, String message) {
        if (context == null) return;
        synchronized (LOCK) {
            try {
                File directory = new File(context.getFilesDir(), DIRECTORY);
                if (!directory.exists() && !directory.mkdirs()) return;
                File target = new File(directory, FILE_NAME);
                String timestamp = new SimpleDateFormat(
                        "yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(new Date());
                String entry = timestamp + " elapsed=" + SystemClock.elapsedRealtime()
                        + " thread=" + clean(Thread.currentThread().getName(), 80)
                        + " " + clean(tag, 80) + ": "
                        + clean(message, MAX_ENTRY_CHARS) + "\n";
                byte[] encoded = entry.getBytes(StandardCharsets.UTF_8);
                if (target.length() + encoded.length > MAX_FILE_BYTES) {
                    File previous = new File(directory, PREVIOUS_FILE_NAME);
                    if (previous.exists() && !previous.delete()) return;
                    if (target.exists() && !target.renameTo(previous)) return;
                }
                try (FileOutputStream output = new FileOutputStream(target, true)) {
                    output.write(encoded);
                }
            } catch (Throwable ignored) {
                // Diagnostics must never affect normal playback or overlay rendering.
            }
        }
    }

    static String recent(Context context) {
        if (context == null) return "";
        synchronized (LOCK) {
            try {
                File directory = new File(context.getFilesDir(), DIRECTORY);
                String previous = read(new File(directory, PREVIOUS_FILE_NAME));
                String current = read(new File(directory, FILE_NAME));
                return previous + current;
            }
            catch (Throwable ignored) { return ""; }
        }
    }

    private static String read(File file) throws Exception {
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1 && output.size() < MAX_FILE_BYTES) {
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String clean(String value, int maximum) {
        String text = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ');
        return text.length() <= maximum ? text : text.substring(0, maximum);
    }
}
