// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native translation of lumiere/text/lineWrap.ts at 9279251.
package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Four native line variants, preserving word boundaries, punctuation and actual ink extents. */
final class FoliaLumiereLayout {
    private FoliaLumiereLayout() { }
    interface Measurer { double natural(String text, double pixels); double spacing(double pixels); }
    static final class Glyph {
        String text;
        double scale, advance, jag;
        boolean upright;
        Glyph(String text, double scale, double advance, double jag, boolean upright) {
            this.text = text; this.scale = scale; this.advance = advance; this.jag = jag; this.upright = upright;
        }
    }
    static final class Word {
        final int start, end;
        final boolean blank;
        Word(int start, int end, boolean blank) { this.start = start; this.end = end; this.blank = blank; }
    }
    static final class Variant {
        final double[] points, spots;
        double along, across, inkWidth, inkHeight;
        int lines;
        Variant(int count) { points = new double[count * 2]; spots = new double[count * 2]; }
    }
    private static final class Span {
        int start, end;
        final boolean word, space;
        Span(int start, int end, boolean word, boolean space) { this.start = start; this.end = end; this.word = word; this.space = space; }
    }
    private static final class Piece {
        int start, end;
        final String text;
        final double pixels;
        final boolean space;
        Piece(int start, int end, String text, double pixels, boolean space) {
            this.start = start; this.end = end; this.text = text; this.pixels = pixels; this.space = space;
        }
    }
    private static final class Fragment {
        final Piece piece;
        final double gap, extent;
        Fragment(Piece piece, double gap, double extent) { this.piece = piece; this.gap = gap; this.extent = extent; }
    }
    private static final class Row {
        final List<Fragment> fragments = new ArrayList<>();
        double width;
    }

    static Variant[][] flow(Measurer measurer, Glyph[] glyphs, List<Word> words, double hero,
                            double horizontalLimit, double verticalLimit) {
        List<Span> spans = spans(glyphs, words);
        List<Piece> pieces = pieces(glyphs, spans, hero);
        double[] horizontal = new double[glyphs.length], vertical = new double[glyphs.length];
        for (Span span : spans) {
            double px = hero * glyphs[span.start].scale;
            double width = measurer.natural(text(glyphs, span.start, span.end), px), canvas = 0;
            for (int i = span.start; i < span.end; i++) canvas += glyphs[i].advance;
            for (int i = span.start; i < span.end; i++) horizontal[i] = canvas > 0 ? width * glyphs[i].advance / canvas : width / (span.end - span.start);
        }
        for (int i = 0; i < glyphs.length; i++) vertical[i] = glyphs[i].upright
                ? hero * glyphs[i].scale * (1 + measurer.spacing(1)) : horizontal[i];
        Variant[][] result = new Variant[2][2];
        for (int orient = 0; orient < 2; orient++) {
            double[] advances = orient == 0 ? horizontal : vertical;
            List<Piece> split = split(glyphs, pieces, advances, orient == 0 ? horizontalLimit : verticalLimit, hero);
            for (int wrapped = 0; wrapped < 2; wrapped++) result[orient][wrapped] = variant(measurer, glyphs, split, advances, hero, orient, wrapped + 1);
        }
        return result;
    }

    private static List<Span> spans(Glyph[] glyphs, List<Word> words) {
        List<Span> ranges = new ArrayList<>(), spans = new ArrayList<>();
        int cursor = 0;
        for (Word word : words) {
            int start = Math.max(cursor, Math.min(glyphs.length, word.start)), end = Math.min(glyphs.length, word.end);
            for (int i = cursor; i < start; i++) ranges.add(new Span(i, i + 1, false, false));
            if (end > start) ranges.add(new Span(start, end, !word.blank, false));
            cursor = Math.max(cursor, end);
        }
        for (int i = cursor; i < glyphs.length; i++) ranges.add(new Span(i, i + 1, false, false));
        for (Span range : ranges) {
            int start = range.start, end = range.end;
            List<Span> tail = new ArrayList<>();
            while (start < end && blank(glyphs[start])) spans.add(new Span(start, ++start, false, true));
            while (end > start && blank(glyphs[end - 1])) tail.add(0, new Span(end - 1, end--, false, true));
            if (end > start) spans.add(new Span(start, end, range.word, false));
            spans.addAll(tail);
        }
        return spans;
    }

