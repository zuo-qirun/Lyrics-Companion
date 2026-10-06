package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricEffectRulesTest {
    @Test public void catalogueHasFifteenEffectsAndConsistentUniqueSettingsValues() {
        String[] values = LyricEffectCatalog.values(false);
        String[] labels = LyricEffectCatalog.labels(false);
        assertEquals(16, values.length);
        assertEquals(values.length, labels.length);
        java.util.Set<String> unique = new java.util.HashSet<>();
        for (int i = 0; i < values.length; i++) {
            assertTrue(unique.add(values[i]));
            assertEquals(values[i], LyricEffectRules.normalize(values[i]));
            assertEquals(labels[i], LyricEffectCatalog.find(values[i]).label);
        }
    }

    @Test public void topStripOnlyOffersIndependentStaticEffects() {
        assertArrayEquals(new String[]{"none", "mirror", "frost"}, LyricEffectCatalog.values(true));
        assertEquals("none", LyricEffectRules.normalizeTop("flame"));
        assertEquals("none", LyricEffectRules.normalizeTop("unknown"));
        assertTrue(LyricEffectRules.enabled("mirror", 50, 0));
        assertTrue(LyricEffectRules.enabled("frost", 50, 0));
        assertFalse(LyricEffectRules.enabled("rainbow", 50, 0));
        assertFalse(LyricEffectRules.enabled("mirror", 0, 30));
        assertFalse(LyricEffectRules.enabled("none", 100, 60));
        assertFalse(AppPreferences.isStyleScopedKey(AppPreferences.KEY_TOP_LYRIC_EFFECT));
    }

    @Test public void staticPausedAndDisabledEffectsNeverDemandExtraFrames() {
        for (String effect : new String[]{"none", "mirror", "frost"}) {
            assertEquals(750L, LyricEffectRules.frameDelay(effect, 50, 30, true, 100L, 100, 750L));
        }
        assertEquals(400L, LyricEffectRules.frameDelay("flame", 50, 30, false, 100L, 100, 400L));
        assertEquals(100L, LyricEffectRules.frameDelay("neon", 0, 60, true, 100L, 100, 100L));
        assertEquals(100L, LyricEffectRules.frameDelay("rainbow", 50, 0, true, 100L, 100, 100L));
    }

    @Test public void animatedEffectsHonorFpsEvenWhenOtherAnimationsWantSixty() {
        assertEquals(42L, LyricEffectRules.frameDelay("ripple", 50, 24, true, 100L, 100, 16L));
        assertEquals(33L, LyricEffectRules.frameDelay("glitch", 50, 30, true, 100L, 100, 100L));
        assertEquals(17L, LyricEffectRules.frameDelay("sparkle", 50, 60, true, 100L, 100, 400L));
        assertEquals(100L, LyricEffectRules.frameDelay("bounce", 50, 30, true, 1000L, 100, 100L));
        assertEquals(100L, LyricEffectRules.frameDelay("cinema", 50, 30, true, -1L, 100, 100L));
    }

    @Test public void bounceSettlesAndChangesWithSpeed() {
        assertEquals(0f, LyricEffectRules.bounce(-1L, 100), 0f);
        assertEquals(0f, LyricEffectRules.bounce(0L, 100), 0f);
        assertEquals(0f, LyricEffectRules.bounce(600L, 100), 0f);
        assertEquals(LyricEffectRules.bounce(100L, 100), LyricEffectRules.bounce(50L, 200), 0.001f);
        assertTrue(LyricEffectRules.bounce(100L, 100) < 0f);
        assertTrue(LyricEffectRules.bounce(300L, 100) > 0f);
    }

    @Test public void karaokeSelectionDoesNotOverrideSavedEstimatedWordPreference() {
        assertTrue(LyricEffectRules.estimatesWords(false, "karaoke", 50, 30));
        assertFalse(LyricEffectRules.estimatesWords(false, "rainbow", 50, 30));
        assertFalse(LyricEffectRules.estimatesWords(false, "karaoke", 50, 0));
        assertFalse(LyricEffectRules.estimatesWords(false, "karaoke", 0, 30));
        assertTrue(LyricEffectRules.estimatesWords(true, "none", 0, 0));
    }

    @Test public void noiseAndWavesAreDeterministicAndBounded() {
        for (long seed = -100; seed <= 100; seed++) {
            float noise = LyricEffectRules.noise(seed);
            assertEquals(noise, LyricEffectRules.noise(seed), 0f);
            assertTrue(noise >= 0f && noise <= 1f);
            float wave = LyricEffectRules.wave(seed * 100L, 100, 0.3f);
            assertTrue(wave >= -1f && wave <= 1f);
        }
    }

    @Test public void glyphBoundariesPreserveSurrogatesAccentsEmojiAndFlags() {
        assertEquals(1, LyricEffectRules.glyphEnd("歌词", 0));
        assertEquals(2, LyricEffectRules.glyphEnd("e\u0301x", 0));
        assertEquals(2, LyricEffectRules.glyphEnd("\uD83D\uDE00x", 0));
        assertEquals(2, LyricEffectRules.glyphEnd("\u2764\uFE0Fx", 0));
        assertEquals(4, LyricEffectRules.glyphEnd("\uD83C\uDDE8\uD83C\uDDF3x", 0));
        assertEquals(7, LyricEffectRules.glyphEnd("\uD83D\uDC69\uD83C\uDFFD\u200D\uD83D\uDCBBx", 0));
    }
    @Test public void unknownEffectsAndFpsDisableAnimation() {
        assertEquals("none", LyricEffectRules.normalize("unknown"));
        assertEquals("none", LyricEffectRules.normalize(null));
        assertEquals(0, LyricEffectRules.fps(999));
        assertEquals(24, LyricEffectRules.fps(24));
        assertEquals(30, LyricEffectRules.fps(30));
    }

    @Test public void speedControlsPeriodAndEntrySettlesToZero() {
        assertEquals(LyricEffectRules.phase(1200, 100), LyricEffectRules.phase(600, 200), 0.001f);
        assertEquals(1f, LyricEffectRules.entry(0, 100), 0.001f);
        assertEquals(0f, LyricEffectRules.entry(600, 100), 0.001f);
        assertEquals(0f, LyricEffectRules.entry(300, 200), 0.001f);
        assertTrue(LyricEffectRules.entry(300, 100) > 0f);
    }

    @Test public void effectsAreStoredPerScreenAndStyle() {
        for (String key : new String[]{AppPreferences.KEY_LYRIC_EFFECT, AppPreferences.KEY_LYRIC_EFFECT_FPS,
                AppPreferences.KEY_LYRIC_EFFECT_SPEED, AppPreferences.KEY_LYRIC_EFFECT_STRENGTH}) {
            assertTrue(AppPreferences.isStyleScopedKey(key));
        }
    }
}
