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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/** Independent Canvas renderer: the old Folia renderer remains the low-resource edition. */
final class FoliaOriginalCanvasRenderer {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint raster = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.LINEAR_TEXT_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint.FontMetrics metrics = new Paint.FontMetrics();
    private final FoliaGpuText gpuText = new FoliaGpuText();
    private final HashMap<String, Bitmap> emos = new HashMap<>();
    private android.content.res.AssetManager assets;
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
    private FoliaArticleLayout.Article articleArticle;
    private FoliaFumeBackground.Scene articleBackground;
    private String articleSeed = "";
    private String themeName = "Folia";
    private String geometrySeed = "";
    private float articleWidth, articleHeight;
    /** Debug-only switch used by the fixture activity to dump fume layout numbers. */
    static boolean debugLayout;
    private List<LrcTimeline.Line> sourceLines = Collections.emptyList();
    private String track = "";
    private String mode = "claddagh";
    private Typeface face;
    private Typeface regularFace, lightFace, italicFace, mediumFace;
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
    /** 还原版自定义项：背景元素强度、逐字旋转、歌词浮动幅度、逐字间距、字幕字号、歌曲信息。 */
    private float backgroundOpacity = 1f;
    private boolean wordRotation = true;
    private float floatMultiplier = 1f;
    private float wordSpacingScale = 1f;
    private float subtitleScale = 1f;
    private boolean showHeader = true;
    private boolean playing;
    private long animationClock, frameDelta;
    private int outgoingAnchor = -1;
    private float containerBlur;

    void setAssets(android.content.res.AssetManager assets) {
        this.assets = assets;
        tempera.setAssets(assets);
        sonnet.setAssets(assets);
    }

    void setGeometrySeed(String seed) {
        geometrySeed = seed == null ? "" : seed;
        tempera.setGeometrySeed(seed);
        sonnet.setGeometrySeed(seed);
        lumiere.setGeometrySeed(seed);
        diorama.setGeometrySeed(seed);
    }

    void setThemeName(String name) {
        themeName = name == null || name.isEmpty() ? "Folia" : name;
    }

    void configureNextLine(boolean show, float scale) {
        showNextLine = show;
        nextLineScale = Math.max(0.5f, Math.min(4, scale));
    }

