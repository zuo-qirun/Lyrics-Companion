// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Canvas translation of the Folia shell background modes (common, latent, monet, nomand, sora, embed).
// Original source revision and license: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Matrix;

/**
 * Paints the Folia shell background behind the lyric scene. Each mode follows the
 * upstream background registry geometry and palette, approximated with Canvas primitives
 * so API 19 devices can render it without shaders.
 */
final class FoliaOriginalBackgroundRenderer {
    static final String NONE = "";
    static final String COMMON = "common";
    static final String LATENT = "latent";
    static final String MONET = "monet";
    static final String NOMAND = "nomand";
    static final String SORA = "sora";
    static final String EMBED = "url";

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmap = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();
    private final int[] palette = new int[] {0xFF09090B, 0xFF09090B, 0xFF09090B, 0xFF09090B};

    private String cachedMode = "";
    private int cachedWidth, cachedHeight;
    private Bitmap cachedCover;
    private Bitmap scaledCover;
    private Bitmap blurSource;
    private Bitmap ditherTile;
    private float[] shapes;
    private float[] stars;

    static String normalize(String mode) {
        if (mode == null) return NONE;
        String value = mode.trim().toLowerCase(java.util.Locale.US);
        switch (value) {
            case COMMON:
            case LATENT:
            case MONET:
            case NOMAND:
            case SORA:
            case EMBED:
                return value;
            default:
                return NONE;
        }
    }

    void clear() {
        cachedMode = "";
        cachedWidth = 0;
        cachedHeight = 0;
        cachedCover = null;
        scaledCover = null;
        blurSource = null;
        ditherTile = null;
        shapes = null;
        stars = null;
    }

    void draw(Canvas canvas, float width, float height, long time, String mode, Bitmap cover,
              int primary, int accent, int muted, float opacity) {
        String normalized = normalize(mode);
        if (normalized.isEmpty() || width <= 0 || height <= 0) return;
        float alpha = Math.max(0f, Math.min(1.5f, opacity));
        if (alpha <= 0.01f) return;
        int w = Math.round(width), h = Math.round(height);
        if (!normalized.equals(cachedMode) || w != cachedWidth || h != cachedHeight
                || cover != cachedCover) {
            cachedMode = normalized;
            cachedWidth = w;
            cachedHeight = h;
            cachedCover = cover;
            shapes = null;
            stars = null;
            scaledCover = null;
            blurSource = null;
            buildPalette(cover, primary, accent, muted);
        }
        if (NONE.equals(normalized)) return;
        if (COMMON.equals(normalized)) {
            drawCommon(canvas, width, height, alpha);
        } else if (LATENT.equals(normalized)) {
            drawLatent(canvas, width, height, time / 1000f, alpha);
        } else if (MONET.equals(normalized)) {
            drawMonet(canvas, width, height, alpha);
        } else if (NOMAND.equals(normalized)) {
            drawNomand(canvas, width, height, alpha);
        } else if (SORA.equals(normalized)) {
            drawSora(canvas, width, height, time / 1000f, alpha);
        }
        // EMBED has no configured source in this port; it intentionally stays black.
    }

