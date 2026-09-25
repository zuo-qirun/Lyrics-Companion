package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class MiguMrcCodecTest {
    @Test public void mrcWordsKeepAbsoluteMillisecondStart() {
        String enhanced = MiguMrcCodec.toEnhancedTimeline(
                "[1000,400](1050,150)你(1200,200)好");
        LrcTimeline timeline = LrcTimeline.parse("", "", enhanced);
        assertEquals("你好", timeline.at(1_275L).lyric);
        assertEquals("好", timeline.at(1_275L).currentWord);
        assertEquals(375, timeline.at(1_275L).wordProgressPermille);
    }
}
