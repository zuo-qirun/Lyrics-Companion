// SPDX-License-Identifier: AGPL-3.0-only
// Android Canvas adaptations of Folia. Source attribution: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.LruCache;

import java.util.Collections;
import java.util.List;

/** Native lyric scenes with bounded caches for text layouts and software blur masks. */
final class FoliaCanvasRenderer {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint raster = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final RectF rect = new RectF();
    private final Rect source = new Rect();
    private final Path path = new Path();
    private final FoliaRingMath.Pose pose = new FoliaRingMath.Pose();
    private final LruCache<Integer, PorterDuffColorFilter> colors = new LruCache<>(32);
    private final LruCache<String, PreparedLine> lines = new LruCache<>(24);
    private final LruCache<String, Bitmap> masks = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private List<LrcTimeline.Line> sourceLines = Collections.emptyList();
    private String track = "";
    private String mode = "claddagh";
    private Typeface face;
    private int anchor = -1;
    private double ringOffset, ringFrom;
    private long transitionAge, lastClock = -1, lastPosition = -1;
    private float width, height, fontScale = 1, strength = 0.5f;
    private float radiusScale = 1, focusRatio = 0.65f, tilt = 45, tracking;
    private int primary = Color.WHITE, accent = 0xFFFF984B, muted = 0xFFB9BBC5;
    private float bass;
    private boolean axis = true, animate = true, showTranslation = true, audioReactive;
    private long time;

    void configure(String mode, Typeface face, float fontScale, int strength, int fps,
                   float radiusScale, float focusScale, float tilt, boolean axis, float tracking,
                   boolean showTranslation, boolean audioReactive) {
        String normalized = FoliaModeCatalog.normalize(mode);
        Typeface resolvedFace = face == null ? Typeface.create("sans-serif", Typeface.BOLD) : face;
        if (!normalized.equals(this.mode) || !resolvedFace.equals(this.face) || this.fontScale != fontScale
                || this.radiusScale != radiusScale || this.focusRatio != focusScale - 1
                || this.tracking != tracking) {
            lines.evictAll();
            masks.evictAll();
        }
        if (!normalized.equals(this.mode)) resetMotion();
        this.mode = normalized;
        this.face = resolvedFace;
        this.fontScale = fontScale;
        this.strength = Math.max(0, Math.min(1, strength / 100f));
        this.radiusScale = radiusScale;
        this.focusRatio = focusScale - 1;
        this.tilt = tilt;
        this.axis = axis;
        this.tracking = tracking;
        this.animate = fps > 0 && FoliaModeCatalog.animated(normalized);
        this.showTranslation = showTranslation;
        this.audioReactive = audioReactive;
    }

    boolean transitioning() { return animate && transitionAge < 1400; }

    void clear() {
        lines.evictAll();
        masks.evictAll();
        colors.evictAll();
        sourceLines = Collections.emptyList();
        track = "";
        resetMotion();
    }

    private void resetMotion() {
        anchor = -1;
        lastClock = -1;
        lastPosition = -1;
        transitionAge = 1400;
        ringOffset = ringFrom = 0;
    }

