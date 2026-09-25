package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Stores the result of song matching, so a replay needs neither search nor lyric HTTP calls. */
final class MatchedLyricCache {
    private static final Object LOCK = new Object();
    private final LyricCache cache;
    MatchedLyricCache(Context context) { cache = new LyricCache(context, "matched"); }

    LrcTimeline read(String key) {
        synchronized (LOCK) {
            try {
                String value = cache.read(key);
                return value == null ? LrcTimeline.EMPTY
                        : LrcTimeline.fromCacheBytes(Base64.decode(value, Base64.DEFAULT));
            } catch (Exception ignored) { return LrcTimeline.EMPTY; }
        }
    }

    void write(String key, LrcTimeline timeline) {
        if (timeline.isEmpty()) return;
        synchronized (LOCK) {
            try { cache.write(key, Base64.encodeToString(timeline.toCacheBytes(), Base64.NO_WRAP)); }
            catch (Exception ignored) { }
        }
    }

    boolean needsUpgrade(String key, String provider, LrcTimeline timeline) {
        boolean canEnhance = "netease".equals(provider) || "kuwo".equals(provider)
                || "kugou".equals(provider) || "qqmusic".equals(provider)
                || "migu".equals(provider);
        return canEnhance && (!timeline.hasWordTiming() || !timeline.hasTranslation()
                || "netease".equals(provider) && !timeline.hasRomaji())
                && cache.read(key + "_upgrade_v2") == null;
    }

    void markUpgradeChecked(String key) { cache.write(key + "_upgrade_v2", "checked"); }

    static String key(String provider, String title, String artist, long duration,
                      String directId, String sourcePackage) {
        // Keep version punctuation and exact known duration; avoid mixing live/studio recordings.
        String identity = provider + "\n" + normalize(title) + "\n" + normalize(artist)
                + "\n" + duration + "\n" + directId + "\n"
                + (normalize(artist).isEmpty() ? sourcePackage : "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte b : digest) key.append(String.format(Locale.ROOT, "%02x", b & 255));
            return key.toString();
        } catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
