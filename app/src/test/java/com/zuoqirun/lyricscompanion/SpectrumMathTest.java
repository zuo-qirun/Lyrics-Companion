package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SpectrumMathTest {
    @Test public void logarithmicBandsStayOrderedAndBounded() {
        int low = SpectrumMath.bandForFrequency(80f, 48_000f);
        int mid = SpectrumMath.bandForFrequency(1_000f, 48_000f);
        int high = SpectrumMath.bandForFrequency(12_000f, 48_000f);

        assertTrue(low >= 0);
        assertTrue(mid > low);
        assertTrue(high > mid);
        assertEquals(-1, SpectrumMath.bandForFrequency(30f, 48_000f));
    }

    @Test public void fftEnergyAppearsInItsFrequencyBand() {
        byte[] fft = new byte[256];
        // The first usable FFT point is 48 kHz / 256 = 187.5 Hz. Its display band
        // must also populate the leading low-frequency bands that have no FFT point.
        fft[2] = 100;
        float[] levels = new SpectrumMath.Analyzer().process(fft, 48_000_000);
        int expected = SpectrumMath.bandForFrequency(187.5f, 48_000f);

        assertTrue(levels[expected] > 0f);
        assertTrue("empty low-frequency display bands inherit nearby FFT energy", levels[0] > 0f);
    }

    @Test public void barsFallSmoothlyAndAcceptANewPeakImmediately() {
        SpectrumMath.BarTracker tracker = new SpectrumMath.BarTracker(1);
        tracker.update(new float[]{1f}, 1_000L);
        tracker.update(new float[]{0f}, 1_100L);

        assertTrue(tracker.barAt(0) > 0f && tracker.barAt(0) < 1f);
        tracker.update(new float[]{0.95f}, 1_120L);
        assertEquals(0.95f, tracker.barAt(0), 0.0001f);
    }
}
