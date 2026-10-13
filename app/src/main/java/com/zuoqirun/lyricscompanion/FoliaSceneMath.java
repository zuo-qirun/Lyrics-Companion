// SPDX-License-Identifier: AGPL-3.0-only
// Native Folia layout/timing formulas. Reference: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.List;

/** Pure layout decisions shared by the renderer and its regression fixtures. */
final class FoliaSceneMath {
    private FoliaSceneMath() { }

    static float clampFont(float width, float min, float vw, float max, float scale) {
        return Math.max(min, Math.min(width * vw / 100, max)) * scale;
    }

    static double random(double seed) {
        double value = Math.sin(seed) * 10000;
        return value - Math.floor(value);
    }

    static double easeOut(double value) {
        return 1 - Math.pow(1 - FoliaRingMath.clamp(value, 0, 1), 3);
    }

    static double spring(double seconds, double stiffness, double damping, double mass) {
        if (seconds <= 0) return 0;
        double decay = damping / (2 * mass);
        double squared = stiffness / mass - decay * decay;
        if (Math.abs(squared) < 0.000001) return 1 - Math.exp(-decay * seconds) * (1 + decay * seconds);
        if (squared > 0) {
            double frequency = Math.sqrt(squared);
            return 1 - Math.exp(-decay * seconds)
                    * (Math.cos(frequency * seconds) + decay / frequency * Math.sin(frequency * seconds));
        }
        double frequency = Math.sqrt(-squared);
        double a = -decay + frequency, b = -decay - frequency;
        return 1 - (b * Math.exp(a * seconds) - a * Math.exp(b * seconds)) / (b - a);
    }

    static int tiltRowCount(int count, long startMs) {
        double score = Math.log(count + 4) / Math.log(24)
                * (random(startMs + 1) * 0.6 + 0.7) * 0.75;
        return score < 0.45 ? 1 : score < 1.05 ? 2 : score < 1.7 ? 3 : 4;
    }

