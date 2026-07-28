package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

final class AlbumArtLoader {
    private static final int MAX_CACHE_ITEMS = 12;
    private static final Map<String, Bitmap> MEMORY_CACHE =
            new LinkedHashMap<String, Bitmap>(MAX_CACHE_ITEMS, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
                    return size() > MAX_CACHE_ITEMS;
                }
            };

    private AlbumArtLoader() {}

    static Bitmap load(Context context, String address) {
        if (address == null || address.trim().isEmpty()) return null;
        synchronized (MEMORY_CACHE) {
            Bitmap cached = MEMORY_CACHE.get(address);
            if (cached != null && !cached.isRecycled()) return cached;
        }
        Bitmap bitmap = null;
        InputStream input = null;
        HttpURLConnection connection = null;
        try {
            Uri uri = Uri.parse(address);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                connection = (HttpURLConnection) new URL(address).openConnection();
                connection.setConnectTimeout(7_000);
                connection.setReadTimeout(10_000);
                connection.setRequestProperty("User-Agent", "Lyrics-Companion/1.0");
                input = connection.getInputStream();
            } else {
                input = context.getContentResolver().openInputStream(uri);
            }
            bitmap = BitmapFactory.decodeStream(input);
            if (bitmap != null) bitmap = constrain(bitmap, 700);
        } catch (Throwable ignored) {
            bitmap = null;
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            if (connection != null) connection.disconnect();
        }
        if (bitmap != null) {
            synchronized (MEMORY_CACHE) { MEMORY_CACHE.put(address, bitmap); }
        }
        return bitmap;
    }

    private static Bitmap constrain(Bitmap source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        int largest = Math.max(width, height);
        if (largest <= maxSide) return source;
        float scale = maxSide / (float) largest;
        Bitmap resized = Bitmap.createScaledBitmap(source,
                Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
        if (resized != source) source.recycle();
        return resized;
    }
}
