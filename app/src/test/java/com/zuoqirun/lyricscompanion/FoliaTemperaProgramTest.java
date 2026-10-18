/*
 * Copyright (C) 2026 zuoqirun
 *
 * This file is part of Lyrics Companion, licensed under the GNU General Public
 * License version 3 or later. See LICENSE for details.
 */

package com.zuoqirun.lyricscompanion;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Regression coverage for the generated Folia Tempera program compiler.
 *
 * <p>The upstream compiler slices oversized drafts with {@code slice(2, -1)}. A
 * draft made of exactly two long lyric lines produces a reversed range; plain
 * Java lists throw {@link IllegalArgumentException} there while the JavaScript
 * counterpart returns an empty array. Long instrumental intros used to crash
 * the whole overlay for that reason.</p>
 */
public class FoliaTemperaProgramTest {

    @Test
    public void compileToleratesTwoLongLinesInOneDraft() {
        List<LrcTimeline.Line> lines = new ArrayList<>();
        lines.add(new LrcTimeline.Line(0L, 10_000L, "在无人的世界里",
                "The light stays with you", words("在无人的世界里", 0L, 10_000L)));
        lines.add(new LrcTimeline.Line(11_000L, 10_000L, "听见风穿过树梢",
                "The light stays with you", words("听见风穿过树梢", 11_000L, 10_000L)));

        Object source = FoliaTemperaTimeline.lines(lines);
        Object compiled = FoliaTemperaGeometryProgram.compile(source, "Folia visual reference", false);

        assertNotNull(compiled);
        assertTrue(compiled instanceof Map);
        assertNotNull(((Map<?, ?>) compiled).get("paragraphs"));
    }

    private static List<LrcTimeline.Word> words(String text, long start, long duration) {
        List<LrcTimeline.Word> result = new ArrayList<>(text.length());
        long step = Math.max(1L, duration / Math.max(1, text.length()));
        for (int i = 0; i < text.length(); i++) {
            result.add(new LrcTimeline.Word(start + step * i, step, text.substring(i, i + 1)));
        }
        return result;
    }
}