    static int tiltAccentRow(int rows, long startMs) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < rows; i++) if (random(startMs + 100 + i) < 0.35) candidates.add(i);
        return candidates.isEmpty() ? -1 : candidates.get((int) (random(startMs + 200) * candidates.size()));
    }

    static float cadenzaFont(float width, int glyphs, int words, float scale) {
        double base = FoliaRingMath.clamp(width * 0.086, 34, 94);
        double penalty = Math.min(Math.max(0, glyphs - 12) * 1.8, 34)
                + Math.min(Math.max(0, words - 7) * 1.5, 18);
        return (float) FoliaRingMath.clamp(FoliaRingMath.clamp(base - penalty, 28, 104) * 1.12 * scale, 24, 132);
    }

    static final class PartitaChunk {
        final int first, end;
        final float x, y, scale;
        PartitaChunk(int first, int end, float x, float y, float scale) {
            this.first = first; this.end = end; this.x = x; this.y = y; this.scale = scale;
        }
    }

    /** The chunk-size pass consumes its random values before the stagger/scale pass. */
    static List<PartitaChunk> partitaChunks(int units, float height, double seed) {
        List<PartitaChunk> result = new ArrayList<>();
        if (units == 0) return result;
        int rows = Math.max(1, Math.min(units, (int) (height * 0.65f / 100)));
        int[] ends = new int[rows];
        int remaining = units, cursor = 0;
        for (int r = 0; r < rows; r++) {
            double average = remaining / (double) (rows - r);
            int take = r == rows - 1 ? remaining : (int) Math.round(average + (random(seed++) - 0.5) * average);
            take = Math.max(1, Math.min(take, remaining - (rows - r - 1)));
            cursor += take; remaining -= take; ends[r] = cursor;
        }
        int first = 0;
        for (int r = 0; r < rows; r++) {
            float x = (r % 2 == 0 ? -1 : 1) * (20 + (float) random(seed++) * 80);
            float scale = 0.8f + (float) random(seed++) * 0.9f;
            result.add(new PartitaChunk(first, ends[r], x, (r - (rows - 1) / 2f) * 2.5f, scale));
            first = ends[r];
        }
        return result;
    }

    static double sonnetHeroScore(int graphemes, long durationMs) {
        // A fractional upstream timestamp becomes integer milliseconds in Android; ignore sub-frame ties.
        return Math.min(graphemes, 8) * 14 + Math.min(2.5, Math.round(durationMs / 10.0) / 100.0) * 18;
    }

    static double cadenzaGlow(long time, long start, long end) {
        if (time < start) return 0;
        if (time <= end) {
            double progress = FoliaRingMath.clamp((time - start) / (double) Math.max(100, end - start), 0, 1);
            if (progress < 0.18) return easeOut(progress / 0.18);
            return progress < 0.9 ? 1 : 1 - (progress - 0.9);
        }
        double fade = FoliaRingMath.clamp((time - end) / 900.0, 0, 1);
        return 0.9 * (1 - fade) * (1 - fade);
    }

    static long hash(String value) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < value.length(); i++) hash = (hash ^ value.charAt(i)) * 16777619;
        return hash & 0xffffffffL;
    }

    static boolean[] cappellaSides(List<LrcTimeline.Line> lines) {
        int[] senders = cappellaSenders(lines);
        boolean[] result = new boolean[senders.length];
        for (int i = 0; i < senders.length; i++) result[i] = senders[i] == 8;
        return result;
    }

    static int[] cappellaSenders(List<LrcTimeline.Line> lines) {
        int[] result = new int[lines.size()];
        boolean[] sequence = {false, true, false, true, true};
        int cursor = 0, avatar = 0;
        boolean hasSender = false;
        for (int i = 0; i < lines.size(); i++) {
            LrcTimeline.Line line = lines.get(i);
            // Folia uses seconds in JavaScript's seed string, without trailing .0.
            String seconds = line.timeMs % 1000 == 0 ? String.valueOf(line.timeMs / 1000)
                    : java.math.BigDecimal.valueOf(line.timeMs, 3).stripTrailingZeros().toPlainString();
            boolean force = (i + 1) % 5 == 0;
            boolean carry = hasSender && FoliaGlyphTimeline.split(line.text.replaceAll("\\s", "")).length <= 12
                    && hash("carry|" + seconds + '|' + i) / (double) 0xffffffffL <= 0.68;
            boolean flip = !force && hash("flip|" + seconds + '|' + i) / (double) 0xffffffffL < 0.18;
            boolean right = force || (carry ? result[i - 1] == 8 : sequence[cursor % sequence.length] ^ flip);
            result[i] = carry && !force ? result[i - 1] : right ? 8 : avatar % 5;
            if (force) { cursor = 0; hasSender = false; }
            else { if (!carry) { cursor++; if (!right) avatar++; } hasSender = true; }
        }
        return result;
    }

    /** Wrap on grapheme boundaries, preferring spaces and punctuation over splitting Latin words. */
    static int[] wrap(String[] glyphs, float[] offsets, float maxWidth) {
        return wrap(glyphs, offsets, maxWidth, 0);
    }

    static int[] wrap(String[] glyphs, float[] offsets, float maxWidth, float spacing) {
        List<Integer> ends = new ArrayList<>();
        int first = 0;
        while (first < glyphs.length) {
            int end = first + 1, boundary = -1;
            while (end <= glyphs.length && FoliaTextSpacing.extent(offsets, first, end, spacing) <= maxWidth) {
                String previous = glyphs[end - 1];
                if (previous.trim().isEmpty() || previous.matches("[，。；！？、,.!?;:]")) boundary = end;
                end++;
            }
            end = Math.max(first + 1, end - 1);
            if (end < glyphs.length && boundary > first && boundary < end
                    && !isCjk(glyphs[end - 1]) && !isCjk(glyphs[end])) end = boundary;
            ends.add(end);
            first = end;
        }
        int[] result = new int[ends.size()];
        for (int i = 0; i < result.length; i++) result[i] = ends.get(i);
        return result;
    }

    static boolean isCjk(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x4e00 && c <= 0x9fa5 || c >= 0x3040 && c <= 0x30ff || c >= 0xac00 && c <= 0xd7af) return true;
        }
        return false;
    }
}
