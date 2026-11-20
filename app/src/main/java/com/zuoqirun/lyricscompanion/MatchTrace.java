package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 最近一次歌词匹配的轨迹，用于诊断页展示。
 *
 * <p>静态、线程安全、容量有上限；每次加载歌词前由
 * {@code MultiSourceLyricClient} 清空并依次记录输入、规则输出、查询与命中。
 * 不参与匹配决策，也不持久化。</p>
 */
final class MatchTrace {

    private static final int MAX_QUERIES = 24;
    private static final int MAX_MATCHES = 8;
    private static final int MAX_CANDIDATES_IN_LINE = 6;
    private static final int MAX_DESCRIBE_LENGTH = 1600;

    private static final Object LOCK = new Object();
    private static String inputLine = "";
    private static String engineLine = "";
    private static final List<String> queryLines = new ArrayList<String>();
    private static final List<String> matchLines = new ArrayList<String>();

    private MatchTrace() {
    }

    /** 清空最近一次轨迹。 */
    static void clear() {
        synchronized (LOCK) {
            inputLine = "";
            engineLine = "";
            queryLines.clear();
            matchLines.clear();
        }
    }

    /** 记录规则输入。 */
    static void recordInput(MatchInput input) {
        if (input == null) {
            return;
        }
        synchronized (LOCK) {
            inputLine = "package=" + shorten(input.packageName, 60)
                    + " source=" + shorten(input.source, 24)
                    + " title=" + shorten(input.title, 80)
                    + " artist=" + shorten(input.artist, 80)
                    + " album=" + shorten(input.album, 40);
        }
    }

    /** 记录规则引擎输出摘要。 */
    static void recordEngine(MatchOutput output) {
        if (output == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("changed=").append(output.changed)
                .append(" title=").append(shorten(output.primaryTitle, 80))
                .append(" artist=").append(shorten(output.primaryArtist, 80));
        if (!output.candidates.isEmpty()) {
            builder.append(" candidates=[");
            int limit = Math.min(output.candidates.size(), MAX_CANDIDATES_IN_LINE);
            for (int i = 0; i < limit; i++) {
                if (i > 0) {
                    builder.append(" | ");
                }
                MatchOutput.Candidate candidate = output.candidates.get(i);
                builder.append(shorten(candidate.title, 40)).append(" / ")
                        .append(shorten(candidate.artist, 40))
                        .append(" @").append(candidate.ruleId);
            }
            if (output.candidates.size() > limit) {
                builder.append(" | ...");
            }
            builder.append(']');
        }
        synchronized (LOCK) {
            engineLine = builder.toString();
        }
    }

    /** 记录一个待查询的 catalog 查询词。 */
    static void recordQuery(int index, String title, String artist) {
        synchronized (LOCK) {
            if (queryLines.size() >= MAX_QUERIES) {
                return;
            }
            queryLines.add("[" + Math.max(0, index) + "] " + shorten(title, 60)
                    + " / " + shorten(artist, 60));
        }
    }

    /** 记录一次命中的候选与分数（未知分数传 -1）。 */
    static void recordMatch(String provider, String candidateTitle, String candidateArtist, int score,
                            String sourceName) {
        synchronized (LOCK) {
            if (matchLines.size() >= MAX_MATCHES) {
                return;
            }
            StringBuilder builder = new StringBuilder();
            builder.append("provider=").append(shorten(provider, 24))
                    .append(" candidate=").append(shorten(candidateTitle, 60))
                    .append(" / ").append(shorten(candidateArtist, 60));
            if (score >= 0) {
                builder.append(" score=").append(score);
            }
            if (sourceName != null && !sourceName.trim().isEmpty()) {
                builder.append(" source=").append(shorten(sourceName, 40));
            }
            matchLines.add(builder.toString());
        }
    }

    /** 诊断页展示用文本。 */
    static String describe() {
        String capturedInput;
        String capturedEngine;
        List<String> capturedQueries;
        List<String> capturedMatches;
        synchronized (LOCK) {
            capturedInput = inputLine;
            capturedEngine = engineLine;
            capturedQueries = new ArrayList<String>(queryLines);
            capturedMatches = new ArrayList<String>(matchLines);
        }
        if (capturedInput.isEmpty() && capturedEngine.isEmpty()
                && capturedQueries.isEmpty() && capturedMatches.isEmpty()) {
            return "匹配轨迹：暂无";
        }
        StringBuilder builder = new StringBuilder("匹配轨迹：");
        if (!capturedInput.isEmpty()) {
            builder.append("\n输入 ").append(capturedInput);
        }
        if (!capturedEngine.isEmpty()) {
            builder.append("\n规则 ").append(capturedEngine);
        }
        for (String line : capturedQueries) {
            builder.append("\n查询 ").append(line);
        }
        for (String line : capturedMatches) {
            builder.append("\n命中 ").append(line);
        }
        String text = builder.toString();
        if (text.length() > MAX_DESCRIBE_LENGTH) {
            return text.substring(0, MAX_DESCRIBE_LENGTH) + "…";
        }
        return text;
    }

    private static String shorten(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength) + "…";
    }
}
