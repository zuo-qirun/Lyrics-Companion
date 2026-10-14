package com.zuoqirun.lyricscompanion;

/** Fast boot retries settle into a minute cadence; a pending retry is never postponed by polling. */
final class DisplayRecoveryBackoff {
    private int attempts;
    private long nextElapsedMs = -1;

    boolean ready(long now) { return nextElapsedMs < 0 || now >= nextElapsedMs; }
    int attempts() { return attempts; }

    long failed(long now) {
        if (nextElapsedMs > now) return nextElapsedMs - now;
        long delay = Math.min(60_000L, 1_500L << Math.min(6, attempts));
        if (attempts < Integer.MAX_VALUE) attempts++;
        nextElapsedMs = now + delay;
        return delay;
    }

    void reset() { attempts = 0; nextElapsedMs = -1; }
}
