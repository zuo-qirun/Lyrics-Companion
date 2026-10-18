// SPDX-License-Identifier: AGPL-3.0-only
// Native port of Folia graphemeTiming.ts / claddaghTimeline.ts (9279251).
// Copyright chthollyphile and Folia contributors. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.Arrays;

/** Original word-to-line mapping, including fractional milliseconds and untimed punctuation. */
final class FoliaOriginalGraphemeTiming {
    final String[] glyphs;
    final double[] starts, ends;
    final int[] wordIndices;
    final double lineEnd, renderEnd;

    FoliaOriginalGraphemeTiming(LrcTimeline.Line line, long nextStart, boolean claddagh) {
        glyphs = FoliaGlyphTimeline.split(line.text);
        starts = new double[glyphs.length];
        ends = new double[glyphs.length];
        wordIndices = new int[glyphs.length];
        Arrays.fill(wordIndices, -1);
        lineEnd = line.timeMs + Math.max(0, line.durationMs > 0 ? line.durationMs
                : nextStart > line.timeMs ? nextStart - line.timeMs : 5000);
        if (line.words.isEmpty()) {
            for (int i = 0; i < glyphs.length; i++) {
                starts[i] = line.timeMs + (lineEnd - line.timeMs) * i / glyphs.length;
                ends[i] = line.timeMs + (lineEnd - line.timeMs) * (i + 1) / glyphs.length;
            }
        } else {
            boolean[] resolved = new boolean[glyphs.length];
            int cursor = 0;
            double lastResolvedTime = line.timeMs;
            for (int wordIndex = 0; wordIndex < line.words.size(); wordIndex++) {
                LrcTimeline.Word word = line.words.get(wordIndex);
                String[] wordGlyphs = FoliaGlyphTimeline.split(word.text);
                if (wordGlyphs.length == 0) continue;
                int match = find(glyphs, wordGlyphs, cursor);
                int start = match < 0 ? cursor : match;
                int end = Math.min(start + wordGlyphs.length, glyphs.length);
                for (int i = cursor; i < start; i++) {
                    starts[i] = ends[i] = word.startMs;
                    resolved[i] = true;
                }
                for (int i = start; i < end; i++) {
                    int local = i - start;
                    starts[i] = word.startMs + word.durationMs * local / (double) wordGlyphs.length;
                    ends[i] = word.startMs + word.durationMs * (local + 1) / (double) wordGlyphs.length;
                    wordIndices[i] = wordIndex;
                    resolved[i] = true;
                    lastResolvedTime = Math.max(lastResolvedTime, ends[i]);
                }
                cursor = Math.max(cursor, end);
            }
            for (int i = 0; i < glyphs.length; i++) if (!resolved[i]) {
                starts[i] = ends[i] = lastResolvedTime;
            }
        }
        if (claddagh) adjustCladdagh(line.timeMs);
        double hintsEnd = renderEnd(line, lineEnd);
        // Claddagh reads explicit renderHints only. The native line has no such override;
        // the other visualizers call getLineRenderHints(), which derives the transition tail.
        renderEnd = claddagh ? Math.max(lineEnd, nextStart > line.timeMs ? nextStart - 340 : lineEnd + 10000)
                : hintsEnd;
    }

    private static int find(String[] source, String[] target, int from) {
        for (int i = from; i <= source.length - target.length; i++) {
            boolean matches = true;
            for (int j = 0; j < target.length; j++) if (!source[i + j].equals(target[j])) {
                matches = false;
                break;
            }
            if (matches) return i;
        }
        return -1;
    }

