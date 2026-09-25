package com.zuoqirun.lyricscompanion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts lx-music's line-relative word tags to this app's absolute YRC word tags. */
final class AwlrcCodec {
    private static final Pattern LINE = Pattern.compile(
            "^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)$");
    private static final Pattern WORD = Pattern.compile("<(\\d+),(\\d+)>");
    private AwlrcCodec() {}

    static String toYrc(String awlrc) {
        if (awlrc == null || awlrc.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String raw : awlrc.split("\\r?\\n")) {
            Matcher line = LINE.matcher(raw.trim());
            if (!line.matches()) continue;
            long start = Long.parseLong(line.group(1)) * 60_000L
                    + Long.parseLong(line.group(2)) * 1_000L;
            if (line.group(3) != null) {
                String fraction = line.group(3);
                start += Long.parseLong(fraction)
                        * (fraction.length() == 1 ? 100L : fraction.length() == 2 ? 10L : 1L);
            }
            String content = line.group(4);
            Matcher word = WORD.matcher(content);
            StringBuilder words = new StringBuilder();
            long lineEnd = start;
            int end = -1;
            long wordStart = 0L, duration = 0L;
            while (word.find()) {
                if (end >= 0) words.append('(').append(wordStart).append(',')
                        .append(duration).append(",0)").append(content, end, word.start());
                wordStart = start + Long.parseLong(word.group(1));
                duration = Long.parseLong(word.group(2));
                lineEnd = Math.max(lineEnd, wordStart + duration);
                end = word.end();
            }
            if (end < 0) continue;
            words.append('(').append(wordStart).append(',').append(duration)
                    .append(",0)").append(content.substring(end));
            result.append('[').append(start).append(',')
                    .append(Math.max(0L, lineEnd - start)).append(']')
                    .append(words).append('\n');
        }
        return result.toString();
    }
}
