package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 匹配规则引擎的输出：清洗后的主结果、全部候选快照与逐规则轨迹。
 *
 * <p>主结果用于歌词查询；候选列表按规则执行顺序记录每个发生变化的中间状态，
 * 供多源匹配依次尝试；轨迹用于诊断页展示。对象不可变。</p>
 */
final class MatchOutput {

    final String primaryTitle;
    final String primaryArtist;
    final String primaryAlbum;
    final List<Candidate> candidates;
    final List<TraceEntry> trace;
    final boolean changed;

    MatchOutput(String primaryTitle, String primaryArtist, String primaryAlbum,
                List<Candidate> candidates, List<TraceEntry> trace, boolean changed) {
        this.primaryTitle = value(primaryTitle);
        this.primaryArtist = value(primaryArtist);
        this.primaryAlbum = value(primaryAlbum);
        this.candidates = Collections.unmodifiableList(
                new ArrayList<Candidate>(candidates == null ? Collections.<Candidate>emptyList() : candidates));
        this.trace = Collections.unmodifiableList(
                new ArrayList<TraceEntry>(trace == null ? Collections.<TraceEntry>emptyList() : trace));
        this.changed = changed;
    }

    static MatchOutput empty() {
        return new MatchOutput("", "", "", null, null, false);
    }

    /** 规则执行过程中产生的候选元数据快照。 */
    static final class Candidate {

        final String title;
        final String artist;
        final String ruleId;
        final String kind;

        Candidate(String title, String artist, String ruleId, String kind) {
            this.title = value(title);
            this.artist = value(artist);
            this.ruleId = value(ruleId);
            this.kind = value(kind);
        }

        String key() {
            return title + "\u0000" + artist;
        }
    }

    /** 单条规则动作的变更轨迹。 */
    static final class TraceEntry {

        final String ruleId;
        final String action;
        final String field;
        final String before;
        final String after;

        TraceEntry(String ruleId, String action, String field, String before, String after) {
            this.ruleId = value(ruleId);
            this.action = value(action);
            this.field = value(field);
            this.before = value(before);
            this.after = value(after);
        }
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
