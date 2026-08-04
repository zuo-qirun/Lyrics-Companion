package com.zuoqirun.lyricscompanion;

/** Small, allocation-free motion model for the Apple Music-like lyric presentation. */
final class AmllStyleMotion {
    private AmllStyleMotion() {}

    static float scrollRemainder(long elapsedMs, long responseMs) {
        if (elapsedMs <= 0L) return 1f;
        if (elapsedMs >= responseMs) return 0f;
        float t = Math.max(0f, elapsedMs / (float) Math.max(1L, responseMs));
        float value = (1f + 5.5f * t) * (float) Math.exp(-5.5f * t);
        return Math.max(0f, Math.min(1f, value));
    }

    static float lineScale(int offset) {
        if (offset == 0) return 1f;
        return Math.max(0.82f, 1f - Math.min(4, Math.abs(offset)) * 0.045f);
    }

    static float lineOpacity(int offset, boolean playing, boolean previewing) {
        if (offset == 0) return 1f;
        int distance = Math.abs(offset);
        if (offset < 0 && playing && !previewing) {
            return Math.max(0.035f, 0.22f - distance * 0.055f);
        }
        return Math.max(0.10f, 0.48f - distance * 0.10f);
    }

    static float lineBlur(int offset, boolean playing, boolean previewing) {
        if (offset == 0 || previewing) return 0f;
        float distance = Math.abs(offset);
        if (offset < 0 && playing) return Math.min(5f, 1.2f + distance * 0.8f);
        return Math.min(4f, 0.5f + distance * 0.65f);
    }

    static float wordLift(float progress) {
        float value = Math.max(0f, Math.min(1f, progress));
        return (float) Math.sin(value * Math.PI) * 0.055f;
    }

}
