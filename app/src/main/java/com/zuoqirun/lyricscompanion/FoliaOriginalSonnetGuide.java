// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native formula port of sonnetGuides.ts at 9279251.
package com.zuoqirun.lyricscompanion;

/** Complete lead-in comet, silk trails, HUD spline and geometric burst; no bitmap rendering. */
final class FoliaOriginalSonnetGuide {
    interface RandomSource { double next(); }
    interface Target {
        void line(double x1, double y1, double x2, double y2, double width, double alpha, boolean white);
        void circle(double x, double y, double radius, boolean fill, double width, double alpha, boolean white);
        void particle(int kind, double size, double x, double y, double rotation, double scale, double alpha);
    }
    private final boolean hero;
    private final double startX, startY, maxAlpha;
    private final int[] particleKinds;
    private final double[] particleSizes, angles, speeds, rotSpeeds;
    private final double[][] trails = new double[2][];
    private final double[] spline;

    FoliaOriginalSonnetGuide(boolean hero, double size, double enterX, double enterY,
                             double timingPhase, RandomSource random) {
        this.hero = hero;
        startX = enterX != 0 ? enterX : (timingPhase < 0.5 ? -1 : 1) * size * 1.8;
        startY = enterY != 0 ? enterY : -size * 0.9;
        maxAlpha = hero ? 0.95 : 0.7;
        int count = hero ? 6 : 3;
        particleKinds = new int[count]; particleSizes = new double[count]; angles = new double[count];
        speeds = new double[count]; rotSpeeds = new double[count];
        for (int index = 0; index < count; index++) {
            particleKinds[index] = (int) Math.floor(random.next() * 4);
            particleSizes[index] = (hero ? 3 : 1.5) + random.next() * 3.5;
            angles[index] = random.next() * Math.PI * 2;
            speeds[index] = (15 + random.next() * 45) * (hero ? 1 : 0.7);
            rotSpeeds[index] = (random.next() - 0.5) * 8;
        }
        if (hero || random.next() > 0.4) {
            double direction = random.next() > 0.5 ? 1 : -1, offset = (random.next() - 0.5) * size * 0.8;
            trails[0] = new double[]{-direction * size * 2.5, offset + size * 1.5,
                    -direction * size * 0.8, offset - size * 2, direction * size * 0.8, offset + size * 2,
                    direction * size * 2.5, offset - size * 1.5, random.next() * 0.15};
        }
        if (hero || random.next() > 0.6) {
            double direction = random.next() > 0.5 ? 1 : -1;
            trails[1] = new double[]{-size * 2, -direction * size * 1.8, size * 2, direction * size * 1.8,
                    -size * 2, direction * size * 1.8, size * 2, -direction * size * 1.8, random.next() * 0.1};
        }
        if (random.next() > 0.4) {
            double length = size * (1.2 + random.next() * 1.5), thickness = (hero ? 6 : 3) + random.next() * 8;
            double angle = (random.next() - 0.5) * Math.PI, x = (random.next() - 0.5) * size * 1.2, y = (random.next() - 0.5) * size * 1.2;
            spline = new double[]{x, y, x + Math.cos(angle) * length, y + Math.sin(angle) * length,
                    thickness, random.next() * 0.15, 0.25 + random.next() * 0.2};
        } else spline = null;
    }

