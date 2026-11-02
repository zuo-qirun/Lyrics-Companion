package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationProgressRulesTest {
    @Test public void progressUnitsMustMatchKnownMediaDuration() {
        assertEquals(90000L, NotificationProgressRules.position(90, 240, false, 240000L));
        assertEquals(90000L, NotificationProgressRules.position(90000, 240000, false, 240000L));
        assertEquals(-1L, NotificationProgressRules.position(50, 100, false, 240000L));
        assertEquals(-1L, NotificationProgressRules.position(90, 240, false, -1L));
        assertEquals(-1L, NotificationProgressRules.position(90, 240, true, 240000L));
        assertEquals(-1L, NotificationProgressRules.position(300, 240, false, 240000L));
    }
}
