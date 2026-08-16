package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TrailingAccentEffectTest {
    @Test public void waitsForTheSustainThenBreathesAndSettles() {
        assertEquals(0f, TrailingAccentEffect.intensity(.15f), .0001f);
        assertTrue(TrailingAccentEffect.intensity(.58f) > .9f);
        assertEquals(0f, TrailingAccentEffect.intensity(1f), .0001f);
    }
}
