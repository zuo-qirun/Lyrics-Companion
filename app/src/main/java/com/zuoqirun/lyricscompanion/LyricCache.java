package com.zuoqirun.lyricscompanion;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class LyricCache {
    private static final long MAX_AGE_MS = 30L * 24L * 60L * 60L * 1000L;
    private final File directory;

    LyricCache(Context context, String provider) {
        directory = new File(context.getCacheDir(), "lyrics_" + provider + "_v1");
    }

    String read(String key) {
        File file = file(key);
        if (!file.isFile() || System.currentTimeMillis() - file.lastModified() > MAX_AGE_MS) {
            return null;
        }
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[(int) Math.min(file.length(), 2_000_000L)];
            int offset = 0;
            int count;
            while (offset < buffer.length
                    && (count = input.read(buffer, offset, buffer.length - offset)) > 0) {
                offset += count;
            }
            return new String(buffer, 0, offset, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    void write(String key, String value) {
        if (value == null || value.isEmpty()) return;
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) return;
            try (FileOutputStream output = new FileOutputStream(file(key))) {
                output.write(value.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { }
    }

    private File file(String key) {
        String safe = key == null ? "unknown" : key.replaceAll("[^A-Za-z0-9_-]", "_");
        return new File(directory, safe + ".lrc");
    }
}
