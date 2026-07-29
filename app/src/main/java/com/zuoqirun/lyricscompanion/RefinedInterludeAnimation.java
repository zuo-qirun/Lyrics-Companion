package com.zuoqirun.lyricscompanion;

/** Timing model mirrored from Refined Now Playing's Interlude component and CSS. */
final class RefinedInterludeAnimation {
    private static final long SCALE_EXTRA_MS = 150L;
    private static final long BREATH_DURATION_MS = 2_000L;

    private RefinedInterludeAnimation() {}

    static DotState dotState(long elapsedMs, long durationMs, int dotIndex) {
        long perDotMs = Math.max(1L, durationMs / 3L);
        long startMs = perDotMs * Math.max(0, Math.min(2, dotIndex));
        float opacityProgress = clamp01((elapsedMs - startMs) / (float) perDotMs);
        float scaleProgress = clamp01((elapsedMs - startMs)
                / (float) (perDotMs + SCALE_EXTRA_MS));
        return new DotState(0.2f + 0.7f * opacityProgress,
                0.9f + 0.1f * cssEase(scaleProgress));
    }

    static float breathScale(long elapsedMs) {
        float phase = Math.floorMod(elapsedMs, BREATH_DURATION_MS)
                / (float) BREATH_DURATION_MS;
        return 1f + 0.05f * (1f - (float) Math.cos(phase * Math.PI * 2d));
    }

    private static float cssEase(float x) {
        float low = 0f;
        float high = 1f;
        for (int i = 0; i < 10; i++) {
            float t = (low + high) * 0.5f;
            float curveX = cubic(t, 0f, 0.25f, 0.25f, 1f);
            if (curveX < x) low = t;
            else high = t;
        }
        return cubic((low + high) * 0.5f, 0f, 0.10f, 1f, 1f);
    }

    private static float cubic(float t, float p0, float p1, float p2, float p3) {
        float inverse = 1f - t;
        return inverse * inverse * inverse * p0
                + 3f * inverse * inverse * t * p1
                + 3f * inverse * t * t * p2 + t * t * t * p3;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static final class DotState {
        final float opacity;
        final float scale;

        DotState(float opacity, float scale) {
            this.opacity = opacity;
            this.scale = scale;
        }
    }
}
