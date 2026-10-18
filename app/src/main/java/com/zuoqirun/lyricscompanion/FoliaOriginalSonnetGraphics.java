// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native execution/replay of the original Sonnet AnimatedGraphics formulas (9279251).
package com.zuoqirun.lyricscompanion;

import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bounded native vector cache; hardware Canvas draws paths without per-frame bitmap rasterization. */
final class FoliaOriginalSonnetGraphics {
    private static final int CACHE_LIMIT = 3;
    private final Map<String, List<Node>> cache = new LinkedHashMap<>();
    private final Map<String, Node> textCache = new LinkedHashMap<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path scratch = new Path();
    private final RectF rect = new RectF();
    private AssetManager assets;
    private FoliaOriginalSonnetGeometryProgram geometryProgram;

    void setAssets(AssetManager assets) {
        if (this.assets != assets) { cache.clear(); textCache.clear(); geometryProgram = null; }
        this.assets = assets;
    }

    void clear() {
        cache.clear();
        textCache.clear();
    }

    FoliaOriginalSonnetGuide.RandomSource prepareShot(String kind, long seed, float width, float height) {
        if (assets == null) {
            final long[] state = {seed & 0xffffffffL};
            return () -> { state[0] = (state[0] * 1664525L + 1013904223L) & 0xffffffffL; return state[0] / 4294967296.0; };
        }
        try {
            String key = kind + ':' + seed + ':' + width + ':' + height;
            // Build in source order before constructing text depth and guide random choices.
            List<Node> nodes = build(kind, seed, width, height);
            cache.put(key, nodes);
            while (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
            return geometryProgram::nextRandom;
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("Cannot prepare original Sonnet geometry", error);
        }
    }

    void drawTextFixed(Canvas canvas, long seed, boolean chorus, float fontSize, float layoutWidth,
                       int primary, int secondary, int accent, float opacity) {
        if (assets == null) return;
        String key = seed + ":" + chorus + ":" + fontSize + ":" + layoutWidth;
        Node node = textCache.get(key);
        if (node == null) try {
            if (geometryProgram == null) geometryProgram = new FoliaOriginalSonnetGeometryProgram(readProgram());
            node = readNode(geometryProgram.textFixedGeometry(seed, chorus, fontSize, layoutWidth));
            textCache.put(key, node);
            while (textCache.size() > 32) textCache.remove(textCache.keySet().iterator().next());
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("Cannot load original Sonnet text geometry", error);
        }
        drawNode(canvas, node, 1, 0, 0, 0, 0, 1, layoutWidth, 1,
                primary, secondary, accent, opacity, false);
    }

    List<FoliaOriginalSonnetLayout.Box> layout(FoliaOriginalSonnetProgram.Shot shot, float width,
                                              float height, float fontScale, FoliaOriginalSonnetLayout.Measurer measurer) {
        if (assets == null) return FoliaOriginalSonnetLayout.build(shot, width, height, fontScale, measurer);
        try {
            if (geometryProgram == null) geometryProgram = new FoliaOriginalSonnetGeometryProgram(readProgram());
            return FoliaOriginalSonnetLayout.buildExact(shot, width, height, fontScale, measurer, geometryProgram);
        } catch (IOException | JSONException error) {
            throw new IllegalStateException("Cannot load original Sonnet layout recipes", error);
        }
    }

    boolean draw(Canvas canvas, String kind, long seed, float width, float height, float rawProgress,
                 double secondsSinceStart, float cameraRotation, float cameraX, float cameraY,
                 float cameraScale, int primary, int secondary, boolean lowResourceMode) {
        if (assets == null) return false;
        // Compile every authored seed/viewport formula. Do not quantize to a recorded motif
        // sample or stretch 800x600: HUD margins, circle radius, bleed and hashes are absolute.
        String key = kind + ':' + seed + ':' + width + ':' + height;
        List<Node> nodes = cache.get(key);
        if (nodes == null) {
            try {
                nodes = build(kind, seed, width, height);
            } catch (IOException | JSONException error) {
                return false;
            }
            cache.put(key, nodes);
            while (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
        }
        int save = canvas.save();
        for (Node node : nodes) drawNode(canvas, node, rawProgress, secondsSinceStart, cameraRotation,
                cameraX, cameraY, cameraScale, width, height, primary, secondary, primary, 1, lowResourceMode);
        canvas.restoreToCount(save);
        return true;
    }

    private void drawNode(Canvas canvas, Node node, float progress, double seconds, float cameraRotation,
                          float cameraX, float cameraY, float cameraScale, float width, float height, int primary, int secondary, int accent,
                          float inheritedAlpha, boolean lowResourceMode) {
        if (node.maskNode) return;
        int save = canvas.save();
        float rotation = "fixed".equals(node.motion) ? -cameraRotation : node.rotation;
        float x = node.x;
        float y = node.y;
        float sx = node.scaleX;
        float sy = node.scaleY;
        if ("particles".equals(node.motion)) {
            x += cameraX * width * 0.4f;
            y += cameraY * height * 0.4f;
            rotation += seconds * 0.05;
            sx *= 1 + (cameraScale - 1) * 0.3f;
            sy *= 1 + (cameraScale - 1) * 0.3f;
        }
        canvas.translate(x, y);
        canvas.rotate((float) Math.toDegrees(rotation));
        canvas.scale(sx, sy);
        canvas.translate(-node.pivotX, -node.pivotY);
        if (node.mask != null) canvas.clipPath(node.mask);
        float alpha = inheritedAlpha * node.alpha;
        for (Draw draw : node.draws) {
            float local = node.animated
                    ? FoliaOriginalSonnetMotion.clamp((progress - draw.delay) / draw.span) : 1;
            if (local <= 0) continue;
            float eased = 1 - (float) Math.pow(1 - local, 3);
            int color = draw.color == 2 ? secondary : draw.color == 3 ? accent : primary;
            float opacity = draw.alpha * alpha;
            if (draw.fill && node.animated) opacity *= 1 - (float) Math.pow(1 - Math.min(1, local * 2), 3);
            paint.setColor((color & 0x00ffffff) | Math.round(Color.alpha(color)
                    * Math.max(0, Math.min(1, opacity))) << 24);
            paint.setStyle(draw.fill ? Paint.Style.FILL : Paint.Style.STROKE);
            paint.setStrokeWidth(draw.width);
            if (draw.fill && draw.wipe != null && node.animated && local < 1) {
                canvas.drawRect(draw.wipe.left, draw.wipe.top,
                        draw.wipe.left + draw.wipe.width() * eased, draw.wipe.bottom, paint);
            } else if (!draw.fill && node.animated && local < 1 && draw.length > 0) {
                buildPath(draw.steps, draw.length * eased, scratch);
                canvas.drawPath(scratch, paint);
            } else {
                canvas.drawPath(draw.path, paint);
            }
        }
        int index = 0;
        for (Node child : node.children) {
            // Low-resource mode only thins the independent ornament particles; authored scene
            // geometry and all text remain present. No preference is overwritten.
            if (lowResourceMode && "particles".equals(node.motion) && index++ % 2 != 0) continue;
            drawNode(canvas, child, progress, seconds, cameraRotation, cameraX, cameraY,
                    cameraScale, width, height, primary, secondary, accent, alpha, lowResourceMode);
        }
        canvas.restoreToCount(save);
    }

    private List<Node> build(String kind, long seed, float width, float height) throws IOException, JSONException {
        if (geometryProgram == null) geometryProgram = new FoliaOriginalSonnetGeometryProgram(readProgram());
        JSONArray nodes = geometryProgram.build(kind, width, height, seed).getJSONArray("nodes");
        List<Node> output = new ArrayList<>();
        for (int i = 0; i < nodes.length(); i++) output.add(readNode(nodes.getJSONObject(i)));
        return output;
    }

    private String readProgram() throws IOException {
        byte[] buffer = new byte[8192];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream input = assets.open("folia_original_canvas/sonnet/geometry-program.json")) {
            int count;
            while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
        }
        return new String(bytes.toByteArray(), "UTF-8");
    }

    private Node readNode(JSONObject json) throws JSONException {
        Node node = new Node();
        node.x = (float) json.optDouble("x", 0);
        node.y = (float) json.optDouble("y", 0);
        node.rotation = (float) json.optDouble("rotation", 0);
        node.alpha = (float) json.optDouble("alpha", 1);
        node.scaleX = (float) json.optDouble("scaleX", 1);
        node.scaleY = (float) json.optDouble("scaleY", 1);
        node.pivotX = (float) json.optDouble("pivotX", 0);
        node.pivotY = (float) json.optDouble("pivotY", 0);
        node.motion = json.optString("motion", "");
        node.animated = json.optBoolean("animated", false);
        node.maskNode = json.optBoolean("isMask", false);
        JSONObject mask = json.optJSONObject("mask");
        if (mask != null) {
            Node maskNode = readNode(mask);
            node.mask = new Path();
            for (Draw draw : maskNode.draws) node.mask.addPath(draw.path);
        }
        JSONArray draws = json.optJSONArray("draws");
        int strokes = 0, fills = 0;
        if (draws != null) for (int i = 0; i < draws.length(); i++) {
            JSONObject entry = draws.getJSONObject(i);
            Draw draw = new Draw();
            draw.fill = "fill".equals(entry.getString("type"));
            JSONObject options = entry.getJSONObject("options");
            draw.color = options.optInt("color", 1);
            draw.alpha = (float) options.optDouble("alpha", 1);
            draw.width = (float) options.optDouble("width", 1);
            draw.length = (float) entry.optDouble("length", 0);
            draw.steps = entry.getJSONArray("path");
            draw.path = new Path();
            buildPath(draw.steps, Float.POSITIVE_INFINITY, draw.path);
            int order = draw.fill ? fills++ : strokes++;
            double slot = order * 0.6180339887498949 % 1;
            double jitter = ((order * 2654435761L) & 0xffffffffL) / 4294967296.0;
            draw.delay = (float) (slot * (draw.fill ? 0.45 : 0.5));
            draw.span = (float) Math.min(draw.fill ? 0.4 + jitter * 0.25 : 0.32 + jitter * 0.26, 1 - draw.delay);
            if (draw.fill && draw.steps.length() == 6 && "rect_hint".equals(draw.steps.getJSONObject(0).getString("type"))) {
                JSONObject hint = draw.steps.getJSONObject(0);
                float x = (float) hint.getDouble("x"), y = (float) hint.getDouble("y");
                draw.wipe = new RectF(x, y, x + (float) hint.getDouble("w"), y + (float) hint.getDouble("h"));
            }
            node.draws.add(draw);
        }
        JSONArray children = json.optJSONArray("children");
        if (children != null) for (int i = 0; i < children.length(); i++) node.children.add(readNode(children.getJSONObject(i)));
        return node;
    }

    private void buildPath(JSONArray steps, float target, Path output) {
        output.reset();
        float consumed = 0;
        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.optJSONObject(i);
            if (step == null) continue;
            String type = step.optString("type");
            float length = f(step, "len");
            if ("rect_hint".equals(type)) continue;
            if ("moveTo".equals(type)) {
                output.moveTo(f(step, "x"), f(step, "y"));
                continue;
            }
            if (consumed >= target) break;
            float ratio = length > 0 ? Math.min(1, (target - consumed) / length) : 1;
            float inv = 1 - ratio;
            switch (type) {
                case "lineTo":
                    output.lineTo(f(step, "lastX") + (f(step, "x") - f(step, "lastX")) * ratio,
                            f(step, "lastY") + (f(step, "y") - f(step, "lastY")) * ratio);
                    break;
                case "quadraticCurveTo": {
                    float x = f(step, "lastX"), y = f(step, "lastY");
                    float cx = f(step, "cx"), cy = f(step, "cy");
                    float tx = f(step, "tx"), ty = f(step, "ty");
                    output.quadTo(x + ratio * (cx - x), y + ratio * (cy - y),
                            inv * inv * x + 2 * inv * ratio * cx + ratio * ratio * tx,
                            inv * inv * y + 2 * inv * ratio * cy + ratio * ratio * ty);
                    break;
                }
                case "bezierCurveTo": {
                    float x = f(step, "lastX"), y = f(step, "lastY");
                    float c1x = f(step, "c1x"), c1y = f(step, "c1y");
                    float c2x = f(step, "c2x"), c2y = f(step, "c2y");
                    float tx = f(step, "tx"), ty = f(step, "ty");
                    float a1x = x + ratio * (c1x - x), a1y = y + ratio * (c1y - y);
                    float a2x = c1x + ratio * (c2x - c1x), a2y = c1y + ratio * (c2y - c1y);
                    float b1x = a1x + ratio * (a2x - a1x), b1y = a1y + ratio * (a2y - a1y);
                    output.cubicTo(a1x, a1y, b1x, b1y,
                            inv * inv * inv * x + 3 * inv * inv * ratio * c1x + 3 * inv * ratio * ratio * c2x + ratio * ratio * ratio * tx,
                            inv * inv * inv * y + 3 * inv * inv * ratio * c1y + 3 * inv * ratio * ratio * c2y + ratio * ratio * ratio * ty);
                    break;
                }
                case "arc": {
                    float cx = f(step, "cx"), cy = f(step, "cy"), radius = f(step, "r");
                    float start = f(step, "start");
                    float diff = step.has("diff") ? f(step, "diff") : f(step, "end") - start;
                    if (!step.has("diff")) {
                        if (step.optBoolean("anticlockwise") && diff > 0) diff -= Math.PI * 2;
                        else if (!step.optBoolean("anticlockwise") && diff < 0) diff += Math.PI * 2;
                    }
                    rect.set(cx - radius, cy - radius, cx + radius, cy + radius);
                    output.arcTo(rect, (float) Math.toDegrees(start), (float) Math.toDegrees(diff * ratio), false);
                    break;
                }
                case "circle":
                    output.addCircle(f(step, "x"), f(step, "y"), f(step, "r"), Path.Direction.CW);
                    break;
                case "rect":
                    output.addRect(f(step, "x"), f(step, "y"), f(step, "x") + f(step, "w"),
                            f(step, "y") + f(step, "h"), Path.Direction.CW);
                    break;
                default:
                    break;
            }
            consumed += length;
        }
    }

    private static float f(JSONObject object, String key) {
        return (float) object.optDouble(key, 0);
    }

    private static final class Node {
        float x, y, rotation, alpha, scaleX, scaleY, pivotX, pivotY;
        String motion;
        boolean animated, maskNode;
        Path mask;
        final List<Draw> draws = new ArrayList<>();
        final List<Node> children = new ArrayList<>();
    }

    private static final class Draw {
        Path path;
        JSONArray steps;
        RectF wipe;
        float length, width, alpha, delay, span;
        int color;
        boolean fill;
    }
}
