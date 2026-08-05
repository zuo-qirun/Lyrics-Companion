package com.zuoqirun.lyricscompanion;

/** Converts word timing into a Unicode-safe, continuously moving text boundary. */
final class KaraokeProgress {
    private KaraokeProgress() {}

    static Boundary boundary(String value, int progressPermille) {
        if (value == null || value.isEmpty() || progressPermille <= 0) {
            return Boundary.EMPTY;
        }
        int codePointCount = value.codePointCount(0, value.length());
        int clampedProgress = Math.min(1000, progressPermille);
        long scaledProgress = (long) codePointCount * clampedProgress;
        int completedCodePoints = (int) (scaledProgress / 1000L);
        if (completedCodePoints >= codePointCount) {
            return new Boundary(value.length(), value.length(), 0f);
        }
        int completeEnd = value.offsetByCodePoints(0, completedCodePoints);
        int partialEnd = value.offsetByCodePoints(completeEnd, 1);
        return new Boundary(completeEnd, partialEnd,
                (scaledProgress % 1000L) / 1000f);
    }

    static final class Boundary {
        static final Boundary EMPTY = new Boundary(0, 0, 0f);

        final int completeEnd;
        final int partialEnd;
        final float partialFraction;

        Boundary(int completeEnd, int partialEnd, float partialFraction) {
            this.completeEnd = completeEnd;
            this.partialEnd = partialEnd;
            this.partialFraction = partialFraction;
        }
    }
}
