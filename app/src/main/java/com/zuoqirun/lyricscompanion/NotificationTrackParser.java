package com.zuoqirun.lyricscompanion;

import java.util.Locale;

/** Conservative text rules shared by standard and custom music notifications. */
final class NotificationTrackParser {
    static final class Track {
        final String title, artist, mediaUri;
        Track(String title, String artist, String mediaUri) {
            this.title = title; this.artist = artist; this.mediaUri = mediaUri;
        }
    }

    static Track parse(String title, String text, String subText, String label) {
        title = clean(title); text = clean(text); subText = clean(subText);
        if (isNoise(title, label)) {
            title = text;
            text = subText;
        }
        if (isNoise(title, label)) return null;
        String uri = title.startsWith("/") || title.startsWith("file://") ? title : "";
        String artist = isNoise(text, label) ? "" : text;
        // Common layouts: TITLE=song, TEXT=artist · album / artist - album.
        if (!artist.isEmpty()) artist = artist.split("\\s+[·•|｜–—-]\\s+", 2)[0].trim();
        if (artist.isEmpty()) {
            String[] composite = title.split("\\s+[-–—|｜]\\s+", 2);
            if (composite.length == 2) {
                // Preserve ambiguous song/artist ordering for LocalTrackQueryRules.
                return new Track(title, "", uri);
            }
        }
        return new Track(title, artist, uri);
    }

    static boolean isNoise(String value, String label) {
        String v = clean(value).toLowerCase(Locale.ROOT);
        return v.isEmpty() || SessionMetadataRules.isServiceTitle(value, label)
                || v.matches("(?:正在运行|运行中|已启动|后台运行|正在播放|正在缓冲|加载中|已暂停|暂停播放|播放|暂停|上一首|下一首|关闭|收藏|喜欢|未知歌手|未知艺术家|unknown artist|unknown|play|pause|next|previous|playing|paused)")
                || v.matches("\\d{1,3}:\\d{2}(?:\\s*/\\s*\\d{1,3}:\\d{2})?")
                || v.contains("下载") || v.contains("升级") || v.contains("更新版本")
                || v.contains("广告") || v.contains("点击领取") || v.contains("登录");
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
}
