// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Canvas translation of Folia lumiere scene, window, motes, starfall and optical line-art programs.
// Original source revision and license: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.ColorFilter;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.zuoqirun.lyricscompanion.FoliaLumiereWindowMath.Move;
import com.zuoqirun.lyricscompanion.FoliaLumiereWindowMath.Slot;
import com.zuoqirun.lyricscompanion.FoliaLumiereWindowMath.Transform;

/** Native optical scene; all motion is a closed function of lyric time, including seek and pause. */
final class FoliaOriginalLumiereRenderer {
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final FoliaLumiereField field = new FoliaLumiereField();
    private final FoliaLumiereField.Frame light = new FoliaLumiereField.Frame();
    private final LruCache<String, Profile> profiles = new LruCache<>(8);
    private final LruCache<String, LineData> prepared = new LruCache<>(14);
    private final LruCache<String, FoliaLumiereWindowMath> windows = new LruCache<>(3);
    private final LruCache<String, List<FoliaLumiereEchoMath.Fragment>> echoes = new LruCache<>(3);
    private final FoliaLumiereEchoMath.Frame echoFrame = new FoliaLumiereEchoMath.Frame();
    private final Matrix echoMatrix = new Matrix();
    private final float[] echoValues = new float[9];
    private final FoliaLumiereWindowMath.ProtectBox[] protectBoxes = new FoliaLumiereWindowMath.ProtectBox[4];
    private int protectCount;
    private double windowVisibility = 1;
    private final LruCache<String, Particle[]> particles = new LruCache<>(4);
    private final LruCache<Integer, ColorFilter> tints = new LruCache<>(16);
    private final Paint spritePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF spriteBounds = new RectF();
    private final double[] camera = new double[4];
    private final float[] lightColor = new float[3], litColor = new float[3], coldColor = new float[3], starColor = new float[3];
    private final Beam[] candidates = new Beam[12];
    private Typeface face;
    private float fontScale = 1, strength = 0.5f, tracking;
    private boolean showTranslation = true, animate = true, lowResource;
    private List<LrcTimeline.Line> lines = Collections.emptyList();
    private List<FoliaLumiereMath.Paragraph> program = Collections.emptyList();
    private String track = "";
    private String geometrySeed = "";
    private String programSeed = "";
    private int paletteKey;
    private float width, height;
    private double time;
    private float paragraphAlpha = 1;
    private FoliaLumiereMath.Paragraph paragraph;
    private Profile lead;
    private String unitSeed;

    FoliaOriginalLumiereRenderer() {
        for (int i = 0; i < candidates.length; i++) candidates[i] = new Beam();
        for (int i = 0; i < protectBoxes.length; i++) protectBoxes[i] = new FoliaLumiereWindowMath.ProtectBox();
        text.setTextAlign(Paint.Align.CENTER);
        ink.setStrokeCap(Paint.Cap.ROUND); ink.setStrokeJoin(Paint.Join.ROUND);
    }

    void setTracking(float value) {
        value = Math.max(0, Math.min(30, value));
        if (tracking != value) { tracking = value; prepared.evictAll(); windows.evictAll(); }
    }

    void setGeometrySeed(String seed) {
        String value = seed == null ? "" : seed;
        if (!geometrySeed.equals(value)) {
            geometrySeed = value; track = "";
            profiles.evictAll(); prepared.evictAll(); windows.evictAll(); particles.evictAll();
        }
    }

    void configure(Typeface face, float fontScale, float strength, boolean showTranslation,
                   boolean animate, boolean lowResourceMode) {
        Typeface resolved = face == null ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : face;
        if (!resolved.equals(this.face) || this.fontScale != fontScale) { prepared.evictAll(); windows.evictAll(); echoes.evictAll(); }
        if (this.lowResource != lowResourceMode) field.clear();
        this.face = resolved; this.fontScale = fontScale; this.strength = strength;
        this.showTranslation = showTranslation; this.animate = animate; this.lowResource = lowResourceMode;
        text.setTypeface(resolved);
    }

    boolean transitioning() { return false; }
    void clear() {
        prepared.evictAll(); windows.evictAll(); echoes.evictAll(); profiles.evictAll(); particles.evictAll(); field.clear();
        lines = Collections.emptyList(); program = Collections.emptyList(); track = "";
    }

    void draw(Canvas canvas, MusicSnapshot snapshot, List<LrcTimeline.Line> incoming, int anchor,
              long lyricTime, long animationClock, long dt, float width, float height, float bass,
              int primary, int accent, int muted) {
        if (width <= 0 || height <= 0 || incoming.isEmpty()) return;
        String identity = snapshot.sourceName + '\n' + snapshot.title + '\n' + snapshot.artist;
        if (!identity.equals(track) || incoming != lines) {
            track = identity; lines = incoming;
            programSeed = geometrySeed.isEmpty() ? identity : geometrySeed;
            program = FoliaLumiereMath.compile(lines, snapshot.durationMs, programSeed);
            profiles.evictAll(); prepared.evictAll(); windows.evictAll(); echoes.evictAll(); particles.evictAll(); field.clear();
        }
        if (this.width != width || this.height != height) {
            this.width = width; this.height = height;
            profiles.evictAll(); prepared.evictAll(); windows.evictAll(); echoes.evictAll(); particles.evictAll(); field.clear();
        }
        if (program.isEmpty()) return;
        time = lyricTime / 1000.0;
        int key = primary * 31 + accent * 17 + muted;
        if (key != paletteKey) { paletteKey = key; palette(primary, accent, muted); field.clear(); }
        ink.setStyle(Paint.Style.FILL); ink.setShader(null); ink.setColor(Color.BLACK); ink.setAlpha(191);
        canvas.drawRect(0, 0, width, height, ink);
        int current = 0;
        for (int i = 1; i < program.size(); i++) if (program.get(i).start <= lyricTime) current = i;
        // Adjacent paragraph holders overlap through the handoff; each retains its own camera.
        double enter = current > 0 ? FoliaLumiereMath.smooth((time - program.get(current).start / 1000.0) / 0.9) : 1;
        if (enter < 1) drawParagraph(canvas, program.get(current - 1), (float) (1 - enter), bass);
        drawParagraph(canvas, program.get(current), (float) enter, bass);
        drawOverlay(canvas);
        if (showTranslation && anchor >= 0 && anchor < lines.size() && !lines.get(anchor).translated.isEmpty()) {
            // Shared subtitle overlay: 20px / 30px line box anchored 34px above the viewport bottom.
            float subtitleSize = 20 * fontScale;
            float subtitleLineHeight = 30 * fontScale;
            float subtitleTop = height - 34 - subtitleLineHeight;
            text.setTextSize(subtitleSize);
            Paint.FontMetrics subtitleMetrics = text.getFontMetrics();
            float subtitleBaseline = subtitleTop
                    + (subtitleLineHeight - (subtitleMetrics.descent - subtitleMetrics.ascent)) / 2f
                    - subtitleMetrics.ascent;
            text.setColor(color(litColor)); text.setAlpha(190);
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(lines.get(anchor).translated, width / 2f, subtitleBaseline, text);
        }
    }

