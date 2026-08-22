package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds conservative fallback searches from file-like MediaSession titles. */
final class LocalTrackQueryRules {
    private static final Pattern AUDIO_EXTENSION = Pattern.compile(
            "(?i)\\.(?:mp3|flac|wav|aac|m4a|ogg|opus|wma|ape|alac|dsf|dff)$");
    private static final Pattern TRACK_PREFIX = Pattern.compile(
            "(?i)^\\s*(?:(?:cd|disc)\\s*\\d+\\s*[-_. ]+)?"
                    + "(?:track\\s*)?\\d{1,3}\\s*[-_.、． ]+\\s*");
    private static final Pattern QUALITY_OR_VERSION_TAG = Pattern.compile(
            "(?i)\\s*[\\[\\(（【][^\\]\\)）】]*(?:\\b(?:hi[ -]?res|lossless|"
                    + "sq|hq|flac|mp3|wav|\\d{2,3}\\s*k(?:bps)?|\\d{2}\\s*bit|"
                    + "remaster(?:ed)?|live)\\b|无损|伴奏|纯音乐|dj版)[^\\]\\)）】]*"
                    + "[\\]\\)）】]\\s*");
    private static final Pattern SPACED_SEPARATOR = Pattern.compile("\\s+[-–—－]\\s+");

    private LocalTrackQueryRules() {}

    static List<Query> fallbackQueries(String source, String title, String artist) {
        String originalTitle = safe(title);
        String originalArtist = safe(artist);
        if (originalTitle.isEmpty() || !shouldParse(source, originalTitle, originalArtist)) {
            return new ArrayList<>();
        }

        Map<String, Query> candidates = new LinkedHashMap<>();
        String cleaned = cleanFileTitle(originalTitle);
        String[] parts = splitArtistAndTitle(cleaned,
                originalArtist.isEmpty() || prefersEmbeddedArtist(source));
        if (parts != null) {
            String left = parts[0];
            String right = parts[1];
            if (prefersEmbeddedArtist(source)) {
                // Podcast/vehicle metadata often puts the album or channel in ARTIST while the
                // actual song credit is embedded in TITLE as "artist - title".
                add(candidates, originalTitle, originalArtist, right, left);
                add(candidates, originalTitle, originalArtist, left, right);
            }
            if (!originalArtist.isEmpty()) {
                if (sameText(left, originalArtist)) {
                    add(candidates, originalTitle, originalArtist, right, originalArtist);
                } else if (sameText(right, originalArtist)) {
                    add(candidates, originalTitle, originalArtist, left, originalArtist);
                } else {
                    add(candidates, originalTitle, originalArtist, right, originalArtist);
                    add(candidates, originalTitle, originalArtist, left, originalArtist);
                }
            } else {
                // Most local libraries use "artist - title"; retain the reverse convention too.
                add(candidates, originalTitle, originalArtist, right, left);
                add(candidates, originalTitle, originalArtist, left, right);
            }
        } else {
            add(candidates, originalTitle, originalArtist, cleaned, originalArtist);
        }
        return new ArrayList<>(candidates.values());
    }

    static String cleanFileTitle(String value) {
        String cleaned = safe(value);
        int slash = Math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'));
        if (slash >= 0 && slash + 1 < cleaned.length()) cleaned = cleaned.substring(slash + 1);
        cleaned = AUDIO_EXTENSION.matcher(cleaned).replaceFirst("");
        cleaned = TRACK_PREFIX.matcher(cleaned).replaceFirst("");
        cleaned = QUALITY_OR_VERSION_TAG.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("_{2,}", " - ")
                .replaceAll("\\s+", " ").trim();
        return trimSeparators(cleaned);
    }

    private static boolean shouldParse(String source, String title, String artist) {
        String normalizedSource = safe(source).toLowerCase(Locale.ROOT);
        return "media".equals(normalizedSource) || "xiaomi".equals(normalizedSource)
                || "huawei".equals(normalizedSource) || "ximalaya".equals(normalizedSource)
                || "dftc_media".equals(normalizedSource) || artist.isEmpty()
                || AUDIO_EXTENSION.matcher(title).find() || title.indexOf('/') >= 0
                || title.indexOf('\\') >= 0 || TRACK_PREFIX.matcher(title).find()
                || QUALITY_OR_VERSION_TAG.matcher(title).find();
    }

