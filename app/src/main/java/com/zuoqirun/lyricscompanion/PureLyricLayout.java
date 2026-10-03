package com.zuoqirun.lyricscompanion;

/** Window selection and sizing for the text-only lyric style. */
final class PureLyricLayout {
    static final float TRANSLATION_SCALE = 0.60f;
    static final float TRANSLATION_GAP_RATIO = 0.14f;

    private PureLyricLayout() { }

    /**
     * Index of the line that goes into the window's first slot.
     *
     * <p>The window keeps the requested number of slots whatever the song has around the current
     * line, so the current line always lands on the same row. It used to shrink to the lines that
     * exist, which pulled the first line of a song up to the top row of the panel and covered
     * whatever the car draws there (issue #31). A negative index is an empty slot.
     */
    static int windowStart(int currentIndex, int requestedCount) {
        int count = Math.max(1, requestedCount);
        return currentIndex - (count - 1) / 2;
    }

    static float constrainedCurrentSize(float requestedSize, float availableHeight,
                                        int lineCount, int translatedLineCount,
                                        boolean currentTranslated, float secondaryScale) {
        return constrainedCurrentSize(requestedSize, availableHeight, lineCount,
                translatedLineCount, currentTranslated, secondaryScale, 0);
    }

    static float constrainedCurrentSize(float requestedSize, float availableHeight,
                                        int lineCount, int translatedLineCount,
                                        boolean currentTranslated, float secondaryScale,
                                        int titleLines) {
        int count = Math.max(1, lineCount);
        float safeScale = Math.max(0.35f, secondaryScale);
        int translations = Math.max(0, Math.min(count, translatedLineCount));
        int secondaryTranslations = Math.max(0,
                translations - (currentTranslated ? 1 : 0));
        float translatedWeight = (currentTranslated ? 1f : 0f)
                * (TRANSLATION_SCALE + TRANSLATION_GAP_RATIO)
                + secondaryTranslations * safeScale
                * (TRANSLATION_SCALE + TRANSLATION_GAP_RATIO);
        float scalable = 1f + (count - 1) * safeScale + translatedWeight
                + (count - 1) * entryGapRatio(count)
                + (titleLines > 0 ? Math.min(3, titleLines) * 1.22f + 0.3f : 0f);
        float capped = Math.max(1f, availableHeight) / Math.max(1f, scalable);
        return Math.min(requestedSize, capped);
    }

    static float entryGapRatio(int lineCount) {
        return lineCount > 3 ? 0.28f : 0.42f;
    }

    static boolean hasDistinctTranslation(String original, String translated) {
        if (translated == null || translated.trim().isEmpty()) return false;
        return original == null || !translated.trim().equals(original.trim());
    }
}
