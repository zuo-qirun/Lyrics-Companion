/*
 * Port of Folia 0.7.17 fumeArticleLayout.ts (revision 9279251).
 * Copyright (c) chthollyphile and Folia contributors.
 * Licensed under the GNU Affero General Public License v3.0 only.
 */
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic paper-article layout used by the "fume" (浮名) mode.
 *
 * <p>Mirrors the upstream layout search: a seeded reading order, an optional
 * hero block that spans the paper, and a binary search over layout density that
 * keeps the resulting article close to the viewport's aspect ratio.
 */
final class FoliaArticleLayout {

    interface Measure {
        /** Number of rendered lines for {@code value} at {@code fontPx} within {@code innerWidth}. */
        int lineCount(String value, float fontPx, float innerWidth);
    }

    static final class Block {
        int line;
        boolean hero;
        float x;
        float y;
        float width;
        float height;
        float innerWidth;
        float font;
        float lineHeight;
        float lineHeightTotal;
    }

    static final class Article {
        float width;
        float height;
        float paperLeft;
        float paperTop;
        float paperRight;
        float paperBottom;
        float gap;
        int columns;
        Block[] blocks;
        Block heroBlock;
    }

    private static final class Attempt {
        float height;
        Block[] blocks;
        Block heroBlock;
    }

    private FoliaArticleLayout() {
    }

    /** Same distribution as upstream fumeMath.seeded(): (hash % 10000) / 10000. */
    private static double seededUnit(String seed) {
        return (FoliaSceneMath.hash(seed) % 10000) / 10000.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int renderableCount(String text) {
        String[] glyphs = FoliaGlyphTimeline.split(text);
        int count = 0;
        for (String glyph : glyphs) if (glyph.trim().length() > 0) count++;
        return count;
    }

    private static float round(float value) {
        return Math.round(value);
    }

    private static String orderKey(String seedKey, int index, String fullText) {
        return seedKey + ':' + index + ':' + fullText;
    }

    /** Upstream chooseNaturalBlockVariant(). */
    private static boolean naturalHero(LrcTimeline.Line line, int blockIndex, int total) {
        int graphemeCount = renderableCount(line.text);
        if (graphemeCount == 0) return false;
        boolean shortEnough = graphemeCount >= 4 && graphemeCount <= 28;
        double centered = Math.abs(blockIndex - total / 2.0) / Math.max(total, 1);
        double random = seededUnit(line.text + ':' + blockIndex);
        return shortEnough && centered < 0.72 && ((blockIndex + 1) % 6 == 0 || random > 0.965);
    }

    /** Upstream chooseFallbackHeroBlockIndex(); returns a block index or -1. */
    private static int chooseFallbackHero(List<LrcTimeline.Line> entries) {
        if (entries.isEmpty()) return -1;
        for (int i = 0; i < entries.size(); i++) {
            if (naturalHero(entries.get(i), i, entries.size())) return -1;
        }
        int bestIndex = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int blockIndex = 0; blockIndex < entries.size(); blockIndex++) {
            LrcTimeline.Line line = entries.get(blockIndex);
            int graphemeCount = renderableCount(line.text);
            if (graphemeCount == 0) continue;
            boolean comfortable = graphemeCount >= 4 && graphemeCount <= 28;
            boolean acceptable = graphemeCount <= 36;
            if (!comfortable && !acceptable) continue;
            double centered = Math.abs(blockIndex - entries.size() / 2.0) / Math.max(entries.size(), 1);
            double centerScore = 1 - centered;
            double lengthScore = graphemeCount >= 6 && graphemeCount <= 22
                    ? 1.0 : graphemeCount <= 28 ? 0.72 : 0.36;
            double stableJitter = seededUnit(line.text + ':' + blockIndex + ":fallback-hero") * 0.04;
            double score = centerScore * 0.62 + lengthScore * 0.34 + stableJitter;
            if (score > bestScore) {
                bestScore = score;
                bestIndex = blockIndex;
            }
        }
        if (bestIndex >= 0) return bestIndex;
        int shortestIndex = -1;
        int shortestCount = Integer.MAX_VALUE;
        for (int blockIndex = 0; blockIndex < entries.size(); blockIndex++) {
            int graphemeCount = renderableCount(entries.get(blockIndex).text);
            if (graphemeCount > 0 && graphemeCount < shortestCount) {
                shortestCount = graphemeCount;
                shortestIndex = blockIndex;
            }
        }
        return shortestIndex;
    }

