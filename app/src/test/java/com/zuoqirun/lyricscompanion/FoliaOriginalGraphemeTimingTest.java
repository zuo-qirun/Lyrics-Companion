package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class FoliaOriginalGraphemeTimingTest {
    @Test public void wordsAndUntimedPunctuationMatchUnmodifiedUpstream() throws Exception {
        JSONArray cases;
        try (InputStream input = getClass().getResourceAsStream("/folia-grapheme-oracle.json")) {
            assertNotNull(input);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int count; (count = input.read(buffer)) >= 0;) bytes.write(buffer, 0, count);
            cases = new JSONArray(bytes.toString("UTF-8"));
        }
        for (int c = 0; c < cases.length(); c++) {
            JSONObject fixture = cases.getJSONObject(c), source = fixture.getJSONObject("line");
            List<LrcTimeline.Word> words = new ArrayList<>();
            JSONArray input = source.getJSONArray("words");
            for (int w = 0; w < input.length(); w++) {
                JSONObject word = input.getJSONObject(w);
                long start = Math.round(word.getDouble("startTime") * 1000);
                words.add(new LrcTimeline.Word(start, Math.round(word.getDouble("endTime") * 1000) - start,
                        word.getString("text")));
            }
            long start = Math.round(source.getDouble("startTime") * 1000);
            LrcTimeline.Line line = new LrcTimeline.Line(start,
                    Math.round(source.getDouble("endTime") * 1000) - start, source.getString("fullText"), "", words);
            for (boolean ring : new boolean[]{false, true}) {
                FoliaOriginalGraphemeTiming actual = new FoliaOriginalGraphemeTiming(line, -1, ring);
                JSONArray expected = fixture.getJSONArray(ring ? "claddagh" : "timeline");
                assertEquals("fixture " + c, expected.length(), actual.glyphs.length);
                for (int g = 0; g < expected.length(); g++) {
                    JSONObject glyph = expected.getJSONObject(g);
                    String label = "fixture=" + c + " ring=" + ring + " glyph=" + g;
                    assertEquals(label, glyph.getString("char"), actual.glyphs[g]);
                    assertEquals(label, glyph.getDouble("startTime") * 1000, actual.starts[g], 1e-9);
                    assertEquals(label, glyph.getDouble("endTime") * 1000, actual.ends[g], 1e-9);
                    assertEquals(label, glyph.optInt("wordIndex", -1), actual.wordIndices[g]);
                }
            }
        }
    }

    @Test public void singleGlyphRingUsesRenderTailForFollowProgress() {
        LrcTimeline.Line line = new LrcTimeline.Line(1000, 1000, "光");
        FoliaOriginalGraphemeTiming timing = new FoliaOriginalGraphemeTiming(line, -1, true);
        assertEquals(12000, timing.renderEnd, 0);
        assertEquals(0.5, timing.playbackProgress(6500), 0);
        assertEquals(0.5, timing.fractionalIndex(6500), 0);
    }
}
