// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// AGSL port of temperaDifferenceFilter.ts at 9279251.
package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;

/** Both shader inputs remain on the GPU: two regions of one recorded hardware layer. */
final class FoliaTemperaGpuInversion {
    interface Layer { void draw(Canvas canvas); }
    private Object state;
    private boolean unavailable;
    private final Cpu fallback = new Cpu();

    boolean draw(Canvas canvas, String instance, int width, int height, int ink, int paper, Layer background, Layer mask) {
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated() && !unavailable) try {
            if (state == null) state = new Api33();
            ((Api33) state).draw(canvas, instance, width, height, ink, paper, background, mask);
            return true;
        } catch (RuntimeException | LinkageError error) {
            unavailable = true; state = null;
        }
        fallback.draw(canvas, instance, ink, paper, background, mask);
        return true;
    }

    void clear() { state = null; unavailable = false; fallback.clear(); }

    @androidx.annotation.RequiresApi(33)
    private static final class Api33 {
        private static final String SOURCE =
                "uniform shader scene; uniform float width; uniform float3 ink; uniform float3 paper;" +
                "float lum(float3 color){return dot(color,float3(0.2126,0.7152,0.0722));}" +
                "float backLum(float2 p){half4 b=scene.eval(p);float3 straight=b.rgb/max(b.a,0.0001);" +
                "return mix(lum(paper),lum(straight),clamp(b.a*3.0,0.0,1.0));}" +
                "half4 main(float2 p){if(p.x>=width)return half4(0);" +
                "half4 back=scene.eval(p);half4 front=scene.eval(p+float2(width,0));" +
                "float2 d=float2(1.5);float light=backLum(p)*0.4+" +
                "(backLum(p+d)+backLum(p-d)+backLum(p+float2(d.x,-d.y))+backLum(p+float2(-d.x,d.y)))*0.15;" +
                "float3 tone=mix(ink,paper,step(abs(light-lum(ink)),abs(light-lum(paper))));" +
                "return half4(tone*front.a+back.rgb*(1.0-front.a),front.a+back.a*(1.0-front.a));}";
        // Different shots must not mutate a node already referenced by the outgoing display list.
        private final android.util.LruCache<String, Surface> nodes = new android.util.LruCache<>(4);

        void draw(Canvas canvas, String instance, int width, int height, int ink, int paper, Layer background, Layer mask) {
            Rect bounds = canvas.getClipBounds(); bounds.inset(-3, -3);
            int planeWidth = bounds.width(), planeHeight = bounds.height();
            String key = width + "x" + height + ':' + instance;
            Surface surface = nodes.get(key);
            if (surface == null) { surface = new Surface(); nodes.put(key, surface); }
            android.graphics.RenderNode node = surface.node;
            android.graphics.RuntimeShader shader = surface.shader;
            node.setPosition(0, 0, planeWidth * 2, planeHeight);
            Canvas recording = node.beginRecording(planeWidth * 2, planeHeight);
            int save = recording.save(); recording.clipRect(0, 0, planeWidth, planeHeight); recording.translate(-bounds.left, -bounds.top); background.draw(recording); recording.restoreToCount(save);
            save = recording.save(); recording.clipRect(planeWidth, 0, planeWidth * 2, planeHeight); recording.translate(planeWidth - bounds.left, -bounds.top); mask.draw(recording); recording.restoreToCount(save);
            node.endRecording();
            shader.setFloatUniform("width", planeWidth);
            shader.setFloatUniform("ink", Color.red(ink) / 255f, Color.green(ink) / 255f, Color.blue(ink) / 255f);
            shader.setFloatUniform("paper", Color.red(paper) / 255f, Color.green(paper) / 255f, Color.blue(paper) / 255f);
            node.setRenderEffect(android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "scene"));
            int clip = canvas.save(); canvas.translate(bounds.left, bounds.top); canvas.clipRect(0, 0, planeWidth, planeHeight); canvas.drawRenderNode(node); canvas.restoreToCount(clip);
        }

        private static final class Surface {
            final android.graphics.RuntimeShader shader = new android.graphics.RuntimeShader(SOURCE);
            final android.graphics.RenderNode node = new android.graphics.RenderNode("Folia Tempera artwork and glyph mask");
        }
    }

    /** Same threshold and premultiplied bilinear five taps when RuntimeShader is unavailable. */
    private static final class Cpu {
        private static final int RING = 5;
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private Bitmap back, front;
        private int[] rowBack = new int[0], rowFront = new int[0], rowOut = new int[0], rowScratch = new int[0];
        private final float[][] alphaRing = new float[RING][];
        private final float[][] premulRing = new float[RING][];
        private final int[] ringTag = new int[RING];
        // RenderThread may still reference an evicted result. Only private scratch planes recycle.
        private final RenderBufferCache<Bitmap> results = new RenderBufferCache<>(4 * 1024 * 1024, true);

        Cpu() { java.util.Arrays.fill(ringTag, Integer.MIN_VALUE); }

        void draw(Canvas target, String instance, int ink, int paper, Layer artwork, Layer mask) {
            Rect bounds = target.getClipBounds(); bounds.inset(-3, -3);
            int width = Math.max(1, bounds.width()), height = Math.max(1, bounds.height());
            int reservedWidth = (width + 63) / 64 * 64, reservedHeight = (height + 63) / 64 * 64;
            boolean tooSmall = back == null || back.isRecycled() || front == null || front.isRecycled()
                    || back.getWidth() < reservedWidth || back.getHeight() < reservedHeight;
            boolean oversized = back != null && (back.getWidth() > reservedWidth * 2 || back.getHeight() > reservedHeight * 2);
            if (tooSmall || oversized) {
                if (back != null && !back.isRecycled()) back.recycle();
                if (front != null && !front.isRecycled()) front.recycle();
                back = Bitmap.createBitmap(reservedWidth, reservedHeight, Bitmap.Config.ARGB_8888);
                front = Bitmap.createBitmap(reservedWidth, reservedHeight, Bitmap.Config.ARGB_8888);
            }
            back.eraseColor(Color.TRANSPARENT); front.eraseColor(Color.TRANSPARENT);
            Canvas artCanvas = new Canvas(back), maskCanvas = new Canvas(front);
            artCanvas.translate(-bounds.left, -bounds.top); maskCanvas.translate(-bounds.left, -bounds.top);
            artwork.draw(artCanvas); mask.draw(maskCanvas);
            float paperLum = luminance(paper), inkLum = luminance(ink);
            Bitmap result = results.get(instance);
            if (result == null || result.isRecycled() || result.getWidth() < width || result.getHeight() < height
                    || result.getWidth() > reservedWidth * 2 || result.getHeight() > reservedHeight * 2) {
                result = Bitmap.createBitmap(reservedWidth, reservedHeight, Bitmap.Config.ARGB_8888);
                results.put(instance, result, result.getByteCount());
            }
            ensureRows(width);
            java.util.Arrays.fill(ringTag, Integer.MIN_VALUE);
            for (int y = 0; y < height; y++) {
                back.getPixels(rowBack, 0, width, 0, y, width, 1);
                front.getPixels(rowFront, 0, width, 0, y, width, 1);
                for (int x = 0; x < width; x++) {
                    int frontAlphaByte = rowFront[x] >>> 24;
                    if (frontAlphaByte == 0) { rowOut[x] = rowBack[x]; continue; }
                    float frontAlpha = frontAlphaByte / 255f;
                    float lum = sample(x, y, width, height, paperLum) * .4f
                            + (sample(x + 1.5f, y + 1.5f, width, height, paperLum) + sample(x - 1.5f, y - 1.5f, width, height, paperLum)
                            + sample(x + 1.5f, y - 1.5f, width, height, paperLum) + sample(x - 1.5f, y + 1.5f, width, height, paperLum)) * .15f;
                    int tone = Math.abs(lum - paperLum) >= Math.abs(lum - inkLum) ? paper : ink;
                    int backColor = rowBack[x];
                    float backAlpha = Color.alpha(backColor) / 255f;
                    float alpha = frontAlpha + backAlpha * (1 - frontAlpha);
                    float backWeight = backAlpha * (1 - frontAlpha);
                    rowOut[x] = Color.argb(Math.round(alpha * 255),
                            Math.round((Color.red(tone) * frontAlpha + Color.red(backColor) * backWeight) / alpha),
                            Math.round((Color.green(tone) * frontAlpha + Color.green(backColor) * backWeight) / alpha),
                            Math.round((Color.blue(tone) * frontAlpha + Color.blue(backColor) * backWeight) / alpha));
                }
                result.setPixels(rowOut, 0, width, 0, y, width, 1);
            }
            target.drawBitmap(result, new Rect(0, 0, width, height), new android.graphics.RectF(bounds), paint);
        }

        private void ensureRows(int width) {
            if (rowBack.length < width) {
                rowBack = new int[width]; rowFront = new int[width]; rowOut = new int[width]; rowScratch = new int[width];
            }
            for (int slot = 0; slot < RING; slot++) {
                if (alphaRing[slot] == null || alphaRing[slot].length < width) {
                    alphaRing[slot] = new float[width]; premulRing[slot] = new float[width];
                }
            }
        }

        private void fillRing(int yy, int width) {
            int slot = yy % RING;
            if (ringTag[slot] == yy || yy < 0 || yy >= front.getHeight()) return;
            back.getPixels(rowScratch, 0, width, 0, yy, width, 1);
            float[] alpha = alphaRing[slot], premul = premulRing[slot];
            for (int x = 0; x < width; x++) {
                int color = rowScratch[x];
                float a = Color.alpha(color) / 255f;
                alpha[x] = a; premul[x] = luminance(color) * a;
            }
            ringTag[slot] = yy;
        }

        private float sample(float x, float y, int width, int height, float paperLum) {
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y); float dx = x - x0, dy = y - y0;
            float alpha = 0, luminance = 0;
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
                int xx = x0 + col, yy = y0 + row;
                if (xx < 0 || xx >= width || yy < 0 || yy >= height) continue;
                fillRing(yy, width);
                int slot = yy % RING;
                float weight = (col == 0 ? 1 - dx : dx) * (row == 0 ? 1 - dy : dy);
                alpha += alphaRing[slot][xx] * weight; luminance += premulRing[slot][xx] * weight;
            }
            float straight = luminance / Math.max(alpha, .0001f), weight = Math.min(1, alpha * 3);
            return paperLum * (1 - weight) + straight * weight;
        }

        private static float luminance(int color) {
            return (Color.red(color) * .2126f + Color.green(color) * .7152f + Color.blue(color) * .0722f) / 255;
        }

        void clear() {
            if (back != null && !back.isRecycled()) back.recycle();
            if (front != null && !front.isRecycled()) front.recycle();
            back = front = null;
            rowBack = rowFront = rowOut = rowScratch = new int[0];
            java.util.Arrays.fill(alphaRing, null);
            java.util.Arrays.fill(premulRing, null);
            java.util.Arrays.fill(ringTag, Integer.MIN_VALUE);
            results.evictAll();
        }
    }
}
