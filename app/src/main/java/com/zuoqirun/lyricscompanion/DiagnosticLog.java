package com.zuoqirun.lyricscompanion;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Small app-private rolling event log for opt-in diagnostic snapshots. */
final class DiagnosticLog {
    private static final Object LOCK = new Object();
    private static final String DIRECTORY = "diagnostics";
    private static final String FILE_NAME = "events.log";
    private static final int MAX_CHARS = 20_000;

    private DiagnosticLog() {}

    static void record(Context context, String tag, String message) {
        if (context == null) return;
        String entry = System.currentTimeMillis() + " " + clean(tag, 48) + ": "
                + clean(message, 500) + "\n";
        synchronized (LOCK) {
            try {
                File directory = new File(context.getFilesDir(), DIRECTORY);
                if (!directory.exists() && !directory.mkdirs()) return;
                File target = new File(directory, FILE_NAME);
                String next = read(target) + entry;
                if (next.length() > MAX_CHARS) next = next.substring(next.length() - MAX_CHARS);
                File temporary = new File(directory, FILE_NAME + ".tmp");
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    output.write(next.getBytes(StandardCharsets.UTF_8));
                }
                if (target.exists() && !target.delete()) return;
                if (!temporary.renameTo(target)) temporary.delete();
            } catch (Throwable ignored) {
                // Diagnostics must never affect normal playback or overlay rendering.
            }
        }
    }

    static String recent(Context context) {
        if (context == null) return "";
        synchronized (LOCK) {
            try { return read(new File(new File(context.getFilesDir(), DIRECTORY), FILE_NAME)); }
            catch (Throwable ignored) { return ""; }
        }
    }

    private static String read(File file) throws Exception {
        if (!file.isFile()) return "";
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) != -1 && output.size() < MAX_CHARS * 2) {
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
