package com.zuoqirun.lyricscompanion;

/** Incoming live lines fade and rise without inventing singing or word timestamps. */
final class LiveLyricTransition {
    private String track = "";
    private String line = "";
    private long elapsed;
    private long lastFrame;
    private boolean transitioning;

    void update(String trackId, String text, boolean live, boolean enabled,
                boolean playing, long now, int durationMs) {
        if (!live || !enabled || text == null || text.trim().isEmpty()) {
            reset();
            return;
        }
        String normalized = text.trim();
        if (!trackId.equals(track)) {
            track = trackId;
            line = normalized;
            transitioning = false;
        } else if (!normalized.equals(line)) {
            line = normalized;
            elapsed = 0L;
            transitioning = durationMs > 0 && playing;
        } else if (transitioning && playing) {
            elapsed += Math.max(0L, now - lastFrame);
            if (elapsed >= durationMs) transitioning = false;
        }
        lastFrame = now;
    }

    float fraction(int durationMs) {
        if (!transitioning || durationMs <= 0) return 1f;
        float t = Math.min(1f, elapsed / (float) durationMs);
        return 1f - (1f - t) * (1f - t);
    }

    boolean isAnimating(boolean playing) { return transitioning && playing; }

    private void reset() {
        track = "";
        line = "";
        elapsed = 0L;
        transitioning = false;
        lastFrame = 0L;
    }
}
