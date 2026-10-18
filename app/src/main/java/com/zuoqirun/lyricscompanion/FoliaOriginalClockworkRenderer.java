// SPDX-License-Identifier: AGPL-3.0-only
// Native port of Folia pendoloClockworkDraw2d.ts / pendoloClockworkScene.ts (9279251).
// Copyright (c) 2026 chthollyphile and Folia contributors. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/** Reuses paints and paths while preserving the upstream mechanical assembly's geometry. */
final class FoliaOriginalClockworkRenderer {
    private static final double[] TOOTH_OFFSETS = {-0.22, -0.12, 0.12, 0.22};
    private static final float[] GUIDE_RADII = {0.3f, 0.6f, 0.85f, 1.15f, 1.4f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int primary, accent;
    private float decor = 1f;

    void draw(Canvas canvas, float radius, float cy, FoliaOriginalClockworkMotion motion,
              float lyricRadius, double angle) {
        int save = canvas.save();
        canvas.translate(0, cy);
        double phase = motion.phase;
        double swing = motion.swing;
        for (float ratio : GUIDE_RADII) circle(canvas, 0, 0, radius * ratio, primary, 0.15f, 1);
        for (int i = 0; i < 60; i++) {
            double a = i * Math.PI * 2 / 60 + angle * 0.2;
            float r = radius * 1.15f;
            line(canvas, (float) Math.cos(a) * r, (float) Math.sin(a) * r,
                    (float) Math.cos(a) * (r + (i % 5 == 0 ? 12 : 6)),
                    (float) Math.sin(a) * (r + (i % 5 == 0 ? 12 : 6)),
                    i % 5 == 0 ? accent : primary, i % 5 == 0 ? 0.35f : 0.15f, i % 5 == 0 ? 1.5f : 1);
        }
        gear(canvas, 0, 0, radius + 8, 36, 10, angle, accent, 0.58f, 2.2f, 0.08f);
        wheel(canvas, 0, 0, radius * 0.2f, radius * 0.85f, 6, angle, primary, 0.42f, 1.8f);
        circle(canvas, 0, 0, radius * 0.88f, primary, 0.18f, 0.8f);
        circle(canvas, 0, 0, radius * 0.92f, primary, 0.18f, 0.8f);
        int engraving = canvas.save();
        canvas.rotate((float) Math.toDegrees(angle));
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            float inner = radius * (i % 2 == 0 ? 0.62f : 0.683f);
            line(canvas, inner * (float) Math.cos(a), inner * (float) Math.sin(a), radius * 0.83f * (float) Math.cos(a),
                    radius * 0.83f * (float) Math.sin(a), primary, i % 2 == 0 ? 0.1f : 0.07f, 0.7f);
        }
        canvas.restoreToCount(engraving);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12 + angle;
            circle(canvas, radius * 0.96f * (float) Math.cos(a), radius * 0.96f * (float) Math.sin(a), 2.2f, primary, 0.25f, 0.8f);
        }
        gear(canvas, 0, 0, radius * 0.22f, 12, 6, -angle * 2.5, accent, 0.72f, 2.1f, 0);
        jewel(canvas, 0, 0, 4.5f, 2.5f, 1.2f, 0.8f);
        for (int i = 0; i < 3; i++) {
            double a = angle * 0.4 + i * Math.PI * 2 / 3;
            float x = radius * 0.52f * (float) Math.cos(a), y = radius * 0.52f * (float) Math.sin(a);
            gear(canvas, x, y, radius * 0.16f, 14, 5, -angle * 3 + i * 0.5, primary, 0.24f, 1.7f, 0);
            jewel(canvas, x, y, 3.5f, 1.8f, 1f, 0.7f);
        }
        float bx = radius * 0.2f, by = -radius * 0.75f, br = radius * 0.28f;
        gear(canvas, bx, by, br, 20, 7, phase * 0.1, accent, 0.72f, 2.1f, 0.10f);
        circle(canvas, bx, by, br * 0.52f, primary, 0.32f, 1.8f);
        int spring = canvas.save();
        canvas.translate(bx, by);
        canvas.rotate((float) Math.toDegrees(swing * 0.6 + phase * 0.03));
        path.reset();
        for (int i = 0; i <= 210; i++) {
            double t = i / 210.0, a = t * 7 * Math.PI;
            float r = br * (0.56f + 0.32f * (float) Math.pow(t, 0.9));
            if (i == 0) path.moveTo(r * (float) Math.cos(a), r * (float) Math.sin(a));
            else path.lineTo(r * (float) Math.cos(a), r * (float) Math.sin(a));
        }
        stroke(canvas, accent, 0.3f, 0.8f);
        canvas.restoreToCount(spring);
        jewel(canvas, bx, by, 3.5f, 1.8f, 1f, 0.7f);
        float tx = radius * 0.32f, ty = radius * 0.78f, tr = radius * 0.34f;
        gear(canvas, tx, ty, tr, 24, 7, -angle * 1.4, primary, 0.42f, 1.8f, 0);
        wheel(canvas, tx, ty, tr * 0.25f, tr * 0.85f, 5, -angle * 1.4, primary, 0.32f, 1.5f);
        int stripes = canvas.save();
        canvas.translate(tx, ty); canvas.rotate((float) Math.toDegrees(-angle * 1.4));
        path.reset(); path.addCircle(0, 0, tr * 0.8f, Path.Direction.CW); canvas.clipPath(path);
        for (int i = 1; i <= 9; i++) line(canvas, -tr, -tr * 0.8f + i * tr * 0.16f,
                tr, -tr * 0.8f + i * tr * 0.16f, primary, i % 2 == 0 ? 0.1f : 0.06f, 0.7f);
        canvas.restoreToCount(stripes); jewel(canvas, tx, ty, 3.5f, 1.8f, 1f, 0.7f);
        float sx = radius * 0.68f, sy = radius * 0.76f, sr = radius * 0.16f;
        double secondAngle = motion.secondGearAngle;
        gear(canvas, sx, sy, sr, 15, 5, secondAngle, accent, 0.58f, 1.8f, 0.06f);
        wheel(canvas, sx, sy, sr * 0.28f, sr * 0.76f, 4, -secondAngle, primary, 0.32f, 1.3f);
        jewel(canvas, sx, sy, 2.8f, 1.5f, 0.8f, 0.6f);
        float ix = (tx + sx) / 2 + radius * 0.04f, iy = (ty + sy) / 2 - radius * 0.02f;
        gear(canvas, ix, iy, radius * 0.08f, 10, 3.5f, angle * 2.2, primary, 0.28f, 1.3f, 0);
        circle(canvas, ix, iy, radius * 0.08f * 0.45f, primary, 0.18f, 0.8f);
        jewel(canvas, ix, iy, 2.2f, 1.2f, 0.7f, 0.5f);
        float end = lyricRadius - Math.max(14, radius * 0.025f);
        line(canvas, radius * 0.8f, 0, end, 0, accent, 0.72f, 2);
        path.reset(); path.moveTo(end, -4); path.lineTo(end + 8, 0); path.lineTo(end, 4); path.close();
        paint.setStyle(Paint.Style.FILL); paint.setColor(alpha(accent, 0.72f)); canvas.drawPath(path, paint);
        // VisualizerPendolo.tsx draws a lucide star over the balance wheel (theme has no lyric icons).
        star(canvas, radius * 0.2f, -radius * 0.75f, Math.max(14f, radius * 0.13f));
        canvas.restoreToCount(save);
    }

    void colors(int primary, int accent, float decor) {
        this.primary = primary;
        this.accent = accent;
        this.decor = decor;
    }

    private void gear(Canvas c, float x, float y, float radius, int teeth, float depth, double rotation,
                      int color, float opacity, float width, float fill) {
        int save = c.save(); c.translate(x, y); c.rotate((float) Math.toDegrees(rotation));
        path.reset(); double step = Math.PI * 2 / teeth;
        for (int i = 0; i < teeth; i++) for (int point = 0; point < 4; point++) {
            double a = i * step + step * TOOTH_OFFSETS[point];
            float r = point == 0 || point == 3 ? radius - depth : radius;
            float px = r * (float) Math.cos(a), py = r * (float) Math.sin(a);
            if (i == 0 && point == 0) path.moveTo(px, py); else path.lineTo(px, py);
        }
        path.close();
        if (fill > 0) { paint.setStyle(Paint.Style.FILL); paint.setColor(alpha(color, fill)); c.drawPath(path, paint); }
        stroke(c, color, opacity, width); c.restoreToCount(save);
    }

    private void wheel(Canvas c, float x, float y, float hub, float rim, int spokes, double rotation, int color, float opacity, float width) {
        int save = c.save(); c.translate(x, y); c.rotate((float) Math.toDegrees(rotation));
        circle(c, 0, 0, hub, color, opacity, width); circle(c, 0, 0, rim, color, opacity, width);
        for (int i = 0; i < spokes; i++) {
            double a = i * Math.PI * 2 / spokes;
            line(c, hub * (float) Math.cos(a), hub * (float) Math.sin(a), rim * (float) Math.cos(a), rim * (float) Math.sin(a), color, opacity, width);
            a += Math.PI / spokes;
            circle(c, (hub + rim) * 0.5f * (float) Math.cos(a), (hub + rim) * 0.5f * (float) Math.sin(a), (rim - hub) * 0.22f, color, opacity, width);
        }
        c.restoreToCount(save);
    }

    private void jewel(Canvas c, float x, float y, float outer, float inner, float outerWidth, float innerWidth) {
        circle(c, x, y, outer, accent, 0.65f, outerWidth);
        circle(c, x, y, inner, accent, 0.28f, innerWidth);
    }

    /** lucide Star icon drawn over the balance wheel at accent@0.62. */
    private void star(Canvas c, float x, float y, float size) {
        float k = size / 24f;
        int save = c.save();
        c.translate(x, y);
        c.scale(k, k);
        c.translate(-12, -12);
        path.reset();
        float[] points = {12, 2, 15.09f, 8.26f, 22, 9.27f, 17, 14.14f, 18.18f, 21.02f, 12, 17.77f,
                5.82f, 21.02f, 7, 14.14f, 2, 9.27f, 8.91f, 8.26f};
        for (int i = 0; i < points.length; i += 2) {
            if (i == 0) path.moveTo(points[i], points[i + 1]);
            else path.lineTo(points[i], points[i + 1]);
        }
        path.close();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(alpha(accent, 0.62f));
        paint.setStrokeWidth(1.2f / k);
        c.drawPath(path, paint);
        c.restoreToCount(save);
    }
    private void circle(Canvas c, float x, float y, float r, int color, float alpha, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setColor(alpha(color, alpha)); paint.setStrokeWidth(width); c.drawCircle(x, y, r, paint);
    }
    private void line(Canvas c, float x1, float y1, float x2, float y2, int color, float alpha, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setColor(alpha(color, alpha)); paint.setStrokeWidth(width); c.drawLine(x1, y1, x2, y2, paint);
    }
    private void stroke(Canvas c, int color, float alpha, float width) {
        paint.setStyle(Paint.Style.STROKE); paint.setColor(alpha(color, alpha)); paint.setStrokeWidth(width); c.drawPath(path, paint);
    }
    private int alpha(int color, float value) { return (color & 0x00ffffff) | Math.round(value * decor * (color >>> 24)) << 24; }
}
