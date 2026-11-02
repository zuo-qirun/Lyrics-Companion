package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Bounded FFT-driven ribbons/particles; no bitmap, shader or particle allocations per frame. */
final class AudioVisualRenderer {
    static final String MODE = "audio_visual_mode";
    static final String STRENGTH = "audio_visual_strength";
    static final String FPS = "audio_visual_fps";
    static final String DENSITY = "audio_visual_density";
    static final String THICKNESS = "audio_visual_thickness";
    static final String COLORS = "audio_visual_colors";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private static final int[] PALETTE = {0xFF61DBE8, 0xFFFFC877, 0xFFF58CBF};

    static boolean requested(Context context, boolean secondary) {
        return supportsStyle(AppPreferences.overlayStyle(context, secondary))
                && !"off".equals(AppPreferences.displayString(context, secondary, MODE, "off"));
    }

    static boolean supportsStyle(String style) {
        return "refined".equals(style) || "compact".equals(style) || FoliaModeCatalog.isStyle(style);
    }

    static float level(float[] levels, int index, boolean live, boolean playing, long time) {
        if (!playing) return .08f;
        if (live && levels != null && levels.length > 0) {
            return Math.max(0f, Math.min(1f, levels[(index % levels.length + levels.length) % levels.length]));
        }
        return .12f + .28f * (.5f + .5f * (float) Math.sin(time / 550d + index * .47d));
    }

    void draw(Canvas canvas, RectF area, String mode, float[] levels, boolean live,
              boolean playing, long clock, float strength, int density, float thickness,
              String colors, int lyricColor, int artworkColor, int customColor) {
        int save = canvas.save();
        canvas.clipRect(area);
        int count = Math.max(12, Math.min(64, density * 64 / 100));
        for (int layer = 0; layer < 3; layer++) {
            int color = "lyric".equals(colors) ? lyricColor : "artwork".equals(colors) ? artworkColor
                    : "custom".equals(colors) ? customColor : PALETTE[layer];
            paint.setColor((color & 0xFFFFFF) | (Math.round(160f * strength) << 24));
            paint.setStrokeWidth(Math.max(1f, thickness));
            if ("particles".equals(mode)) {
                paint.setStyle(Paint.Style.FILL);
                for (int i = 0; i < count; i++) {
                    float energy = level(levels, i, live, playing, clock);
                    float x = area.left + area.width() * (i + .5f) / count;
                    for (int p = 0; p < 3; p++) {
                        float phase = playing ? ((clock + i * 137L + p * 431L) % 2400L) / 2400f : .5f;
                        float y = area.bottom - area.height() * (.12f + energy * strength * .8f) * phase;
                        canvas.drawCircle(x + layer * thickness * 2, y, thickness * (.6f + energy), paint);
                    }
                }
            } else {
                paint.setStyle(Paint.Style.STROKE);
                path.reset();
                for (int i = 0; i <= 96; i++) {
                    float fraction = i / 96f;
                    float energy = level(levels, i * SpectrumMath.BAND_COUNT / 97, live, playing, clock);
                    double phase = fraction * Math.PI * 4d + clock / 1300d + layer * 1.3d;
                    float y = area.centerY() + (float) Math.sin(phase) * area.height()
                            * (.035f + energy * .28f) * strength + (layer - 1) * area.height() * .06f;
                    float x = area.left + fraction * area.width();
                    if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                }
                canvas.drawPath(path, paint);
            }
        }
        canvas.restoreToCount(save);
    }
}
