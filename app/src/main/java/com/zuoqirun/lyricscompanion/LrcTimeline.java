package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses NetEase LRC/YRC payloads and resolves the visible lyric at a playback position. */
final class LrcTimeline {
    private static final long PLAIN_LINE_HOLD_MS = 5_000L;
    private static final long MIN_INTERLUDE_MS = 5_000L;
    private static final Pattern TIME_TAG = Pattern.compile(
            "\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern YRC_LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),\\d+\\)");
    static final LrcTimeline EMPTY = new LrcTimeline(Collections.emptyList());

    private final List<Line> lines;

    private LrcTimeline(List<Line> lines) {
        this.lines = lines;
    }

    static LrcTimeline parse(String original, String translated) {
        return parse(original, translated, "");
    }

    static LrcTimeline parse(String original, String translated, String wordByWord) {
        TreeMap<Long, String> originals = parseTimedLines(original);
        TreeMap<Long, String> translations = parseTimedLines(translated);
        List<Line> enhanced = parseYrcLines(wordByWord, originals, translations);
        if (!enhanced.isEmpty()) {
            return new LrcTimeline(Collections.unmodifiableList(enhanced));
        }
        if (originals.isEmpty()) {
            return EMPTY;
        }
        List<Line> result = new ArrayList<>(originals.size());
        for (Map.Entry<Long, String> entry : originals.entrySet()) {
            result.add(new Line(entry.getKey(), 0L, entry.getValue(),
                    closestTranslation(translations, entry.getKey()), Collections.emptyList()));
        }
        return new LrcTimeline(Collections.unmodifiableList(result));
    }

    At at(long positionMs) {
        if (lines.isEmpty()) {
            return At.EMPTY;
        }
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        int currentIndex = low - 1;
        Line current = currentIndex >= 0 ? lines.get(currentIndex) : null;
        Line previous = currentIndex > 0 ? lines.get(currentIndex - 1) : null;
        Line next = low < lines.size() ? lines.get(low) : null;
        if (current == null && next != null && next.timeMs >= MIN_INTERLUDE_MS) {
            long duration = next.timeMs;
            return new At("", "", "", next.text, true, false, "", "",
                    0L, duration, -1L, 0L, 0,
                    buildInterludeNearby(-1, 0L, duration));
        }
        if (current != null && next != null) {
            long visibleDurationMs = current.durationMs > 0L
                    ? current.durationMs : PLAIN_LINE_HOLD_MS;
            long currentEndMs = current.timeMs + visibleDurationMs;
            long gapDurationMs = next.timeMs - currentEndMs;
            if (gapDurationMs >= MIN_INTERLUDE_MS && positionMs >= currentEndMs) {
                return new At(current.text, "", "", next.text, true, false, "", "",
                        currentEndMs, gapDurationMs, -1L, 0L, 0,
                        buildInterludeNearby(currentIndex, currentEndMs, gapDurationMs));
            }
        }
        long lineStartMs = current == null ? -1L : current.timeMs;
        long lineDurationMs = current == null ? 0L
                : Math.max(1_000L, current.durationMs > 0L ? current.durationMs
                : next == null ? 5_000L : next.timeMs - current.timeMs);
        String completedText = "";
        String currentWord = "";
        long wordStartMs = -1L;
        long wordDurationMs = 0L;
        int wordProgressPermille = 0;
        if (current != null && !current.words.isEmpty()) {
            StringBuilder completed = new StringBuilder();
            for (Word word : current.words) {
                if (positionMs < word.startMs) {
                    break;
                }
                if (word.durationMs <= 0L || positionMs >= word.startMs + word.durationMs) {
                    completed.append(word.text);
                    continue;
                }
                currentWord = word.text;
                wordStartMs = word.startMs;
                wordDurationMs = word.durationMs;
                wordProgressPermille = (int) Math.max(0L, Math.min(1000L,
                        (positionMs - word.startMs) * 1000L / word.durationMs));
                break;
            }
            completedText = completed.toString();
        }
        boolean wordTimed = current != null && !current.words.isEmpty();
        return new At(previous == null ? "" : previous.text,
                current == null ? "" : current.text,
                current == null ? "" : current.translated,
                next == null ? "" : next.text, false, wordTimed, completedText, currentWord,
                lineStartMs, lineDurationMs, wordStartMs, wordDurationMs,
                wordProgressPermille, buildNearby(currentIndex));
    }

    boolean isEmpty() {
        return lines.isEmpty();
    }

    boolean containsLyricText(String value) {
        String normalized = normalizeLyricText(value);
        if (normalized.isEmpty()) return false;
        for (Line line : lines) {
            if (normalized.equals(normalizeLyricText(line.text))
                    || normalized.equals(normalizeLyricText(line.translated))) return true;
        }
        return false;
    }

