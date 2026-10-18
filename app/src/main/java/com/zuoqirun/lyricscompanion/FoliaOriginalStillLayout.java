// SPDX-License-Identifier: AGPL-3.0-only
// VisualizerStill.tsx flex layout, Folia 9279251. See third_party/folia/NOTICE.md.
package com.zuoqirun.lyricscompanion;

import java.util.List;

final class FoliaOriginalStillLayout {
    private FoliaOriginalStillLayout() { }

    /** Source CSS scales each text box after flex layout, so font scale never changes row positions. */
    static float[] lyricCenters(List<LrcTimeline.Line> lines, int current, float height, boolean translation) {
        float[] heights = new float[3], textHeights = new float[3];
        float total = 64; // two gap-8 gaps
        for (int row = 0; row < 3; row++) {
            int index = current + row - 1;
            if (index < 0 || index >= lines.size()) { heights[row] = 80; total += 80; continue; }
            LrcTimeline.Line line = lines.get(index);
            textHeights[row] = line.text.isEmpty() ? 0 : row == 1 ? 50 : 36;
            heights[row] = textHeights[row];
            if (translation && !line.translated.isEmpty()) heights[row] += row == 1 ? 44 : 36;
            total += heights[row];
        }
        float top = (height - 64 - total) / 2; // pb-16 precedes justify-center
        float[] centers = new float[3];
        for (int row = 0; row < 3; row++) {
            centers[row] = top + textHeights[row] / 2;
            top += heights[row] + 32;
        }
        return centers;
    }
}
