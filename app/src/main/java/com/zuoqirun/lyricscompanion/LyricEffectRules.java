package com.zuoqirun.lyricscompanion;

/** Animation math is independent of lyric timing and shared by every text drawing path. */
final class LyricEffectRules {
    private LyricEffectRules() { }

    static String normalize(String effect) {
        return LyricEffectCatalog.find(effect).value;
    }

    static boolean enabled(String effect, int strength, int fps) {
        LyricEffectCatalog.Effect selected = LyricEffectCatalog.find(effect);
        return selected != LyricEffectCatalog.Effect.NONE && strength > 0
                && (!selected.animated || fps(fps) > 0);
    }

    static boolean entryOnly(String effect) {
        return "cinema".equals(effect) || "bounce".equals(effect);
    }

    static boolean estimatesWords(boolean existingPreference, String effect, int strength, int fps) {
        return existingPreference || "karaoke".equals(effect) && enabled(effect, strength, fps);
    }

    static long frameDelay(String effect, int strength, int fps, boolean playing,
                           long entryAgeMs, int speed, long baseDelay) {
        if (!enabled(effect, strength, fps) || !playing || !LyricEffectCatalog.find(effect).animated) {
            return baseDelay;
        }
        long effectDelay = Math.round(1000f / fps(fps));
        boolean animating = !entryOnly(effect) || entryAgeMs >= 0L && entry(entryAgeMs, speed) > 0f;
        return Math.max(effectDelay, animating ? Math.min(baseDelay, effectDelay) : baseDelay);
    }

    static float wave(long timeMs, int speed, float spatialPhase) {
        return (float) Math.sin((phase(timeMs, speed) + spatialPhase) * Math.PI * 2d);
    }

    static float bounce(long ageMs, int speed) {
        if (ageMs < 0L || entry(ageMs, speed) == 0f) return 0f;
        float duration = 60_000f / Math.max(25, Math.min(300, speed));
        float progress = Math.max(0f, Math.min(1f, ageMs / duration));
        return -(float) Math.sin(progress * Math.PI * 3d) * (1f - progress) * (1f - progress);
    }

    /** Stable randomness avoids per-frame allocation and freezes naturally with the playback clock. */
    static float noise(long seed) {
        long mixed = seed ^ (seed >>> 33);
        mixed *= 0xff51afd7ed558ccdL;
        mixed ^= mixed >>> 33;
        return (mixed & 0xFFFFFFL) / (float) 0xFFFFFFL;
    }

    static int glyphEnd(String text, int start) {
        int first = text.codePointAt(start);
        int end = start + Character.charCount(first);
        if (first >= 0x1F1E6 && first <= 0x1F1FF && end < text.length()) {
            int next = text.codePointAt(end);
            if (next >= 0x1F1E6 && next <= 0x1F1FF) end += Character.charCount(next);
        }
        boolean joinNext = false;
        while (end < text.length()) {
            int code = text.codePointAt(end);
            int type = Character.getType(code);
            if (code == 0x200D) joinNext = true;
            else if (joinNext) joinNext = false;
            else if (type != Character.NON_SPACING_MARK && type != Character.COMBINING_SPACING_MARK
                    && code != 0xFE0F && !(code >= 0x1F3FB && code <= 0x1F3FF)) break;
            end += Character.charCount(code);
        }
        return end;
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
