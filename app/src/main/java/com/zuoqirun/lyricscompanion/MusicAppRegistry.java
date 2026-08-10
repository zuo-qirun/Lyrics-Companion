package com.zuoqirun.lyricscompanion;

import java.util.Locale;

final class MusicAppRegistry {
    private static final App[] KNOWN_APPS = {
            new App("netease", "网易云音乐", "com.netease.cloudmusic"),
            new App("netease", "网易云音乐车机版", "com.netease.cloudmusic.iot"),
            new App("qqmusic", "QQ 音乐", "com.tencent.qqmusic"),
            new App("qqmusic", "QQ 音乐", "com.tencent.qqmusiccar"),
            new App("kugou", "酷狗音乐", "com.kugou.android"),
            new App("kugou", "酷狗音乐", "com.kugou.android.auto"),
            new App("kugou", "酷狗概念版", "com.kugou.android.lite"),
            new App("kugou", "酷狗音乐", "com.kugou.auto"),
            new App("kuwo", "酷我音乐", "cn.kuwo.player"),
            new App("kuwo", "酷我音乐", "cn.kuwo.kwmusiccar"),
            new App("kuwo", "酷我音乐", "cn.kuwo.kwmusic"),
            new App("kuwo", "酷我音乐", "cn.kuwo.car"),
            new App("kuwo", "酷我音乐", "com.shaiban.audioplayer.mplayer"),
            new App("spotify", "Spotify", "com.spotify.music"),
            new App("soda", "汽水音乐", "com.luna.music"),
            new App("soda", "汽水音乐", "com.luna.music.car"),
            new App("migu", "咪咕音乐", "cmccwm.mobilemusic"),
            new App("xiaomi", "小米音乐", "com.miui.player"),
            new App("huawei", "华为音乐", "com.android.mediacenter"),
            new App("apple_music", "Apple Music", "com.apple.android.music"),
            new App("youtube_music", "YouTube Music", "com.google.android.apps.youtube.music"),
            new App("amazon_music", "Amazon Music", "com.amazon.mp3")
    };

    private MusicAppRegistry() {}

    static App resolve(String packageName, String applicationLabel) {
        String normalizedPackage = safe(packageName).toLowerCase(Locale.ROOT);
        for (App app : KNOWN_APPS) {
            if (normalizedPackage.equals(app.packagePrefix)) return app;
        }
        for (App app : KNOWN_APPS) {
            if (normalizedPackage.startsWith(app.packagePrefix + ".")) return app;
        }
        App packageFeatureMatch = resolveFeatures(normalizedPackage, normalizedPackage);
        if (packageFeatureMatch != null) return packageFeatureMatch;
        String label = safe(applicationLabel).trim();
        App labelMatch = resolveFeatures(label, normalizedPackage);
        if (labelMatch != null) return labelMatch;
        if (label.isEmpty()) {
            int separator = normalizedPackage.lastIndexOf('.');
            label = separator >= 0 ? normalizedPackage.substring(separator + 1) : normalizedPackage;
        }
        if (label.isEmpty() || "player".equalsIgnoreCase(label)
                || "music".equalsIgnoreCase(label)) {
            label = "音乐播放器";
        }
        return new App("media", label, normalizedPackage, false);
    }

    private static App resolveFeatures(String value, String packageName) {
        String label = safe(value).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}·•]+", "");
        if (containsAny(label, "网易", "netease", "cloudmusic", "163音乐")) {
            return new App("netease", "网易云音乐", packageName);
        }
        if (containsAny(label, "qq", "腾讯音乐", "qqmusic")) {
            return new App("qqmusic", "QQ 音乐", packageName);
        }
        if (containsAny(label, "酷狗", "kugou", "kgmusic")) {
            return new App("kugou", "酷狗音乐", packageName);
        }
        if (containsAny(label, "酷我", "kuwo", "kwmusic")) {
            return new App("kuwo", "酷我音乐", packageName);
        }
        if (containsAny(label, "汽水", "lunamusic", "sodamusic")) {
            return new App("soda", "汽水音乐", packageName);
        }
        return null;
    }

    static String lyricCatalogForSource(String sourceId) {
        switch (safe(sourceId)) {
            case "netease": return "netease";
            case "qqmusic": return "qqmusic";
            case "kugou": return "kugou";
            case "kuwo": return "kuwo";
            case "soda": return "soda";
            default: return "";
        }
    }

    private static boolean containsAny(String value, String... features) {
        for (String feature : features) {
            if (value.contains(feature)) return true;
        }
        return false;
    }

    static int selectionScore(int playbackRank, boolean hasMetadata,
                              boolean supportsTransportControls, boolean known,
                              boolean currentSession) {
        int score = playbackRank;
        if (hasMetadata) score += 200;
        if (supportsTransportControls) score += 40;
        if (currentSession) score += 10;
        return score;
    }

    private static String safe(String value) { return value == null ? "" : value; }

    static final class App {
        final String sourceId;
        final String displayName;
        final String packagePrefix;
        final boolean known;

        App(String sourceId, String displayName, String packagePrefix) {
            this(sourceId, displayName, packagePrefix, true);
        }

        private App(String sourceId, String displayName, String packagePrefix, boolean known) {
            this.sourceId = sourceId;
            this.displayName = displayName;
            this.packagePrefix = packagePrefix;
            this.known = known;
        }
    }
}
