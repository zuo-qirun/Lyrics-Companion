package com.zuoqirun.lyricscompanion;

/**
 * 匹配规则引擎的输入：播放器上报的原始元数据。
 *
 * <p>该类不依赖 Android，可在 JVM 单测中直接使用。所有字段做 trim 处理，
 * null 归一为空串，规则引擎永远拿到确定值。</p>
 */
final class MatchInput {

    final String title;
    final String artist;
    final String album;
    final String packageName;
    final String source;

    MatchInput(String title, String artist, String album, String packageName, String source) {
        this.title = value(title);
        this.artist = value(artist);
        this.album = value(album);
        this.packageName = value(packageName);
        this.source = value(source);
    }

    MatchInput(String title, String artist, String packageName, String source) {
        this(title, artist, "", packageName, source);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
