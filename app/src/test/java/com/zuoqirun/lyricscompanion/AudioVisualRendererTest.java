package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class AudioVisualRendererTest {
    @Test public void unsupportedBackgroundsCannotStartVisualizationCapture() {
        assertFalse(AudioVisualRenderer.supportsStyle("pure"));
        assertFalse(AudioVisualRenderer.supportsStyle("default"));
        assertFalse(AudioVisualRenderer.supportsStyle("island"));
        assertTrue(AudioVisualRenderer.supportsStyle("compact"));
        assertTrue(AudioVisualRenderer.supportsStyle("refined"));
        assertTrue(AudioVisualRenderer.supportsStyle("folia_original"));
    }

    @Test public void liveSpectrumDrivesEnergyAndPauseStaysStill() {
        assertEquals(.8f, AudioVisualRenderer.level(new float[]{.1f, .8f}, 1, true, true, 0L), 0f);
        assertEquals(.1f, AudioVisualRenderer.level(new float[]{.1f, .8f}, 2, true, true, 0L), 0f);
        assertEquals(AudioVisualRenderer.level(null, 0, false, false, 0L),
                AudioVisualRenderer.level(null, 0, false, false, 5000L), 0f);
        assertNotEquals(AudioVisualRenderer.level(null, 0, false, true, 0L),
                AudioVisualRenderer.level(null, 0, false, true, 500L), .001f);
    }
}