    private void drawParagraph(Canvas canvas, FoliaLumiereMath.Paragraph part, float alpha, float bass) {
        if (alpha < 0.001f || part.shots.isEmpty()) return;
        paragraph = part; paragraphAlpha = alpha;
        FoliaLumiereMath.Shot leadShot = part.shots.get(0);
        for (FoliaLumiereMath.Shot shot : part.shots) if (!shot.bridge) { leadShot = shot; break; }
        unitSeed = programSeed + ":lumiere-p" + leadShot.paragraph;
        lead = profile(leadShot.profile, part.shots.indexOf(leadShot));
        int index = 0;
        for (int i = 1; i < part.shots.size(); i++) if (part.shots.get(i).start / 1000.0 <= time) index = i;
        FoliaLumiereMath.Shot shot = part.shots.get(index);
        Profile active = profile(shot.profile, index), previous = index > 0 ? profile(part.shots.get(index - 1).profile, index - 1) : active;
        double local = time - part.start / 1000.0;
        windowVisibility = FoliaLumiereMath.smooth(local / .6);
        double openingLength = Math.min(lead.starOpening, Math.max(0.8, (part.end - part.start) / 1000.0 * 0.4));
        boolean opening = part.opening && part.end - part.start >= 2000 && lead.stars > 0;
        double ignition = opening ? openingLength * 0.45 : 0;
        double intensity = opening ? FoliaLumiereMath.smooth((local - ignition + 0.4) / 1.6) : 1;
        double flash = opening && local >= ignition ? 1.8 * Math.exp(-(local - ignition) / 0.35) : 0;
        double handoff = index > 0 ? FoliaLumiereMath.smooth((time - shot.start / 1000.0) / 0.9) : 1;
        double glareK = index > 0 ? FoliaLumiereMath.cubic((time - shot.start / 1000.0) / 1.6) : 1;
        buildLight(active, previous, handoff, glareK, intensity, flash, bass,
                time - shot.start / 1000.0, index > 0 ? time - part.shots.get(index - 1).start / 1000.0 : 0);
        FoliaLumiereMath.camera(camera, animate ? time : part.start / 1000.0, part.start / 1000.0,
                part.end / 1000.0, lead.push, lead.driftX, lead.driftY, width, height);
        int saved = canvas.save();
        canvas.translate((float) camera[0], (float) camera[1]);
        canvas.rotate((float) Math.toDegrees(camera[3])); canvas.scale((float) camera[2], (float) camera[2]);
        canvas.translate(-width / 2, -height / 2);
        if (alpha < 0.999f) canvas.saveLayerAlpha(-width, -height, width * 2, height * 2, (int) (alpha * 255), Canvas.ALL_SAVE_FLAG);
        field.draw(canvas, width, height, light, lowResource);
        drawEcho(canvas, part, intensity);
        drawStars(canvas, opening ? local : local + openingLength + 20, openingLength);
        for (int i = Math.max(0, index - 1); i <= Math.min(part.shots.size() - 1, index + 1); i++) {
            FoliaLumiereMath.Shot artShot = part.shots.get(i);
            double begin = i == 0 ? part.start / 1000.0 + ignition - 0.6 : artShot.start / 1000.0 - 0.6;
            double out = i == part.shots.size() - 1 ? 1 : 1 - FoliaLumiereMath.smooth((time - artShot.end / 1000.0) / 0.8);
            drawArt(canvas, profile(artShot.profile, i), (time - begin) / 3.6,
                    FoliaLumiereMath.smooth((time - begin) / 1.2) * out);
        }
        drawMotes(canvas, lead.motes, false);
        drawWindow(canvas);
        if (lead.front != null) drawMotes(canvas, lead.front, true);
        canvas.restoreToCount(saved);
    }

    private Profile profile(int index, int shotIndex) {
        String key = unitSeed + ':' + shotIndex + ':' + index;
        Profile found = profiles.get(key);
        if (found == null) {
            found = new Profile(FoliaLumiereGeometry.build(index, width / height, unitSeed, shotIndex));
            profiles.put(key, found);
        }
        return found;
    }

    private void palette(int primary, int accent, int muted) {
        float[] champagne = {1, 0.86f, 0.62f}, cool = {0.62f, 0.68f, 0.8f};
        float[] a = channels(accent), p = channels(primary), m = channels(muted);
        normalize(a); normalize(p); normalize(m);
        for (int i = 0; i < 3; i++) lightColor[i] = (float) FoliaLumiereMath.mix(FoliaLumiereMath.mix(champagne[i], channel(accent, i), 0.18), a[i], 0.3);
        normalize(lightColor);
        for (int i = 0; i < 3; i++) {
            litColor[i] = (float) FoliaLumiereMath.mix(FoliaLumiereMath.mix(lightColor[i], 1, 0.2), FoliaLumiereMath.mix(p[i], 1, 0.2), 0.3);
            coldColor[i] = (float) FoliaLumiereMath.mix(FoliaLumiereMath.mix(lightColor[i], cool[i], 0.55), FoliaLumiereMath.mix(m[i], cool[i], 0.35), 0.3);
            light.color[i] = lightColor[i];
        }
    }

    private void buildLight(Profile active, Profile previous, double k, double glareK,
                            double intensity, double ignite, float bass, double local, double previousLocal) {
        int count = resolve(active, k * intensity, bass, local, 0);
        if (k < 1) count = resolve(previous, (1 - k) * intensity, bass, previousLocal, count);
        // Preserve the original six brightest beams when two rigs overlap.
        if (k < 1) for (int i = 0; i < count; i++) for (int j = i + 1; j < count; j++) if (candidates[j].c[0] > candidates[i].c[0]) {
            Beam swap = candidates[i]; candidates[i] = candidates[j]; candidates[j] = swap;
        }
        light.count = Math.min(6, count); light.time = time;
        light.identity = active.data.optString("kind").hashCode() * 31
                + previous.data.optString("kind").hashCode() + paletteKey;
        for (int i = 0; i < light.count; i++) {
            Beam beam = candidates[i];
            System.arraycopy(beam.a, 0, light.a, i * 4, 4); System.arraycopy(beam.b, 0, light.b, i * 4, 4);
            System.arraycopy(beam.c, 0, light.c, i * 4, 4); System.arraycopy(beam.d, 0, light.d, i * 4, 4);
            System.arraycopy(beam.e, 0, light.e, i * 4, 4); System.arraycopy(beam.f, 0, light.f, i * 4, 4);
        }
        String[] fogKeys = {"density", "tyndallBase", "scale", "warp", "driftX", "driftY", "ambient"};
        for (int i = 0; i < fogKeys.length; i++) {
            float value = (float) FoliaLumiereMath.mix(number(previous.fog, fogKeys[i], 0), number(active.fog, fogKeys[i], 0), k);
            if (i < 4) light.fogA[i] = value; else light.fogB[i - 4] = value;
        }
        light.fogB[3] = lowResource ? 3 : 5;
        JSONObject ga = active.rig.optJSONObject("glare"), gp = previous.rig.optJSONObject("glare");
        JSONObject gaw = active.wide.optJSONObject("glare"), gpw = previous.wide.optJSONObject("glare");
        double aspect = width / height;
        light.glare[0] = (float) FoliaLumiereMath.mix(position(gp, gpw, "x", aspect, true), position(ga, gaw, "x", aspect, true), glareK);
        light.glare[1] = (float) FoliaLumiereMath.mix(number(gp, "y", 0), number(ga, "y", 0), glareK);
        light.glare[2] = (float) FoliaLumiereMath.mix(number(gp, "radius", 0.12), number(ga, "radius", 0.12), glareK);
        double glareIntensity = ga != null && gp != null ? FoliaLumiereMath.mix(number(gp, "intensity", 0), number(ga, "intensity", 0), glareK)
                : k < 0.5 ? number(gp, "intensity", 0) * (1 - k * 2) : number(ga, "intensity", 0) * (k * 2 - 1);
        light.glare[3] = (float) (glareIntensity * intensity * (1 + ignite));
        light.glareB[0] = (float) FoliaLumiereMath.mix(number(gp, "streak", 0), number(ga, "streak", 0), glareK);
        JSONObject selected = k < 0.5 ? previous.rig : active.rig;
        JSONObject caustic = selected.optJSONObject("caustic"), floor = caustic == null ? null : caustic.optJSONObject("floor");
        double fade = k < 0.5 ? 1 - k * 2 : k * 2 - 1;
        light.caustic[0] = (float) number(caustic, "scale", 1); light.caustic[1] = (float) number(caustic, "speed", 0);
        light.caustic[2] = (float) (number(caustic, "inBeam", 0) * fade); light.caustic[3] = caustic == null ? 0 : 1;
        light.causticFloor[0] = (float) (number(floor, "cx", 0.5) * aspect); light.causticFloor[1] = (float) number(floor, "cy", 0.5);
        light.causticFloor[2] = (float) (number(floor, "rx", 1) * aspect); light.causticFloor[3] = (float) number(floor, "ry", 1);
        light.causticB[0] = (float) (number(floor, "strength", 0) * fade);
        JSONObject wave = selected.optJSONObject("wave");
        light.wave[0] = pattern(wave == null ? "" : wave.optString("mode"), new String[]{"rings", "slits", "sources", "airy"});
        light.wave[1] = (float) number(wave, "frequency", 1); light.wave[2] = (float) number(wave, "speed", 0);
        light.wave[3] = (float) (number(wave, "strength", 0) * fade);
        light.waveB[0] = (float) (number(wave, "cx", 0.5) * aspect); light.waveB[1] = (float) number(wave, "cy", 0.5);
        light.waveB[2] = (float) number(wave, "radius", 1); light.waveB[3] = (float) number(wave, "separation", 0);
        light.shield[0] = lead.cx * (float) aspect; light.shield[1] = lead.cy;
        light.shield[2] = lead.regionWidth * (float) aspect / 2; light.shield[3] = lead.regionHeight / 2;
        light.shieldB[0] = (float) number(wave, "shield", 0.45);
    }

