package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class AwlrcCodecTest {
    @Test public void lxRelativeWordsBecomeAbsoluteMilliseconds() {
        String yrc = AwlrcCodec.toYrc("[00:01.000]<50,150>你<200,200>好");
        LrcTimeline.At at = LrcTimeline.parse("", "", yrc).at(1_250L);
        assertEquals("你好", at.lyric);
        assertEquals("好", at.currentWord);
        assertEquals(250, at.wordProgressPermille);
    }
}