    private static List<Piece> pieces(Glyph[] glyphs, List<Span> spans, double hero) {
        List<Piece> result = new ArrayList<>();
        Span pending = null;
        for (int i = 0; i < spans.size(); i++) {
            Span span = spans.get(i);
            if (span.space) {
                if (pending != null) result.add(piece(glyphs, pending.start, pending.end, pending.start, hero, false));
                pending = null;
                result.add(piece(glyphs, span.start, span.end, span.start, hero, true));
                continue;
            }
            Piece previous = result.isEmpty() ? null : result.get(result.size() - 1);
            Span next = i + 1 < spans.size() ? spans.get(i + 1) : null;
            if (!span.word) {
                String text = text(glyphs, span.start, span.end);
                if (text.matches("[\\(\\[\\{（【《「『〈〔［“‘]+") && next != null && !next.space) {
                    pending = pending != null ? new Span(pending.start, span.end, false, false) : span;
                    continue;
                }
                if (pending == null && previous != null && !previous.space && previous.end == span.start) {
                    previous.end = span.end;
                    continue;
                }
            }
            int start = pending == null ? span.start : pending.start;
            pending = null;
            result.add(piece(glyphs, start, span.end, span.start, hero, false));
        }
        if (pending != null) result.add(piece(glyphs, pending.start, pending.end, pending.start, hero, false));
        return result;
    }

