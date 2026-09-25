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
    private static final Pattern OFFSET_TAG = Pattern.compile(
            "(?im)^\\s*\\[offset:\\s*([+-]?\\d+)\\s*]\\s*$");
    static final LrcTimeline EMPTY = new LrcTimeline(Collections.emptyList());

    /** Lossless cache format: includes translations and every word's timing. */
    byte[] toCacheBytes() throws java.io.IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream out = new java.io.DataOutputStream(bytes);
        out.writeInt(2);
        out.writeInt(lines.size());
        for (Line line : lines) {
            out.writeLong(line.timeMs); out.writeLong(line.durationMs);
            out.writeUTF(line.text);
            out.writeInt(line.extendedLyrics.size());
            for (ExtendedLyric extended : line.extendedLyrics) {
                out.writeUTF(extended.kind);
                out.writeUTF(extended.text);
            }
            out.writeInt(line.words.size());
            for (Word word : line.words) {
                out.writeLong(word.startMs); out.writeLong(word.durationMs); out.writeUTF(word.text);
            }
        }
        out.flush();
        return bytes.toByteArray();
    }

    static LrcTimeline fromCacheBytes(byte[] bytes) throws java.io.IOException {
        if (bytes.length > 12_000_000) throw new java.io.IOException("Cache too large");
        java.io.DataInputStream in = new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes));
        int version = in.readInt();
        if (version != 1 && version != 2) throw new java.io.IOException("Unknown cache version");
        int count = in.readInt();
        if (count < 1 || count > 20_000) throw new java.io.IOException("Invalid line count");
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long time = in.readLong(), duration = in.readLong();
            String text = in.readUTF();
            List<ExtendedLyric> extended = new ArrayList<>();
            if (version == 1) {
                String translation = in.readUTF();
                if (!translation.isEmpty()) extended.add(new ExtendedLyric("translation", translation));
            } else {
                int extensionCount = in.readInt();
                if (extensionCount < 0 || extensionCount > 16) {
                    throw new java.io.IOException("Invalid extension count");
                }
                for (int e = 0; e < extensionCount; e++) {
                    extended.add(new ExtendedLyric(in.readUTF(), in.readUTF()));
                }
            }
            int wordCount = in.readInt();
            if (time < 0 || duration < 0 || wordCount < 0 || wordCount > 20_000)
                throw new java.io.IOException("Invalid cached line");
            List<Word> words = new ArrayList<>();
            for (int w = 0; w < wordCount; w++) {
                long start = in.readLong(), length = in.readLong();
                if (start < 0 || length < 0) throw new java.io.IOException("Invalid cached word");
                words.add(new Word(start, length, in.readUTF()));
            }
            result.add(new Line(time, duration, text, extended,
                    Collections.unmodifiableList(words)));
        }
        if (in.available() != 0) throw new java.io.IOException("Trailing cache data");
        return fromTimedLines(result);
    }

    private final List<Line> lines;

    private LrcTimeline(List<Line> lines) {
        this.lines = lines;
    }

    static LrcTimeline fromTimedLines(List<Line> input) {
        if (input.isEmpty()) return EMPTY;
        TreeMap<Long, Line> ordered = new TreeMap<>();
        for (Line line : input) ordered.put(line.timeMs, line);
        return new LrcTimeline(Collections.unmodifiableList(new ArrayList<>(ordered.values())));
    }

    static LrcTimeline parse(String original, String translated) {
        return parse(original, translated, "");
    }

    static LrcTimeline parse(String original, String translated, String wordByWord) {
        return parse(original, translated, wordByWord, "");
    }

    static LrcTimeline parse(String original, String translated, String wordByWord,
                             String romanized) {
        long offsetMs = offsetOf(wordByWord, original, translated, romanized);
        TreeMap<Long, String> originals = parseTimedLines(original, offsetMs);
        TreeMap<Long, String> translations = parseTimedLines(translated, offsetMs);
        TreeMap<Long, String> romaji = parseTimedLines(romanized, offsetMs);
        List<Line> enhanced = parseYrcLines(wordByWord, originals, translations, romaji,
                offsetMs);
        if (!enhanced.isEmpty()) {
            return new LrcTimeline(Collections.unmodifiableList(enhanced));
        }
        if (originals.isEmpty()) {
            return EMPTY;
        }
        List<Line> result = new ArrayList<>(originals.size());
        for (Map.Entry<Long, String> entry : originals.entrySet()) {
            result.add(new Line(entry.getKey(), 0L, entry.getValue(),
                    extensions(translations, romaji, entry.getKey()), Collections.emptyList()));
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
            return new At("", "", "", next.text, true, false, "", "", false,
                    0L, duration, -1L, 0L, 0,
                    buildInterludeNearby(-1, 0L, duration));
        }
        if (current != null && next != null) {
            long visibleDurationMs = current.durationMs > 0L
                    ? current.durationMs : PLAIN_LINE_HOLD_MS;
            long currentEndMs = current.timeMs + visibleDurationMs;
            long gapDurationMs = next.timeMs - currentEndMs;
            if (gapDurationMs >= MIN_INTERLUDE_MS && positionMs >= currentEndMs) {
                return new At(current.text, "", "", next.text, true, false, "", "", false,
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
        boolean trailingWord = false;
        if (current != null && !current.words.isEmpty()) {
            StringBuilder completed = new StringBuilder();
            for (int wordIndex = 0; wordIndex < current.words.size(); wordIndex++) {
                Word word = current.words.get(wordIndex);
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
                trailingWord = isTrailingWord(current.words, wordIndex);
                break;
            }
            completedText = completed.toString();
        }
        boolean wordTimed = current != null && !current.words.isEmpty();
        return new At(previous == null ? "" : previous.text,
                current == null ? "" : current.text,
                current == null ? "" : current.translated,
                next == null ? "" : next.text, false, wordTimed, completedText, currentWord,
                trailingWord,
                lineStartMs, lineDurationMs, wordStartMs, wordDurationMs,
                wordProgressPermille, buildNearby(currentIndex));
    }

    /** Matches Refined Now Playing's rule: a sustained final word before punctuation or line end. */
    private static boolean isTrailingWord(List<Word> words, int wordIndex) {
        Word word = words.get(wordIndex);
        if (word.durationMs < 1_000L || isOnlyPunctuation(word.text)) return false;
        for (int index = wordIndex + 1; index < words.size(); index++) {
            String value = words.get(index).text;
            if (value == null || value.trim().isEmpty()) continue;
            return isOnlyPunctuation(value);
        }
        return true;
    }

    private static boolean isOnlyPunctuation(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) continue;
            int type = Character.getType(codePoint);
            boolean punctuation = type == Character.CONNECTOR_PUNCTUATION
                    || type == Character.DASH_PUNCTUATION
                    || type == Character.START_PUNCTUATION
                    || type == Character.END_PUNCTUATION
                    || type == Character.INITIAL_QUOTE_PUNCTUATION
                    || type == Character.FINAL_QUOTE_PUNCTUATION
                    || type == Character.OTHER_PUNCTUATION
                    || type == Character.MATH_SYMBOL
                    || type == Character.CURRENCY_SYMBOL
                    || type == Character.MODIFIER_SYMBOL
                    || type == Character.OTHER_SYMBOL;
            if (!punctuation) return false;
        }
        return true;
    }

    boolean isEmpty() {
        return lines.isEmpty();
    }

    int lineCount() {
        return lines.size();
    }

    int qualityScore() {
        int score = 0;
        for (Line line : lines) {
            if (!line.words.isEmpty()) score += 4;
            if (!line.translated.isEmpty()) score += 2;
            if (!line.romaji.isEmpty()) score += 2;
        }
        return score;
    }

    boolean hasWordTiming() {
        for (Line line : lines) if (!line.words.isEmpty()) return true;
        return false;
    }

    boolean hasRomaji() {
        for (Line line : lines) if (!line.romaji.isEmpty()) return true;
        return false;
    }

    boolean hasTranslation() {
        for (Line line : lines) if (!line.translated.isEmpty()) return true;
        return false;
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
                "", "", false, -1L, 0L, -1L, 0L, 0, Collections.emptyList());
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
            result.add(new NearbyLine(line.text, line.translated, line.romaji,
                    index - currentIndex,
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
            result.add(new NearbyLine(line.text, line.translated, line.romaji,
                    index - previousIndex - 1, line.timeMs, line.durationMs, false));
        }
        result.add(new NearbyLine("", "", 0, startMs, durationMs, true));
        int end = Math.min(lines.size() - 1, previousIndex + 3);
        for (int index = previousIndex + 1; index <= end; index++) {
            Line line = lines.get(index);
            result.add(new NearbyLine(line.text, line.translated, line.romaji,
                    index - previousIndex, line.timeMs, line.durationMs, false));
        }
        return Collections.unmodifiableList(result);
    }

    private static long offsetOf(String... inputs) {
        for (String input : inputs) {
            if (input == null) continue;
            Matcher matcher = OFFSET_TAG.matcher(input);
            if (matcher.find()) {
                try { return Long.parseLong(matcher.group(1)); }
                catch (NumberFormatException ignored) { return 0L; }
            }
        }
        return 0L;
    }

    private static TreeMap<Long, String> parseTimedLines(String value, long offsetMs) {
        TreeMap<Long, String> result = new TreeMap<>();
        if (value == null || value.isEmpty()) return result;
        for (String rawLine : value.split("\\r?\\n")) {
            Matcher matcher = TIME_TAG.matcher(rawLine);
            List<Long> timestamps = new ArrayList<>();
            int textStart = -1;
            while (matcher.find()) {
                timestamps.add(Math.max(0L, toMilliseconds(matcher.group(1), matcher.group(2),
                        matcher.group(3)) + offsetMs));
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
                                            TreeMap<Long, String> translations,
                                            TreeMap<Long, String> romaji, long offsetMs) {
        List<Line> result = new ArrayList<>();
        if (value == null || value.isEmpty()) return result;
        for (String rawLine : value.split("\\r?\\n")) {
            Matcher lineMatcher = YRC_LINE.matcher(rawLine);
            if (!lineMatcher.matches()) continue;
            long lineStart = Math.max(0L, Long.parseLong(lineMatcher.group(1)) + offsetMs);
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
                previousStart = Math.max(0L, Long.parseLong(wordMatcher.group(1)) + offsetMs);
                previousDuration = Long.parseLong(wordMatcher.group(2));
                textStart = wordMatcher.end();
            }
            if (previousStart >= 0L && textStart >= 0) {
                words.add(new Word(previousStart, previousDuration, content.substring(textStart)));
            }
            // Keep the word list and the displayed line on the same UTF-16 coordinate system.
            // Some providers include untimed padding inside the first/last YRC word. Trimming
            // only the concatenated line shifts every subsequent karaoke boundary.
            trimTimedWordEdges(words);
            StringBuilder text = new StringBuilder();
            for (Word word : words) text.append(word.text);
            String lineText = text.toString();
            if (!lineText.isEmpty()) {
                List<ExtendedLyric> extended = new ArrayList<>();
                String translation = enhancedTranslation(originalLines, translations,
                        lineStart, lineText);
                if (!translation.isEmpty()) {
                    extended.add(new ExtendedLyric("translation", translation));
                }
                String romanized = closestTranslation(romaji, lineStart);
                if (!romanized.isEmpty()) {
                    extended.add(new ExtendedLyric("romaji", romanized));
                }
                result.add(new Line(lineStart, Long.parseLong(lineMatcher.group(2)), lineText,
                        extended, Collections.unmodifiableList(words)));
            }
        }
        return result;
    }

    private static void trimTimedWordEdges(List<Word> words) {
        if (words.isEmpty()) return;
        int first = 0;
        while (first < words.size() && words.get(first).text.trim().isEmpty()) first++;
        if (first >= words.size()) {
            words.clear();
            return;
        }
        int last = words.size() - 1;
        while (last >= first && words.get(last).text.trim().isEmpty()) last--;
        if (last + 1 < words.size()) words.subList(last + 1, words.size()).clear();
        if (first > 0) words.subList(0, first).clear();
        Word leading = words.get(0);
        String leadingText = trimLeadingWhitespace(leading.text);
        if (!leadingText.equals(leading.text)) {
            words.set(0, new Word(leading.startMs, leading.durationMs, leadingText));
        }
        int end = words.size() - 1;
        Word trailing = words.get(end);
        String trailingText = trimTrailingWhitespace(trailing.text);
        if (!trailingText.equals(trailing.text)) {
            words.set(end, new Word(trailing.startMs, trailing.durationMs, trailingText));
        }
    }

    private static String trimLeadingWhitespace(String value) {
        int start = 0;
        while (start < value.length()) {
            int codePoint = value.codePointAt(start);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
            start += Character.charCount(codePoint);
        }
        return value.substring(start);
    }

    private static String trimTrailingWhitespace(String value) {
        int end = value.length();
        while (end > 0) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) break;
            end -= Character.charCount(codePoint);
        }
        return value.substring(0, end);
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

    private static List<ExtendedLyric> extensions(TreeMap<Long, String> translations,
                                                   TreeMap<Long, String> romaji, long timestamp) {
        List<ExtendedLyric> result = new ArrayList<>(2);
        String translation = closestTranslation(translations, timestamp);
        String romanized = closestTranslation(romaji, timestamp);
        if (!translation.isEmpty()) result.add(new ExtendedLyric("translation", translation));
        if (!romanized.isEmpty()) result.add(new ExtendedLyric("romaji", romanized));
        return result;
    }

    static final class ExtendedLyric {
        final String kind;
        final String text;

        ExtendedLyric(String kind, String text) {
            this.kind = kind == null ? "" : kind;
            this.text = text == null ? "" : text;
        }
    }

    static final class Line {
        final long timeMs;
        final long durationMs;
        final String text;
        final String translated;
        final String romaji;
        final List<ExtendedLyric> extendedLyrics;
        final List<Word> words;

        Line(long timeMs, long durationMs, String text) {
            this(timeMs, durationMs, text, "", Collections.emptyList());
        }

        Line(long timeMs, long durationMs, String text, String translated, List<Word> words) {
            this(timeMs, durationMs, text,
                    translated == null || translated.isEmpty() ? Collections.emptyList()
                            : Collections.singletonList(new ExtendedLyric("translation", translated)),
                    words);
        }

        Line(long timeMs, long durationMs, String text, List<ExtendedLyric> extendedLyrics,
             List<Word> words) {
            this.timeMs = timeMs;
            this.durationMs = durationMs;
            this.text = text;
            this.extendedLyrics = Collections.unmodifiableList(new ArrayList<>(extendedLyrics));
            this.translated = extension("translation");
            this.romaji = extension("romaji");
            this.words = words;
        }

        private String extension(String kind) {
            for (ExtendedLyric value : extendedLyrics) {
                if (kind.equals(value.kind)) return value.text;
            }
            return "";
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
        static final At EMPTY = new At("", "", "", "", false, false, "", "", false,
                -1L, 0L, -1L, 0L, 0, Collections.emptyList());
        final String previousLyric;
        final String lyric;
        final String translatedLyric;
        final String romajiLyric;
        final String nextLyric;
        final boolean interlude;
        final boolean wordTimed;
        final String completedLyric;
        final String currentWord;
        final boolean trailingWord;
        final long lineStartMs;
        final long lineDurationMs;
        final long wordStartMs;
        final long wordDurationMs;
        final int wordProgressPermille;
        final List<NearbyLine> nearbyLines;

        At(String previousLyric, String lyric, String translatedLyric, String nextLyric,
           boolean interlude, boolean wordTimed, String completedLyric, String currentWord,
           boolean trailingWord,
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
            this.trailingWord = trailingWord;
            this.lineStartMs = lineStartMs;
            this.lineDurationMs = lineDurationMs;
            this.wordStartMs = wordStartMs;
            this.wordDurationMs = wordDurationMs;
            this.wordProgressPermille = wordProgressPermille;
            this.nearbyLines = nearbyLines == null ? Collections.emptyList() : nearbyLines;
            String romanized = "";
            for (NearbyLine line : this.nearbyLines) {
                if (line.offset == 0) {
                    romanized = line.romaji;
                    break;
                }
            }
            this.romajiLyric = romanized;
        }
    }

    static final class NearbyLine {
        final String text;
        final String translated;
        final String romaji;
        final int offset;
        final long timeMs;
        final long durationMs;
        final boolean interlude;

        NearbyLine(String text, String translated, int offset, long timeMs,
                   long durationMs, boolean interlude) {
            this(text, translated, "", offset, timeMs, durationMs, interlude);
        }

        NearbyLine(String text, String translated, String romaji, int offset, long timeMs,
                   long durationMs, boolean interlude) {
            this.text = text == null ? "" : text;
            this.translated = translated == null ? "" : translated;
            this.romaji = romaji == null ? "" : romaji;
            this.offset = offset;
            this.timeMs = timeMs;
            this.durationMs = durationMs;
            this.interlude = interlude;
        }
    }
}
