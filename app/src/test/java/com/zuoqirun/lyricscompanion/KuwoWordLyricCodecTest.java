package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class KuwoWordLyricCodecTest {
    @Test public void turnsKuwoOffsetsIntoAbsoluteWordTimesAndTrimsOverlap() {
        String enhanced = KuwoWordLyricCodec.toEnhancedTimeline(
                "[00:01.00]<100,0>你<120,20>好");
        assertTrue(enhanced.contains("[1000,120](1050,20,0)你(1070,50,0)好"));
        LrcTimeline timeline = LrcTimeline.parse("", "", enhanced);
        assertEquals("你好", timeline.at(1_090L).lyric);
        assertEquals(400, timeline.at(1_090L).wordProgressPermille);
    }
}
