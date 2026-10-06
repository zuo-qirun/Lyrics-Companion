package com.zuoqirun.lyricscompanion;

/** The same visibility decision supplies both layout height and translated text. */
final class LyricTranslationRules {
    private LyricTranslationRules() { }

    static String select(boolean enabled, String translation, String romaji) {
        if (!enabled) return "";
        if (translation != null && !translation.isEmpty()) return translation;
        return romaji == null ? "" : romaji;
    }
}
