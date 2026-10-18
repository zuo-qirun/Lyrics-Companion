/*
 * Port of Folia 0.7.17 FumeBackground.ts (revision 9279251).
 * Copyright (c) chthollyphile and Folia contributors.
 * Licensed under the GNU Affero General Public License v3.0 only.
 */
package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/** Seeded paper-halo decorations drawn behind the fume article. */
final class FoliaFumeBackground {

    private static final int RING = 0;
    private static final int SQUARE = 1;
    private static final int CROSS = 2;
    private static final int SPARK = 3;

    static final class Shape {
        int kind;
        float x;
        float y;
        float width;
        float height;
        float rotation;
        float rotationSpeed;
        float strokeWidth;
        float opacity;
        float depth;
        boolean accent;
        float ringGapStart;
        float ringGapSize;
    }

    static final class Scene {
        float width;
        float height;
        Shape[] shapes;
    }

    private FoliaFumeBackground() {
    }

    private static double seeded(String seed) {
        return (FoliaSceneMath.hash(seed) % 10000) / 10000.0;
    }

    private static float mix(float from, float to, double amount) {
        return (float) (from + (to - from) * amount);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int withAlpha(int color, float alpha) {
        int value = (int) (clamp(alpha, 0f, 1f) * 255f + 0.5f);
        return (color & 0x00FFFFFF) | (value << 24);
    }

    private static int mixColor(int from, int to, float amount) {
        float inverse = 1f - amount;
        int a = (int) ((from >>> 24) * inverse + (to >>> 24) * amount);
        int r = (int) (((from >> 16) & 0xFF) * inverse + ((to >> 16) & 0xFF) * amount);
        int g = (int) (((from >> 8) & 0xFF) * inverse + ((to >> 8) & 0xFF) * amount);
        int b = (int) ((from & 0xFF) * inverse + (to & 0xFF) * amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** Mirrors JavaScript's number-to-string conversion for the seed templates. */
    private static String numberText(float value) {
        if (value == Math.round(value)) return String.valueOf(Math.round(value));
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static float[] choosePaperHaloAnchor(String seedKey,
                                                 float paperLeft, float paperTop,
                                                 float paperRight, float paperBottom,
                                                 float worldWidth, float worldHeight,
                                                 float width, float height) {
        if (seeded(seedKey + ":inside-chance") < 0.22) {
            return new float[]{
                    clamp(mix(paperLeft + width * 0.12f, paperRight - width * 0.12f,
                            seeded(seedKey + ":inside-x")), 0, worldWidth),
                    clamp(mix(paperTop + height * 0.12f, paperBottom - height * 0.12f,
                            seeded(seedKey + ":inside-y")), 0, worldHeight)};
        }
        int side = (int) Math.floor(seeded(seedKey + ":side") * 4) % 4;
        float overflowX = width * mix(0.16f, 0.24f, seeded(seedKey + ":overflow-x"));
        float overflowY = height * mix(0.16f, 0.24f, seeded(seedKey + ":overflow-y"));
        float spanJitterX = width * mix(-0.18f, 0.18f, seeded(seedKey + ":span-jitter-x"));
        float spanJitterY = height * mix(-0.18f, 0.18f, seeded(seedKey + ":span-jitter-y"));
        if (side == 0) {
            return new float[]{
                    clamp(paperLeft - overflowX, 0, worldWidth),
                    clamp(mix(paperTop - height * 0.12f, paperBottom + height * 0.12f,
                            seeded(seedKey + ":y")) + spanJitterY, 0, worldHeight)};
        }
        if (side == 1) {
            return new float[]{
                    clamp(paperRight + overflowX, 0, worldWidth),
                    clamp(mix(paperTop - height * 0.12f, paperBottom + height * 0.12f,
                            seeded(seedKey + ":y")) + spanJitterY, 0, worldHeight)};
        }
        if (side == 2) {
            return new float[]{
                    clamp(mix(paperLeft - width * 0.12f, paperRight + width * 0.12f,
                            seeded(seedKey + ":x")) + spanJitterX, 0, worldWidth),
                    clamp(paperTop - overflowY, 0, worldHeight)};
        }
        return new float[]{
                clamp(mix(paperLeft - width * 0.12f, paperRight + width * 0.12f,
                        seeded(seedKey + ":x")) + spanJitterX, 0, worldWidth),
                clamp(paperBottom + overflowY, 0, worldHeight)};
    }

    static Scene build(float viewportWidth, float viewportHeight,
                       float worldWidth, float worldHeight,
                       float paperLeft, float paperTop, float paperRight, float paperBottom,
                       String seed) {
        Scene scene = new Scene();
        scene.shapes = new Shape[0];
        if (viewportWidth <= 0 || viewportHeight <= 0 || worldWidth <= 0 || worldHeight <= 0) {
            scene.width = Math.max(worldWidth, viewportWidth);
            scene.height = Math.max(worldHeight, viewportHeight);
            return scene;
        }
        String base = seed == null || seed.isEmpty() ? "fume" : seed;
        float worldW = Math.max(worldWidth, viewportWidth * 1.2f);
        float worldH = Math.max(worldHeight, viewportHeight * 1.2f);
        scene.width = worldW;
        scene.height = worldH;

        float baseUnit = clamp(Math.min(viewportWidth, viewportHeight) * 0.72f, 320, 760);
        float left = clamp(Math.min(paperLeft, paperRight), 0, worldW);
        float top = clamp(Math.min(paperTop, paperBottom), 0, worldH);
        float right = clamp(Math.max(paperLeft, paperRight), 0, worldW);
        float bottom = clamp(Math.max(paperTop, paperBottom), 0, worldH);

        int shapeCount = worldW > worldH ? 8 : 7;
        int sparkCount = worldW > worldH ? 12 : 9;
        float sparkLeft = worldW * 0.2f;
        float sparkRight = worldW * 0.8f;
        float sparkTop = worldH * 0.18f;
        float sparkBottom = worldH * 0.82f;
        int sparkColumns = (int) Math.ceil(Math.sqrt(sparkCount
                * (worldW / Math.max(worldH, 1))));
        int sparkRows = (int) Math.ceil(sparkCount / (double) Math.max(1, sparkColumns));
        float sparkCellWidth = (sparkRight - sparkLeft) / Math.max(1, sparkColumns);
        float sparkCellHeight = (sparkBottom - sparkTop) / Math.max(1, sparkRows);

        java.util.List<Shape> shapes = new java.util.ArrayList<>();
        String worldKey = numberText(worldW) + ":" + numberText(worldH);
        for (int index = 0; index < shapeCount; index++) {
            String local = base + ":" + worldKey + ":" + index;
            Shape shape = new Shape();
            shape.kind = index % 3;
            shape.width = baseUnit * mix(0.82f, 1.36f, seeded(local + ":size"));
            shape.height = shape.width;
            float[] anchor = choosePaperHaloAnchor(local, left, top, right, bottom,
                    worldW, worldH, shape.width, shape.height);
            shape.x = anchor[0];
            shape.y = anchor[1];
            shape.rotation = mix((float) (-Math.PI * 0.2), (float) (Math.PI * 0.2),
                    seeded(local + ":rotation"));
            shape.rotationSpeed = mix(-0.045f, 0.045f, seeded(local + ":rotation-speed"));
            shape.strokeWidth = mix(0.25f, 2.1f, seeded(local + ":stroke-width"));
            shape.opacity = mix(0.01f, 0.16f, seeded(local + ":opacity"));
            shape.accent = seeded(local + ":color") > 0.5;
            shape.depth = (float) seeded(local + ":depth");
            if (shape.kind == RING) {
                shape.ringGapStart = mix((float) -Math.PI, (float) Math.PI,
                        seeded(local + ":gap-start"));
                shape.ringGapSize = mix((float) (Math.PI * 0.12), (float) (Math.PI * 0.24),
                        seeded(local + ":gap-size"));
            }
            shapes.add(shape);
        }
        for (int index = 0; index < sparkCount; index++) {
            String local = base + ":" + worldKey + ":spark:" + index;
            Shape shape = new Shape();
            shape.kind = SPARK;
            shape.width = baseUnit * mix(0.1f, 0.24f, seeded(local + ":size"));
            shape.height = shape.width;
            int column = index % Math.max(1, sparkColumns);
            int row = index / Math.max(1, sparkColumns);
            float jitterX = mix(-0.32f, 0.32f, seeded(local + ":jitter-x")) * sparkCellWidth;
            float jitterY = mix(-0.32f, 0.32f, seeded(local + ":jitter-y")) * sparkCellHeight;
            shape.x = clamp(sparkLeft + (column + 0.5f) * sparkCellWidth + jitterX, sparkLeft, sparkRight);
            shape.y = clamp(sparkTop + (row + 0.5f) * sparkCellHeight + jitterY, sparkTop, sparkBottom);
            shape.rotation = mix((float) -Math.PI, (float) Math.PI, seeded(local + ":rotation"));
            shape.rotationSpeed = mix(-0.18f, 0.18f, seeded(local + ":rotation-speed"));
            shape.strokeWidth = mix(0.75f, 1.7f, seeded(local + ":stroke-width"));
            shape.opacity = mix(0.08f, 0.22f, seeded(local + ":opacity"));
            shape.accent = seeded(local + ":color") > 0.5;
            shape.depth = (float) seeded(local + ":depth");
            shapes.add(shape);
        }
        shapes.sort((first, second) -> Float.compare(first.depth, second.depth));
        scene.shapes = shapes.toArray(new Shape[0]);
        return scene;
    }

    private static void buildShapePath(Path path, Shape shape) {
        path.reset();
        if (shape.kind == RING) {
            float radius = shape.width * 0.5f;
            path.addArc(new RectF(-radius, -radius, radius, radius),
                    (float) Math.toDegrees(shape.ringGapStart + shape.ringGapSize),
                    (float) Math.toDegrees(Math.PI * 2 - shape.ringGapSize));
        } else {
            float size = shape.width;
            float half = size * 0.5f;
            if (shape.kind == SQUARE) {
                path.addRect(-half, -half, half, half, Path.Direction.CW);
            } else if (shape.kind == CROSS) {
                float arm = size * 0.3f;
                path.moveTo(-arm, -half);
                path.lineTo(arm, -half);
                path.lineTo(arm, -arm);
                path.lineTo(half, -arm);
                path.lineTo(half, arm);
                path.lineTo(arm, arm);
                path.lineTo(arm, half);
                path.lineTo(-arm, half);
                path.lineTo(-arm, arm);
                path.lineTo(-half, arm);
                path.lineTo(-half, -arm);
                path.lineTo(-arm, -arm);
                path.close();
            } else {
                float inner = size * 0.13f;
                path.moveTo(0, -half);
                path.lineTo(inner, -inner);
                path.lineTo(half, 0);
                path.lineTo(inner, inner);
                path.lineTo(0, half);
                path.lineTo(-inner, inner);
                path.lineTo(-half, 0);
                path.lineTo(-inner, -inner);
                path.close();
            }
        }
    }

    private static Shader lineGradient(Shape shape, float opacity, int secondary, int accent) {
        float halfWidth = shape.width * 0.55f;
        float halfHeight = shape.height * 0.28f;
        int first = withAlpha(secondary, opacity * 0.18f);
        int second = withAlpha(mixColor(secondary, accent, 0.24f), opacity * 0.58f);
        int third = withAlpha(mixColor(secondary, accent, 0.62f), opacity * 0.92f);
        int fourth = withAlpha(accent, opacity * 0.7f);
        return new LinearGradient(-halfWidth, -halfHeight, halfWidth, halfHeight,
                new int[]{first, second, third, fourth},
                new float[]{0f, 0.28f, 0.54f, 1f}, Shader.TileMode.CLAMP);
    }

    /**
     * Draws the seeded decorations. The caller is expected to have already
     * applied the world transform (translate viewport center / scale / camera).
     */
    static void draw(Canvas canvas, Scene scene, Paint paint, float timeSeconds,
                     float viewportWidth, float viewportHeight,
                     float bgScale, float bgCameraX, float bgCameraY,
                     float originX, float originY, float parallaxStrength,
                     int accentColor, int secondaryColor) {
        if (scene == null || scene.shapes.length == 0) return;
        int base = canvas.save();
        canvas.translate(viewportWidth / 2f, viewportHeight / 2f);
        canvas.scale(bgScale, bgScale);
        canvas.translate(-bgCameraX, -bgCameraY);
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);
        Path path = new Path();
        for (Shape shape : scene.shapes) {
            float layerResponse = mix(0.58f, 1.16f, shape.depth);
            float offsetX = (bgCameraX - originX) * (1f - layerResponse) * parallaxStrength;
            float offsetY = (bgCameraY - originY) * (1f - layerResponse) * parallaxStrength;
            float x = shape.x + offsetX;
            float y = shape.y + offsetY;
            float rotation = shape.rotation + timeSeconds * shape.rotationSpeed;
            int color = shape.accent ? accentColor : secondaryColor;
            int save = canvas.save();
            canvas.translate(x, y);
            canvas.rotate((float) Math.toDegrees(rotation));
            if (shape.kind == SPARK) {
                buildShapePath(path, shape);
                paint.setStrokeWidth(shape.strokeWidth);
                paint.setStrokeCap(Paint.Cap.BUTT);
                paint.setColor(withAlpha(color, clamp(shape.opacity, 0, 0.42f)));
                paint.setShadowLayer(10f, 0, 0, withAlpha(color, shape.opacity * 0.75f));
                canvas.drawPath(path, paint);
                paint.clearShadowLayer();
            } else {
                float opacity = clamp(shape.opacity, 0, 0.42f);
                paint.setStrokeCap(shape.kind == RING ? Paint.Cap.ROUND : Paint.Cap.BUTT);
                buildShapePath(path, shape);
                paint.setStrokeWidth(Math.max(shape.strokeWidth * 0.28f, 0.14f));
                paint.setShader(lineGradient(shape, opacity * 0.56f, secondaryColor, accentColor));
                canvas.drawPath(path, paint);
                buildShapePath(path, shape);
                paint.setStrokeWidth(Math.max(shape.strokeWidth * 0.92f, 0.78f));
                paint.setShader(lineGradient(shape, opacity, secondaryColor, accentColor));
                canvas.drawPath(path, paint);
                paint.setShader(null);
            }
            canvas.restoreToCount(save);
        }
        canvas.restoreToCount(base);
    }
}
