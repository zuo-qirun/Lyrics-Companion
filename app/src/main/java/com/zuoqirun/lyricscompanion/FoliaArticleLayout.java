// SPDX-License-Identifier: AGPL-3.0-only
// Folia fumeArticleLayout.ts / fumeTextMeasure.ts geometry adaptation (9279251).
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Seeded paper composition; measured em widths are supplied once by the native font engine. */
final class FoliaArticleLayout {
    static final class Block {
        int line;
        float x, y, width, height, font, lineHeight;
        boolean hero;
    }
    private FoliaArticleLayout() { }
    private static double seeded(String seed) { return FoliaSceneMath.hash(seed) % 10000 / 10000.0; }

    static Block[] build(final List<LrcTimeline.Line> lines, float width, float height, float scale,
                         final String seed, float[] emWidths) {
        float paper = (float) FoliaRingMath.clamp(Math.max(width * 1.95, width + 520), 920, 2400);
        int maxColumns = paper >= 1120 ? 4 : paper >= 760 ? 3 : paper >= 500 ? 2 : 1;
        double bestScore = Double.POSITIVE_INFINITY;
        Block[] best = new Block[0];
        for (int columns = maxColumns; columns >= 1; columns--) {
            double low = 0.82, high = 1.42;
            float gap = (float) FoliaRingMath.clamp(Math.round(paper * (columns >= 4 ? 0.0065 : columns == 3 ? 0.0085 : 0.0115)), 6, 14);
            final String key = seed + ':' + columns + ':' + Math.round(paper);
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < lines.size(); i++) if (!lines.get(i).text.trim().isEmpty()) order.add(i);
            Collections.sort(order, new Comparator<Integer>() {
                @Override public int compare(Integer a, Integer b) {
                    return Double.compare(seeded(key + ':' + a + ':' + lines.get(a).text), seeded(key + ':' + b + ':' + lines.get(b).text));
                }
            });
            boolean hasHero = false;
            for (int position = 0; position < order.size(); position++) hasHero |= naturalHero(lines.get(order.get(position)), position, order.size());
            int fallback = -1;
            double heroScore = -1;
            if (!hasHero) for (int position = 0; position < order.size(); position++) {
                LrcTimeline.Line line = lines.get(order.get(position));
                int count = FoliaGlyphTimeline.split(line.text.replaceAll("\\s", "")).length;
                if (count == 0 || count > 36) continue;
                double centered = Math.abs(position - order.size() / 2.0) / Math.max(1, order.size());
                double score = (1 - centered) * 0.62 + (count >= 6 && count <= 22 ? 1 : count <= 28 ? 0.72 : 0.36) * 0.34
                        + seeded(line.text + ':' + position + ":fallback-hero") * 0.04;
                if (score > heroScore) { heroScore = score; fallback = position; }
            }
            for (int attempt = 0; attempt < 8; attempt++) {
                double density = (low + high) / 2;
                float columnWidth = (paper - gap * (columns - 1)) / columns;
                float[] bottoms = new float[columns];
                java.util.Arrays.fill(bottoms, Math.max(height * 0.82f, 220));
                Block[] blocks = new Block[lines.size()];
                int bodyCursor = 0, heroCursor = 0;
                for (int position = 0; position < order.size(); position++) {
                    int index = order.get(position);
                    Block block = new Block(); block.line = index;
                    block.hero = position == fallback || naturalHero(lines.get(index), position, order.size());
                    int span = block.hero ? Math.min(columns, 2) : 1;
                    float spanWidth = span == 1 ? paper : columnWidth * span + gap * (span - 1);
                    block.width = block.hero ? span == 1 ? paper : columns == 2 ? columnWidth * 1.5f + gap * 0.5f : spanWidth : columnWidth;
                    double fontLow = block.hero ? 18 : 10, fontHigh = block.hero ? 58 : 30;
                    block.font = (float) (fontLow * scale * density);
                    for (int search = 0; search < 8; search++) {
                        double mid = (fontLow + fontHigh) / 2;
                        float font = (float) (mid * scale * density);
                        if (emWidths[index] * font <= block.width) { block.font = font; fontLow = mid; }
                        else fontHigh = mid;
                    }
                    block.lineHeight = Math.round(block.font * (block.hero ? 1.02f : 1.06f));
                    block.height = Math.max(1, (float) Math.ceil(emWidths[index] * block.font / block.width)) * block.lineHeight;
                    float minimum = Float.MAX_VALUE;
                    List<Integer> candidates = new ArrayList<>();
                    for (int column = 0; column <= columns - span; column++) {
                        float covered = 0;
                        for (int c = column; c < column + span; c++) covered = Math.max(covered, bottoms[c]);
                        if (covered < minimum) { minimum = covered; candidates.clear(); candidates.add(column); }
                        else if (covered == minimum) candidates.add(column);
                    }
                    int column = candidates.get((block.hero ? heroCursor++ : bodyCursor++) % candidates.size());
                    block.x = Math.max(width * 0.86f, 280) + column * (columnWidth + gap) + (block.hero ? Math.max(0, (spanWidth - block.width) / 2) : 0);
                    block.y = minimum;
                    float blockGap = block.hero ? Math.max(Math.round(block.lineHeight * 0.2f), 6) : Math.max(Math.round(block.lineHeight * 0.08f), 2);
                    for (int c = column; c < column + span; c++) bottoms[c] = block.y + block.height + blockGap;
                    blocks[index] = block;
                }
                float articleHeight = 0;
                for (float bottom : bottoms) articleHeight = Math.max(articleHeight, bottom);
                articleHeight += Math.max(height * 0.82f, 220);
                double target = Math.max(height, 240) * 2.45;
                double score = Math.abs(articleHeight - target) + Math.max(0, articleHeight - target) * 0.14;
                if (score < bestScore) { bestScore = score; best = blocks; }
                if (articleHeight < target) low = density; else high = density;
            }
        }
        return best;
    }

    private static boolean naturalHero(LrcTimeline.Line line, int index, int count) {
        int glyphs = FoliaGlyphTimeline.split(line.text.replaceAll("\\s", "")).length;
        return glyphs >= 4 && glyphs <= 28 && Math.abs(index - count / 2.0) / Math.max(1, count) < 0.72
                && ((index + 1) % 6 == 0 || seeded(line.text + ':' + index) > 0.965);
    }
}
