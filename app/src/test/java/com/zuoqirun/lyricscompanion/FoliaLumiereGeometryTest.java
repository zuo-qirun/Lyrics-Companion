// SPDX-License-Identifier: AGPL-3.0-only
package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;

import static org.junit.Assert.*;

/** Full unmodified TypeScript outputs, including every point and random consumption order. */
public final class FoliaLumiereGeometryTest {
    @Test public void allHundredRecipesMatchSourceAtThreeArbitraryViewportsAndSongSeeds() throws Exception {
        String source;
        try (InputStream raw = getClass().getResourceAsStream("/folia_lumiere_geometry.json.gz");
             GZIPInputStream input = new GZIPInputStream(raw);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            source = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
        }
        JSONArray cases = new JSONObject(source).getJSONArray("cases");
        assertEquals(300, cases.length());
        for (int i = 0; i < cases.length(); i++) {
            JSONObject fixture = cases.getJSONObject(i);
            JSONObject actual = FoliaLumiereGeometry.build(fixture.getInt("index"), fixture.getDouble("aspect"),
                    fixture.getString("seed"), fixture.getInt("shot"));
            same(fixture.getJSONObject("rig"), actual.getJSONObject("rig"), "case " + i + ".rig");
            same(fixture.getJSONObject("art"), actual.getJSONObject("art"), "case " + i + ".art");
        }
    }

    private static void same(Object expected, Object actual, String at) throws Exception {
        if (expected instanceof JSONObject) {
            assertTrue(at, actual instanceof JSONObject);
            JSONObject a = (JSONObject) expected, b = (JSONObject) actual;
            assertEquals(at + " property count", a.length(), b.length());
            for (Iterator<String> keys = a.keys(); keys.hasNext();) {
                String key = keys.next();
                assertTrue(at + "." + key, b.has(key));
                same(a.get(key), b.get(key), at + "." + key);
            }
        } else if (expected instanceof JSONArray) {
            assertTrue(at, actual instanceof JSONArray);
            JSONArray a = (JSONArray) expected, b = (JSONArray) actual;
            assertEquals(at + " array length", a.length(), b.length());
            for (int i = 0; i < a.length(); i++) same(a.get(i), b.get(i), at + "[" + i + "]");
        } else if (expected instanceof Number) {
            assertTrue(at, actual instanceof Number);
            assertEquals(at, ((Number) expected).doubleValue(), ((Number) actual).doubleValue(), 1e-10);
        } else assertEquals(at, expected, actual);
    }
}
