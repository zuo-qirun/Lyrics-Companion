package com.zuoqirun.lyricscompanion;

/** Metadata completion is not a track change; inactive gaps preserve the last identity. */
final class TrackIdentity {
    private String source = "", title = "", artist = "", key = "";

    String update(String source, String title, String artist) {
        if (title.isEmpty()) return "";
        boolean same = source.equals(this.source) && title.equals(this.title)
                && (artist.isEmpty() || this.artist.isEmpty() || artist.equals(this.artist));
        if (!same || key.isEmpty()) key = source + '\n' + title + '\n' + artist;
        this.source = source;
        this.title = title;
        if (!same || !artist.isEmpty()) this.artist = artist;
        return key;
    }
}
