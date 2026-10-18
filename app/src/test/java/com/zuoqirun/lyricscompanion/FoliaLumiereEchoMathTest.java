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

public final class FoliaLumiereEchoMathTest {
    @Test public void wordFragmentsAndEachScatteredGlyphMatchOriginalRuntimeAcrossSongsAndViewports() throws Exception {
        String source;
        try (InputStream input = getClass().getResourceAsStream("/folia_lumiere_echo.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        JSONArray fixtures = new JSONObject(source).getJSONArray("cases");
        for (int c = 0; c < fixtures.length(); c++) {
            JSONObject fixture = fixtures.getJSONObject(c); JSONArray inputs = fixture.getJSONArray("inputs");
            List<FoliaLumiereEchoMath.Input> lines = new ArrayList<>();
            for (int i = 0; i < inputs.length(); i++) {
                JSONObject data = inputs.getJSONObject(i); FoliaLumiereEchoMath.Input line = new FoliaLumiereEchoMath.Input();
                line.glyphs = new String[data.getJSONArray("glyphs").length()]; line.starts = new double[line.glyphs.length]; line.offsets = new double[line.glyphs.length + 1];
                for (int g = 0; g < line.glyphs.length; g++) { line.glyphs[g] = data.getJSONArray("glyphs").getString(g); line.starts[g] = data.getJSONArray("starts").getDouble(g); }
                for (int g = 0; g < line.offsets.length; g++) line.offsets[g] = data.getJSONArray("offsets").getDouble(g);
                line.words = new ArrayList<>(); JSONArray words = data.getJSONArray("words");
                for (int w = 0; w < words.length(); w++) line.words.add(new int[]{words.getJSONArray(w).getInt(0), words.getJSONArray(w).getInt(1)});
                lines.add(line);
            }
            List<FoliaLumiereEchoMath.Fragment> fragments = FoliaLumiereEchoMath.build(lines, fixture.getString("seed"));
            JSONObject beam = fixture.getJSONObject("beam"); JSONArray frames = fixture.getJSONArray("frames");
            FoliaLumiereEchoMath.Frame frame = new FoliaLumiereEchoMath.Frame();
            for (int i = 0; i < frames.length(); i++) {
                JSONObject expected = frames.getJSONObject(i); JSONArray holders = expected.getJSONArray("holders");
                assertEquals(holders.length(), fragments.size());
                for (int f = 0; f < fragments.size(); f++) {
                    FoliaLumiereEchoMath.Fragment fragment = fragments.get(f);
                    FoliaLumiereEchoMath.frame(frame, fragment, expected.getDouble("time"), fixture.getDouble("height"), beam.getDouble("ox"), beam.getDouble("oy"),
                            beam.getDouble("dx"), beam.getDouble("dy"), beam.getDouble("halfWidth"), beam.getDouble("tanSpread"));
                    JSONObject holder = holders.getJSONObject(f); String at = "case " + c + " time " + expected.getDouble("time") + " fragment " + f;
                    assertEquals(at, holder.getBoolean("visible"), frame.presence > .003);
                    if (!holder.getBoolean("visible")) continue;
                    assertEquals(at + " x", holder.getDouble("x"), frame.x, 1e-8); assertEquals(at + " y", holder.getDouble("y"), frame.y, 1e-8);
                    assertEquals(at + " rotation", holder.getDouble("rotation"), frame.rotation, 1e-10);
                    assertEquals(at + " scaleX", holder.getDouble("scaleX"), frame.scaleX, 1e-10); assertEquals(at + " scaleY", holder.getDouble("scaleY"), frame.scaleY, 1e-10);
                    assertEquals(at + " skew", holder.getDouble("skew"), frame.skew, 1e-10);
                    JSONArray glyphs = holder.getJSONArray("glyphs"); assertEquals(fragment.glyphs.length, glyphs.length());
                    for (int g = 0; g < fragment.glyphs.length; g++) {
                        FoliaLumiereEchoMath.Glyph glyph = fragment.glyphs[g]; JSONObject a = glyphs.getJSONObject(g);
                        assertEquals(at + " glyph x " + g, a.getDouble("x"), glyph.offset + glyph.dx * fixture.getDouble("font") * frame.split * glyph.speed, 1e-8);
                        assertEquals(at + " glyph y " + g, a.getDouble("y"), glyph.dy * fixture.getDouble("font") * frame.split * glyph.speed, 1e-8);
                        assertEquals(at + " glyph rotation " + g, a.getDouble("rotation"), glyph.spin * frame.split * 4, 1e-10);
                    }
                }
            }
        }
    }
}
