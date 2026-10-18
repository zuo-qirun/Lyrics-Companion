// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native data adapter for graphemeTiming.ts / temperaProgram.ts, revision 9279251.
package com.zuoqirun.lyricscompanion;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static com.zuoqirun.lyricscompanion.FoliaTemperaGeometryOps.*;

/** Milliseconds are converted once; upstream's fractional grapheme times are never rounded. */
final class FoliaTemperaTimeline {
    static List<Object> lines(List<LrcTimeline.Line> source) {
        List<Object> output = new ArrayList<>();
        for (int index = 0; index < source.size(); index++) {
            LrcTimeline.Line line = source.get(index);
            double start = line.timeMs / 1000d;
            double end = start + (line.durationMs > 0 ? line.durationMs / 1000d
                    : index + 1 < source.size() ? Math.max(.001, (source.get(index + 1).timeMs - line.timeMs) / 1000d) : 5);
            List<Object> words = new ArrayList<>();
            for (LrcTimeline.Word word : line.words) words.add(object("text", word.text,
                    "startTime", word.startMs / 1000d, "endTime", (word.startMs + word.durationMs) / 1000d));
            List<Object> glyphs = timeline(line.text, start, end, words);
            output.add(object("fullText", line.text, "startTime", start, "endTime", end, "words", words,
                    "renderEndTime", renderEnd(start, end, words), "nativeSegments", segments(line, glyphs)));
        }
        return output;
    }

    static double renderEnd(double start, double end, List<Object> words) {
        double raw = Math.max(0, end - start);
        if (raw < .1) return Math.max(end, start + .067);
        boolean fast = raw < .18;
        double enter = fast ? Math.min(.06, Math.max(.045, raw * .45)) : Math.min(.42, Math.max(.22, Math.max(raw, .12) * .34));
        double exit = fast ? Math.min(.04, Math.max(.03, raw * .22)) : Math.min(.32, Math.max(.18, Math.max(raw, .12) * .18));
        double last = words.isEmpty() ? end : number(get(words.get(words.size() - 1), "endTime"));
        double pass = Math.max(last, start) + (fast ? .03 : .06);
        return Math.max(end, Math.max(fast ? start + enter + .01 : pass, Math.max(pass, end - exit)) + exit);
    }

    static List<Object> timeline(String text, double start, double end, List<Object> words) {
        String[] source = FoliaGlyphTimeline.split(text);
        List<Object> output = new ArrayList<>();
        for (int i = 0; i < source.length; i++) output.add(null);
        if (words.isEmpty()) {
            even(source, start, end, -1, output, 0, source.length);
            return output;
        }
        int cursor = 0; double last = start;
        for (int w = 0; w < words.size(); w++) {
            Object word = words.get(w); String[] values = FoliaGlyphTimeline.split(string(get(word, "text")));
            if (values.length == 0) continue;
            int first = -1;
            for (int i = cursor; i <= source.length - values.length && first < 0; i++) {
                boolean matched = true;
                for (int j = 0; j < values.length; j++) if (!source[i + j].equals(values[j])) { matched = false; break; }
                if (matched) first = i;
            }
            if (first < 0) first = cursor;
            int finish = Math.min(first + values.length, source.length);
            double wordStart = number(get(word, "startTime")), wordEnd = number(get(word, "endTime"));
            for (int i = cursor; i < first; i++) output.set(i, object("char", source[i], "startTime", wordStart, "endTime", wordStart));
            double unit = Math.max(0, wordEnd - wordStart) / values.length;
            for (int i = first; i < finish; i++) {
                double glyphStart = wordStart + unit * (i - first), glyphEnd = i - first == values.length - 1 ? wordEnd : wordStart + unit * (i - first + 1);
                output.set(i, object("char", source[i], "startTime", glyphStart, "endTime", glyphEnd, "wordIndex", (double) w));
                last = Math.max(last, glyphEnd);
            }
            cursor = Math.max(cursor, finish);
        }
        for (int i = 0; i < source.length; i++) if (output.get(i) == null) output.set(i, object("char", source[i], "startTime", last, "endTime", last));
        return output;
    }

