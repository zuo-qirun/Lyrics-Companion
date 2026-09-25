package com.zuoqirun.lyricscompanion;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Migu MRC TEA decoder and absolute millisecond word timeline converter. */
final class MiguMrcCodec {
    private static final long DELTA = 0x9E3779B9L;
    private static final long[] KEY = {
            27303562373562475L, 18014862372307051L, 22799692160172081L,
            34058940340699235L, 30962724186095721L, 27303523720101991L,
            27303523720101998L, 31244139033526382L, 28992395054481524L
    };
    private static final Pattern LINE = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern WORD = Pattern.compile("\\((\\d+),(\\d+)\\)");
    private MiguMrcCodec() {}

    static String decrypt(String encrypted) {
        if (encrypted == null || encrypted.length() < 32) return encrypted == null ? "" : encrypted;
        if (encrypted.trim().startsWith("[")) return encrypted;
        int length = encrypted.length() / 16;
        if (length < 2 || length > 65536) return "";
        long[] data = new long[length];
        try {
            for (int i = 0; i < length; i++) {
                data[i] = new java.math.BigInteger(encrypted.substring(i * 16, i * 16 + 16), 16)
                        .longValue();
            }
        } catch (NumberFormatException error) { return ""; }
        long sum = (6L + 52L / length) * DELTA;
        long last = data[0];
        while (sum != 0L) {
            int e = (int) ((sum >>> 2) & 3L);
            for (int index = length - 1; index > 0; index--) {
                long previous = data[index - 1];
                last = data[index] - mix(last, previous, sum, KEY[(index & 3) ^ e]);
                data[index] = last;
            }
            long previous = data[length - 1];
            last = data[0] - mix(last, previous, sum, KEY[e]);
            data[0] = last;
            sum -= DELTA;
        }
        ByteBuffer bytes = ByteBuffer.allocate(length * 8).order(ByteOrder.LITTLE_ENDIAN);
        for (long value : data) bytes.putLong(value);
        return StandardCharsets.UTF_16LE.decode((ByteBuffer) bytes.flip()).toString()
                .replace("\u0000", "").replace("\uFEFF", "");
    }

    private static long mix(long current, long previous, long sum, long key) {
        return ((current ^ sum) + (previous ^ key))
                ^ (((previous >> 5) ^ (current << 2))
                + ((current >> 3) ^ (previous << 4)));
    }

    static String toEnhancedTimeline(String mrc) {
        if (mrc == null || mrc.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String raw : mrc.split("\\r?\\n")) {
            Matcher line = LINE.matcher(raw.trim());
            if (!line.matches()) continue;
            long start = Long.parseLong(line.group(1));
            long duration = Long.parseLong(line.group(2));
            Matcher word = WORD.matcher(line.group(3));
            String body = line.group(3);
            StringBuilder words = new StringBuilder();
            int end = -1;
            long wordStart = -1L, wordDuration = 0L;
            while (word.find()) {
                if (end >= 0) words.append('(').append(wordStart).append(',')
                        .append(wordDuration).append(",0)")
                        .append(body, end, word.start());
                wordStart = Long.parseLong(word.group(1));
                wordDuration = Long.parseLong(word.group(2));
                end = word.end();
            }
            if (end < 0) continue;
            words.append('(').append(wordStart).append(',').append(wordDuration)
                    .append(",0)").append(body.substring(end));
            result.append('[').append(start).append(',').append(duration).append(']')
                    .append(words).append('\n');
        }
        return result.toString();
    }
}
