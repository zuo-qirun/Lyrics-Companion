package com.zuoqirun.lyricscompanion;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/** Decodes Kuwo's mobile word-timed lyric response into absolute millisecond word tags. */
final class KuwoWordLyricCodec {
    private static final byte[] XOR_KEY = "yeelion".getBytes(StandardCharsets.US_ASCII);
    private static final Pattern LINE = Pattern.compile(
            "^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?](.*)$");
    private static final Pattern WORD = Pattern.compile("<(-?\\d+),(-?\\d+)(?:,-?\\d+)?>");
    private static final Pattern KUWO_TAG = Pattern.compile("(?m)^\\[kuwo:([^]]+)]");
    private static final int MAX_DECOMPRESSED_BYTES = 10 * 1024 * 1024;

    private KuwoWordLyricCodec() {}

    static String decode(byte[] response) throws Exception {
        if (response == null || response.length < 15) return "";
        String prefix = new String(response, 0, Math.min(10, response.length),
                StandardCharsets.US_ASCII);
        if (!"tp=content".equalsIgnoreCase(prefix)) return "";
        int body = -1;
        for (int index = 0; index + 3 < response.length; index++) {
            if (response[index] == '\r' && response[index + 1] == '\n'
                    && response[index + 2] == '\r' && response[index + 3] == '\n') {
                body = index + 4;
                break;
            }
        }
        if (body < 0) return "";
        ByteArrayOutputStream inflated = new ByteArrayOutputStream();
        try (InflaterInputStream input = new InflaterInputStream(
                new ByteArrayInputStream(response, body, response.length - body))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) {
                    if (inflated.size() + count > MAX_DECOMPRESSED_BYTES) {
                        throw new IOException("Kuwo lyric exceeds size limit");
                    }
                    inflated.write(buffer, 0, count);
                }
            }
        }
        byte[] decoded = Base64.decode(inflated.toByteArray(), Base64.DEFAULT);
        for (int index = 0; index < decoded.length; index++) {
            decoded[index] ^= XOR_KEY[index % XOR_KEY.length];
        }
        return new String(decoded, StandardCharsets.UTF_8).replace("\uFEFF", "");
    }

    static String toEnhancedTimeline(String lyric) {
        if (lyric == null || lyric.isEmpty()) return "";
        long firstDivisor = 1L;
        long secondDivisor = 1L;
        Matcher tag = KUWO_TAG.matcher(lyric);
        if (tag.find()) {
            try {
                long value = Long.parseLong(tag.group(1).split("\\]")[0].trim(), 8);
                firstDivisor = value / 10L;
                secondDivisor = value % 10L;
                if (firstDivisor <= 0L || secondDivisor <= 0L) return "";
            } catch (NumberFormatException error) {
                return "";
            }
        }
        StringBuilder enhanced = new StringBuilder();
        for (String rawLine : lyric.split("\\r?\\n")) {
            Matcher line = LINE.matcher(rawLine.trim());
            if (!line.matches()) continue;
            long lineStart = Long.parseLong(line.group(1)) * 60_000L
                    + Long.parseLong(line.group(2)) * 1_000L;
            if (line.group(3) != null) {
                String fraction = line.group(3);
                lineStart += Long.parseLong(fraction)
                        * (fraction.length() == 1 ? 100L : fraction.length() == 2 ? 10L : 1L);
            }
            String body = line.group(4);
            Matcher marker = WORD.matcher(body);
            List<Word> words = new ArrayList<>();
            int textStart = -1;
            long start = -1L;
            long end = -1L;
            while (marker.find()) {
                if (start >= 0L && textStart >= 0) {
                    words.add(new Word(start, end, body.substring(textStart, marker.start())));
                }
                long first = Long.parseLong(marker.group(1));
                long second = Long.parseLong(marker.group(2));
                start = Math.abs((first + second) / (firstDivisor * 2L));
                end = start + Math.abs((first - second) / (secondDivisor * 2L));
                textStart = marker.end();
            }
            if (start >= 0L && textStart >= 0) {
                words.add(new Word(start, end, body.substring(textStart)));
            }
            if (words.isEmpty()) continue;
            for (int index = 1; index < words.size(); index++) {
                Word previous = words.get(index - 1);
                previous.end = Math.min(previous.end, words.get(index).start);
                if (previous.start > previous.end) previous.start = previous.end;
            }
            long lineEnd = words.get(words.size() - 1).end;
            enhanced.append('[').append(lineStart).append(',')
                    .append(Math.max(0L, lineEnd)).append(']');
            for (Word word : words) {
                enhanced.append('(').append(lineStart + word.start).append(',')
                        .append(Math.max(0L, word.end - word.start)).append(",0)")
                        .append(word.text);
            }
            enhanced.append('\n');
        }
        return enhanced.toString();
    }

    private static final class Word {
        long start;
        long end;
        final String text;

        Word(long start, long end, String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }
    }
}
