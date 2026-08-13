package com.zuoqirun.lyricscompanion;

/** Window selection and sizing for the text-only lyric style. */
final class PureLyricLayout {
    static final float TRANSLATION_SCALE = 0.60f;
    static final float TRANSLATION_GAP_RATIO = 0.14f;

    private PureLyricLayout() { }

    static int windowStart(int total, int currentIndex, int requestedCount) {
        int count = Math.max(1, Math.min(requestedCount, total));
        int before = (count - 1) / 2;
        return Math.max(0, Math.min(Math.max(0, total - count), currentIndex - before));
    }

    static float constrainedCurrentSize(float requestedSize, float availableHeight,
                                        int lineCount, int translatedLineCount,
                                        boolean currentTranslated, float secondaryScale) {
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
                + (count - 1) * entryGapRatio(count);
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
