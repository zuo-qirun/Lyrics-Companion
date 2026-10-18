// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Original Tempera vector compositions and timing; attribution/differences: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.zuoqirun.lyricscompanion.FoliaTemperaMotion.*;
import static com.zuoqirun.lyricscompanion.FoliaTemperaGeometryOps.*;

/** Entirely native Canvas player for all 121 original Tempera compositions and their motion vocabulary. */
final class FoliaOriginalTemperaRenderer {
    private static final String ASSET_ROOT = "folia_original_canvas/tempera/";
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG | Paint.LINEAR_TEXT_FLAG);
    private final Paint graphics = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path paperDots = new Path(), wipePath = new Path();
    private double dotWidth, dotHeight;
    private final Frame glyphFrame = new Frame();
    private final FoliaTemperaGpuInversion inversion = new FoliaTemperaGpuInversion();
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final List<Shot> program = new ArrayList<>();
    private final List<Paragraph> paragraphs = new ArrayList<>();
    private final LruCache<Shot, Drawing> drawings = new LruCache<>(4);
    private final LruCache<Shot, List<Glyph>> layouts = new LruCache<>(8);
    private final int[] palette = new int[13];
    private AssetManager assets;
    private Typeface face = Typeface.create("sans-serif", Typeface.NORMAL);
    private List<LrcTimeline.Line> lines = Collections.emptyList();
    private String seed = "", identity = "", geometrySeed, resourceError;
    private float fontScale = 1, tracking;
    private int paperColor = 0xFF09090B;
    private double width, height, time;
    private boolean animate = true;
    private boolean wholeLineLyrics;
    private double settleStretch = .5;
    private double cameraIntensity = 1, glyphMotion = 1;
    private boolean showBlocks = true, showDecor = true, showCornerMarks = true, textInversion = true, enableTransitions = true;

    void setAssets(AssetManager assets) { this.assets = assets; }
    void setPaperColor(int color) { paperColor = color; }

    /** Upstream geometrySeed is an input (normally currentSongId), independent of content cache ids. */
    void setGeometrySeed(String seed) {
        String resolved = seed == null || seed.isEmpty() ? null : seed;
        if (resolved == null ? geometrySeed == null : resolved.equals(geometrySeed)) return;
        geometrySeed = resolved;
        this.seed = "";
        drawings.evictAll();
        layouts.evictAll();
    }

    void setTracking(float tracking) {
        if (this.tracking != tracking) {
            this.tracking = tracking;
            layouts.evictAll();
        }
    }

    void configure(Typeface face, float fontScale, float strength, boolean showTranslation,
                   boolean animate, boolean lowResourceMode) {
        Typeface resolved = face == null ? Typeface.create("sans-serif", Typeface.NORMAL) : face;
        if (!resolved.equals(this.face) || this.fontScale != fontScale) layouts.evictAll();
        this.face = resolved;
        this.fontScale = fontScale;
        this.animate = animate;
    }

    void configureTempera(double cameraIntensity, double glyphMotion, double settleStretch,
                          boolean wholeLineLyrics, boolean showBlocks, boolean showDecor,
                          boolean showCornerMarks, boolean textInversion, boolean enableTransitions) {
        if (this.wholeLineLyrics != wholeLineLyrics) { this.wholeLineLyrics = wholeLineLyrics; seed = ""; }
        if (this.settleStretch != settleStretch) { this.settleStretch = settleStretch; layouts.evictAll(); }
        if (this.showDecor != showDecor) drawings.evictAll();
        this.cameraIntensity = cameraIntensity; this.glyphMotion = glyphMotion;
        this.showBlocks = showBlocks; this.showDecor = showDecor; this.showCornerMarks = showCornerMarks;
        this.textInversion = textInversion; this.enableTransitions = enableTransitions;
    }

    boolean transitioning() { return false; }

    void clear() {
        program.clear();
        paragraphs.clear();
        drawings.evictAll();
        layouts.evictAll();
        lines = Collections.emptyList();
        seed = "";
        identity = "";
        resourceError = null;
        inversion.clear();
    }

    void draw(Canvas canvas, MusicSnapshot snapshot, List<LrcTimeline.Line> incoming, int anchor,
              long lyricTime, long animationClock, long delta, float width, float height, float bass,
              int primary, int accent, int muted) {
        this.time = lyricTime / 1000.0;
        if (this.width != width || this.height != height) { layouts.evictAll(); drawings.evictAll(); }
        this.width = width;
        this.height = height;
        text.setTypeface(face);
        if (!loadProfiles()) {
            text.setTextSize(18);
            text.setTextAlign(Paint.Align.CENTER);
            text.setColor(primary);
            canvas.drawText(resourceError == null ? "正在载入原版 Tempera 构图" : resourceError, width / 2, height / 2, text);
            return;
        }
        String identity = snapshot.sourceName + '\n' + snapshot.title + '\n' + snapshot.artist;
        String resolvedSeed = geometrySeed == null ? identity : geometrySeed;
        if (incoming != lines || !identity.equals(this.identity) || !resolvedSeed.equals(seed)) {
            lines = incoming;
            seed = resolvedSeed;
            this.identity = identity;
            drawings.evictAll();
            layouts.evictAll();
            compile();
        }
        if (program.isEmpty()) return;
        palette(primary, accent, muted);
        int current = 0;
        while (current + 1 < paragraphs.size() && paragraphs.get(current + 1).start <= time) current++;
        Paragraph active = paragraphs.get(current);
        Transition frame = paragraphFrame(current, false, null);
        drawParagraph(canvas, active, frame);
        Transition overlay = frame;
        if (enableTransitions && animate && current + 1 < paragraphs.size() && active.transition != null
                && !"block-wipe".equals(active.transition) && time >= active.transitionStart) {
            Transition incomingFrame = paragraphFrame(current + 1, true, active);
            drawParagraph(canvas, paragraphs.get(current + 1), incomingFrame);
            if (incomingFrame.wipe > .001 && incomingFrame.wipe < 1.999) overlay = incomingFrame;
        }
        drawOverlay(canvas, overlay);
    }

    private Transition paragraphFrame(int index, boolean incoming, Paragraph outgoing) {
        if (!enableTransitions || !animate) return new Transition();
        Paragraph paragraph = paragraphs.get(index), previous = index > 0 ? paragraphs.get(index - 1) : null;
        if (incoming && outgoing != null) return transition(outgoing.transition, true,
                (time - outgoing.transitionStart) / Math.max(.001, outgoing.transitionEnd - outgoing.transitionStart),
                paragraph.shots.isEmpty() ? 0 : paragraph.shots.get(0).flow);
        if (previous != null && "block-wipe".equals(previous.transition)) {
            double duration = Math.max(.35, Math.min(1, previous.transitionEnd - previous.transitionStart));
            if (time >= paragraph.start && time <= paragraph.start + duration) return transition(previous.transition, true,
                    (time - paragraph.start) / duration, paragraph.shots.isEmpty() ? 0 : paragraph.shots.get(0).flow);
        }
        return paragraph.transition != null && time >= paragraph.transitionStart
                ? transition(paragraph.transition, false, (time - paragraph.transitionStart) / Math.max(.001, paragraph.transitionEnd - paragraph.transitionStart),
                paragraph.shots.isEmpty() ? 0 : paragraph.shots.get(paragraph.shots.size() - 1).flow) : new Transition();
    }

    private void drawParagraph(Canvas canvas, Paragraph paragraph, Transition frame) {
        if (frame.alpha <= .001) return;
        int save = canvas.saveLayerAlpha(0, 0, (float) width, (float) height, (int) (frame.alpha * 255), Canvas.ALL_SAVE_FLAG);
        canvas.translate((float) (width / 2 + frame.x * width), (float) (height / 2 + frame.y * height));
        canvas.scale((float) frame.scale, (float) frame.scale); canvas.rotate((float) Math.toDegrees(frame.rotation));
        canvas.translate((float) -width / 2, (float) -height / 2);
        if (showBlocks) {
            graphics.setStyle(Paint.Style.FILL); graphics.setColor(palette[1]); graphics.setAlpha((int) (.35 * 255));
            canvas.drawRect(0, 0, (float) width, (float) height, graphics);
            if (dotWidth != width || dotHeight != height) {
                dotWidth = width; dotHeight = height; paperDots.reset();
                double spacing = Math.max(26, Math.sqrt(width * height / 6000));
                int columns = Math.min(200, (int) Math.ceil(width / spacing) + 1), rows = Math.min(200, (int) Math.ceil(height / spacing) + 1);
                for (int row = 0; row < rows; row++) for (int col = 0; col < columns; col++) {
                    float x = (float) (col * spacing + (row % 2 == 0 ? 0 : spacing / 2)), y = (float) (row * spacing);
                    paperDots.addRect(x - .8f, y - .8f, x + .8f, y + .8f, Path.Direction.CW);
                }
            }
            graphics.setColor(palette[12]); graphics.setAlpha((int) (.05 * 255)); canvas.drawPath(paperDots, graphics);
        }
        int active = 0;
        while (active + 1 < paragraph.shots.size() && paragraph.shots.get(active + 1).start <= time) active++;
        for (int shot = 0; shot <= active && shot < paragraph.shots.size(); shot++) {
            Shot value = paragraph.shots.get(shot);
            double handoff = Math.min(1.1, Math.max(.4, (value.end - value.start) * .3));
            if (shot == active || shot < active && (time - value.end) / handoff < 1) drawShot(canvas, value);
        }
        canvas.restoreToCount(save);
    }

    private void drawOverlay(Canvas canvas, Transition frame) {
        if (frame.wipe > .001 && frame.wipe < 1.999) {
            double span = Math.hypot(width, height), notch = span * .08, length = span + notch * 2;
            float start = (float) (-span / 2 - notch), end = (float) (start + length), half = (float) (span / 2);
            wipePath.reset(); wipePath.moveTo(start, -half); wipePath.lineTo(end, -half); wipePath.lineTo((float) (end + notch), 0);
            wipePath.lineTo(end, half); wipePath.lineTo(start, half); wipePath.lineTo((float) (start + notch), 0); wipePath.close();
            int save = canvas.save(); double offset = (frame.wipe - 1) * length;
            canvas.translate((float) (width / 2 + Math.cos(frame.angle) * offset), (float) (height / 2 + Math.sin(frame.angle) * offset));
            canvas.rotate((float) Math.toDegrees(frame.angle)); graphics.setStyle(Paint.Style.FILL); graphics.setColor(palette[11]); canvas.drawPath(wipePath, graphics); canvas.restoreToCount(save);
        }
        if (showCornerMarks) {
            float px = (float) Math.max(28, width * .045), py = (float) Math.max(28, height * .045);
            graphics.setColor(palette[2]); graphics.setAlpha((int) (.5 * 255)); graphics.setStrokeWidth(1.5f); graphics.setStyle(Paint.Style.STROKE);
            wipePath.reset(); wipePath.moveTo(px, py + 14); wipePath.lineTo(px, py); wipePath.lineTo(px + 14, py);
            wipePath.moveTo((float) width - px - 14, (float) height - py); wipePath.lineTo((float) width - px, (float) height - py); wipePath.lineTo((float) width - px, (float) height - py - 14);
            canvas.drawPath(wipePath, graphics);
        }
    }

    private boolean loadProfiles() {
        if (!profiles.isEmpty()) return true;
        if (assets == null) return false;
        try {
            JSONObject manifest = read("manifest.json").getJSONObject("profiles");
            JSONArray order = read("manifest.json").getJSONArray("kinds");
            for (int i = 0; i < order.length(); i++) {
                String kind = order.getString(i);
                profiles.put(kind, new Profile(kind, manifest.getJSONObject(kind)));
            }
            return true;
        } catch (IOException | JSONException error) {
            resourceError = "原版 Tempera 构图资源读取失败";
            return false;
        }
    }

    private JSONObject read(String filename) throws IOException, JSONException {
        try (InputStream input = assets.open(ASSET_ROOT + filename);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return new JSONObject(output.toString("UTF-8"));
        }
    }

    /** Executes the original native-compiled paragraph/shot compiler, including bridge shots. */
    private void compile() {
        program.clear();
        paragraphs.clear();
        List<Object> source = FoliaTemperaTimeline.lines(lines);
        Map<String, Object> compiled = map(FoliaTemperaGeometryProgram.compile(source, seed, wholeLineLyrics));
        List<Object> allParagraphs = list(get(compiled, "paragraphs"));
        for (int p = 0; p < allParagraphs.size(); p++) {
            Object nativeParagraph = allParagraphs.get(p);
            Paragraph paragraph = new Paragraph();
            paragraph.start = number(get(nativeParagraph, "startTime"));
            paragraph.end = number(get(nativeParagraph, "endTime"));
            Object transition = get(nativeParagraph, "transitionOut");
            if (transition != null) {
                paragraph.transition = string(get(transition, "kind"));
                paragraph.transitionStart = number(get(transition, "startTime"));
                paragraph.transitionEnd = number(get(transition, "endTime"));
            }
            List<Object> shots = list(get(nativeParagraph, "shots"));
            for (int i = 0; i < shots.size(); i++) {
                Object nativeShot = shots.get(i);
                Shot shot = new Shot();
                shot.profile = profiles.get(string(get(nativeShot, "kind")));
                shot.id = string(get(nativeShot, "id"));
                shot.start = number(get(nativeShot, "startTime"));
                shot.end = number(get(nativeShot, "endTime"));
                shot.lyricEnd = number(get(nativeShot, "lyricEndTime"));
                shot.flow = number(get(nativeShot, "flowAngle"));
                shot.paragraph = p; shot.index = i;
                shot.vectorSeed = hash(seed + ":" + string(get(nativeParagraph, "id"))) + i * 97L;
                shot.camera = get(nativeShot, "camera");
                shot.cameraEnd = get(nativeShot, "cameraEnd");
                shot.decor = get(nativeShot, "decor");
                shot.motif = string(get(shot.decor, "motif"));
                shot.hatchAngle = number(get(shot.decor, "hatchAngle"));
                shot.crossCount = (int) number(get(shot.decor, "crossCount"));
                shot.scribbleSeed = (long) number(get(shot.decor, "scribbleSeed"));
                for (Object slice : list(get(nativeShot, "slices"))) {
                    Object line = source.get((int) number(get(slice, "lineIndex")));
                    List<Object> segments = list(get(line, "nativeSegments"));
                    int first = (int) number(get(slice, "segmentStart")), end = (int) number(get(slice, "segmentEnd"));
                    List<Object> row = new ArrayList<>();
                    for (int segment = first; segment < end; segment++) if (!string(get(segments.get(segment), "text")).trim().isEmpty()) row.add(segments.get(segment));
                    if (!row.isEmpty()) shot.nativeLines.add(row);
                }
                program.add(shot);
                paragraph.shots.add(shot);
            }
            paragraphs.add(paragraph);
        }
    }

    private void drawShot(Canvas canvas, Shot shot) {
        double duration = Math.max(.001, shot.end - shot.start);
        double handoff = Math.min(1.1, Math.max(.4, duration * .3));
        double exit = animate ? inOut((time - shot.end) / handoff) : time > shot.end ? 1 : 0;
        if (exit >= .999 || time < shot.start) return;
        Drawing drawing = drawing(shot);
        if (drawing == null) return;
        double progress = (time - shot.start) / duration, amount = cameraAmount(progress);
        Profile profile = shot.profile;
        double cameraX = camera(shot, "x", amount), cameraY = camera(shot, "y", amount);
        double scale = camera(shot, "zoom", amount), rotation = camera(shot, "rotation", amount);
        List<Glyph> glyphs = layout(shot);
        double revealDone = shot.start;
        for (Glyph glyph : glyphs) revealDone = Math.max(revealDone, glyph.settle);
        double breathWeight = cubicInOut((time - revealDone) / 1.2);
        if (animate && breathWeight > 0) {
            double phase = hash(shot.id) % 1024 / 1024.0 * Math.PI * 2;
            double[] breath = breath(time, phase);
            cameraX += breath[0] * breathWeight;
            cameraY += breath[1] * breathWeight;
            scale += breath[2] * breathWeight;
            rotation += breath[3] * breathWeight;
        }
        double enter = animate ? enter((time - shot.start) / (handoff * .8)) : 1;
        double travel = exit * Math.max(width, height) * .55 - (1 - enter) * Math.max(width, height) * .32;
        int save = canvas.saveLayerAlpha(0, 0, (float) width, (float) height, (int) (255 * (1 - exit)), Canvas.ALL_SAVE_FLAG);
        canvas.translate((float) (width / 2 + cameraX * width * cameraIntensity + Math.cos(shot.flow) * travel),
                (float) (height / 2 + cameraY * height * cameraIntensity + Math.sin(shot.flow) * travel));
        canvas.scale((float) ((1 + (scale - 1) * cameraIntensity) * (1 - exit * .08)), (float) ((1 + (scale - 1) * cameraIntensity) * (1 - exit * .08)));
        canvas.rotate((float) Math.toDegrees(rotation * cameraIntensity));
        canvas.translate((float) -width / 2, (float) -height / 2);
        FoliaOriginalVectorScene.Motion motion = (node, local) -> blockTransform(node, local, shot, progress);
        FoliaTemperaGpuInversion.Layer artwork = target -> {
            if (showBlocks) drawing.scene.draw(target, palette, motion);
            if (showDecor) drawWatermark(target, shot);
        };
        boolean gpu = textInversion && inversion.draw(canvas, seed + ':' + shot.id, (int) Math.ceil(width), (int) Math.ceil(height),
                palette[2], palette[1], artwork, target -> drawTextMask(target, shot, glyphs, Color.WHITE));
        if (!gpu) { artwork.draw(canvas); drawTextMask(canvas, shot, glyphs, palette[2]); }
        // Flat original vector tones permit GPU clip-path inversion without reading back the framebuffer.
        // Unlike upstream's 5-tap shader, partial-alpha overlapping strokes remain a documented difference.
        if (!gpu && textInversion && showBlocks) drawing.scene.visit(motion, (draw, transform, opacity) -> {
            double luminance = luminance(palette[Math.max(0, Math.min(palette.length - 1, draw.color))]);
            double background = luminance * draw.alpha * opacity + luminance(palette[1]) * (1 - draw.alpha * opacity);
            if (Math.abs(background - luminance(palette[1])) < Math.abs(background - luminance(palette[2]))) return;
            clip.set(draw.silhouette);
            clip.transform(transform);
            int invert = canvas.save();
            canvas.clipPath(clip);
            drawTextMask(canvas, shot, glyphs, palette[1]);
            canvas.restoreToCount(invert);
        });
        if (showDecor && animate) drawEchoes(canvas, glyphs);
        canvas.restoreToCount(save);
    }

    private static double camera(Shot shot, String key, double amount) {
        double start = number(get(shot.camera, key));
        return start + (number(get(shot.cameraEnd, key)) - start) * amount;
    }

    private float blockTransform(FoliaOriginalVectorScene.Node node, Matrix local, Shot shot, double progress) {
        JSONObject options = node.motion;
        if (options == null || !animate) return 1;
        double pace = Math.max(.2, shot.lyricEnd - shot.start), budget = Math.max(.5, pace);
        double delay = Math.min(1.4, Math.max(0, pace * options.optDouble("delay", 0)));
        double span = Math.min(2.6, Math.max(.7, pace * options.optDouble("span", .45)));
        double compress = Math.min(1, budget / (delay + span));
        double enter = enter((time - shot.start - delay * compress) / (span * compress));
        double carry = Math.max(width, height) * .09, creep = inOut(progress) * carry * .35, behind = (1 - enter) * carry - creep;
        double dx = options.optDouble("enterDX") * (1 - enter) - Math.cos(shot.flow) * behind;
        double dy = options.optDouble("enterDY") * (1 - enter) - Math.sin(shot.flow) * behind;
        double floating = options.optBoolean("drift") ? 1 + Math.sin(time * .5 + node.driftPhase) * .02 : 1;
        double sx = (options.optBoolean("grow") ? Math.max(.0001, enter) : 1) * floating;
        local.postScale((float) sx, (float) floating, node.x, node.y);
        if (options.optBoolean("drift")) local.postRotate((float) Math.toDegrees(Math.sin(time * .33 + node.driftPhase) * .012), node.x, node.y);
        local.postTranslate((float) dx, (float) dy);
        return (float) (enter * options.optDouble("alpha", 1));
    }

    private Drawing drawing(Shot shot) {
        Drawing cached = drawings.get(shot);
        if (cached != null) return cached;
        try {
            JSONArray nodes = FoliaTemperaGeometryOps.generate(shot.profile.kind, width, height, shot.vectorSeed,
                    shot.flow, shot.motif, shot.hatchAngle, shot.crossCount, shot.scribbleSeed, showDecor);
            Drawing drawing = new Drawing(new FoliaOriginalVectorScene(nodes), shot.flow);
            drawings.put(shot, drawing);
            return drawing;
        } catch (JSONException error) {
            resourceError = "原版 Tempera 构图资源读取失败";
            return null;
        }
    }

    private List<Glyph> layout(Shot shot) {
        List<Glyph> cached = layouts.get(shot);
        if (cached != null) return cached;
        int maxGraphemes = 3;
        for (Object row : shot.nativeLines) {
            int count = 0;
            for (Object segment : list(row)) count += list(get(segment, "graphemes")).size();
            maxGraphemes = Math.max(maxGraphemes, count);
        }
        shot.baseFont = Math.max(34, Math.min(150, width / Math.max(5, maxGraphemes * 1.05) * 1.5)) * fontScale;
        Fn measure = values -> {
            text.setTextSize((float) number(values[1]));
            return (double) text.measureText(string(values[0])) + tracking * Math.max(0, string(values[0]).codePointCount(0, string(values[0]).length()) - 1);
        };
        Object options = object("lines", shot.nativeLines, "shotKind", shot.profile.kind,
                "width", width, "height", height, "baseFontSize", shot.baseFont,
                "fontFamily", object("measure", measure), "fontWeight", 600d,
                "seed", (double) shot.vectorSeed, "settleStretch", settleStretch);
        List<Glyph> result = new ArrayList<>();
        for (Object placement : list(FoliaTemperaGeometryProgram.layout(options))) {
            Glyph glyph = new Glyph();
            glyph.text = string(get(placement, "char"));
            glyph.x = number(get(placement, "x")); glyph.y = number(get(placement, "y"));
            glyph.rotation = number(get(placement, "rotation")); glyph.fontSize = number(get(placement, "fontSize"));
            glyph.start = number(get(placement, "startTime")); glyph.end = number(get(placement, "endTime"));
            glyph.settle = number(get(placement, "settleTime")); glyph.release = number(get(placement, "releaseTime"));
            glyph.trackingX = number(get(placement, "trackingX")); glyph.trackingY = number(get(placement, "trackingY"));
            glyph.enterX = number(get(placement, "enterX")); glyph.enterY = number(get(placement, "enterY"));
            glyph.enterRotation = number(get(placement, "enterRotation")); glyph.enterScale = number(get(placement, "enterScale"));
            glyph.style = list(ENTER_STYLES).indexOf(get(placement, "enterStyle"));
            result.add(glyph);
        }
        layouts.put(shot, result);
        return result;
    }

    private void drawTextMask(Canvas canvas, Shot shot, List<Glyph> glyphs, int color) {
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(face);
        // Shadows belong to the same filtered mask; the original inserts every shadow below type.
        if (showDecor) for (int i = glyphs.size() - 1; i >= 0; i--) drawGlyph(canvas, glyphs.get(i), color, true);
        for (Glyph glyph : glyphs) drawGlyph(canvas, glyph, color, false);
        if (showDecor) for (Object fragment : list(get(shot.decor, "fragments"))) {
            drawCentered(canvas, string(get(fragment, "char")), number(get(fragment, "x")) * width,
                    number(get(fragment, "y")) * height, Math.max(12, shot.baseFont * number(get(fragment, "scale"))),
                    number(get(fragment, "rotation")), color, .42);
        }
    }

    private void drawGlyph(Canvas canvas, Glyph glyph, int color, boolean shadow) {
        glyph(glyph, time, animate ? glyphMotion : 0, glyphFrame);
        if (!glyphFrame.visible) return;
        int save = canvas.save();
        canvas.translate((float) (glyph.x + glyphFrame.x + (shadow ? glyph.fontSize * .06 : 0)),
                (float) (glyph.y + glyphFrame.y + (shadow ? glyph.fontSize * .08 : 0)));
        canvas.rotate((float) Math.toDegrees(glyphFrame.rotation));
        canvas.scale((float) glyphFrame.scaleX, (float) glyphFrame.scaleY);
        text.setTextSize((float) glyph.fontSize); text.setColor(color);
        text.setAlpha((int) (255 * glyphFrame.alpha * (shadow ? .34 : 1)));
        canvas.drawText(glyph.text, 0, -(text.ascent() + text.descent()) / 2, text);
        canvas.restoreToCount(save);
    }

    private void drawEchoes(Canvas canvas, List<Glyph> glyphs) {
        text.setTextAlign(Paint.Align.CENTER); text.setTypeface(face); text.setColor(palette[12]);
        for (Glyph glyph : glyphs) {
            glyph(glyph, time, glyphMotion, glyphFrame);
            if (!glyphFrame.visible || glyphFrame.echoAlpha <= .004) continue;
            text.setTextSize((float) glyph.fontSize);
            float baseline = -(text.ascent() + text.descent()) / 2;
            for (int echo = 0; echo < 2; echo++) {
                int save = canvas.save(); double depth = 1 + echo * .85;
                canvas.translate((float) (glyph.x + glyphFrame.echoX * depth), (float) (glyph.y + glyphFrame.echoY * depth));
                canvas.rotate((float) Math.toDegrees(glyphFrame.rotation));
                canvas.scale((float) glyphFrame.scaleX, (float) glyphFrame.scaleY);
                text.setAlpha((int) (255 * glyphFrame.echoAlpha / (echo + 1.4)));
                canvas.drawText(glyph.text, 0, baseline, text); canvas.restoreToCount(save);
            }
        }
    }

    private void drawWatermark(Canvas canvas, Shot shot) {
        Object watermark = get(shot.decor, "watermark");
        if (watermark == null) return;
        drawCentered(canvas, string(get(watermark, "text")), number(get(watermark, "x")) * width,
                number(get(watermark, "y")) * height, Math.max(48, shot.baseFont * number(get(watermark, "scale"))),
                number(get(watermark, "rotation")), palette[12], .16);
    }

    private void drawCentered(Canvas canvas, String value, double x, double y, double size,
                              double rotation, int color, double alpha) {
        int save = canvas.save(); canvas.translate((float) x, (float) y); canvas.rotate((float) Math.toDegrees(rotation));
        text.setTypeface(face); text.setTextSize((float) size); text.setTextAlign(Paint.Align.CENTER);
        text.setColor(color); text.setAlpha((int) (255 * alpha));
        canvas.drawText(value, 0, -(text.ascent() + text.descent()) / 2, text); canvas.restoreToCount(save);
    }

    private void palette(int ink, int accent, int muted) {
        int paper = paperColor;
        if (Math.abs(luminance(ink) - luminance(paper)) < 96) {
            int target = luminance(paper) < 128 ? 0xFFF4F4F2 : 0xFF141414;
            int tinted = mix(target, ink, .14);
            ink = Math.abs(luminance(tinted) - luminance(paper)) >= 96 ? tinted : target;
        }
        palette[1] = paper;
        palette[2] = ink;
        palette[3] = mix(paper, accent, .55);
        palette[4] = mix(paper, muted, .6);
        palette[5] = mix(paper, ink, .78);
        palette[6] = accent;
        palette[7] = mix(paper, ink, .6);
        palette[8] = mix(paper, ink, .8);
        palette[9] = tintTone(mix(paper, ink, .12), accent);
        palette[10] = tintTone(mix(paper, ink, .3), muted);
        palette[11] = tintTone(mix(paper, ink, .52), accent);
        palette[12] = mix(paper, ink, .72);
    }

    private static int tintTone(int base, int tint) {
        int mixed = mix(base, tint, .3);
        double gain = luminance(base) / Math.max(.5, luminance(mixed));
        int result = Color.rgb((int) Math.min(255, Math.round(Color.red(mixed) * gain)),
                (int) Math.min(255, Math.round(Color.green(mixed) * gain)), (int) Math.min(255, Math.round(Color.blue(mixed) * gain)));
        return Math.abs(luminance(result) - luminance(base)) > 1.5 ? base : result;
    }

    private static double luminance(int color) { return Color.red(color) * .2126 + Color.green(color) * .7152 + Color.blue(color) * .0722; }
    private static int mix(int from, int to, double amount) {
        return Color.rgb((int) Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
                (int) Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
                (int) Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
    }

    private static final class Profile {
        final String kind, mood, align;
        final double cx, cy, rw, rh, rotation, fontScale, enterX, enterY, travel, zoomStart, zoomEnd;
        Profile(String kind, JSONObject json) throws JSONException {
            this.kind = kind;
            mood = json.getString("mood");
            JSONObject region = json.getJSONObject("region"), enter = json.getJSONObject("enter"), camera = json.getJSONObject("camera");
            cx = region.getDouble("cx"); cy = region.getDouble("cy"); rw = region.getDouble("w"); rh = region.getDouble("h");
            align = region.optString("align", "center"); rotation = region.optDouble("rotation"); fontScale = region.optDouble("fontScale", 1);
            enterX = enter.getDouble("x"); enterY = enter.getDouble("y"); travel = camera.getDouble("travel");
            zoomStart = camera.getDouble("zoomStart"); zoomEnd = camera.getDouble("zoomEnd");
        }
    }

    private static final class Paragraph {
        String transition;
        double start, end, transitionStart, transitionEnd;
        final List<Shot> shots = new ArrayList<>();
    }

    private static final class Shot {
        Profile profile;
        String id, motif;
        int paragraph, index, crossCount;
        long vectorSeed, scribbleSeed;
        double start, end, lyricEnd, flow, hatchAngle, baseFont;
        Object camera, cameraEnd, decor;
        final List<Object> nativeLines = new ArrayList<>();
    }

    private static final class Drawing {
        final FoliaOriginalVectorScene scene;
        final double flow;
        Drawing(FoliaOriginalVectorScene scene, double flow) { this.scene = scene; this.flow = flow; }
    }

}
