package com.zuoqirun.lyricscompanion;

/** Detects timed and live lyric changes without advancing beyond the playback clock. */
final class LyricEffectTimeline {
    private boolean initialized;
    private String source = "";
    private String title = "";
    private String artist = "";
    private String text = "";
    private long lineStartMs;
    private long entryPositionMs = -1L;

    void reset() {
        initialized = false;
        entryPositionMs = -1L;
    }

    void update(String source, String title, String artist, String text,
                long lineStartMs, long positionMs) {
        boolean changed = !this.source.equals(source) || !this.title.equals(title)
                || !this.artist.equals(artist) || !this.text.equals(text)
                || this.lineStartMs != lineStartMs;
        if (!initialized || changed) {
            entryPositionMs = initialized && !text.isEmpty() ? positionMs : -1L;
            this.source = source;
            this.title = title;
            this.artist = artist;
            this.text = text;
            this.lineStartMs = lineStartMs;
            initialized = true;
        }
    }

    long age(long positionMs) {
        return entryPositionMs < 0L || positionMs < 0L ? -1L
                : Math.max(0L, positionMs - entryPositionMs);
    }
}
