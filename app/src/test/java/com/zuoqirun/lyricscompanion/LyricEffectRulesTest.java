package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricEffectRulesTest {
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
