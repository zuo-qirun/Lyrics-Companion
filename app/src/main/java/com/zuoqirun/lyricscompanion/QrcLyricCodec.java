package com.zuoqirun.lyricscompanion;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/** QQ Music cloud-QRC decoder, adapted from Proify/LyricProvider (Apache-2.0). */
final class QrcLyricCodec {
    private static final byte[] KEY = "!@#)(*$%123ZXC!@!@#)(NHL"
            .getBytes(StandardCharsets.US_ASCII);
    private static final Pattern LYRIC_CONTENT = Pattern.compile(
            "LyricContent\\s*=\\s*\"([\\s\\S]*?)\"(?=\\s*/?>)");
    private static final Pattern LINE = Pattern.compile("^\\[(\\d+)\\s*,\\s*(\\d+)](.*)$");
    private static final Pattern WORD = Pattern.compile("([^()\\r\\n]*)\\((\\d+)\\s*,\\s*(\\d+)\\)");

    private QrcLyricCodec() {}

    static String encryptedContent(String response, String tagName) {
        if (response == null || response.isEmpty()) return "";
        Pattern pattern = Pattern.compile("<" + Pattern.quote(tagName)
                + "[^>]*>.*?<!\\[CDATA\\[(.*?)]]>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    static String decryptToTimedLyric(String encryptedHex) throws Exception {
        return toEnhancedTimeline(decryptTimedPayload(encryptedHex));
    }

    static String decryptToLrc(String encryptedHex) throws Exception {
        return toPlainLrc(decryptTimedPayload(encryptedHex));
    }

    /** Converts QRC's text-before-timestamp words into LrcTimeline's marker-before-text form. */
    static String toEnhancedTimeline(String qrc) {
        if (qrc == null || qrc.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String rawLine : qrc.split("\\r?\\n")) {
            Matcher line = LINE.matcher(rawLine.trim());
            if (!line.matches()) continue;
            Matcher word = WORD.matcher(line.group(3));
            StringBuilder converted = new StringBuilder();
            while (word.find()) {
                String text = word.group(1);
                if (text.isEmpty()) continue;
                converted.append('(').append(word.group(2)).append(',')
                        .append(word.group(3)).append(",0)").append(text);
            }
            if (converted.length() > 0) {
                result.append('[').append(line.group(1)).append(',').append(line.group(2))
                        .append(']').append(converted).append('\n');
            }
        }
        return result.toString();
    }

    static String toPlainLrc(String timed) {
        if (timed == null || timed.isEmpty()) return "";
        if (Pattern.compile("(?m)^\\[\\d{1,3}:\\d{2}(?:[.:]\\d{1,3})?]")
                .matcher(timed).find()) {
            return timed.trim();
        }
        StringBuilder result = new StringBuilder();
        for (String rawLine : timed.split("\\r?\\n")) {
            Matcher line = LINE.matcher(rawLine.trim());
            if (!line.matches()) continue;
            long startMs = Long.parseLong(line.group(1));
            String text = line.group(3).replaceAll("\\(\\d+\\s*,\\s*\\d+\\)", "")
                    .trim();
            if (text.isEmpty() || "//".equals(text)) continue;
            result.append(String.format(Locale.ROOT, "[%02d:%02d.%03d]",
                    startMs / 60_000L, startMs / 1_000L % 60L, startMs % 1_000L))
                    .append(text).append('\n');
        }
        return result.toString();
    }

    private static String decryptTimedPayload(String encryptedHex) throws Exception {
        if (encryptedHex == null || encryptedHex.isEmpty()) return "";
        if (!isHex(encryptedHex)) return decodeXmlEntities(encryptedHex).trim();
        byte[] encrypted = fromHex(encryptedHex);
        String value = new String(inflate(QrcDesCompat.decrypt(encrypted, KEY)),
                StandardCharsets.UTF_8);
        Matcher matcher = LYRIC_CONTENT.matcher(value);
        return decodeXmlEntities(matcher.find() ? matcher.group(1) : value).trim();
    }

    private static boolean isHex(String value) {
        if ((value.length() & 1) != 0) return false;
        for (int i = 0; i < value.length(); i++) {
            if (Character.digit(value.charAt(i), 16) < 0) return false;
        }
        return !value.isEmpty();
    }

    private static byte[] fromHex(String value) {
        if ((value.length() & 1) != 0) throw new IllegalArgumentException("Odd QRC hex length");
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid QRC hex");
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static byte[] inflate(byte[] compressed) throws Exception {
        try (InflaterInputStream input = new InflaterInputStream(
                new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static String decodeXmlEntities(String value) {
        return value.replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
