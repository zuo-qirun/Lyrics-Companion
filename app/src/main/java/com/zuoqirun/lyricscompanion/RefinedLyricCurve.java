package com.zuoqirun.lyricscompanion;

/**
 * Refined Now Playing's curved lyric transform, adapted to Canvas coordinates.
 * The transform origin is the left-center of a lyric line.
 */
final class RefinedLyricCurve {
    private RefinedLyricCurve() {}

    static Transform calculate(float yOffsetPx, float lineHeightPx,
                               float viewportHeightPx, float density,
                               float curvatureDegrees) {
        if (!Float.isFinite(yOffsetPx) || !Float.isFinite(lineHeightPx)
                || !Float.isFinite(viewportHeightPx) || viewportHeightPx <= 0f) {
            return Transform.IDENTITY;
        }
        float safeDensity = Math.max(0.01f, density);
        float curvature = Math.max(10f, Math.min(80f, curvatureDegrees));

        // Matches Refined Now Playing's setRotateTransform geometry. Its CSS uses
        // transform-origin: left center; translateX/translateY/scale/rotate.
        float originX = (-120f + (curvature - 25f)) * safeDensity;
        float originY = -(yOffsetPx + lineHeightPx / 2f);
        double length = Math.hypot(originX, originY);
        float rotation = Math.min(yOffsetPx / viewportHeightPx * -curvature, 90f);
        double angle = Math.toRadians(rotation)
                + Math.atan2(originY, originX);
        float translationY = (float) (Math.sin(angle) * length - originY);
        float translationX = (float) (Math.cos(angle) * length - originX);

        double distance = Math.abs(yOffsetPx * 2f / viewportHeightPx);
        float opacity = (float) Math.max(1d - Math.pow(distance, 1.15d) * 1.2d, 0d);
        return new Transform(translationX, translationY, rotation, opacity);
    }

    static final class Transform {
        static final Transform IDENTITY = new Transform(0f, 0f, 0f, 1f);

        final float translationX;
        final float translationY;
        final float rotationDegrees;
        final float opacity;

        Transform(float translationX, float translationY,
                  float rotationDegrees, float opacity) {
            this.translationX = translationX;
            this.translationY = translationY;
            this.rotationDegrees = rotationDegrees;
            this.opacity = opacity;
        }
    }
}
