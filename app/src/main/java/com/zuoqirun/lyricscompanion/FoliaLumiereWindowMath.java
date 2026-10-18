// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native translation of lumiere/text/window{Slots,Geometry}.ts at 9279251.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Exact continuous window geometry; units, frame clearance and fit are shared by all typography. */
final class FoliaLumiereWindowMath {
    interface Source {
        int size();
        double start(int index);
        Line line(int index);
        int typography(int current);
    }
    static class Line {
        double start, jitter, vx, vy, phase;
        FoliaLumiereLayout.Variant[][] flow;
        final Slot[] free = new Slot[2];
    }
    static class Slot {
        double dx, dy, scale, alpha, orient, rotation, wrap;
        Slot(double dx, double dy, double scale, double alpha, double orient, double rotation) {
            this.dx = dx; this.dy = dy; this.scale = scale; this.alpha = alpha; this.orient = orient; this.rotation = rotation;
        }
        Slot(Slot source) { this(source.dx, source.dy, source.scale, source.alpha, source.orient, source.rotation); wrap = source.wrap; }
    }
    static final class Move {
        final double orient, wrap, phase;
        final boolean fly;
        Move(double orient, double wrap, double phase, boolean fly) { this.orient = orient; this.wrap = wrap; this.phase = phase; this.fly = fly; }
    }
    static final class Transform extends Slot {
        double x, y, slideStart = Double.NEGATIVE_INFINITY, restOrient, restWrap;
        int current;
        final List<Move> moves = new ArrayList<>();
        Transform(Slot source) { super(source); restOrient = source.orient; restWrap = source.wrap; }
    }
    static final class ProtectBox {
        int line;
        double x, y, cos, sin, halfW, halfH, margin, weight;
    }
    static double shield(ProtectBox[] boxes, int count, int line, double x, double y, double half, double flying) {
        double protect = 0;
        for (int i = 0; i < count; i++) {
            ProtectBox box = boxes[i];
            if (box.line == line || box.weight <= 0) continue;
            double dx = x - box.x, dy = y - box.y;
            double u = Math.abs(dx * box.cos + dy * box.sin) - box.halfW - half;
            double v = Math.abs(dy * box.cos - dx * box.sin) - box.halfH - half;
            double outside = u > 0 && v > 0 ? Math.hypot(u, v) : Math.max(0, Math.max(u, v));
            protect += box.weight * (1 - FoliaLumiereMath.smooth(outside / box.margin));
        }
        return 1 - .6 * Math.min(1, protect) * (1 - flying);
    }

