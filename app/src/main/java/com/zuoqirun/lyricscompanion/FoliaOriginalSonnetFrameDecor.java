// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native formula port of sonnetFrameDecor.ts at 9279251.
package com.zuoqirun.lyricscompanion;

/** Pure local geometry; callers apply the segment transform and draw with a hardware Canvas. */
final class FoliaOriginalSonnetFrameDecor {
    interface Target {
        void path(double[] points, boolean fill, double width, double alpha);
        void circle(double x, double y, double radius, double alpha, boolean white);
    }

    private FoliaOriginalSonnetFrameDecor() { }

    static void draw(Target target, double measuredWidth, double measuredHeight, double rotation,
                     double fontSize, int variant, double rawProgress) {
        double progress = clamp(rawProgress, 0, 1);
        double eased = progress == 1 ? 1 : 1 - Math.pow(2, -10 * progress);
        if (eased <= 0) return;
        long quarterTurns = Math.round(rotation / (Math.PI / 2));
        boolean odd = Math.abs(rotation - quarterTurns * Math.PI / 2) < 0.000001
                && Math.abs(quarterTurns % 2) == 1;
        double pad = clamp(fontSize * 0.22, 8, 20);
        double halfW = (odd ? measuredHeight : measuredWidth) / 2 + pad;
        double halfH = (odd ? measuredWidth : measuredHeight) / 2 + pad;
        double cornerGap = clamp(Math.min(halfW, halfH) * (variant == 1 ? 0.42 : 0.3), 6, 30);
        double stroke = clamp(fontSize * 0.03, 1.2, 2.2);
        double[] xs = {-halfW, halfW, halfW, -halfW};
        double[] ys = {-halfH, -halfH, halfH, halfH};
        for (int side = 0; side < 4; side++) {
            double sideProgress = clamp((eased - side * 0.2) / 0.4, 0, 1);
            if (sideProgress <= 0) continue;
            int next = (side + 1) % 4;
            double insetX = xs[next] == xs[side] ? 0 : cornerGap * (xs[next] > xs[side] ? 1 : -1);
            double insetY = ys[next] == ys[side] ? 0 : cornerGap * (ys[next] > ys[side] ? 1 : -1);
            double startX = xs[side] + insetX, startY = ys[side] + insetY;
            double endX = xs[next] - insetX, endY = ys[next] - insetY;
            if (variant != 3) {
                line(target, startX, startY, startX + (endX - startX) * sideProgress,
                        startY + (endY - startY) * sideProgress, stroke, 0.85);
            } else {
                double length = Math.hypot(endX - startX, endY - startY);
                if (length < 1) continue;
                double ux = (endX - startX) / length, uy = (endY - startY) / length;
                double dash = clamp(fontSize * 0.14, 5, 8), gap = dash * 0.7;
                for (double offset = 0; offset + dash <= length * sideProgress + 0.001; offset += dash + gap) {
                    line(target, startX + ux * offset, startY + uy * offset,
                            startX + ux * (offset + dash), startY + uy * (offset + dash), stroke, 0.85);
                }
            }
        }
        for (int corner = 0; corner < 4; corner++) {
            double phase = clamp((eased - 0.35 - corner * 0.14) / 0.3, 0, 1);
            double op = Math.pow(2, -10 * phase) * Math.sin((phase - 0.3 / 4) * Math.PI * 2 / 0.3) + 1;
            if (op <= 0.02) continue;
            double sx = corner == 0 || corner == 3 ? -1 : 1, sy = corner < 2 ? -1 : 1;
            double cx = xs[corner], cy = ys[corner];
            if (variant == 0) {
                double arm = clamp(pad * 0.8, 5, 12), x = cx + sx * 3, y = cy + sy * 3;
                target.path(new double[]{x + sx * arm * op, y, x, y, x, y + sy * arm * op}, false, stroke, 0.9 * Math.min(1, op));
            } else if (variant == 1) {
                double radius = clamp(pad * 0.55, 4, 9), petal = radius * 0.44 * op;
                target.circle(cx - radius, cy, petal, 0.8, false);
                target.circle(cx + radius, cy, petal, 0.8, false);
                target.circle(cx, cy - radius, petal, 0.8, false);
                target.circle(cx, cy + radius, petal, 0.8, false);
                target.circle(cx, cy, radius * 0.3 * op, 0.5, true);
            } else if (variant == 2) {
                double arm = clamp(pad, 6, 14), x = cx + sx * 2.5, y = cy + sy * 2.5;
                target.path(new double[]{x + sx * arm * op, y, x, y, x, y + sy * arm * op}, false, stroke * 1.4, 0.9);
                target.path(new double[]{x + sx * (arm * op + 4), y + sy * 4,
                        x + sx * 4, y + sy * 4, x + sx * 4, y + sy * (arm * op + 4)}, false, stroke * 0.8, 0.5);
            } else {
                double size = clamp(pad * 0.6, 4, 9), dx = sx / Math.sqrt(2), dy = sy / Math.sqrt(2);
                double px = sy / Math.sqrt(2), py = -sx / Math.sqrt(2), half = size * 0.7 * op;
                double tipX = cx + dx * (2 + size * op), tipY = cy + dy * (2 + size * op);
                double baseX = cx + dx * 2, baseY = cy + dy * 2;
                target.path(new double[]{tipX, tipY, baseX + px * half, baseY + py * half,
                        baseX - px * half, baseY - py * half}, true, 0, 0.85);
                target.circle(cx, cy, stroke, 0.9, false);
            }
        }
        if (variant == 2) {
            double diamond = clamp(pad * 0.45, 3.5, 7);
            for (int side = 0; side < 4; side++) {
                double phase = clamp((eased - 0.35 - side * 0.14) / 0.3, 0, 1);
                double op = Math.pow(2, -10 * phase) * Math.sin((phase - 0.3 / 4) * Math.PI * 2 / 0.3) + 1;
                if (op <= 0.02) continue;
                int next = (side + 1) % 4;
                double midX = (xs[side] + xs[next]) / 2, midY = (ys[side] + ys[next]) / 2, size = diamond * op;
                target.path(new double[]{midX, midY - size, midX + size, midY,
                        midX, midY + size, midX - size, midY}, true, 0, 0.85);
            }
        }
    }

    private static void line(Target target, double x1, double y1, double x2, double y2, double width, double alpha) {
        target.path(new double[]{x1, y1, x2, y2}, false, width, alpha);
    }
    private static double clamp(double value, double min, double max) { return Math.min(max, Math.max(min, value)); }
}
