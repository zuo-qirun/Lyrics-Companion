// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native adaptation of sonnetProgram.ts / sonnetSemantic.ts at 92792519ce4b72d540e69f3634aa408810c6e0f6.
// Source attribution and remaining rendering differences: third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Seek-safe paragraph/shot compiler. All timestamps use milliseconds. */
final class FoliaOriginalSonnetProgram {
    static final String[] KINDS = {"editorial-column", "type-impact", "fragment-collage",
            "tracking-ribbon", "mask-reveal", "poster-blocks", "quiet-tableau"};
    static final String[] TRANSITIONS = {"fast-blur", "mono-glitch", "camera-pull"};

    static final class Segment {
        String text;
        final FoliaGlyphTimeline timeline;
        final int first;
        int end;
        final int sourceIndex;
        final boolean word;
        Segment(String text, FoliaGlyphTimeline timeline, int first, int end, int sourceIndex, boolean word) {
            this.text = text; this.timeline = timeline; this.first = first; this.end = end;
            this.sourceIndex = sourceIndex; this.word = word;
        }
        long start() { return first < timeline.starts.length ? timeline.starts[first] : 0; }
        long finish() { return end > first ? timeline.ends[end - 1] : start(); }
        int visibleLength() {
            int count = 0;
            for (int i = first; i < end; i++) if (!timeline.glyphs[i].trim().isEmpty()) count++;
            return count;
        }
        float score() { return Math.min(visibleLength(), 8) * 14
                + Math.min(2.5f, Math.max(0, finish() - start()) / 1000f) * 18; }
    }

    static final class CompiledLine {
        final int sourceIndex;
        final LrcTimeline.Line line;
        final long end;
        final List<Segment> segments;
        CompiledLine(int sourceIndex, LrcTimeline.Line line, long end, List<Segment> segments) {
            this.sourceIndex = sourceIndex; this.line = line; this.end = end; this.segments = segments;
        }
    }

    static final class Shot {
        String id, kind;
        int paragraphIndex, shotIndex;
        long start, end;
        float cameraX, cameraY, zoom, rotation;
        final List<CompiledLine> lines = new ArrayList<>();
        final List<Segment> segments = new ArrayList<>();
    }

    static final class Paragraph {
        String id, kind, transition;
        long start, end, transitionStart, transitionEnd;
        final List<CompiledLine> lines = new ArrayList<>();
        final List<Shot> shots = new ArrayList<>();
    }

    final String seed;
    final long gapThreshold;
    final List<Paragraph> paragraphs = new ArrayList<>();
    final List<Shot> shots = new ArrayList<>();