    final Source source;
    final double width, height, cx, cy, regionWidth, fontH;
    final double[] band;
    final double columnCap;
    final LinkedHashMap<String, Slot> slots = new LinkedHashMap<String, Slot>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Slot> eldest) { return size() > 128; }
    };
    FoliaLumiereWindowMath(Source source, double width, double height, double cx, double cy,
                          double regionWidth, double heroPixels) {
        this.source = source; this.width = width; this.height = height; this.cx = cx; this.cy = cy;
        this.regionWidth = regionWidth; fontH = heroPixels / height;
        band = FoliaLumiereLayout.band(width, height); columnCap = (band[3] - band[2]) * height;
    }
    double maxWidth(int kind) { return (band[1] - band[0]) * height * (kind == 2 ? .9 : 1); }
    private int typography(int current) { return source.typography(Math.max(0, Math.min(source.size() - 1, current))); }
    private double fit(FoliaLumiereLayout.Variant variant, int kind, int orient, double scale) {
        return FoliaLumiereLayout.fit(variant.along, orient == 1 ? columnCap : maxWidth(kind), scale);
    }
    private int wrap(int index, int kind, int orient, double scale) {
        return FoliaLumiereLayout.wrap(source.line(index).flow[orient], orient == 1 ? columnCap : maxWidth(kind), scale) ? 1 : 0;
    }
    private double across(int index, int kind, int orient, double scale) {
        if (index < 0 || index >= source.size()) return fontH * scale;
        FoliaLumiereLayout.Variant variant = source.line(index).flow[orient][wrap(index, kind, orient, scale)];
        return variant.across / height * scale * fit(variant, kind, orient, scale);
    }
    private double tiltedHeight(Line line, int wrapped, double scale, int kind, int orient, double tilt) {
        FoliaLumiereLayout.Variant variant = line.flow[orient][wrapped];
        return (variant.inkHeight * Math.cos(tilt) + variant.inkWidth * Math.sin(tilt)) / height * scale * fit(variant, kind, orient, scale);
    }

    Slot slot(int index, int current, int kind) {
        String key = index + "|" + current + "|" + kind;
        Slot cached = slots.get(key); if (cached != null) return cached;
        Line line = source.line(index);
        int relative = Math.max(-2, Math.min(2, index - current)), orient = kind == 1 ? 1 : 0;
        double shiftX = 0, shiftY = 0;
        if (current >= 0 && current < source.size()) {
            FoliaLumiereLayout.Variant variant = source.line(current).flow[orient][wrap(current, kind, orient, 1)];
            double scale = fit(variant, kind, orient, 1);
            shiftX = FoliaLumiereLayout.clampInto(cx, variant.inkWidth / height * scale, band[0], band[1]) - cx;
            shiftY = FoliaLumiereLayout.clampInto(cy, variant.inkHeight / height * scale, band[2], band[3]) - cy;
        }
        Slot slot; int wrapped = -1;
        if (kind == 2 && relative != 0) {
            Slot base = new Slot(line.free[relative < 0 ? 0 : 1]);
            if (Math.abs(relative) > 1) { base.dx *= 1.45; base.dy *= 1.45; base.scale *= .85; base.alpha = 0; }
            int baseOrient = base.orient >= .5 ? 1 : 0, sign = base.dy < 0 ? -1 : 1;
            double currentHalf = fontH / 2;
            if (current >= 0 && current < source.size()) {
                FoliaLumiereLayout.Variant variant = source.line(current).flow[0][wrap(current, kind, 0, 1)];
                currentHalf = variant.inkHeight / height * fit(variant, kind, 0, 1) / 2;
            }
            double gap = fontH * .5, centerY = cy + shiftY;
            double room = sign < 0 ? centerY - currentHalf - gap - band[2] : band[3] - (centerY + currentHalf + gap);
            double tilt = Math.abs(base.rotation) + .03, scale = base.scale;
            wrapped = wrap(index, kind, baseOrient, scale);
            if (baseOrient == 1 && room > 0) {
                if (wrapped == 0 && line.flow[baseOrient][1].lines > 1 && tiltedHeight(line, 0, scale, kind, baseOrient, tilt) > room) wrapped = 1;
                double tall = tiltedHeight(line, wrapped, scale, kind, baseOrient, tilt);
                if (tall > room) scale = Math.max(base.scale * .5, scale * room / tall);
            }
            double clearance = currentHalf + gap + tiltedHeight(line, wrapped, scale, kind, baseOrient, tilt) / 2;
            double wider = baseOrient == 1 ? Math.max(0, across(index, kind, 1, scale) - fontH * scale) / 2 : 0;
            slot = new Slot(base.dx + Math.signum(base.dx) * wider, sign * Math.max(Math.abs(base.dy), clearance), scale, base.alpha, base.orient, base.rotation);
        } else if (relative == 0) slot = new Slot(0, 0, 1, 1, orient, 0);
        else {
            boolean previous = relative < 0, far = Math.abs(relative) > 1, vertical = kind == 1;
            int sign = previous ? -1 : 1;
            double nearScale = vertical ? .58 : .52, scale = far ? vertical ? .48 : .44 : nearScale;
            double offset = across(current, kind, orient, 1) / 2 + (previous ? vertical ? .95 : .93 : vertical ? .88 : .735) * fontH;
            if (far) offset += across(current + sign, kind, orient, nearScale) + (previous ? vertical ? .78 : .56 : vertical ? .7 : .56) * fontH;
            offset += across(index, kind, orient, scale) / 2;
            // Measured against the browser reference the previous line clears the active one by
            // ~0.22 * height, not the ~0.15 the stack formula produces, so keep a wider gap.
            if (previous && !far) offset *= 1.5;
            double alpha = far ? 0 : previous ? .9 : .95;
            slot = vertical ? new Slot(-sign * offset, sign * (far ? .07 : .03), scale, alpha, 1, 0)
                    : new Slot(sign * regionWidth * (far ? .24 : .16), sign * offset, scale, alpha, 0, 0);
        }
        slot.dx += shiftX; slot.dy += shiftY;
        slot.wrap = wrapped >= 0 ? wrapped : wrap(index, kind, slot.orient >= .5 ? 1 : 0, slot.scale);
        slots.put(key, slot); return slot;
    }

    Transform transform(int index, double time) {
        Line line = source.line(index); int current = -1;
        for (int i = 0; i < source.size(); i++) {
            if (source.start(i) - 1.2 <= time) current = i; else break;
        }
        int first = current;
        while (first >= 0 && source.start(first) - 1.2 + .6 + 1.5 + .45 > time) first--;
        first++;
        int base = first - 1, initialKind = typography(base);
        Slot initial = slot(index, base, initialKind); Transform t = new Transform(initial); t.current = current;
        double widthCap = maxWidth(initialKind), jitterOn = initialKind == 2 ? 0 : 1;
        double awayX = initialKind == 1 ? away(index, base, initialKind) : 0, awayY = initialKind == 1 ? 0 : away(index, base, initialKind);
        double hero = index == base ? 1 : 0;
        for (int c = first; c <= Math.min(source.size() - 1, current); c++) {
            int beforeKind = typography(c - 1), afterKind = typography(c);
            Slot before = slot(index, c - 1, beforeKind), after = slot(index, c, afterKind);
            boolean kindChanged = beforeKind != afterKind;
            if (same(before, after) && !kindChanged) continue;
            int relative = Math.max(-2, Math.min(2, index - c));
            double lag = relative == -2 ? 0 : relative == -1 ? .15 : relative == 0 ? .3 : relative == 1 ? .5 : .6;
            double start = source.start(c) - 1.2 + lag, phase = FoliaLumiereMath.clamp((time - start) / 1.5), k = FoliaLumiereMath.sine(phase);
            if (kindChanged) {
                widthCap += (maxWidth(afterKind) - maxWidth(beforeKind)) * k;
                jitterOn += ((afterKind == 2 ? 0 : 1) - (beforeKind == 2 ? 0 : 1)) * k;
            }
            awayX += ((afterKind == 1 ? away(index, c, afterKind) : 0) - (beforeKind == 1 ? away(index, c - 1, beforeKind) : 0)) * k;
            awayY += ((afterKind == 1 ? 0 : away(index, c, afterKind)) - (beforeKind == 1 ? 0 : away(index, c - 1, beforeKind))) * k;
            hero += ((index == c ? 1 : 0) - (index == c - 1 ? 1 : 0)) * k;
            double fade = after.alpha < before.alpha ? FoliaLumiereMath.cubic(phase / .6)
                    : after.alpha > before.alpha ? FoliaLumiereMath.cubic((phase - .4) / .6) : k;
            t.dx += (after.dx - before.dx) * k; t.dy += (after.dy - before.dy) * k;
            t.scale += (after.scale - before.scale) * k; t.rotation += (after.rotation - before.rotation) * k;
            t.alpha += (after.alpha - before.alpha) * fade; t.orient += (after.orient - before.orient) * phase; t.wrap += (after.wrap - before.wrap) * k;
            if (phase > 0) t.slideStart = start;
            if (phase >= 1) { t.restOrient = after.orient; t.restWrap = after.wrap; t.moves.clear(); }
            else if (phase > 0) t.moves.add(new Move(after.orient, after.wrap, phase, afterKind == 2 || before.orient != after.orient));
        }
        double o = FoliaLumiereMath.clamp(t.orient), w = FoliaLumiereMath.clamp(t.wrap);
        double fitH = FoliaLumiereMath.mix(FoliaLumiereLayout.fit(line.flow[0][0].along, widthCap, t.scale), FoliaLumiereLayout.fit(line.flow[0][1].along, widthCap, t.scale), w);
        double fitV = FoliaLumiereMath.mix(FoliaLumiereLayout.fit(line.flow[1][0].along, columnCap, t.scale), FoliaLumiereLayout.fit(line.flow[1][1].along, columnCap, t.scale), w);
        double fitted = t.scale * FoliaLumiereMath.mix(fitH, fitV, o);
        double jitter = jitterOn * line.jitter * regionWidth * FoliaLumiereMath.clamp((1 - t.scale) / .5);
        double baseX = cx + t.dx + jitter * (1 - t.orient), baseY = cy + t.dy + jitter * .5 * t.orient;
        double alongH = FoliaLumiereMath.mix(line.flow[0][0].inkWidth, line.flow[0][1].inkWidth, w) / height * fitted;
        double alongV = FoliaLumiereMath.mix(line.flow[1][0].inkHeight, line.flow[1][1].inkHeight, w) / height * fitted;
        double x = baseX + (FoliaLumiereLayout.clampInto(baseX, alongH, band[0], band[1]) - baseX) * (1 - o) * jitterOn;
        double y = baseY + (FoliaLumiereLayout.clampInto(baseY, alongV, band[2], band[3]) - baseY) * o * jitterOn;
        double age = time - line.start, orbitX = Math.sin(time * .52 + line.phase) * .03, orbitY = Math.cos(time * .41 + line.phase * 1.3) * .021;
        double driftX = FoliaLumiereMath.mix(FoliaLumiereMath.away(line.vx * age + orbitX, awayX), FoliaLumiereMath.held(line.vx, line.vy, age, false) + orbitX, FoliaLumiereMath.clamp(hero));
        double driftY = FoliaLumiereMath.mix(FoliaLumiereMath.away(line.vy * age + orbitY, awayY), FoliaLumiereMath.held(line.vx, line.vy, age, true) + orbitY, FoliaLumiereMath.clamp(hero));
        t.x = (x + driftX) * height; t.y = (y + driftY) * height;
        t.scale = fitted * (1 + .025 * Math.sin(time * .43 + line.phase));
        t.rotation += .03 * Math.sin(time * .23 + line.phase * .7);
        t.alpha = FoliaLumiereMath.clamp(t.alpha); t.orient = o; t.wrap = w;
        return t;
    }
    private static int away(int index, int current, int kind) { return (index == current ? 0 : index < current ? -1 : 1) * (kind == 1 ? -1 : 1); }
    private static boolean same(Slot a, Slot b) {
        return a.dx == b.dx && a.dy == b.dy && a.scale == b.scale && a.alpha == b.alpha && a.orient == b.orient && a.rotation == b.rotation && a.wrap == b.wrap;
    }
}
