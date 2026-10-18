// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Adapted from sonnetMotion.ts / sonnetCameraTracking.ts (9279251).
package com.zuoqirun.lyricscompanion;

/** Absolute-time camera paths and edge-preserving temporal focus filtering. */
final class FoliaOriginalSonnetMotion {
    interface FocusSampler { void sample(double time, float[] output); }
    private FoliaOriginalSonnetMotion() { }

    static float clamp(double value) { return (float) Math.min(1, Math.max(0, value)); }

    static float bezier(double x1, double y1, double x2, double y2, double value) {
        double target = clamp(value);
        if (target == 0 || target == 1) return (float) target;
        double low = 0, high = 1, t = target;
        for (int i = 0; i < 12; i++) {
            if (coordinate(x1, x2, t) < target) low = t; else high = t;
            t = (low + high) / 2;
        }
        return (float) coordinate(y1, y2, t);
    }

    private static double coordinate(double a, double b, double t) {
        double inverse = 1 - t;
        return 3 * inverse * inverse * t * a + 3 * inverse * t * t * b + t * t * t;
    }

    static float inOut(double value) { return bezier(0.65, 0, 0.35, 1, value); }

    static float expo(double value) {
        double p = clamp(value); return p == 1 ? 1 : (float) (1 - Math.pow(2, -10 * p));
    }

    static float path(String kind, double value) {
        float p = clamp(value);
        if ("tracking-ribbon".equals(kind) || "fragment-collage".equals(kind)
                || "quiet-tableau".equals(kind) || "poster-blocks".equals(kind)) return p * 0.55f + inOut(p) * 0.45f;
        if (p < 0.18f) return expo(p / 0.18) * 0.22f;
        if (p < 0.78f) return 0.22f + (p - 0.18f) / 0.6f * 0.56f;
        float settle = (p - 0.78f) / 0.22f;
        return 0.78f + (1 - (1 - settle) * (1 - settle)) * 0.22f;
    }

    /** Result: normalized x, y, scale and rotation in radians. */
    static void frame(String kind, double progress, float[] out) {
        float linear = clamp(progress), p = path(kind, progress);
        switch (kind) {
            case "editorial-column":
                out[0] = -0.055f + p * 0.095f; out[1] = 0.025f - p * 0.04f;
                out[2] = 0.98f + p * 0.07f; out[3] = -0.006f + p * 0.01f; break;
            case "type-impact":
                out[0] = -0.035f + p * 0.07f; out[1] = 0.018f - p * 0.028f;
                out[2] = 1 + (1 - expo(Math.min(linear / 0.18f, 1))) * 0.22f + p * 0.08f;
                out[3] = -0.01f + p * 0.016f; break;
            case "fragment-collage":
                out[0] = -0.045f + p * 0.085f; out[1] = 0.028f - (float) Math.sin(p * Math.PI) * 0.055f;
                out[2] = 0.97f + p * 0.09f; out[3] = -0.014f + p * 0.028f; break;
            case "tracking-ribbon":
                out[0] = -0.16f + p * 0.28f; out[1] = 0.05f - p * 0.085f;
                out[2] = 0.98f + p * 0.07f; out[3] = 0.008f - p * 0.014f; break;
            case "mask-reveal":
                out[0] = 0.035f - p * 0.065f; out[1] = 0.1f - p * 0.135f;
                out[2] = 0.96f + p * 0.12f; out[3] = -0.006f + p * 0.009f; break;
            case "poster-blocks":
                out[0] = -0.012f + p * 0.024f; out[1] = 0.008f - p * 0.016f;
                out[2] = 0.99f + p * 0.025f; out[3] = -0.0015f + p * 0.003f; break;
            default:
                out[0] = -0.022f + p * 0.04f; out[1] = 0.014f - p * 0.025f;
                out[2] = 1 + p * 0.028f; out[3] = -0.002f + p * 0.003f; break;
        }
    }

    static void breath(double seconds, double phase, float[] out) {
        double tau = seconds * Math.PI * 2;
        out[0] = (float) ((Math.sin(tau * 0.13 + phase) * 0.65 + Math.sin(tau * 0.31 + phase * 1.7) * 0.35) * 0.006);
        out[1] = (float) ((Math.cos(tau * 0.11 + phase * 2.3) * 0.65 + Math.sin(tau * 0.29 + phase * 0.9) * 0.35) * 0.006);
        out[2] = (float) Math.sin(tau * 0.09 + phase * 1.3) * 0.002f;
        out[3] = (float) Math.sin(tau * 0.07 + phase * 2.9) * 0.0015f;
    }

    static void weights(long[] starts, long[] ends, double time, float[] out) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < starts.length; i++) {
            double start = Math.min(starts[i], ends[i]), end = Math.max(starts[i], ends[i]);
            double distance = time < start ? start - time : time > end ? time - end : 0;
            double logWeight = -(distance * distance) / (2 * 350 * 350);
            maximum = Math.max(maximum, logWeight);
        }
        double total = 0;
        for (int i = 0; i < starts.length; i++) {
            double start = Math.min(starts[i], ends[i]), end = Math.max(starts[i], ends[i]);
            double distance = time < start ? start - time : time > end ? time - end : 0;
            out[i] = (float) Math.exp(-(distance * distance) / (2 * 350 * 350) - maximum); total += out[i];
        }
        if (total > 0) for (int i = 0; i < starts.length; i++) out[i] /= total;
    }

    static void smoothFocus(double time, long start, long end, FocusSampler sampler,
                            float[] center, float[] scratch, float[] output) {
        time = Math.min(end, Math.max(start, time));
        sampler.sample(time, center);
        float x = 0, y = 0, total = 0;
        for (int i = 0; i < 5; i++) {
            sampler.sample(Math.min(end, Math.max(start, time + (i - 2) * 60)), scratch);
            float dx = scratch[0] - center[0], dy = scratch[1] - center[1];
            if (dx * dx + dy * dy > 96 * 96) continue;
            float weight = i == 2 ? 6 : i == 1 || i == 3 ? 4 : 1;
            x += scratch[0] * weight; y += scratch[1] * weight; total += weight;
        }
        output[0] = x / total; output[1] = y / total;
    }
}
