// SPDX-License-Identifier: AGPL-3.0-only
package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public final class FoliaLumiereWindowMathTest {
    @Test public void sourceWindowTransformsMatchAtAll936ViewportTypographyAndOverlapSamples() throws Exception {
        String source;
        try (InputStream input = getClass().getResourceAsStream("/folia_lumiere_window.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        JSONArray windows = new JSONObject(source).getJSONArray("windows");
        for (int fixtureIndex = 0; fixtureIndex < windows.length(); fixtureIndex++) {
            JSONObject fixture = windows.getJSONObject(fixtureIndex), region = fixture.getJSONObject("region");
            JSONArray inputLines = fixture.getJSONArray("lines"), kinds = fixture.getJSONArray("kinds");
            FoliaLumiereWindowMath.Line[] lines = new FoliaLumiereWindowMath.Line[inputLines.length()];
            int[] typography = new int[lines.length];
            for (int i = 0; i < lines.length; i++) {
                JSONObject input = inputLines.getJSONObject(i); FoliaLumiereWindowMath.Line line = new FoliaLumiereWindowMath.Line();
                line.start = input.getJSONObject("line").getDouble("startTime");
                line.jitter = input.getDouble("jitter"); line.vx = input.getJSONObject("velocity").getDouble("x");
                line.vy = input.getJSONObject("velocity").getDouble("y"); line.phase = input.getDouble("motionPhase");
                line.flow = new FoliaLumiereLayout.Variant[2][2];
                JSONArray flow = input.getJSONArray("flow");
                for (int o = 0; o < 2; o++) for (int w = 0; w < 2; w++) {
                    JSONObject variant = flow.getJSONArray(o).getJSONObject(w); FoliaLumiereLayout.Variant v = new FoliaLumiereLayout.Variant(0);
                    v.along = variant.getDouble("along"); v.across = variant.getDouble("across"); v.lines = variant.getInt("lines");
                    v.inkWidth = variant.getDouble("inkWidth"); v.inkHeight = variant.getDouble("inkHeight"); line.flow[o][w] = v;
                }
                JSONArray placements = input.getJSONArray("placements");
                for (int p = 0; p < 2; p++) {
                    JSONObject slot = placements.getJSONArray(p).getJSONObject(1);
                    line.free[p] = new FoliaLumiereWindowMath.Slot(slot.getDouble("dx"), slot.getDouble("dy"), slot.getDouble("scale"),
                            slot.getDouble("alpha"), slot.getDouble("orient"), slot.getDouble("rotation"));
                }
                lines[i] = line; typography[i] = "vertical".equals(kinds.getString(i)) ? 1 : "crossed".equals(kinds.getString(i)) ? 2 : 0;
            }
            FoliaLumiereWindowMath window = new FoliaLumiereWindowMath(new FoliaLumiereWindowMath.Source() {
                @Override public int size() { return lines.length; }
                @Override public double start(int i) { return lines[i].start; }
                @Override public FoliaLumiereWindowMath.Line line(int i) { return lines[i]; }
                @Override public int typography(int i) { return typography[i]; }
            }, fixture.getDouble("width"), fixture.getDouble("height"), region.getDouble("cx"), region.getDouble("cy"),
                    region.getDouble("w"), fixture.getDouble("hero"));
            JSONArray frames = fixture.getJSONArray("frames");
            for (int f = 0; f < frames.length(); f++) {
                JSONObject frame = frames.getJSONObject(f); JSONArray transforms = frame.getJSONArray("transforms");
                for (int line = 0; line < lines.length; line++) {
                    String at = "window " + fixtureIndex + " time " + frame.getDouble("time") + " line " + line;
                    JSONObject a = transforms.getJSONObject(line); FoliaLumiereWindowMath.Transform b = window.transform(line, frame.getDouble("time"));
                    assertEquals(at + " x", a.getDouble("x"), b.x, 1e-8); assertEquals(at + " y", a.getDouble("y"), b.y, 1e-8);
                    assertEquals(at + " scale", a.getDouble("scale"), b.scale, 1e-10); assertEquals(at + " rotation", a.getDouble("rotation"), b.rotation, 1e-10);
                    assertEquals(at + " alpha", a.getDouble("alpha"), b.alpha, 1e-10); assertEquals(at + " orient", a.getDouble("orient"), b.orient, 1e-10);
                    assertEquals(at + " wrap", a.getDouble("wrap"), b.wrap, 1e-10); assertEquals(at + " restOrient", a.getDouble("restOrient"), b.restOrient, 1e-10);
                    assertEquals(at + " restWrap", a.getDouble("restWrap"), b.restWrap, 1e-10);
                    assertEquals(at + " move count", a.getJSONArray("moves").length(), b.moves.size());
                }
            }
        }
    }
}