    /**
     * Binary search for the largest font that still lays the block out on one line.
     * Mirrors upstream buildPreparedSingleLine (heroScale is 1 for the default tuning).
     */
    private static float searchFont(String value, float innerWidth, float density,
                                    boolean hero, float fontScale, Measure measure) {
        double low = hero ? 18.0 : 10.0;
        double high = hero ? 58.0 : 30.0;
        float best = -1f;
        for (int iteration = 0; iteration < 8; iteration++) {
            double middle = (low + high) / 2.0;
            float candidate = (float) (middle * fontScale * density);
            if (measure.lineCount(value, candidate, innerWidth) <= 1) {
                best = candidate;
                low = middle;
            } else {
                high = middle;
            }
        }
        if (best > 0f) return best;
        return (float) ((hero ? 18.0 : 10.0) * fontScale * density);
    }

    private static Attempt attempt(List<LrcTimeline.Line> lines,
                                   float viewportWidth, float viewportHeight,
                                   float fontScale, Measure measure,
                                   float paperWidth, float hMargin, float vMargin,
                                   int columns, float gap, double densityScale,
                                   String layoutSeedKey) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (renderableCount(lines.get(i).text) > 0) candidates.add(i);
        }
        if (candidates.isEmpty()) return null;

        String seedKey = layoutSeedKey + ':' + columns + ':' + Math.round(paperWidth);
        final String key = seedKey;
        final List<LrcTimeline.Line> source = lines;
        Collections.sort(candidates, new Comparator<Integer>() {
            @Override
            public int compare(Integer left, Integer right) {
                double leftValue = seededUnit(orderKey(key, left, source.get(left).text));
                double rightValue = seededUnit(orderKey(key, right, source.get(right).text));
                int result = Double.compare(leftValue, rightValue);
                return result != 0 ? result : Integer.compare(left, right);
            }
        });

        List<LrcTimeline.Line> entries = new ArrayList<>();
        for (int index : candidates) entries.add(lines.get(index));
        // forcedHero applies to positions inside the seeded reading order.
        int forcedHeroIndex = chooseFallbackHero(entries);

        float columnWidth = (float) ((paperWidth - gap * (columns - 1)) / (double) columns);
        float[] columnHeights = new float[columns];
        for (int i = 0; i < columns; i++) columnHeights[i] = vMargin;

        int bodyColumnTieCursor = 0;
        int heroPlacementTieCursor = 0;
        Attempt result = new Attempt();
        result.blocks = new Block[lines.size()];

        for (int blockIndex = 0; blockIndex < candidates.size(); blockIndex++) {
            int index = candidates.get(blockIndex);
            LrcTimeline.Line line = lines.get(index);
            boolean hero = blockIndex == forcedHeroIndex
                    || naturalHero(line, blockIndex, candidates.size());
            int span = hero ? Math.min(columns, columns <= 1 ? 1 : 2) : 1;
            float heroSpanWidth = span > 1 ? columnWidth * span + gap * (span - 1) : paperWidth;
            float blockWidth = !hero ? columnWidth
                    : (span == 1 ? paperWidth : (columns == 2 ? columnWidth * 1.5f + gap * 0.5f : heroSpanWidth));
            float innerWidth = Math.max(blockWidth, 120f);

            float fontPx = searchFont(line.text, innerWidth, (float) densityScale, hero, fontScale, measure);
            float lineHeight = round(fontPx * (hero ? 1.02f : 1.06f));
            float blockGap = hero ? Math.max(round(lineHeight * 0.2f), 6f) : Math.max(round(lineHeight * 0.08f), 2f);
            int rows = Math.max(1, measure.lineCount(line.text, fontPx, innerWidth));
            float blockHeight = lineHeight * rows;
            float x;
            float y;

            if (hero) {
                if (span == 1) {
                    y = columnHeights[0];
                    for (int i = 1; i < columns; i++) y = Math.max(y, columnHeights[i]);
                    x = hMargin;
                    columnHeights[0] = y + blockHeight + blockGap;
                } else {
                    float bestHeight = Float.POSITIVE_INFINITY;
                    List<Integer> starts = new ArrayList<>();
                    for (int startColumn = 0; startColumn <= columns - span; startColumn++) {
                        float covered = 0f;
                        for (int columnIndex = startColumn; columnIndex < startColumn + span; columnIndex++) {
                            covered = Math.max(covered, columnHeights[columnIndex]);
                        }
                        if (covered < bestHeight) {
                            bestHeight = covered;
                            starts.clear();
                            starts.add(startColumn);
                        } else if (covered == bestHeight) {
                            starts.add(startColumn);
                        }
                    }
                    int targetStart = starts.isEmpty() ? 0 : starts.get(heroPlacementTieCursor % starts.size());
                    heroPlacementTieCursor++;
                    y = bestHeight;
                    x = hMargin + targetStart * (columnWidth + gap)
                            + Math.max((heroSpanWidth - blockWidth) * 0.5f, 0f);
                    for (int columnIndex = targetStart; columnIndex < targetStart + span; columnIndex++) {
                        columnHeights[columnIndex] = y + blockHeight + blockGap;
                    }
                }
            } else {
                float minHeight = columnHeights[0];
                List<Integer> candidateColumns = new ArrayList<>();
                candidateColumns.add(0);
                for (int columnIndex = 1; columnIndex < columns; columnIndex++) {
                    float height = columnHeights[columnIndex];
                    if (height < minHeight) {
                        minHeight = height;
                        candidateColumns.clear();
                        candidateColumns.add(columnIndex);
                    } else if (height == minHeight) {
                        candidateColumns.add(columnIndex);
                    }
                }
                int targetColumn = candidateColumns.get(bodyColumnTieCursor % candidateColumns.size());
                bodyColumnTieCursor++;
                x = hMargin + targetColumn * (columnWidth + gap);
                y = columnHeights[targetColumn];
                columnHeights[targetColumn] = y + blockHeight + blockGap;
            }

            Block block = new Block();
            block.line = index;
            block.hero = hero;
            block.x = x;
            block.y = y;
            block.width = blockWidth;
            block.innerWidth = innerWidth;
            block.font = fontPx;
            block.lineHeight = lineHeight;
            block.lineHeightTotal = lineHeight;
            block.height = blockHeight;
            result.blocks[index] = block;
            if (hero) result.heroBlock = block;
        }

        float maxHeight = columnHeights[0];
        for (int i = 1; i < columns; i++) maxHeight = Math.max(maxHeight, columnHeights[i]);
        result.height = maxHeight + vMargin;
        return result;
    }

    /**
     * Builds the article layout for the given lyric lines. Returns {@code null}
     * when no line has renderable text.
     */
    static Article build(List<LrcTimeline.Line> lines, float viewportWidth, float viewportHeight,
                         float fontScale, String layoutSeedKey, Measure measure) {
        float paperWidth = (float) clamp(Math.max(viewportWidth * 1.95, viewportWidth + 520), 920, 2400);
        float viewportH = Math.max(viewportHeight, 240f);
        int maxColumns = paperWidth >= 1120 ? 4 : paperWidth >= 760 ? 3 : paperWidth >= 500 ? 2 : 1;
        float targetHeight = viewportH * 2.45f;
        float hMargin = Math.max(viewportWidth * 0.86f, 280f);
        float vMargin = Math.max(viewportHeight * 0.82f, 220f);
        String key = (layoutSeedKey == null || layoutSeedKey.isEmpty() ? "Folia" : layoutSeedKey);

        Attempt best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int bestColumns = 1;
        float bestGap = 10f;
        for (int columns = maxColumns; columns >= 1; columns--) {
            double low = 0.82;
            double high = 1.42;
            float gapDensity = columns >= 4 ? 0.0065f : columns == 3 ? 0.0085f : 0.0115f;
            float gap = (float) clamp(Math.round(paperWidth * gapDensity), 6, 14);
            for (int iteration = 0; iteration < 8; iteration++) {
                double densityScale = (low + high) / 2.0;
                Attempt attempt = attempt(lines, viewportWidth, viewportHeight, fontScale, measure,
                        paperWidth, hMargin, vMargin, columns, gap, densityScale, key);
                if (attempt == null) continue;
                double coveragePenalty = Math.abs(attempt.height - targetHeight);
                double overflowPenalty = attempt.height < targetHeight ? 0 : (attempt.height - targetHeight) * 0.14;
                double score = coveragePenalty + overflowPenalty;
                if (score < bestScore) {
                    bestScore = score;
                    best = attempt;
                    bestColumns = columns;
                    bestGap = gap;
                }
                if (attempt.height < targetHeight) {
                    low = densityScale;
                } else {
                    high = densityScale;
                }
            }
        }
        if (best == null) return null;

        Article article = new Article();
        article.columns = bestColumns;
        article.gap = bestGap;
        article.width = paperWidth + hMargin * 2f;
        article.height = best.height;
        article.paperLeft = hMargin;
        article.paperTop = vMargin;
        article.paperRight = hMargin + paperWidth;
        article.paperBottom = Math.max(best.height - vMargin, vMargin);
        article.blocks = best.blocks;
        article.heroBlock = best.heroBlock;
        return article;
    }
}