    private int resolve(Profile profile, double weight, float bass, double local, int offset) {
        JSONArray beams = profile.rig.optJSONArray("beams"), wide = profile.wide.optJSONArray("beams");
        if (beams == null) return offset;
        double aspect = width / height;
        for (int i = 0; i < Math.min(6, beams.length()) && offset < candidates.length; i++) {
            JSONObject b = beams.optJSONObject(i), w = wide == null ? b : wide.optJSONObject(i);
            Beam out = candidates[offset++];
            double angle = number(b, "angle", 0);
            if (Math.abs(angle - number(w, "angle", angle)) > 1e-7) angle = Math.atan2(Math.sin(angle), Math.cos(angle) * aspect);
            angle += oscillate(b.optJSONObject("sway"));
            out.a[0] = (float) position(b, w, "x", aspect, true); out.a[1] = (float) number(b, "y", 0);
            out.a[2] = (float) Math.cos(angle); out.a[3] = (float) Math.sin(angle);
            out.b[0] = (float) number(b, "width", 0) / 2; out.b[1] = (float) Math.tan(number(b, "spread", 0));
            out.b[2] = (float) number(b, "softness", 0.5);
            double reveal = b.has("reveal") ? 0.06 + 0.94 * FoliaLumiereMath.smooth(local / number(b, "reveal", 1)) : 1;
            out.b[3] = (float) (number(b, "length", 1) * reveal);
            out.c[0] = (float) (number(b, "intensity", 1) * (1 + oscillate(b.optJSONObject("pulse"))) * weight * (1 + 0.15 * Math.pow(Math.max(0, bass), 1.5)));
            out.c[1] = (float) number(b, "streaks", 0); out.c[2] = (float) number(b, "streakFreq", 0);
            out.c[3] = (float) (time * number(b, "streakSpeed", 0));
            JSONArray tint = b.optJSONArray("tint");
            for (int c = 0; c < 3; c++) out.d[c] = lightColor[c] * (tint == null ? 1 : (float) tint.optDouble(c, 1));
            out.d[3] = (float) number(b, "core", 0.5);
            JSONObject gobo = b.optJSONObject("gobo");
            out.e[0] = pattern(gobo == null ? "" : gobo.optString("pattern"), new String[]{"blinds", "cross", "lattice", "leaves"});
            out.e[1] = (float) number(gobo, "frequency", 0); out.e[2] = (float) number(gobo, "duty", 1);
            out.e[3] = (float) (number(gobo, "drift", 0) * time); out.f[0] = (float) number(b, "spectrum", 0);
            double reach = number(b, "reach", 0), wideReach = number(w, "reach", reach);
            if (reach > 0 && Math.abs(wideReach - reach) > 1e-7) {
                double base = number(b, "angle", 0);
                if (Math.abs(base - number(w, "angle", base)) > 1e-7) reach = (reach - 0.02) * Math.hypot(Math.cos(base) * aspect, Math.sin(base)) + 0.02;
                else reach = FoliaLumiereMath.mix(reach, wideReach, aspect - 1);
            }
            out.f[1] = (float) reach;
        }
        return offset;
    }

    private double oscillate(JSONObject osc) {
        return osc == null || !animate ? 0 : number(osc, "amplitude", 0)
                * Math.sin((time / Math.max(0.001, number(osc, "period", 1)) + number(osc, "phase", 0)) * Math.PI * 2);
    }

