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
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private Bitmap back, front;
        private int[] pixels = new int[0], masks = new int[0], output = new int[0];
        private float[] premultiplied = new float[0], alphas = new float[0];
        private final android.util.LruCache<String, Bitmap> results = new android.util.LruCache<>(2);

        void draw(Canvas target, String instance, int ink, int paper, Layer artwork, Layer mask) {
            Rect bounds = target.getClipBounds(); bounds.inset(-3, -3);
            int width = Math.max(1, bounds.width()), height = Math.max(1, bounds.height()), size = width * height;
            if (back == null || back.getWidth() < width || back.getHeight() < height) {
                int reservedWidth = (width + 63) / 64 * 64, reservedHeight = (height + 63) / 64 * 64;
                back = Bitmap.createBitmap(reservedWidth, reservedHeight, Bitmap.Config.ARGB_8888);
                front = Bitmap.createBitmap(reservedWidth, reservedHeight, Bitmap.Config.ARGB_8888);
                int capacity = reservedWidth * reservedHeight;
                pixels = new int[capacity]; masks = new int[capacity]; output = new int[capacity];
                premultiplied = new float[capacity]; alphas = new float[capacity];
            }
            back.eraseColor(Color.TRANSPARENT); front.eraseColor(Color.TRANSPARENT);
            Canvas artCanvas = new Canvas(back), maskCanvas = new Canvas(front);
            artCanvas.translate(-bounds.left, -bounds.top); maskCanvas.translate(-bounds.left, -bounds.top);
            artwork.draw(artCanvas); mask.draw(maskCanvas);
            back.getPixels(pixels, 0, width, 0, 0, width, height); front.getPixels(masks, 0, width, 0, 0, width, height);
            float paperLum = luminance(paper), inkLum = luminance(ink);
            for (int i = 0; i < size; i++) { alphas[i] = Color.alpha(pixels[i]) / 255f; premultiplied[i] = luminance(pixels[i]) * alphas[i]; }
            for (int y = 0, i = 0; y < height; y++) for (int x = 0; x < width; x++, i++) {
                float frontAlpha = Color.alpha(masks[i]) / 255f;
                if (frontAlpha == 0) { output[i] = pixels[i]; continue; }
                float lum = sample(x, y, width, height, paperLum) * .4f
                        + (sample(x + 1.5f, y + 1.5f, width, height, paperLum) + sample(x - 1.5f, y - 1.5f, width, height, paperLum)
                        + sample(x + 1.5f, y - 1.5f, width, height, paperLum) + sample(x - 1.5f, y + 1.5f, width, height, paperLum)) * .15f;
                int tone = Math.abs(lum - paperLum) >= Math.abs(lum - inkLum) ? paper : ink;
                float alpha = frontAlpha + alphas[i] * (1 - frontAlpha);
                float backWeight = alphas[i] * (1 - frontAlpha);
                output[i] = Color.argb(Math.round(alpha * 255),
                        Math.round((Color.red(tone) * frontAlpha + Color.red(pixels[i]) * backWeight) / alpha),
                        Math.round((Color.green(tone) * frontAlpha + Color.green(pixels[i]) * backWeight) / alpha),
                        Math.round((Color.blue(tone) * frontAlpha + Color.blue(pixels[i]) * backWeight) / alpha));
            }
            Bitmap result = results.get(instance);
            if (result == null || result.getWidth() < width || result.getHeight() < height) {
                result = Bitmap.createBitmap((width + 63) / 64 * 64, (height + 63) / 64 * 64, Bitmap.Config.ARGB_8888); results.put(instance, result);
            }
            result.setPixels(output, 0, width, 0, 0, width, height);
            target.drawBitmap(result, new Rect(0, 0, width, height), new android.graphics.RectF(bounds), paint);
        }

        private float sample(float x, float y, int width, int height, float paperLum) {
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y); float dx = x - x0, dy = y - y0;
            float alpha = 0, luminance = 0;
            for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) {
                int xx = x0 + col, yy = y0 + row;
                if (xx < 0 || xx >= width || yy < 0 || yy >= height) continue;
                int index = yy * width + xx; float weight = (col == 0 ? 1 - dx : dx) * (row == 0 ? 1 - dy : dy);
                alpha += alphas[index] * weight; luminance += premultiplied[index] * weight;
            }
            float straight = luminance / Math.max(alpha, .0001f), weight = Math.min(1, alpha * 3);
            return paperLum * (1 - weight) + straight * weight;
        }
        private static float luminance(int color) { return (Color.red(color) * .2126f + Color.green(color) * .7152f + Color.blue(color) * .0722f) / 255; }
        void clear() { back = front = null; pixels = masks = output = new int[0]; premultiplied = alphas = new float[0]; results.evictAll(); }
    }
}
