// SPDX-License-Identifier: AGPL-3.0-only
// Folia grapheme/timeline adapter. Attribution and source revision: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.List;

/** Grapheme-safe native timeline, preserving actual word timestamps whenever supplied. */
final class FoliaGlyphTimeline {
    final String[] glyphs;
    final long[] starts;
    final long[] ends;
    final int[] offsets;
    final long renderEnd;

    FoliaGlyphTimeline(LrcTimeline.Line line, long nextStart) {
        glyphs = split(line.text);
        starts = new long[glyphs.length];
        ends = new long[glyphs.length];
        offsets = new int[glyphs.length + 1];
        long lineEnd = line.timeMs + Math.max(1, line.durationMs > 0 ? line.durationMs
                : nextStart > line.timeMs ? nextStart - line.timeMs : 5000);
        renderEnd = Math.max(lineEnd, nextStart > line.timeMs
                ? nextStart - FoliaRingMath.ENTRY_LEAD_MS : lineEnd + 10000);
        int cursor = 0;
        for (int i = 0; i < glyphs.length; i++) {
            offsets[i] = cursor;
            cursor += glyphs[i].length();
            starts[i] = line.timeMs + (lineEnd - line.timeMs) * i / Math.max(1, glyphs.length);
            ends[i] = line.timeMs + (lineEnd - line.timeMs) * (i + 1) / Math.max(1, glyphs.length);
        }
        offsets[glyphs.length] = cursor;
        int wordCursor = 0;
        for (LrcTimeline.Word word : line.words) {
            int begin = line.text.indexOf(word.text, wordCursor);
            if (begin < 0 || word.text.isEmpty()) continue;
            int finish = begin + word.text.length();
            int first = -1, last = -1;
            for (int i = 0; i < glyphs.length; i++) {
                if (offsets[i] < finish && offsets[i + 1] > begin) {
                    if (first < 0) first = i;
                    last = i;
                }
            }
            if (first >= 0) {
                for (int i = first; i <= last; i++) {
                    starts[i] = word.startMs + word.durationMs * (i - first) / (last - first + 1);
                    ends[i] = word.startMs + word.durationMs * (i - first + 1) / (last - first + 1);
                }
            }
            wordCursor = finish;
        }
        // Missing spaces/punctuation borrow the interval between timed neighboring glyphs.
        if (!line.words.isEmpty()) {
            for (int i = 0; i < glyphs.length; i++) {
                if (glyphs[i].trim().isEmpty()) {
                    starts[i] = i > 0 ? ends[i - 1] : line.timeMs;
                    ends[i] = i + 1 < glyphs.length ? Math.max(starts[i], starts[i + 1]) : lineEnd;
                }
            }
        }
        for (int i = 0; i < glyphs.length; i++) {
            if (ends[i] != starts[i]) continue;
            int end = i;
            while (end < glyphs.length && starts[end] == ends[end]) end++;
            long gapStart = i > 0 ? ends[i - 1] : line.timeMs;
            long gapEnd = end < glyphs.length ? starts[end] : lineEnd;
            long deficit = (end - i) * 60L - (gapEnd - gapStart);
            if (deficit > 0) {
                long steal = i > 0 ? Math.min(end < glyphs.length ? deficit / 2 : deficit,
                        Math.max(0, ends[i - 1] - starts[i - 1] - 40)) : 0;
                gapStart -= steal;
                if (i > 0) ends[i - 1] = gapStart;
                if (end < glyphs.length) {
                    gapEnd += Math.min(deficit - steal, Math.max(0, ends[end] - starts[end] - 40));
                    starts[end] = gapEnd;
                }
            }
            for (int g = i; g < end; g++) {
                starts[g] = gapStart + (gapEnd - gapStart) * (g - i) / (end - i);
                ends[g] = gapStart + (gapEnd - gapStart) * (g - i + 1) / (end - i);
            }
            i = end - 1;
        }
    }

    double progress(int index, long time) {
        return FoliaRingMath.clamp((time - starts[index]) / (double) Math.max(1, ends[index] - starts[index]), 0, 1);
    }

    static String[] split(String text) {
        List<String> result = new ArrayList<>();
        int begin = 0, previous = -1, regionals = 0;
        for (int index = 0; index < text.length();) {
            int code = text.codePointAt(index);
            int type = Character.getType(code);
            boolean regional = code >= 0x1F1E6 && code <= 0x1F1FF;
            boolean join = index == 0 || code == 0x200D || previous == 0x200D
                    || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK || code >= 0xFE00 && code <= 0xFE0F
                    || code >= 0x1F3FB && code <= 0x1F3FF || regional && regionals % 2 == 1;
            if (!join) {
                result.add(text.substring(begin, index));
                begin = index;
            }
            regionals = regional ? regionals + 1 : 0;
            previous = code;
            index += Character.charCount(code);
        }
        if (begin < text.length()) result.add(text.substring(begin));
        return result.toArray(new String[0]);
    }
}
