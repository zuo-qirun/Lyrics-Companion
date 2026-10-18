// SPDX-License-Identifier: AGPL-3.0-only
// Folia lyric word adapter. Attribution and source revision: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the player's real per-word lyric timings onto grapheme ranges. The restored Folia modes
 * segment text with ICU when a line only carries plain LRC; when the player supplies timed words
 * they must drive those segments instead of a synthetic split.
 *
 * <p>Callers receive only the timed word spans. Inter-word punctuation and spaces stay between
 * spans so the caller can decide whether to attach them to the previous word or mark them as a
 * non-word segment; every glyph is preserved between the spans and the returned ranges.
 */
final class FoliaLyricWords {
    private FoliaLyricWords() {
    }

    static boolean hasRealWords(LrcTimeline.Line line) {
        return line.words != null && !line.words.isEmpty();
    }

    /**
     * Glyph index pairs {first, end} for the timed words of one line, or null when the line has no
     * usable word data. {@code offsets} must hold one extra entry for the end of the glyph list.
     */
    static List<int[]> spans(LrcTimeline.Line line, int glyphCount, int[] offsets) {
        if (!hasRealWords(line) || glyphCount == 0) return null;
        List<int[]> spans = new ArrayList<>();
        int cursor = 0, previous = 0;
        for (LrcTimeline.Word word : line.words) {
            if (word == null || word.text.isEmpty()) continue;
            int found = line.text.indexOf(word.text, cursor);
            if (found < 0) continue;
            cursor = found + word.text.length();
            int first = 0;
            while (first < glyphCount && offsets[first + 1] <= found) first++;
            int end = first;
            while (end < glyphCount && offsets[end] < cursor) end++;
            if (end <= first || first < previous) continue;
            spans.add(new int[]{first, end});
            previous = end;
        }
        return spans.isEmpty() ? null : spans;
    }

    /** Same mapping for renderers that measure glyph offsets as doubles. */
    static List<int[]> spans(LrcTimeline.Line line, int glyphCount, double[] offsets) {
        if (!hasRealWords(line) || glyphCount == 0) return null;
        List<int[]> spans = new ArrayList<>();
        int cursor = 0, previous = 0;
        for (LrcTimeline.Word word : line.words) {
            if (word == null || word.text.isEmpty()) continue;
            int found = line.text.indexOf(word.text, cursor);
            if (found < 0) continue;
            cursor = found + word.text.length();
            int first = 0;
            while (first < glyphCount && offsets[first + 1] <= found) first++;
            int end = first;
            while (end < glyphCount && offsets[end] < cursor) end++;
            if (end <= first || first < previous) continue;
            spans.add(new int[]{first, end});
            previous = end;
        }
        return spans.isEmpty() ? null : spans;
    }

    /** Contiguous glyph ranges covering the whole line, with timed words kept as their own ranges. */
    static List<int[]> ranges(LrcTimeline.Line line, int glyphCount, int[] offsets) {
        List<int[]> spans = spans(line, glyphCount, offsets);
        if (spans == null) return null;
        return cover(spans, glyphCount);
    }

    /** Contiguous glyph ranges covering the whole line, with timed words kept as their own ranges. */
    static List<int[]> ranges(LrcTimeline.Line line, int glyphCount, double[] offsets) {
        List<int[]> spans = spans(line, glyphCount, offsets);
        if (spans == null) return null;
        return cover(spans, glyphCount);
    }

    private static List<int[]> cover(List<int[]> spans, int glyphCount) {
        List<int[]> ranges = new ArrayList<>();
        int previous = 0;
        for (int[] span : spans) {
            if (span[0] > previous) ranges.add(new int[]{previous, span[0]});
            ranges.add(new int[]{span[0], span[1]});
            previous = span[1];
        }
        if (previous < glyphCount) ranges.add(new int[]{previous, glyphCount});
        return ranges;
    }
}