    FoliaOriginalSonnetProgram(List<LrcTimeline.Line> lines, String seed) {
        this.seed = seed;
        List<CompiledLine> compiled = new ArrayList<>();
        List<Long> gaps = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LrcTimeline.Line line = lines.get(i);
            long next = i + 1 < lines.size() ? lines.get(i + 1).timeMs : Long.MAX_VALUE;
            long duration = line.durationMs > 0 ? line.durationMs
                    : next < Long.MAX_VALUE ? Math.max(1, next - line.timeMs) : 5000;
            long end = Math.max(line.timeMs, Math.min(line.timeMs + duration, next));
            compiled.add(new CompiledLine(i, line, end, segments(line, next, i)));
            if (i > 0) {
                long gap = line.timeMs - compiled.get(i - 1).end;
                if (gap > 0) gaps.add(gap);
            }
        }
        Collections.sort(gaps);
        double median = gaps.isEmpty() ? 500 : gaps.size() % 2 == 1 ? gaps.get(gaps.size() / 2)
                : (gaps.get(gaps.size() / 2 - 1) + gaps.get(gaps.size() / 2)) / 2.0;
        gapThreshold = Math.round(Math.min(3500, Math.max(1250, median * 2.5)));
        List<CompiledLine> draft = new ArrayList<>();
        for (CompiledLine line : compiled) {
            if (!draft.isEmpty() && line.line.timeMs - draft.get(draft.size() - 1).end >= gapThreshold) {
                splitDraft(draft); draft.clear();
            }
            draft.add(line);
        }
        if (!draft.isEmpty()) splitDraft(draft);
        String previousKind = null, previousTransition = null;
        for (int p = 0; p < paragraphs.size(); p++) {
            Paragraph paragraph = paragraphs.get(p);
            paragraph.id = "sonnet-p" + p;
            paragraph.start = paragraph.lines.get(0).line.timeMs;
            paragraph.end = paragraph.lines.get(paragraph.lines.size() - 1).end;
            paragraph.kind = classify(paragraph, p == paragraphs.size() - 1);
            for (CompiledLine line : paragraph.lines) {
                Shot shot = paragraph.shots.isEmpty() ? null : paragraph.shots.get(paragraph.shots.size() - 1);
                if (shot == null || shot.lines.size() >= 4 || line.end - shot.start > 6000) {
                    shot = new Shot(); shot.start = line.line.timeMs; shot.paragraphIndex = p;
                    shot.shotIndex = paragraph.shots.size(); shot.id = "p" + p + "-s" + shot.shotIndex;
                    paragraph.shots.add(shot); shots.add(shot);
                }
                shot.lines.add(line); shot.end = line.end;
                for (Segment segment : line.segments) if (!segment.text.trim().isEmpty()) shot.segments.add(segment);
            }
            for (Shot shot : paragraph.shots) {
                StringBuilder signature = new StringBuilder();
                for (CompiledLine line : shot.lines) {
                    if (signature.length() > 0) signature.append('|');
                    signature.append(line.line.text);
                }
                String prefix = seed + ":" + p + ":" + shot.shotIndex;
                shot.kind = choose(KINDS, prefix + ":" + signature, previousKind);
                int wordCount = 0;
                for (Segment segment : shot.segments) if (segment.word) wordCount++;
                if ("breath".equals(paragraph.kind) && shot.shotIndex == 0 && wordCount <= 2) shot.kind = "quiet-tableau";
                previousKind = shot.kind;
                long random = hash(prefix + ":camera");
                shot.cameraX = ((random & 255) / 255f - 0.5f) * 0.18f;
                shot.cameraY = ((random >>> 8 & 255) / 255f - 0.5f) * 0.14f;
                float zoomBase = "poster-blocks".equals(shot.kind) ? 1.02f : "quiet-tableau".equals(shot.kind) ? 1.12f : 1.22f;
                float zoomSpan = "poster-blocks".equals(shot.kind) ? 0.16f : "quiet-tableau".equals(shot.kind) ? 0.2f : 0.26f;
                shot.zoom = zoomBase + (random >>> 16 & 255) / 255f * zoomSpan;
                shot.rotation = ((random >>> 24 & 255) / 255f - 0.5f) * 0.08f;
            }
            if (p + 1 < paragraphs.size()) {
                long next = paragraphs.get(p + 1).lines.get(0).line.timeMs;
                long gap = next - paragraph.end;
                long duration = Math.min(300, Math.max(160, gap > 0 ? gap / 2 : 200));
                paragraph.transition = choose(TRANSITIONS, seed + ":" + p + ":transition", previousTransition);
                previousTransition = paragraph.transition;
                paragraph.transitionStart = Math.max(paragraph.start, next - duration);
                paragraph.transitionEnd = next;
            }
        }
    }

    Shot shotAt(long time) {
        for (int i = shots.size() - 1; i >= 0; i--) if (time >= shots.get(i).start) return shots.get(i);
        return shots.isEmpty() ? null : shots.get(0);
    }

    private void splitDraft(List<CompiledLine> source) {
        int first = 0;
        while (first < source.size()) {
            int count = source.size() - first;
            int split = source.size();
            if (count > 6 || count > 1 && source.get(source.size() - 1).end - source.get(first).line.timeMs > 18000) {
                long largestGap = Long.MIN_VALUE;
                split = first + Math.min(4, count - 1);
                for (int i = first + 2; i < source.size() - 1; i++) {
                    long gap = source.get(i).line.timeMs - source.get(i - 1).end;
                    if (gap > largestGap) { largestGap = gap; split = i; }
                }
            }
            Paragraph paragraph = new Paragraph();
            paragraph.lines.addAll(source.subList(first, Math.max(first + 1, split)));
            paragraphs.add(paragraph); first = Math.max(first + 1, split);
        }
    }

    private static String classify(Paragraph paragraph, boolean last) {
        if (last) return "outro";
        long duration = paragraph.end - paragraph.start;
        int words = 0, punctuation = 0;
        for (CompiledLine line : paragraph.lines) {
            for (Segment segment : line.segments) if (segment.word) words++;
            for (int i = 0; i < line.line.text.length(); i++) if ("!?！？…".indexOf(line.line.text.charAt(i)) >= 0) punctuation++;
        }
        if (duration <= 3500 || words <= 3) return "breath";
        return punctuation >= 2 || words / Math.max(1, duration / 1000f) > 2.5f ? "lift" : "verse";
    }

    static List<Segment> segments(LrcTimeline.Line line, long next, int sourceIndex) {
        List<Segment> output = new ArrayList<>();
        FoliaGlyphTimeline timeline = new FoliaGlyphTimeline(line, next < Long.MAX_VALUE ? next : -1);
        List<int[]> spans = FoliaLyricWords.spans(line, timeline.glyphs.length, timeline.offsets);
        if (spans != null) {
            int previous = 0;
            for (int[] span : spans) {
                if (span[0] > previous) {
                    output.add(new Segment(line.text.substring(timeline.offsets[previous], timeline.offsets[span[0]]),
                            timeline, previous, span[0], sourceIndex, false));
                }
                output.add(new Segment(line.text.substring(timeline.offsets[span[0]], timeline.offsets[span[1]]),
                        timeline, span[0], span[1], sourceIndex, true));
                previous = span[1];
            }
            if (previous < timeline.glyphs.length) {
                output.add(new Segment(line.text.substring(timeline.offsets[previous]),
                        timeline, previous, timeline.glyphs.length, sourceIndex, false));
            }
            return output;
        }
        BreakIterator iterator = BreakIterator.getWordInstance(Locale.SIMPLIFIED_CHINESE);
        iterator.setText(line.text);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            int first = 0, finish = 0;
            while (first < timeline.glyphs.length && timeline.offsets[first + 1] <= start) first++;
            while (finish < timeline.glyphs.length && timeline.offsets[finish] < end) finish++;
            if (finish <= first) continue;
            String part = line.text.substring(start, end);
            boolean word = false;
            for (int i = 0; i < part.length();) {
                int cp = part.codePointAt(i);
                if (Character.isLetterOrDigit(cp)) word = true;
                i += Character.charCount(cp);
            }
            if (!output.isEmpty() && !word && !part.trim().isEmpty()) {
                Segment previous = output.get(output.size() - 1);
                previous.text += part; previous.end = finish;
            } else output.add(new Segment(part, timeline, first, finish, sourceIndex, word));
        }
        return output;
    }

    static int hero(List<Segment> segments) {
        int best = 0; float score = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if (segment.word && segment.visibleLength() > 0 && segment.score() > score) {
                best = i; score = segment.score();
            }
        }
        return best;
    }

    static List<Integer> semiHeroes(List<Segment> segments, int hero) {
        List<Integer> picks = new ArrayList<>();
        int count = 0;
        for (Segment segment : segments) if (segment.word && segment.visibleLength() > 0) count++;
        if (count < 4) return picks;
        boolean early = hero <= (segments.size() - 1) / 2f;
        int primary = bestSemi(segments, hero, early, -1);
        if (primary < 0) primary = bestSemi(segments, hero, !early, -1);
        if (primary >= 0) picks.add(primary);
        if (count >= 9 && primary >= 0) {
            int secondary = bestSemi(segments, hero, !early, primary);
            if (secondary >= 0) picks.add(secondary);
        }
        Collections.sort(picks); return picks;
    }

    private static int bestSemi(List<Segment> segments, int hero, boolean after, int other) {
        int best = -1; float bestScore = -1;
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            if ((i > hero) != after || !segment.word || segment.visibleLength() < 2 || Math.abs(i - hero) < 2
                    || other >= 0 && Math.abs(i - other) < 2 || segment.score() < segments.get(hero).score() * 0.35f) continue;
            if (segment.score() > bestScore) { best = i; bestScore = segment.score(); }
        }
        return best;
    }

    static long hash(String value) {
        int hash = 0x811c9dc5;
        for (int i = 0; i < value.length(); i++) { hash ^= value.charAt(i); hash *= 16777619; }
        return hash & 0xffffffffL;
    }

    private static String choose(String[] choices, String seed, String previous) {
        int first = (int) (hash(seed) % choices.length);
        for (int i = 0; i < choices.length; i++) {
            String value = choices[(first + i) % choices.length];
            if (!value.equals(previous)) return value;
        }
        return choices[first];
    }
}
