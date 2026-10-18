// SPDX-License-Identifier: AGPL-3.0-only
package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import static org.junit.Assert.*;

/** Executes compiled native formulas against the pinned original, including unrecorded dimensions. */
public class FoliaTemperaGeometryTest {
    @Test public void arraySliceToleratesReversedAndOutOfRangeBounds() {
        java.util.List<Object> two = new java.util.ArrayList<>(java.util.Arrays.asList("first", "second"));
        assertEquals(0, FoliaTemperaGeometryOps.list(
                FoliaTemperaGeometryOps.method(two, "slice", 2d, -1d)).size());
        assertEquals(0, FoliaTemperaGeometryOps.list(
                FoliaTemperaGeometryOps.method(two, "slice", 2d)).size());
        assertEquals(1, FoliaTemperaGeometryOps.list(
                FoliaTemperaGeometryOps.method(two, "slice", -1d)).size());
        assertEquals(0, FoliaTemperaGeometryOps.list(
                FoliaTemperaGeometryOps.method(two, "slice", 5d, 9d)).size());
        assertEquals(0, FoliaTemperaGeometryOps.list(
                FoliaTemperaGeometryOps.method(two, "slice", -9d, -5d)).size());
        assertEquals(2, FoliaTemperaGeometryOps.list(
                FoliaTemperaGeometryOps.method(two, "slice", -9d, 9d)).size());
    }

    @Test public void everyOriginalCompositionMatchesEveryPathAndMotionParameter() throws Exception {
        JSONArray cases = fixture().getJSONArray("cases");
        assertEquals(605, cases.length());
        Set<String> kinds = new HashSet<>();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject item = cases.getJSONObject(i); kinds.add(item.getString("kind"));
            JSONArray actual = FoliaTemperaGeometryOps.generate(item.getString("kind"), item.getDouble("width"),
                    item.getDouble("height"), item.getLong("seed"), item.getDouble("flowAngle"),
                    item.getString("motif"), item.getDouble("hatchAngle"), item.getInt("crossCount"),
                    item.getLong("scribbleSeed"), item.getBoolean("showDecor"));
            compare(item.getString("kind") + " case " + (i % 5), item.getJSONArray("nodes"), actual);
        }
        assertEquals(121, kinds.size());
    }

    @Test public void fullProgramMatchesUpstreamChunksChoicesBridgesDecorAndTransitions() throws Exception {
        JSONArray programs = fixture().getJSONArray("programs");
        assertEquals(8, programs.length());
        for (int i = 0; i < programs.length(); i++) {
            JSONObject item = programs.getJSONObject(i);
            Object nativeLines = FoliaTemperaGeometryOps.fromJson(item.getJSONArray("lines"));
            Object actual = FoliaTemperaGeometryProgram.compile(nativeLines, item.getString("seed"), item.getBoolean("wholeLine"));
            compare("program " + i, item.getJSONObject("expected"), FoliaTemperaGeometryOps.json(actual));
        }
    }

    private JSONObject fixture() throws Exception {
        String fixture;
        try (InputStream input = new GZIPInputStream(getClass().getResourceAsStream("/folia_tempera_geometry_reference.json.gz"));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384]; int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            fixture = output.toString("UTF-8");
        }
        return new JSONObject(fixture);
    }

    @Test public void nativeTypesetterMatchesAllOriginalProfilesAndSettlingPaces() throws Exception {
        JSONArray layouts = fixture().getJSONArray("layouts");
        assertEquals(363, layouts.length());
        for (int i = 0; i < layouts.length(); i++) {
            JSONObject item = layouts.getJSONObject(i);
            Object options = FoliaTemperaGeometryOps.fromJson(item.getJSONObject("options"));
            FoliaTemperaGeometryOps.Fn measure = values -> String.valueOf(values[0]).length() * FoliaTemperaGeometryOps.number(values[1]) * .6;
            FoliaTemperaGeometryOps.put(options, "fontFamily", FoliaTemperaGeometryOps.object("measure", measure));
            compare("layout " + i, item.getJSONArray("expected"), FoliaTemperaGeometryOps.json(FoliaTemperaGeometryProgram.layout(options)));
        }
    }

    private static void compare(String path, Object expected, Object actual) throws Exception {
        if (expected instanceof JSONObject) {
            assertTrue(path, actual instanceof JSONObject);
            JSONObject left = (JSONObject) expected, right = (JSONObject) actual;
            assertEquals(path + " field count", left.length(), right.length());
            for (java.util.Iterator<String> keys = left.keys(); keys.hasNext();) {
                String key = keys.next();
                assertTrue(path + "." + key + " missing", right.has(key));
                compare(path + "." + key, left.get(key), right.get(key));
            }
        } else if (expected instanceof JSONArray) {
            assertTrue(path, actual instanceof JSONArray);
            JSONArray left = (JSONArray) expected, right = (JSONArray) actual;
            assertEquals(path + " length", left.length(), right.length());
            for (int i = 0; i < left.length(); i++) compare(path + "[" + i + "]", left.get(i), right.get(i));
        } else if (expected instanceof Number) {
            assertTrue(path, actual instanceof Number);
            assertEquals(path, ((Number) expected).doubleValue(), ((Number) actual).doubleValue(), .000002);
        } else assertEquals(path, expected, actual);
    }
}
