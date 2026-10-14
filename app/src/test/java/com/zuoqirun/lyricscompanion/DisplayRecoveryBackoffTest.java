package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DisplayRecoveryBackoffTest {
    @Test public void recoveryContinuesPastTwelveAttemptsAndStaysBoundedForHours() {
        DisplayRecoveryBackoff retry = new DisplayRecoveryBackoff();
        long now = 0;
        for (int i = 0; i < 1000; i++) {
            assertTrue(retry.ready(now));
            long delay = retry.failed(now);
            assertTrue(delay >= 1500 && delay <= 60000);
            if (i > 6) assertEquals(60000, delay);
            assertFalse(retry.ready(now + delay - 1));
            now += delay;
        }
        assertEquals(1000, retry.attempts());
    }

    @Test public void visibilityPollingDoesNotPostponeTheScheduledRecovery() {
        DisplayRecoveryBackoff retry = new DisplayRecoveryBackoff();
        assertEquals(1500, retry.failed(100));
        assertEquals(1000, retry.failed(600));
        assertEquals(500, retry.failed(1100));
        assertEquals(1, retry.attempts());
        assertTrue(retry.ready(1600));
        assertEquals(3000, retry.failed(1600));
    }

    @Test public void successfulRecoveryResetsTheBootCadenceWithoutAffectingOtherTargets() {
        DisplayRecoveryBackoff secondary = new DisplayRecoveryBackoff(), extras = new DisplayRecoveryBackoff();
        secondary.failed(0); extras.failed(0);
        secondary.reset();
        assertTrue(secondary.ready(0));
        assertEquals(0, secondary.attempts());
        assertFalse(extras.ready(100));
        assertEquals(1500, secondary.failed(100));
    }
}
