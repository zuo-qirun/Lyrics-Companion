package com.zuoqirun.lyricscompanion;

/** Grapheme start positions with identical inter-glyph gaps and no trailing gap. */
final class FoliaTextSpacing {
    private FoliaTextSpacing() { }
    static void apply(float[] measuredPrefixes, float gap, float[] offsets) {
        int count = measuredPrefixes.length - 1;
        for (int i = 0; i <= count; i++) offsets[i] = measuredPrefixes[i] + Math.min(i, Math.max(0, count - 1)) * gap;
    }
    static float extent(float[] offsets, int first, int end, float gap) {
        return end <= first ? 0 : offsets[end] - offsets[first] - (end < offsets.length - 1 ? gap : 0);
    }
}
