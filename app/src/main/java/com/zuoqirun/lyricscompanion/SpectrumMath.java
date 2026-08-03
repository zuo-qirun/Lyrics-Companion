package com.zuoqirun.lyricscompanion;

/** Pure spectrum conversion and animation state, kept independent from Android audio APIs. */
final class SpectrumMath {
    static final int BAND_COUNT = 32;
    private static final float LOWEST_FREQUENCY_HZ = 60f;
    private static final float HIGHEST_FREQUENCY_HZ = 16_000f;

    private SpectrumMath() { }

    static int bandForFrequency(float frequencyHz, float sampleRateHz) {
        float highest = Math.min(HIGHEST_FREQUENCY_HZ, sampleRateHz * 0.48f);
        if (highest <= LOWEST_FREQUENCY_HZ || frequencyHz < LOWEST_FREQUENCY_HZ) return -1;
        if (frequencyHz >= highest) return BAND_COUNT - 1;
        float normalized = (float) (Math.log(frequencyHz / LOWEST_FREQUENCY_HZ)
                / Math.log(highest / LOWEST_FREQUENCY_HZ));
        return Math.max(0, Math.min(BAND_COUNT - 1, (int) (normalized * BAND_COUNT)));
    }

    static final class Analyzer {
        private final float[] smoothed = new float[BAND_COUNT];
        private float referenceLevel;

        float[] process(byte[] fft, int samplingRateMilliHz) {
            float[] sums = new float[BAND_COUNT];
            int[] counts = new int[BAND_COUNT];
            float sampleRateHz = samplingRateMilliHz / 1000f;
            if (fft == null || fft.length < 4 || sampleRateHz <= 0f) return new float[BAND_COUNT];
            int complexBins = fft.length / 2;
            for (int bin = 1; bin < complexBins; bin++) {
                int index = bin * 2;
                if (index + 1 >= fft.length) break;
                int band = bandForFrequency(bin * sampleRateHz / fft.length, sampleRateHz);
                if (band < 0) continue;
                float real = fft[index] / 128f;
                float imaginary = fft[index + 1] / 128f;
                sums[band] += real * real + imaginary * imaginary;
                counts[band]++;
            }
            float[] magnitudes = new float[BAND_COUNT];
            for (int i = 0; i < BAND_COUNT; i++) {
                if (counts[i] > 0) magnitudes[i] = (float) Math.sqrt(sums[i] / counts[i]);
            }
            // The low end of a small FFT can have fewer bins than our logarithmic display
            // bands (for example, 44.1 kHz / 1024 first jumps from 43 Hz to 86 Hz). Fill
            // those display-only gaps from their closest sampled neighbours so the left edge
            // never contains permanently dead bars. Silence remains zero throughout.
            interpolateEmptyBands(magnitudes, counts);
            float strongest = 0f;
            for (int i = 0; i < BAND_COUNT; i++) {
                strongest = Math.max(strongest, magnitudes[i]);
            }
            if (strongest < 0.006f) {
                for (int i = 0; i < BAND_COUNT; i++) smoothed[i] *= 0.72f;
                return smoothed.clone();
            }
            referenceLevel = strongest >= referenceLevel
                    ? strongest : Math.max(strongest, referenceLevel * 0.94f);
            float intensity = clamp((float) (Math.log1p(strongest * 8f) / Math.log1p(4f)));
            for (int i = 0; i < BAND_COUNT; i++) {
                float desired = clamp((float) Math.sqrt(magnitudes[i]
                        / Math.max(0.012f, referenceLevel)) * intensity);
                float smoothing = desired >= smoothed[i] ? 0.72f : 0.24f;
                smoothed[i] += (desired - smoothed[i]) * smoothing;
            }
            return smoothed.clone();
        }

        private static void interpolateEmptyBands(float[] magnitudes, int[] counts) {
            int previous = -1;
            for (int current = 0; current < magnitudes.length; current++) {
                if (counts[current] == 0) continue;
                if (previous < 0) {
                    for (int i = 0; i < current; i++) magnitudes[i] = magnitudes[current];
                } else if (current - previous > 1) {
                    for (int i = previous + 1; i < current; i++) {
                        float fraction = (i - previous) / (float) (current - previous);
                        magnitudes[i] = magnitudes[previous]
                                + (magnitudes[current] - magnitudes[previous]) * fraction;
                    }
                }
                previous = current;
            }
            if (previous >= 0) {
                for (int i = previous + 1; i < magnitudes.length; i++) {
                    magnitudes[i] = magnitudes[previous];
                }
            }
        }
    }

    static final class BarTracker {
        private final float[] bars;
        private long lastUpdateElapsedMs = -1L;

        BarTracker(int count) {
            bars = new float[count];
        }

        void update(float[] targets, long nowElapsedMs) {
            float elapsedSeconds = lastUpdateElapsedMs < 0L ? 1f / 60f
                    : Math.max(1f / 120f, Math.min(0.25f,
                    (nowElapsedMs - lastUpdateElapsedMs) / 1000f));
            lastUpdateElapsedMs = nowElapsedMs;
            for (int i = 0; i < bars.length; i++) {
                float target = targets != null && i < targets.length ? clamp(targets[i]) : 0f;
                bars[i] = target >= bars[i] ? target
                        : Math.max(target, bars[i] - 3.8f * elapsedSeconds);
            }
        }

        float barAt(int index) { return bars[index]; }

        boolean hasVisibleBar() {
            for (float bar : bars) if (bar > 0.012f) return true;
            return false;
        }

        void reset() {
            for (int i = 0; i < bars.length; i++) {
                bars[i] = 0f;
            }
            lastUpdateElapsedMs = -1L;
        }
    }

    static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