    private void adjustCladdagh(double lineStart) {
        for (int i = 0; i < starts.length;) {
            if (starts[i] != ends[i]) { i++; continue; }
            int end = i;
            while (end < starts.length && starts[end] == ends[end]) end++;
            double gapStart = i > 0 ? ends[i - 1] : lineStart;
            double gapEnd = end < starts.length ? starts[end] : lineEnd;
            double deficit = (end - i) * 60 - (gapEnd - gapStart);
            if (deficit > 0) {
                if (i > 0 && end < starts.length) {
                    double previous = Math.min(deficit / 2, Math.max(0, ends[i - 1] - starts[i - 1] - 40));
                    double next = Math.min(deficit - previous, Math.max(0, ends[end] - starts[end] - 40));
                    gapStart -= previous; gapEnd += next;
                    ends[i - 1] = gapStart; starts[end] = gapEnd;
                } else if (i > 0) {
                    gapStart -= Math.min(deficit, Math.max(0, ends[i - 1] - starts[i - 1] - 40));
                    ends[i - 1] = gapStart;
                } else if (end < starts.length) {
                    gapEnd += Math.min(deficit, Math.max(0, ends[end] - starts[end] - 40));
                    starts[end] = gapEnd;
                }
            }
            double unit = Math.max(0, gapEnd - gapStart) / (end - i);
            for (int g = i; g < end; g++) {
                starts[g] = gapStart + unit * (g - i);
                ends[g] = gapStart + unit * (g - i + 1);
            }
            i = end;
        }
    }

    static double renderEnd(LrcTimeline.Line line, double lineEnd) {
        double duration = Math.max(0, lineEnd - line.timeMs);
        if (duration < 100) return Math.max(lineEnd, line.timeMs + 67);
        boolean fast = duration < 180;
        double enter = fast ? clamp(duration * 0.45, 45, 60) : clamp(Math.max(duration, 120) * 0.34, 220, 420);
        double exit = fast ? clamp(duration * 0.22, 30, 40) : clamp(Math.max(duration, 120) * 0.18, 180, 320);
        LrcTimeline.Word last = line.words.isEmpty() ? null : line.words.get(line.words.size() - 1);
        double passStart = Math.max(last == null ? lineEnd : last.startMs + last.durationMs, line.timeMs) + (fast ? 30 : 60);
        double exitStart = Math.max(passStart, lineEnd - exit);
        if (fast) exitStart = Math.max(exitStart, line.timeMs + enter + 10);
        return Math.max(lineEnd, exitStart + exit);
    }

    double progress(int index, double time) {
        return time >= ends[index] ? 1 : time > starts[index]
                ? clamp((time - starts[index]) / (ends[index] - starts[index]), 0, 1) : 0;
    }

    double fractionalIndex(double time) {
        if (starts.length == 0 || time <= starts[0]) return 0;
        int last = starts.length - 1;
        if (last == 0) return (time - starts[0]) / Math.max(200, renderEnd - starts[0]);
        if (time >= starts[last]) {
            if (renderEnd > starts[last]) return last + clamp((time - starts[last]) / (renderEnd - starts[last]), 0, 1) * 2;
            double duration = ends[last] - starts[last], gap = starts[last] - starts[last - 1];
            return last + Math.min((time - starts[last]) / (duration > 0 ? duration : gap > 0 ? gap : 500), 1.8);
        }
        for (int i = 0; i < last; i++) if (time >= starts[i] && time < starts[i + 1]) {
            return i + (time - starts[i]) / (starts[i + 1] - starts[i]);
        }
        return last;
    }

    double playbackProgress(double time) {
        if (starts.length == 0) return 0;
        return starts.length == 1 ? clamp((time - starts[0]) / Math.max(1, renderEnd - starts[0]), 0, 1)
                : clamp(fractionalIndex(time) / (starts.length - 1), 0, 1);
    }

    double wordOffset(double[] angles, double time) {
        if (angles.length == 0) return 0;
        double index = fractionalIndex(time);
        int last = angles.length - 1;
        if (index <= last) {
            int i = (int) Math.floor(index);
            return angles[i] + (angles[Math.min(i + 1, last)] - angles[i]) * (index - i);
        }
        return angles[last] + (angles[last] - angles[Math.max(0, last - 1)]) * (index - last);
    }

    private static double clamp(double x, double min, double max) { return Math.max(min, Math.min(max, x)); }
}
