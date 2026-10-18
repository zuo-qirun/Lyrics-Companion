// SPDX-License-Identifier: AGPL-3.0-only
// Original claddaghLayout.ts measured-prefix centre calculation (Folia 9279251).
// Copyright chthollyphile and Folia contributors. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

final class FoliaOriginalRingMath {
    private FoliaOriginalRingMath() { }

    static void measuredOffsets(float[] naturalPrefixes, double fontSize, double extraTracking, float[] destination) {
        destination[0] = 0;
        // pretext's natural line width includes the CSS tracking after its last grapheme.
        // The separate 0.18em tracking only occurs between prefixes in claddaghLayout.ts.
        for (int i = 1; i < destination.length; i++) {
            destination[i] = (float) Math.max(destination[i - 1], naturalPrefixes[i] + fontSize * 0.04
                    + (i - 1) * (fontSize * 0.22 + extraTracking));
        }
    }

    static double angles(float[] measuredOffsets, double spacingScale, double fontSize,
                         double radius, double[] destination) {
        double safeScale = Math.max(0.1, spacingScale);
        double safeRadius = Math.max(1, Math.max(radius, fontSize * 2));
        double span = measuredOffsets[measuredOffsets.length - 1] * safeScale / safeRadius;
        double fit = span > 4.25 ? 4.25 / span : 1;
        for (int i = 0; i < destination.length; i++) {
            double center = ((double) measuredOffsets[i] + measuredOffsets[i + 1]) / 2.0 * safeScale;
            destination[i] = (center / safeRadius - span / 2) * fit;
        }
        return fit;
    }
}