    private static boolean prefersEmbeddedArtist(String source) {
        String normalized = safe(source).toLowerCase(Locale.ROOT);
        return "ximalaya".equals(normalized) || "dftc_media".equals(normalized);
    }

    /** Bare (unbracketed) quality/format tags seen in car-player file titles, e.g. WAV真无损. */
    private static final Pattern BARE_QUALITY_TAG = Pattern.compile(
            "(?i)(?:wav|flac|ape|mp3|dsf|dff|hi-res|hires|lossless|真无损|无损|CD音轨)");

    /** Track numbers up to four digits followed by a separator, e.g. 0007. / 12- / 003_. */
    private static final Pattern LEADING_TRACK_NUMBER = Pattern.compile(
            "^\\s*\\d{1,4}\\s*[.、_－-]");

    /**
     * File-style or artist-split titles mark a genuine track on vendors that swap their title
     * field to the current lyric line while a song plays (observed on Dongfeng head units).
     */
    static boolean looksLikeStructuredTrackTitle(String value) {
        String v = safe(value);
        return !v.isEmpty() && (AUDIO_EXTENSION.matcher(v).find()
                || TRACK_PREFIX.matcher(v).find()
                || QUALITY_OR_VERSION_TAG.matcher(v).find()
                || SPACED_SEPARATOR.matcher(v).find()
                || LEADING_TRACK_NUMBER.matcher(v).find()
                || BARE_QUALITY_TAG.matcher(v).find());
    }

    private static String[] splitArtistAndTitle(String value, boolean allowCompactSeparator) {
        Matcher spaced = SPACED_SEPARATOR.matcher(value);
        if (spaced.find()) {
            return validParts(value.substring(0, spaced.start()), value.substring(spaced.end()));
        }
        int fullWidth = firstSeparator(value, '–', '—', '－');
        if (fullWidth > 0 && fullWidth < value.length() - 1) {
            return validParts(value.substring(0, fullWidth), value.substring(fullWidth + 1));
        }
        if (allowCompactSeparator) {
            int asciiDash = value.indexOf('-');
            if (asciiDash > 0 && asciiDash == value.lastIndexOf('-')
                    && asciiDash < value.length() - 1) {
                return validParts(value.substring(0, asciiDash), value.substring(asciiDash + 1));
            }
            int underscore = value.indexOf('_');
            if (underscore > 0 && underscore == value.lastIndexOf('_')
                    && underscore < value.length() - 1) {
                return validParts(value.substring(0, underscore), value.substring(underscore + 1));
            }
        }
        return null;
    }

    private static String[] validParts(String left, String right) {
        String first = trimSeparators(left);
        String second = trimSeparators(right);
        return first.isEmpty() || second.isEmpty() ? null : new String[]{first, second};
    }

    private static int firstSeparator(String value, char... separators) {
        int selected = -1;
        for (char separator : separators) {
            int index = value.indexOf(separator);
            if (index >= 0 && (selected < 0 || index < selected)) selected = index;
        }
        return selected;
    }

    private static void add(Map<String, Query> candidates, String originalTitle,
                            String originalArtist, String title, String artist) {
        String candidateTitle = safe(title);
        String candidateArtist = safe(artist);
        if (candidateTitle.isEmpty()) return;
        String key = identity(candidateTitle) + "|" + identity(candidateArtist);
        String originalKey = identity(originalTitle) + "|" + identity(originalArtist);
        if (!key.equals(originalKey) && !candidates.containsKey(key)) {
            candidates.put(key, new Query(candidateTitle, candidateArtist));
        }
    }

    private static boolean sameText(String left, String right) {
        String normalizedLeft = identity(left);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(identity(right));
    }

    private static String identity(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[\\p{P}\\s]+", "");
    }

    private static String trimSeparators(String value) {
        return safe(value).replaceAll("^[\\s._–—－-]+|[\\s._–—－-]+$", "").trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static final class Query {
        final String title;
        final String artist;

        Query(String title, String artist) {
            this.title = safe(title);
            this.artist = safe(artist);
        }
    }
}
