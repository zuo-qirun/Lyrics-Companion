package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

/** KuGou KRC decoder/parser, adapted from Proify/LyricProvider (Apache-2.0). */
final class KrcLyricCodec {
    private static final byte[] KEY = new byte[]{
            64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45,
            (byte) 206, (byte) 210, 110, 105
    };
    private static final Pattern LINE = Pattern.compile("^\\[(\\d+)\\s*,\\s*(\\d+)](.*)$");
    private static final Pattern WORD = Pattern.compile("<(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*\\d+>");
    private static final Pattern LANGUAGE = Pattern.compile("(?m)^\\[language:([^]]+)]\\s*$",
            Pattern.CASE_INSENSITIVE);

    private KrcLyricCodec() {}

    static String decrypt(byte[] input) throws Exception {
        if (input == null || input.length <= 4) return "";
        byte[] compressed = new byte[input.length - 4];
        for (int i = 0; i < compressed.length; i++) {
            compressed[i] = (byte) (input[i + 4] ^ KEY[i % KEY.length]);
        }
        try (InflaterInputStream inflater = new InflaterInputStream(
                new ByteArrayInputStream(compressed));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = inflater.read(buffer)) >= 0) {
                if (count > 0) output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name()).replace("\uFEFF", "");
        }
    }

    /** Converts relative KRC word tags into the absolute word tags understood by LrcTimeline. */
    static String toEnhancedTimeline(String decryptedKrc) {
        if (decryptedKrc == null || decryptedKrc.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String rawLine : decryptedKrc.split("\\r?\\n")) {
            Matcher line = LINE.matcher(rawLine.trim());
            if (!line.matches()) continue;
            long lineStart = Long.parseLong(line.group(1));
            long lineDuration = Long.parseLong(line.group(2));
            String body = line.group(3);
            Matcher word = WORD.matcher(body);
            long previousOffset = -1L;
            long previousDuration = 0L;
            int textStart = -1;
            StringBuilder converted = new StringBuilder();
            while (word.find()) {
                if (previousOffset >= 0L && textStart >= 0) {
                    converted.append('(').append(lineStart + previousOffset).append(',')
                            .append(previousDuration).append(",0)")
                            .append(body, textStart, word.start());
                }
                previousOffset = Long.parseLong(word.group(1));
                previousDuration = Long.parseLong(word.group(2));
                textStart = word.end();
            }
            if (previousOffset >= 0L && textStart >= 0) {
                converted.append('(').append(lineStart + previousOffset).append(',')
                        .append(previousDuration).append(",0)")
                        .append(body.substring(textStart));
            }
            if (converted.length() > 0) {
                result.append('[').append(lineStart).append(',').append(lineDuration).append(']')
                        .append(converted).append('\n');
            }
        }
        return result.toString();
    }

    static String encodedLanguage(String decryptedKrc) {
        if (decryptedKrc == null || decryptedKrc.isEmpty()) return "";
        Matcher matcher = LANGUAGE.matcher(decryptedKrc);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    static String toTranslationLrc(String decryptedKrc, String languageJson) {
        if (decryptedKrc == null || decryptedKrc.isEmpty()
                || languageJson == null || languageJson.isEmpty()) return "";
        List<Long> lineStarts = new ArrayList<>();
        for (String rawLine : decryptedKrc.split("\\r?\\n")) {
            Matcher line = LINE.matcher(rawLine.trim());
            if (line.matches()) lineStarts.add(Long.parseLong(line.group(1)));
        }
        JSONArray sections;
        try {
            sections = new JSONObject(languageJson).optJSONArray("content");
        } catch (Exception ignored) {
            return "";
        }
        if (sections == null) return "";
        JSONArray translationRows = null;
        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section != null && section.optInt("type", -1) == 1) {
                translationRows = section.optJSONArray("lyricContent");
                break;
            }
        }
        if (translationRows == null || translationRows.length() != lineStarts.size()) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < translationRows.length(); i++) {
            JSONArray fragments = translationRows.optJSONArray(i);
            if (fragments == null) continue;
            StringBuilder text = new StringBuilder();
            for (int j = 0; j < fragments.length(); j++) text.append(fragments.optString(j, ""));
            String value = text.toString().trim();
            if (value.isEmpty() || "//".equals(value)) continue;
            long startMs = lineStarts.get(i);
            result.append(String.format(Locale.ROOT, "[%02d:%02d.%03d]",
                    startMs / 60_000L, startMs / 1_000L % 60L, startMs % 1_000L))
                    .append(value).append('\n');
        }
        return result.toString();
    }
}