    void draw(Canvas canvas, MusicSnapshot snapshot, long lyricTime, long clock,
              float density, int primary, int accent, int muted) {
        int save = canvas.save();
        canvas.clipRect(0, 0, canvas.getWidth(), canvas.getHeight());
        canvas.scale(density, density);
        width = canvas.getWidth() / density;
        height = canvas.getHeight() / density;
        this.primary = primary;
        this.accent = accent;
        this.muted = muted;
        time = lyricTime;
        List<LrcTimeline.Line> incoming = snapshot.timelineLines;
        boolean newTrack = !track.equals(snapshot.sourceName + '\n' + snapshot.title + '\n' + snapshot.artist);
        if (newTrack || incoming != sourceLines) {
            sourceLines = incoming;
            track = snapshot.sourceName + '\n' + snapshot.title + '\n' + snapshot.artist;
            lines.evictAll();
            resetMotion();
        }
        int current = activeIndex(sourceLines, time);
        boolean seek = lastPosition >= 0 && (time < lastPosition - 500 || time > lastPosition + 2500);
        long dt = lastClock < 0 || !snapshot.playing || !animate ? 0 : Math.max(0, Math.min(100, clock - lastClock));
        transitionAge += dt;
        if (current != anchor) {
            ringFrom = ringOffset;
            if (anchor < 0 || Math.abs(current - anchor) > 1 || seek || !snapshot.playing || !animate) {
                ringOffset = current * Math.PI;
                transitionAge = 1400;
            } else {
                transitionAge = 0;
            }
            anchor = current;
        }
        if (seek) {
            ringOffset = current * Math.PI;
            transitionAge = 1400;
        }
        ringOffset = transitionAge >= 1400 ? anchor * Math.PI
                : ringFrom + (anchor * Math.PI - ringFrom) * FoliaRingMath.spring(transitionAge / 1000.0);
        lastClock = clock;
        lastPosition = time;
        AudioSpectrumSource.Frame audio = AudioSpectrumSource.latestFrame();
        float targetBass = 0;
        if (audioReactive && snapshot.playing && audio.live && audio.levels.length > 0) {
            for (int i = 0; i < Math.min(6, audio.levels.length); i++) targetBass += audio.levels[i];
            targetBass /= Math.min(6, audio.levels.length);
        }
        if (snapshot.playing) bass += (targetBass - bass) * 0.18f;
        if (sourceLines.isEmpty()) {
            label(canvas, snapshot.lyrics.lyric.isEmpty() ? snapshot.active ? "正在匹配歌词" : "等待播放"
                    : snapshot.lyrics.lyric, width / 2, height / 2,
                    Math.min(40 * fontScale, width / 12), primary, Paint.Align.CENTER);
        } else if ("claddagh".equals(mode)) {
            ring(canvas);
        } else if ("pendolo".equals(mode)) {
            clockwork(canvas);
        } else if ("cappella".equals(mode)) {
            conversation(canvas, snapshot.albumArt);
        } else if ("monet".equals(mode)) {
            portrait(canvas, snapshot);
        } else if ("fume".equals(mode)) {
            article(canvas);
        } else if ("diorama".equals(mode)) {
            perspective(canvas);
        } else if ("sonnet".equals(mode) || "tempera".equals(mode)) {
            poster(canvas, "tempera".equals(mode));
        } else if ("lumiere".equals(mode)) {
            lightField(canvas);
        } else if ("still".equals(mode)) {
            staticLine(canvas);
        } else {
            typography(canvas);
        }
        if (!"monet".equals(mode) && !"cappella".equals(mode)) subtitle(canvas);
        shape.setShader(null);
        shape.setColorFilter(null);
        shape.setStyle(Paint.Style.FILL);
        canvas.restoreToCount(save);
    }

