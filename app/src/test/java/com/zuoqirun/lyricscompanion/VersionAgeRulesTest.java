package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class VersionAgeRulesTest {
    @Test public void datesHandleYearAndLeapDayWithoutTreatingCodesAsVersionCounts() {
        assertEquals(30, VersionAgeRules.daysBehind("20260906-abcd", "20261006-efgh"));
        assertEquals(1, VersionAgeRules.daysBehind("20240228-a", "20240229-b"));
        assertEquals(1, VersionAgeRules.daysBehind("20251231-a", "20260101-b"));
        assertEquals(0, VersionAgeRules.daysBehind("20261007-a", "20261006-b"));
        assertEquals(-1, VersionAgeRules.daysBehind("1.0", "20261006-b"));
        assertEquals(-1, VersionAgeRules.daysBehind("20260230-a", "20261006-b"));
    }

    @Test public void warnOnlyWhenKnownOutdatedAndSubstantiallyBehind() {
        assertTrue(VersionAgeRules.shouldWarn(true, 30, -1));
        assertTrue(VersionAgeRules.shouldWarn(true, 1, 3));
        assertFalse(VersionAgeRules.shouldWarn(false, 100, 10));
        assertFalse(VersionAgeRules.shouldWarn(true, -1, -1));
        assertFalse(VersionAgeRules.shouldWarn(true, 29, 2));
    }
}
