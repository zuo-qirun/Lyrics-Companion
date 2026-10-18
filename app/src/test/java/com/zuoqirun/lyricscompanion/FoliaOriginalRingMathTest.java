package com.zuoqirun.lyricscompanion;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import static org.junit.Assert.*;

public final class FoliaOriginalRingMathTest {
    @Test public void prefixCentresMatchActualPretextMeasurementsFromChromium() throws Exception {
        JSONArray cases;
        try (InputStream input = getClass().getResourceAsStream("/folia-claddagh-spacing-oracle.json")) {
            assertNotNull(input);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            for (int n; (n = input.read(buffer)) >= 0;) bytes.write(buffer, 0, n);
            cases = new JSONArray(bytes.toString("UTF-8"));
        }
        for (int c = 0; c < cases.length(); c++) {
            JSONObject row = cases.getJSONObject(c);
            JSONArray prefixes = row.getJSONArray("prefixes"), expected = row.getJSONArray("values");
            float[] natural = new float[prefixes.length()], offsets = new float[prefixes.length()];
            for (int i = 0; i < natural.length; i++) natural[i] = (float) prefixes.getDouble(i);
            double[] angles = new double[expected.length()];
            FoliaOriginalRingMath.measuredOffsets(natural, 72, 0, offsets);
            double fit = FoliaOriginalRingMath.angles(offsets, 1, 72, 352, angles);
            for (int i = 0; i < angles.length; i++) {
                assertEquals(row.getString("text") + ':' + i, expected.getJSONObject(i).getDouble("nominalAngle"), angles[i], 2e-7);
                assertEquals(expected.getJSONObject(i).getDouble("scaleFactor"), fit, 2e-7);
            }
        }
    }
}
