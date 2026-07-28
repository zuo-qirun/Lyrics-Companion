package com.zuoqirun.lyricscompanion;

import android.graphics.Bitmap;

final class MusicSnapshot {
    static final MusicSnapshot EMPTY = new MusicSnapshot(false, false, "音乐播放器", "", "",
            null, -1L, 0L, false, false, "", LrcTimeline.At.EMPTY);

    final boolean active;
    final boolean playing;
    final String sourceName;
    final String title;
    final String artist;
    final Bitmap albumArt;
    final long durationMs;
    final long positionMs;
    final boolean lyricLoaded;
    final boolean lyricAvailable;
    final String lyricSourceName;
    final LrcTimeline.At lyrics;

    MusicSnapshot(boolean active, boolean playing, String sourceName, String title, String artist,
                  Bitmap albumArt, long durationMs, long positionMs,
                  boolean lyricLoaded, boolean lyricAvailable,
                  String lyricSourceName, LrcTimeline.At lyrics) {
        this.active = active;
        this.playing = playing;
        this.sourceName = sourceName;
        this.title = title;
        this.artist = artist;
        this.albumArt = albumArt;
        this.durationMs = durationMs;
        this.positionMs = positionMs;
        this.lyricLoaded = lyricLoaded;
        this.lyricAvailable = lyricAvailable;
        this.lyricSourceName = lyricSourceName;
        this.lyrics = lyrics;
    }
}