    private void drawArt(Canvas canvas, Profile profile, double progress, double fade) {
        fade *= profile.artGain;
        if (fade <= 0.003) return;
        for (ArtPath spec : profile.paths) {
            double local = FoliaLumiereMath.clamp((progress - spec.delay) / Math.max(0.001, spec.span));
            double amount = (1 - Math.pow(1 - local, 3)) * spec.total;
            if (Math.abs(amount - spec.drawn) > 1e-5) spec.redraw(amount, height);
            double lit = light.light(spec.midX, spec.midY);
            double opacity = Math.min(1, spec.alpha * fade * (0.55 + 0.9 * lit));
            ink.setShader(null); ink.setStyle(Paint.Style.STROKE); ink.setColor(color(lightColor));
            // The recipe expands dashes itself, preserving cumulative phase across polyline corners.
            ink.setPathEffect(null);
            ink.setStrokeWidth((float) (spec.width * height * 3.2)); ink.setAlpha((int) (opacity * 0.12 * 255)); canvas.drawPath(spec.path, ink);
            ink.setStrokeWidth((float) (spec.width * height)); ink.setAlpha((int) (opacity * 255)); canvas.drawPath(spec.path, ink);
        }
        ink.setStyle(Paint.Style.FILL);
        JSONArray nodes = profile.art.optJSONArray("nodes");
        if (nodes != null) for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.optJSONObject(i);
            JSONArray at = node.optJSONArray("at");
            if (at == null) continue;
            double x = at.optDouble(0), y = at.optDouble(1);
            double appear = FoliaLumiereMath.clamp((progress - number(node, "delay", 0)) / 0.08);
            double twinkle = 0.55 + 0.45 * Math.sin(time * 2.3 + number(node, "twinklePhase", 0));
            double opacity = appear * fade * (0.25 + 0.75 * light.light(x, y)) * twinkle;
            star(canvas, (float) x * height, (float) y * height,
                    (float) (number(node, "size", 0.02) * height * (1 + (1 - appear) * 1.5) * (0.8 + 0.2 * twinkle)), lightColor, opacity);
        }
    }

    private void drawMotes(Canvas canvas, JSONObject spec, boolean front) {
        if (spec == null) return;
        String key = unitSeed + (front ? ":front" : ":motes");
        Particle[] data = particles.get(key);
        if (data == null) {
            int count = Math.min(front ? 20 : 600, (int) number(spec, "count", 0));
            data = new Particle[count]; FoliaLumiereMath.Random rng = new FoliaLumiereMath.Random(key);
            for (int i = 0; i < count; i++) {
                Particle p = new Particle(); p.depth = rng.next(); p.x = rng.next() * (width / height + 0.16) - 0.08; p.y = rng.next() * 1.16 - 0.08;
                p.size = number(spec, "sizeMin", 0.004) + (number(spec, "sizeMax", 0.013) - number(spec, "sizeMin", 0.004)) * p.depth * p.depth;
                p.phaseA = rng.next() * Math.PI * 2; p.phaseB = rng.next() * Math.PI * 2;
                p.freqA = 0.6 + rng.next() * 0.8; p.freqB = 0.5 + rng.next() * 0.9;
                p.twinklePhase = rng.next() * Math.PI * 2; p.twinkleFreq = 0.8 + rng.next() * 2.2; data[i] = p;
            }
            particles.put(key, data);
        }
        double omega = Math.PI * 2 / Math.max(0.1, number(spec, "swirlPeriod", 9));
        int skip = lowResource && !front ? 2 : 1;
        for (int i = 0; i < data.length; i += skip) {
            Particle p = data[i]; double speed = 0.45 + p.depth * 0.9, swirl = number(spec, "swirl", 0.02) * (0.5 + p.depth);
            double x = FoliaLumiereMath.wrap(p.x + number(spec, "driftX", 0) * speed * time + swirl * Math.sin(time * omega * p.freqA + p.phaseA), -0.08, width / height + 0.08);
            double y = FoliaLumiereMath.wrap(p.y + number(spec, "driftY", 0) * speed * time + swirl * Math.cos(time * omega * p.freqB + p.phaseB), -0.08, 1.08);
            double twinkleAmount = number(spec, "twinkle", 0.5);
            double twinkle = 1 - twinkleAmount + twinkleAmount * (0.5 + 0.5 * Math.sin(time * p.twinkleFreq * Math.PI * 2 + p.twinklePhase));
            double alpha = Math.min(1, (light.light(x, y) * number(spec, "gain", 1.3) + number(spec, "ambient", 0.015)) * twinkle);
            glow(canvas, (float) x * height, (float) y * height, (float) p.size * height / 2, lightColor, alpha, front);
        }
    }

    private void drawStars(Canvas canvas, double local, double opening) {
        if (lead.stars <= 0) return;
        FoliaLumiereMath.Random rng = new FoliaLumiereMath.Random(unitSeed + ":starfall");
        for (int i = 0; i < Math.min(900, lead.stars); i++) {
            boolean bright = rng.next() < 0.08; double x = rng.next() * width / height;
            double y = 0.02 + Math.pow(rng.next(), 1.7) * 0.9, from = -0.05 - rng.next() * 0.25;
            double delay = opening * (0.02 + 0.7 * Math.pow(rng.next(), 1.4)), fall = 0.45 + rng.next() * 0.7;
            double size = bright ? 0.018 + rng.next() * 0.02 : 0.003 + rng.next() * 0.006;
            double phase = rng.next() * Math.PI * 2, frequency = 0.4 + rng.next() * 1.8;
            if (lowResource && i % 2 != 0) continue;
            double progress = (local - delay) / fall;
            if (progress <= 0) continue;
            double py = FoliaLumiereMath.mix(from, y, 1 - Math.pow(1 - FoliaLumiereMath.clamp(progress), 3));
            double flash = local >= delay + fall ? Math.exp(-(local - delay - fall) / 0.25) : 0;
            double twinkle = 0.55 + 0.45 * Math.sin(time * frequency * Math.PI * 2 + phase);
            double alpha = Math.min(1, ((progress < 1 ? 0.9 : (bright ? 0.7 : 0.45) * twinkle) + light.light(x, py) * 0.8 + flash) * lead.starBrightness);
            if (bright) star(canvas, (float) x * height, (float) py * height, (float) (size * height * (1 + flash * 1.5)), lightColor, alpha);
            else glow(canvas, (float) x * height, (float) py * height, (float) size * height / 2, lightColor, alpha, false);
            if (progress < 1) {
                double speed = (y - from) * 3 * Math.pow(1 - progress, 2) / fall;
                ink.setShader(null); ink.setColor(color(lightColor)); ink.setAlpha((int) (alpha * 0.6 * 255));
                ink.setStrokeWidth(Math.max(1.5f, (float) size * height * 0.7f));
                canvas.drawLine((float) x * height, (float) py * height, (float) x * height, (float) (py - Math.min(0.25, speed * 0.09)) * height, ink);
            }
        }
        int rain = Math.min(360, lead.rain);
        for (int i = 0; i < rain; i++) {
            double spread = (rng.next() + rng.next() + rng.next() - 1.5) / 1.5;
            double x = width / height * (0.5 + spread * lead.rainSpread * 0.5);
            double period = 1.25 / Math.max(lead.rainSpeed, 0.01) * (0.7 + rng.next() * 0.6), phase = rng.next();
            double size = 0.003 + rng.next() * 0.005, sway = (rng.next() - 0.5) * 0.02;
            double cycle = FoliaLumiereMath.fract(time / period + phase), y = -0.1 + cycle * 1.25;
            x += Math.sin(time * 0.7 + phase * 6) * sway;
            double alpha = Math.min(1, (0.18 + light.light(x, y) * 1.1) * FoliaLumiereMath.clamp(cycle / 0.08)
                    * FoliaLumiereMath.clamp((1 - cycle) / 0.15) * FoliaLumiereMath.clamp((local - opening * 0.6) / 1.5) * lead.starBrightness);
            glow(canvas, (float) x * height, (float) y * height, (float) size * height / 2, lightColor, alpha, false);
        }
    }

    private void drawEcho(Canvas canvas, FoliaLumiereMath.Paragraph part, double intensity) {
        double font = height * number(lead.data.optJSONObject("echo"), "size", .34);
        List<FoliaLumiereEchoMath.Fragment> fragments = echoes.get(unitSeed);
        if (fragments == null) {
            List<FoliaLumiereEchoMath.Input> inputs = new ArrayList<>();
            text.setTypeface(face); text.setTextSize((float) font);
            for (int i = part.first; i < part.last; i++) {
                LrcTimeline.Line line = lines.get(i);
                FoliaGlyphTimeline offsets = new FoliaGlyphTimeline(line, i + 1 < lines.size() ? lines.get(i + 1).timeMs : -1);
                FoliaOriginalGraphemeTiming timing = new FoliaOriginalGraphemeTiming(line, i + 1 < lines.size() ? lines.get(i + 1).timeMs : -1, false);
                FoliaLumiereEchoMath.Input input = new FoliaLumiereEchoMath.Input();
                input.glyphs = timing.glyphs; input.starts = new double[timing.starts.length];
                input.offsets = new double[timing.glyphs.length + 1]; input.words = words(offsets, line.text);
                StringBuilder prefix = new StringBuilder();
                for (int g = 0; g < timing.glyphs.length; g++) {
                    input.starts[g] = timing.starts[g] / 1000.0;
                    prefix.append(timing.glyphs[g]);
                    input.offsets[g + 1] = text.measureText(prefix.toString()) + (g + 1) * font * .04;
                }
                inputs.add(input);
            }
            fragments = FoliaLumiereEchoMath.build(inputs, unitSeed); echoes.put(unitSeed, fragments);
        }
        double ox = light.count > 0 ? light.a[0] : width / height / 2, oy = light.count > 0 ? light.a[1] : 0;
        double bx = light.count > 0 ? light.a[2] : 0, by = light.count > 0 ? light.a[3] : 1;
        double half = light.count > 0 ? light.b[0] : .08, spread = light.count > 0 ? light.b[1] : 0;
        double visibility = FoliaLumiereMath.smooth((time - part.start / 1000.0) / 1.2);
        for (FoliaLumiereEchoMath.Fragment fragment : fragments) {
            FoliaLumiereEchoMath.frame(echoFrame, fragment, time, height, ox, oy, bx, by, half, spread);
            double alpha = echoFrame.presence * visibility;
            if (alpha <= .003) continue;
            double glyphAlpha = alpha * (.05 + .4 * light.light(echoFrame.x / height, echoFrame.y / height));
            echoValues[0] = (float) (Math.cos(echoFrame.rotation) * echoFrame.scaleX);
            echoValues[3] = (float) (Math.sin(echoFrame.rotation) * echoFrame.scaleX);
            echoValues[1] = (float) (-Math.sin(echoFrame.rotation - echoFrame.skew) * echoFrame.scaleY);
            echoValues[4] = (float) (Math.cos(echoFrame.rotation - echoFrame.skew) * echoFrame.scaleY);
            echoValues[2] = (float) echoFrame.x; echoValues[5] = (float) echoFrame.y;
            echoValues[6] = echoValues[7] = 0; echoValues[8] = 1; echoMatrix.setValues(echoValues);
            int saved = canvas.save(); canvas.concat(echoMatrix);
            text.setTypeface(face); text.setTextSize((float) font); text.setColor(color(lightColor));
            Paint.FontMetrics metrics = text.getFontMetrics();
            for (FoliaLumiereEchoMath.Glyph glyph : fragment.glyphs) {
                if (glyph.text.trim().isEmpty()) continue;
                int glyphSaved = canvas.save();
                canvas.translate((float) (glyph.offset + glyph.dx * font * echoFrame.split * glyph.speed),
                        (float) (glyph.dy * font * echoFrame.split * glyph.speed));
                canvas.rotate((float) Math.toDegrees(glyph.spin * echoFrame.split * 4));
                float baseline = -(metrics.ascent + metrics.descent) / 2;
                text.setStyle(Paint.Style.FILL); text.setAlpha((int) (glyphAlpha * .14 * 255));
                canvas.drawText(glyph.text, 0, baseline, text);
                text.setStyle(Paint.Style.STROKE); text.setStrokeWidth((float) (font * .012)); text.setAlpha((int) (glyphAlpha * 255));
                canvas.drawText(glyph.text, 0, baseline, text); canvas.restoreToCount(glyphSaved);
            }
            canvas.restoreToCount(saved);
        }
        text.setStyle(Paint.Style.FILL);
    }

    private void drawOverlay(Canvas canvas) {
        float unit = Math.min(width, height), px = Math.max(30, width * 0.065f), py = Math.max(30, height * 0.085f);
        float arm = unit * 0.045f, mark = unit * 0.012f;
        ink.setShader(null); ink.setStyle(Paint.Style.STROKE); ink.setPathEffect(null);
        ink.setStrokeWidth(Math.max(1.2f, unit / 600)); ink.setColor(color(lightColor)); ink.setAlpha(128);
        for (int i = 0; i < 4; i++) {
            float x = i == 0 || i == 3 ? px : width - px, y = i < 2 ? py : height - py;
            float sx = i == 0 || i == 3 ? 1 : -1, sy = i < 2 ? 1 : -1;
            path.rewind(); path.moveTo(x, y + sy * arm); path.lineTo(x, y); path.lineTo(x + sx * arm, y); canvas.drawPath(path, ink);
        }
        ink.setAlpha(102);
        for (float x : new float[]{px, width - px}) {
            canvas.drawCircle(x, height / 2, mark * 0.6f, ink); canvas.drawLine(x - mark, height / 2, x + mark, height / 2, ink);
            canvas.drawLine(x, height / 2 - mark, x, height / 2 + mark, ink);
        }
        ink.setAlpha(70);
        for (int side : new int[]{-1, 1}) for (int k = 0; k < 4; k++) {
            float x = width / 2 + side * (unit * 0.1f + k * unit * 0.018f);
            canvas.drawLine(x, py, x + side * unit * 0.009f, py, ink);
        }
        ink.setStyle(Paint.Style.FILL);
    }

    private void sprite(Canvas canvas, Bitmap bitmap, float x, float y, float width, float height,
                        float[] rgb, double alpha) {
        if (alpha <= .003 || width <= 0 || height <= 0) return;
        int color = color(rgb); ColorFilter tint = tints.get(color);
        if (tint == null) { tint = new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY); tints.put(color, tint); }
        spritePaint.setColorFilter(tint); spritePaint.setAlpha((int) (FoliaLumiereMath.clamp(alpha) * 255));
        spriteBounds.set(x - width / 2, y - height / 2, x + width / 2, y + height / 2);
        canvas.drawBitmap(bitmap, null, spriteBounds, spritePaint);
    }

    private void glow(Canvas canvas, float x, float y, float radius, float[] color, double alpha, boolean ring) {
        sprite(canvas, ring ? FoliaLumiereSprites.BOKEH : FoliaLumiereSprites.DOT, x, y, radius * 2, radius * 2, color, alpha);
    }

    private void star(Canvas canvas, float x, float y, float size, float[] color, double alpha) {
        sprite(canvas, FoliaLumiereSprites.STAR, x, y, size, size, color, alpha);
    }

    private void drawWindow(Canvas canvas) {
        int cursor = paragraph.first - 1;
        for (int i = paragraph.first; i < paragraph.last; i++) if (lines.get(i).timeMs / 1000.0 - 1.2 <= time) cursor = i;
        int first = cursor;
        while (first >= paragraph.first && lines.get(first).timeMs / 1000.0 - 1.2 + 2.55 > time) first--;
        int low = Math.max(paragraph.first, Math.min(cursor, first + 1) - 3), high = Math.min(paragraph.last - 1, Math.max(cursor, paragraph.first) + 3);
        Transform[] frameTransforms = new Transform[Math.max(0, high - low + 1)];
        for (int i = low; i <= high; i++) frameTransforms[i - low] = transform(i, time);
        protectCount = 0; double later = 0;
        for (int i = cursor; i >= low && protectCount < protectBoxes.length; i--) {
            double eased = FoliaLumiereMath.sine((time - (lines.get(i).timeMs / 1000.0 - 1.2)) / 1.5);
            Transform t = frameTransforms[i - low]; double o = FoliaLumiereMath.clamp(t.orient);
            double weight = (eased - later) * FoliaLumiereMath.clamp(t.alpha) * (1 - 4 * o * (1 - o));
            later = eased;
            if (weight > .0001) {
                LineData d = line(i); double w = t.wrap;
                double inkW = FoliaLumiereMath.mix(FoliaLumiereMath.mix(d.flow[0][0].inkWidth, d.flow[0][1].inkWidth, w),
                        FoliaLumiereMath.mix(d.flow[1][0].inkWidth, d.flow[1][1].inkWidth, w), t.orient);
                double inkH = FoliaLumiereMath.mix(FoliaLumiereMath.mix(d.flow[0][0].inkHeight, d.flow[0][1].inkHeight, w),
                        FoliaLumiereMath.mix(d.flow[1][0].inkHeight, d.flow[1][1].inkHeight, w), t.orient);
                FoliaLumiereWindowMath.ProtectBox box = protectBoxes[protectCount++];
                box.line = i; box.x = t.x; box.y = t.y; box.cos = Math.cos(t.rotation); box.sin = Math.sin(t.rotation);
                box.halfW = inkW / 2 * t.scale; box.halfH = inkH / 2 * t.scale;
                box.margin = .5 * d.font * t.scale; box.weight = weight;
            }
            if (eased >= 1) break;
        }
        // Pixi's spots container is below tracks, halos, glyphs and stars.
        for (int i = low; i <= high; i++) drawSpot(canvas, line(i), frameTransforms[i - low]);
        for (int i = low; i <= high; i++) {
            LineData data = line(i);
            Transform transform = frameTransforms[i - low];
            if (transform.alpha <= 0.003) continue;
            for (int g = 0; g < data.glyphs.length; g++) {
                Glyph glyph = data.glyphs[g];
                if (glyph.text.trim().isEmpty()) continue;
                float[] local = local(data, glyph, transform, time);
                double cos = Math.cos(transform.rotation), sin = Math.sin(transform.rotation);
                float x = (float) (transform.x + (local[0] * cos - local[1] * sin) * transform.scale);
                float y = (float) (transform.y + (local[0] * sin + local[1] * cos) * transform.scale);
                float font = (float) (data.font * glyph.scale * transform.scale * local[3]);
                double lit = FoliaLumiereMath.smooth((time - glyph.start) / Math.max(glyph.end - glyph.start, 0.3));
                double age = time - glyph.start, flash = age <= 0 ? 0 : age < 0.15 ? FoliaLumiereMath.smooth(age / 0.15) : Math.exp(-(age - 0.15) / 0.45);
                double illumination = light.light(x / height, y / height), heat = FoliaLumiereMath.clamp(illumination * 1.3 + flash * 0.2);
                double decay = FoliaLumiereMath.decay(time, glyph.start, lead.decayDelay, lead.decayStrength) * glyph.driftSpeed;
                double lineEnd = lines.get(i).timeMs / 1000.0 + (lines.get(i).durationMs > 0 ? lines.get(i).durationMs / 1000.0
                        : i + 1 < lines.size() ? (lines.get(i + 1).timeMs - lines.get(i).timeMs) / 1000.0 : 5);
                double passed = i < cursor || i == cursor && time > lineEnd
                        ? FoliaLumiereMath.mix(1, .75, FoliaLumiereMath.clamp((time - lineEnd) / 2.5)) : 1;
                double shield = FoliaLumiereWindowMath.shield(protectBoxes, protectCount, i, x, y, font / 2, local[5]);
                double alpha = transform.alpha * windowVisibility * passed * (1 - FoliaLumiereMath.clamp((decay - 1) / 2)) * (0.35 + 0.65 * local[4]) * shield;
                double revealed = Math.min(1, 0.22 + illumination * 0.45 + local[5] * 0.4);
                double glyphAlpha = alpha * FoliaLumiereMath.mix(revealed, Math.min(1, 0.55 + 0.4 * heat + local[5] * 0.3), lit);
                if (local[5] > 0.01 && !lowResource) drawTrail(canvas, i, data, glyph, transform, time, alpha);
                float haloSize = font * (2.2f + .8f * (float) illumination);
                sprite(canvas, FoliaLumiereSprites.DOT, x, y, haloSize, haloSize * .9f, litColor,
                        alpha * lit * (0.03 + 0.14 * illumination + 0.08 * flash));
                int saved = canvas.save(); canvas.translate(x, y);
                canvas.rotate((float) Math.toDegrees(transform.rotation + local[2]));
                text.setTypeface(face); text.setTextSize(Math.max(1, font)); text.setStyle(Paint.Style.FILL);
                text.setColor(glyphColor(illumination, flash, local[5], lit)); text.setAlpha((int) (FoliaLumiereMath.clamp(glyphAlpha) * 255));
                Paint.FontMetrics metrics = text.getFontMetrics(); canvas.drawText(glyph.text, 0, -(metrics.ascent + metrics.descent) / 2, text);
                canvas.restoreToCount(saved);
                float starX = x + font * glyph.starX, starY = y + font * glyph.starY;
                int starSaved = canvas.save(); canvas.rotate((float) Math.toDegrees(glyph.starRotation * (1 + (1 - flash) * .6)), starX, starY);
                for (int c = 0; c < 3; c++) starColor[c] = (litColor[c] + 1) / 2;
                star(canvas, starX, starY, font * glyph.starSize * (0.8f + 0.5f * (float) flash),
                        starColor, transform.alpha * windowVisibility * flash * .55 * shield);
                canvas.restoreToCount(starSaved);
            }
        }
    }

    private void drawSpot(Canvas canvas, LineData data, Transform transform) {
        if (data.glyphs.length == 0) return;
        double alpha = transform.alpha * windowVisibility * FoliaLumiereMath.smooth((time - data.singStart + .3) / .3)
                * (1 - FoliaLumiereMath.smooth((time - data.singEnd) / .6));
        if (alpha <= .003) return;
        int orient = transform.moves.isEmpty() ? (transform.restOrient >= .5 ? 1 : 0)
                : transform.moves.get(transform.moves.size() - 1).orient >= .5 ? 1 : 0;
        double x = 0, y = 0;
        for (int sample = 0; sample < 8; sample++) {
            double at = time - .3 * sample / 7, position = 0;
            for (Glyph glyph : data.glyphs) position += FoliaLumiereMath.clamp((at - glyph.start) / Math.max(glyph.end - glyph.start, .08));
            double index = Math.min(data.glyphs.length - 1, Math.max(0, position - .5));
            int lo = (int) Math.floor(index), hi = Math.min(data.glyphs.length - 1, lo + 1);
            double[] single = data.flow[orient][0].spots, wrapped = data.flow[orient][1].spots;
            double sx = FoliaLumiereMath.mix(single[lo * 2], single[hi * 2], index - lo);
            double sy = FoliaLumiereMath.mix(single[lo * 2 + 1], single[hi * 2 + 1], index - lo);
            double wx = FoliaLumiereMath.mix(wrapped[lo * 2], wrapped[hi * 2], index - lo);
            double wy = FoliaLumiereMath.mix(wrapped[lo * 2 + 1], wrapped[hi * 2 + 1], index - lo);
            x += FoliaLumiereMath.mix(sx, wx, transform.wrap) / 8;
            y += FoliaLumiereMath.mix(sy, wy, transform.wrap) / 8;
        }
        double cos = Math.cos(transform.rotation), sin = Math.sin(transform.rotation);
        float px = (float) (transform.x + (x * cos - y * sin) * transform.scale);
        float py = (float) (transform.y + (x * sin + y * cos) * transform.scale);
        float size = (float) (data.font * transform.scale * 5);
        int saved = canvas.save(); canvas.rotate((float) Math.toDegrees(transform.rotation), px, py);
        sprite(canvas, FoliaLumiereSprites.DOT, px, py, size * (orient == 1 ? 1 : 1.6f), size * (orient == 1 ? 1.6f : 1), litColor, .07 * alpha);
        canvas.restoreToCount(saved);
    }

    private int glyphColor(double illumination, double flash, double flying, double lit) {
        int rgb = 0xff000000;
        double white = FoliaLumiereMath.clamp(0.08 * illumination + 0.1 * flash + 0.25 * flying);
        for (int i = 0; i < 3; i++) {
            double hot = FoliaLumiereMath.mix(litColor[i], 1, white), cold = FoliaLumiereMath.mix(coldColor[i], litColor[i], illumination * 0.35);
            rgb |= (int) Math.round(FoliaLumiereMath.clamp(FoliaLumiereMath.mix(cold, hot, lit)) * 255) << (16 - i * 8);
        }
        return rgb;
    }

    private void drawTrail(Canvas canvas, int index, LineData line, Glyph glyph, Transform transform, double at, double alpha) {
        double start = Math.max(transform.slideStart, at - 0.45);
        if (Double.isInfinite(start) || Double.isNaN(start) || at - start < 0.001) return;
        if (at > transform.slideStart + 1.5 + .45) return;
        if (line.trailTime != at) {
            line.trailTime = at;
            for (int i = 0; i <= 20; i++) line.trailTransforms[i] = transform(index, start + (at - start) * i / 20);
            line.trailFlying = false;
            for (Transform t : line.trailTransforms) for (Move move : t.moves) if (move.fly) line.trailFlying = true;
        }
        if (!line.trailFlying) return;
        path.rewind(); double length = 0; float px = 0, py = 0;
        float fontPx = line.font; double flying = 0;
        for (int i = 0; i <= 20; i++) {
            double sample = start + (at - start) * i / 20;
            Transform t = line.trailTransforms[i]; float[] p = local(line, glyph, t, sample);
            fontPx = (float) (line.font * t.scale * glyph.scale * p[3]); flying = p[5];
            double cos = Math.cos(t.rotation), sin = Math.sin(t.rotation);
            float wobble = (float) (Math.sin(i * 0.9 + glyph.wobble + sample * 6) * line.font * 0.02);
            float x = (float) (t.x + (p[0] * cos - p[1] * sin) * t.scale) + wobble;
            float y = (float) (t.y + (p[0] * sin + p[1] * cos) * t.scale) - wobble * 0.6f;
            if (i == 0) path.moveTo(x, y); else { path.lineTo(x, y); length += Math.hypot(x - px, y - py); }
            px = x; py = y;
        }
        double strength = Math.min(1, length / (line.font * 3));
        if (strength > 0.05) {
            double shield = FoliaLumiereWindowMath.shield(protectBoxes, protectCount, index, px, py, fontPx / 2, flying);
            ink.setStyle(Paint.Style.STROKE); ink.setShader(null); ink.setStrokeWidth(1.2f);
            ink.setColor(color(litColor)); ink.setAlpha((int) (FoliaLumiereMath.clamp(alpha * 0.55 * strength * shield) * 255)); canvas.drawPath(path, ink);
            ink.setStyle(Paint.Style.FILL);
        }
    }

    private Transform transform(int index, double at) {
        FoliaLumiereWindowMath window = windows.get(unitSeed);
        if (window == null) {
            window = new FoliaLumiereWindowMath(new FoliaLumiereWindowMath.Source() {
                @Override public int size() { return paragraph.last - paragraph.first; }
                @Override public double start(int i) { return lines.get(i + paragraph.first).timeMs / 1000.0; }
                @Override public FoliaLumiereWindowMath.Line line(int i) { return FoliaOriginalLumiereRenderer.this.line(i + paragraph.first); }
                @Override public int typography(int i) { return FoliaOriginalLumiereRenderer.this.typography(i + paragraph.first); }
            }, width, height, lead.cx * width / height, lead.cy, lead.regionWidth * width / height, lead.hero * height * fontScale);
            windows.put(unitSeed, window);
        }
        return window.transform(index - paragraph.first, at);
    }

    private float[] local(LineData line, Glyph glyph, Transform t, double at) {
        float[] start = line.points[t.restOrient >= 0.5 ? 1 : 0][t.restWrap >= 0.5 ? 1 : 0];
        double x = start[glyph.index * 2], y = start[glyph.index * 2 + 1], orient = t.restOrient, flying = 0;
        for (Move move : t.moves) {
            float[] target = line.points[move.orient >= 0.5 ? 1 : 0][move.wrap >= 0.5 ? 1 : 0];
            double tx = target[glyph.index * 2], ty = target[glyph.index * 2 + 1];
            double k = move.fly ? FoliaLumiereMath.cubic((move.phase - glyph.delay) / glyph.duration) : FoliaLumiereMath.sine(move.phase);
            if (move.fly) {
                double u = 1 - k, a = u * u * u, b = 3 * u * u * k, c = 3 * u * k * k, d = k * k * k;
                x = a * x + b * (x + glyph.c1x) + c * (tx + glyph.c2x) + d * tx;
                y = a * y + b * (y + glyph.c1y) + c * (ty + glyph.c2y) + d * ty;
                flying = Math.max(flying, Math.sin(Math.PI * k));
            } else { x = FoliaLumiereMath.mix(x, tx, k); y = FoliaLumiereMath.mix(y, ty, k); }
            orient = FoliaLumiereMath.mix(orient, move.orient, k);
        }
        double gather = FoliaLumiereMath.smooth((at - (lines.get(line.index).timeMs / 1000.0 - 2.8)) / 1.8);
        double scatter = (1 - gather) * (1 - gather) * lead.decayStrength;
        double amount = FoliaLumiereMath.decay(at, glyph.start, lead.decayDelay, lead.decayStrength) * glyph.driftSpeed;
        double breath = line.font * 0.022 * Math.min(1, lead.decayStrength + 0.3);
        x += glyph.scatterX * line.font * scatter + glyph.driftX * line.font * amount + Math.sin(at * 0.9 + glyph.phase) * breath;
        y += glyph.scatterY * line.font * scatter + glyph.driftY * line.font * amount + Math.cos(at * 1.13 + glyph.phase * 1.7) * breath;
        double rotation = glyph.verticalRotation * orient + glyph.spin * flying + glyph.driftSpin * amount * 0.18;
        return new float[]{(float) x, (float) y, (float) rotation, (float) (1 - 0.28 * flying), (float) gather, (float) flying};
    }

    private int typography(int index) {
        int clamped = Math.max(paragraph.first, Math.min(paragraph.last - 1, index));
        for (int i = 0; i < paragraph.shots.size(); i++) {
            FoliaLumiereMath.Shot shot = paragraph.shots.get(i);
            if (clamped >= shot.first && clamped < shot.last) {
                String typography = FoliaLumiereProfiles.get(shot.profile).optString("typography");
                return "vertical".equals(typography) ? 1 : "crossed".equals(typography) ? 2 : 0;
            }
        }
        return lead.typography;
    }

    private LineData line(int index) {
        String key = unitSeed + ':' + index;
        LineData data = prepared.get(key);
        if (data != null) return data;
        data = new LineData(); data.index = index; data.font = lead.hero * height * fontScale; data.start = lines.get(index).timeMs / 1000.0; data.singStart = Double.POSITIVE_INFINITY; data.singEnd = Double.NEGATIVE_INFINITY;
        LrcTimeline.Line line = lines.get(index);
        FoliaGlyphTimeline timing = new FoliaGlyphTimeline(line, index + 1 < lines.size() ? lines.get(index + 1).timeMs : -1);
        FoliaOriginalGraphemeTiming exactTiming = new FoliaOriginalGraphemeTiming(line, index + 1 < lines.size() ? lines.get(index + 1).timeMs : -1, false);
        int n = Math.min(2048, timing.glyphs.length);
        data.glyphs = new Glyph[n]; data.points = new float[2][2][n * 2];
        int skip = 0;
        for (int i = paragraph.first; i < index; i++) skip += FoliaGlyphTimeline.split(lines.get(i).text).length * 19 + 4;
        FoliaLumiereMath.Random rng = new FoliaLumiereMath.Random(unitSeed + ":window"); rng.skip(skip);
        List<int[]> words = words(timing, line.text);
        String wordSeed = unitSeed + ':' + (index - paragraph.first) + ':' + line.text;
        FoliaLumiereMath.Random scales = new FoliaLumiereMath.Random(wordSeed + ":words"), jags = new FoliaLumiereMath.Random(wordSeed + ":jags");
        int heroWord = -1, longest = 0;
        for (int w = 0; w < words.size(); w++) {
            int[] word = words.get(w); String value = line.text.substring(timing.offsets[word[0]], timing.offsets[word[1]]);
            if (!minor(value) && word[1] - word[0] >= longest) { longest = word[1] - word[0]; heroWord = w; }
        }
        float[] glyphScales = new float[n], glyphJags = new float[n], wordScales = new float[words.size()];
        float keyScale = 0;
        for (int w = 0; w < words.size(); w++) {
            int[] word = words.get(w); String value = line.text.substring(timing.offsets[word[0]], timing.offsets[word[1]]);
            double jitter = scales.next();
            wordScales[w] = value.trim().isEmpty() ? 1 : w == heroWord && words.size() > 1 ? 1.45f : minor(value) ? 0.62f : (float) (0.85 + jitter * 0.35);
            keyScale = Math.max(keyScale, wordScales[w]);
        }
        for (int w = 0; w < words.size(); w++) {
            int[] word = words.get(w); float scale = wordScales[w];
            double offset = (jags.next() - 0.5) * 2;
            float jag = scale == keyScale && words.size() > 1 ? 0 : (float) (offset * (scale < 0.7 ? 0.32 : 0.2));
            for (int g = word[0]; g < word[1] && g < n; g++) { glyphScales[g] = scale; glyphJags[g] = jag; }
        }
        StringBuilder prefix = new StringBuilder(); float previousWidth = 0;
        for (int g = 0; g < n; g++) {
            Glyph glyph = new Glyph(); glyph.index = g; glyph.text = timing.glyphs[g];
            glyph.scale = glyphScales[g] > 0 ? glyphScales[g] : 1; glyph.jag = glyphJags[g];
            glyph.start = exactTiming.starts[g] / 1000.0; glyph.end = exactTiming.ends[g] / 1000.0;
            if (!glyph.text.trim().isEmpty()) { data.singStart = Math.min(data.singStart, glyph.start); data.singEnd = Math.max(data.singEnd, glyph.end); }
            text.setTypeface(face); text.setTextSize(data.font * 1.5f);
            prefix.append(glyph.text); float nextWidth = text.measureText(prefix.toString());
            glyph.advance = ((nextWidth - previousWidth) / 1.5f + data.font * 0.04f + tracking) * glyph.scale;
            previousWidth = nextWidth;
            glyph.verticalAdvance = FoliaLumiereLayout.upright(glyph.text) ? data.font * glyph.scale * 1.04f + tracking : glyph.advance;
            double a1 = rng.next() * Math.PI * 2, a2 = a1 + Math.PI * (rng.next() < 0.5 ? 0.5 : 1.5) + (rng.next() - 0.5) * 0.8;
            double m1 = data.font * (1.6 + rng.next() * 3.2), m2 = data.font * (1 + rng.next() * 2.6);
            glyph.c1x = Math.cos(a1) * m1; glyph.c1y = Math.sin(a1) * m1; glyph.c2x = Math.cos(a2) * m2; glyph.c2y = Math.sin(a2) * m2;
            glyph.duration = 0.45 + rng.next() * 0.4; glyph.delay = rng.next() * (1 - glyph.duration); glyph.spin = (rng.next() - 0.5) * 2.4; glyph.wobble = rng.next() * Math.PI * 2;
            double angle = -Math.PI / 2 + (rng.next() - 0.5) * 2.2, scatterAngle = rng.next() * Math.PI * 2, scatterDistance = 0.6 + rng.next() * 1.4;
            glyph.starX = (float) (-0.15 + rng.next() * 0.5); glyph.starY = (float) (-0.62 + Math.pow(rng.next(), 1.4) * 0.9);
            glyph.starRotation = (rng.next() - .5) * .5; glyph.starSize = (float) (0.65 + rng.next() * 0.7);
            glyph.driftX = Math.cos(angle); glyph.driftY = Math.sin(angle); glyph.driftSpeed = 0.6 + rng.next() * 0.9; glyph.driftSpin = (rng.next() - 0.5) * 2;
            glyph.scatterX = Math.cos(scatterAngle) * scatterDistance; glyph.scatterY = Math.sin(scatterAngle) * scatterDistance; glyph.phase = rng.next() * Math.PI * 2;
            glyph.verticalRotation = FoliaLumiereLayout.upright(glyph.text) ? 0 : Math.PI / 2;
            data.glyphs[g] = glyph;
        }
        data.jitter = (rng.next() - 0.5) * 0.16;
        double velocity = rng.next() * Math.PI * 2, speed = 0.013 + rng.next() * 0.009;
        data.vx = Math.cos(velocity) * speed; data.vy = Math.sin(velocity) * speed; data.phase = rng.next() * Math.PI * 2;
        FoliaLumiereLayout.Glyph[] flowGlyphs = new FoliaLumiereLayout.Glyph[n];
        for (int i = 0; i < n; i++) {
            Glyph glyph = data.glyphs[i];
            flowGlyphs[i] = new FoliaLumiereLayout.Glyph(glyph.text, glyph.scale, glyph.advance,
                    glyph.jag * data.font, FoliaLumiereLayout.upright(glyph.text));
        }
        List<FoliaLumiereLayout.Word> flowWords = new ArrayList<>();
        for (int[] word : words) {
            String value = line.text.substring(timing.offsets[word[0]], timing.offsets[word[1]]);
            flowWords.add(new FoliaLumiereLayout.Word(word[0], word[1], value.matches("[\\s\\p{P}\\p{S}]+")));
        }
        final float letterSpacing = .04f + tracking / Math.max(1, data.font);
        FoliaLumiereLayout.Measurer measurer = new FoliaLumiereLayout.Measurer() {
            @Override public double natural(String value, double pixels) {
                if (value.trim().isEmpty()) return 0;
                text.setTypeface(face); text.setTextSize((float) pixels);
                return text.measureText(value) + FoliaGlyphTimeline.split(value).length * pixels * letterSpacing;
            }
            @Override public double spacing(double pixels) { return letterSpacing * pixels; }
        };
        double[] band = FoliaLumiereLayout.band(width, height);
        data.flow = FoliaLumiereLayout.flow(measurer, flowGlyphs, flowWords, data.font,
                (band[1] - band[0]) * height, (band[3] - band[2]) * height);
        for (int o = 0; o < 2; o++) for (int w = 0; w < 2; w++) {
            double[] points = data.flow[o][w].points;
            for (int i = 0; i < points.length; i++) data.points[o][w][i] = (float) points[i];
        }
        FoliaLumiereMath.Random free = new FoliaLumiereMath.Random(unitSeed + ":placements:" + (index - paragraph.first));
        for (int i = 0; i < 2; i++) {
            double angle = Math.PI * ((i == 0 ? 1.12 : 0.12) + free.next() * 0.76);
            double rx = lead.regionWidth * width / height * (0.24 + free.next() * 0.22), ry = lead.regionHeight * (0.3 + free.next() * 0.18);
            boolean v = free.next() < 0.5; double scale = 0.5 + free.next() * 0.2, rotation = (free.next() - 0.5) * (v ? 0.3 : 0.7);
            data.free[i] = new Slot((float) (Math.cos(angle) * rx), (float) (Math.sin(angle) * ry), (float) scale, i == 0 ? 0.9f : 0.95f, v ? 1 : 0, (float) rotation);
        }
        prepared.put(key, data); return data;
    }

    private static List<int[]> words(FoliaGlyphTimeline timing, String value) {
        List<int[]> words = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= 24) {
            android.icu.text.BreakIterator iterator = android.icu.text.BreakIterator.getWordInstance(Locale.SIMPLIFIED_CHINESE);
            iterator.setText(value); int previous = iterator.first();
            for (int end = iterator.next(); end != android.icu.text.BreakIterator.DONE; previous = end, end = iterator.next()) {
                int first = 0, last = 0;
                while (first < timing.glyphs.length && timing.offsets[first] < previous) first++;
                while (last < timing.glyphs.length && timing.offsets[last] < end) last++;
                if (last > first) words.add(new int[]{first, last});
            }
            return words;
        }
        java.text.BreakIterator iterator = java.text.BreakIterator.getWordInstance(Locale.SIMPLIFIED_CHINESE);
        iterator.setText(value); int previous = iterator.first();
        for (int end = iterator.next(); end != java.text.BreakIterator.DONE; previous = end, end = iterator.next()) {
            int first = 0, last = 0;
            while (first < timing.glyphs.length && timing.offsets[first] < previous) first++;
            while (last < timing.glyphs.length && timing.offsets[last] < end) last++;
            if (last > first) words.add(new int[]{first, last});
        }
        return words;
    }
    private static boolean minor(String value) {
        if (value.trim().isEmpty() || value.matches("[\\p{Punct}，。！？、；：…]+")) return true;
        if (value.length() == 1 && "的了在把是我你他她它们和与也就都着过吗呢吧啊呀哦被让给从向到这那之而又很".contains(value)) return true;
        return (" a an the of to in on at me my with and or is i you it by for ").contains(" " + value.toLowerCase(Locale.ROOT) + " ");
    }

    private static double number(JSONObject object, String name, double fallback) { return object == null ? fallback : object.optDouble(name, fallback); }
    private static double position(JSONObject a, JSONObject b, String name, double aspect, boolean fraction) {
        double base = number(a, name, 0), wide = number(b, name, base);
        if (a == b) return fraction ? base * aspect : base;
        return FoliaLumiereMath.mix(base, fraction ? wide * 2 : wide, aspect - 1);
    }
    private static int pattern(String value, String[] values) { for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i + 1; return 0; }
    private static float channel(int value, int i) { return ((value >> (16 - i * 8)) & 255) / 255f; }
    private static float[] channels(int value) { return new float[]{channel(value, 0), channel(value, 1), channel(value, 2)}; }
    private static void normalize(float[] rgb) { float peak = Math.max(rgb[0], Math.max(rgb[1], rgb[2])); if (peak < 0.04f) { rgb[0] = 1; rgb[1] = 0.86f; rgb[2] = 0.62f; } else for (int i = 0; i < 3; i++) rgb[i] /= peak; }
    private static int color(float[] rgb) { return Color.rgb((int) Math.round(FoliaLumiereMath.clamp(rgb[0]) * 255), (int) Math.round(FoliaLumiereMath.clamp(rgb[1]) * 255), (int) Math.round(FoliaLumiereMath.clamp(rgb[2]) * 255)); }

    private static final class Profile {
        final JSONObject data, rig, wide, art, artWide, fog, motes, front;
        final float cx, cy, regionWidth, regionHeight, hero, push, driftX, driftY, decayDelay, decayStrength, artGain;
        final int typography, stars, rain;
        final double starOpening, starBrightness, rainSpread, rainSpeed;
        final ArtPath[] paths;
        Profile(JSONObject source) {
            data = source; rig = source.optJSONObject("rig"); wide = rig;
            art = source.optJSONObject("art"); artWide = art; fog = rig.optJSONObject("fog");
            motes = source.optJSONObject("motes"); front = source.optJSONObject("front");
            JSONObject region = source.optJSONObject("region"), camera = source.optJSONObject("camera"), decay = source.optJSONObject("decay"), starfall = source.optJSONObject("starfall");
            cx = (float) number(region, "cx", 0.5); cy = (float) number(region, "cy", 0.54);
            regionWidth = (float) number(region, "w", 0.72); regionHeight = (float) number(region, "h", 0.5);
            hero = (float) number(source, "heroSize", 0.085); push = (float) number(camera, "push", 0.035);
            driftX = (float) number(camera, "driftX", 0); driftY = (float) number(camera, "driftY", -0.006);
            decayDelay = (float) number(decay, "delay", 1); decayStrength = (float) number(decay, "strength", 1);
            artGain = (float) number(source, "artGain", 1);
            typography = "vertical".equals(source.optString("typography")) ? 1 : "crossed".equals(source.optString("typography")) ? 2 : 0;
            stars = (int) number(starfall, "stars", 0); rain = (int) number(starfall, "rain", 0);
            starOpening = number(starfall, "opening", 3.2); starBrightness = number(starfall, "brightness", 1);
            rainSpread = number(starfall, "rainSpread", 0.35); rainSpeed = number(starfall, "rainSpeed", 0.22);
            JSONArray specs = art.optJSONArray("paths");
            paths = new ArtPath[specs == null ? 0 : specs.length()];
            for (int i = 0; i < paths.length; i++) paths[i] = new ArtPath(specs.optJSONObject(i));
        }
    }

    /** Same cumulative-length reveal and dash intersections as lineart/lineArt.ts. */
    private static final class ArtPath {
        final Path path = new Path();
        final double[] x, y, lengths;
        final double width, alpha, delay, span, total, midX, midY, dashOn, dashOff;
        double drawn = -1;
        ArtPath(JSONObject spec) {
            JSONArray points = spec.optJSONArray("points"), dash = spec.optJSONArray("dash");
            int count = points == null ? 0 : points.length();
            x = new double[count]; y = new double[count]; lengths = new double[count];
            for (int i = 0; i < count; i++) {
                JSONArray point = points.optJSONArray(i);
                x[i] = point.optDouble(0); y[i] = point.optDouble(1);
                if (i > 0) lengths[i] = lengths[i - 1] + Math.hypot(x[i] - x[i - 1], y[i] - y[i - 1]);
            }
            width = number(spec, "width", 0.001); alpha = number(spec, "alpha", 0.5);
            delay = number(spec, "delay", 0); span = number(spec, "span", 1);
            total = count > 0 ? lengths[count - 1] : 0;
            midX = count > 0 ? x[count / 2] : 0; midY = count > 0 ? y[count / 2] : 0;
            dashOn = dash == null ? 0 : dash.optDouble(0); dashOff = dash == null ? 0 : dash.optDouble(1);
        }
        void redraw(double amount, double height) {
            path.rewind(); drawn = amount;
            if (amount <= 0 || x.length < 2) return;
            double limit = Math.min(amount, total), period = dashOn + dashOff;
            path.moveTo((float) (x[0] * height), (float) (y[0] * height));
            for (int i = 1; i < x.length; i++) {
                double start = lengths[i - 1], end = Math.min(lengths[i], limit);
                if (start >= limit) break;
                if (period <= 0) at(i, end, height, false);
                else for (int k = (int) Math.floor(start / period); k * period < end; k++) {
                    double from = Math.max(start, k * period), to = Math.min(end, k * period + dashOn);
                    if (to <= from) continue;
                    at(i, from, height, true); at(i, to, height, false);
                }
            }
        }
        private void at(int i, double distance, double height, boolean move) {
            double segment = lengths[i] - lengths[i - 1];
            double k = segment > 0 ? (distance - lengths[i - 1]) / segment : 0;
            float px = (float) ((x[i - 1] + (x[i] - x[i - 1]) * k) * height);
            float py = (float) ((y[i - 1] + (y[i] - y[i - 1]) * k) * height);
            if (move) path.moveTo(px, py); else path.lineTo(px, py);
        }
    }
    private static final class Beam { final float[] a = new float[4], b = new float[4], c = new float[4], d = new float[4], e = new float[4], f = new float[4]; }
    private static final class Particle { double depth, x, y, size, phaseA, phaseB, freqA, freqB, twinklePhase, twinkleFreq; }
    private static final class LineData extends FoliaLumiereWindowMath.Line {
        int index; float font; double singStart, singEnd;
        Glyph[] glyphs; float[][][] points;
        double trailTime = Double.NaN;
        boolean trailFlying;
        final Transform[] trailTransforms = new Transform[21];
    }
    private static final class Glyph {
        int index; String text; float scale, jag, advance, verticalAdvance, starX, starY, starSize;
        double start, end, c1x, c1y, c2x, c2y, delay, duration, spin, wobble, starRotation;
        double driftX, driftY, driftSpeed, driftSpin, scatterX, scatterY, phase, verticalRotation;
    }
}
