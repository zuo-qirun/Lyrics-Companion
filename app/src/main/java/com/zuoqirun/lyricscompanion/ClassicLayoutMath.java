package com.zuoqirun.lyricscompanion;

/** Pure layout calculations for the classic lyrics card. */
final class ClassicLayoutMath {
    private ClassicLayoutMath() { }

    static float contentScale(float width, float height, float density) {
        float safeDensity = Math.max(0.01f, density);
        float referenceArea = 390f * 226f * safeDensity * safeDensity;
        float areaScale = (float) Math.sqrt(Math.max(0.01f,
                width * height / referenceArea));
        float heightScale = Math.max(0.01f, height / (226f * safeDensity));
        return Math.min(areaScale, heightScale);
    }

    static float constrainedTextScale(float requested, float density, float unit,
                                      float titleScale, float nextScale,
                                      float statusBaseline, float titleBaseline,
                                      float previousBaseline, float currentBaseline,
                                      float translationBaseline, float nextBaseline,
                                      boolean hasTranslation) {
        float safeDensityUnit = Math.max(0.01f, density * unit);
        float limit = Math.max(0.45f, requested);

        // Approximate Android font metrics conservatively: ascent is about 82% of text size
        // and descent about 25%. Keeping those extents apart prevents glyphs from colliding.
        limit = Math.min(limit, adjacentLimit(previousBaseline - titleBaseline,
                15f * titleScale, 12f, safeDensityUnit, true));
        limit = Math.min(limit, adjacentLimit(currentBaseline - previousBaseline,
                12f, 22f, safeDensityUnit, false));
        if (hasTranslation) {
            limit = Math.min(limit, adjacentLimit(translationBaseline - currentBaseline,
                    22f, 12f, safeDensityUnit, false));
            limit = Math.min(limit, adjacentLimit(nextBaseline - translationBaseline,
                    12f, 22f * nextScale, safeDensityUnit, false));
        } else {
            limit = Math.min(limit, adjacentLimit(nextBaseline - currentBaseline,
                    22f, 22f * nextScale, safeDensityUnit, false));
        }

        float titleAscent = 0.82f * 15f * Math.max(0.5f, titleScale) * safeDensityUnit;
        float statusRoom = titleBaseline - statusBaseline - titleAscent;
        if (statusRoom > 0f) {
            limit = Math.min(limit, statusRoom / (0.25f * 11f * safeDensityUnit));
        }
        return Math.max(0.45f, Math.min(requested, limit));
    }

    private static float adjacentLimit(float baselineGap, float upperSize,
                                       float lowerSize, float densityUnit,
                                       boolean upperIsFixed) {
        float safeGap = Math.max(0f, baselineGap - 2f * densityUnit);
        if (upperIsFixed) {
            float fixedDescent = 0.25f * upperSize * densityUnit;
            return Math.max(0f, safeGap - fixedDescent)
                    / Math.max(0.01f, 0.82f * lowerSize * densityUnit);
        }
        float extent = 0.25f * upperSize + 0.82f * lowerSize;
        return safeGap / Math.max(0.01f, extent * densityUnit);
    }
}