    private static Piece piece(Glyph[] glyphs, int start, int end, int fontIndex, double hero, boolean space) {
        return new Piece(start, end, text(glyphs, fontIndex, end), hero * glyphs[fontIndex].scale, space);
    }
    private static List<Piece> split(Glyph[] glyphs, List<Piece> pieces, double[] advances, double limit, double hero) {
        List<Piece> result = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.space || extent(piece, advances) <= limit || piece.end - piece.start < 2) { result.add(piece); continue; }
            List<Piece> parts = new ArrayList<>();
            for (int i = piece.start; i < piece.end; i++) {
                Piece previous = parts.isEmpty() ? null : parts.get(parts.size() - 1);
                if (previous != null && !glyphs[i].text.matches(".*[\\p{L}\\p{N}].*")) previous.end = i + 1;
                else parts.add(new Piece(i, i + 1, glyphs[i].text, hero * glyphs[i].scale, false));
            }
            result.addAll(parts);
        }
        return result;
    }
    private static double extent(Piece piece, double[] advances) {
        double total = 0; for (int i = piece.start; i < piece.end; i++) total += advances[i]; return total;
    }
    private static List<Row> rows(Measurer measurer, List<Piece> pieces, double[] advances, double maxWidth) {
        List<Row> rows = new ArrayList<>(); Row row = new Row(); double gap = 0;
        for (Piece piece : pieces) {
            if (piece.space) { gap = Math.max(gap, measurer.natural("a a", piece.pixels) - measurer.natural("aa", piece.pixels)); continue; }
            double width = extent(piece, advances);
            if (!row.fragments.isEmpty() && row.width + gap + width > maxWidth + 1e-7) {
                rows.add(row); row = new Row(); gap = 0;
            }
            if (row.fragments.isEmpty()) gap = 0;
            row.fragments.add(new Fragment(piece, gap, width)); row.width += gap + width; gap = 0;
        }
        if (!row.fragments.isEmpty()) rows.add(row);
        return rows;
    }
    private static Variant variant(Measurer measurer, Glyph[] glyphs, List<Piece> pieces,
                                   double[] advances, double hero, int orient, int count) {
        List<Row> rows = rows(measurer, pieces, advances, Double.POSITIVE_INFINITY);
        if (count > 1 && rows.size() == 1 && rows.get(0).fragments.size() > 1) {
            double widest = 0, widestGap = 0, total = rows.get(0).width;
            for (Piece piece : pieces) {
                if (piece.space) widestGap = Math.max(widestGap, measurer.natural("a a", piece.pixels) - measurer.natural("aa", piece.pixels));
                else widest = Math.max(widest, extent(piece, advances));
            }
            double target = (total + widest) / 2 + widestGap;
            if (rows(measurer, pieces, advances, target).size() > count) target = total / 2 + widest + widestGap;
            rows = rows(measurer, pieces, advances, target);
            while (rows.size() > count) {
                Row extra = rows.remove(count), last = rows.get(count - 1);
                for (int i = 0; i < extra.fragments.size(); i++) {
                    Fragment f = extra.fragments.get(i);
                    Fragment merged = new Fragment(f.piece, i == 0 ? widestGap : f.gap, f.extent);
                    last.fragments.add(merged); last.width += merged.gap + merged.extent;
                }
            }
        }
        if (rows.isEmpty()) rows.add(new Row());
        int[] rowOf = new int[glyphs.length]; Arrays.fill(rowOf, -1);
        double[] offsets = new double[glyphs.length];
        for (int r = 0; r < rows.size(); r++) {
            double cursor = 0;
            for (Fragment fragment : rows.get(r).fragments) {
                Piece unit = fragment.piece; double gapStart = cursor; cursor += fragment.gap;
                for (int i = unit.start - 1; i >= 0 && rowOf[i] == -1 && blank(glyphs[i]); i--) {
                    rowOf[i] = r; offsets[i] = gapStart + fragment.gap / 2;
                }
                double extent = extent(unit, advances), ratio = extent > 0 ? fragment.extent / extent : 0, inner = cursor;
                for (int i = unit.start; i < unit.end; i++) {
                    double advance = advances[i] * ratio;
                    rowOf[i] = r; offsets[i] = inner + advance / 2; inner += advance;
                }
                cursor += fragment.extent;
            }
        }
        for (int i = 1; i < glyphs.length; i++) if (rowOf[i] == -1 && rowOf[i - 1] != -1) {
            rowOf[i] = rowOf[i - 1]; offsets[i] = offsets[i - 1] + advances[i - 1] / 2;
        }
        for (int i = glyphs.length - 1; i >= 0; i--) if (rowOf[i] == -1) {
            rowOf[i] = i + 1 < glyphs.length && rowOf[i + 1] != -1 ? rowOf[i + 1] : 0;
            offsets[i] = i + 1 < glyphs.length ? Math.max(0, offsets[i + 1] - advances[i + 1] / 2) : 0;
        }
        Variant result = new Variant(glyphs.length); result.lines = rows.size();
        result.across = (result.lines - 1) * hero * 1.6 + hero;
        double inkX = 0, inkY = 0;
        for (Row row : rows) result.along = Math.max(result.along, row.width);
        for (int i = 0; i < glyphs.length; i++) {
            Glyph glyph = glyphs[i]; double size = hero * glyph.scale;
            double along = offsets[i] - rows.get(rowOf[i]).width / 2;
            double cross = (rowOf[i] - (result.lines - 1) / 2.0) * hero * 1.6;
            double x = orient == 0 ? along : -cross + glyph.jag * .8;
            double y = orient == 0 ? cross + (1 - glyph.scale) * hero * .32 + glyph.jag : along;
            result.points[i * 2] = x; result.points[i * 2 + 1] = y;
            result.spots[i * 2] = orient == 0 ? along : -cross; result.spots[i * 2 + 1] = orient == 0 ? cross : along;
            if (!blank(glyph)) {
                inkX = Math.max(inkX, Math.abs(x) + (orient == 0 ? advances[i] : size) / 2);
                inkY = Math.max(inkY, Math.abs(y) + (orient == 0 ? size : advances[i]) / 2);
            }
        }
        result.inkWidth = inkX * 2; result.inkHeight = inkY * 2;
        return result;
    }
    static double fit(double along, double budget, double scale) { return Math.min(1, budget / Math.max(scale * along, 1e-6)); }
    static boolean wrap(Variant[] flow, double budget, double scale) { return flow[1].lines > 1 && scale * flow[0].along * .8 > budget; }
    static double clampInto(double center, double extent, double lo, double hi) {
        return extent >= hi - lo ? (lo + hi) / 2 : Math.min(hi - extent / 2, Math.max(lo + extent / 2, center));
    }
    static double[] band(double width, double height) {
        double left = Math.max(30, width * .065) / height + .04, top = Math.max(30, height * .085) / height + .035;
        return new double[]{left, width / height - left, top, Math.max(top + .2, .8)};
    }
    static boolean upright(String text) {
        int code = text.codePointAt(0);
        return code >= 0x1100 && code <= 0x11ff || code >= 0x2e00 && code <= 0x2fff
                || code >= 0x3000 && code <= 0x30ff || code >= 0x3100 && code <= 0x31ff
                || code >= 0x3200 && code <= 0x9fff || code >= 0xac00 && code <= 0xd7af
                || code >= 0xf900 && code <= 0xfaff || code >= 0xfe30 && code <= 0xfe6f
                || code >= 0xff00 && code <= 0xffef || code >= 0x20000 && code <= 0x3134f;
    }
    private static boolean blank(Glyph glyph) { return glyph.text.trim().isEmpty(); }
    private static String text(Glyph[] glyphs, int start, int end) {
        StringBuilder out = new StringBuilder(); for (int i = start; i < end; i++) out.append(glyphs[i].text); return out.toString();
    }
}
