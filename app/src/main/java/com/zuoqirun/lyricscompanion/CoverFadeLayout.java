package com.zuoqirun.lyricscompanion;

/** Geometry for a clear upper cover fading into the day/night background. */
final class CoverFadeLayout {
    private CoverFadeLayout() { }

    static float coverBottom(float height, int percent) {
        return Math.max(1f, height) * Math.max(40, Math.min(70, percent)) / 100f;
    }

    static float fadeStart(float height, int coverPercent, int fadePercent) {
        return Math.max(0f, coverBottom(height, coverPercent)
                - Math.max(1f, height) * Math.max(0, Math.min(40, fadePercent)) / 100f);
    }

    static int capsuleAlpha(boolean active, int opacity) {
        return Math.round((active ? 217 : 166) * Math.max(0, Math.min(100, opacity)) / 100f);
    }
}
