package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.lang.ref.WeakReference;

final class AlbumArtLoader {
    private static volatile boolean lowResourceMode;
    private static WeakReference<Bitmap> sharedSource = new WeakReference<>(null);
    private static WeakReference<Bitmap> sharedScaled = new WeakReference<>(null);
    private static final int MAX_INPUT_BYTES = 8 * 1024 * 1024;
    private static final int MAX_CACHE_KIB = 8 * 1024;
    private static final LruCache<String, Bitmap> MEMORY_CACHE =
            new LruCache<String, Bitmap>(MAX_CACHE_KIB) {
                @Override protected int sizeOf(String key, Bitmap bitmap) {
                    return Math.max(1, bitmap.getAllocationByteCount() / 1024);
                }
            };

    private AlbumArtLoader() {}

    static Bitmap load(Context context, String address) {
        setLowResourceMode(AppPreferences.lowResourceMode(context));
        if (address == null || address.trim().isEmpty()) return null;
        Bitmap cached = MEMORY_CACHE.get(address);
        if (cached != null && !cached.isRecycled()) return cached;
        Bitmap bitmap = null;
        InputStream input = null;
        HttpURLConnection connection = null;
        try {
            Uri uri = Uri.parse(address);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                connection = HttpCompat.open(address);
                connection.setConnectTimeout(7_000);
                connection.setReadTimeout(10_000);
                connection.setRequestProperty("User-Agent", "Lyrics-Companion/1.0");
                input = connection.getInputStream();
            } else {
                input = context.getContentResolver().openInputStream(uri);
            }
            EncodedBuffer encoded = readEncoded(input);
            bitmap = decodeSampled(encoded, ResourceProfile.artSide(lowResourceMode));
        } catch (Throwable ignored) {
            bitmap = null;
        } finally {
            try { if (input != null) input.close(); } catch (Exception ignored) { }
            if (connection != null) connection.disconnect();
        }
        if (bitmap != null) {
            MEMORY_CACHE.put(address, bitmap);
            MEMORY_CACHE.trimToSize(ResourceProfile.artCacheKiB(lowResourceMode));
        }
        return bitmap;
    }

    static void clearMemoryCache() {
        MEMORY_CACHE.evictAll();
    }

    static synchronized void setLowResourceMode(boolean low) {
        if (lowResourceMode == low) return;
        lowResourceMode = low;
        MEMORY_CACHE.evictAll();
        sharedSource.clear();
        sharedScaled.clear();
    }

    static int cachedKiB() { return MEMORY_CACHE.size(); }

    /** The bitmap may belong to a MediaSession or live snapshot: never recycle it. */
    static synchronized Bitmap limitShared(Bitmap bitmap, boolean low) {
        if (bitmap == null || bitmap.isRecycled() || !low) return bitmap;
        int largest = Math.max(bitmap.getWidth(), bitmap.getHeight());
        int side = ResourceProfile.artSide(true);
        if (largest <= side) return bitmap;
        Bitmap cached = sharedScaled.get();
        if (sharedSource.get() == bitmap && cached != null && !cached.isRecycled()) return cached;
        float ratio = side / (float) largest;
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, Math.round(bitmap.getWidth() * ratio)),
                Math.max(1, Math.round(bitmap.getHeight() * ratio)), true);
        sharedSource = new WeakReference<>(bitmap);
        sharedScaled = new WeakReference<>(scaled);
        return scaled;
    }

    private static EncodedBuffer readEncoded(InputStream input) throws Exception {
        EncodedBuffer output = new EncodedBuffer(64 * 1024);
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) continue;
            total += count;
            if (total > MAX_INPUT_BYTES) return null;
            output.write(buffer, 0, count);
        }
        return output;
    }

    private static Bitmap decodeSampled(EncodedBuffer encoded, int maxSide) {
        if (encoded == null || encoded.length() == 0) return null;
        byte[] data = encoded.data();
        int length = encoded.length();
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sample = 1;
        int largest = Math.max(bounds.outWidth, bounds.outHeight);
        while (largest / (sample * 2) > maxSide) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decoded = BitmapFactory.decodeByteArray(data, 0, length, options);
        return decoded == null ? null : constrain(decoded, maxSide);
    }

    private static final class EncodedBuffer extends ByteArrayOutputStream {
        EncodedBuffer(int initialSize) {
            super(initialSize);
        }

        byte[] data() {
            return buf;
        }

        int length() {
            return count;
        }
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
