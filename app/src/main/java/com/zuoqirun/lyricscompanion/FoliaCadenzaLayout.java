// SPDX-License-Identifier: AGPL-3.0-only
// Port of Folia cadenzaPlacements.ts (9279251); attribution: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Seeded emphasis and collision search in measured text space, independent of Android drawing. */
final class FoliaCadenzaLayout {
    static final class Placement {
        int first, end, word, row;
        double x, y, width, height, scale, emphasis = 1;
        double rotation, driftX, driftY;
        double baseX, baseY, collisionWidth, collisionHeight, padding;
        long start, finish;
    }

    private FoliaCadenzaLayout() { }

    static List<Placement> build(LrcTimeline.Line line, FoliaGlyphTimeline timeline,
                                 float[] offsets, int[] rowEnds, float font, float step, float maxWidth) {
        List<int[]> words = new ArrayList<>();
        int cursor = 0;
        for (LrcTimeline.Word word : line.words) {
            int found = line.text.indexOf(word.text, cursor);
            if (found < 0 || word.text.isEmpty()) continue;
            int first = glyphIndex(timeline, found), end = glyphIndex(timeline, found + word.text.length());
            if (end > first) words.add(new int[]{first, end});
            cursor = found + word.text.length();
        }
        if (words.isEmpty()) for (int g = 0; g < timeline.glyphs.length; g++) words.add(new int[]{g, g + 1});
        List<Placement> plans = new ArrayList<>();
        double totalHeight = Math.max(1, rowEnds.length) * step;
        int rowFirst = 0;
        for (int row = 0; row < rowEnds.length; row++) {
            int rowEnd = rowEnds[row];
            for (int w = 0; w < words.size(); w++) {
                int first = Math.max(rowFirst, words.get(w)[0]), end = Math.min(rowEnd, words.get(w)[1]);
                if (end <= first) continue;
                Placement p = new Placement();
                p.first = first; p.end = end; p.word = w; p.row = row;
                p.start = timeline.starts[words.get(w)[0]];
                p.finish = timeline.ends[words.get(w)[1] - 1];
                p.width = Math.max(offsets[end] - offsets[first], font * 0.18);
                p.baseX = -(offsets[rowEnd] - offsets[rowFirst]) / 2 + offsets[first] - offsets[rowFirst];
                p.baseY = -totalHeight / 2 + font + row * step;
                plans.add(p);
            }
            rowFirst = rowEnd;
        }
        Placement hero = null;
        double bestScore = -1;
        for (Placement p : plans) {
            int[] word = words.get(p.word);
            String value = line.text.substring(timeline.offsets[p.first], timeline.offsets[p.end]);
            if (p.first != word[0] || p.end != word[1] || value.trim().isEmpty()) continue;
            double semantic = FoliaSceneMath.isCjk(value) ? 0.18 : Math.min((word[1] - word[0]) * 0.08, 0.36);
            double center = 1 - Math.abs(p.word - (words.size() - 1) / 2.0) / Math.max(1, words.size());
            double score = semantic + center * 0.18;
            if (score > bestScore) { hero = p; bestScore = score; }
        }
        if (hero != null) hero.emphasis = 1.46 * (1 + FoliaRingMath.clamp(bestScore - 0.48, 0, 0.52));
        for (Placement p : plans) {
            p.scale = 1.01 * p.emphasis;
            p.height = font * p.scale * 0.95;
            p.collisionWidth = p.width * p.scale * (p.emphasis > 1 ? 1.48 : 1.26);
            p.collisionHeight = p.height * (p.emphasis > 1 ? 1.36 : 1.24);
            p.padding = p.emphasis > 1 ? 16 : 8;
        }
        Collections.sort(plans, new Comparator<Placement>() {
            @Override public int compare(Placement a, Placement b) {
                int emphasis = Double.compare(b.emphasis, a.emphasis);
                if (emphasis != 0) return emphasis;
                return a.row != b.row ? a.row - b.row : a.first - b.first;
            }
        });
        List<double[]> occupied = new ArrayList<>();
        for (Placement p : plans) {
            double preferredX = p.emphasis > 1 ? -p.width / 2 : p.baseX;
            double preferredY = p.emphasis > 1 ? 0 : p.baseY;
            if (p.emphasis <= 1 && hero != null) {
                double dx = preferredX + p.width / 2 - hero.width * hero.scale / 2;
                double dy = preferredY - p.height * 0.46 + hero.height * 0.46;
                double distance = Math.hypot(dx, dy);
                if (distance < 1) { dx = preferredX >= 0 ? 1 : -1; dy = p.row % 2 == 0 ? -0.65 : 0.65; }
                double separation = hero.width * hero.scale * 0.34 + p.width * 0.52 + p.padding * 2;
                if (distance < separation) {
                    double length = Math.max(1, Math.hypot(dx, dy));
                    preferredX += dx / length * (separation - distance);
                    preferredY += dy / length * (separation - distance) * 0.92;
                }
            }
            double stepRadius = Math.max(10, Math.round(font * 0.14));
            double maxRadius = p.emphasis > 1 ? Math.max(20, step * 0.5)
                    : Math.max(step * 2.2, Math.max(p.collisionWidth * 0.75, 56));
            double baseAngle = hero != null && p.emphasis <= 1 ? Math.atan2(preferredY, preferredX + p.width / 2) : 0;
            double bestX = preferredX, bestY = preferredY, fallbackScore = Double.POSITIVE_INFINITY;
            boolean found = false;
            for (double radius = 0; radius <= maxRadius && !found; radius += stepRadius) {
                int samples = radius == 0 ? 1 : p.emphasis > 1 ? 8
                        : Math.max(12, (int) Math.round(Math.PI * 2 * radius / Math.max(stepRadius * 1.1, 10)));
                for (int sample = 0; sample < samples; sample++) {
                    double angle = baseAngle + sample / (double) samples * Math.PI * 2;
                    double dx = radius == 0 ? 0 : Math.cos(angle) * radius;
                    double dy = radius == 0 ? 0 : Math.sin(angle) * radius * (p.emphasis > 1 ? 0.8 : 0.92);
                    double left = preferredX + dx - p.padding;
                    double top = preferredY + dy - p.collisionHeight - p.padding;
                    double right = left + p.collisionWidth + p.padding * 2;
                    double bottom = top + p.collisionHeight + p.padding * 2;
                    if (left < -maxWidth / 2 - 72 || right > maxWidth / 2 + 72
                            || top < -Math.max(totalHeight * 0.9, step * 1.6)
                            || bottom > Math.max(totalHeight * 0.9, step * 1.45)) continue;
                    double overlap = 0;
                    for (double[] box : occupied) overlap += Math.max(0, Math.min(right, box[2]) - Math.max(left, box[0]))
                            * Math.max(0, Math.min(bottom, box[3]) - Math.max(top, box[1]));
                    double score = overlap * 2.2 + Math.hypot(dx, dy);
                    if (score < fallbackScore) { bestX = preferredX + dx; bestY = preferredY + dy; fallbackScore = score; }
                    if (overlap == 0) { bestX = preferredX + dx; bestY = preferredY + dy; found = true; break; }
                }
            }
            p.x = bestX; p.y = bestY;
            occupied.add(new double[]{p.x - p.padding, p.y - p.collisionHeight - p.padding,
                    p.x + p.collisionWidth + p.padding, p.y + p.padding});
            int fragmentIndex = 0;
            for (Placement other : plans) if (other.row == p.row && other.first < p.first) fragmentIndex++;
            double seed = line.timeMs + p.word * 17 + p.row * 31 + fragmentIndex * 13;
            p.rotation = (FoliaSceneMath.random(seed + 3) - 0.5) * 12;
            double outwardX = p.x + p.width / 2, outwardY = p.y - p.height * 0.46;
            double length = Math.max(Math.hypot(outwardX, outwardY), 1);
            double drift = p.emphasis > 1 ? 4 + FoliaSceneMath.random(seed + 6) * 4 : 5 + FoliaSceneMath.random(seed + 6) * 6;
            p.driftX = outwardX / length * drift + (FoliaSceneMath.random(seed + 7) - 0.5) * 2.4;
            p.driftY = outwardY / length * drift * 0.72 + (FoliaSceneMath.random(seed + 8) - 0.5) * 2;
        }
        return Collections.unmodifiableList(plans);
    }

    private static int glyphIndex(FoliaGlyphTimeline timeline, int offset) {
        int result = 0;
        while (result < timeline.glyphs.length && timeline.offsets[result] < offset) result++;
        return result;
    }
}
