package com.zuoqirun.lyricscompanion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 匹配规则引擎：按顺序应用规则包，产出一份稳定的清洗结果。
 *
 * <p>引擎不修改入参；主结果与候选列表都基于原始元数据推导，因此同一输入
 * 永远得到同一输出，方便单测与缓存。任何规则/动作异常都被隔离，不影响
 * 后续规则执行。</p>
 */
final class MatchRuleEngine {

    static final String CANDIDATE_KIND = "cleaned";

    private static final int MAX_CANDIDATES = 32;
    private static final int MAX_TRACE_ENTRIES = 64;
    private static final int MAX_ACTIONS = 4096;

    private MatchRuleEngine() {
    }

    /** 应用全部规则包；packs 为空或入参为空时返回未变化的空输出。 */
    static MatchOutput apply(List<MatchRulePack> packs, MatchInput input) {
        if (input == null) {
            return MatchOutput.empty();
        }
        String[] fields = new String[]{input.title, input.artist, input.album};
        List<MatchOutput.Candidate> candidates = new ArrayList<MatchOutput.Candidate>();
        List<MatchOutput.TraceEntry> trace = new ArrayList<MatchOutput.TraceEntry>();
        Set<String> candidateKeys = new LinkedHashSet<String>();
        String originalKey = key(input.title, input.artist, input.album);
        int actionCount = 0;
        if (packs != null) {
            for (MatchRulePack pack : packs) {
                if (pack == null || !pack.appliesTo(input.packageName, input.source)) {
                    continue;
                }
                for (MatchRule rule : pack.rules) {
                    if (actionCount >= MAX_ACTIONS) {
                        break;
                    }
                    boolean matched;
                    try {
                        matched = rule.matches(input, fields);
                    } catch (RuntimeException e) {
                        matched = false;
                    }
                    if (!matched) {
                        continue;
                    }
                    for (MatchRule.Action action : rule.then) {
                        if (actionCount >= MAX_ACTIONS) {
                            break;
                        }
                        actionCount++;
                        String[] before = new String[]{fields[0], fields[1], fields[2]};
                        boolean changed;
                        try {
                            changed = action.apply(fields, input);
                        } catch (RuntimeException e) {
                            changed = false;
                        }
                        if (!changed) {
                            continue;
                        }
                        recordTrace(trace, rule.id, action.type, before, fields);
                        String candidateKey = key(fields[0], fields[1], fields[2]);
                        if (!candidateKey.equals(originalKey)
                                && !candidateKeys.contains(candidateKey)
                                && candidates.size() < MAX_CANDIDATES) {
                            candidateKeys.add(candidateKey);
                            candidates.add(new MatchOutput.Candidate(fields[0], fields[1], rule.id,
                                    CANDIDATE_KIND));
                        }
                    }
                }
            }
        }
        boolean changed = !fields[0].equals(input.title)
                || !fields[1].equals(input.artist)
                || !fields[2].equals(input.album);
        return new MatchOutput(fields[0], fields[1], fields[2],
                Collections.unmodifiableList(candidates), Collections.unmodifiableList(trace), changed);
    }

    private static void recordTrace(List<MatchOutput.TraceEntry> trace, String ruleId, String action,
                                    String[] before, String[] after) {
        if (trace.size() >= MAX_TRACE_ENTRIES) {
            return;
        }
        String[] labels = new String[]{"title", "artist", "album"};
        for (int i = 0; i < after.length; i++) {
            if (trace.size() >= MAX_TRACE_ENTRIES) {
                return;
            }
            if (!after[i].equals(before[i])) {
                trace.add(new MatchOutput.TraceEntry(ruleId, action, labels[i], before[i], after[i]));
            }
        }
    }

    private static String key(String title, String artist, String album) {
        return title + "\u0000" + artist + "\u0000" + album;
    }
}
