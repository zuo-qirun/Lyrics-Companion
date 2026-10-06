package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricEffectTimelineTest {
    @Test public void firstFrameAndSettingsReloadDoNotReplayEntry() {
        LyricEffectTimeline timeline = new LyricEffectTimeline();
        timeline.update("player", "song", "artist", "first", 0L, 5000L);
        assertEquals(-1L, timeline.age(5000L));
        timeline.update("player", "song", "artist", "second", 6000L, 6000L);
        assertEquals(0L, timeline.age(6000L));
        timeline.reset();
        timeline.update("player", "song", "artist", "second", 6000L, 6200L);
        assertEquals(-1L, timeline.age(6200L));
    }

    @Test public void liveLinesWithNoTimestampTriggerEntryAndFreezeAtPausedPosition() {
        LyricEffectTimeline timeline = new LyricEffectTimeline();
        timeline.update("player", "song", "artist", "first", -1L, 1000L);
        timeline.update("player", "song", "artist", "second", -1L, 2000L);
        assertEquals(0L, timeline.age(2000L));
        timeline.update("player", "song", "artist", "second", -1L, 2100L);
        assertEquals(100L, timeline.age(2100L));
        timeline.update("player", "song", "artist", "second", -1L, 2100L);
        assertEquals(100L, timeline.age(2100L));
        assertEquals(-1L, timeline.age(-1L));
    }

    @Test public void repeatedTimedTextAndTrackChangesStillTriggerEntry() {
        LyricEffectTimeline timeline = new LyricEffectTimeline();
        timeline.update("player", "song", "artist", "chorus", 1000L, 1000L);
        timeline.update("player", "song", "artist", "chorus", 2000L, 2000L);
        assertEquals(50L, timeline.age(2050L));
        timeline.update("player", "new song", "artist", "chorus", 2000L, 3000L);
        assertEquals(0L, timeline.age(3000L));
        timeline.update("other player", "new song", "artist", "chorus", 2000L, 3100L);
        assertEquals(0L, timeline.age(3100L));
        timeline.update("other player", "new song", "new artist", "chorus", 2000L, 3200L);
        assertEquals(0L, timeline.age(3200L));
        timeline.update("other player", "new song", "new artist", "", -1L, 3300L);
        assertEquals(-1L, timeline.age(3300L));
    }
}