    /**
     * Folia 还原版自定义项。数值都用百分比表达，越界值在这里收敛，避免设置页与渲染器各算一半。
     */
    void setOriginalOptions(int background, boolean rotation, int floating, int spacing,
                            int subtitle, boolean header) {
        backgroundOpacity = Math.max(0f, Math.min(1.5f, background / 100f));
        wordRotation = rotation;
        floatMultiplier = Math.max(0f, Math.min(2f, floating / 100f));
        wordSpacingScale = Math.max(0.5f, Math.min(2f, spacing / 100f));
        subtitleScale = Math.max(0.5f, Math.min(2f, subtitle / 100f));
        showHeader = header;
        lumiere.setSubtitleScale(subtitleScale);
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
        mediumFace = face == null ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : face;
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
        if (!"monet".equals(mode) && !"lumiere".equals(mode) && !"cappella".equals(mode)
                && !"still".equals(mode) && !"pendolo".equals(mode) && !"fume".equals(mode)) subtitle(canvas);
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
                // CJK glyphs sit ~0.07 em below the Latin baseline in the reference rendering.
                if (isCjkGlyph(line.timeline.glyphs[glyph])) canvas.translate(0, size * 0.07f);
                glyph(canvas, line, glyph, color, opacity, Math.round((float) pose.blur * 100) / 100f, glow);
                canvas.restoreToCount(save);
            }
        }
    }

    private static boolean isCjkGlyph(String glyph) {
        if (glyph.isEmpty()) return false;
        char code = glyph.charAt(0);
        return code >= 0x4e00 && code <= 0x9fa5 || code >= 0x3040 && code <= 0x30ff
                || code >= 0xac00 && code <= 0xd7af;
    }

    private static boolean hasCjk(String text) {
        if (text == null) return false;
        for (int i = 0; i < text.length(); i++) {
            char code = text.charAt(i);
            if (code >= 0x4e00 && code <= 0x9fa5 || code >= 0x3040 && code <= 0x30ff
                    || code >= 0xac00 && code <= 0xd7af) return true;
        }
        return false;
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
            if ("claddagh".equals(mode)) {
                float fade = (float) FoliaRingMath.clamp((glow - 0.5f) / 2, 0, 1);
                drawSoftMask(canvas, line, index, color, opacity * fade,
                        (float) Math.hypot(glow * 0.5, blur), 1);
            } else if ("cadenza".equals(mode)) {
                // CSS blur radius 40px -> Gaussian sigma 20; three stacked shadows alpha 1 / 0.92 / 0.26.
                float intensity = (float) FoliaRingMath.clamp(glow / 40f, 0, 1) * opacity;
                drawSoftMask(canvas, line, index, color, intensity, 20f, 1);
                drawSoftMask(canvas, line, index, color, intensity * 0.92f, 20f, 2);
                drawSoftMask(canvas, line, index, color, intensity * 0.26f, 20f, 3);
            } else {
                // GlowWord text-shadow: two layers, CSS blur 20 / 40px (sigma 10 / 20), scaled on
                // screens wider than 1536px. The halo carries the word's active colour at full alpha.
                float large = width <= 1536 ? 1
                        : width >= 2200 ? 1.5f : 1 + 0.5f * (width - 1536) / 664f;
                float intensity = (float) FoliaRingMath.clamp(glow / 18f, 0, 1) * opacity;
                drawSoftMask(canvas, line, index, accent, intensity * 0.95f, 10 * large, 1);
                drawSoftMask(canvas, line, index, accent, intensity * 0.88f, 20 * large, 2);
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
            if ("claddagh".equals(mode)) {
                claddaghSubtitle(canvas, value);
            } else if ("classic".equals(mode)) {
                typographySubtitle(canvas, value, FoliaSceneMath.clampFont(width, 18, 2.6f, 20, fontScale) * subtitleScale, mediumFace);
            } else if ("partita".equals(mode)) {
                typographySubtitle(canvas, value, FoliaSceneMath.clampFont(width, 16.8f, 2.2f, 19.2f, fontScale) * subtitleScale, mediumFace);
            } else if ("tilt".equals(mode) || "cadenza".equals(mode)) {
                typographySubtitle(canvas, value, FoliaSceneMath.clampFont(width, 18, 2.6f, 20, fontScale) * subtitleScale, mediumFace);
            } else {
                float size = FoliaSceneMath.clampFont(width, 18, 2.6f, 20, fontScale) * subtitleScale;
                text.setTypeface(face);
                text.setTextSize(size);
                size *= Math.min(1, width * 0.84f / Math.max(1, text.measureText(value)));
                label(canvas, value, width / 2, height - 32 - size * 0.25f, size, alpha(muted, 0.6f), Paint.Align.CENTER, regularFace);
            }
        }
        if (showNextLine && (!showTranslation || value.isEmpty()) && anchor + 1 < sourceLines.size()) {
            PreparedLine next = prepare(anchor + 1,
                    Math.max(14, Math.min(width * 0.02f, 16)) * nextLineScale, width, regularFace, 0);
            slice(canvas, next, 0, next.timeline.glyphs.length, width / 2,
                    height - 36, width * 0.84f, 0.6f, muted, false, 0, 0);
        }
    }

    /**
     * Shared upstream subtitle overlay for the typography modes: bottom = 32px plus the 2px box
     * padding, line-height 1.5, weight 500, secondary colour and a 0 1px 2px background shadow.
     */
    private void typographySubtitle(Canvas canvas, String value, float size, Typeface typeface) {
        float lineHeight = size * 1.5f;
        float top = height - 34 - lineHeight;
        text.setTypeface(typeface);
        text.setTextSize(Math.max(1, size));
        text.getFontMetrics(metrics);
        float baseline = top + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
        text.setShader(null);
        text.setShadowLayer(1, 0, 2, alpha(0x09090b, 0.24f));
        label(canvas, value, width / 2, baseline, size, alpha(muted, 0.62f), Paint.Align.CENTER, typeface);
        text.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
    }

    /**
     * Upstream shared subtitle overlay with the player chrome hidden: bottom = 32 + 80 = 112 px,
     * font-size clamp(1.1rem, 2.2vw, 1.45rem), font-weight 500.
     */
    private void claddaghSubtitle(Canvas canvas, String value) {
        float size = Math.max(17.6f, Math.min(width * 0.022f, 23.2f)) * fontScale * subtitleScale;
        float lineHeight = size * 1.5f;
        float top = height - 112f - 2f - lineHeight;
        text.setTypeface(mediumFace);
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(alpha(muted, 0.6f));
        text.setShader(null);
        text.setShadowLayer(2f, 0f, 1f, alpha(0x09090b, 0.24f));
        text.getFontMetrics(metrics);
        float baseline = top + (lineHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent;
        canvas.drawText(value, width / 2f, baseline, text);
        text.clearShadowLayer();
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
            float distance = (strength < 0.34f ? 10 : strength > 0.66f ? 18 : 14) * floatMultiplier;
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
        LrcTimeline.Line source = sourceLines.get(index);
        double lineSeed = source.timeMs / 1000.0;
        int charCount = FoliaGlyphTimeline.split(source.text).length;
        int rows = tiltLineCount(charCount, lineSeed);
        float size = FoliaSceneMath.clampFont(width, 50, 6.875f, 90, fontScale);
        PreparedLine normal = prepare(index, size, width, regularFace, 0.08f);
        PreparedLine italic = prepare(index, size, width, italicFace, 0.15f);
        // Upstream splits the line into sentence segments; for CJK fixtures this is an even split
        // over the non-space glyphs (9 chars -> 4 + 4 with the inter-word space dropped).
        List<Integer> glyphs = new ArrayList<>();
        for (int g = 0; g < normal.timeline.glyphs.length; g++) {
            if (!normal.timeline.glyphs[g].trim().isEmpty()) glyphs.add(g);
        }
        if (glyphs.isEmpty()) {
            rows = 1;
        } else {
            rows = Math.max(1, Math.min(rows, glyphs.size()));
        }
        List<int[]> segments = new ArrayList<>();
        if (glyphs.isEmpty()) {
            segments.add(new int[]{0, Math.max(0, normal.timeline.glyphs.length - 1)});
        } else if (rows <= 1) {
            segments.add(new int[]{glyphs.get(0), glyphs.get(glyphs.size() - 1)});
        } else {
            int base = glyphs.size() / rows;
            int remainder = glyphs.size() % rows;
            int cursor = 0;
            for (int r = 0; r < rows; r++) {
                int length = base + (r < remainder ? 1 : 0);
                if (length <= 0) continue;
                int first = glyphs.get(cursor), last = glyphs.get(cursor + length - 1);
                segments.add(new int[]{first, last});
                cursor += length;
            }
        }
        // Italic accent row: candidates where seededRandom(seed, 100 + row) < 0.35, then pick one.
        int tilted = -1;
        List<Integer> candidates = new ArrayList<>();
        for (int r = 0; r < segments.size(); r++) {
            if (tiltSeededRandom(lineSeed, 100 + r) < 0.35) candidates.add(r);
        }
        if (!candidates.isEmpty()) {
            tilted = candidates.get((int) Math.floor(tiltSeededRandom(lineSeed, 200) * candidates.size()) % candidates.size());
        }
        float gap = width < 640 ? 12 : 16;
        float contentTop = height * 0.15f;
        float innerTop = contentTop + 32;
        float innerHeight = height * 0.70f - 64;
        float total = 0;
        for (int r = 0; r < segments.size(); r++) {
            total += (r == tilted ? 1.25f : 1.35f) * size;
        }
        total += gap * (segments.size() - 1);
        float rowTop = innerTop + (innerHeight - total) / 2;
        for (int r = 0; r < segments.size(); r++) {
            int[] segment = segments.get(r);
            int first = segment[0];
            int last = segment[1];
            PreparedLine line = r == tilted ? italic : normal;
            float rowHeight = (r == tilted ? 1.25f : 1.35f) * size;
            float centerY = rowTop + rowHeight / 2;
            boolean visible = line.timeline.starts[first] <= time + 250;
            if (!visible) {
                rowTop += rowHeight + gap;
                continue;
            }
            float extent = line.offsets[last] - line.offsets[first] + line.widths[last];
            float rowWidth = extent + line.spacing;
            float left = (width - rowWidth) / 2;
            float entry = animate ? (float) FoliaSceneMath.easeOut(
                    (time - line.timeline.starts[first] + 250) / (r == tilted ? 600.0 : 550.0)) : 1;
            int save = canvas.save();
            canvas.translate(0, centerY + size * 0.036f + (1 - entry) * (r == tilted ? 24 : 20));
            int visual = 0;
            for (int i = first; i <= last; i++) {
                if (line.timeline.glyphs[i].trim().isEmpty()) continue;
                float reveal = animate ? (float) FoliaRingMath.clamp(
                        (time - line.timeline.starts[first] + 250 - visual * (r == tilted ? 50 : 40)) / 500.0, 0, 1) : 1;
                if (reveal <= 0) continue;
                float duration = (float) FoliaRingMath.clamp(line.timeline.ends[i] - line.timeline.starts[i], 200, 900);
                double elapsed = time - line.timeline.starts[i];
                double intensity;
                if (elapsed < 0) {
                    intensity = 0;
                } else if (elapsed <= duration) {
                    intensity = Math.sin(Math.PI * elapsed / duration);
                } else {
                    double after = duration * 1.2;
                    intensity = elapsed - duration < after ? 0.25 * (elapsed - duration) / after : 0.25;
                }
                float scale = (float) (1 + intensity * (r == tilted ? 0.18 : 0.15));
                int glyphSave = canvas.save();
                canvas.translate(left + line.offsets[i] - line.offsets[first] + line.widths[i] / 2,
                        r == tilted ? (visual % 2 == 0 ? -1 : 1) * size / 6 * (2 - reveal) : 0);
                canvas.scale(scale, scale);
                glyph(canvas, line, i, r == tilted ? accent : primary, reveal * entry, 0, 0);
                canvas.restoreToCount(glyphSave);
                visual++;
            }
            canvas.restoreToCount(save);
            rowTop += rowHeight + gap;
        }
    }

    /** Rows used by the upstream tilt layout (1..4) from the char count and line seed. */
    private int tiltLineCount(int charCount, double seed) {
        double normalized = Math.log(charCount + 4) / Math.log(24);
        double jitter = tiltSeededRandom(seed, 1) * 0.6 + 0.7;
        double score = normalized * jitter * 0.75;
        if (score < 0.45) return 1;
        if (score < 1.05) return 2;
        if (score < 1.7) return 3;
        return 4;
    }

    private double tiltSeededRandom(double seed, double offset) {
        return FoliaSceneMath.random(seed * 1000 + offset);
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
            float margin = Math.max(size * 0.12f * 0.7f * wordSpacingScale,
                    (extent * (s * 1.4f - 1) / 2 + nextWidth * (nextScale * 1.4f - 1) / 2 + x - nextX
                            + size * 0.05f) * 0.7f * wordSpacingScale);
            advances[w] = extent + Math.round(margin * 10) / 10f;
            first = words[w];
        }
        int rowFirst = 0, glyphFirst = 0;
        int rowCount = 1;
        float used = 0;
        for (float advance : advances) { if (used > 0 && used + advance > available) { rowCount++; used = 0; } used += advance; }
        float rowStep = size * 1.22f;
        // content-center in the 70vh container: the row block is centred on the viewport.
        float y = height / 2 - rowCount * rowStep / 2 + rowStep / 2;
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
                float passedRotate = (float) (FoliaSceneMath.random(wordSeed + 8) - 0.5) * 45;
                float wordWidth = line.extent(glyphFirst, end);
                long start = line.timeline.starts[Math.min(glyphFirst, line.timeline.starts.length - 1)];
                long finish = line.timeline.ends[end - 1];
                float entered = animate ? (float) FoliaSceneMath.spring(Math.max(0, time - start + 150) / 1000.0, 200, 20, 1) : 1;
                float passedFast = animate ? (float) FoliaSceneMath.easeOut((time - finish) / 500.0) : 0;
                // The rotate delta uses its own 5s linear transition, much slower than the other fields.
                float passedLinear = animate ? (float) FoliaRingMath.clamp((time - finish) / 5000.0, 0, 1) : 0;
                float displayDuration = Math.max(100, finish - start);
                int wordColor;
                if (!animate) {
                    wordColor = accent;
                } else if (time < start - 150) {
                    wordColor = primary;
                } else if (time <= finish) {
                    wordColor = mix(primary, accent,
                            (float) FoliaRingMath.clamp((time - start) / (double) displayDuration, 0, 1));
                } else {
                    float fade = (float) FoliaRingMath.clamp((time - finish) / 800.0, 0, 1);
                    wordColor = mix(accent, primary, fade * fade * (3 - 2 * fade));
                }
                float scale = 0.5f + (baseScale * 1.4f - 0.5f) * entered;
                scale += (baseScale - baseScale * 1.4f) * passedFast;
                float opacity = animate ? (float) FoliaRingMath.clamp((time - start + 150) / 100.0, 0, 1) * (1 - 0.18f * passedFast) : 1;
                int save = canvas.save();
                canvas.translate(left + wordWidth / 2 + x + (1 - entered) * (float) Math.sin(dy) * 100,
                        y + dy + (1 - entered) * (float) Math.cos(x) * 50);
                canvas.rotate(wordRotation ? rotation + (1 - entered) * 20 + passedLinear * passedRotate : 0);
                canvas.scale(scale, scale);
                slice(canvas, line, glyphFirst, end, 0, 0, Float.MAX_VALUE, opacity,
                        wordColor, false, 0, animate ? (1 - passedFast) * 18 : 0);
                canvas.restoreToCount(save);
                left += advances[w];
                glyphFirst = end;
            }
            y += rowStep;
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
        float chunkHeight = line.size * 1.22f + 6.4f;
        float marginBottom = 9.6f;
        float step = chunkHeight + marginBottom;
        // The upstream column centres the whole block group; the trailing margin participates too.
        float groupOffset = -marginBottom / 2;
        for (int r = 0; r < line.chunks.size(); r++) {
            FoliaSceneMath.PartitaChunk chunk = line.chunks.get(r);
            int first = chunk.first == 0 ? 0 : words[chunk.first - 1], end = words[chunk.end - 1];
            long start = line.timeline.starts[first], finish = line.timeline.ends[end - 1];
            float entry = animate ? (float) FoliaSceneMath.spring(Math.max(0, time - start + 150) / 1000.0, 200, 20, 1) : 1;
            float opacity = animate ? (float) FoliaRingMath.clamp((time - start + 150) / 100.0, 0, 1) : 1;
            float extent = line.extent(first, end) + (chunk.end - chunk.first) * 12.8f + 16;
            int save = canvas.save();
            canvas.translate(width / 2 + chunk.x + (1 - entry) * (r % 2 == 0 ? -40 : 40),
                    height / 2 - line.chunks.size() * step / 2 + r * step + chunkHeight / 2
                            + groupOffset + chunk.y);
            canvas.scale(0.85f + 0.15f * entry, 0.85f + 0.15f * entry);
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
                        animate ? 20 * (1 - colorPass) : 0);
                canvas.restoreToCount(wordSave);
                left += wordWidth + 12.8f; glyphFirst = glyphEnd;
            }
            boolean active = time <= finish;
            int guideColor = active ? accent : (time > finish ? alpha(Color.WHITE, 0.22f) : alpha(Color.WHITE, 0.14f));
            float guideOpacity = opacity;
            shape.setColor(alpha(guideColor, guideOpacity));
            shape.setStrokeWidth(1);
            float chunkWidth = extent;
            float vX = (r % 2 == 0 ? -1 : 1) * (chunkWidth / 2 + 8);
            float vTop = chunkHeight / 2 + 16 - 32;
            float vBottom = chunkHeight / 2 + 16;
            canvas.drawRect(vX, vTop, vX + 1, vBottom, shape);
            float hY = chunkHeight / 2 + 8;
            canvas.drawRect(-(chunkWidth / 2 + 16), hY, chunkWidth / 2 + 20, hY + 1, shape);
            if (active) {
                // Upstream active guides carry a boxShadow-ish halo (0 0 10px).
                shape.setColor(alpha(accent, 0.20f * guideOpacity));
                shape.setStrokeWidth(3);
                canvas.drawRect(vX - 1, vTop, vX + 2, vBottom, shape);
                canvas.drawRect(-(chunkWidth / 2 + 16), hY - 1, chunkWidth / 2 + 20, hY + 2, shape);
                shape.setStrokeWidth(1);
            }
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
            // The first frame starts 16 degrees past the resting rotation and eases in.
            canvas.rotate((float) p.rotation * drift + (1 - entry) * 16);
            canvas.scale(scale, scale);
            float opacity = entry * (passed ? 0.82f : 1);
            slice(canvas, line, p.first, p.end, 0, (float) p.height * 0.42f - line.baseline,
                    Float.MAX_VALUE, opacity, mix(primary, accent, progress * (1 - colorPass)), false, 0,
                    animate ? (float) FoliaSceneMath.cadenzaGlow(time, p.start, p.finish) * 40 : 0);
            canvas.restoreToCount(save);
        }
    }

    private void article(Canvas canvas) {
        int center = Math.max(0, anchor);
        String layoutSeed = themeName.isEmpty() ? "Folia" : themeName;
        if (articleArticle == null || articleWidth != width || articleHeight != height
                || !layoutSeed.equals(articleSeed)) {
            articleArticle = FoliaArticleLayout.build(sourceLines, width, height, fontScale, layoutSeed,
                    new FoliaArticleLayout.Measure() {
                        @Override
                        public int lineCount(String value, float fontSize, float innerWidth) {
                            text.setTypeface(regularFace);
                            text.setTextSize(Math.max(1f, fontSize));
                            return Math.max(1, (int) Math.ceil(text.measureText(value)
                                    / Math.max(1f, innerWidth)));
                        }
                    });
            articleBlocks = articleArticle == null ? null : articleArticle.blocks;
            articleSeed = layoutSeed;
            articleWidth = width;
            articleHeight = height;
            String identitySeed = geometrySeed.isEmpty() ? "Folia" : geometrySeed;
            articleBackground = articleArticle == null ? null : FoliaFumeBackground.build(
                    width, height, articleArticle.width, articleArticle.height,
                    articleArticle.paperLeft, articleArticle.paperTop,
                    articleArticle.paperRight, articleArticle.paperBottom,
                    identitySeed + ':' + layoutSeed);
            if (debugLayout && articleBackground != null) {
                for (int s = 0; s < articleBackground.shapes.length; s++) {
                    FoliaFumeBackground.Shape shape = articleBackground.shapes[s];
                    android.util.Log.i("FoliaFixture", "fume bg " + s + " kind=" + shape.kind
                            + " x=" + shape.x + " y=" + shape.y + " w=" + shape.width
                            + " rot=" + shape.rotation + " op=" + shape.opacity
                            + " accent=" + shape.accent + " depth=" + shape.depth);
                }
            }
            articleCamera.clear();
        }
        if (articleArticle == null || articleBlocks == null) return;
        if (debugLayout) {
            for (FoliaArticleLayout.Block debugBlock : articleBlocks) {
                if (debugBlock != null) {
                    android.util.Log.i("FoliaFixture", "fume block line=" + debugBlock.line
                            + " hero=" + debugBlock.hero + " x=" + debugBlock.x + " y=" + debugBlock.y
                            + " w=" + debugBlock.width + " h=" + debugBlock.height
                            + " font=" + debugBlock.font + " lh=" + debugBlock.lineHeight);
                }
            }
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
        articleCamera.ensureInitialized(articleArticle.width / 2f, articleArticle.height / 2f);
        articleCamera.step(overview ? -2 : center, overview ? overviewX : focusX,
                overview ? overviewY : focusY, overview ? overviewScale : targetScale,
                focus.x, focus.y + focus.lineHeight / 2, overviewX, overviewY, overviewScale,
                width, height, animationClock / 1000.0, frameDelta / 1000.0,
                playing, motionClock.seek, strength);
        articleCamera.x = FoliaCameraMotion.clamp(articleCamera.x, 0, articleArticle.width);
        articleCamera.y = FoliaCameraMotion.clamp(articleCamera.y, 0, articleArticle.height);
        if (debugLayout) {
            android.util.Log.i("FoliaFixture", "fume camera x=" + articleCamera.x + " y="
                    + articleCamera.y + " scale=" + articleCamera.scale + " focus=" + focus.line
                    + " hero=" + focus.hero + " article=" + articleArticle.width + "x"
                    + articleArticle.height + " paper=" + articleArticle.paperLeft + ','
                    + articleArticle.paperTop + ',' + articleArticle.paperRight + ','
                    + articleArticle.paperBottom);
        }
        float cameraX = (float) articleCamera.x, cameraY = (float) articleCamera.y;
        float cameraScale = (float) articleCamera.scale;
        if (articleBackground != null) {
            float backgroundX = (float) FoliaCameraMotion.mix(articleArticle.width / 2f, cameraX, 0.9);
            float backgroundY = (float) (FoliaCameraMotion.mix(articleArticle.height / 2f, cameraY, 0.74)
                    - FoliaCameraMotion.clamp(height * 0.22f / Math.max(cameraScale, 0.001f), 48, 180));
            float backgroundScale = (float) FoliaCameraMotion.clamp(cameraScale * 0.94f, 0.22, 2.24);
            FoliaFumeBackground.draw(canvas, articleBackground, shape, time / 1000f,
                    width, height, backgroundScale, backgroundX, backgroundY,
                    articleArticle.width / 2f, articleArticle.height / 2f, 0.72f, accent, muted,
                    backgroundOpacity);
        }
        int save = canvas.save();
        canvas.translate(width / 2, height / 2);
        canvas.scale(cameraScale, cameraScale);
        canvas.translate(-cameraX, -cameraY);
        for (FoliaArticleLayout.Block block : articleBlocks) {
            if (block == null) continue;
            if ((block.x + block.width - cameraX) * cameraScale < -width / 2 - 96
                    || (block.x - cameraX) * cameraScale > width / 2 + 96
                    || (block.y + block.height - cameraY) * cameraScale < -height / 2 - 96
                    || (block.y - cameraY) * cameraScale > height / 2 + 96) continue;
            drawArticleBlock(canvas, block, center);
        }
        canvas.restoreToCount(save);
        if (focus != null) articleSubtitle(canvas, focus);
    }

    /** One fume article block: text is clipped to the paper and stamped in as it is printed. */
    private void drawArticleBlock(Canvas canvas, FoliaArticleLayout.Block block, int center) {
        LrcTimeline.Line source = sourceLines.get(block.line);
        PreparedLine line = prepare(block.line, block.font, width,
                block.hero ? face : regularFace, 0);
        text.setTypeface(line.font);
        text.setTextSize(line.size);
        text.getFontMetrics(metrics);
        float baselineOffset = block.lineHeight * (hasCjk(source.text) ? 0.52f : 0.5f);
        long start = source.timeMs;
        long end = start + Math.max(40, source.durationMs);
        boolean passed = animate && time >= end;
        boolean live = animate && !passed && time >= start;
        float waitingOpacity = block.hero ? 0.06f : 0.035f;
        float passedOpacity = block.hero ? 0.74f : 0.58f;
        float liveOpacity = block.hero ? 0.985f : 0.92f;
        int[] rows = FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, block.width, line.spacing);
        int first = 0;
        int top = canvas.save();
        canvas.clipRect(block.x, block.y - block.lineHeight, block.x + block.width,
                block.y + rows.length * block.lineHeight + block.lineHeight);
        for (int r = 0; r < rows.length; r++) {
            int rowEnd = rows[r];
            float rowWidth = line.offsets[rowEnd - 1] + line.widths[rowEnd - 1] - line.offsets[first];
            float rowLeft = block.hero ? Math.max((block.width - rowWidth) * 0.08f, 0f) : 0f;
            float baseline = block.y + r * block.lineHeight + baselineOffset - line.baseline;
            for (int g = first; g < rowEnd; g++) {
                float opacity;
                int color = primary;
                float glow = 0f;
                if (live) {
                    if (!animate || time < line.timeline.starts[g]) {
                        opacity = waitingOpacity;
                    } else {
                        opacity = liveOpacity;
                        float trail = (float) FoliaRingMath.clamp(
                                (time - line.timeline.ends[g]) / 700f, 0, 1);
                        color = mix(accent, primary,
                                0.18f + 0.82f * (float) Math.pow(trail, 1.35));
                        glow = (1f - trail) * 12f * strength;
                    }
                } else {
                    opacity = passed ? passedOpacity : waitingOpacity;
                }
                int glyphSave = canvas.save();
                canvas.translate(block.x + rowLeft + line.offsets[g] - line.offsets[first]
                                + line.widths[g] / 2f,
                        baseline);
                glyph(canvas, line, g, color, opacity, 0, glow);
                canvas.restoreToCount(glyphSave);
            }
            first = rowEnd;
        }
        canvas.restoreToCount(top);
    }

    /** fume draws its own 17.6px subtitle instead of the shared bottom caption. */
    private void articleSubtitle(Canvas canvas, FoliaArticleLayout.Block focus) {
        LrcTimeline.Line source = sourceLines.get(focus.line);
        if (source.translated == null || source.translated.isEmpty()) return;
        float size = 17.6f * fontScale * subtitleScale;
        float lineHeight = size * 1.5f;
        text.setTypeface(regularFace);
        text.setTextSize(size);
        text.getFontMetrics(metrics);
        float baseline = height - 34f - lineHeight + (lineHeight - (metrics.descent - metrics.ascent)) / 2f
                - metrics.ascent;
        label(canvas, source.translated, width / 2f, baseline, size,
                alpha(muted, 0.36f), Paint.Align.CENTER, regularFace);
    }

    private void clockwork(Canvas canvas) {
        float min = Math.min(width, height);
        float radius = min * 0.42f;
        float lyricRadius = min * 0.48f;
        float cy = height * 0.5f;
        float stepRad = (float) Math.toRadians(12.5);
        // showGearDecor defaults to 'subtle' (0.6x) upstream, so every decoration alpha shrinks.
        clockwork.colors(primary, accent, 0.6f);
        clockwork.draw(canvas, radius, cy, clockworkMotion, lyricRadius, ringOffset / Math.PI * stepRad);
        float camera = (float) (ringOffset / Math.PI);
        float availableTextWidth = Math.max(140f, Math.min(width * 0.46f, width - radius - 48f));
        int count = sourceLines.size();
        if (count == 0) return;
        int focal = Math.max(0, Math.min(count - 1, anchor));
        int from = Math.max(0, focal - 8), to = Math.min(count, focal + 9);
        float mainFocalPx = Math.round(28 * fontScale);
        float mainIdlePx = Math.round(22 * fontScale);
        float focalLineHeight = Math.round(mainFocalPx * 1.2f);
        float idleLineHeight = Math.round(mainIdlePx * 1.5f);
        float subFocalPx = Math.round(16 * fontScale);
        float subIdlePx = Math.round(12 * fontScale);
        float subFocalLineHeight = Math.round(subFocalPx * 1.5f);
        float subIdleLineHeight = Math.round(subIdlePx * 1.5f);
        // Upstream lineBlockHeight is always measured at the focal size, independent of the
        // rendered size, which keeps the angular spacing between lines stable.
        float[] heights = new float[count];
        float[] angles = new float[count];
        for (int i = from; i < to; i++) {
            PreparedLine measure = prepare(i, mainFocalPx, width, regularFace, 0.01f);
            int[] rows = FoliaSceneMath.wrap(measure.timeline.glyphs, measure.offsets,
                    availableTextWidth / 1.25f, measure.spacing);
            float height = rows.length * focalLineHeight;
            if (showTranslation && !sourceLines.get(i).translated.isEmpty()) {
                height += subFocalLineHeight + Math.round(0.25f * subFocalPx);
            }
            heights[i] = height * 1.25f;
        }
        for (int i = focal + 1; i < to; i++) angles[i] = angles[i - 1]
                + Math.max(stepRad, (heights[i - 1] + heights[i] + 24f) / (2 * lyricRadius));
        for (int i = focal - 1; i >= from; i--) angles[i] = angles[i + 1]
                - Math.max(stepRad, (heights[i + 1] + heights[i] + 24f) / (2 * lyricRadius));
        for (int i = from; i < to; i++) {
            if (Math.abs(angles[i]) >= Math.PI / 2) continue;
            float display = angles[i] + (anchor - camera) * stepRad;
            boolean focus = i == anchor;
            float distance = Math.abs(i - camera);
            float scale = focus ? 1.25f : Math.max(0.7f, 1 - Math.abs(i - focal) * 0.08f);
            float opacity = (float) Math.max(0.12f, Math.pow(Math.max(0, Math.cos(display * 0.75f)), 2.5)
                    * (1 - distance * 0.18f));
            opacity *= FoliaRingMath.clamp((110 - Math.toDegrees(Math.abs(display))) / 28f, 0, 1);
            if (opacity <= 0.01f) continue;
            float fontSize = focus ? mainFocalPx : mainIdlePx;
            float lineHeight = focus ? focalLineHeight : idleLineHeight;
            float subSize = focus ? subFocalPx : subIdlePx;
            float subLineHeight = focus ? subFocalLineHeight : subIdleLineHeight;
            float maxWidth = availableTextWidth / scale;
            PreparedLine line = prepare(i, fontSize, width, regularFace, 0.01f);
            int[] rows = FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, maxWidth, line.spacing);
            boolean hasSub = showTranslation && !sourceLines.get(i).translated.isEmpty();
            float contentHeight = rows.length * lineHeight
                    + (hasSub ? Math.round(0.25f * subSize) + subLineHeight : 0);
            int save = canvas.save();
            canvas.translate(lyricRadius * (float) Math.cos(display), cy + lyricRadius * (float) Math.sin(display)
                    + contentHeight / 2f);
            canvas.rotate((float) Math.toDegrees(0.35f * display));
            canvas.scale(scale, scale);
            int first = 0;
            for (int r = 0; r < rows.length; r++) {
                int end = rows[r];
                float centerY = -contentHeight + lineHeight / 2f + r * lineHeight;
                if (focus) {
                    pendoloSweepRow(canvas, line, first, end, centerY, lineHeight, opacity);
                } else {
                    for (int g = first; g < end; g++) {
                        int glyphSave = canvas.save();
                        canvas.translate(line.offsets[g] - line.offsets[first] + line.widths[g] / 2f, centerY);
                        glyph(canvas, line, g, primary, 0.75f * opacity, 0, 0);
                        canvas.restoreToCount(glyphSave);
                    }
                }
                first = end;
            }
            if (hasSub) {
                float subCenter = -contentHeight + rows.length * lineHeight
                        + Math.round(0.25f * subSize) + subLineHeight / 2f;
                text.setTypeface(regularFace);
                text.setTextSize(subSize);
                text.getFontMetrics(metrics);
                float baseline = -(metrics.ascent + metrics.descent) / 2f;
                label(canvas, sourceLines.get(i).translated, 0, subCenter + baseline, subSize,
                        focus ? alpha(muted, opacity) : alpha(muted, 0.6f * opacity), Paint.Align.LEFT);
            }
            canvas.restoreToCount(save);
        }
    }

    /**
     * PendoloActiveLyricSweep: a cool primary@0.52 base layer with a warm mask-revealed fill.
     */
    private void pendoloSweepRow(Canvas canvas, PreparedLine line, int first, int end,
                                 float centerY, float lineHeight, float opacity) {
        for (int g = first; g < end; g++) {
            int save = canvas.save();
            canvas.translate(line.offsets[g] - line.offsets[first] + line.widths[g] / 2f, centerY);
            glyph(canvas, line, g, primary, 0.52f * opacity, 0, 0);
            canvas.restoreToCount(save);
        }
        float fillWidth = 0;
        for (int g = first; g < end; g++) {
            double progress = line.timeline.progress(g, time);
            if (progress <= 0) break;
            fillWidth = line.offsets[g] - line.offsets[first] + line.widths[g] * (float) Math.min(1, progress);
            if (progress < 1) break;
        }
        if (fillWidth <= 0.5f) return;
        int fill = mix(primary, accent, 0.32f);
        int save = canvas.save();
        canvas.clipRect(0, centerY - lineHeight * 0.62f, fillWidth, centerY + lineHeight * 0.38f);
        for (int g = first; g < end; g++) {
            int glyphSave = canvas.save();
            canvas.translate(line.offsets[g] - line.offsets[first] + line.widths[g] / 2f, centerY);
            glyph(canvas, line, g, fill, opacity, 0, 0);
            canvas.restoreToCount(glyphSave);
        }
        canvas.restoreToCount(save);
    }

    private void conversation(Canvas canvas, MusicSnapshot snapshot) {
        Bitmap cover = snapshot.albumArt;
        int center = Math.max(0, anchor);
        int lineCount = sourceLines.size();
        if (lineCount == 0) return;
        float base = Math.max(15f, Math.min(26f, 18f * fontScale));
        float horizontal = width >= 1024 ? 56f : width >= 640 ? 32f : 16f;
        float panelWidth = Math.min(width - horizontal * 2f, 896f);
        float maxTextWidth = Math.max(96f,
                (float) Math.floor(panelWidth * (width >= 640 ? 0.68f : 0.78f)) - 56f);
        float usableHeight = Math.max(200f, height - 240f);
        float panelLeft = (width - panelWidth) / 2f;
        float panelRight = panelLeft + panelWidth;
        float topY = width >= 1024 ? 80f : width >= 640 ? 64f : 48f;

        // Upstream getVisibleMessages(): accumulate estimated heights from the newest message
        // backwards, drop the title when it no longer fits and keep at least two messages.
        int firstLine = center;
        float accumulated = 0f;
        int visibleCount = 0;
        for (int i = center; i >= Math.max(0, center - 19); i--) {
            float estimate = cappellaRowEstimate(i, base, maxTextWidth, center);
            if (visibleCount >= 2 && accumulated + estimate > usableHeight) break;
            accumulated += estimate;
            visibleCount++;
            firstLine = i;
        }
        boolean showTitle = !snapshot.title.isEmpty()
                && accumulated + cappellaTitleEstimate(snapshot.title, base) <= usableHeight;

        float y = topY;
        if (showTitle) {
            y = drawCappellaTitleRow(canvas, cover, y, panelLeft, panelRight, snapshot.title, base);
        }
        for (int i = firstLine; i <= center; i++) {
            PreparedLine line = prepare(i, base, width);
            boolean right = i < chatSides.length && chatSides[i];
            int sender = i < chatSenders.length ? chatSenders[i] : 0;
            int avatarIndex = avatarTile(sender);
            if (isCappellaInterlude(i)) {
                long start = sourceLines.get(i).timeMs;
                long end = i + 1 < lineCount ? sourceLines.get(i + 1).timeMs : start + 3000;
                if (time >= start) {
                    y = drawCappellaEmoji(canvas, cover, y, panelLeft, panelRight, right,
                            time < end ? EMOJI_ACTIVE : EMOJI_PASSED, cappellaEmojiIndex(start, i),
                            avatarIndex, cappellaLineEnd(line, i), end);
                }
                continue;
            }
            y = drawCappellaBubbleRow(canvas, cover, y, panelLeft, panelRight, maxTextWidth, base,
                    i, center, right, sender);
            int gapState = cappellaEmojiState(i, line);
            if (gapState != EMOJI_NONE) {
                long nextStart = sourceLines.get(i + 1).timeMs;
                y = drawCappellaEmoji(canvas, cover, y, panelLeft, panelRight, right, gapState,
                        cappellaEmojiIndex(cappellaGapStart(i), i), avatarIndex,
                        cappellaLineEnd(line, i), nextStart);
            }
        }
    }

    private float cappellaTitleEstimate(String title, float base) {
        text.setTypeface(regularFace);
        text.setTextSize(base);
        float lineHeight = base * 1.45f;
        float measured = (float) Math.ceil(lineHeight + 26f);
        float rendered = Math.max(measured, Math.max(44f, lineHeight + 24f));
        return rendered + 12f;
    }

    private float cappellaRowEstimate(int index, float base, float maxTextWidth, int center) {
        boolean active = index == center;
        if (isCappellaInterlude(index)) {
            float image = active ? 160f : 110f;
            return image + (active ? (float) Math.ceil(image * 0.12f) : 0f) + 48f + 12f;
        }
        float size = base * (active ? 1.34f : 0.94f);
        int padY = active ? 16 : 12;
        PreparedLine line = prepare(index, size, width);
        int rows = cappellaTextRows(line, line.timeline.glyphs.length, maxTextWidth);
        float lineHeight = size * 1.45f;
        float measured = (float) Math.ceil(rows * lineHeight + padY * 2f + 2f);
        float rendered = Math.max(measured, Math.max(active ? 64f : 44f, lineHeight + padY * 2f));
        float scale = active ? 1.12f : 0.92f;
        float estimate = Math.max(40f, active ? (float) Math.ceil(rendered * scale) : rendered) + 12f;
        if (cappellaEmojiState(index, line) != EMOJI_NONE) {
            float sticker = index == center ? 160f : 110f;
            estimate += (float) Math.ceil((48f + sticker) * (index == center ? 1.12f : 0.92f)) + 12f;
        }
        return estimate;
    }

    private int cappellaTextRows(PreparedLine line, int glyphCount, float maxTextWidth) {
        int[] wrapped = FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, maxTextWidth, line.spacing);
        int rows = 1;
        int start = 0;
        for (int end : wrapped) {
            if (start >= glyphCount) break;
            if (glyphCount <= end) return rows;
            rows++;
            start = end;
        }
        return rows;
    }

    private float cappellaBaseline(float top, float padY, float lineHeight, float size, Typeface font) {
        text.setTypeface(font);
        text.setTextSize(Math.max(1f, size));
        Paint.FontMetrics metrics = new Paint.FontMetrics();
        text.getFontMetrics(metrics);
        return top + padY + (lineHeight - (metrics.descent - metrics.ascent)) / 2f - metrics.ascent;
    }

    private float drawCappellaTitleRow(Canvas canvas, Bitmap cover, float y, float panelLeft,
                                       float panelRight, String title, float base) {
        text.setTypeface(regularFace);
        text.setTextSize(base);
        float textWidth = text.measureText(title);
        float lineHeight = base * 1.45f;
        float bubbleWidth = (float) Math.ceil(textWidth + 32f + 2f);
        float bubbleHeight = Math.max((float) Math.ceil(lineHeight + 24f + 2f),
                Math.max(44f, lineHeight + 24f));
        float rowHeight = Math.max(40f, bubbleHeight);
        float bottom = y + rowHeight;
        float bubbleLeft = panelRight - 52f - bubbleWidth;
        float bubbleTop = bottom - bubbleHeight;
        float bubbleRight = bubbleLeft + bubbleWidth;
        drawBubble(canvas, bubbleLeft, bubbleTop, bubbleRight, bottom,
                alpha(mix(accent, primary, 0.18f), 0.94f), false);
        drawBubbleBorder(canvas, bubbleLeft, bubbleTop, bubbleRight, bottom,
                alpha(mix(accent, primary, 0.34f), 0.3f), false);
        float baseline = cappellaBaseline(bubbleTop, 12f, lineHeight, base, regularFace);
        label(canvas, title, bubbleLeft + 16f, baseline, base, 0xFF09090B, Paint.Align.LEFT, regularFace);
        chatAvatar(canvas, cover, panelRight - 20f, bottom - 20f, avatarTile(8), 1f);
        return bottom + 12f;
    }

    private float drawCappellaBubbleRow(Canvas canvas, Bitmap cover, float y, float panelLeft,
                                        float panelRight, float maxTextWidth, float base, int index,
                                        int center, boolean right, int sender) {
        boolean active = index == center;
        float size = base * (active ? 1.34f : 0.94f);
        int padX = active ? 20 : 16;
        int padY = active ? 16 : 12;
        PreparedLine line = prepare(index, size, width);
        int glyphCount = line.timeline.glyphs.length;
        int target = glyphCount;
        if (active && animate) {
            target = 0;
            while (target < glyphCount && time + 200 >= line.timeline.starts[target]) target++;
            target = Math.max(1, target);
        }
        int rows = cappellaTextRows(line, target, maxTextWidth);
        float lineHeight = size * 1.45f;
        float textWidth = target > 0 ? line.extent(0, target) : 0f;
        float bubbleWidth = Math.max(active ? 72f : 0f, Math.max(size * 0.6f, textWidth)) + padX * 2f + 2f;
        float measured = (float) Math.ceil(rows * lineHeight + padY * 2f + 2f);
        float bubbleHeight = Math.max(measured, Math.max(active ? 64f : 44f, lineHeight + padY * 2f));
        // Scaled-up rows reserve their enlarged height in the column flow (upstream flexbox model).
        float rowHeight = Math.max(40f, active ? (float) Math.ceil(bubbleHeight * 1.12f) : bubbleHeight);
        float bottom = y + rowHeight;
        float bubbleLeft = right ? panelRight - 52f - bubbleWidth : panelLeft + 52f;
        float bubbleTop = bottom - bubbleHeight;
        float bubbleRight = bubbleLeft + bubbleWidth;
        float scale = active ? 1.12f : 0.92f;
        float opacity = active ? 1f : 0.82f;
        float entry = active && animate
                ? (float) FoliaSceneMath.easeOut((time - sourceLines.get(index).timeMs) / 320.0) : 1f;
        int bubbleColor;
        int borderColor;
        int lyricColor;
        if (right) {
            bubbleColor = mix(accent, primary, 0.18f);
            borderColor = mix(accent, primary, 0.34f);
            lyricColor = 0xFF09090B;
        } else {
            float tone = (sender % 9) / 8f;
            float accentMix = 0.18f + tone * 0.62f;
            bubbleColor = mix(muted, accent, accentMix);
            borderColor = mix(muted, accent, Math.min(1f, accentMix + 0.18f));
            lyricColor = primary;
        }
        if (index == anchor ? overrideCurrentColor : overrideInactiveColor) {
            lyricColor = CoverLyricColorRules.color(index == anchor ? accent : muted, bubbleColor, index == anchor);
        }
        int save = canvas.save();
        canvas.translate(0f, (1f - entry) * 22f);
        canvas.scale(scale, scale, right ? panelRight : panelLeft, bottom);
        drawBubble(canvas, bubbleLeft, bubbleTop, bubbleRight, bottom,
                alpha(bubbleColor, right ? 0.94f : 1f), !right);
        drawBubbleBorder(canvas, bubbleLeft, bubbleTop, bubbleRight, bottom,
                alpha(borderColor, right ? 0.3f : 0.26f), !right);
        int visible = glyphCount;
        if (active && animate) {
            visible = 0;
            while (visible < glyphCount && time >= line.timeline.starts[visible]) visible++;
        }
        int[] wrapped = FoliaSceneMath.wrap(line.timeline.glyphs, line.offsets, maxTextWidth, line.spacing);
        int clip = canvas.save();
        rect.set(bubbleLeft, bubbleTop, bubbleRight, bottom);
        canvas.clipRect(rect);
        int start = 0;
        for (int r = 0; r < wrapped.length && start < visible; r++) {
            int end = wrapped[r];
            float rowOffset = line.offsets[start];
            float baseline = cappellaBaseline(bubbleTop, padY, lineHeight, size, line.font)
                    + r * lineHeight;
            int limit = Math.min(end, visible);
            for (int g = start; g < limit; g++) {
                float fade = 1f;
                if (active && animate) {
                    long fadeMs = Math.max(40L, Math.min(220L,
                            line.timeline.ends[g] - line.timeline.starts[g]));
                    fade = (float) FoliaRingMath.clamp(
                            (time - line.timeline.starts[g]) / (double) fadeMs, 0.0, 1.0);
                }
                int glyphSave = canvas.save();
                canvas.translate(bubbleLeft + padX + line.offsets[g] - rowOffset, baseline);
                glyph(canvas, line, g, lyricColor, opacity * fade, 0, 0, true);
                canvas.restoreToCount(glyphSave);
            }
            start = end;
        }
        canvas.restoreToCount(clip);
        chatAvatar(canvas, cover, right ? panelRight - 20f : panelLeft + 20f, bottom - 20f,
                avatarTile(sender), opacity);
        long ready = line.timeline.starts.length == 0 ? sourceLines.get(index).timeMs
                : line.timeline.starts[line.timeline.starts.length - 1] + 220L;
        if (time >= ready) {
            long endMs = cappellaLineEnd(line, index);
            String stamp = String.format(Locale.ROOT, "%d:%02d", endMs / 60000, endMs / 1000 % 60);
            float stampY = bottom - 6f;
            if (right) label(canvas, stamp, bubbleLeft - 8f, stampY, 11,
                    alpha(muted, 0.62f * opacity), Paint.Align.RIGHT);
            else label(canvas, stamp, bubbleRight + 8f, stampY, 11,
                    alpha(muted, 0.62f * opacity), Paint.Align.LEFT);
        }
        canvas.restoreToCount(save);
        return bottom + 12f;
    }

    private void drawBubbleBorder(Canvas canvas, float left, float top, float right, float bottom,
                                  int color, boolean tailLeft) {
        float radius = 24;
        float[] radii = {radius, radius, radius, radius, radius, radius, radius, radius};
        if (tailLeft) {
            radii[6] = 6;
            radii[7] = 6;
        } else {
            radii[4] = 6;
            radii[5] = 6;
        }
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeWidth(1);
        shape.setColor(color);
        path.reset();
        rect.set(left, top, right, bottom);
        path.addRoundRect(rect, radii, Path.Direction.CW);
        canvas.drawPath(path, shape);
        path.reset();
        shape.setStyle(Paint.Style.FILL);
    }

    private static final String[] CAPPELLA_EMO_NAMES = {"happy1", "love1", "normal1",
            "sleepy1", "sleepy2", "sleepy3", "vibe1", "vibe2", "vibe3"};
    private static final int EMOJI_NONE = 0, EMOJI_ACTIVE = 1, EMOJI_PASSED = 2;

    private boolean isCappellaInterlude(int index) {
        return index >= 0 && index < sourceLines.size()
                && "......".equals(sourceLines.get(index).text.trim());
    }

    private boolean cappellaInterludeFollows(int index) {
        return index + 1 < sourceLines.size() && isCappellaInterlude(index + 1);
    }

    /** Natural end of the lyric line (providers set durationMs; word timings are the fallback). */
    private long cappellaLineEnd(PreparedLine line, int index) {
        LrcTimeline.Line source = sourceLines.get(index);
        if (source.durationMs > 0) return source.timeMs + source.durationMs;
        if (line.timeline.ends.length > 0) return line.timeline.ends[line.timeline.ends.length - 1];
        return source.timeMs;
    }

    private long cappellaGapStart(int index) {
        return cappellaLineEnd(prepare(index, 18f, width), index) + 50;
    }

    /** Upstream parser attachInterludes(): gaps longer than 3s become an "..." emoji message. */
    private int cappellaEmojiState(int index, PreparedLine line) {
        if (index < 0 || index + 1 >= sourceLines.size()) return EMOJI_NONE;
        long wordEnd = cappellaLineEnd(line, index);
        long nextStart = sourceLines.get(index + 1).timeMs;
        if (nextStart - wordEnd <= 3000) return EMOJI_NONE;
        if (time < wordEnd + 50) return EMOJI_NONE;
        return time < nextStart ? EMOJI_ACTIVE : EMOJI_PASSED;
    }

    private int avatarTile(int sender) {
        return sender == 8 ? 8 : new int[]{0, 3, 6, 1, 4}[Math.max(0, Math.min(4, sender))];
    }

    /** FNV-1a 32-bit, matching the upstream deterministic message ids. */
    private static long fnv1a(String value) {
        long hash = 2166136261L;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash = (hash * 16777619L) & 0xffffffffL;
        }
        return hash;
    }

    private static double seededUnit(String... parts) {
        StringBuilder seed = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) seed.append('|');
            seed.append(parts[i]);
        }
        return fnv1a(seed.toString()) / (double) 0xffffffffL;
    }

    /** JavaScript Number#toString formatting for deterministic seeds. */
    private static String jsNumber(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e15) return Long.toString((long) value);
        return new java.math.BigDecimal(Double.toString(value)).stripTrailingZeros().toPlainString();
    }

    private int cappellaEmojiIndex(long activationStartMs, int lineIndex) {
        double unit = seededUnit("interlude", jsNumber(activationStartMs / 1000.0),
                Integer.toString(lineIndex));
        return (int) Math.floor(unit * CAPPELLA_EMO_NAMES.length) % CAPPELLA_EMO_NAMES.length;
    }

    private float drawCappellaEmoji(Canvas canvas, Bitmap cover, float y, float panelLeft,
                                    float panelRight, boolean right, int state, int emojiIndex,
                                    int avatarTile, long stampMs, long activationEnd) {
        boolean active = state == EMOJI_ACTIVE;
        float stickerSize = active ? 160f : 110f;
        float scale = active ? 1.12f : 0.92f;
        float rowHeight = (float) Math.ceil((48f + stickerSize) * scale);
        float bottom = y + rowHeight;
        float opacity = active ? 1f : 0.82f;
        float stickerLeft = right ? panelRight - 52f - stickerSize : panelLeft + 52f;
        float stickerTop = bottom - stickerSize;
        int save = canvas.save();
        canvas.scale(scale, scale, right ? panelRight : panelLeft, bottom);
        chatAvatar(canvas, cover, right ? panelRight - 20f : panelLeft + 20f, bottom - 20f,
                avatarTile, opacity);
        Bitmap emoji = emojiBitmap(emojiIndex);
        if (emoji != null && !emoji.isRecycled()) {
            float wiggle = (float) Math.sin((time % 1900L) / 1900.0 * Math.PI * 2.0) * 1.6f;
            int frame = canvas.save();
            canvas.rotate(wiggle, stickerLeft + stickerSize / 2f, stickerTop + stickerSize / 2f);
            path.reset();
            rect.set(stickerLeft, stickerTop, stickerLeft + stickerSize, stickerTop + stickerSize);
            path.addRoundRect(rect, 16, 16, Path.Direction.CW);
            int clip = canvas.save();
            canvas.clipPath(path);
            float fit = Math.min(stickerSize / emoji.getWidth(), stickerSize / emoji.getHeight());
            float drawWidth = emoji.getWidth() * fit;
            float drawHeight = emoji.getHeight() * fit;
            rect.set(stickerLeft + (stickerSize - drawWidth) / 2f,
                    stickerTop + (stickerSize - drawHeight) / 2f,
                    stickerLeft + (stickerSize + drawWidth) / 2f,
                    stickerTop + (stickerSize + drawHeight) / 2f);
            shape.setColor(alpha(Color.WHITE, opacity));
            canvas.drawBitmap(emoji, null, rect, shape);
            canvas.restoreToCount(clip);
            canvas.restoreToCount(frame);
        }
        if (time >= activationEnd) {
            String stamp = String.format(Locale.ROOT, "%d:%02d", stampMs / 60000, stampMs / 1000 % 60);
            float stampY = bottom - 4.5f;
            if (right) label(canvas, stamp, stickerLeft - 8f, stampY, 11,
                    alpha(muted, 0.62f * opacity), Paint.Align.RIGHT);
            else label(canvas, stamp, stickerLeft + stickerSize + 8f, stampY, 11,
                    alpha(muted, 0.62f * opacity), Paint.Align.LEFT);
        }
        canvas.restoreToCount(save);
        return bottom + 12f;
    }

    private Bitmap emojiBitmap(int index) {
        if (index < 0 || index >= CAPPELLA_EMO_NAMES.length) return null;
        String name = CAPPELLA_EMO_NAMES[index];
        Bitmap cached = emos.get(name);
        if (cached != null && !cached.isRecycled()) return cached;
        if (assets == null) return null;
        Bitmap decoded = null;
        try {
            java.io.InputStream stream = assets.open("folia_original_canvas/cappella/emo/" + name + ".png");
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inScaled = false;
            options.inSampleSize = 2;
            try {
                decoded = android.graphics.BitmapFactory.decodeStream(stream, null, options);
            } finally {
                stream.close();
            }
        } catch (java.io.IOException ignored) {
            return null;
        }
        if (decoded != null) emos.put(name, decoded);
        return decoded;
    }

    /** Bubble with the upstream 24 px radius and a 6 px tail corner on the avatar side. */
    private void drawBubble(Canvas canvas, float left, float top, float right, float bottom,
                            int color, boolean tailLeft) {
        float radius = 24;
        float[] radii = {radius, radius, radius, radius, radius, radius, radius, radius};
        if (tailLeft) {
            radii[6] = 6;
            radii[7] = 6;
        } else {
            radii[4] = 6;
            radii[5] = 6;
        }
        shape.setColor(color);
        path.reset();
        rect.set(left, top, right, bottom);
        path.addRoundRect(rect, radii, Path.Direction.CW);
        canvas.drawPath(path, shape);
        path.reset();
    }

    private void chatAvatar(Canvas canvas, Bitmap cover, float avatarX, float avatarY, int tile, float opacity) {
        if (cover != null && !cover.isRecycled()) {
            path.reset();
            path.addCircle(avatarX, avatarY, 20, Path.Direction.CW);
            int clip = canvas.save();
            canvas.clipPath(path);
            rect.set(avatarX - 20, avatarY - 20, avatarX + 20, avatarY + 20);
            source.set(cover.getWidth() * (tile % 3) / 3, cover.getHeight() * (tile / 3) / 3,
                    cover.getWidth() * (tile % 3 + 1) / 3, cover.getHeight() * (tile / 3 + 1) / 3);
            shape.setColor(alpha(Color.WHITE, opacity));
            canvas.drawBitmap(cover, source, rect, shape);
            canvas.restoreToCount(clip);
        } else {
            shape.setColor(alpha(accent, opacity * 0.5f));
            canvas.drawCircle(avatarX, avatarY, 20, shape);
            label(canvas, tile == 8 ? "♪" : "♫", avatarX, avatarY + 7, 22, alpha(primary, opacity), Paint.Align.CENTER);
        }
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeWidth(1);
        shape.setColor(alpha(0x33FFFFFF, opacity));
        canvas.drawCircle(avatarX, avatarY, 20, shape);
        shape.setStyle(Paint.Style.FILL);
    }

    /**
     * Monet rail layout, ported from VisualizerMonet.tsx / MonetLyricsRail.tsx / monetRailLayout.ts.
     * The upstream page is a flexbox column (artist/header, title+album, rail, description capsule)
     * vertically centred in the left column; the rail itself is a translated stack whose active line
     * is pinned at 46% of the rail height.
     */
    private void portrait(Canvas canvas, MusicSnapshot snapshot) {
        float ls = width <= 1536f ? 1f : width >= 2200f ? 1.16f : 1f + 0.16f * (width - 1536f) / 664f;
        float padY = width >= 1024 ? 32 : width >= 640 ? 24 : 20;
        float padX = width >= 1024 ? 56 : width >= 640 ? 32 : 20;
        float padRight = width >= 1280 ? 48 : width >= 1024 ? 40 : width >= 640 ? 32 : 20;
        float artistFont = FoliaSceneMath.clampFont(width, 16 * ls, 1.8f * ls, 28.8f * ls, 1);
        float titleFont = FoliaSceneMath.clampFont(width, 23.2f * ls, 3.3f * ls, 44.8f * ls, 1);
        float lyricFont = FoliaSceneMath.clampFont(width, 21.44f * ls, 2.75f * ls, 36.48f * ls, 1.2f * fontScale);
        float inactiveFont = FoliaSceneMath.clampFont(width, 17.28f * ls, 2f * ls, 23.68f * ls, 1.2f * fontScale);
        float translationFont = FoliaSceneMath.clampFont(width, 15.04f * ls, 1.28f * ls, 18.24f * ls, 1.2f * fontScale);
        float albumFont = 14 * ls;
        float rowWidth = Math.min(width, 1520 * ls);
        float rowLeft = (width - rowWidth) / 2;
        float portraitColumn = width >= 768 ? Math.max(220 * ls, Math.min(width * 0.28f, 430 * ls)) : 0;
        float columnWidth = rowWidth - portraitColumn;
        float contentWidth = Math.max(1, columnWidth - padX * 2);
        float artistLineHeight = artistFont * 1.5f;
        float headerHeight = artistLineHeight + 62;
        float titleLineHeight = titleFont * 1.06f;
        float titleLead = titleFont * 1.19f;
        float titleBlock = titleLead + 44;
        float railHeight = Math.min(Math.max(280, height * 0.52f), 520 * ls);
        float contentHeight = padY * 2 + headerHeight + 12 + titleBlock + railHeight + 50;
        float columnTop = (height - contentHeight) / 2;
        float x = rowLeft + padX;
        float artistTop = columnTop + padY;

        monetDecor(canvas);
        monetPortrait(canvas, snapshot, rowLeft + rowWidth, portraitColumn, padRight, ls);

        float titleFlowTop = artistTop + headerHeight + 12;
        if (showHeader) {
            text.setTypeface(italicFace);
            text.setTextSize(artistFont);
            text.getFontMetrics(metrics);
            float artistBaseline = artistTop + (artistLineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
            label(canvas, snapshot.artist, x, artistBaseline, artistFont, alpha(primary, 0.96f), Paint.Align.LEFT, italicFace);
            shape.setShader(new LinearGradient(0, artistTop + artistLineHeight + 6, 0,
                    artistTop + artistLineHeight + 62, alpha(primary, 0.72f), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawRect(x, artistTop + artistLineHeight + 6, x + 1, artistTop + artistLineHeight + 62, shape);
            shape.setShader(null);

            text.setTypeface(face);
            text.setTextSize(titleFont);
            text.getFontMetrics(metrics);
            float titleBaseline = titleFlowTop + (titleLineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
            label(canvas, snapshot.title, x, titleBaseline, titleFont, primary, Paint.Align.LEFT, face);
            float albumTop = titleFlowTop + titleLead;
            text.setTypeface(regularFace);
            text.setTextSize(albumFont);
            text.getFontMetrics(metrics);
            float albumBaseline = albumTop + (20 * ls - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
            label(canvas, "MONET", x, albumBaseline, albumFont, alpha(muted, 0.84f), Paint.Align.LEFT);
        }

        float railTop = titleFlowTop + titleBlock;
        if (!sourceLines.isEmpty()) {
            int center = Math.max(0, Math.min(sourceLines.size() - 1, anchor));
            boolean hasTranslation = showTranslation && !sourceLines.get(center).translated.isEmpty();
            float lineHeight = lyricFont * 1.18f;
            float textPadTop = Math.max(lyricFont * 0.16f, 8);
            float textPadBottom = Math.max(lyricFont * 0.34f, 14);
            float textBox = lineHeight + textPadTop + textPadBottom;
            float transLineHeight = translationFont * 1.28f;
            float transPadTop = Math.max(translationFont * 0.45f, 7);
            float transPadBottom = Math.max(translationFont * 0.18f, 5);
            float translationBlock = hasTranslation ? transLineHeight + transPadTop + transPadBottom : 0;
            float activeHeight = textBox + translationBlock;
            float focus = railHeight * 0.46f;
            float inactiveScale = Math.max(0.72f, Math.min(0.92f, inactiveFont / Math.max(1, lyricFont)));
            int first = Math.max(0, center - 2);
            int last = Math.min(sourceLines.size() - 1, center + 2);
            int count = last - first + 1;
            int centerSlot = center - first;
            float[] tops = new float[count];
            float[] scales = new float[count];
            float[] opacities = new float[count];
            float[] blurs = new float[count];
            float[] heights = new float[count];
            boolean[] actives = new boolean[count];
            for (int i = first; i <= last; i++) {
                int slot = i - first;
                int distance = Math.abs(i - center);
                actives[slot] = distance == 0;
                if (distance == 0) {
                    scales[slot] = 1;
                    opacities[slot] = 1;
                    blurs[slot] = 0;
                } else {
                    scales[slot] = Math.max(0.68f, inactiveScale * (float) Math.pow(0.9, distance - 1));
                    if (i < center) {
                        opacities[slot] = Math.max(0.28f, 0.52f - (distance - 1) * 0.12f);
                        blurs[slot] = 1.1f + (distance - 1) * 0.7f;
                    } else {
                        opacities[slot] = Math.max(0.36f, 0.72f - (distance - 1) * 0.18f);
                        blurs[slot] = distance == 1 ? 0.7f : 1.8f + (distance - 2) * 0.8f;
                    }
                }
                heights[slot] = (distance == 0 ? activeHeight : textBox) * scales[slot];
            }
            float activeGap = Math.max(18, lyricFont * 0.49f);
            float idleGap = Math.max(14, lyricFont * 0.38f);
            tops[centerSlot] = focus - heights[centerSlot] / 2;
            for (int slot = centerSlot + 1; slot < count; slot++) {
                tops[slot] = tops[slot - 1] + heights[slot - 1] + (slot - 1 == centerSlot ? activeGap : idleGap);
            }
            for (int slot = centerSlot - 1; slot >= 0; slot--) {
                tops[slot] = tops[slot + 1] - heights[slot] - (slot + 1 == centerSlot ? activeGap : idleGap);
            }
            int railClip = canvas.save();
            canvas.clipRect(rowLeft, railTop, rowLeft + columnWidth, railTop + railHeight);
            for (int slot = 0; slot < count; slot++) {
                int index = first + slot;
                boolean active = actives[slot];
                PreparedLine line = prepare(index, lyricFont, width, active ? face : regularFace, 0);
                text.setTypeface(line.font);
                text.setTextSize(line.size);
                text.getFontMetrics(metrics);
                float baseline = textPadTop + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
                int lineSave = canvas.save();
                canvas.translate(x, railTop + tops[slot]);
                canvas.scale(scales[slot], scales[slot]);
                if (active) {
                    // Monet word-sweep: soft accent glow trailing each revealed grapheme, then the
                    // grapheme fill mixes base -> keyword colour while it is being sung.
                    int glowColor = mix(accent, primary, 0.3f);
                    float glowRadiusA = lyricFont * 0.28f;
                    float glowRadiusB = lyricFont * 0.65f;
                    for (int i = 0; i < line.timeline.glyphs.length; i++) {
                        float glow = monetGlow(line, i);
                        if (glow <= 0.02f) continue;
                        for (int layer = 0; layer < 2; layer++) {
                            int glyphSave = canvas.save();
                            canvas.translate(line.offsets[i] + line.widths[i] / 2f, baseline - line.baseline);
                            drawSoftMask(canvas, line, i, glowColor, 0.88f * glow,
                                    layer == 0 ? glowRadiusA : glowRadiusB, 3 + layer);
                            canvas.restoreToCount(glyphSave);
                        }
                    }
                    for (int i = 0; i < line.timeline.glyphs.length; i++) {
                        float progress = (float) line.timeline.progress(i, time);
                        int color;
                        if (progress <= 0) color = alpha(primary, 0.34f);
                        else if (progress >= 1) color = alpha(primary, 0.98f);
                        else color = mix(alpha(primary, 0.98f), alpha(accent, 0.98f), progress);
                        canvas.drawText(line.timeline.glyphs[i], line.offsets[i], baseline, textColor(color));
                    }
                    if (hasTranslation) {
                        monetRailTranslation(canvas, sourceLines.get(index).translated, contentWidth,
                                textBox + transPadTop, transLineHeight, translationFont);
                    }
                } else {
                    int color = alpha(primary, opacities[slot] * (index < center ? 0.98f : 0.46f));
                    float blur = blurs[slot];
                    for (int i = 0; i < line.timeline.glyphs.length; i++) {
                        if (blur > 0.2f) {
                            int glyphSave = canvas.save();
                            canvas.translate(line.offsets[i] + line.widths[i] / 2f, baseline - line.baseline);
                            drawSoftMask(canvas, line, i, color, 1, blur, 2);
                            canvas.restoreToCount(glyphSave);
                        } else {
                            canvas.drawText(line.timeline.glyphs[i], line.offsets[i], baseline, textColor(color));
                        }
                    }
                }
                canvas.restoreToCount(lineSave);
            }
            canvas.restoreToCount(railClip);
        }

        float descTop = columnTop + padY + headerHeight + 12 + titleBlock + railHeight + 16;
        String capsule = (snapshot.sourceName == null || snapshot.sourceName.isEmpty()
                ? "Monet" : snapshot.sourceName).toUpperCase(Locale.ROOT);
        text.setTypeface(regularFace);
        text.setTextSize(12 * ls);
        float capsuleWidth = 16 * 2 + 10 + 12 + text.measureText(capsule);
        rect.set(x, descTop, x + capsuleWidth, descTop + 34);
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(0x2E09090B);
        canvas.drawRoundRect(rect, 17, 17, shape);
        shape.setStyle(Paint.Style.STROKE);
        shape.setStrokeWidth(1);
        shape.setColor(alpha(primary, 0.16f));
        canvas.drawRoundRect(rect, 17, 17, shape);
        shape.setStyle(Paint.Style.FILL);
        shape.setColor(accent);
        canvas.drawCircle(x + 16 + 5, descTop + 17, 5, shape);
        text.getFontMetrics(metrics);
        float capsuleBaseline = descTop + 17 + (16 - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
        label(canvas, capsule, x + 16 + 10 + 12, capsuleBaseline, 12 * ls, alpha(primary, 0.9f), Paint.Align.LEFT);
        shape.setColor(Color.WHITE);
    }

    /** monetLyricMotion.resolveMonetGlow: rise to a peak shortly after the grapheme, then decay. */
    private float monetGlow(PreparedLine line, int glyph) {
        long start = line.timeline.starts[glyph];
        long end = line.timeline.ends[glyph];
        double rise = Math.max(1, end - start) * 1.18;
        double peak = start + rise;
        double tail = Math.max(line.timeline.renderEnd, end + 1050);
        if (time <= start) return 0;
        if (time < peak) return smooth((time - start) / rise);
        return smooth((tail - time) / Math.max(1, tail - peak));
    }

    private static float smooth(double value) {
        double t = Math.max(0, Math.min(1, value));
        return (float) (t * t * (3 - 2 * t));
    }

    private void monetRailTranslation(Canvas canvas, String value, float maxWidth,
                                      float lineTop, float lineHeight, float size) {
        if (value == null || value.isEmpty()) return;
        text.setTypeface(regularFace);
        text.setTextSize(size);
        text.getFontMetrics(metrics);
        float baseline = lineTop + (lineHeight - (metrics.descent - metrics.ascent)) / 2 - metrics.ascent;
        int save = canvas.save();
        canvas.clipRect(0, lineTop, maxWidth, lineTop + lineHeight);
        text.setTextAlign(Paint.Align.LEFT);
        text.setShader(null);
        text.clearShadowLayer();
        text.setColor(alpha(primary, 0.68f));
        canvas.drawText(value, 0, baseline, text);
        canvas.restoreToCount(save);
    }

    /** MonetFloatingDecor: ten very dim theme-icon petals drifting behind the column. */
    private void monetDecor(Canvas canvas) {
        if (width < 640 || backgroundOpacity <= 0.001f) return;
        int base = alpha(muted, 0.55f);
        shape.setStyle(Paint.Style.FILL);
        for (int i = 0; i < 10; i++) {
            float px = (((i * 127 + 43) % 80) + 10) / 100f * width;
            float py = (((i * 211 + 17) % 80) + 5) / 100f * height;
            float size = 46 + (i * 53) % 40;
            float rotation = (i * 97) % 360;
            float opacity = (0.06f + ((i * 31) % 12) / 100f) * backgroundOpacity;
            if (opacity <= 0.001f) continue;
            shape.setColor(alpha(base, opacity));
            int save = canvas.save();
            canvas.translate(px, py);
            canvas.rotate(rotation);
            rect.set(-size * 0.22f, -size * 0.10f, size * 0.22f, size * 0.10f);
            canvas.drawOval(rect, shape);
            canvas.restoreToCount(save);
        }
        shape.setColor(Color.WHITE);
    }

    /** MonetPortraitImage square: 135.135% of the inner column, bleeding left, with the dark hanger. */
    private void monetPortrait(Canvas canvas, MusicSnapshot snapshot, float columnRight,
                               float portraitColumn, float padRight, float ls) {
        if (portraitColumn <= 0 || snapshot.albumArt == null || snapshot.albumArt.isRecycled()) return;
        float inner = Math.max(1, portraitColumn - 12 - padRight);
        float artSize = inner * 1.35135f;
        float left = columnRight - portraitColumn + 12 - inner * 0.35135f;
        float top = (height - artSize) / 2;
        source.set(0, 0, snapshot.albumArt.getWidth(), snapshot.albumArt.getHeight());
        rect.set(left, top, left + artSize, top + artSize);
        int coverClip = canvas.save();
        path.reset();
        path.addRoundRect(rect, 32 * ls, 32 * ls, Path.Direction.CW);
        canvas.clipPath(path);
        shape.setColor(Color.WHITE);
        canvas.drawBitmap(snapshot.albumArt, source, rect, shape);
        canvas.restoreToCount(coverClip);
        rect.set(left + artSize - 32 - 12, top - 12, left + artSize - 32, top + 44);
        shape.setColor(0xDB09090B);
        canvas.drawRoundRect(rect, 6, 6, shape);
        shape.setColor(Color.WHITE);
    }

    private Paint textColor(int color) {
        text.setTextAlign(Paint.Align.LEFT);
        text.setShader(null);
        text.clearShadowLayer();
        text.setColor(color);
        return text;
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
