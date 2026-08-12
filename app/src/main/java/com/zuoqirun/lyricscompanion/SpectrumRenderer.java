package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Shared, stateless spectrum painter used by every lyric surface. */
final class SpectrumRenderer {
    private SpectrumRenderer() { }

    static void draw(Canvas canvas, Paint paint, RectF bounds, float[] levels, String style,
                     String colorMode, int baseColor, int customColor, int[] artworkPalette) {
        if (bounds.width() <= 1f || bounds.height() <= 1f || levels == null || levels.length == 0) {
            return;
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setShader(null);
        int count = levels.length;
        float slot = bounds.width() / count;
        float itemWidth = Math.max(1f, slot * ("dots".equals(style) ? 0.34f : 0.58f));
        Path wave = "wave".equals(style) ? new Path() : null;
        RectF item = new RectF();
        for (int i = 0; i < count; i++) {
            float level = clamp(levels[i]);
            int color = colorAt(i, count, colorMode, baseColor, customColor, artworkPalette);
            paint.setColor(withAlpha(color, 220));
            float centerX = bounds.left + slot * (i + 0.5f);
            float height = Math.max(itemWidth, bounds.height() * (0.10f + level * 0.90f));
            if (wave != null) {
                float y = bounds.bottom - height;
                if (i == 0) wave.moveTo(centerX, y); else wave.lineTo(centerX, y);
            } else if ("mirror".equals(style)) {
                float half = height * 0.5f;
                item.set(centerX - itemWidth / 2f, bounds.centerY() - half,
                        centerX + itemWidth / 2f, bounds.centerY() + half);
                canvas.drawRoundRect(item, itemWidth / 2f, itemWidth / 2f, paint);
            } else if ("dots".equals(style)) {
                float radius = itemWidth * 0.5f;
                canvas.drawCircle(centerX, bounds.bottom - height + radius, radius, paint);
            } else {
                float radius = "capsule".equals(style) ? itemWidth / 2f : itemWidth * 0.24f;
                item.set(centerX - itemWidth / 2f, bounds.bottom - height,
                        centerX + itemWidth / 2f, bounds.bottom);
                canvas.drawRoundRect(item, radius, radius, paint);
            }
        }
        if (wave != null) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(2f, bounds.height() * 0.055f));
            if ("rainbow".equals(colorMode)) {
                paint.setShader(new LinearGradient(bounds.left, 0f, bounds.right, 0f,
                        rainbowColors(), null, Shader.TileMode.CLAMP));
            } else if ("artwork".equals(colorMode) && artworkPalette != null
                    && artworkPalette.length > 1) {
                paint.setShader(new LinearGradient(bounds.left, 0f, bounds.right, 0f,
                        artworkPalette, null, Shader.TileMode.CLAMP));
            } else {
                paint.setColor(withAlpha("custom".equals(colorMode) ? customColor : baseColor, 225));
            }
            canvas.drawPath(wave, paint);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    static int colorAt(int index, int count, String mode, int baseColor, int customColor,
                       int[] artworkPalette) {
        if ("rainbow".equals(mode)) {
            return hslColor((index * 360f / Math.max(1, count)) % 360f, 0.82f, 0.62f);
        }
        if ("artwork".equals(mode) && artworkPalette != null && artworkPalette.length > 0) {
            return artworkPalette[Math.min(artworkPalette.length - 1,
                    index * artworkPalette.length / Math.max(1, count))];
        }
        if ("custom".equals(mode) && customColor != 0) return customColor;
        return baseColor;
    }

    private static int[] rainbowColors() {
        return new int[]{0xFFFF5E7D, 0xFFFFC857, 0xFF55EFC4, 0xFF54A0FF, 0xFFB76DFF};
    }

    private static int hslColor(float hue, float saturation, float lightness) {
        float chroma = (1f - Math.abs(2f * lightness - 1f)) * saturation;
        float section = hue / 60f;
        float x = chroma * (1f - Math.abs(section % 2f - 1f));
        float red = 0f;
        float green = 0f;
        float blue = 0f;
        if (section < 1f) { red = chroma; green = x; }
        else if (section < 2f) { red = x; green = chroma; }
        else if (section < 3f) { green = chroma; blue = x; }
        else if (section < 4f) { green = x; blue = chroma; }
        else if (section < 5f) { red = x; blue = chroma; }
        else { red = chroma; blue = x; }
        float match = lightness - chroma / 2f;
        int r = Math.round((red + match) * 255f);
        int g = Math.round((green + match) * 255f);
        int b = Math.round((blue + match) * 255f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