    void draw(Target target, double progress) {
        double drawProgress = clamp(progress / 0.35), fade = 1 - clamp((progress - 0.4) / 0.3);
        double strokeAlpha = hero ? 0.82 : 0.55;
        if (drawProgress > 0 && fade > 0) {
            double previousX = startX, previousY = startY;
            for (int index = 1; index <= 20; index++) {
                double t = index / 20.0 * drawProgress, inverse = 1 - t;
                double x = startX * (inverse * inverse * inverse + 3 * inverse * inverse * t * 0.6 + 3 * inverse * t * t * 0.2);
                double y = startY * (inverse * inverse * inverse + 3 * inverse * inverse * t * 0.4 + 3 * inverse * t * t * 0.1);
                double intensity = Math.pow(index / 20.0, 2);
                target.line(previousX, previousY, x, y, (hero ? 2.5 : 1.5) + intensity * (hero ? 5 : 3),
                        Math.min(1, intensity * strokeAlpha * fade * 1.6) * maxAlpha, false);
                previousX = x; previousY = y;
            }
            target.circle(previousX, previousY, hero ? 14 : 9, true, 0, 0.5 * fade * maxAlpha, false);
            target.circle(previousX, previousY, hero ? 4.5 : 3, true, 0, fade * maxAlpha, true);
        }
        for (double[] trail : trails) {
            if (trail == null) continue;
            double local = (progress - trail[8]) / 0.55;
            if (local <= 0 || local >= 1.3 || fade <= 0) continue;
            double head = Math.min(1, local), tail = Math.max(0, local - 0.35);
            if (head > tail) {
                double previousX = bezier(trail, tail, 0), previousY = bezier(trail, tail, 1);
                for (int index = 1; index <= 25; index++) {
                    double t = tail + index / 25.0 * (head - tail), x = bezier(trail, t, 0), y = bezier(trail, t, 1);
                    double intensity = Math.pow(index / 25.0, 2);
                    target.line(previousX, previousY, x, y, (hero ? 2 : 1) + intensity * (hero ? 5 : 2.5),
                            intensity * strokeAlpha * fade * 0.9 * maxAlpha, false);
                    previousX = x; previousY = y;
                }
            }
            if (head > 0 && head < 1) {
                double x = bezier(trail, head, 0), y = bezier(trail, head, 1);
                target.circle(x, y, hero ? 7 : 4, true, 0, 0.9 * fade * maxAlpha, false);
                target.circle(x, y, hero ? 2.5 : 1.5, true, 0, fade * maxAlpha, true);
                target.circle(x, y, hero ? 20 : 12, false, hero ? 2 : 1, 0.4 * fade * maxAlpha, false);
            }
        }
        if (spline != null) {
            double local = (progress - spline[5]) / spline[6];
            if (local > 0 && local < 1.3 && fade > 0) {
                double head = clamp(local * 1.5), tail = clamp((local - 0.3) * 1.5);
                if (head > tail) {
                    double hx = spline[0] + (spline[2] - spline[0]) * head, hy = spline[1] + (spline[3] - spline[1]) * head;
                    double tx = spline[0] + (spline[2] - spline[0]) * tail, ty = spline[1] + (spline[3] - spline[1]) * tail;
                    target.line(tx, ty, hx, hy, spline[4], strokeAlpha * fade * 0.7 * maxAlpha, false);
                    target.line(tx, ty, hx, hy, spline[4] * 0.3, strokeAlpha * fade * 0.9 * maxAlpha, true);
                }
            }
        }
        double burst = clamp((progress - 0.3) / 0.7), eased = 1 - Math.pow(1 - burst, 3);
        for (int index = 0; index < particleKinds.length; index++) {
            target.particle(particleKinds[index], particleSizes[index], Math.cos(angles[index]) * speeds[index] * eased,
                    Math.sin(angles[index]) * speeds[index] * eased, rotSpeeds[index] * burst,
                    1 - burst * 0.4, (1 - burst) * (hero ? 1 : 0.8) * (hero ? 0.8 : 0.6) * maxAlpha);
        }
    }

    private static double bezier(double[] curve, double t, int axis) {
        double inverse = 1 - t;
        return inverse * inverse * inverse * curve[axis] + 3 * inverse * inverse * t * curve[axis + 2]
                + 3 * inverse * t * t * curve[axis + 4] + t * t * t * curve[axis + 6];
    }
    private static double clamp(double value) { return Math.min(1, Math.max(0, value)); }
}
