package com.zuoqirun.lyricscompanion;

/** Motion curve for a word-timed phrase-ending sustain: a calm bloom, never a strobe. */
final class TrailingAccentEffect {
    private TrailingAccentEffect() { }

    static float intensity(float wordProgress) {
        float progress = Math.max(0f, Math.min(1f, wordProgress));
        if (progress < .16f) return 0f;
        float sustain = (progress - .16f) / .84f;
        // One soft breath that settles naturally at the end of the held syllable.
        return (float) Math.sin(Math.PI * sustain);
    }
}
