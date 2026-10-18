// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Android Canvas adaptation of Folia Sonnet (92792519ce4b72d540e69f3634aa408810c6e0f6).
// See third_party/folia/NOTICE.md for upstream attribution and rendering differences.
package com.zuoqirun.lyricscompanion;

import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Build;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Independent Sonnet director, semantic typography and continuous native camera. */
final class FoliaOriginalSonnetRenderer {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint shape = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private final FoliaOriginalSonnetGraphics originalGraphics = new FoliaOriginalSonnetGraphics();
    private final float[] motion = new float[4], tail = new float[4], breath = new float[4];
    private final float[] focus = new float[2], center = new float[2], scratch = new float[2];
    private final Map<FoliaOriginalSonnetProgram.Shot, ShotView> views = new LinkedHashMap<>();
    private List<LrcTimeline.Line> source;
    private String identity = "";
    private String geometrySeed = "";
    private FoliaOriginalSonnetProgram program;
    private Typeface face = Typeface.DEFAULT, bold = Typeface.DEFAULT_BOLD, heavy = Typeface.DEFAULT_BOLD, light = Typeface.DEFAULT;
    private final PorterDuffXfermode screenBlend = new PorterDuffXfermode(PorterDuff.Mode.SCREEN);
    private float fontScale = 1, strength = 1, width, height, tracking;
    private boolean animate = true, lowResourceMode, transition;
    private Canvas frameCanvas;
    private int frameColor;
    private Canvas guideCanvas;
    private int guideColor;
    private final FoliaOriginalSonnetGuide.Target guideTarget = new FoliaOriginalSonnetGuide.Target() {
        @Override public void line(double x1, double y1, double x2, double y2, double width, double opacity, boolean white) {
            shape.setStyle(Paint.Style.STROKE); shape.setStrokeWidth((float) width);
            shape.setColor(alpha(white ? Color.WHITE : guideColor, (float) opacity));
            guideCanvas.drawLine((float) x1, (float) y1, (float) x2, (float) y2, shape);
        }
        @Override public void circle(double x, double y, double radius, boolean fill, double width, double opacity, boolean white) {
            shape.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE); shape.setStrokeWidth((float) width);
            shape.setColor(alpha(white ? Color.WHITE : guideColor, (float) opacity));
            guideCanvas.drawCircle((float) x, (float) y, (float) radius, shape);
        }
        @Override public void particle(int kind, double size, double x, double y, double rotation, double scale, double opacity) {
            int save = guideCanvas.save(); guideCanvas.translate((float) x, (float) y);
            guideCanvas.rotate((float) Math.toDegrees(rotation)); guideCanvas.scale((float) scale, (float) scale);
            float s = (float) size;
            shape.setColor(alpha(guideColor, (float) opacity)); shape.setStyle(Paint.Style.FILL);
            if (kind == 0) guideCanvas.drawCircle(0, 0, s, shape);
            else if (kind == 1) guideCanvas.drawRect(-s, -s, s, s, shape);
            else if (kind == 2) {
                shape.setStyle(Paint.Style.STROKE); shape.setStrokeWidth(2);
                path.reset(); path.moveTo(-s, 0); path.lineTo(s, 0); path.moveTo(0, -s); path.lineTo(0, s);
                guideCanvas.drawPath(path, shape);
            } else {
                path.reset(); path.moveTo(0, -s); path.lineTo(s, 0); path.lineTo(0, s); path.lineTo(-s, 0); path.close();
                guideCanvas.drawPath(path, shape);
            }
            guideCanvas.restoreToCount(save);
        }
    };
    private final FoliaOriginalSonnetFrameDecor.Target frameTarget = new FoliaOriginalSonnetFrameDecor.Target() {
        @Override public void path(double[] points, boolean fill, double stroke, double opacity) {
            path.reset();
            path.moveTo((float) points[0], (float) points[1]);
            for (int index = 2; index < points.length; index += 2) path.lineTo((float) points[index], (float) points[index + 1]);
            if (fill) path.close();
            shape.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
            shape.setStrokeWidth((float) stroke); shape.setColor(alpha(frameColor, (float) opacity));
            frameCanvas.drawPath(path, shape);
        }
        @Override public void circle(double x, double y, double radius, double opacity, boolean white) {
            shape.setStyle(Paint.Style.FILL); shape.setColor(alpha(white ? Color.WHITE : frameColor, (float) opacity));
            frameCanvas.drawCircle((float) x, (float) y, (float) radius, shape);
        }
    };

    void configure(Typeface face, float fontScale, float strength, boolean showTranslation,
                   boolean animate, boolean lowResourceMode) {
        Typeface incoming = face == null ? Typeface.DEFAULT : face;
        if (this.face != incoming || this.fontScale != fontScale) views.clear();
        this.face = incoming; this.bold = Typeface.create(incoming, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 28) {
            bold = Typeface.create(incoming, 700, false);
            heavy = Typeface.create(incoming, 900, false);
            light = Typeface.create(incoming, 300, false);
        } else { heavy = bold; light = incoming; }
        this.fontScale = Math.max(0.1f, fontScale);
        this.strength = Math.max(0, Math.min(2, strength * 2));
        this.animate = animate; this.lowResourceMode = lowResourceMode;
        // The parent scene owns the shared subtitle; this renderer draws no second subtitle.
    }

    void setTracking(float dp) {
        float value = Math.max(0, Math.min(30, dp));
        if (tracking != value) { tracking = value; views.clear(); }
    }

    void setAssets(AssetManager assets) {
        originalGraphics.setAssets(assets);
    }

    void setGeometrySeed(String seed) {
        String next = seed == null ? "" : seed;
        if (!geometrySeed.equals(next)) {
            geometrySeed = next;
            program = null;
            views.clear();
            originalGraphics.clear();
        }
    }

    void draw(Canvas canvas, MusicSnapshot snapshot, List<LrcTimeline.Line> lines, int anchor,
              long lyricTime, long animationClock, long dt, float width, float height,
              float bass, int primary, int accent, int muted) {
        if (lines.isEmpty() || width <= 0 || height <= 0) { transition = false; return; }
        String nextIdentity = snapshot.sourceName + '\u241f' + snapshot.title + '\u241f' + snapshot.artist;
        if (source != lines || !identity.equals(nextIdentity) || program == null) {
            source = lines; identity = nextIdentity;
            program = new FoliaOriginalSonnetProgram(lines, geometrySeed.isEmpty() ? nextIdentity : geometrySeed); views.clear();
        }
        if (this.width != width || this.height != height) { this.width = width; this.height = height; views.clear(); }
        FoliaOriginalSonnetProgram.Shot shot = program.shotAt(lyricTime);
        if (shot == null) return;
        ShotView view = views.get(shot);
        if (view == null) {
            view = build(shot); views.put(shot, view);
            while (views.size() > 3) views.remove(views.keySet().iterator().next());
        }
        double time = animate ? lyricTime : shot.end;
        double seconds = time / 1000.0;
        float progress = FoliaOriginalSonnetMotion.clamp((time - shot.start) / Math.max(1, shot.end - shot.start));
        FoliaOriginalSonnetMotion.frame(shot.kind, progress, motion);
        if (time > shot.end) {
            FoliaOriginalSonnetMotion.frame(shot.kind, 0.8, tail);
            float drift = (float) (1 - Math.exp(-(time - shot.end) / 1000 * 0.4)) * 2;
            for (int i = 0; i < 4; i++) motion[i] += (motion[i] - tail[i]) * drift;
        }
        float breathing = FoliaOriginalSonnetMotion.inOut((time - view.revealDone) / 1200);
        if (breathing > 0) {
            FoliaOriginalSonnetMotion.breath(seconds, (FoliaOriginalSonnetProgram.hash(shot.id) % 1024) / 1024.0 * Math.PI * 2, breath);
            motion[0] += breath[0] * breathing; motion[1] += breath[1] * breathing;
            motion[2] += breath[2] * breathing; motion[3] += breath[3] * breathing;
        }
        FoliaOriginalSonnetMotion.smoothFocus(time, shot.start, shot.end, view, center, scratch, focus);
        float camera = animate ? strength : 0;
        float pivotX = view.pivotX + (focus[0] - view.pivotX) * camera;
        float pivotY = view.pivotY + (focus[1] - view.pivotY) * camera;
        float scale = shot.zoom * (1 + (motion[2] - 1) * camera);
        float rotation = (shot.rotation + motion[3]) * camera;
        float x = width * ("poster-blocks".equals(shot.kind) ? 0.5f : 0.5f + shot.cameraX) + motion[0] * width * camera;
        float y = height * ("poster-blocks".equals(shot.kind) ? 0.5f : 0.48f + shot.cameraY + (shot.shotIndex % 2 == 1 ? 0.025f : -0.025f)) + motion[1] * height * camera;
        float alpha = transitionAlpha(shot, lyricTime);
        int layer = alpha < 0.999f ? canvas.saveLayerAlpha(0, 0, width, height, Math.round(alpha * 255), Canvas.ALL_SAVE_FLAG) : canvas.save();
        background(canvas, shot, primary, accent);
        canvas.translate(x, y); canvas.rotate((float) Math.toDegrees(rotation)); canvas.scale(scale, scale); canvas.translate(-pivotX, -pivotY);
        graphics(canvas, shot, seconds, progress, bass, primary, accent, muted);
        for (BoxView box : view.boxes) leadGuide(canvas, box, time, accent, muted);
        for (int i = view.boxes.size() - 1; i >= 0; i--) {
            BoxView box = view.boxes.get(i);
            textBackdrop(canvas, box, time, shot, primary, accent, muted, camera);
            frames(canvas, box, time, shot, primary);
        }
        // Pixi puts all chromatic copies in a shared SCREEN layer below every text glyph.
        for (BoxView box : view.boxes) glyphs(canvas, box, time, shot, primary, accent, camera, true);
        for (BoxView box : view.boxes) glyphs(canvas, box, time, shot, primary, accent, camera, false);
        canvas.restoreToCount(layer);
        outerFrame(canvas, primary);
    }

    boolean transitioning() { return animate && transition; }

    void clear() {
        source = null;
        identity = "";
        program = null;
        views.clear();
        originalGraphics.clear();
        transition = false;
    }

    private ShotView build(FoliaOriginalSonnetProgram.Shot shot) {
        List<FoliaOriginalSonnetLayout.Box> layout = originalGraphics.layout(shot, width, height, fontScale,
                (value, size, role) -> {
                    text.setTypeface(typeface(role)); text.setTextSize(size);
                    return text.measureText(value) + Math.max(0, FoliaGlyphTimeline.split(value).length - 1) * tracking;
                });
        ShotView view = new ShotView();
        long shotSeed = FoliaOriginalSonnetProgram.hash(program.seed + ":sonnet-p" + shot.paragraphIndex) + shot.shotIndex * 97;
        FoliaOriginalSonnetGuide.RandomSource random = originalGraphics.prepareShot(shot.kind, shotSeed, width, height);
        for (FoliaOriginalSonnetLayout.Box placement : layout) {
            BoxView box = new BoxView(); box.box = placement;
            FoliaOriginalSonnetProgram.Segment segment = placement.segment;
            box.seed = FoliaOriginalSonnetProgram.hash(segment.text + ':' + segment.timeline.offsets[segment.first]
                    + ':' + segment.timeline.offsets[segment.end] + ':' + placement.index + ":normal-offset");
            if (placement.role == FoliaOriginalSonnetLayout.SUPPORT) {
                float distance = ((box.seed / (float) 0xffffffffL) * 2 - 1) * placement.size * 0.3f;
                double normal = placement.rotation + (placement.flowVertical ? 0 : Math.PI / 2);
                placement.x += Math.cos(normal) * distance; placement.y += Math.sin(normal) * distance;
            }
            box.depth = placement.role == FoliaOriginalSonnetLayout.DECORATION
                    ? (random.next() > 0.5 ? 1 : -1) * (0.5f + (float) random.next() * 0.8f) : 0;
            int length = segment.end - segment.first;
            box.x = new float[length]; box.y = new float[length]; box.advance = new float[length];
            text.setTextSize(placement.size); text.setTypeface(typeface(placement.role));
            float total = 0;
            for (int g = 0; g < length; g++) {
                box.advance[g] = placement.vertical ? placement.size * 0.9f : Math.max(placement.size * 0.2f, text.measureText(segment.timeline.glyphs[segment.first + g]));
                if (g + 1 < length) box.advance[g] += tracking;
                total += box.advance[g];
            }
            float cursor = -total / 2, cosine = (float) Math.cos(placement.rotation), sine = (float) Math.sin(placement.rotation);
            for (int g = 0; g < length; g++) {
                float local = cursor + box.advance[g] / 2; cursor += box.advance[g];
                box.x[g] = placement.x + (placement.vertical ? -local * sine : local * cosine);
                box.y[g] = placement.y + (placement.vertical ? local * cosine : local * sine);
            }
            view.boxes.add(box);
            long frameHash = FoliaOriginalSonnetProgram.hash(segment.text + ':' + segment.timeline.offsets[segment.first]
                    + ':' + segment.timeline.offsets[segment.end] + ":frame-decor");
            box.textSeed = placement.index * 13L;
            for (int index = 0; index < segment.text.length(); index++) box.textSeed += segment.text.charAt(index);
            box.chorusShape = "chorus".equals(program.paragraphs.get(shot.paragraphIndex).kind) || box.textSeed % 100 < 35;
            box.backgroundShape = placement.role != FoliaOriginalSonnetLayout.DECORATION && segment.word
                    && box.textSeed % 100 < (box.chorusShape ? 26 : 15) && (frameHash & 1023) / 1024.0 >= 0.4;
            box.guide = new FoliaOriginalSonnetGuide(placement.role == FoliaOriginalSonnetLayout.HERO
                    || placement.role == FoliaOriginalSonnetLayout.SEMI_HERO, placement.size,
                    placement.enterX, placement.enterY, placement.timingPhase, random);
            if (placement.role != FoliaOriginalSonnetLayout.DECORATION && length > 0) {
                view.tracking.add(box); view.revealDone = Math.max(view.revealDone, segment.timeline.starts[segment.end - 1]);
                if (placement.role == FoliaOriginalSonnetLayout.HERO && view.tracking.size() == 1) { view.pivotX = placement.x; view.pivotY = placement.y; }
            }
        }
        for (BoxView box : view.boxes) if (box.box.role == FoliaOriginalSonnetLayout.HERO) { view.pivotX = box.box.x; view.pivotY = box.box.y; break; }
        if ("poster-blocks".equals(shot.kind)) view.pivotX = view.pivotY = 0;
        view.starts = new long[view.tracking.size()]; view.ends = new long[view.tracking.size()]; view.weights = new float[view.tracking.size()];
        for (int i = 0; i < view.tracking.size(); i++) {
            FoliaOriginalSonnetProgram.Segment segment = view.tracking.get(i).box.segment;
            view.starts[i] = segment.start(); view.ends[i] = segment.timeline.starts[segment.end - 1];
        }
        return view;
    }

    private Typeface typeface(int role) {
        return role == FoliaOriginalSonnetLayout.DECORATION ? light
                : role == FoliaOriginalSonnetLayout.HERO || role == FoliaOriginalSonnetLayout.SEMI_HERO ? heavy : bold;
    }

    private void textBackdrop(Canvas canvas, BoxView view, double time, FoliaOriginalSonnetProgram.Shot shot,
                              int primary, int accent, int secondary, float camera) {
        if (!view.backgroundShape || time < view.box.segment.start()) return;
        FoliaOriginalSonnetLayout.Box box = view.box;
        double duration = Math.min(Math.min(1800, Math.max(650, Math.max(1, shot.end - shot.start) * 0.42)), Math.max(1, shot.end - shot.start) * 0.72);
        float entered = animate ? FoliaOriginalSonnetMotion.expo((time - box.segment.start()) / Math.max(80, duration)) : 1;
        float offset = (1 - entered) * (animate ? strength : 0);
        float depth = -0.5f - view.textSeed % 5 * 0.1f;
        float scale = (0.86f + entered * 0.14f) * (1 + depth * 0.45f);
        int save = canvas.save();
        canvas.translate(box.x + box.enterX * offset + motion[0] * width * camera * depth * 2.5f,
                box.y + box.enterY * offset + motion[1] * height * camera * depth * 2.5f);
        canvas.rotate((float) Math.toDegrees(box.rotation)); canvas.scale(scale, scale);
        originalGraphics.drawTextFixed(canvas, view.textSeed, view.chorusShape, box.size, width,
                primary, secondary, accent, 0.16f + entered * 0.84f);
        canvas.restoreToCount(save);
    }

    private void glyphs(Canvas canvas, BoxView view, double time, FoliaOriginalSonnetProgram.Shot shot,
                        int primary, int accent, float camera, boolean aberrationOnly) {
        FoliaOriginalSonnetLayout.Box box = view.box;
        FoliaOriginalSonnetProgram.Segment segment = box.segment;
        boolean decoration = box.role == FoliaOriginalSonnetLayout.DECORATION, emphasized = box.role == FoliaOriginalSonnetLayout.HERO || box.role == FoliaOriginalSonnetLayout.SEMI_HERO;
        if (aberrationOnly && (decoration || lowResourceMode)) return;
        text.setTypeface(typeface(box.role)); text.setTextSize(box.size); text.setTextAlign(Paint.Align.CENTER);
        float baseline = -(text.ascent() + text.descent()) / 2;
        float duration = Math.min(Math.max(650, Math.min(1800, (shot.end - shot.start) * 0.42f)), (shot.end - shot.start) * 0.72f);
        for (int g = 0; g < view.x.length; g++) {
            int index = segment.first + g;
            long start = segment.timeline.starts[index];
            if (time < start) continue;
            String glyph = segment.timeline.glyphs[index];
            if (glyph.trim().isEmpty()) continue;
            float entered = animate ? FoliaOriginalSonnetMotion.expo((time - start) / Math.max(80, duration)) : 1;
            float offset = (1 - entered) * (animate ? strength : 0), stagger = g % 2 == 0 ? -1 : 1;
            float depthScale = 1 + view.depth * 0.45f;
            float glyphScale = (emphasized && "type-impact".equals(shot.kind) ? 0.52f + entered * 0.48f : 0.86f + entered * 0.14f) * depthScale;
            float gx = view.x[g] + (box.enterX + (box.vertical ? stagger * box.size * 0.28f : 0)) * offset + motion[0] * width * camera * view.depth * 2.5f;
            float gy = view.y[g] + (box.enterY + (box.vertical ? 0 : stagger * box.size * 0.24f)) * offset + motion[1] * height * camera * view.depth * 2.5f;
            int save = canvas.save(); canvas.translate(gx, gy); canvas.rotate((float) Math.toDegrees(box.rotation + stagger * (emphasized ? 0.055f : 0.035f) * offset)); canvas.scale(glyphScale, glyphScale);
            float opacity = (0.16f + entered * 0.84f) * (decoration ? 0.2f : 1);
            if (aberrationOnly) {
                float aberration = box.size * (emphasized ? 0.025f : 0.01f) * (1 - FoliaOriginalSonnetMotion.inOut(entered) * 0.8f);
                text.setStyle(Paint.Style.FILL); text.setXfermode(screenBlend);
                text.setColor(alpha(primary & 0xff00ffff, opacity * (emphasized ? 0.8f : 0.5f)));
                canvas.drawText(glyph, -aberration, baseline + aberration * 0.5f, text);
                int redTint = primary & 0xffff0000 | Math.round(Color.blue(primary) * (0x44 / 255f));
                text.setColor(alpha(redTint, opacity * (emphasized ? 0.8f : 0.5f)));
                canvas.drawText(glyph, aberration, baseline - aberration * 0.5f, text);
                text.setXfermode(null); canvas.restoreToCount(save); continue;
            }
            if (box.role == FoliaOriginalSonnetLayout.SEMI_HERO && animate && !lowResourceMode) {
                float ghostDuration = Math.min(700, Math.max(400, (shot.end - shot.start) * 0.12f + 100));
                float p = FoliaOriginalSonnetMotion.clamp((time - start) / ghostDuration);
                if (p > 0 && p < 1) {
                    float envelope = p <= 0.2f ? p / 0.2f : (float) Math.pow(1 - (p - 0.2f) / 0.8f, 2);
                    float spread = (1 - (float) Math.pow(1 - p, 3)) * box.size * 0.85f * (view.seed % 2 == 0 ? 1 : -1);
                    text.setStyle(Paint.Style.STROKE); text.setStrokeWidth(Math.max(1, Math.min(8, box.size * 0.006f)));
                    for (int echo = 1; echo <= 2; echo++) {
                        float shift = spread * (echo == 1 ? 1 : 1.7f);
                        text.setColor(alpha(primary, envelope * (echo == 1 ? 0.3f : 0.16f)));
                        float cosine = (float) Math.cos(-box.rotation), sine = (float) Math.sin(-box.rotation);
                        canvas.drawText(glyph, (box.flowVertical ? cosine : -sine) * shift,
                                baseline + (box.flowVertical ? sine : cosine) * shift, text);
                    }
                }
            }
            text.setStyle(decoration ? Paint.Style.STROKE : Paint.Style.FILL);
            text.setStrokeWidth(Math.max(1, Math.min(8, box.size * 0.006f)));
            text.setColor(alpha(decoration ? accent : primary, opacity)); canvas.drawText(glyph, 0, baseline, text);
            text.setStyle(Paint.Style.FILL); canvas.restoreToCount(save);
        }
    }

    private void leadGuide(Canvas canvas, BoxView view, double time, int accent, int secondary) {
        FoliaOriginalSonnetLayout.Box box = view.box;
        if (box.role == FoliaOriginalSonnetLayout.DECORATION) return;
        double lead = Math.min(380, Math.max(200, 180 + (box.segment.finish() - box.segment.start()) * 0.1));
        if (time < box.segment.start() - lead || time > box.segment.start() + 650 || view.guide == null) return;
        double progress = (time - box.segment.start() + lead) / (lead + 650);
        int save = canvas.save(); canvas.translate(box.x, box.y);
        guideCanvas = canvas;
        guideColor = box.role == FoliaOriginalSonnetLayout.HERO || box.role == FoliaOriginalSonnetLayout.SEMI_HERO ? accent : secondary;
        view.guide.draw(guideTarget, progress);
        guideCanvas = null; canvas.restoreToCount(save);
    }

    private void frames(Canvas canvas, BoxView view, double time, FoliaOriginalSonnetProgram.Shot shot, int primary) {
        FoliaOriginalSonnetLayout.Box box = view.box;
        if (box.role == FoliaOriginalSonnetLayout.DECORATION) return;
        long hash = FoliaOriginalSonnetProgram.hash(box.segment.text + ':' + box.segment.timeline.offsets[box.segment.first]
                + ':' + box.segment.timeline.offsets[box.segment.end] + ":frame-decor");
        if ((hash & 1023) / 1024f >= 0.4f || time < box.segment.start()) { shape.setStyle(Paint.Style.FILL); return; }
        int save = canvas.save(); canvas.translate(box.x, box.y); canvas.rotate((float) Math.toDegrees(box.rotation));
        double shotDuration = Math.max(1, shot.end - shot.start);
        double growDuration = Math.min(Math.min(1800, Math.max(650, shotDuration * 0.42)), shotDuration * 0.72) * 1.25;
        double growth = animate ? (time - box.segment.start()) / Math.max(1, growDuration) : 1;
        frameCanvas = canvas;
        frameColor = (primary & 0xff000000) | Math.round(Color.red(primary) * 0.55f) << 16
                | Math.round(Color.green(primary) * 0.55f) << 8 | Math.round(Color.blue(primary) * 0.55f);
        FoliaOriginalSonnetFrameDecor.draw(frameTarget, box.width, box.height, box.rotation,
                box.size, (int) (hash >>> 10) % 4, growth);
        frameCanvas = null;
        shape.setStyle(Paint.Style.FILL); canvas.restoreToCount(save);
    }

    private void background(Canvas canvas, FoliaOriginalSonnetProgram.Shot shot, int primary, int accent) {
        long seed = FoliaOriginalSonnetProgram.hash(program.seed + ":sonnet-p" + shot.paragraphIndex);
        shape.setStrokeWidth(1);
        for (int i = 0; i < 9; i++) {
            float x = (seed + i * 97) % 997 / 997f * width, y = (seed + i * 193) % 991 / 991f * height;
            shape.setColor(alpha(i % 2 == 0 ? accent : primary, 0.12f + i % 4 * 0.04f)); shape.setStrokeWidth(i % 3 == 0 ? 2 : 1);
            canvas.drawLine(x, y, Math.min(width, x + 32 + (seed + i * 43) % 180), y, shape);
        }
    }

    private void graphics(Canvas canvas, FoliaOriginalSonnetProgram.Shot shot, double seconds, float progress,
                          float bass, int primary, int accent, int muted) {
        long seed = FoliaOriginalSonnetProgram.hash(program.seed + ":sonnet-p" + shot.paragraphIndex) + shot.shotIndex * 97;
        long lastWord = shot.segments.isEmpty() ? shot.end : shot.segments.get(shot.segments.size() - 1).start();
        double target = Math.min(shot.end, Math.max(lastWord, shot.start + (shot.end - shot.start) * 0.95));
        float raw = animate ? FoliaOriginalSonnetMotion.clamp((seconds * 1000 - shot.start)
                / Math.max(1000, target - shot.start)) : 1;
        float camera = animate ? strength : 0;
        if (originalGraphics.draw(canvas, shot.kind, seed, width, height, raw,
                animate ? seconds - shot.start / 1000.0 : 0,
                (shot.rotation + motion[3]) * camera,
                motion[0] * camera, motion[1] * camera,
                motion[2], primary, muted, lowResourceMode)) return;
        float halfW = width / 2, halfH = height / 2, r = Math.min(width, height);
        float growth = animate ? 1 - (float) Math.pow(1 - progress, 3) : 1;
        shape.setStyle(Paint.Style.STROKE); shape.setStrokeWidth(1); shape.setColor(alpha(muted, 0.28f));
        canvas.drawRect(-halfW * 0.9f, -halfH * 0.9f, halfW * 0.9f, halfH * 0.9f, shape);
        if ("editorial-column".equals(shot.kind)) {
            for (int i = 1; i <= 6; i++) canvas.drawLine(-halfW + width * i / 7, -halfH, -halfW + width * i / 7, halfH * growth, shape);
            for (int i = 1; i <= 4; i++) canvas.drawLine(-halfW, -halfH + height * i / 5, halfW * growth, -halfH + height * i / 5, shape);
            shape.setStrokeWidth(4); shape.setColor(alpha(primary, 0.3f)); canvas.drawRect(-width * 0.3f, -height * 0.3f, width * 0.3f, height * 0.3f, shape);
        } else if ("type-impact".equals(shot.kind) || "fragment-collage".equals(shot.kind)) {
            int save = canvas.save(); canvas.rotate((float) (animate ? seconds * 2.8648 : 0));
            shape.setStrokeWidth(3); shape.setColor(alpha(primary, 0.28f));
            if (seed % 3 == 0) {
                rect.set(-r * 0.6f, -r * 0.6f, r * 0.6f, r * 0.6f);
                canvas.drawArc(rect, 0, 360 * growth, false, shape);
                for (int i = 0; i < 32 * growth; i++) {
                    double a = i / 32.0 * Math.PI * 2;
                    float inner = r * (0.3f + i % 3 * 0.05f);
                    shape.setStrokeWidth(1); canvas.drawLine((float) Math.cos(a) * inner, (float) Math.sin(a) * inner, (float) Math.cos(a) * r * 0.55f, (float) Math.sin(a) * r * 0.55f, shape);
                }
            } else {
                for (int layer = 0; layer < 3; layer++) {
                    float radius = r * (0.7f - layer * 0.18f) * Math.max(0.1f, growth);
                    path.reset(); path.moveTo(0, -radius); path.lineTo(radius, 0); path.lineTo(0, radius); path.lineTo(-radius, 0); path.close(); canvas.drawPath(path, shape);
                }
            }
            canvas.restoreToCount(save);
        }
        int particles = lowResourceMode ? 8 : 20;
        int save = canvas.save(); canvas.rotate((float) ((seconds - shot.start / 1000.0) * 0.05 * 180 / Math.PI));
        shape.setStyle(Paint.Style.FILL);
        for (int i = 0; i < particles; i++) {
            long random = FoliaOriginalSonnetProgram.hash(seed + ":particle:" + i);
            float x = (random & 65535) / 65535f * width - halfW, y = (random >>> 16 & 65535) / 65535f * height - halfH;
            float entry = animate ? FoliaOriginalSonnetMotion.clamp(progress * 1.5 - i / (float) particles * 0.5) : 1;
            shape.setColor(alpha(accent, entry * 0.3f)); float size = (1 + random % 4) * (1 + Math.max(0, bass) * 0.42f);
            canvas.drawRect(x, y, x + size, y + size, shape);
        }
        canvas.restoreToCount(save);
    }

    private float transitionAlpha(FoliaOriginalSonnetProgram.Shot shot, long time) {
        transition = false;
        if (!animate) return 1;
        int index = program.shots.indexOf(shot);
        if (index > 0) {
            FoliaOriginalSonnetProgram.Shot previous = program.shots.get(index - 1);
            long duration = Math.min(240, Math.max(140, Math.round((shot.start - previous.start) * 0.18)));
            if (time >= shot.start && time <= shot.start + duration) { transition = true; return 0.18f + FoliaOriginalSonnetMotion.inOut((time - shot.start) / (double) duration) * 0.82f; }
        }
        if (index + 1 < program.shots.size()) {
            FoliaOriginalSonnetProgram.Shot next = program.shots.get(index + 1);
            long duration = Math.min(240, Math.max(140, Math.round((next.start - shot.start) * 0.18)));
            if (time >= next.start - duration) { transition = true; return 1 - FoliaOriginalSonnetMotion.inOut((time - next.start + duration) / (double) duration); }
        }
        return 1;
    }

    private void outerFrame(Canvas canvas, int primary) {
        float px = Math.max(30, width * 0.05f), py = Math.max(30, height * 0.05f);
        shape.setStyle(Paint.Style.FILL); shape.setColor(alpha(primary, 0.8f));
        canvas.drawRect(px, py, px + 30, py + 4, shape);
        canvas.drawRect(width - px - 4, height - py - 16, width - px, height - py, shape);
        shape.setStrokeWidth(1); shape.setColor(alpha(primary, 0.5f));
        canvas.drawLine(px, py + 16, px, py + 120, shape);
        canvas.drawLine(width - px - 160, height - py, width - px - 20, height - py, shape);
        canvas.drawLine(width - px, height - py - 180, width - px, height - py - 30, shape);
        shape.setColor(alpha(primary, 0.8f));
        canvas.drawLine(width - px - 6, py + 20, width - px + 6, py + 20, shape);
        canvas.drawLine(width - px, py + 14, width - px, py + 26, shape);
        path.reset(); path.moveTo(px, height - py - 4); path.lineTo(px + 4, height - py); path.lineTo(px, height - py + 4); path.lineTo(px - 4, height - py); path.close();
        canvas.drawPath(path, shape);
    }

    private static int alpha(int color, float opacity) {
        return color & 0x00ffffff | Math.round(Color.alpha(color) * Math.max(0, Math.min(1, opacity))) << 24;
    }

    private static final class BoxView {
        FoliaOriginalSonnetLayout.Box box;
        float[] x, y, advance;
        long seed;
        long textSeed;
        boolean chorusShape, backgroundShape;
        FoliaOriginalSonnetGuide guide;
        float depth;
    }

    private static final class ShotView implements FoliaOriginalSonnetMotion.FocusSampler {
        final List<BoxView> boxes = new ArrayList<>(), tracking = new ArrayList<>();
        long[] starts, ends;
        float[] weights;
        float pivotX, pivotY;
        long revealDone;
        @Override public void sample(double time, float[] output) {
            if (tracking.isEmpty()) { output[0] = pivotX; output[1] = pivotY; return; }
            FoliaOriginalSonnetMotion.weights(starts, ends, time, weights);
            output[0] = output[1] = 0;
            for (int i = 0; i < tracking.size(); i++) {
                BoxView box = tracking.get(i); FoliaOriginalSonnetProgram.Segment segment = box.box.segment;
                int last = box.x.length - 1, current = 0;
                while (current < last && time > segment.timeline.starts[segment.first + current + 1]) current++;
                float x = box.x[current], y = box.y[current];
                if (current < last && time > segment.timeline.starts[segment.first + current]) {
                    float p = FoliaOriginalSonnetMotion.clamp((time - segment.timeline.starts[segment.first + current])
                            / Math.max(1, segment.timeline.starts[segment.first + current + 1] - segment.timeline.starts[segment.first + current]));
                    x += (box.x[current + 1] - x) * p; y += (box.y[current + 1] - y) * p;
                }
                float cx = (box.x[0] + box.x[last]) / 2, cy = (box.y[0] + box.y[last]) / 2;
                output[0] += (cx + (x - cx) * 0.5f) * weights[i]; output[1] += (cy + (y - cy) * 0.5f) * weights[i];
            }
        }
    }
}
