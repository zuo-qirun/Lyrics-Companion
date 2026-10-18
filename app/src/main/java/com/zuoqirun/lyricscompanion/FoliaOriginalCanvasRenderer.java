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
import java.util.ArrayList;
import java.util.List;

/** Independent Canvas renderer: the old Folia renderer remains the low-resource edition. */
final class FoliaOriginalCanvasRenderer {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint raster = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final FoliaGpuText gpuText = new FoliaGpuText();
    private final RectF rect = new RectF();
    private final Rect source = new Rect();
    private final Path path = new Path();
    private final FoliaOriginalClockworkRenderer clockwork = new FoliaOriginalClockworkRenderer();
    private final FoliaOriginalClockworkMotion clockworkMotion = new FoliaOriginalClockworkMotion();
    private final FoliaCameraMotion.Clock motionClock = new FoliaCameraMotion.Clock();
    private final FoliaCameraMotion.Spring orbit = new FoliaCameraMotion.Spring();
    private final FoliaCameraMotion.Spring audioBass = new FoliaCameraMotion.Spring();
    private final FoliaCameraMotion.Article articleCamera = new FoliaCameraMotion.Article();
    private final FoliaOriginalDioramaRenderer diorama = new FoliaOriginalDioramaRenderer();
    private final FoliaOriginalTemperaRenderer tempera = new FoliaOriginalTemperaRenderer();
    private final FoliaOriginalLumiereRenderer lumiere = new FoliaOriginalLumiereRenderer();
    private final FoliaOriginalSonnetRenderer sonnet = new FoliaOriginalSonnetRenderer();
    private final FoliaRingMath.Pose pose = new FoliaRingMath.Pose();
    private final LruCache<Integer, PorterDuffColorFilter> colors = new LruCache<>(32);
    private final LruCache<String, PreparedLine> lines = new LruCache<>(24);
    private final LruCache<Integer, FoliaCameraMotion.Spring> chatPositions = new LruCache<>(32);
    private final LruCache<String, Bitmap> masks = new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getByteCount(); }
    };
    private FoliaArticleLayout.Block[] articleBlocks;
    private float articleWidth, articleHeight;
    private List<LrcTimeline.Line> sourceLines = Collections.emptyList();
    private String track = "";
    private String mode = "claddagh";
    private Typeface face;
    private Typeface regularFace, lightFace, italicFace;
    private boolean[] chatSides = new boolean[0];
    private int[] chatSenders = new int[0];
    private int anchor = -1;
    private double ringOffset, ringFrom;
    private long transitionAge, lastClock = -1, lastPosition = -1;
    private float width, height, fontScale = 1, strength = 0.5f;
    private float radiusScale = 1, focusRatio = 0.65f, tilt = 45, tracking;
    private int primary = Color.WHITE, accent = 0xFFFF984B, muted = 0xFFB9BBC5;
    private float bass;
    private boolean axis = true, animate = true, showTranslation = true, audioReactive;
    private long time;
    private boolean showNextLine = true;
    private boolean lowResourceMode;
    private boolean overrideCurrentColor, overrideInactiveColor;
    private float nextLineScale = 1;
    private boolean playing;
    private long animationClock, frameDelta;
    private int outgoingAnchor = -1;
    private float containerBlur;

    void setAssets(android.content.res.AssetManager assets) { tempera.setAssets(assets); sonnet.setAssets(assets); }

    void setGeometrySeed(String seed) {
        tempera.setGeometrySeed(seed);
        sonnet.setGeometrySeed(seed);
        lumiere.setGeometrySeed(seed);
        diorama.setGeometrySeed(seed);
    }

    void configureNextLine(boolean show, float scale) {
        showNextLine = show;
        nextLineScale = Math.max(0.5f, Math.min(4, scale));
    }

    void setLowResourceMode(boolean low) {
        if (lowResourceMode != low) { masks.evictAll(); lines.evictAll(); }
        lowResourceMode = low;
    }

    void setLyricColorOverrides(boolean current, boolean inactive) {
        overrideCurrentColor = current;
        overrideInactiveColor = inactive;
    }

    void configure(String mode, Typeface face, float fontScale, int strength, int fps,
                   float radiusScale, float focusScale, float tilt, boolean axis, float tracking,
                   boolean showTranslation, boolean audioReactive) {
        String normalized = FoliaModeCatalog.normalize(mode);
        Typeface resolvedFace = face == null ? Typeface.create("sans-serif", Typeface.BOLD) : face;
        regularFace = face == null ? Typeface.create("sans-serif", Typeface.NORMAL) : face;
        lightFace = face == null ? Typeface.create("sans-serif-light", Typeface.NORMAL) : face;
        italicFace = Typeface.create(lightFace, Typeface.ITALIC);
        if (!normalized.equals(this.mode) || !resolvedFace.equals(this.face) || this.fontScale != fontScale
                || this.radiusScale != radiusScale || this.focusRatio != focusScale - 1
                || this.tracking != tracking) {
            articleBlocks = null;
            lines.evictAll();
            chatPositions.evictAll();
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
        if (!this.animate) bass = 0;
        this.showTranslation = showTranslation;
        this.audioReactive = audioReactive;
        diorama.configure(face, fontScale, this.strength, showTranslation, animate, lowResourceMode);
        diorama.setTracking(tracking);
        tempera.configure(face, fontScale, this.strength, showTranslation, animate, lowResourceMode);
        tempera.setTracking(tracking);
        lumiere.configure(face, fontScale, this.strength, showTranslation, animate, lowResourceMode);
        lumiere.setTracking(tracking);
        sonnet.configure(face, fontScale, this.strength, showTranslation, animate, lowResourceMode);
        sonnet.setTracking(tracking);
    }

    boolean transitioning() { return animate && (orbit.moving(anchor * Math.PI)
            || transitionAge < 1400 || diorama.transitioning() || tempera.transitioning()
            || lumiere.transitioning() || sonnet.transitioning()); }

    void clear() {
        articleBlocks = null;
        chatPositions.evictAll();
        lines.evictAll();
        masks.evictAll();
        colors.evictAll();
        gpuText.clear();
        sourceLines = Collections.emptyList();
        track = "";
        diorama.clear(); tempera.clear(); lumiere.clear(); sonnet.clear();
        resetMotion();
    }

    private void resetMotion() {
        anchor = -1;
        lastClock = -1;
        lastPosition = -1;
        transitionAge = 1400;
        ringOffset = ringFrom = 0;
        motionClock.clear(); orbit.clear(); audioBass.clear(); articleCamera.clear();
        chatPositions.evictAll();
        clockworkMotion.clear();
        outgoingAnchor = -1;
    }

    void draw(Canvas canvas, MusicSnapshot snapshot, long lyricTime, long clock,
              float density, int primary, int accent, int muted) {
        // Shader opacity must not inherit the previous frame's last decorative colour alpha.
        shape.setShader(null);
        shape.setColorFilter(null);
        shape.setColor(Color.WHITE);
        shape.setAlpha(255);
        shape.setStyle(Paint.Style.FILL);
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
            chatSides = FoliaSceneMath.cappellaSides(incoming);
            chatSenders = FoliaSceneMath.cappellaSenders(incoming);
            track = snapshot.sourceName + '\n' + snapshot.title + '\n' + snapshot.artist;
            articleBlocks = null;
            lines.evictAll();
            resetMotion();
        }
        int current = activeIndex(sourceLines, time);
        playing = snapshot.playing && animate;
        motionClock.step(time, clock, playing);
        boolean seek = motionClock.seek;
        long dt = motionClock.deltaMs;
        animationClock = motionClock.animationMs;
        frameDelta = dt;
        transitionAge += dt;
        if (current != anchor) {
            ringFrom = ringOffset;
            outgoingAnchor = anchor;
            if (anchor < 0 || Math.abs(current - anchor) > 1 || seek || !playing) {
                ringOffset = current * Math.PI;
                orbit.snap(ringOffset);
                transitionAge = 1400;
            } else {
                transitionAge = 0;
            }
            anchor = current;
        }
        if (seek) {
            ringOffset = current * Math.PI;
            orbit.snap(ringOffset);
            articleCamera.clear();
            diorama.clear(); tempera.clear(); lumiere.clear(); sonnet.clear();
            outgoingAnchor = -1;
            transitionAge = 1400;
        }
        ringOffset = orbit.step(anchor * Math.PI, dt / 1000.0,
                "pendolo".equals(mode) ? 360 : 55, "pendolo".equals(mode) ? 20 : 14,
                "pendolo".equals(mode) ? 0.8 : 0.9);
        lastClock = clock;
        lastPosition = time;
        AudioSpectrumSource.Frame audio = AudioSpectrumSource.latestFrame();
        float targetBass = 0;
        if (audioReactive && snapshot.playing && audio.live && audio.levels.length > 0) {
            for (int i = 0; i < Math.min(6, audio.levels.length); i++) targetBass += audio.levels[i];
            targetBass /= Math.min(6, audio.levels.length);
        }
        bass = (float) audioBass.step(targetBass, dt / 1000.0, 150, 25, 1);
        clockworkMotion.step(dt / 1000.0, targetBass, !playing, strength);
        if (sourceLines.isEmpty()) {
            label(canvas, snapshot.lyrics.lyric.isEmpty() ? snapshot.active ? "正在匹配歌词" : "等待播放"
                    : snapshot.lyrics.lyric, width / 2, height / 2,
                    Math.min(40 * fontScale, width / 12), primary, Paint.Align.CENTER);
        } else if ("claddagh".equals(mode)) {
            ring(canvas);
        } else if ("pendolo".equals(mode)) {
            clockwork(canvas);
        } else if ("cappella".equals(mode)) {
            conversation(canvas, snapshot);
        } else if ("monet".equals(mode)) {
            portrait(canvas, snapshot);
        } else if ("fume".equals(mode)) {
            article(canvas);
        } else if ("sonnet".equals(mode)) {
            sonnet.draw(canvas, snapshot, sourceLines, anchor, time, animationClock, frameDelta,
                    width, height, bass, primary, accent, muted);
        } else if ("diorama".equals(mode)) {
            diorama.draw(canvas, snapshot, sourceLines, anchor, time, animationClock, frameDelta,
                    width, height, bass, primary, accent, muted);
        } else if ("tempera".equals(mode)) {
            tempera.draw(canvas, snapshot, sourceLines, anchor, time, animationClock, frameDelta,
                    width, height, bass, primary, accent, muted);
        } else if ("lumiere".equals(mode)) {
            lumiere.draw(canvas, snapshot, sourceLines, anchor, time, animationClock, frameDelta,
                    width, height, bass, primary, accent, muted);
        } else if ("still".equals(mode)) {
            staticLine(canvas);
        } else {
            drawTypographyTransition(canvas);
        }
        if (!"monet".equals(mode) && !"lumiere".equals(mode)
                && !"still".equals(mode) && !"pendolo".equals(mode)) subtitle(canvas);
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
        return prepare(index, size, radius, sceneFace(), "claddagh".equals(mode) ? 0.22f : 0);
    }

    private Typeface sceneFace() {
        return "tilt".equals(mode) ? regularFace : "cappella".equals(mode)
                || "pendolo".equals(mode) || "monet".equals(mode) ? regularFace : face;
    }

    private PreparedLine prepare(int index, float size, float radius, Typeface font, float spacingEm) {
        LrcTimeline.Line line = sourceLines.get(index);
        String key = index + ":" + size + ":" + radius + ":" + width + ':' + height + ':' + font.hashCode() + ':' + spacingEm + ':' + tracking;
        PreparedLine cached = lines.get(key);
        if (cached != null) return cached;
        text.setTypeface(font);
        text.setTextSize(size);
        text.getFontMetrics(metrics);
        long nextStart = index + 1 < sourceLines.size() ? sourceLines.get(index + 1).timeMs : -1;
        FoliaGlyphTimeline timeline = new FoliaGlyphTimeline(line, nextStart);
        PreparedLine result = new PreparedLine(index, timeline, font, size, -(metrics.ascent + metrics.descent) / 2);
        if ("claddagh".equals(mode)) result.ringTiming = new FoliaOriginalGraphemeTiming(line, nextStart, true);
        double spacingScale = (1 + focusRatio) / 1.65;
        result.spacing = size * spacingEm + tracking;
        float[] measuredPrefixes = new float[timeline.glyphs.length + 1];
        for (int i = 0; i < timeline.glyphs.length; i++) {
            result.widths[i] = text.measureText(timeline.glyphs[i]);
            measuredPrefixes[i + 1] = text.measureText(line.text, 0, timeline.offsets[i + 1]);
        }
        if ("claddagh".equals(mode)) FoliaOriginalRingMath.measuredOffsets(measuredPrefixes, size, tracking, result.offsets);
        else FoliaTextSpacing.apply(measuredPrefixes, result.spacing, result.offsets);
        result.total = result.offsets[timeline.glyphs.length];
        result.arcScale = FoliaOriginalRingMath.angles(result.offsets, spacingScale, size, radius, result.angles);
        lines.put(key, result);
        return result;
    }

    private void ring(Canvas canvas) {
        float size = 72 * fontScale;
        float radius = (float) FoliaRingMath.radius(width, radiusScale);
        float intensity = strength < 0.34f ? 0.08f : strength > 0.66f ? 0.95f : 0.25f;
        float maxScale = strength < 0.34f ? 1.08f : strength > 0.66f ? 1.95f : 1.25f;
        float powerScale = animate ? Math.min(1 + bass * intensity, maxScale) : 1;
        if (axis) {
            int save = canvas.save();
            canvas.translate(width / 2, height / 2);
            canvas.rotate(90 - tilt);
            canvas.scale(1 + bass * bass * 1.5f, 1 + bass * bass * 0.5f);
            float colorRatio = Math.min(1, Math.max(0, bass - 0.02f) / 0.58f);
            shape.setShader(new LinearGradient(-150, 0, 150, 0,
                    new int[]{Color.TRANSPARENT, alpha(mix(primary, accent, colorRatio), 0.2f + colorRatio * 0.75f),
                            alpha(mix(primary, accent, colorRatio), 0.2f + colorRatio * 0.75f), Color.TRANSPARENT},
                    new float[]{0, 0.2f, 0.8f, 1}, Shader.TileMode.CLAMP));
            canvas.drawRect(-150, -2, 150, 2, shape);
            shape.setShader(null);
            canvas.restoreToCount(save);
        }
        PreparedLine active = anchor >= 0 ? prepare(anchor, size, radius) : null;
        double activeOffset = active == null || !animate ? 0 : active.ringTiming.wordOffset(active.angles, time);
        double progress = active == null || !animate ? 0 : active.ringTiming.playbackProgress(time);
        double follow = Math.PI * 0.52 * (1 - Math.pow(1 - progress, 1.35));
        for (int index = Math.max(0, anchor - 1); index <= Math.min(sourceLines.size() - 1, anchor + 2); index++) {
            PreparedLine line = prepare(index, size, radius);
            double distance = Math.abs(ringOffset - index * Math.PI) / Math.PI;
            double offset = !animate ? 0 : line.ringTiming.wordOffset(line.angles, time);
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
                float sung = (float) line.ringTiming.progress(glyph, time);
                float highlight = Math.min(1, sung * 15);
                int color = mix(primary, accent, highlight);
                float opacity = Math.round((float) pose.opacity * 1000) / 1000f * (0.55f + 0.45f * highlight);
                float flash = animate && sung > 0 && sung < 0.4f
                        ? (float) Math.pow(1 - sung / 0.4, 2) * 0.65f : 0;
                float glow = (float) (24 * Math.pow(pose.focus, 2) * (1 + flash));
                int save = canvas.save();
                // Match the CSS values actually sent to Chromium, rather than unrounded ideals.
                canvas.translate(width / 2 + (float) (Math.round(pose.x * 10) / 10.0),
                        height / 2 + (float) (Math.round(pose.y * 10) / 10.0));
                canvas.rotate((float) (Math.round(pose.rotation * 100) / 100.0));
                float scale = (float) (Math.round(pose.scale * 1000) / 1000.0);
                canvas.scale(scale, scale);
                // CSS centres a span whose width includes its trailing 0.04em letter spacing.
                canvas.translate(-size * 0.02f, 0);
                glyph(canvas, line, glyph, color, opacity, Math.round((float) pose.blur * 100) / 100f, glow);
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
        glyph(canvas, line, index, color, opacity, blur, glow, false);
    }

    private void glyph(Canvas canvas, PreparedLine line, int index, int color,
                       float opacity, float blur, float glow, boolean resolvedColor) {
        blur = Math.max(blur, containerBlur);
        if (lowResourceMode) { blur = 0; glow = 0; }
        if (!resolvedColor && (line.index == anchor ? overrideCurrentColor : overrideInactiveColor)) {
            color = line.index == anchor ? accent : muted;
        }
        if (opacity <= 0 || line.timeline.glyphs[index].trim().isEmpty()) return;
        text.setTypeface(line.font);
        text.setTextSize(line.size);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(alpha(color, opacity));
        if (glow > 0.5f) {
            float fade = (float) FoliaRingMath.clamp((glow - 0.5f) / 2, 0, 1);
            if ("claddagh".equals(mode)) drawSoftMask(canvas, line, index, color, opacity * fade,
                    (float) Math.hypot(glow * 0.5, blur), 1);
            else {
                // Upstream glow has its own accent layer, separate from the body's progress colour.
                // Fade its alpha at a constant CSS-shadow radius; this also avoids raster-cache churn.
                float glowOpacity = opacity * fade * Math.min(1.6f, glow / 20);
                drawSoftMask(canvas, line, index, accent, glowOpacity, 33.8f, 1);
                drawSoftMask(canvas, line, index, accent, glowOpacity * 0.92f, 33.8f, 2);
            }
        }
        if (blur > 0.2f) {
            drawSoftMask(canvas, line, index, color, opacity, blur, 0);
        } else {
            canvas.drawText(line.timeline.glyphs[index], 0, line.baseline, text);
        }
    }

    private void drawSoftMask(Canvas canvas, PreparedLine line, int index, int color, float opacity, float blur, int layer) {
        if (gpuText.draw(canvas, line.index + ":" + index + ':' + layer, line.timeline.glyphs[index], line.font, line.size, line.baseline,
                color, opacity, blur)) return;
        drawMask(canvas, line, index, color, opacity, blur);
    }

    private void drawMask(Canvas canvas, PreparedLine line, int index, int color, float opacity, float sigma) {
        // RenderEffect/CSS blur use sigma. BlurMaskFilter's public radius is converted by Skia:
        // https://skia.googlesource.com/skia/+/refs/heads/android/10-release/src/core/SkBlurMask.cpp
        float radius = sigma > 0.5f ? (sigma - 0.5f) / 0.57735f : 0;
        int pad = (int) Math.ceil(sigma * 3) + 3;
        String key = line.timeline.glyphs[index] + ':' + line.size + ':' + sigma + ':' + line.font.hashCode();
        text.setTypeface(line.font);
        text.setTextSize(line.size);
        Bitmap mask = masks.get(key);
        if (mask == null) {
            text.getFontMetrics(metrics);
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
            masks.trimToSize(ResourceProfile.maskCacheBytes(lowResourceMode));
        }
        PorterDuffColorFilter filter = colors.get(color);
        if (filter == null) {
            filter = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            colors.put(color, filter);
        }
        shape.setColorFilter(filter);
        shape.setAlpha(Math.round(255 * Math.max(0, Math.min(1, opacity))));
        text.getFontMetrics(metrics);
        float top = line.baseline + metrics.top - pad;
        canvas.drawBitmap(mask, -mask.getWidth() / 2f, top, shape);
        shape.setColorFilter(null);
        shape.setAlpha(255);
    }

    private void label(Canvas canvas, String value, float x, float y, float size, int color, Paint.Align align) {
        label(canvas, value, x, y, size, color, align, sceneFace());
    }

    private void label(Canvas canvas, String value, float x, float y, float size, int color, Paint.Align align, Typeface font) {
        text.setTypeface(font);
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
            float entry = reveal && animate
                    ? (float) FoliaRingMath.clamp((time - line.timeline.starts[i] + 180) / 360.0, 0, 1) : 1;
            int glyphSave = canvas.save();
            canvas.translate(line.offsets[i] + line.widths[i] / 2 - line.total / 2,
                    animate ? (1 - entry) * 18 : 0);
            glyph(canvas, line, i, mix(primary, accent, Math.min(1, progress * 15)),
                    opacity * (0.22f + 0.78f * entry), 0,
                    animate && progress > 0 && progress < 1 ? 8 * strength : 0);
            canvas.restoreToCount(glyphSave);
        }
        canvas.restoreToCount(save);
    }

    private void subtitle(Canvas canvas) {
        if (anchor < 0 || anchor >= sourceLines.size()) return;
        LrcTimeline.Line active = sourceLines.get(anchor);
        String value = !active.translated.isEmpty() ? active.translated : active.romaji;
        if (showTranslation && !value.isEmpty()) {
            float size = FoliaSceneMath.clampFont(width, 18, 2.6f, 20, fontScale);
            text.setTypeface(face);
            text.setTextSize(size);
            size *= Math.min(1, width * 0.84f / Math.max(1, text.measureText(value)));
            label(canvas, value, width / 2, height - 32 - size * 0.25f, size, alpha(muted, 0.6f), Paint.Align.CENTER, regularFace);
        }
        if (showNextLine && (!showTranslation || value.isEmpty()) && anchor + 1 < sourceLines.size()) {
            PreparedLine next = prepare(anchor + 1,
                    Math.max(14, Math.min(width * 0.02f, 16)) * nextLineScale, width, regularFace, 0);
            slice(canvas, next, 0, next.timeline.glyphs.length, width / 2,
                    height - 36, width * 0.84f, 0.6f, muted, false, 0, 0);
        }
    }

    private void staticLine(Canvas canvas) {
        int current = Math.max(0, anchor);
        float[] centers = FoliaOriginalStillLayout.lyricCenters(sourceLines, current, height, showTranslation);
        for (int offset = -1; offset <= 1; offset++) {
            int index = current + offset;
            if (index < 0 || index >= sourceLines.size()) continue;
            float size = (offset == 0 ? 40 : 30) * fontScale;
            PreparedLine line = prepare(index, size, width, face, 0.02f);
            float opacity = offset == 0 ? 1 : 0.3f;
            float y = centers[offset + 1];
            int rowSave = canvas.save();
            canvas.translate(-size * 0.01f, 0);
            slice(canvas, line, 0, line.timeline.glyphs.length, width / 2, y,
                    width - 96, opacity, primary, false, 0, 0);
            canvas.restoreToCount(rowSave);
            String translation = sourceLines.get(index).translated;
            if (showTranslation && !translation.isEmpty()) {
                float subtitleSize = (offset == 0 ? 24 : 20) * fontScale;
                text.setTypeface(regularFace); text.setTextSize(subtitleSize); text.getFontMetrics(metrics);
                float baseline = y + (offset == 0 ? 25 + 12 + 16 : 18 + 8 + 14)
                        - (metrics.ascent + metrics.descent) / 2;
                trackedLabel(canvas, translation, width / 2, baseline, subtitleSize,
                        alpha(muted, offset == 0 ? 0.8f : 0.3f), subtitleSize * 0.025f);
            }
        }
    }

    private void trackedLabel(Canvas canvas, String value, float centerX, float baseline,
                              float size, int color, float letterSpacing) {
        text.setTypeface(regularFace); text.setTextSize(size); text.setColor(color);
        text.setTextAlign(Paint.Align.LEFT); text.setShader(null); text.clearShadowLayer();
        String[] glyphs = FoliaGlyphTimeline.split(value);
        float left = centerX - (text.measureText(value) + glyphs.length * letterSpacing) / 2;
        int cursor = 0;
        for (int i = 0; i < glyphs.length; i++) {
            canvas.drawText(glyphs[i], left + text.measureText(value, 0, cursor) + i * letterSpacing, baseline, text);
            cursor += glyphs[i].length();
        }
    }

    private void typography(Canvas canvas) {
        if ("tilt".equals(mode)) tiltTypography(canvas);
        else if ("partita".equals(mode)) partitaTypography(canvas);
        else if ("cadenza".equals(mode)) cadenzaTypography(canvas);
        else classicTypography(canvas);
    }

    /** Keep the outgoing line alive for its original exit; never replace the whole scene in one frame. */
    private void drawTypographyTransition(Canvas canvas) {
        if (!animate || anchor < 0) { typography(canvas); return; }
        int current = anchor;
        if (outgoingAnchor >= 0 && outgoingAnchor < sourceLines.size() && transitionAge < 450) {
            float p = (float) FoliaCameraMotion.ease(Math.min(1, transitionAge / ("tilt".equals(mode) ? 450.0 : 300.0)));
            if (p < 1) {
                anchor = outgoingAnchor;
                int layer = canvas.saveLayerAlpha(0, 0, width, height, Math.round((1 - p) * 255), Canvas.ALL_SAVE_FLAG);
                canvas.scale(1 + 0.1f * p, 1 + 0.1f * p, width / 2, height / 2);
                containerBlur = p * 20;
                typography(canvas);
                containerBlur = 0;
                canvas.restoreToCount(layer);
                anchor = current;
            }
        }
        long age = Math.max(0, time - sourceLines.get(current).timeMs);
        float entry = (float) FoliaSceneMath.easeOut(age / 300.0);
        int layer = canvas.saveLayerAlpha(0, 0, width, height, Math.round(entry * 255), Canvas.ALL_SAVE_FLAG);
        canvas.scale(0.9f + 0.1f * entry, 0.9f + 0.1f * entry, width / 2, height / 2);
        containerBlur = (1 - entry) * 10;
        if ("classic".equals(mode) || "partita".equals(mode)) {
            double duration = strength < 0.34f ? 8.5 : strength > 0.66f ? 5.8 : 7;
            float distance = strength < 0.34f ? 10 : strength > 0.66f ? 18 : 14;
            double phase = animationClock / (duration * 1000) % 1 * 4;
            int segment = (int) phase;
            double p = FoliaCameraMotion.ease(phase - segment);
            float[] ys = {0, -distance, 0, distance * 0.45f, 0};
            float[] scales = {1, 1.01f, 1, 0.995f, 1};
            canvas.translate(0, (float) FoliaCameraMotion.mix(ys[segment], ys[segment + 1], p));
            float s = (float) FoliaCameraMotion.mix(scales[segment], scales[segment + 1], p);
            canvas.scale(s, s, width / 2, height / 2);
        }
        typography(canvas);
        containerBlur = 0;
        canvas.restoreToCount(layer);
    }

    private void slice(Canvas canvas, PreparedLine line, int first, int end, float x, float y,
                       float maxWidth, float opacity, int color, boolean reveal, float stagger, float glow) {
        if (end <= first) return;
        float extent = line.extent(first, end);
        float fit = Math.min(1, maxWidth / Math.max(1, extent));
        int save = canvas.save();
        canvas.translate(x, y);
        canvas.scale(fit, fit);
        for (int i = first; i < end; i++) {
            float entered = reveal && animate ? (float) FoliaSceneMath.easeOut(
                    (time - line.timeline.starts[first] + 250 - (i - first) * stagger) / 500.0) : 1;
            if (entered < 0.001f) continue;
            int glyphSave = canvas.save();
            canvas.translate(line.offsets[i] - line.offsets[first] + line.widths[i] / 2 - extent / 2, 0);
            glyph(canvas, line, i, color, opacity * entered, 0, glow);
            canvas.restoreToCount(glyphSave);
        }
        canvas.restoreToCount(save);
    }

    private void tiltTypography(Canvas canvas) {
        int index = Math.max(0, anchor);
        long seed = sourceLines.get(index).timeMs;
        int rows = FoliaSceneMath.tiltRowCount(sourceLines.get(index).text.trim().length(), seed);
        int tilted = FoliaSceneMath.tiltAccentRow(rows, seed);
        float size = FoliaSceneMath.clampFont(width, 50, 6.875f, 90, fontScale);
        PreparedLine normal = prepare(index, size, width, regularFace, 0.08f);
        PreparedLine italic = prepare(index, size, width, italicFace, 0.15f);
        int count = normal.timeline.glyphs.length;
        rows = Math.min(rows, Math.max(1, count));
        int perRow = (int) Math.ceil(count / (double) rows);
        float rowStep = size * 1.35f + 16;
        // The upstream main container occupies 70vh and is centered in the shell.
        float firstY = height / 2 - (rows - 1) * rowStep / 2;
        for (int r = 0; r < rows; r++) {
            int first = r * perRow, end = Math.min(count, first + perRow);
            if (first >= end) continue;
            boolean isTilt = r == tilted;
            PreparedLine line = isTilt ? italic : normal;
            float extent = line.extent(first, end);
            float fit = Math.min(1, width * 0.85f / Math.max(1, extent));
            float entry = animate ? (float) FoliaSceneMath.easeOut(
                    (time - line.timeline.starts[first] + 250) / (isTilt ? 600.0 : 550.0)) : 1;
            int save = canvas.save();
            canvas.translate(width / 2, firstY + r * rowStep + (1 - entry) * (isTilt ? 24 : 20));
            canvas.scale(fit * (isTilt ? 0.92f + 0.08f * entry : 1), fit);
            int visual = 0;
            for (int g = first; g < end; g++) {
                float reveal = animate ? (float) FoliaSceneMath.easeOut(
                        (time - line.timeline.starts[first] + 250 - visual * (isTilt ? 50 : 40)) / 500.0) : 1;
                float duration = Math.max(200, Math.min(900, line.timeline.ends[g] - line.timeline.starts[g]));
                float pulse = animate ? (float) Math.sin(Math.PI * FoliaRingMath.clamp(
                        (time - line.timeline.starts[g]) / duration, 0, 1)) : 0;
                int glyphSave = canvas.save();
                canvas.translate(line.offsets[g] - line.offsets[first] + line.widths[g] / 2 - extent / 2,
                        isTilt ? (visual % 2 == 0 ? -1 : 1) * size / 6 * (2 - reveal) : 0);
                float scale = 1 + pulse * (isTilt ? 0.18f : 0.15f);
                canvas.scale(scale, scale);
                glyph(canvas, line, g, isTilt ? accent : primary, reveal * entry, 0, 0);
                canvas.restoreToCount(glyphSave);
                if (!line.timeline.glyphs[g].trim().isEmpty()) visual++;
            }
            canvas.restoreToCount(save);
        }
    }

    private int[] wordEnds(PreparedLine line, int index) {
        if (line.timeline.glyphs.length == 0) return new int[0];
        if (line.semanticEnds != null) return line.semanticEnds;
        List<Integer> ends = new ArrayList<>();
        String value = sourceLines.get(index).text;
        if (sourceLines.get(index).words.isEmpty() && android.os.Build.VERSION.SDK_INT >= 24) {
            android.icu.text.BreakIterator iterator = android.icu.text.BreakIterator.getWordInstance(java.util.Locale.SIMPLIFIED_CHINESE);
            iterator.setText(value);
            int first = iterator.first();
            for (int finish = iterator.next(); finish != android.icu.text.BreakIterator.DONE;
                 first = finish, finish = iterator.next()) {
                if (value.substring(first, finish).trim().isEmpty()) continue;
                int end = 0;
                while (end < line.timeline.glyphs.length && line.timeline.offsets[end] < finish) end++;
                if (end > 0 && (ends.isEmpty() || end > ends.get(ends.size() - 1))) ends.add(end);
            }
        }
        int cursor = 0;
        for (LrcTimeline.Word word : ends.isEmpty() ? sourceLines.get(index).words : Collections.<LrcTimeline.Word>emptyList()) {
            int found = sourceLines.get(index).text.indexOf(word.text, cursor);
            if (found < 0 || word.text.isEmpty()) continue;
            cursor = found + word.text.length();
            int end = 0;
            while (end < line.timeline.glyphs.length && line.timeline.offsets[end] < cursor) end++;
            if (end > 0 && (ends.isEmpty() || end > ends.get(ends.size() - 1))) ends.add(end);
        }
        if (ends.isEmpty()) {
            for (int i = 0; i < line.timeline.glyphs.length; i++) {
                if (FoliaSceneMath.isCjk(line.timeline.glyphs[i]) || line.timeline.glyphs[i].trim().isEmpty()) ends.add(i + 1);
            }
        }
        if (ends.isEmpty() || ends.get(ends.size() - 1) != line.timeline.glyphs.length) ends.add(line.timeline.glyphs.length);
        int[] result = new int[ends.size()];
        for (int i = 0; i < result.length; i++) result[i] = ends.get(i);
        line.semanticEnds = result;
        return result;
    }

    private void classicTypography(Canvas canvas) {
        int index = Math.max(0, anchor);
        float size = FoliaSceneMath.clampFont(width, 36, 6, 72, fontScale);
        PreparedLine line = current(size);
        int[] words = wordEnds(line, index);
        double seed = sourceLines.get(index).timeMs / 1000.0;
        float available = Math.min(width - 64, 1152);
        // Reserve each word's peak spring scale, as in the upstream non-legacy layout.
        float[] advances = new float[words.length];
        int first = 0;
        for (int w = 0; w < words.length; w++) {
            float extent = line.extent(first, words[w]);
            float s = 1.1f + (float) FoliaSceneMath.random(seed + w + 4) * 0.2f;
            float nextWidth = w + 1 < words.length ? line.extent(words[w], words[w + 1]) : 0;
            float nextScale = w + 1 < words.length ? 1.1f + (float) FoliaSceneMath.random(seed + w + 5) * 0.2f : 1;
            float x = (float) (FoliaSceneMath.random(seed + w + 1) - 0.5) * 40;
            float nextX = w + 1 < words.length ? (float) (FoliaSceneMath.random(seed + w + 2) - 0.5) * 40 : 0;
            float margin = Math.max(size * 0.12f * 0.7f,
                    (extent * (s * 1.4f - 1) / 2 + nextWidth * (nextScale * 1.4f - 1) / 2 + x - nextX + size * 0.05f) * 0.7f);
            advances[w] = extent + Math.round(margin * 10) / 10f;
            first = words[w];
        }
        int rowFirst = 0, glyphFirst = 0;
        int rowCount = 1;
        float used = 0;
        for (float advance : advances) { if (used > 0 && used + advance > available) { rowCount++; used = 0; } used += advance; }
        float y = height / 2 - (rowCount - 1) * size * 1.65f / 2;
        while (rowFirst < words.length) {
            int rowEnd = rowFirst;
            float extent = 0;
            while (rowEnd < words.length && (rowEnd == rowFirst || extent + advances[rowEnd] <= available)) extent += advances[rowEnd++];
            float left = width / 2 - extent / 2;
            for (int w = rowFirst; w < rowEnd; w++) {
                int end = words[w];
                double wordSeed = seed + w;
                float baseScale = 1.1f + (float) FoliaSceneMath.random(wordSeed + 4) * 0.2f;
                float x = (float) (FoliaSceneMath.random(wordSeed + 1) - 0.5) * 40;
                float dy = (float) (FoliaSceneMath.random(wordSeed + 2) - 0.5) * 40;
                float rotation = (float) (FoliaSceneMath.random(wordSeed + 3) - 0.5) * 10;
                float wordWidth = line.extent(glyphFirst, end);
                long start = line.timeline.starts[Math.min(glyphFirst, line.timeline.starts.length - 1)];
                long finish = line.timeline.ends[end - 1];
                float entered = animate ? (float) FoliaSceneMath.spring(Math.max(0, time - start + 150) / 1000.0, 200, 20, 1) : 1;
                float passed = animate ? (float) FoliaSceneMath.easeOut((time - finish) / 500.0) : 0;
                float scale = 0.5f + (baseScale * 1.4f - 0.5f) * entered;
                scale += (baseScale - baseScale * 1.4f) * passed;
                float opacity = animate ? (float) FoliaRingMath.clamp((time - start + 150) / 100.0, 0, 1) * (1 - 0.18f * passed) : 1;
                int save = canvas.save();
                canvas.translate(left + wordWidth / 2 + x + (1 - entered) * (float) Math.sin(dy) * 100,
                        y + dy + (1 - entered) * (float) Math.cos(x) * 50);
                canvas.rotate(rotation + (1 - entered) * 20 + passed * (float) (FoliaSceneMath.random(wordSeed + 8) - 0.5) * 45);
                canvas.scale(scale, scale);
                slice(canvas, line, glyphFirst, end, 0, 0, Float.MAX_VALUE, opacity,
                        mix(accent, primary, passed), false, 0, animate ? (1 - passed) * 18 * strength : 0);
                canvas.restoreToCount(save);
                left += advances[w];
                glyphFirst = end;
            }
            y += size * 1.65f;
            rowFirst = rowEnd;
        }
    }

    private void partitaTypography(Canvas canvas) {
        int index = Math.max(0, anchor);
        int count = FoliaGlyphTimeline.split(sourceLines.get(index).text).length;
        float densityScale = count > 40 ? 0.8f : 1;
        PreparedLine line = current(FoliaSceneMath.clampFont(width, 40, 5.5f, 72, fontScale * densityScale));
        int[] words = wordEnds(line, index);
        if (line.chunks == null) line.chunks = FoliaSceneMath.partitaChunks(words.length, height, sourceLines.get(index).timeMs / 1000.0);
        float step = line.size * 1.22f + 6.4f + 9.6f;
        for (int r = 0; r < line.chunks.size(); r++) {
            FoliaSceneMath.PartitaChunk chunk = line.chunks.get(r);
            int first = chunk.first == 0 ? 0 : words[chunk.first - 1], end = words[chunk.end - 1];
            long start = line.timeline.starts[first], finish = line.timeline.ends[end - 1];
            float entry = animate ? (float) FoliaSceneMath.spring(Math.max(0, time - start + 150) / 1000.0, 200, 20, 1) : 1;
            float opacity = animate ? (float) FoliaRingMath.clamp((time - start + 150) / 100.0, 0, 1) : 1;
            float extent = line.extent(first, end) + (chunk.end - chunk.first) * 12.8f;
            float fit = Math.min(1, (width - 64 - Math.abs(chunk.x) * 2) / Math.max(1, extent));
            int save = canvas.save();
            canvas.translate(width / 2 + chunk.x + (1 - entry) * (r % 2 == 0 ? -40 : 40),
                    height / 2 + (r - (line.chunks.size() - 1) / 2f) * step + chunk.y);
            canvas.scale(fit * (0.85f + 0.15f * entry), fit * (0.85f + 0.15f * entry));
            float left = -extent / 2;
            int glyphFirst = first;
            for (int w = chunk.first; w < chunk.end; w++) {
                int glyphEnd = words[w];
                double seed = start / 1000.0 + (w - chunk.first) * 7.13;
                float wordWidth = line.extent(glyphFirst, glyphEnd);
                float dx = (float) (FoliaSceneMath.random(seed + 1) - 0.5) * 12;
                float dy = (float) (FoliaSceneMath.random(seed + 2) - 0.5) * 12;
                float wordEntry = animate ? (float) FoliaSceneMath.spring(Math.max(0,
                        time - line.timeline.starts[glyphFirst] + 150) / 1000.0, 200, 20, 1) : 1;
                float passed = animate ? (float) FoliaSceneMath.easeOut((time - line.timeline.ends[glyphEnd - 1]) / 500.0) : 0;
                float wordOpacity = animate ? (float) FoliaRingMath.clamp(
                        (time - line.timeline.starts[glyphFirst] + 150) / 100.0, 0, 1) * (1 - 0.18f * passed) : 1;
                float scale = 0.5f + (chunk.scale * 1.4f - 0.5f) * wordEntry - chunk.scale * 0.4f * passed;
                int wordSave = canvas.save();
                canvas.translate(left + wordWidth / 2 + dx + (1 - wordEntry) * (float) Math.sin(dy) * 100,
                        dy + (1 - wordEntry) * (float) Math.cos(dx) * 50);
                canvas.rotate((float) (FoliaSceneMath.random(seed + 3) - 0.5) * 6 + (1 - wordEntry) * 20
                        + (float) (FoliaSceneMath.random(seed + 8) - 0.5) * 20
                        * (float) FoliaRingMath.clamp((time - line.timeline.ends[glyphEnd - 1]) / 5000.0, 0, 1));
                canvas.scale(scale, scale);
                float colorProgress = animate ? (float) FoliaRingMath.clamp(
                        (time - line.timeline.starts[glyphFirst] + 150) / (double) Math.max(200,
                                line.timeline.ends[glyphEnd - 1] - line.timeline.starts[glyphFirst]), 0, 1) : 1;
                float colorPass = animate ? (float) FoliaSceneMath.easeOut((time - line.timeline.ends[glyphEnd - 1]) / 800.0) : 0;
                slice(canvas, line, glyphFirst, glyphEnd, 0, 0, Float.MAX_VALUE, opacity * wordOpacity,
                        mix(primary, accent, colorProgress * (1 - colorPass)), false, 0,
                        animate ? 20 * (1 - colorPass) * strength : 0);
                canvas.restoreToCount(wordSave);
                left += wordWidth + 12.8f; glyphFirst = glyphEnd;
            }
            shape.setColor(alpha(time <= finish ? accent : primary, opacity * (time <= finish ? 1 : 0.22f)));
            float side = (r % 2 == 0 ? -1 : 1) * (extent / 2 + 16);
            float bottom = line.size * 1.22f / 2 + 3.2f;
            canvas.drawRect(side, bottom - 16, side + 1, bottom + 16, shape);
            canvas.drawRect(-extent / 2 - 24, bottom + 8, extent / 2 + 28, bottom + 9, shape);
            canvas.restoreToCount(save);
        }
    }

    private void cadenzaTypography(Canvas canvas) {
        int index = Math.max(0, anchor);
        LrcTimeline.Line source = sourceLines.get(index);
        float size = FoliaSceneMath.cadenzaFont(width, FoliaGlyphTimeline.split(source.text).length,
                Math.max(1, source.words.size()), fontScale);
        size = (float) Math.floor(size * 100) / 100; // Chromium quantizes the upstream Canvas font to hundredths.
        PreparedLine line = current(size);
        float compression = (float) FoliaRingMath.clamp(0.92 - Math.max(0, line.timeline.glyphs.length - 12) * 0.018, 0.62, 0.92);
        float wrapWidth = Math.min(width - 48, Math.max(Math.min(220, width - 48), Math.min(width * 0.72f * compression, 820)));
        float step = Math.round(size * (FoliaSceneMath.isCjk(source.text) ? 1.22f : 1.1f));
        if (line.placements == null) line.placements = FoliaCadenzaLayout.build(source, line.timeline, line.offsets,
                FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, wrapWidth, line.spacing), size, step, wrapWidth, line.spacing);
        float focusY = height * 0.42f + (animate ? (float) Math.sin(time / 1000.0 * 2.3) * (3 + bass * 8) : 0);
        // Waiting fragments are hidden; draw passed fragments before active fragments, as upstream.
        for (int status = 0; status < 2; status++) for (FoliaCadenzaLayout.Placement p : line.placements) {
            boolean passed = time > p.finish;
            if ((status == 0) != passed || (animate && time < p.start - 180)) continue;
            float entry = animate ? (float) (1 - Math.exp(-14 * Math.max(0, time - p.start + 180) / 1000.0)) : 1;
            float progress = (float) FoliaRingMath.clamp((time - p.start) / (double) Math.max(1, p.finish - p.start), 0, 1);
            float colorPass = animate ? (float) FoliaRingMath.clamp((time - p.finish) / 800.0, 0, 1) : 0;
            float drift = (float) FoliaRingMath.clamp((time - p.finish) / 5000.0, 0, 1);
            drift = drift < 0.5f ? 2 * drift * drift : 1 - (float) Math.pow(-2 * drift + 2, 2) / 2;
            float pulse = animate && !passed ? 1 + (float) Math.sin(time / 1000.0 * 10 + p.start / 1000.0 * 5) * 0.04f : 1;
            float settle = animate ? (float) FoliaSceneMath.easeOut((time - p.finish) / 500.0) : 0;
            float scale = (float) p.scale * (1.3f * pulse - (1.3f * pulse - 1) * settle);
            int save = canvas.save();
            canvas.translate(width / 2 + (float) (p.x + p.width / 2 + p.driftX * drift),
                    focusY + (float) (p.y - p.height * 0.42 + p.driftY * drift));
            canvas.rotate((float) p.rotation * drift);
            canvas.scale(scale, scale);
            float opacity = entry * (passed ? 0.82f : 1);
            slice(canvas, line, p.first, p.end, 0, (float) p.height * 0.42f - line.baseline,
                    Float.MAX_VALUE, opacity, mix(primary, accent, progress * (1 - colorPass)), false, 0,
                    animate ? (float) FoliaSceneMath.cadenzaGlow(time, p.start, p.finish) * 40 * strength : 0);
            canvas.restoreToCount(save);
        }
    }

    private void article(Canvas canvas) {
        int center = Math.max(0, anchor);
        if (articleBlocks == null || articleWidth != width || articleHeight != height) {
            text.setTypeface(regularFace); text.setTextSize(32);
            float[] emWidths = new float[sourceLines.size()];
            for (int i = 0; i < sourceLines.size(); i++) emWidths[i] = text.measureText(sourceLines.get(i).text) / 32;
            articleBlocks = FoliaArticleLayout.build(sourceLines, width, height, fontScale, "Folia", emWidths);
            articleWidth = width; articleHeight = height;
        }
        FoliaArticleLayout.Block focus = articleBlocks[center];
        if (focus == null) {
            for (FoliaArticleLayout.Block block : articleBlocks) if (block != null) { focus = block; break; }
            if (focus == null) return;
            center = focus.line;
        }
        PreparedLine target = prepare(center, focus.font, width, focus.hero ? face : regularFace, 0);
        int activeGlyph = -1;
        while (activeGlyph + 1 < target.timeline.glyphs.length && target.timeline.starts[activeGlyph + 1] <= time) activeGlyph++;
        double printedProgress = activeGlyph < 0 ? 0 : activeGlyph + target.timeline.progress(activeGlyph, time);
        int[] focusRows = FoliaSceneMath.wrap(target.timeline.glyphs, target.offsets, focus.width, target.spacing);
        int row = 0, firstGlyph = 0;
        while (row < focusRows.length - 1 && printedProgress > focusRows[row]) firstGlyph = focusRows[row++];
        int glyph = Math.min(target.timeline.glyphs.length - 1, Math.max(firstGlyph, (int) printedProgress));
        float focusX = focus.x, focusY = focus.y + (row + 0.5f) * focus.lineHeight;
        if (glyph >= 0) focusX += target.offsets[glyph] - target.offsets[firstGlyph]
                + target.widths[glyph] * (printedProgress >= target.timeline.glyphs.length ? 1 : (float) (printedProgress - glyph));
        // Follow the pen into the next printed row, rather than jumping to its left edge.
        if (row < focusRows.length - 1 && printedProgress > focusRows[row] - 0.7) {
            float blend = (float) FoliaCameraMotion.ease(Math.min(1, (printedProgress - focusRows[row] + 0.7) / 0.7));
            focusX += (focus.x - focusX) * blend;
            focusY += focus.lineHeight * blend;
        } else if (row > 0 && printedProgress < firstGlyph + 0.7) {
            int previousFirst = row > 1 ? focusRows[row - 2] : 0;
            float blend = (float) FoliaCameraMotion.ease(Math.max(0, 1 - (printedProgress - firstGlyph) / 0.7));
            float previousX = focus.x + target.extent(previousFirst, firstGlyph);
            focusX += (previousX - focusX) * blend;
            focusY -= focus.lineHeight * blend;
        }
        float targetScale = (float) FoliaRingMath.clamp(FoliaRingMath.clamp(Math.min(width, height) * 0.115, 64, 124)
                / Math.max(1, focus.lineHeight), 0.88, 2.2);
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, maxX = 0, maxY = 0;
        for (FoliaArticleLayout.Block block : articleBlocks) if (block != null) {
            minX = Math.min(minX, block.x); minY = Math.min(minY, block.y);
            maxX = Math.max(maxX, block.x + block.width); maxY = Math.max(maxY, block.y + block.height);
        }
        double overviewX = (minX + maxX) / 2, overviewY = (minY + maxY) / 2;
        double overviewScale = FoliaCameraMotion.clamp(Math.min(
                width / Math.max(1, maxX - minX + 2 * FoliaCameraMotion.clamp(width * 0.2, 120, 280)),
                height / Math.max(1, maxY - minY + 2 * FoliaCameraMotion.clamp(height * 0.2, 96, 220))), 0.22, 0.72);
        boolean overview = time >= prepare(sourceLines.size() - 1, 32, width).timeline.renderEnd;
        articleCamera.step(overview ? -2 : center, overview ? overviewX : focusX,
                overview ? overviewY : focusY, overview ? overviewScale : targetScale,
                focus.x, focus.y + focus.lineHeight / 2, overviewX, overviewY, overviewScale,
                width, height, animationClock / 1000.0, frameDelta / 1000.0,
                playing, motionClock.seek || !animate, strength);
        float cameraX = (float) articleCamera.x, cameraY = (float) articleCamera.y;
        float cameraScale = (float) articleCamera.scale;
        int save = canvas.save();
        canvas.translate(width / 2, height / 2);
        canvas.scale(cameraScale, cameraScale);
        canvas.translate(-cameraX, -cameraY);
        shape.setColor(alpha(muted, 0.1f)); shape.setStrokeWidth(0.7f);
        // Paper rules and registration marks live in world space and move with the article.
        canvas.drawLine(minX, minY - 28, maxX, minY - 28, shape);
        canvas.drawLine(minX, maxY + 28, maxX, maxY + 28, shape);
        label(canvas, "FOLIA  /  " + sourceLines.size(), minX, minY - 42, 11,
                alpha(muted, 0.35f), Paint.Align.LEFT, regularFace);
        for (FoliaArticleLayout.Block block : articleBlocks) {
            if (block == null) continue;
            if ((block.x + block.width - cameraX) * cameraScale < -width / 2 - 64
                    || (block.x - cameraX) * cameraScale > width / 2 + 64
                    || (block.y + block.height - cameraY) * cameraScale < -height / 2 - 64
                    || (block.y - cameraY) * cameraScale > height / 2 + 64) continue;
            PreparedLine line = prepare(block.line, block.font, width, block.hero ? face : regularFace, 0);
            int[] rows = FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, block.width, line.spacing);
            int first = 0;
            for (int r = 0; r < rows.length; r++) {
                int end = rows[r];
                for (int g = first; g < end; g++) {
                    float printed = animate ? (float) FoliaRingMath.clamp((time - line.timeline.starts[g]) / 180.0, 0, 1) : 1;
                    float opacity = overview ? 0.72f : block.line == center ? printed : block.line < center ? 0.18f : 0.06f;
                    int glyphSave = canvas.save();
                    canvas.translate(block.x + line.offsets[g] - line.offsets[first] + line.widths[g] / 2,
                            block.y + r * block.lineHeight + block.lineHeight / 2);
                    glyph(canvas, line, g, primary, opacity, 0,
                            animate && block.line == center ? printed * 10 * strength : 0);
                    canvas.restoreToCount(glyphSave);
                }
                first = end;
            }
        }
        canvas.restoreToCount(save);
    }

    private void clockwork(Canvas canvas) {
        float radius = Math.min(width, height) * 0.42f;
        float lyricRadius = Math.min(width, height) * 0.48f;
        float cx = 0, cy = height * 0.5f;
        clockwork.colors(primary, accent);
        clockwork.draw(canvas, radius, cy, clockworkMotion, lyricRadius,
                ringOffset / Math.PI * Math.toRadians(12.5));
        float camera = (float) (ringOffset / Math.PI);
        double[] angles = new double[sourceLines.size()];
        float maxWidth = Math.max(140, Math.min(width * 0.46f, width - radius - 48)) / 1.25f;
        float[] heights = new float[sourceLines.size()];
        for (int i = Math.max(0, anchor - 9); i < Math.min(sourceLines.size(), anchor + 10); i++) {
            PreparedLine line = prepare(i, 28 * fontScale, width);
            heights[i] = FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, maxWidth, line.spacing).length * 34 * fontScale;
            if (showTranslation && !sourceLines.get(i).translated.isEmpty()) heights[i] += 27;
            heights[i] *= 1.25f;
        }
        int focal = Math.max(0, anchor);
        for (int i = focal + 1; i < Math.min(sourceLines.size(), focal + 9); i++) angles[i] = angles[i - 1]
                + Math.max(Math.toRadians(12.5), (heights[i - 1] + heights[i] + 24) / (2 * lyricRadius));
        for (int i = focal - 1; i >= Math.max(0, focal - 8); i--) angles[i] = angles[i + 1]
                - Math.max(Math.toRadians(12.5), (heights[i + 1] + heights[i] + 24) / (2 * lyricRadius));
        for (int i = Math.max(0, anchor - 8); i < Math.min(sourceLines.size(), anchor + 9); i++) {
            double angle = angles[i] + (anchor - camera) * Math.toRadians(12.5);
            if (Math.abs(angle) >= Math.PI / 2) continue;
            float x = cx + lyricRadius * (float) Math.cos(angle);
            float y = cy + lyricRadius * (float) Math.sin(angle);
            float distance = Math.abs(i - camera);
            float opacity = Math.max(0.12f, (float) Math.pow(Math.cos(angle * 0.75), 2.5) * (1 - distance * 0.18f));
            PreparedLine line = prepare(i, 28 * fontScale, width);
            float scale = i == anchor ? 1.25f : Math.max(0.7f, 1 - distance * 0.08f);
            int save = canvas.save();
            canvas.translate(x, y);
            canvas.rotate((float) Math.toDegrees(angle) * 0.35f);
            canvas.scale(scale, scale);
            int[] rows = FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, maxWidth, line.spacing);
            int first = 0;
            float top = -(rows.length - 1) * line.size * 1.2f / 2;
            for (int r = 0; r < rows.length; r++) {
                int end = rows[r];
                float extent = line.extent(first, end);
                slice(canvas, line, first, end, extent / 2, top + r * line.size * 1.2f,
                        maxWidth, opacity, primary, false, 0, 0);
                first = end;
            }
            if (showTranslation && !sourceLines.get(i).translated.isEmpty()) label(canvas,
                    sourceLines.get(i).translated, 0, top + rows.length * line.size * 1.2f + 12,
                    16 * fontScale, alpha(muted, opacity), Paint.Align.LEFT);
            canvas.restoreToCount(save);
        }
    }

    private void conversation(Canvas canvas, MusicSnapshot snapshot) {
        Bitmap cover = snapshot.albumArt;
        int center = Math.max(0, anchor);
        float base = Math.max(15, Math.min(26, 18 * fontScale));
        float panelWidth = Math.min(width - 64, 896);
        float maxTextWidth = Math.max(96, (Math.min(width - 32, 896)) * (width >= 640 ? 0.68f : 0.78f) - 56);
        float usableHeight = Math.max(200, height - 240);
        int first = center;
        float totalHeight = 0;
        for (int i = center; i >= Math.max(0, center - 19); i--) {
            PreparedLine line = prepare(i, base * (i == center ? 1.34f : 0.94f), width);
            float block = Math.max(i == center ? 64 : 44,
                    FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, maxTextWidth, line.spacing).length * line.size * 1.45f
                            + (i == center ? 32 : 24)) + 12;
            if (totalHeight + block > usableHeight && center - first >= 1) break;
            totalHeight += block;
            first = i;
        }
        float y = width >= 1024 ? 80 : width >= 640 ? 64 : 48;
        if (first == 0 && !snapshot.title.isEmpty()) {
            text.setTypeface(regularFace);
            text.setTextSize(base);
            float bubbleWidth = Math.max(base, text.measureText(snapshot.title)) + 34;
            float x = (width + panelWidth) / 2 - bubbleWidth - 48;
            float bubbleHeight = (float) Math.ceil(base * 1.45f + 26);
            shape.setColor(alpha(mix(accent, primary, 0.18f), 0.94f));
            rect.set(x, y, x + bubbleWidth, y + bubbleHeight);
            canvas.drawRoundRect(rect, 20, 20, shape);
            label(canvas, snapshot.title, x + 16, y + 13 + base * 1.04f, base, 0xFF09090B, Paint.Align.LEFT);
            chatAvatar(canvas, cover, (width + panelWidth) / 2 - 20, y + 20, 8, 1);
            y += bubbleHeight + 12;
        }
        for (int i = first; i <= center; i++) {
            float size = base * (i == center ? 1.34f : 0.94f);
            PreparedLine line = prepare(i, size, width);
            boolean right = chatSides[i];
            int visible = line.timeline.glyphs.length;
            if (i == center && animate) {
                visible = 0;
                while (visible < line.timeline.starts.length && time + 200 >= line.timeline.starts[visible]) visible++;
                visible = Math.max(1, visible);
            }
            float paddingX = i == center ? 20 : 16, paddingY = i == center ? 16 : 12;
            int[] rows = FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, maxTextWidth, line.spacing);
            float extent = 0;
            int start = 0, visibleRows = 0;
            for (int end : rows) {
                if (start >= visible) break;
                extent = Math.max(extent, line.extent(start, Math.min(end, visible)));
                start = end; visibleRows++;
            }
            float bubbleWidth = Math.max(size, extent) + paddingX * 2 + 2;
            float bubbleHeight = Math.max(i == center ? 64 : 44, Math.max(1, visibleRows) * size * 1.45f + paddingY * 2 + 2);
            float x = right ? (width + panelWidth) / 2 - bubbleWidth - 48 : (width - panelWidth) / 2 + 48;
            float entry = i == center && animate ? (float) FoliaSceneMath.easeOut((time - sourceLines.get(i).timeMs) / 320.0) : 1;
            float opacity = i == center ? 1 : 0.82f;
            int bubbleColor = right ? alpha(mix(accent, primary, 0.18f), 0.94f)
                    : mix(muted, accent, 0.18f + (i % 5) * 0.0775f);
            int lyricColor = right ? 0xFF09090B : primary;
            if (i == anchor ? overrideCurrentColor : overrideInactiveColor) {
                lyricColor = CoverLyricColorRules.color(i == anchor ? accent : muted, bubbleColor, i == anchor);
            }
            shape.setColor(alpha(bubbleColor, opacity));
            rect.set(x, y, x + bubbleWidth, y + bubbleHeight);
            FoliaCameraMotion.Spring position = chatPositions.get(i);
            if (position == null) { position = new FoliaCameraMotion.Spring(); chatPositions.put(i, position); }
            if (motionClock.seek || !animate) position.snap(y);
            float motionY = (float) position.step(y, frameDelta / 1000.0, 340, 28, 0.72);
            int save = canvas.save();
            canvas.translate(0, motionY - y + (1 - entry) * 22);
            canvas.scale(0.98f + 0.02f * entry, 0.98f + 0.02f * entry, right ? x + bubbleWidth : x, y);
            canvas.drawRoundRect(rect, 20, 20, shape);
            int clipping = canvas.save();
            canvas.clipRect(rect);
            start = 0;
            for (int r = 0; r < rows.length; r++) {
                int end = rows[r];
                float rowWidth = line.extent(start, end);
                for (int g = start; g < end; g++) {
                    float reveal = i != center || !animate ? 1 : (float) FoliaRingMath.clamp(
                            (time - line.timeline.starts[g]) / Math.max(40, Math.min(220, line.timeline.ends[g] - line.timeline.starts[g])), 0, 1);
                    int glyphSave = canvas.save();
                    canvas.translate(x + paddingX + line.offsets[g] - line.offsets[start] + line.widths[g] / 2,
                            y + paddingY + size * 0.725f + r * size * 1.45f);
                    glyph(canvas, line, g, lyricColor, opacity * reveal, 0, 0, true);
                    canvas.restoreToCount(glyphSave);
                }
                start = end;
            }
            canvas.restoreToCount(clipping);
            canvas.restoreToCount(save);
            float avatarX = right ? (width + panelWidth) / 2 - 20 : (width - panelWidth) / 2 + 20;
            float avatarY = motionY + 20;
            chatAvatar(canvas, cover, avatarX, avatarY, chatSenders[i] == 8 ? 8 : new int[]{0, 3, 6, 1, 4}[chatSenders[i]], opacity);
            if (i < center || time >= line.timeline.renderEnd - 340) label(canvas,
                    String.format(java.util.Locale.ROOT, "%d:%02d", sourceLines.get(i).timeMs / 60000,
                            sourceLines.get(i).timeMs / 1000 % 60), right ? x : x + bubbleWidth,
                    motionY + bubbleHeight + 13, 10, alpha(muted, 0.6f), right ? Paint.Align.LEFT : Paint.Align.RIGHT);
            y += bubbleHeight + 12;
        }
    }

    private void chatAvatar(Canvas canvas, Bitmap cover, float avatarX, float avatarY, int tile, float opacity) {
            if (cover != null && !cover.isRecycled()) {
                path.reset();
                path.addRoundRect(new RectF(avatarX - 20, avatarY - 20, avatarX + 20, avatarY + 20), 12, 12, Path.Direction.CW);
                int clip = canvas.save();
                canvas.clipPath(path);
                rect.set(avatarX - 20, avatarY - 20, avatarX + 20, avatarY + 20);
                source.set(cover.getWidth() * (tile % 3) / 3, cover.getHeight() * (tile / 3) / 3,
                        cover.getWidth() * (tile % 3 + 1) / 3, cover.getHeight() * (tile / 3 + 1) / 3);
                shape.setColor(Color.WHITE);
                canvas.drawBitmap(cover, source, rect, shape);
                canvas.restoreToCount(clip);
            } else {
                shape.setColor(alpha(accent, opacity * 0.5f));
                canvas.drawCircle(avatarX, avatarY, 20, shape);
                label(canvas, tile == 8 ? "♪" : "♫", avatarX, avatarY + 7, 22, primary, Paint.Align.CENTER);
            }
    }

    private void portrait(Canvas canvas, MusicSnapshot snapshot) {
        boolean hasPortrait = width >= 768;
        float column = hasPortrait ? Math.max(220, Math.min(width * 0.28f, 430)) : 0;
        float artSize = hasPortrait ? Math.min(Math.max(210, Math.min(width * 0.26f, 380)), column - 44) * 1.35135f : 0;
        float left = width - 32 - artSize;
        float top = (height - artSize) / 2;
        if (hasPortrait && snapshot.albumArt != null && !snapshot.albumArt.isRecycled()) {
            source.set(0, 0, snapshot.albumArt.getWidth(), snapshot.albumArt.getHeight());
            rect.set(left, top, left + artSize, top + artSize);
            shape.setColor(Color.WHITE);
            int coverClip = canvas.save();
            path.reset(); path.addRoundRect(rect, 32, 32, Path.Direction.CW); canvas.clipPath(path);
            canvas.drawBitmap(snapshot.albumArt, source, rect, shape);
            canvas.restoreToCount(coverClip);
            shape.setColor(0xFF09090B);
            rect.set(left + artSize - 44, top - 12, left + artSize - 32, top + 44);
            canvas.drawRoundRect(rect, 6, 6, shape);
        }
        float margin = width >= 1024 ? 56 : width >= 640 ? 32 : 20;
        float lyricWidth = Math.max(1, width - column - margin * 2);
        float x = margin;
        float headerTop = Math.max(24, height / 2 - 236);
        label(canvas, snapshot.artist, x, headerTop,
                FoliaSceneMath.clampFont(width, 16, 1.8f, 28.8f, 1), alpha(primary, 0.96f), Paint.Align.LEFT, italicFace);
        shape.setShader(new LinearGradient(0, headerTop + 10, 0, headerTop + 66,
                alpha(primary, 0.72f), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(x, headerTop + 10, x + 1, headerTop + 66, shape);
        shape.setShader(null);
        text.setTypeface(face);
        text.setTextSize(FoliaSceneMath.clampFont(width, 23.2f, 3.3f, 44.8f, 1));
        float titleSize = text.getTextSize() * Math.min(1, lyricWidth / Math.max(1, text.measureText(snapshot.title)));
        label(canvas, snapshot.title, x, headerTop + 94, titleSize, primary, Paint.Align.LEFT);
        int center = Math.max(0, anchor);
        for (int i = Math.max(0, center - 2); i < Math.min(sourceLines.size(), center + 3); i++) {
            float size = FoliaSceneMath.clampFont(width, i == center ? 21.44f : 17.28f,
                    i == center ? 2.75f : 2, i == center ? 36.48f : 23.68f, 1.2f * fontScale);
            PreparedLine line = prepare(i, size, width, i == center ? face : regularFace, 0);
            float opacity = i == center ? 1 : i < center ? 0.45f : 0.24f;
            float y = headerTop + 270 + (i - (float) (ringOffset / Math.PI)) * 64
                    + (i > center && showTranslation && !sourceLines.get(center).translated.isEmpty() ? 32 : 0);
            int clip = canvas.save();
            canvas.clipRect(x, headerTop + 136, x + lyricWidth, height - 36);
            slice(canvas, line, 0, line.timeline.glyphs.length, x + Math.min(line.total, lyricWidth) / 2, y,
                    lyricWidth, opacity, primary, false, 0, i == center ? 6 * strength : 0);
            if (i == center && showTranslation) label(canvas, sourceLines.get(i).translated, x, y + 42,
                    FoliaSceneMath.clampFont(width, 15.04f, 1.28f, 18.24f, 1.2f * fontScale),
                    alpha(muted, 0.84f), Paint.Align.LEFT);
            canvas.restoreToCount(clip);
        }
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
        FoliaOriginalGraphemeTiming ringTiming;
        final Typeface font;
        final float size, baseline;
        float spacing;
        float extent(int first, int end) { return FoliaTextSpacing.extent(offsets, first, end, spacing); }
        final float[] widths, offsets;
        final double[] angles;
        int[] semanticEnds;
        List<FoliaSceneMath.PartitaChunk> chunks;
        List<FoliaCadenzaLayout.Placement> placements;
        float total;
        double arcScale;

        final int index;
        PreparedLine(int index, FoliaGlyphTimeline timeline, Typeface font, float size, float baseline) {
            this.index = index;
            this.timeline = timeline;
            this.font = font;
            this.size = size;
            this.baseline = baseline;
            widths = new float[timeline.glyphs.length];
            offsets = new float[timeline.glyphs.length + 1];
            angles = new double[timeline.glyphs.length];
        }
    }
}
