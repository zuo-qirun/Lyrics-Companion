package com.zuoqirun.lyricscompanion;

import android.graphics.Bitmap;

/** API-neutral snapshot shared by modern MediaSession and Android 4.4 RemoteController. */
final class MusicPlaybackData {
    static final int STATE_NONE = 0;
    static final int STATE_STOPPED = 1;
    static final int STATE_PAUSED = 2;
    static final int STATE_PLAYING = 3;
    static final int STATE_FAST_FORWARDING = 4;
    static final int STATE_REWINDING = 5;
    static final int STATE_BUFFERING = 6;
    static final int STATE_ERROR = 7;
    static final int STATE_CONNECTING = 8;
    static final int STATE_SKIPPING_TO_PREVIOUS = 9;
    static final int STATE_SKIPPING_TO_NEXT = 10;
    static final int STATE_SKIPPING_TO_QUEUE_ITEM = 11;

    final String mediaId;
    final String title;
    final String artist;
    final Bitmap albumArt;
    final String albumArtUri;
    final String mediaUri;
    final long durationMs;
    final boolean statePresent;
    final int state;
    final long positionMs;
    final long positionUpdatedAtElapsedMs;
    final float speed;
    final boolean sessionLyricPresent;
    final String sessionLyric;

    MusicPlaybackData(String mediaId, String title, String artist, Bitmap albumArt,
                      String albumArtUri, long durationMs, boolean statePresent, int state,
                      long positionMs, long positionUpdatedAtElapsedMs, float speed) {
        this(mediaId, title, artist, albumArt, albumArtUri, "", durationMs, statePresent, state,
                positionMs, positionUpdatedAtElapsedMs, speed, false, "");
    }

    MusicPlaybackData(String mediaId, String title, String artist, Bitmap albumArt,
                      String albumArtUri, String mediaUri, long durationMs,
                      boolean statePresent, int state, long positionMs,
                      long positionUpdatedAtElapsedMs, float speed) {
        this(mediaId, title, artist, albumArt, albumArtUri, mediaUri, durationMs,
                statePresent, state, positionMs, positionUpdatedAtElapsedMs, speed, false, "");
    }

    MusicPlaybackData(String mediaId, String title, String artist, Bitmap albumArt,
                      String albumArtUri, String mediaUri, long durationMs,
                      boolean statePresent, int state, long positionMs,
                      long positionUpdatedAtElapsedMs, float speed,
                      boolean sessionLyricPresent, String sessionLyric) {
        this.mediaId = value(mediaId);
        this.title = value(title);
        this.artist = value(artist);
        this.albumArt = albumArt;
        this.albumArtUri = value(albumArtUri);
        this.mediaUri = value(mediaUri);
        this.durationMs = durationMs;
        this.statePresent = statePresent;
        this.state = state;
        this.positionMs = positionMs;
        this.positionUpdatedAtElapsedMs = positionUpdatedAtElapsedMs;
        this.speed = speed;
        this.sessionLyricPresent = sessionLyricPresent;
        this.sessionLyric = value(sessionLyric);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
