package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricTranslationRulesTest {
    @Test public void disablingTranslationAlsoRemovesRomajiFallbackAndItsLayoutRow() {
        assertEquals("", LyricTranslationRules.select(false, "译文", "romaji"));
        assertEquals("", LyricTranslationRules.select(false, "", "romaji"));
    }

    @Test public void enabledTranslationPrefersTranslationThenRomaji() {
        assertEquals("译文", LyricTranslationRules.select(true, "译文", "romaji"));
        assertEquals("romaji", LyricTranslationRules.select(true, "", "romaji"));
        assertEquals("", LyricTranslationRules.select(true, null, null));
    }
}