    private void buildPalette(Bitmap cover, int primary, int accent, int muted) {
        int base = Color.rgb(9, 9, 11);
        int warm = blend(accent, base, 0.35f);
        int cool = blend(muted, base, 0.35f);
        if (cover != null && !cover.isRecycled()) {
            int w = Math.max(1, cover.getWidth()), h = Math.max(1, cover.getHeight());
            int warmScore = Integer.MIN_VALUE, coolScore = Integer.MIN_VALUE, lumScore = -1;
            int warmColor = warm, coolColor = cool, bright = warm;
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    int x = Math.min(w - 1, Math.max(0, (col * w + w / 2) / 8));
                    int y = Math.min(h - 1, Math.max(0, (row * h + h / 2) / 8));
                    int color;
                    try {
                        color = cover.getPixel(x, y);
                    } catch (RuntimeException error) {
                        continue;
                    }
                    int r = Color.red(color), g = Color.green(color), b = Color.blue(color);
                    int luminance = (r * 3 + g * 6 + b) / 10;
                    if (r - b > warmScore) { warmScore = r - b; warmColor = color; }
                    if (b - r > coolScore) { coolScore = b - r; coolColor = color; }
                    if (luminance > lumScore) { lumScore = luminance; bright = color; }
                }
            }
            warm = blend(warmColor, accent, 0.28f);
            cool = blend(coolColor, muted, 0.3f);
            base = blend(blend(bright, base, 0.86f), primary, 0.06f);
        }
        palette[0] = base;
        palette[1] = warm;
        palette[2] = cool;
        palette[3] = brighten(blend(primary, base, 0.25f), 0.12f);
    }

    private static int brighten(int color, float amount) {
        return blend(color, Color.WHITE, amount);
    }

    private void drawCommon(Canvas canvas, float width, float height, float alpha) {
        if (shapes == null) shapes = buildCommonShapes(width, height);
        fill.setShader(null);
        stroke.setShader(null);
        canvas.drawColor(blend(palette[0], Color.BLACK, 0.55f));
        float unit = Math.min(width, height);
        for (int i = 0; i + 6 < shapes.length; i += 7) {
            float x = shapes[i] * width;
            float y = shapes[i + 1] * height;
            float size = shapes[i + 2] * unit;
            int kind = (int) shapes[i + 3];
            boolean filled = shapes[i + 4] > 0.5f;
            float rotation = shapes[i + 5] * 360f;
            float alphaScale = shapes[i + 6] * 0.22f * alpha;
            int color = kind == 3 ? palette[2] : kind == 2 ? palette[3] : palette[1];
            int shapeColor = blend(blend(color, palette[0], 0.4f), Color.BLACK, 0.5f);
            fill.setColor(shapeColor);
            fill.setAlpha(Math.round(Math.max(0f, Math.min(1f, alphaScale)) * 255));
            stroke.setColor(shapeColor);
            stroke.setAlpha(Math.round(Math.max(0f, Math.min(1f, alphaScale * 2.1f)) * 255));
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(Math.max(0.7f, unit * 0.0018f));
            rect.set(0, 0, size, size);
            switch (kind) {
                case 0: {
                    canvas.save();
                    canvas.translate(x, y);
                    canvas.rotate(rotation);
                    rect.set(-size / 2, -size / 2, size / 2, size / 2);
                    if (filled) canvas.drawCircle(0, 0, size / 2, fill);
                    else canvas.drawCircle(0, 0, size / 2, stroke);
                    canvas.restore();
                    break;
                }
                case 1: {
                    canvas.save();
                    canvas.translate(x, y);
                    canvas.rotate(rotation);
                    rect.set(-size / 2, -size / 2, size / 2, size / 2);
                    if (filled) canvas.drawRect(rect, fill);
                    else canvas.drawRect(rect, stroke);
                    canvas.restore();
                    break;
                }
                case 2: {
                    canvas.save();
                    canvas.translate(x, y);
                    canvas.rotate(rotation);
                    path.reset();
                    path.moveTo(0, -size * 0.55f);
                    path.lineTo(size * 0.52f, size * 0.42f);
                    path.lineTo(-size * 0.52f, size * 0.42f);
                    path.close();
                    if (filled) canvas.drawPath(path, fill);
                    else canvas.drawPath(path, stroke);
                    canvas.restore();
                    break;
                }
                default: {
                    canvas.save();
                    canvas.translate(x, y);
                    canvas.rotate(45);
                    rect.set(-size * 0.36f, -size * 0.36f, size * 0.36f, size * 0.36f);
                    if (filled) canvas.drawRect(rect, fill);
                    else canvas.drawRect(rect, stroke);
                    canvas.restore();
                    break;
                }
            }
        }
    }

    /**
     * Deterministic layout mirroring the reference frame: faint circles, squares, triangles and
     * diamonds scattered over the near-black canvas with a few small outline clusters.
     */
    private float[] buildCommonShapes(float width, float height) {
        double seed = FoliaSceneMath.hash("folia-bg-common");
        float[] values = new float[] {
                0.14f, 0.27f, 0.11f, 0, 1, 0.1f, 0.9f,
                0.73f, 0.20f, 0.095f, 0, 1, 0.3f, 0.85f,
                0.35f, 0.40f, 0.095f, 0, 1, 0.5f, 0.8f,
                0.76f, 0.71f, 0.08f, 0, 1, 0.2f, 0.85f,
                0.30f, 0.48f, 0.11f, 0, 0, 0.4f, 1.1f,
                0.11f, 0.59f, 0.075f, 1, 0, 0.2f, 1.0f,
                0.107f, 0.285f, 0.06f, 2, 1, 0.3f, 0.9f,
                0.36f, 0.685f, 0.11f, 2, 1, 0.2f, 0.95f,
                0.51f, 0.74f, 0.095f, 2, 1, 0.4f, 0.9f,
                0.16f, 0.82f, 0.028f, 0, 0, 0.1f, 1.3f,
                0.91f, 0.49f, 0.07f, 3, 1, 0.2f, 0.9f,
                0.13f, 0.90f, 0.055f, 2, 1, 0.4f, 0.8f,
                0.17f, 0.92f, 0.045f, 1, 1, 0.9f, 0.9f,
                0.115f, 0.845f, 0.045f, 3, 1, 0.6f, 0.85f
        };
        // Slight seeded jitter keeps the composition from looking machine placed.
        for (int i = 0; i < values.length; i += 7) {
            values[i] += (float) ((FoliaSceneMath.random(seed + i) - 0.5) * 0.02);
            values[i + 1] += (float) ((FoliaSceneMath.random(seed + i + 1) - 0.5) * 0.02);
        }
        return values;
    }

    private void drawLatent(Canvas canvas, float width, float height, float time, float alpha) {
        canvas.drawColor(blend(palette[0], Color.BLACK, 0.35f));
        fill.setShader(null);
        int ember = blend(palette[1], 0xFFFFB066, 0.42f);
        int dusk = blend(palette[2], 0xFF5E6786, 0.35f);
        // Warm core drifting slowly in the upper half.
        float cx = width * (0.54f + 0.03f * (float) Math.sin(time * 0.25));
        float cy = height * (0.22f + 0.02f * (float) Math.cos(time * 0.21));
        fill.setShader(new RadialGradient(cx, cy, Math.max(width, height) * 0.6f,
                new int[] {withAlpha(blend(ember, Color.WHITE, 0.16f), 0.66f * alpha),
                        withAlpha(ember, 0.26f * alpha), Color.TRANSPARENT},
                new float[] {0f, 0.42f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        // Cool counter light along the lower-left edge.
        fill.setShader(new RadialGradient(width * 0.06f, height * 0.86f, Math.max(width, height) * 0.72f,
                new int[] {withAlpha(dusk, 0.5f * alpha), withAlpha(dusk, 0.16f * alpha),
                        Color.TRANSPARENT},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        // Secondary warm rim on the right.
        fill.setShader(new RadialGradient(width * 0.93f, height * 0.42f, Math.max(width, height) * 0.42f,
                new int[] {withAlpha(blend(ember, dusk, 0.4f), 0.26f * alpha), Color.TRANSPARENT},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(null);
        drawDither(canvas, width, height, alpha * 0.85f);
        drawVignette(canvas, width, height, 0.5f * alpha);
    }

    private void drawDither(Canvas canvas, float width, float height, float alpha) {
        if (ditherTile == null) ditherTile = buildDitherTile();
        bitmap.setShader(null);
        bitmap.setColorFilter(null);
        matrix.reset();
        matrix.setScale(3f, 3f);
        BitmapShader shader = new BitmapShader(ditherTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        shader.setLocalMatrix(matrix);
        fill.setShader(shader);
        fill.setAlpha(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(null);
        fill.setAlpha(255);
    }

    private static Bitmap buildDitherTile() {
        Bitmap tile = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[] {
                0x38000000, 0x00000000, 0x1C000000, 0x00000000,
                0x00000000, 0x2A000000, 0x00000000, 0x14000000,
                0x1C000000, 0x00000000, 0x34000000, 0x00000000,
                0x00000000, 0x14000000, 0x00000000, 0x2A000000
        };
        tile.setPixels(pixels, 0, 4, 0, 0, 4, 4);
        return tile;
    }

    private void drawMonet(Canvas canvas, float width, float height, float alpha) {
        Bitmap cover = coverBitmap(Math.round(width), Math.round(height));
        if (cover != null) {
            bitmap.setShader(null);
            bitmap.setColorFilter(null);
            bitmap.setAlpha(Math.round(Math.max(0f, Math.min(1f, 0.85f * alpha)) * 255));
            canvas.drawBitmap(cover, null, new RectF(0, 0, width, height), bitmap);
            bitmap.setAlpha(255);
        }
        // Painterly warm wash and olive shadow, matching the reference treatment.
        fill.setShader(new LinearGradient(0, 0, width, height,
                new int[] {withAlpha(blend(palette[1], Color.WHITE, 0.12f), 0.5f * alpha),
                        withAlpha(palette[1], 0.24f * alpha), withAlpha(palette[2], 0.3f * alpha)},
                new float[] {0f, 0.52f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(new RadialGradient(width * 0.08f, height * 0.12f, Math.max(width, height) * 0.7f,
                new int[] {withAlpha(palette[1], 0.34f * alpha), Color.TRANSPARENT},
                new float[] {0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(null);
        drawVignette(canvas, width, height, 0.62f * alpha);
    }

    private void drawNomand(Canvas canvas, float width, float height, float alpha) {
        int paper = blend(palette[1], 0xFF8A5230, 0.55f);
        int shadow = blend(paper, Color.BLACK, 0.45f);
        canvas.drawColor(blend(paper, Color.BLACK, 0.12f));
        fill.setShader(new LinearGradient(0, height, width, 0,
                new int[] {withAlpha(blend(paper, Color.BLACK, 0.3f), 0.55f * alpha),
                        withAlpha(blend(paper, Color.WHITE, 0.08f), 0.4f * alpha),
                        withAlpha(blend(paper, Color.BLACK, 0.18f), 0.5f * alpha)},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(null);
        // Halftone dots modulated by a soft diagonal band; the reference reads as
        // a slightly darker dot grid over a warm paper field.
        float step = Math.max(5.5f, Math.min(width, height) * 0.0085f);
        int limit = 40000;
        int dots = 0;
        double seed = FoliaSceneMath.hash("folia-bg-nomand");
        for (int row = 0; ; row++) {
            float y = step / 2 + row * step;
            if (y >= height) break;
            for (float x = step / 2 + (row % 2 == 0 ? 0 : step / 2); x < width; x += step) {
                dots++;
                float band = (float) (0.5 + 0.5 * Math.sin((x * 0.5 + y * 0.7) / Math.max(1f, width) * 2.2));
                float jitter = (float) FoliaSceneMath.random(seed + dots);
                float radius = step * (0.16f + 0.2f * band + 0.08f * jitter);
                float density = 0.18f + 0.22f * band + 0.1f * jitter;
                if ((dots & 1) == 0) {
                    fill.setColor(shadow);
                    fill.setAlpha(Math.round(density * alpha * 255));
                } else {
                    fill.setColor(blend(paper, Color.WHITE, 0.35f));
                    fill.setAlpha(Math.round(density * 0.55f * alpha * 255));
                }
                canvas.drawCircle(x, y, radius, fill);
                if (dots >= limit) break;
            }
            if (dots >= limit) break;
        }
        drawVignette(canvas, width, height, 0.5f * alpha);
    }

    private void drawSora(Canvas canvas, float width, float height, float time, float alpha) {
        canvas.drawColor(Color.BLACK);
        if (stars == null) stars = buildStars(width, height);
        fill.setShader(null);
        for (int i = 0; i + 4 < stars.length; i += 5) {
            float x = stars[i] * width;
            float y = stars[i + 1] * height;
            float radius = stars[i + 2];
            float baseAlpha = stars[i + 3];
            float phase = stars[i + 4];
            float twinkle = 0.72f + 0.28f * (float) Math.sin(time * 0.9f + phase);
            int value = Math.round(Math.max(0f, Math.min(1f, baseAlpha * twinkle * alpha)) * 255);
            fill.setColor(Color.WHITE);
            fill.setAlpha(value);
            canvas.drawCircle(x, y, radius, fill);
        }
    }

    private float[] buildStars(float width, float height) {
        double seed = FoliaSceneMath.hash("folia-bg-sora");
        int count = 130;
        float[] values = new float[count * 5];
        for (int i = 0; i < count; i++) {
            values[i * 5] = (float) FoliaSceneMath.random(seed + i * 5);
            values[i * 5 + 1] = (float) FoliaSceneMath.random(seed + i * 5 + 1);
            values[i * 5 + 2] = 0.4f + (float) FoliaSceneMath.random(seed + i * 5 + 2) * 1.3f;
            values[i * 5 + 3] = 0.25f + (float) FoliaSceneMath.random(seed + i * 5 + 3) * 0.75f;
            values[i * 5 + 4] = (float) (FoliaSceneMath.random(seed + i * 5 + 4) * Math.PI * 2);
        }
        return values;
    }

    private void drawVignette(Canvas canvas, float width, float height, float alpha) {
        fill.setShader(new RadialGradient(width / 2, height / 2, Math.max(width, height) * 0.72f,
                new int[] {Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(Color.BLACK, alpha)},
                new float[] {0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(null);
    }

    private Bitmap coverBitmap(int width, int height) {
        if (cachedCover == null || cachedCover.isRecycled()) return null;
        int w = Math.max(2, width), h = Math.max(2, height);
        if (scaledCover != null && scaledCover.getWidth() == w && scaledCover.getHeight() == h) {
            return scaledCover;
        }
        try {
            scaledCover = createBlurredCover(cachedCover, w, h);
        } catch (RuntimeException | OutOfMemoryError error) {
            scaledCover = null;
        }
        return scaledCover;
    }

    /**
     * Approximates the Monet painterly treatment with a two step downscale/upscale blur.
     */
    private Bitmap createBlurredCover(Bitmap cover, int width, int height) {
        int smallWidth = Math.max(12, width / 28), smallHeight = Math.max(9, height / 28);
        Bitmap small = Bitmap.createScaledBitmap(cover, smallWidth, smallHeight, true);
        if (blurSource != null && !blurSource.isRecycled()) blurSource.recycle();
        blurSource = small;
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        bitmap.setShader(null);
        bitmap.setAlpha(255);
        canvas.drawBitmap(small, null, new RectF(0, 0, width, height), bitmap);
        return result;
    }

    private static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        int a = Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t);
        int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t);
        int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t);
        int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t);
        return Color.argb(a, r, g, b);
    }

    private static int withAlpha(int color, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