    private static void even(String[] glyphs, double start, double end, int word, List<Object> out, int first, int count) {
        double unit = Math.max(0, end - start) / Math.max(1, glyphs.length);
        for (int i = 0; i < count; i++) {
            Map<String, Object> glyph = object("char", glyphs[i], "startTime", start + unit * i,
                    "endTime", i == glyphs.length - 1 ? end : start + unit * (i + 1));
            if (word >= 0) glyph.put("wordIndex", (double) word);
            out.set(first + i, glyph);
        }
    }

    /** Groups timed glyphs into the player's real word units when that data exists. */
    static List<Object> segments(LrcTimeline.Line line, List<Object> glyphs) {
        String[] chars = new String[glyphs.size()];
        int[] offsets = new int[glyphs.size() + 1];
        for (int i = 0; i < glyphs.size(); i++) {
            chars[i] = string(get(glyphs.get(i), "char"));
            offsets[i + 1] = offsets[i] + chars[i].length();
        }
        List<int[]> spans = FoliaLyricWords.spans(line, glyphs.size(), offsets);
        if (spans != null) {
            List<Object> output = new ArrayList<>();
            int previous = 0;
            for (int[] span : spans) {
                if (span[0] > previous) output.add(segment(line, glyphs, offsets, previous, span[0], false));
                output.add(segment(line, glyphs, offsets, span[0], span[1], true));
                previous = span[1];
            }
            if (previous < glyphs.size()) output.add(segment(line, glyphs, offsets, previous, glyphs.size(), false));
            return output;
        }
        return segments(line.text, glyphs);
    }

    private static Object segment(LrcTimeline.Line line, List<Object> glyphs, int[] offsets,
                                  int first, int end, boolean word) {
        List<Object> selected = new ArrayList<>();
        for (int i = first; i < end; i++) selected.add(new java.util.LinkedHashMap<>(map(glyphs.get(i))));
        return object("text", line.text.substring(offsets[first], offsets[end]),
                "startOffset", (double) offsets[first], "endOffset", (double) offsets[end],
                "graphemes", selected,
                "startTime", selected.isEmpty() ? 0d : number(get(selected.get(0), "startTime")),
                "endTime", selected.isEmpty() ? 0d : number(get(selected.get(selected.size() - 1), "endTime")),
                "isWordLike", word);
    }

    static List<Object> segments(String text, List<Object> glyphs) {
        List<Object> output = new ArrayList<>();
        int[] offsets = new int[glyphs.size() + 1];
        for (int i = 0; i < glyphs.size(); i++) offsets[i + 1] = offsets[i] + string(get(glyphs.get(i), "char")).length();
        BreakIterator iterator = BreakIterator.getWordInstance(Locale.ROOT);
        iterator.setText(text); int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            List<Object> selected = new ArrayList<>();
            for (int i = 0; i < glyphs.size(); i++) if (offsets[i + 1] > start && offsets[i] < end) selected.add(new java.util.LinkedHashMap<>(map(glyphs.get(i))));
            String part = text.substring(start, end); boolean word = false;
            for (int offset = 0; offset < part.length();) { int cp = part.codePointAt(offset); if (Character.isLetterOrDigit(cp) || Character.getType(cp) == Character.NON_SPACING_MARK) word = true; offset += Character.charCount(cp); }
            if (selected.isEmpty()) continue;
            if (!output.isEmpty() && !word && !part.trim().isEmpty()) {
                Object previous = output.get(output.size() - 1); double tail = number(get(previous, "endTime"));
                for (Object glyph : selected) if (number(get(glyph, "endTime")) <= number(get(glyph, "startTime"))) { put(glyph, "startTime", tail); put(glyph, "endTime", tail); }
                put(previous, "text", string(get(previous, "text")) + part); put(previous, "endOffset", (double) end);
                list(get(previous, "graphemes")).addAll(selected);
                double finish = tail; for (Object glyph : selected) finish = Math.max(finish, number(get(glyph, "endTime"))); put(previous, "endTime", finish);
            } else output.add(object("text", part, "startOffset", (double) start, "endOffset", (double) end,
                    "graphemes", selected, "startTime", get(selected.get(0), "startTime"),
                    "endTime", get(selected.get(selected.size() - 1), "endTime"), "isWordLike", word));
        }
        return output;
    }
}
