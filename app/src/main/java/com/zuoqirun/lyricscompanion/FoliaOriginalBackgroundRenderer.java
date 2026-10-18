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
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

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

    /** Upstream theme.backgroundColor used by every background preset. */
    private static final int THEME_BACKGROUND = 0xFF09090B;

    private static final int[] BAYER_8X8 = {
            0, 32, 8, 40, 2, 34, 10, 42,
            48, 16, 56, 24, 50, 18, 58, 26,
            12, 44, 4, 36, 14, 46, 6, 38,
            60, 28, 52, 20, 62, 30, 54, 22,
            3, 35, 11, 43, 1, 33, 9, 41,
            51, 19, 59, 27, 49, 17, 57, 25,
            15, 47, 7, 39, 13, 45, 5, 37,
            63, 31, 55, 23, 61, 29, 53, 21,
    };

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmap = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pixelBitmap = new Paint();
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();

    private int primaryColor = 0xFFF5F8FF;
    private int accentColor = 0xFFFF984B;
    private int mutedColor = 0xFFB9BBC5;

    private String cachedMode = "";
    private int cachedWidth, cachedHeight;
    private Bitmap cachedCover;
    private Bitmap blurSource;
    private Bitmap ditherTile;
    private Bitmap monetBitmap;
    private Bitmap nomandBitmap;
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
        blurSource = null;
        ditherTile = null;
        shapes = null;
        stars = null;
        recycle(monetBitmap);
        monetBitmap = null;
        recycle(nomandBitmap);
        nomandBitmap = null;
    }

    void draw(Canvas canvas, float width, float height, long time, String mode, Bitmap cover,
              int primary, int accent, int muted, float opacity) {
        String normalized = normalize(mode);
        if (normalized.isEmpty() || width <= 0 || height <= 0) return;
        float alpha = Math.max(0f, Math.min(1.5f, opacity));
        if (alpha <= 0.01f) return;
        primaryColor = primary;
        accentColor = accent;
        mutedColor = muted;
        int w = Math.round(width), h = Math.round(height);
        if (!normalized.equals(cachedMode) || w != cachedWidth || h != cachedHeight
                || cover != cachedCover) {
            cachedMode = normalized;
            cachedWidth = w;
            cachedHeight = h;
            cachedCover = cover;
            shapes = null;
            stars = null;
            blurSource = null;
            recycle(monetBitmap);
            monetBitmap = null;
            recycle(nomandBitmap);
            nomandBitmap = null;
        }
        if (NONE.equals(normalized)) return;
        if (COMMON.equals(normalized)) {
            drawCommon(canvas, width, height, time / 1000f, alpha);
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

    // ---------------------------------------------------------------- common

    private void drawCommon(Canvas canvas, float width, float height, float time, float alpha) {
        if (shapes == null) shapes = buildCommonShapes(width, height);
        fill.setShader(null);
        stroke.setShader(null);
        canvas.drawColor(THEME_BACKGROUND);
        int base = blend(mutedColor, THEME_BACKGROUND, 0.62f);
        for (int i = 0; i + 7 < shapes.length; i += 8) {
            float x = shapes[i] * width;
            float y = shapes[i + 1] * height;
            float size = shapes[i + 2];
            int kind = (int) shapes[i + 3];
            boolean filled = shapes[i + 4] > 0.68f;
            float rotation = shapes[i + 5] * 360f + time * shapes[i + 6] * 12f;
            float shapeAlpha = (0.11f + 0.08f * shapes[i + 7]) * alpha;
            int color = withAlpha(base, shapeAlpha);
            fill.setColor(color);
            stroke.setColor(color);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1f);
            canvas.save();
            canvas.translate(x, y);
            canvas.rotate(rotation);
            switch (kind) {
                case 0:
                    if (filled) canvas.drawCircle(0, 0, size / 2f, fill);
                    else canvas.drawCircle(0, 0, size / 2f, stroke);
                    break;
                case 1:
                    rect.set(-size / 2f, -size / 2f, size / 2f, size / 2f);
                    if (filled) canvas.drawRect(rect, fill);
                    else canvas.drawRect(rect, stroke);
                    break;
                case 2:
                    path.reset();
                    path.moveTo(0, -size / 2f);
                    path.lineTo(size / 2f, size / 2f);
                    path.lineTo(-size / 2f, size / 2f);
                    path.close();
                    if (filled) canvas.drawPath(path, fill);
                    else canvas.drawPath(path, stroke);
                    break;
                default:
                    drawCross(canvas, size, filled);
                    break;
            }
            canvas.restore();
        }
        drawParticles(canvas, width, height, time, alpha);
        drawVignette(canvas, width, height, 0.6f * alpha);
    }

    private void drawCross(Canvas canvas, float size, boolean filled) {
        float[][] points = {
                {0.2f, 0.0f}, {0.0f, 0.2f}, {0.3f, 0.5f}, {0.0f, 0.8f},
                {0.2f, 1.0f}, {0.5f, 0.7f}, {0.8f, 1.0f}, {1.0f, 0.8f},
                {0.7f, 0.5f}, {1.0f, 0.2f}, {0.8f, 0.0f}, {0.5f, 0.3f},
        };
        path.reset();
        for (int i = 0; i < points.length; i++) {
            float x = (points[i][0] - 0.5f) * size;
            float y = (points[i][1] - 0.5f) * size;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        if (filled) canvas.drawPath(path, fill);
        else canvas.drawPath(path, stroke);
    }

    private void drawParticles(Canvas canvas, float width, float height, float time, float alpha) {
        double seed = FoliaSceneMath.hash("folia-bg-common-particles");
        fill.setShader(null);
        for (int i = 0; i < 20; i++) {
            float x = (float) FoliaSceneMath.random(seed + i * 3) * width;
            float baseY = (float) FoliaSceneMath.random(seed + i * 3 + 1) * height;
            float speed = 4f + (float) FoliaSceneMath.random(seed + i * 3 + 2) * 10f;
            float y = baseY - (time * speed) % Math.max(1f, height);
            if (y < 0) y += height;
            float size = 1f + (float) FoliaSceneMath.random(seed + i * 7) * 4f;
            float particleAlpha = (float) FoliaSceneMath.random(seed + i * 11) * 0.3f * alpha;
            fill.setColor(withAlpha(accentColor, particleAlpha));
            canvas.drawCircle(x, y, size / 2f, fill);
        }
    }

    private float[] buildCommonShapes(float width, float height) {
        double seed = FoliaSceneMath.hash("folia-bg-common");
        float[] values = new float[15 * 8];
        for (int i = 0; i < 15; i++) {
            int base = i * 8;
            values[base] = (float) FoliaSceneMath.random(seed + base);
            values[base + 1] = (float) FoliaSceneMath.random(seed + base + 1);
            values[base + 2] = 40f + (float) FoliaSceneMath.random(seed + base + 2) * 100f;
            values[base + 3] = (float) Math.floor(FoliaSceneMath.random(seed + base + 3) * 4f);
            values[base + 4] = (float) FoliaSceneMath.random(seed + base + 4);
            values[base + 5] = (float) FoliaSceneMath.random(seed + base + 5);
            values[base + 6] = (float) FoliaSceneMath.random(seed + base + 6) * 2f - 1f;
            values[base + 7] = (float) FoliaSceneMath.random(seed + base + 7);
        }
        return values;
    }

    // ---------------------------------------------------------------- latent

    private void drawLatent(Canvas canvas, float width, float height, float time, float alpha) {
        canvas.drawColor(THEME_BACKGROUND);
        fill.setShader(null);
        int ember = blend(accentColor, 0xFFFFB066, 0.42f);
        int dusk = blend(blend(mutedColor, 0xFF6A78A6, 0.6f), THEME_BACKGROUND, 0.22f);
        float cx = width * (0.54f + 0.03f * (float) Math.sin(time * 0.25));
        float cy = height * (0.2f + 0.02f * (float) Math.cos(time * 0.21));
        fill.setShader(new RadialGradient(cx, cy, Math.max(width, height) * 0.6f,
                new int[]{withAlpha(blend(ember, Color.WHITE, 0.1f), 0.85f * alpha),
                        withAlpha(ember, 0.44f * alpha), withAlpha(ember, 0.12f * alpha),
                        Color.TRANSPARENT},
                new float[]{0f, 0.4f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        // The reference smear runs from the upper middle toward the right edge.
        fill.setShader(new LinearGradient(width * 0.3f, -height * 0.1f, width * 1.05f, height * 0.5f,
                new int[]{Color.TRANSPARENT, withAlpha(blend(ember, Color.WHITE, 0.12f), 0.32f * alpha),
                        Color.TRANSPARENT},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(new RadialGradient(width * 0.9f, height * 0.95f, Math.max(width, height) * 0.5f,
                new int[]{withAlpha(0xFF4E5A85, 0.28f * alpha), Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(new RadialGradient(width * 0.05f, height * 0.88f, Math.max(width, height) * 0.78f,
                new int[]{withAlpha(dusk, 0.72f * alpha), withAlpha(dusk, 0.28f * alpha),
                        Color.TRANSPARENT},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(new RadialGradient(width * 0.94f, height * 0.45f, Math.max(width, height) * 0.46f,
                new int[]{withAlpha(blend(ember, dusk, 0.5f), 0.26f * alpha), Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        // Cool rim from the top-right keeps the field from reading as pure orange.
        fill.setShader(new LinearGradient(width, 0, 0, height,
                new int[]{withAlpha(0xFF6D7EAE, 0.18f * alpha), Color.TRANSPARENT},
                new float[]{0f, 0.7f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(null);
        drawDither(canvas, width, height, alpha * 0.42f);
        // Upstream default overlay: 35% theme background over both shader layers.
        fill.setShader(null);
        fill.setColor(withAlpha(THEME_BACKGROUND, 0.35f * alpha));
        canvas.drawRect(0, 0, width, height, fill);
        drawVignette(canvas, width, height, 0.4f * alpha);
    }

    private void drawDither(Canvas canvas, float width, float height, float alpha) {
        if (ditherTile == null) ditherTile = buildDitherTile();
        bitmap.setShader(null);
        bitmap.setColorFilter(null);
        matrix.reset();
        matrix.setScale(2.4f, 2.4f);
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
        int[] pixels = new int[]{
                0x38FFFFFF, 0x00000000, 0x1CFFFFFF, 0x00000000,
                0x00000000, 0x2AFFFFFF, 0x00000000, 0x14FFFFFF,
                0x1CFFFFFF, 0x00000000, 0x34FFFFFF, 0x00000000,
                0x00000000, 0x14FFFFFF, 0x00000000, 0x2AFFFFFF
        };
        tile.setPixels(pixels, 0, 4, 0, 0, 4, 4);
        return tile;
    }

    // ---------------------------------------------------------------- monet

    private void drawMonet(Canvas canvas, float width, float height, float alpha) {
        Bitmap processed = monetBitmap(Math.round(width), Math.round(height));
        bitmap.setShader(null);
        bitmap.setColorFilter(null);
        bitmap.setAlpha(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255));
        if (processed != null) {
            canvas.drawBitmap(processed, null, new RectF(0, 0, width, height), bitmap);
        } else {
            canvas.drawColor(THEME_BACKGROUND);
        }
        bitmap.setAlpha(255);

        // paintMonetOverlay(): upstream bakes this into the 1920x1080 poster bitmap.
        float overlay = 0.74f;
        fill.setShader(null);
        fill.setShader(new LinearGradient(0, 0, width, height,
                new int[]{withAlpha(accentColor, (0.08f + overlay * 0.62f) * alpha),
                        withAlpha(THEME_BACKGROUND, (0.16f + overlay * 0.58f) * alpha),
                        withAlpha(primaryColor, (0.06f + overlay * 0.36f) * alpha)},
                new float[]{0f, 0.44f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(new RadialGradient(width * 0.18f, height * 0.34f, width * 0.55f,
                new int[]{withAlpha(accentColor, overlay * 0.52f * alpha), Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(new LinearGradient(0, 0, width * 0.5f, height * 0.6f,
                new int[]{withAlpha(blend(accentColor, Color.WHITE, 0.2f), 0.2f * alpha),
                        Color.TRANSPARENT},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(new LinearGradient(width * 0.45f, 0, width, 0,
                new int[]{withAlpha(THEME_BACKGROUND, 0f),
                        withAlpha(THEME_BACKGROUND, (0.18f + overlay * 0.38f) * alpha)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        // 18 faint vertical streaks.
        fill.setShader(null);
        fill.setColor(withAlpha(THEME_BACKGROUND, (0.1f + overlay * 0.08f) * alpha));
        for (int index = 0; index < 18; index++) {
            float x = (index * 127) % Math.max(1f, width);
            float y = ((index * 211) % Math.max(1f, height)) - 40;
            canvas.drawRect(x, y, x + 1, y + height * 0.28f, fill);
        }
        // readabilityGradient.
        fill.setShader(new LinearGradient(0, 0, width, 0,
                new int[]{withAlpha(THEME_BACKGROUND, 0.18f * alpha),
                        withAlpha(THEME_BACKGROUND, 0.32f * alpha),
                        withAlpha(THEME_BACKGROUND, 0.66f * alpha),
                        withAlpha(THEME_BACKGROUND, 0.82f * alpha)},
                new float[]{0f, 0.34f, 0.7f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        // The browser reference keeps the wash dimmer than the raw cover: flatten it with one
        // more theme-coloured pass and a soft vignette.
        fill.setShader(null);
        fill.setColor(withAlpha(THEME_BACKGROUND, 0.38f * alpha));
        canvas.drawRect(0, 0, width, height, fill);
        drawVignette(canvas, width, height, 0.3f * alpha);
    }

    /**
     * Rebuilds the static Monet poster (cover + wash + overlay) at a reduced resolution so the
     * cost stays bounded on API 19 phones. Mirrors monetBackgroundPipeline.ts.
     */
    private Bitmap monetBitmap(int width, int height) {
        int pw = Math.max(2, Math.min(width, 480));
        int ph = Math.max(2, Math.min(height, Math.round(pw * (height / (float) width))));
        if (monetBitmap != null && !monetBitmap.isRecycled()
                && monetBitmap.getWidth() == pw && monetBitmap.getHeight() == ph) {
            return monetBitmap;
        }
        recycle(monetBitmap);
        Bitmap base = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
        Canvas poster = new Canvas(base);
        poster.drawColor(THEME_BACKGROUND);
        if (cachedCover != null && !cachedCover.isRecycled()) {
            Bitmap blurred = blurredCover(pw, ph);
            if (blurred != null) {
                poster.drawBitmap(blurred, null, new RectF(0, 0, pw, ph), bitmap);
            }
        }
        applyMonetPostProcess(base, pw, ph);
        monetBitmap = base;
        return base;
    }

    /** Cover-cropped blur standing in for the upstream 6px canvas blur. */
    private Bitmap blurredCover(int width, int height) {
        Bitmap cover = cachedCover;
        if (cover == null || cover.isRecycled()) return null;
        cropRect(cover, width, height);
        int smallWidth = Math.max(6, width / 8), smallHeight = Math.max(6, height / 8);
        Bitmap small = Bitmap.createBitmap(smallWidth, smallHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(small);
        RectF dstRect = new RectF(0, 0, smallWidth, smallHeight);
        canvas.drawBitmap(cover, src, dstRect, bitmap);
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas up = new Canvas(result);
        up.drawBitmap(small, null, new RectF(0, 0, width, height), bitmap);
        if (small != result) recycle(small);
        if (blurSource != null && blurSource != result) recycle(blurSource);
        blurSource = result;
        return result;
    }

    private void applyMonetPostProcess(Bitmap base, int width, int height) {
        int[] pixels = new int[width * height];
        base.getPixels(pixels, 0, width, 0, 0, width, height);
        int background = THEME_BACKGROUND;
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int a = Color.alpha(color);
            if (a == 0) continue;
            float r = Color.red(color), g = Color.green(color), b = Color.blue(color);
            float luminance = luminance(r, g, b);
            // Upstream default tuning: saturation 1.05, grayscale 0.
            r = luminance + (r - luminance) * 1.05f;
            g = luminance + (g - luminance) * 1.05f;
            b = luminance + (b - luminance) * 1.05f;
            // Upstream default tuning: wash 0.34 toward the theme-derived wash colour.
            int wash = resolveWashColor(luminance / 255f);
            r = r + (Color.red(wash) - r) * 0.34f;
            g = g + (Color.green(wash) - g) * 0.34f;
            b = b + (Color.blue(wash) - b) * 0.34f;
            pixels[i] = Color.argb(a, clamp255(r), clamp255(g), clamp255(b));
        }
        base.setPixels(pixels, 0, width, 0, 0, width, height);
    }

    private int resolveWashColor(float normalizedLuminance) {
        float backgroundLuminance = luminance(Color.red(THEME_BACKGROUND), Color.green(THEME_BACKGROUND),
                Color.blue(THEME_BACKGROUND));
        float primaryLuminance = luminance(Color.red(primaryColor), Color.green(primaryColor),
                Color.blue(primaryColor));
        int shadow = backgroundLuminance <= primaryLuminance ? THEME_BACKGROUND : primaryColor;
        int highlight = backgroundLuminance <= primaryLuminance ? primaryColor : THEME_BACKGROUND;
        float amount;
        int from, to;
        if (normalizedLuminance <= 0.55f) {
            amount = normalizedLuminance / 0.55f;
            from = shadow;
            to = accentColor;
        } else {
            amount = (normalizedLuminance - 0.55f) / 0.45f;
            from = accentColor;
            to = highlight;
        }
        return blend(from, to, amount);
    }

    // ---------------------------------------------------------------- nomand

    private void drawNomand(Canvas canvas, float width, float height, float alpha) {
        Bitmap dithered = nomandBitmap(Math.round(width), Math.round(height));
        pixelBitmap.setShader(null);
        pixelBitmap.setAlpha(Math.round(Math.max(0f, Math.min(1f, alpha)) * 255));
        if (dithered != null) {
            canvas.drawBitmap(dithered, null, new RectF(0, 0, width, height), pixelBitmap);
        } else {
            canvas.drawColor(THEME_BACKGROUND);
        }
        pixelBitmap.setAlpha(255);
        // The reference keeps a faint diagonal light drift over the flat halftone plate.
        fill.setShader(null);
        fill.setShader(new LinearGradient(0, 0, width, height,
                new int[]{withAlpha(Color.WHITE, 0.03f * alpha), withAlpha(Color.BLACK, 0.05f * alpha),
                        withAlpha(Color.BLACK, 0.09f * alpha)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(null);
    }

    /**
     * ImageDithering(8x8, size 3, colorSteps 4) approximation. The reference plate is dominated by
     * the accent colour (the cover is mostly mapped to the front colour) with a fine dark dot
     * pattern, so keep a flat accent base and only stamp darker/lighter cells from the cover.
     */
    private Bitmap nomandBitmap(int width, int height) {
        int cell = 3;
        int cols = Math.max(1, (width + cell - 1) / cell);
        int rows = Math.max(1, (height + cell - 1) / cell);
        if (nomandBitmap != null && !nomandBitmap.isRecycled()
                && nomandBitmap.getWidth() == cols && nomandBitmap.getHeight() == rows) {
            return nomandBitmap;
        }
        recycle(nomandBitmap);
        int[] sampled = new int[cols * rows];
        if (cachedCover != null && !cachedCover.isRecycled()) {
            Bitmap sample = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(sample);
            cropRect(cachedCover, cols, rows);
            canvas.drawBitmap(cachedCover, src, new RectF(0, 0, cols, rows), bitmap);
            sample.getPixels(sampled, 0, cols, 0, 0, cols, rows);
            recycle(sample);
        } else {
            for (int i = 0; i < sampled.length; i++) sampled[i] = THEME_BACKGROUND;
        }
        int base = blend(accentColor, THEME_BACKGROUND, 0.65f);
        int darkDot = blend(base, THEME_BACKGROUND, 0.62f);
        int lightDot = blend(base, primaryColor, 0.22f);
        int[] out = new int[cols * rows];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                int color = sampled[y * cols + x];
                float luminance = luminance(Color.red(color), Color.green(color), Color.blue(color)) / 255f;
                float threshold = BAYER_8X8[(y & 7) * 8 + (x & 7)] / 64f - 0.5f;
                float value = luminance + threshold * 0.5f;
                if (value < 0.34f) {
                    out[y * cols + x] = darkDot;
                } else if (value > 0.82f) {
                    out[y * cols + x] = lightDot;
                } else {
                    out[y * cols + x] = base;
                }
            }
        }
        Bitmap result = Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888);
        result.setPixels(out, 0, cols, 0, 0, cols, rows);
        nomandBitmap = result;
        return result;
    }

    // ---------------------------------------------------------------- sora

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
        fill.setAlpha(255);
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

    // ---------------------------------------------------------------- shared

    private void drawVignette(Canvas canvas, float width, float height, float alpha) {
        fill.setShader(new RadialGradient(width / 2, height / 2, Math.max(width, height) * 0.75f,
                new int[]{Color.TRANSPARENT, Color.TRANSPARENT, withAlpha(Color.BLACK, alpha)},
                new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, height, fill);
        fill.setShader(null);
    }

    /** Centre-crops the cover to the destination aspect ratio, filling {@link #src}. */
    private void cropRect(Bitmap cover, int width, int height) {
        int coverWidth = Math.max(1, cover.getWidth());
        int coverHeight = Math.max(1, cover.getHeight());
        float targetAspect = width / (float) Math.max(1, height);
        float coverAspect = coverWidth / (float) coverHeight;
        if (coverAspect > targetAspect) {
            int croppedWidth = Math.max(1, Math.round(coverHeight * targetAspect));
            int left = (coverWidth - croppedWidth) / 2;
            src.set(left, 0, left + croppedWidth, coverHeight);
        } else {
            int croppedHeight = Math.max(1, Math.round(coverWidth / targetAspect));
            int top = (coverHeight - croppedHeight) / 2;
            src.set(0, top, coverWidth, top + croppedHeight);
        }
        dst.set(0, 0, width, height);
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

    private static float luminance(float r, float g, float b) {
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    private static int clamp255(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static void recycle(Bitmap value) {
        if (value != null && !value.isRecycled()) value.recycle();
    }
}
