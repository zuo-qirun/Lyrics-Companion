package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.util.LruCache;

/** GPU glyph blur/glow on API 31+, with the existing bounded mask cache as the older fallback. */
final class FoliaGpuText {
    private Object state;
    private boolean unavailable;

    boolean draw(Canvas canvas, String instance, String glyph, Typeface face, float size, float baseline,
                 int color, float opacity, float blur) {
        if (Build.VERSION.SDK_INT < 31 || !canvas.isHardwareAccelerated() || unavailable) return false;
        try {
            if (state == null) state = new Api31();
            ((Api31) state).draw(canvas, instance, glyph, face, size, baseline, color, opacity, blur);
            return true;
        } catch (RuntimeException | LinkageError error) {
            // Some vendor renderers advertise hardware Canvas without RenderEffect support.
            unavailable = true;
            state = null;
            return false;
        }
    }

    void clear() { state = null; }

    @androidx.annotation.RequiresApi(31)
    private static final class Api31 {
        private static final int PADDING = 128;
        private final LruCache<String, Glyph> glyphs = new LruCache<>(96);
        private final LruCache<String, android.graphics.RenderEffect> effects = new LruCache<>(96);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);

        void draw(Canvas canvas, String instance, String glyph, Typeface face, float size, float baseline,
                  int color, float opacity, float blur) {
            // A RenderNode's properties are shared by every reference in the display list.
            // Give each glyph/layer its own node so later draws cannot recolor earlier ones.
            String key = instance + ':' + face.hashCode() + ":" + size + ':' + glyph;
            Glyph cached = glyphs.get(key);
            if (cached == null) {
                paint.setTypeface(face); paint.setTextSize(size); paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(Color.WHITE);
                Paint.FontMetrics metrics = paint.getFontMetrics();
                float width = paint.measureText(glyph);
                android.graphics.RenderNode node = new android.graphics.RenderNode("Folia glyph");
                int nodeWidth = Math.max(1, (int) Math.ceil(width) + 2 * PADDING);
                int nodeHeight = Math.max(1, (int) Math.ceil(metrics.descent - metrics.ascent) + 2 * PADDING);
                node.setPosition(0, 0, nodeWidth, nodeHeight);
                node.setClipToBounds(false);
                Canvas recording = node.beginRecording(nodeWidth, nodeHeight);
                recording.drawText(glyph, PADDING + width / 2, PADDING - metrics.ascent, paint);
                node.endRecording();
                cached = new Glyph(node, PADDING + width / 2, PADDING - metrics.ascent);
                glyphs.put(key, cached);
            }
            float radius = Math.max(0.0001f, blur);
            String effectKey = color + ":" + radius;
            android.graphics.RenderEffect effect = effects.get(effectKey);
            if (effect == null) {
                android.graphics.RenderEffect tint = android.graphics.RenderEffect.createColorFilterEffect(
                        new android.graphics.PorterDuffColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN));
                effect = android.graphics.RenderEffect.createBlurEffect(radius, radius, tint,
                        android.graphics.Shader.TileMode.DECAL);
                effects.put(effectKey, effect);
            }
            cached.node.setRenderEffect(effect);
            cached.node.setAlpha(Math.max(0, Math.min(1, opacity)));
            int save = canvas.save();
            canvas.translate(-cached.originX, baseline - cached.originY);
            canvas.drawRenderNode(cached.node);
            canvas.restoreToCount(save);
        }

        private static final class Glyph {
            final android.graphics.RenderNode node;
            final float originX, originY;
            Glyph(android.graphics.RenderNode node, float originX, float originY) {
                this.node = node; this.originX = originX; this.originY = originY;
            }
        }
    }
}
