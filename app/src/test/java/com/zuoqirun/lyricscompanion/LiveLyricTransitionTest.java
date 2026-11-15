package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class LiveLyricTransitionTest {
    @Test public void timedRepeatedLineTransitionsAtEachTimestampAndRespectsDuration() {
        LiveLyricTransition state = new LiveLyricTransition();
        state.update("song", "重复副歌", 1000L, true, true, true, 100, 800);
        assertEquals(1f, state.fraction(800), 0f);
        state.update("song", "重复副歌", 3000L, true, true, true, 200, 800);
        assertEquals(0f, state.fraction(800), 0f);
        state.update("song", "重复副歌", 3000L, true, true, true, 600, 800);
        assertEquals(0.75f, state.fraction(800), 0.001f);
        state.update("song", "重复副歌", 3000L, true, true, true, 1000, 800);
        assertFalse(state.isAnimating(true));
    }

    @Test public void switchingFromLiveToTimedLyricsDoesNotLeaveTheLineInvisible() {
        LiveLyricTransition state = new LiveLyricTransition();
        state.update("song", "第一句", -1L, true, true, true, 100, 300);
        state.update("song", "第二句", 2000L, true, true, true, 200, 300);
        assertTrue(state.isAnimating(true));
        state.update("song", "第二句", 2000L, true, false, true, 250, 300);
        assertEquals(1f, state.fraction(300), 0f);
    }
    @Test public void firstLineIsVisibleAndOnlyChangedTextTransitions() {
        LiveLyricTransition state = new LiveLyricTransition();
        state.update("track", "first", true, true, true, 100, 300);
        assertEquals(1f, state.fraction(300), 0f);
        state.update("track", "second", true, true, true, 200, 300);
        assertEquals(0f, state.fraction(300), 0f);
        state.update("track", " second ", true, true, true, 350, 300);
        assertEquals(0.75f, state.fraction(300), 0.001f);
        state.update("track", "second", true, true, true, 500, 300);
        assertFalse(state.isAnimating(true));
        assertEquals(1f, state.fraction(300), 0f);
    }

    @Test public void pauseFreezesAndResumeDoesNotIncludePausedTime() {
        LiveLyricTransition state = new LiveLyricTransition();
        state.update("track", "a", true, true, true, 100, 300);
        state.update("track", "b", true, true, true, 200, 300);
        state.update("track", "b", true, true, true, 300, 300);
        float before = state.fraction(300);
        state.update("track", "b", true, true, false, 1000, 300);
        assertEquals(before, state.fraction(300), 0f);
        assertFalse(state.isAnimating(false));
        state.update("track", "b", true, true, true, 1050, 300);
        assertEquals(0.75f, state.fraction(300), 0.001f);
    }

    @Test public void songChangeTimedLyricsAndDisabledAnimationResetState() {
        LiveLyricTransition state = new LiveLyricTransition();
        state.update("one", "a", true, true, true, 100, 300);
        state.update("one", "b", true, true, true, 200, 300);
        state.update("two", "c", true, true, true, 210, 300);
        assertEquals(1f, state.fraction(300), 0f);
        state.update("two", "d", false, true, true, 220, 300);
        assertFalse(state.isAnimating(true));
        state.update("two", "e", true, false, true, 230, 300);
        assertEquals(1f, state.fraction(300), 0f);
    }

    @Test public void zeroDurationAndPausedReplacementNeverHideTheLine() {
        LiveLyricTransition state = new LiveLyricTransition();
        state.update("one", "a", true, true, true, 100, 0);
        state.update("one", "b", true, true, true, 200, 0);
        assertEquals(1f, state.fraction(0), 0f);
        state.update("one", "c", true, true, false, 300, 300);
        assertEquals(1f, state.fraction(300), 0f);
    }
}
