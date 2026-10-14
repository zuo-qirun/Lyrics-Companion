package com.zuoqirun.lyricscompanion;

import java.util.Random;

/** Choose a shared hue once per track; current/inactive contrast is resolved at draw time. */
final class TrackColorSelector {
    private static final int[] COLORS = {0xFFEF4444, 0xFFF97316, 0xFFEAB308, 0xFF84CC16,
            0xFF22C55E, 0xFF14B8A6, 0xFF06B6D4, 0xFF3B82F6, 0xFF6366F1, 0xFFA855F7,
            0xFFD946EF, 0xFFEC4899};
    private final TrackIdentity identity = new TrackIdentity();
    private final Random random;
    private String selectedTrack = "";
    private int index = -1;

    TrackColorSelector(Random random) { this.random = random; }

    int select(String source, String title, String artist) {
        String track = identity.update(source, title, artist);
        if (!track.isEmpty() && !track.equals(selectedTrack)) {
            int next = random.nextInt(COLORS.length - (index < 0 ? 0 : 1));
            if (index >= 0 && next >= index) next++;
            index = next;
            selectedTrack = track;
        }
        return COLORS[index < 0 ? 0 : index];
    }
}
