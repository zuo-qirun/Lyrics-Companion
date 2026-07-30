package com.zuoqirun.lyricscompanion;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RealtimeCaptionStoreTest {
    @After public void tearDown() { RealtimeCaptionStore.clear(); }

    @Test public void partialDoesNotReplaceConfirmedHistory() {
        RealtimeCaptionStore.status(RealtimeCaptionState.Status.STARTING, "Local", "");
        RealtimeCaptionStore.finalLine("第一句", "zh", "Local");
        RealtimeCaptionStore.partial("第二句的一半", "zh", "Local");
        RealtimeCaptionState state = RealtimeCaptionStore.snapshot();
        assertEquals("第一句", state.finalLines.get(0));
        assertEquals("第二句的一半", state.partialText);
        assertEquals("zh", state.language);
    }

    @Test public void finalLinesAreDeduplicatedAndBounded() {
        for (int i = 0; i < 5; i++) RealtimeCaptionStore.finalLine("第" + i + "句", "zh", "Local");
        RealtimeCaptionStore.finalLine("第4句", "zh", "Local");
        RealtimeCaptionState state = RealtimeCaptionStore.snapshot();
        assertEquals(3, state.finalLines.size());
        assertEquals("第2句", state.finalLines.get(0));
        assertEquals("第4句", state.finalLines.get(2));
    }

    @Test public void clearRemovesTextWhenTrackOrCaptureStops() {
        RealtimeCaptionStore.finalLine("hello", "en", "Local");
        RealtimeCaptionStore.clear();
        RealtimeCaptionState state = RealtimeCaptionStore.snapshot();
        assertEquals(RealtimeCaptionState.Status.OFF, state.status);
        assertTrue(state.finalLines.isEmpty());
        assertFalse(state.isVisible());
    }
}
