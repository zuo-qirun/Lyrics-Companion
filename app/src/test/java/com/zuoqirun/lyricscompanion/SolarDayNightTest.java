package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class SolarDayNightTest {
    private long time(String value) { return java.time.Instant.parse(value).toEpochMilli(); }
    @Test public void equinoxAtEquatorHasDayAndNight() {
        assertFalse(SolarDayNight.isDark(time("2026-03-20T12:00:00Z"), 0, 0));
        assertTrue(SolarDayNight.isDark(time("2026-03-20T00:00:00Z"), 0, 0));
    }
    @Test public void longitudeAndUtcDateLineAreHandled() {
        assertFalse(SolarDayNight.isDark(time("2026-06-21T04:00:00Z"), 31, 121));
        assertTrue(SolarDayNight.isDark(time("2026-06-21T16:00:00Z"), 31, 121));
        assertFalse(SolarDayNight.isDark(time("2026-03-20T00:00:00Z"), 0, 180));
    }
    @Test public void polarDayAndNightDoNotProduceInvalidTimes() {
        assertFalse(SolarDayNight.isDark(time("2026-06-21T00:00:00Z"), 89, 0));
        assertTrue(SolarDayNight.isDark(time("2026-12-21T12:00:00Z"), 89, 0));
        assertFalse(SolarDayNight.isDark(time("2026-12-21T00:00:00Z"), -89, 0));
    }
    @Test public void staleOrDifferentForegroundNeverOverridesTheme() {
        assertEquals("dark", SolarDayNight.foregroundOverride("maps", "maps", true, "dark"));
        assertEquals("", SolarDayNight.foregroundOverride("maps", "maps", false, "dark"));
        assertEquals("", SolarDayNight.foregroundOverride("maps", "player", true, "dark"));
        assertEquals("", SolarDayNight.foregroundOverride("", "", true, "dark"));
    }
}
