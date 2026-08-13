package com.zuoqirun.lyricscompanion;

/** Window selection and sizing for the text-only lyric style. */
final class PureLyricLayout {
    private PureLyricLayout() { }

    static int windowStart(int total, int currentIndex, int requestedCount) {
        int count = Math.max(1, Math.min(requestedCount, total));
        int before = (count - 1) / 2;
        return Math.max(0, Math.min(Math.max(0, total - count), currentIndex - before));
    }

    static float constrainedCurrentSize(float requestedSize, float availableHeight,
                                        int lineCount, float secondaryScale, float density) {
        int count = Math.max(1, lineCount);
        if (count == 1) return Math.min(requestedSize, availableHeight);
        float safeScale = Math.max(0.35f, secondaryScale);
        float gapRatio = count > 3 ? 0.28f : 0.42f;
        float minimumGap = Math.max(3f * density, 0f);
        float fixedGaps = (count - 1) * minimumGap;
        float scalable = 1f + (count - 1) * (safeScale + gapRatio);
        float capped = Math.max(1f, availableHeight - fixedGaps) / scalable;
        return Math.min(requestedSize, capped);
    }
}