    static int activeIndex(List<LrcTimeline.Line> lines, long time) {
        int low = 0, high = lines.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= time) low = mid + 1; else high = mid;
        }
        return low - 1;
    }

    private PreparedLine prepare(int index, float size, float radius) {
        LrcTimeline.Line line = sourceLines.get(index);
        String key = index + ":" + size + ":" + radius + ":" + width;
        PreparedLine cached = lines.get(key);
        if (cached != null) return cached;
        text.setTypeface(face);
        text.setTextSize(size);
        text.getFontMetrics(metrics);
        FoliaGlyphTimeline timeline = new FoliaGlyphTimeline(line,
                index + 1 < sourceLines.size() ? sourceLines.get(index + 1).timeMs : -1);
        PreparedLine result = new PreparedLine(timeline, size, -(metrics.ascent + metrics.descent) / 2);
        double spacingScale = (1 + focusRatio) / 1.65;
        for (int i = 0; i < timeline.glyphs.length; i++) {
            result.widths[i] = text.measureText(timeline.glyphs[i]);
            result.offsets[i + 1] = Math.max(result.offsets[i], text.measureText(line.text, 0,
                    timeline.offsets[i + 1]) + i * (size * 0.22f + tracking));
        }
        result.total = result.offsets[timeline.glyphs.length];
        double span = result.total * spacingScale / Math.max(radius, size * 2);
        result.arcScale = span > FoliaRingMath.MAX_ARC ? FoliaRingMath.MAX_ARC / span : 1;
        for (int i = 0; i < timeline.glyphs.length; i++) {
            double center = (result.offsets[i] + result.offsets[i + 1]) * 0.5 * spacingScale;
            result.angles[i] = (center / Math.max(radius, size * 2) - span / 2) * result.arcScale;
        }
        lines.put(key, result);
        return result;
    }

    private void ring(Canvas canvas) {
        float size = 72 * fontScale;
        float radius = (float) FoliaRingMath.radius(width, radiusScale);
        float powerScale = animate ? Math.min(1 + bass * strength * 0.5f, 1.25f) : 1;
        if (axis) {
            int save = canvas.save();
            canvas.translate(width / 2, height / 2);
            canvas.rotate(90 - tilt);
            canvas.scale(1 + bass * bass * 1.5f, 1 + bass * bass * 0.5f);
            shape.setShader(new LinearGradient(-150, 0, 150, 0,
                    new int[]{Color.TRANSPARENT, alpha(accent, 0.2f + bass * 0.75f),
                            alpha(accent, 0.2f + bass * 0.75f), Color.TRANSPARENT},
                    new float[]{0, 0.2f, 0.8f, 1}, Shader.TileMode.CLAMP));
            canvas.drawRect(-150, -2, 150, 2, shape);
            shape.setShader(null);
            canvas.restoreToCount(save);
        }
        PreparedLine active = anchor >= 0 ? prepare(anchor, size, radius) : null;
        double activeOffset = active == null || !animate ? 0 : FoliaRingMath.wordOffset(active.angles,
                active.timeline.starts, active.timeline.ends, time, active.timeline.renderEnd);
        double progress = active == null || !animate ? 0 : FoliaRingMath.clamp(FoliaRingMath.fractionalIndex(
                active.timeline.starts, active.timeline.ends, time, active.timeline.renderEnd)
                / Math.max(1, active.angles.length - 1), 0, 1);
        double follow = Math.PI * 0.52 * (1 - Math.pow(1 - progress, 1.35));
        for (int index = Math.max(0, anchor - 1); index <= Math.min(sourceLines.size() - 1, anchor + 2); index++) {
            PreparedLine line = prepare(index, size, radius);
            double distance = Math.abs(ringOffset - index * Math.PI) / Math.PI;
            double offset = !animate ? 0 : FoliaRingMath.wordOffset(line.angles, line.timeline.starts,
                    line.timeline.ends, time, line.timeline.renderEnd);
            if (index >= anchor) offset += (activeOffset * 0.28 + follow)
                    * (index > anchor ? 1 : FoliaRingMath.clamp(distance, 0, 1));
            double entry = FoliaRingMath.clamp((time - (sourceLines.get(index).timeMs - 340)) / 340.0, 0, 1);
            double lengthFade = 1, lengthScale = 1;
            if (index > anchor && active != null) {
                double currentLength = visualLength(sourceLines.get(anchor).text);
                double nextLength = visualLength(sourceLines.get(index).text);
                if (currentLength > 10) {
                    double overlap = FoliaRingMath.clamp((currentLength - 10) / 8, 0, 1)
                            * FoliaRingMath.clamp((nextLength - 5) / 5, 0.4, 1);
                    double fade = FoliaRingMath.clamp((distance - 0.4) / 0.5, 0, 1);
                    lengthFade = 1 - overlap * fade;
                    lengthScale = 1 - overlap * 0.25 * fade;
                }
            }
            for (int glyph = 0; glyph < line.angles.length; glyph++) {
                double psi = index * Math.PI + line.angles[glyph] - ringOffset - offset;
                FoliaRingMath.project(pose, psi, radius * powerScale, tilt, distance, focusRatio,
                        line.arcScale, index < anchor, index > anchor, entry, lengthFade, lengthScale);
                if (pose.opacity < 0.005) continue;
                float sung = (float) line.timeline.progress(glyph, time);
                float highlight = Math.min(1, sung * 15);
                int color = mix(primary, accent, highlight);
                float opacity = (float) pose.opacity * (0.55f + 0.45f * highlight);
                float flash = sung > 0 && sung < 0.4f ? (float) Math.pow(1 - sung / 0.4, 2) * 0.65f : 0;
                float glow = (float) (24 * Math.pow(pose.focus, 2) * (1 + flash));
                int save = canvas.save();
                canvas.translate(width / 2 + (float) pose.x, height / 2 + (float) pose.y);
                canvas.rotate((float) pose.rotation);
                canvas.scale((float) pose.scale, (float) pose.scale);
                glyph(canvas, line, glyph, color, opacity, (float) pose.blur, glow);
                canvas.restoreToCount(save);
            }
        }
    }

    private static double visualLength(String text) {
        double length = 0;
        for (int i = 0; i < text.length(); i++) {
            char code = text.charAt(i);
            length += code >= 0x4e00 && code <= 0x9fa5 || code >= 0x3040 && code <= 0x30ff
                    || code >= 0xac00 && code <= 0xd7af ? 1 : 0.5;
        }
        return length;
    }

    private void glyph(Canvas canvas, PreparedLine line, int index, int color,
                       float opacity, float blur, float glow) {
        if (opacity <= 0 || line.timeline.glyphs[index].trim().isEmpty()) return;
        text.setTypeface(face);
        text.setTextSize(line.size);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(alpha(color, opacity));
        if (glow > 0.5f) {
            drawMask(canvas, line, index, color, opacity, Math.round(glow * 0.5f));
        }
        if (blur > 0.2f) {
            drawMask(canvas, line, index, color, opacity, Math.max(1, Math.round(blur)));
        } else {
            canvas.drawText(line.timeline.glyphs[index], 0, line.baseline, text);
        }
    }

    private void drawMask(Canvas canvas, PreparedLine line, int index, int color, float opacity, int blur) {
        // Quantize the changing focus blur to avoid allocating a bitmap for every frame radius.
        int radius = blur <= 2 ? 2 : blur <= 4 ? 4 : blur <= 8 ? 8 : blur <= 16 ? 16 : 32;
        String key = line.timeline.glyphs[index] + ':' + line.size + ':' + radius;
        Bitmap mask = masks.get(key);
        if (mask == null) {
            text.getFontMetrics(metrics);
            int pad = radius * 3 + 3;
            int w = Math.max(1, (int) Math.ceil(line.widths[index]) + pad * 2);
            int h = Math.max(1, (int) Math.ceil(metrics.bottom - metrics.top) + pad * 2);
            mask = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas offscreen = new Canvas(mask);
            raster.set(text);
            raster.setColor(Color.WHITE);
            raster.setTextAlign(Paint.Align.CENTER);
            raster.setMaskFilter(radius == 0 ? null : new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL));
            offscreen.drawText(line.timeline.glyphs[index], w / 2f, pad - metrics.top, raster);
            raster.setMaskFilter(null);
            masks.put(key, mask);
        }
        PorterDuffColorFilter filter = colors.get(color);
        if (filter == null) {
            filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            colors.put(color, filter);
        }
        shape.setColorFilter(filter);
        shape.setAlpha(Math.round(255 * Math.max(0, Math.min(1, opacity))));
        text.getFontMetrics(metrics);
        float top = line.baseline + metrics.top - (radius * 3 + 3);
        canvas.drawBitmap(mask, -mask.getWidth() / 2f, top, shape);
        shape.setColorFilter(null);
        shape.setAlpha(255);
    }

    private void label(Canvas canvas, String value, float x, float y, float size, int color, Paint.Align align) {
        text.setTypeface(face);
        text.setTextSize(Math.max(1, size));
        text.setTextAlign(align);
        text.setColor(color);
        text.setShader(null);
        text.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
        canvas.drawText(value, x, y, text);
    }

    private PreparedLine current(float size) {
        return prepare(Math.max(0, Math.min(sourceLines.size() - 1, anchor)), size, width);
    }

    private void row(Canvas canvas, int index, float x, float y, float size, float maxWidth,
                     float opacity, boolean reveal, float rotation) {
        PreparedLine line = prepare(index, size, width);
        float scale = Math.min(1, maxWidth / Math.max(1, line.total));
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        canvas.scale(scale, scale);
        for (int i = 0; i < line.timeline.glyphs.length; i++) {
            float progress = (float) line.timeline.progress(i, time);
            float entry = reveal ? (float) FoliaRingMath.clamp((time - line.timeline.starts[i] + 180) / 360.0, 0, 1) : 1;
            int glyphSave = canvas.save();
            canvas.translate(line.offsets[i] + line.widths[i] / 2 - line.total / 2,
                    animate ? (1 - entry) * 18 : 0);
            glyph(canvas, line, i, mix(primary, accent, Math.min(1, progress * 15)),
                    opacity * (0.22f + 0.78f * entry), 0, progress > 0 && progress < 1 ? 8 * strength : 0);
            canvas.restoreToCount(glyphSave);
        }
        canvas.restoreToCount(save);
    }

    private void subtitle(Canvas canvas) {
        if (anchor < 0 || anchor >= sourceLines.size()) return;
        LrcTimeline.Line active = sourceLines.get(anchor);
        String value = !active.translated.isEmpty() ? active.translated : active.romaji;
        if (showTranslation && !value.isEmpty()) {
            float size = Math.max(14, Math.min(23.2f, width * 0.022f)) * fontScale;
            text.setTypeface(face);
            text.setTextSize(size);
            size *= Math.min(1, width * 0.84f / Math.max(1, text.measureText(value)));
            label(canvas, value, width / 2, height - 42, size, alpha(muted, 0.8f), Paint.Align.CENTER);
        }
        if (anchor + 1 < sourceLines.size()) row(canvas, anchor + 1, width / 2, height - 18,
                Math.max(12, Math.min(19, width * 0.018f)) * fontScale, width * 0.82f, 0.35f, false, 0);
    }

    private void staticLine(Canvas canvas) {
        int index = Math.max(0, anchor);
        row(canvas, index, width / 2, height / 2, Math.max(32, Math.min(72, width * 0.06f))
                * fontScale, width * 0.88f, 1, false, 0);
    }

    private void typography(Canvas canvas) {
        int index = Math.max(0, anchor);
        boolean cloud = "partita".equals(mode), tilted = "tilt".equals(mode), mind = "cadenza".equals(mode);
        float size = Math.max(32, Math.min(72, width * (mind ? 0.062f : 0.055f))) * fontScale;
        PreparedLine line = current(size);
        int count = line.timeline.glyphs.length;
        int rows = cloud ? Math.max(1, Math.min(5, (int) (height * 0.65f / 100)))
                : tilted ? Math.max(1, Math.min(4, (int) Math.ceil(Math.log(count + 4) / Math.log(24) * 1.5)))
                : Math.max(1, (int) Math.ceil(line.total / Math.max(1, width * 0.78f)));
        int perRow = Math.max(1, (int) Math.ceil(count / (double) rows));
        float lineHeight = size * 1.4f;
        float firstY = height * 0.48f - (rows - 1) * lineHeight / 2;
        for (int r = 0; r < rows; r++) {
            int first = r * perRow, end = Math.min(count, first + perRow);
            if (first >= count) break;
            float rowWidth = line.offsets[end] - line.offsets[first];
            float fit = Math.min(1, width * 0.75f / Math.max(1, rowWidth));
            float stagger = cloud ? (r % 2 == 0 ? -1 : 1) * (20 + random(sourceLines.get(index).timeMs * 0.001 + r) * 80) : 0;
            int save = canvas.save();
            canvas.translate(width / 2 + stagger, firstY + r * lineHeight);
            canvas.scale(fit, fit);
            if (tilted && r % 2 == 1) canvas.rotate(-8);
            if (cloud) {
                shape.setColor(alpha(accent, 0.1f));
                canvas.drawRect(-rowWidth / 2 - 30, size * 0.58f, rowWidth / 2 + 30, size * 0.58f + 1, shape);
            }
            for (int g = first; g < end; g++) {
                float progress = (float) line.timeline.progress(g, time);
                float entry = (float) FoliaRingMath.clamp((time - line.timeline.starts[g] + 180) / 360.0, 0, 1);
                float energy = animate ? (float) Math.sin(time / 1000.0 * 1.4 + g * 0.7) * strength : 0;
                float x = line.offsets[g] - line.offsets[first] + line.widths[g] / 2 - rowWidth / 2;
                float y = tilted ? (g % 2 == 0 ? -1 : 1) * size * 0.1f : mind ? energy * 10 : energy * 4;
                int glyphSave = canvas.save();
                canvas.translate(x, y + (animate ? (1 - entry) * 24 : 0));
                if (!cloud && !mind && !tilted && animate) canvas.rotate(energy * 4);
                if (mind) canvas.scale(0.94f + 0.06f * entry + bass * 0.08f, 0.94f + 0.06f * entry);
                glyph(canvas, line, g, mix(primary, accent, Math.min(1, progress * 15)),
                        0.22f + entry * 0.78f, 0, progress > 0 && progress < 1 ? 18 * strength : 0);
                canvas.restoreToCount(glyphSave);
            }
            canvas.restoreToCount(save);
        }
    }

    private void article(Canvas canvas) {
        float size = Math.max(18, Math.min(44, width * 0.045f)) * fontScale;
        float step = size * 1.85f;
        int center = Math.max(0, anchor);
        float camera = (float) (ringOffset / Math.PI);
        for (int i = Math.max(0, center - 4); i < Math.min(sourceLines.size(), center + 5); i++) {
            float y = height / 2 + (i - camera) * step;
            if (y < 24 || y > height - 60) continue;
            float opacity = i == center ? 1 : i < center ? 0.32f : 0.16f;
            row(canvas, i, width * 0.47f, y, size, width * 0.78f, opacity, i == center, 0);
            label(canvas, String.valueOf(i + 1), width * 0.05f, y, 10,
                    alpha(accent, 0.35f), Paint.Align.LEFT);
        }
        shape.setColor(alpha(accent, 0.1f));
        canvas.drawRect(width * 0.08f, height * 0.12f, width * 0.08f + 1, height * 0.83f, shape);
    }

    private void clockwork(Canvas canvas) {
        float radius = Math.min(width, height) * 0.42f;
        float cx = 0, cy = height * 0.5f;
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeWidth(1);
        shape.setColor(alpha(accent, 0.2f));
        for (int ring = 0; ring < 3; ring++) canvas.drawCircle(cx, cy, radius * (0.82f + ring * 0.08f), shape);
        double spin = animate ? time / 1000.0 * Math.PI * 2 / 60 : 0;
        for (int tick = 0; tick < 60; tick++) {
            double angle = tick * Math.PI * 2 / 60 + spin;
            float inner = radius * (tick % 5 == 0 ? 0.84f : 0.9f);
            canvas.drawLine(cx + (float) Math.cos(angle) * inner, cy + (float) Math.sin(angle) * inner,
                    cx + (float) Math.cos(angle) * radius * 0.96f,
                    cy + (float) Math.sin(angle) * radius * 0.96f, shape);
        }
        shape.setStyle(Paint.Style.FILL);
        float camera = (float) (ringOffset / Math.PI);
        for (int i = Math.max(0, anchor - 8); i < Math.min(sourceLines.size(), anchor + 9); i++) {
            double angle = (i - camera) * 100 / 8 * Math.PI / 180;
            if (Math.abs(angle) >= Math.PI / 2) continue;
            float x = cx + radius * (float) Math.cos(angle);
            float y = cy + radius * (float) Math.sin(angle);
            float distance = Math.abs(i - camera);
            float opacity = Math.max(0.12f, (float) Math.pow(Math.cos(angle * 0.75), 2.5) * (1 - distance * 0.18f));
            float size = Math.max(18, Math.min(36, width * 0.038f)) * fontScale * (i == anchor ? 1.25f : Math.max(0.7f, 1 - distance * 0.08f));
            PreparedLine line = prepare(i, size, width);
            float fitWidth = Math.max(1, width - x - 24);
            row(canvas, i, x + Math.min(fitWidth, line.total) / 2 + 14, y, size,
                    fitWidth, opacity, false, (float) (angle * 180 / Math.PI));
        }
    }

    private void conversation(Canvas canvas, Bitmap cover) {
        int center = Math.max(0, anchor);
        float size = Math.max(18, Math.min(32, width * 0.035f)) * fontScale;
        float bubbleHeight = size * 2.2f;
        float camera = (float) (ringOffset / Math.PI);
        for (int i = Math.max(0, center - 3); i <= center; i++) {
            PreparedLine line = prepare(i, size, width);
            boolean right = i % 2 == 1;
            float fitWidth = Math.min(width * 0.69f, line.total);
            float bubbleWidth = fitWidth + 32;
            float y = height * 0.61f + (i - camera) * (bubbleHeight + 18);
            float x = right ? width - bubbleWidth - 68 : 68;
            float entry = i == center && animate ? (float) FoliaRingMath.spring(Math.min(1.4, transitionAge / 1000.0)) : 1;
            float opacity = i == center ? 1 : 0.5f;
            shape.setColor(alpha(right ? mix(accent, Color.BLACK, 0.7f) : mix(primary, Color.BLACK, 0.83f), opacity));
            rect.set(x, y - bubbleHeight / 2, x + bubbleWidth, y + bubbleHeight / 2);
            int save = canvas.save();
            canvas.scale(entry, entry, right ? x + bubbleWidth : x, y);
            canvas.drawRoundRect(rect, 14, 14, shape);
            row(canvas, i, x + bubbleWidth / 2, y, size, fitWidth, opacity, i == center, 0);
            canvas.restoreToCount(save);
            float avatarX = right ? width - 40 : 40;
            if (cover != null && !cover.isRecycled()) {
                path.reset();
                path.addCircle(avatarX, y, 20, Path.Direction.CW);
                int clip = canvas.save();
                canvas.clipPath(path);
                rect.set(avatarX - 20, y - 20, avatarX + 20, y + 20);
                source.set(0, 0, cover.getWidth(), cover.getHeight());
                shape.setColor(Color.WHITE);
                canvas.drawBitmap(cover, source, rect, shape);
                canvas.restoreToCount(clip);
            } else {
                shape.setColor(alpha(accent, opacity * 0.5f));
                canvas.drawCircle(avatarX, y, 20, shape);
                label(canvas, right ? "♪" : "♫", avatarX, y + 7, 22, primary, Paint.Align.CENTER);
            }
        }
    }

    private void portrait(Canvas canvas, MusicSnapshot snapshot) {
        float artSize = Math.min(height * 0.65f, width * 0.36f);
        float left = width - artSize - width * 0.08f;
        float top = (height - artSize) / 2;
        if (snapshot.albumArt != null && !snapshot.albumArt.isRecycled()) {
            source.set(0, 0, snapshot.albumArt.getWidth(), snapshot.albumArt.getHeight());
            rect.set(left, top, left + artSize, top + artSize);
            shape.setColor(Color.WHITE);
            canvas.drawBitmap(snapshot.albumArt, source, rect, shape);
        }
        float lyricWidth = Math.max(1, left - width * 0.12f);
        float x = width * 0.06f + lyricWidth / 2;
        label(canvas, snapshot.artist, width * 0.06f, height * 0.22f, Math.max(14, width * 0.018f), muted, Paint.Align.LEFT);
        text.setTypeface(face);
        text.setTextSize(Math.max(20, width * 0.033f));
        float titleSize = text.getTextSize() * Math.min(1, lyricWidth / Math.max(1, text.measureText(snapshot.title)));
        label(canvas, snapshot.title, width * 0.06f, height * 0.32f, titleSize, primary, Paint.Align.LEFT);
        int center = Math.max(0, anchor);
        for (int i = Math.max(0, center - 1); i < Math.min(sourceLines.size(), center + 3); i++) {
            row(canvas, i, x, height * 0.53f + (i - (float) (ringOffset / Math.PI)) * 48,
                    Math.max(22, Math.min(48, width * 0.045f)) * fontScale,
                    lyricWidth, i == center ? 1 : 0.35f, false, 0);
        }
    }

    private void perspective(Canvas canvas) {
        float camera = (float) (ringOffset / Math.PI);
        for (int i = Math.min(sourceLines.size() - 1, anchor + 4); i >= Math.max(0, anchor - 1); i--) {
            float z = i - camera;
            float scale = 1 / Math.max(0.6f, 1 + Math.abs(z) * 0.65f);
            float x = width / 2 + (float) Math.sin(i * 2.3) * width * 0.14f * (1 - scale);
            float y = height * 0.48f - z * height * 0.1f;
            int save = canvas.save();
            canvas.scale(scale, scale, x, y);
            row(canvas, i, x, y, 64 * fontScale, width * 0.8f,
                    i == anchor ? 1 : 0.24f * scale, i == anchor, z * -3);
            canvas.restoreToCount(save);
        }
        motes(canvas, 64, false);
    }

    private void poster(Canvas canvas, boolean tempera) {
        int index = Math.max(0, anchor);
        PreparedLine line = current(Math.min(96, width * 0.1f) * fontScale);
        float margin = Math.min(width, height) * 0.08f;
        shape.setColor(alpha(accent, tempera ? 0.3f : 0.1f));
        float block = width * (0.22f + random(sourceLines.get(index).timeMs) * 0.24f);
        rect.set(width - margin - block, margin, width - margin, height - margin);
        canvas.drawRect(rect, shape);
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeWidth(1);
        shape.setColor(alpha(primary, 0.25f));
        rect.set(margin, margin, width - margin, height - margin);
        canvas.drawRect(rect, shape);
        shape.setStyle(Paint.Style.FILL);
        label(canvas, "LYRICS / " + String.format(java.util.Locale.ROOT, "%03d", index + 1),
                margin + 10, margin + 20, 10, alpha(primary, 0.6f), Paint.Align.LEFT);
        int save = canvas.save();
        canvas.translate(width / 2, height * 0.5f);
        canvas.rotate(tempera ? -6 : (index % 2 == 0 ? -3 : 3));
        float fit = Math.min(1, width * 0.82f / Math.max(1, line.total));
        canvas.scale(fit, fit);
        for (int g = 0; g < line.timeline.glyphs.length; g++) {
            float progress = (float) line.timeline.progress(g, time);
            float entry = (float) FoliaRingMath.clamp((time - line.timeline.starts[g] + 120) / 240.0, 0, 1);
            int glyphSave = canvas.save();
            canvas.translate(line.offsets[g] + line.widths[g] / 2 - line.total / 2,
                    animate ? (1 - entry) * 50 * (g % 2 == 0 ? 1 : -1) : 0);
            if (tempera && animate) canvas.scale(1 + (1 - entry) * 0.5f, entry * 0.5f + 0.5f);
            glyph(canvas, line, g, tempera && g % 2 == 0 ? accent : mix(primary, accent, progress),
                    0.3f + 0.7f * entry, 0, 0);
            canvas.restoreToCount(glyphSave);
        }
        canvas.restoreToCount(save);
        label(canvas, "+", width - margin, height - margin, 26, primary, Paint.Align.CENTER);
    }

    private void lightField(Canvas canvas) {
        int center = Math.max(0, anchor);
        float size = Math.max(32, Math.min(64, width * 0.06f)) * fontScale;
        PreparedLine line = current(size);
        float fit = Math.min(1, width * 0.8f / Math.max(1, line.total));
        int save = canvas.save();
        canvas.translate(width / 2, height * 0.47f);
        canvas.scale(fit, fit);
        for (int g = 0; g < line.timeline.glyphs.length; g++) {
            float p = (float) line.timeline.progress(g, time);
            float age = (time - line.timeline.starts[g]) / 1000f;
            float glow = p > 0 ? 20 * (float) Math.exp(-Math.max(0, age - 0.5f) * 0.35) : 0;
            int glyphSave = canvas.save();
            canvas.translate(line.offsets[g] + line.widths[g] / 2 - line.total / 2, 0);
            glyph(canvas, line, g, p > 0 ? accent : primary, p > 0 ? 1 : 0.22f, 0, glow);
            if (p > 0) {
                shape.setColor(alpha(accent, 0.06f * strength));
                path.reset();
                path.moveTo(-line.widths[g] / 2, -size);
                path.lineTo(line.widths[g] / 2, -size);
                path.lineTo(line.widths[g] * 2, -height * 0.6f);
                path.lineTo(-line.widths[g] * 2, -height * 0.6f);
                path.close();
                canvas.drawPath(path, shape);
            }
            canvas.restoreToCount(glyphSave);
        }
        canvas.restoreToCount(save);
        if (center > 0) row(canvas, center - 1, width / 2, height * 0.28f,
                size * 0.55f, width * 0.8f, 0.16f, false, 0);
        motes(canvas, 42, true);
    }

    private void motes(Canvas canvas, int count, boolean light) {
        double phase = animate ? time / 1000.0 : 0;
        for (int i = 0; i < count; i++) {
            float x = random(i * 7.17) * width;
            float y = (float) ((random(i * 3.71 + 8) * height + phase * (light ? -8 : 4)) % height);
            if (y < 0) y += height;
            float a = 0.05f + random(i * 2.41 + 2) * 0.15f;
            shape.setColor(alpha(accent, a));
            canvas.drawCircle(x, y, 0.8f + random(i * 5.71 + 3) * (light ? 2 : 1), shape);
        }
    }

    private static float random(double seed) {
        double value = Math.sin(seed + 1) * 10000;
        return (float) (value - Math.floor(value));
    }

    private static int alpha(int color, float opacity) {
        return (color & 0x00ffffff) | Math.round(Color.alpha(color) * Math.max(0, Math.min(1, opacity))) << 24;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        return Color.argb(Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    private static final class PreparedLine {
        final FoliaGlyphTimeline timeline;
        final float size, baseline;
        final float[] widths, offsets;
        final double[] angles;
        float total;
        double arcScale;

        PreparedLine(FoliaGlyphTimeline timeline, float size, float baseline) {
            this.timeline = timeline;
            this.size = size;
            this.baseline = baseline;
            widths = new float[timeline.glyphs.length];
            offsets = new float[timeline.glyphs.length + 1];
            angles = new double[timeline.glyphs.length];
        }
    }
}
