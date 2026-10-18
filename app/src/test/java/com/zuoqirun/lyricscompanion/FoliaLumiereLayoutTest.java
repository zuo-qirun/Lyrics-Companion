// SPDX-License-Identifier: AGPL-3.0-only
package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class FoliaLumiereLayoutTest {
    @Test public void allFourLayoutsMatchUnmodifiedSourceForFortyWordAndPunctuationCases() throws Exception {
        String source;
        try (InputStream input = getClass().getResourceAsStream("/folia_lumiere_layout.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        JSONArray cases = new JSONObject(source).getJSONArray("cases");
        for (int f = 0; f < cases.length(); f++) {
            JSONObject fixture = cases.getJSONObject(f); JSONArray inputs = fixture.getJSONArray("glyphs"), spans = fixture.getJSONArray("words");
            FoliaLumiereLayout.Glyph[] glyphs = new FoliaLumiereLayout.Glyph[inputs.length()];
            for (int i = 0; i < glyphs.length; i++) {
                JSONObject g = inputs.getJSONObject(i);
                glyphs[i] = new FoliaLumiereLayout.Glyph(g.getString("char"), g.getDouble("scale"), g.getDouble("advance"), g.getDouble("jag"), g.getBoolean("upright"));
            }
            List<FoliaLumiereLayout.Word> words = new ArrayList<>();
            for (int i = 0; i < spans.length(); i++) {
                JSONObject w = spans.getJSONObject(i);
                words.add(new FoliaLumiereLayout.Word(w.getInt("start"), w.getInt("end"), w.getBoolean("blank")));
            }
            final double spacing = fixture.getDouble("spacing");
            FoliaLumiereLayout.Measurer measure = new FoliaLumiereLayout.Measurer() {
                @Override public double natural(String text, double px) {
                    if (text.trim().isEmpty()) return 0;
                    double sum = 0;
                    for (int i = 0; i < text.length();) {
                        int cp = text.codePointAt(i); String c = new String(Character.toChars(cp));
                        sum += px * (c.trim().isEmpty() ? .3 : FoliaLumiereLayout.upright(c) ? 1 : .55) + px * spacing;
                        i += Character.charCount(cp);
                    }
                    return sum;
                }
                @Override public double spacing(double px) { return spacing * px; }
            };
            FoliaLumiereLayout.Variant[][] flow = FoliaLumiereLayout.flow(measure, glyphs, words, fixture.getDouble("hero"), fixture.getDouble("limit"), fixture.getDouble("limit"));
            JSONArray expected = fixture.getJSONArray("flow");
            for (int o = 0; o < 2; o++) for (int w = 0; w < 2; w++) {
                String at = "case " + f + "/" + o + "/" + w + ": " + fixture.getString("fullText");
                JSONObject a = expected.getJSONArray(o).getJSONObject(w); FoliaLumiereLayout.Variant b = flow[o][w];
                assertEquals(at + " lines", a.getInt("lines"), b.lines);
                assertEquals(at + " along", a.getDouble("along"), b.along, 1e-8);
                assertEquals(at + " across", a.getDouble("across"), b.across, 1e-8);
                assertEquals(at + " inkWidth", a.getDouble("inkWidth"), b.inkWidth, 1e-8);
                assertEquals(at + " inkHeight", a.getDouble("inkHeight"), b.inkHeight, 1e-8);
                JSONArray points = a.getJSONArray("points"), spots = a.getJSONArray("spots");
                for (int g = 0; g < glyphs.length; g++) {
                    assertEquals(at + " point x " + g, points.getJSONObject(g).getDouble("x"), b.points[g * 2], 1e-8);
                    assertEquals(at + " point y " + g, points.getJSONObject(g).getDouble("y"), b.points[g * 2 + 1], 1e-8);
                    assertEquals(at + " spot x " + g, spots.getJSONObject(g).getDouble("x"), b.spots[g * 2], 1e-8);
                    assertEquals(at + " spot y " + g, spots.getJSONObject(g).getDouble("y"), b.spots[g * 2 + 1], 1e-8);
                }
            }
        }
    }
}
