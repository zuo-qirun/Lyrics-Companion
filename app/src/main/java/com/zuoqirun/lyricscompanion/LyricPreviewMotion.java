package com.zuoqirun.lyricscompanion;

/** Pure motion helpers for direct, interruptible lyric preview gestures. */
final class LyricPreviewMotion {
    private static final float DECELERATION_RATE = 0.99f;

    private LyricPreviewMotion() {}

    static float project(float positionPx, float velocityPxPerSecond) {
        return positionPx + velocityPxPerSecond / 1_000f
                * DECELERATION_RATE / (1f - DECELERATION_RATE);
    }

    static int projectedLineDelta(float positionPx, float velocityPxPerSecond,
                                  float stepPx) {
        if (stepPx <= 0f) return 0;
        int delta = Math.round(-project(positionPx, velocityPxPerSecond) / stepPx);
        return Math.max(-3, Math.min(3, delta));
    }

    static float rubberBand(float overshootPx, float dimensionPx) {
        if (dimensionPx <= 0f || overshootPx == 0f) return 0f;
        float constant = 0.55f;
        return overshootPx * dimensionPx * constant
                / (dimensionPx + constant * Math.abs(overshootPx));
    }

    static SpringState stepCritical(float positionPx, float velocityPxPerSecond,
                                    float deltaSeconds, float responseSeconds) {
        float dt = Math.max(0f, Math.min(0.05f, deltaSeconds));
        float response = Math.max(0.12f, responseSeconds);
        float omega = (float) (Math.PI * 2d / response);
        float c = velocityPxPerSecond + omega * positionPx;
        float decay = (float) Math.exp(-omega * dt);
        float position = (positionPx + c * dt) * decay;
        float velocity = (velocityPxPerSecond - omega * c * dt) * decay;
        if (Math.abs(position) < 0.35f && Math.abs(velocity) < 4f) {
            return new SpringState(0f, 0f, true);
        }
        return new SpringState(position, velocity, false);
    }

    static final class SpringState {
        final float position;
        final float velocity;
        final boolean settled;

        SpringState(float position, float velocity, boolean settled) {
            this.position = position;
            this.velocity = velocity;
            this.settled = settled;
        }
    }
}