    static At liveLine(String text) {
        return new At("", text == null ? "" : text.trim(), "", "", false, false,
                "", "", -1L, 0L, -1L, 0L, 0, Collections.emptyList());
    }

    long shiftedPosition(long positionMs, int direction) {
        if (lines.isEmpty() || direction == 0) return Math.max(0L, positionMs);
        int low = 0;
        int high = lines.size();
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= positionMs) low = mid + 1;
            else high = mid;
        }
        int current = low - 1;
        int target = direction > 0 ? Math.min(lines.size() - 1, current + 1)
                : Math.max(0, current - 1);
        return lines.get(target).timeMs;
    }

    static int revealedCodePointCount(String value, int progressPermille) {
        if (value == null || value.isEmpty() || progressPermille <= 0) return 0;
        int codePointCount = value.codePointCount(0, value.length());
        return Math.min(codePointCount,
                (codePointCount * Math.min(1000, progressPermille) + 999) / 1000);
    }

    private List<NearbyLine> buildNearby(int currentIndex) {
        if (currentIndex < 0 || lines.isEmpty()) return Collections.emptyList();
        List<NearbyLine> result = new ArrayList<>();
        int start = Math.max(0, currentIndex - 3);
        int end = Math.min(lines.size() - 1, currentIndex + 3);
        for (int index = start; index <= end; index++) {
            Line line = lines.get(index);
            result.add(new NearbyLine(line.text, line.translated, index - currentIndex,
                    line.timeMs, line.durationMs, false));
        }
        return Collections.unmodifiableList(result);
    }

    private List<NearbyLine> buildInterludeNearby(int previousIndex, long startMs,
                                                   long durationMs) {
        List<NearbyLine> result = new ArrayList<>();
        int start = Math.max(0, previousIndex - 2);
        for (int index = start; index <= previousIndex; index++) {
            Line line = lines.get(index);
            result.add(new NearbyLine(line.text, line.translated,
                    index - previousIndex - 1, line.timeMs, line.durationMs, false));
        }
        result.add(new NearbyLine("", "", 0, startMs, durationMs, true));
        int end = Math.min(lines.size() - 1, previousIndex + 3);
        for (int index = previousIndex + 1; index <= end; index++) {
            Line line = lines.get(index);
            result.add(new NearbyLine(line.text, line.translated,
                    index - previousIndex, line.timeMs, line.durationMs, false));
        }
        return Collections.unmodifiableList(result);
    }

    private static TreeMap<Long, String> parseTimedLines(String value) {
        TreeMap<Long, String> result = new TreeMap<>();
        if (value == null || value.isEmpty()) return result;
        for (String rawLine : value.split("\\r?\\n")) {
            Matcher matcher = TIME_TAG.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            int textStart = -1;
            while (matcher.find()) {
                timestamps.add(toMilliseconds(matcher.group(1), matcher.group(2), matcher.group(3)));
                textStart = matcher.end();
            }
            if (timestamps.isEmpty() || textStart < 0) continue;
            String text = rawLine.substring(textStart).trim();
            if (text.isEmpty()) continue;
            for (Long timestamp : timestamps) result.put(timestamp, text);
        }
        return result;
    }

    private static List<Line> parseYrcLines(String value, TreeMap<Long, String> originalLines,
                                            TreeMap<Long, String> translations) {
        List<Line> result = new ArrayList<>();
        if (value == null || value.isEmpty()) return result;
        for (String rawLine : value.split("\\r?\\n")) {
            Matcher lineMatcher = YRC_LINE.matcher(rawLine);
            if (!lineMatcher.matches()) continue;
            long lineStart = Long.parseLong(lineMatcher.group(1));
            String content = lineMatcher.group(3);
            Matcher wordMatcher = YRC_WORD.matcher(content);
            List<Word> words = new ArrayList<>();
            long previousStart = -1L;
            long previousDuration = 0L;
            int textStart = -1;
            while (wordMatcher.find()) {
                if (previousStart >= 0L && textStart >= 0) {
                    words.add(new Word(previousStart, previousDuration,
                            content.substring(textStart, wordMatcher.start())));
                }
                previousStart = Long.parseLong(wordMatcher.group(1));
                previousDuration = Long.parseLong(wordMatcher.group(2));
                textStart = wordMatcher.end();
            }
            if (previousStart >= 0L && textStart >= 0) {
                words.add(new Word(previousStart, previousDuration, content.substring(textStart)));
            }
            StringBuilder text = new StringBuilder();
            for (Word word : words) text.append(word.text);
            String lineText = text.toString().trim();
            if (!lineText.isEmpty()) {
                result.add(new Line(lineStart, Long.parseLong(lineMatcher.group(2)), lineText,
                        enhancedTranslation(originalLines, translations, lineStart, lineText),
                        Collections.unmodifiableList(words)));
            }
        }
        return result;
    }

    private static long toMilliseconds(String minutes, String seconds, String fraction) {
        long result = Long.parseLong(minutes) * 60_000L + Long.parseLong(seconds) * 1_000L;
        if (fraction == null || fraction.isEmpty()) return result;
        long value = Long.parseLong(fraction);
        if (fraction.length() == 1) value *= 100L;
        else if (fraction.length() == 2) value *= 10L;
        else if (fraction.length() > 3) value /= (long) Math.pow(10, fraction.length() - 3);
        return result + value;
    }

    private static String closestTranslation(TreeMap<Long, String> translations, long timestamp) {
        return closestTranslation(translations, timestamp, 500L);
    }

    private static String enhancedTranslation(TreeMap<Long, String> originalLines,
                                              TreeMap<Long, String> translations,
                                              long lineStart, String lineText) {
        String normalized = normalizeLyricText(lineText);
        Map.Entry<Long, String> matchingOriginal = null;
        long matchingDistance = Long.MAX_VALUE;
        if (!normalized.isEmpty()) {
            for (Map.Entry<Long, String> original : originalLines.entrySet()) {
                if (!normalized.equals(normalizeLyricText(original.getValue()))) continue;
                long distance = Math.abs(original.getKey() - lineStart);
                if (distance < matchingDistance) {
                    matchingOriginal = original;
                    matchingDistance = distance;
                }
            }
        }
        if (matchingOriginal != null && matchingDistance <= 5_000L) {
            return closestTranslation(translations, matchingOriginal.getKey(), 500L);
        }
        return closestTranslation(translations, lineStart, 2_500L);
    }

    private static String normalizeLyricText(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{P}\\s]+", "");
    }

    private static String closestTranslation(TreeMap<Long, String> translations, long timestamp,
                                             long maxDistanceMs) {
        if (translations.isEmpty()) return "";
        Map.Entry<Long, String> floor = translations.floorEntry(timestamp);
        Map.Entry<Long, String> ceil = translations.ceilingEntry(timestamp);
        Map.Entry<Long, String> best = floor;
        if (best == null || ceil != null
                && Math.abs(ceil.getKey() - timestamp) < Math.abs(best.getKey() - timestamp)) {
            best = ceil;
        }
        return best != null && Math.abs(best.getKey() - timestamp) <= maxDistanceMs
                ? best.getValue() : "";
    }

    private static final class Line {
        final long timeMs;
        final long durationMs;
        final String text;
        final String translated;
        final List<Word> words;

        Line(long timeMs, long durationMs, String text, String translated, List<Word> words) {
            this.timeMs = timeMs;
            this.durationMs = durationMs;
            this.text = text;
            this.translated = translated;
            this.words = words;
        }
    }

    private static final class Word {
        final long startMs;
        final long durationMs;
        final String text;

        Word(long startMs, long durationMs, String text) {
            this.startMs = startMs;
            this.durationMs = durationMs;
            this.text = text;
        }
    }

    static final class At {
        static final At EMPTY = new At("", "", "", "", false, false, "", "",
                -1L, 0L, -1L, 0L, 0, Collections.emptyList());
        final String previousLyric;
        final String lyric;
        final String translatedLyric;
        final String nextLyric;
        final boolean interlude;
        final boolean wordTimed;
        final String completedLyric;
        final String currentWord;
        final long lineStartMs;
        final long lineDurationMs;
        final long wordStartMs;
        final long wordDurationMs;
        final int wordProgressPermille;
        final List<NearbyLine> nearbyLines;

        At(String previousLyric, String lyric, String translatedLyric, String nextLyric,
           boolean interlude, boolean wordTimed, String completedLyric, String currentWord,
           long lineStartMs,
           long lineDurationMs, long wordStartMs, long wordDurationMs,
           int wordProgressPermille, List<NearbyLine> nearbyLines) {
            this.previousLyric = previousLyric;
            this.lyric = lyric;
            this.translatedLyric = translatedLyric;
            this.nextLyric = nextLyric;
            this.interlude = interlude;
            this.wordTimed = wordTimed;
            this.completedLyric = completedLyric;
            this.currentWord = currentWord;
            this.lineStartMs = lineStartMs;
            this.lineDurationMs = lineDurationMs;
            this.wordStartMs = wordStartMs;
            this.wordDurationMs = wordDurationMs;
            this.wordProgressPermille = wordProgressPermille;
            this.nearbyLines = nearbyLines == null ? Collections.emptyList() : nearbyLines;
        }
    }

    static final class NearbyLine {
        final String text;
        final String translated;
        final int offset;
        final long timeMs;
        final long durationMs;
        final boolean interlude;

        NearbyLine(String text, String translated, int offset, long timeMs,
                   long durationMs, boolean interlude) {
            this.text = text == null ? "" : text;
            this.translated = translated == null ? "" : translated;
            this.offset = offset;
            this.timeMs = timeMs;
            this.durationMs = durationMs;
            this.interlude = interlude;
        }
    }
}
