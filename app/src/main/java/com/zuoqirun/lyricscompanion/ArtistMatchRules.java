package com.zuoqirun.lyricscompanion;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 歌手字段的拆分与相似度规则。
 *
 * <p>用于替代过去简单的「整串包含」判断：多歌手合作曲目在查询与候选之间
 * 常出现分隔符、别名或顺序差异，这里按段比较。纯 Java 实现，可 JVM 单测。</p>
 */
final class ArtistMatchRules {

    /** 多歌手分隔符：逗号、顿号、斜杠、&、分号，以及 feat./ft. 标记。 */
    private static final Pattern SEPARATOR = Pattern.compile(
            "\\s*[,，、/&;；]\\s*|\\s+(?:feat\\.?|ft\\.?)\\s+");

    /** 单次比较最多保留的歌手段数，避免异常输入放大计算量。 */
    private static final int MAX_SEGMENTS = 16;

    private static final Pattern PUNCTUATION_OR_SPACE = Pattern.compile("[\\p{P}\\s]+");

    private ArtistMatchRules() {
    }

    /** 拆分歌手字符串，保留原始段的顺序并做 trim。 */
    static List<String> segments(String value) {
        List<String> result = new ArrayList<String>();
        if (value == null) {
            return result;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return result;
        }
        String[] parts = SEPARATOR.split(trimmed);
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String segment = part.trim();
            if (segment.isEmpty()) {
                continue;
            }
            result.add(segment);
            if (result.size() >= MAX_SEGMENTS) {
                break;
            }
        }
        if (result.isEmpty()) {
            result.add(trimmed);
        }
        return result;
    }

    /** 归一化歌手字符串：全半角/兼容字符统一、小写、去标点与空白。 */
    static String canonical(String value) {
        if (value == null) {
            return "";
        }
        String normalized;
        try {
            normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        } catch (RuntimeException e) {
            normalized = value;
        }
        return PUNCTUATION_OR_SPACE.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("");
    }

    /**
     * 计算歌手匹配分。
     *
     * <p>整串相等返回 +70；任一段相等或整串互相包含返回 +45；
     * 两边都非空且无法对应返回 −45；wanted 为空返回 0。</p>
     */
    static int score(String wanted, String found) {
        String wantedTrimmed = wanted == null ? "" : wanted.trim();
        if (wantedTrimmed.isEmpty()) {
            return 0;
        }
        String foundTrimmed = found == null ? "" : found.trim();
        if (foundTrimmed.isEmpty()) {
            return 0;
        }
        String wantedCanonical = canonical(wantedTrimmed);
        String foundCanonical = canonical(foundTrimmed);
        if (!wantedCanonical.isEmpty() && wantedCanonical.equals(foundCanonical)) {
            return 70;
        }
        List<String> wantedSegments = segments(wantedTrimmed);
        List<String> foundSegments = segments(foundTrimmed);
        for (String wantedSegment : wantedSegments) {
            String wantedSegmentCanonical = canonical(wantedSegment);
            if (wantedSegmentCanonical.isEmpty()) {
                continue;
            }
            for (String foundSegment : foundSegments) {
                if (wantedSegmentCanonical.equals(canonical(foundSegment))) {
                    return 45;
                }
            }
        }
        if (!wantedCanonical.isEmpty() && !foundCanonical.isEmpty()
                && (wantedCanonical.contains(foundCanonical) || foundCanonical.contains(wantedCanonical))) {
            return 45;
        }
        return -45;
    }
}
