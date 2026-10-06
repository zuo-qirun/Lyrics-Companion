package com.zuoqirun.lyricscompanion;

/** Animation math is independent of lyric timing and shared by every text drawing path. */
final class LyricEffectRules {
    private LyricEffectRules() { }

    static String normalize(String effect) {
        return "neon".equals(effect) || "sweep".equals(effect) || "cinema".equals(effect)
                ? effect : "none";
    }

    static int fps(int fps) {
        return fps == 24 || fps == 30 || fps == 60 ? fps : 0;
    }

    static float phase(long timeMs, int speedPercent) {
        long period = Math.max(400L, 240_000L / Math.max(25, Math.min(300, speedPercent)));
        return Math.max(0L, timeMs) % period / (float) period;
    }

    static float pulse(long timeMs, int speedPercent) {
        return 0.5f + 0.5f * (float) Math.sin(phase(timeMs, speedPercent) * Math.PI * 2d);
    }

    static float entry(long ageMs, int speedPercent) {
        float duration = 60_000f / Math.max(25, Math.min(300, speedPercent));
        float progress = Math.max(0f, Math.min(1f, ageMs / duration));
        return (float) Math.pow(1f - progress, 3d);
    }
}
