package com.zuoqirun.lyricscompanion;

/** Shared limits and geometry for the strip editor and its actual overlay window. */
final class TopLyricLayout {
    static final int MIN_REGION_PERCENT = 10;

    private TopLyricLayout() { }

    static int regionPercent(int value) {
        return Math.max(MIN_REGION_PERCENT, Math.min(100, value));
    }

    static int width(int screenWidth, int percent, int leftMargin, int rightMargin) {
        int available = Math.max(1, screenWidth - Math.max(0, leftMargin)
                - Math.max(0, rightMargin));
        return Math.min(available, Math.max(1, screenWidth * regionPercent(percent) / 100));
    }

    static int x(int screenWidth, int width, int leftMargin, int rightMargin,
                 String align, int offset) {
        int left = Math.min(Math.max(0, leftMargin), Math.max(0, screenWidth - width));
        int right = Math.max(left, screenWidth - width - Math.max(0, rightMargin));
        int origin = "left".equals(align) ? left : "right".equals(align) ? right
                : left + (right - left) / 2;
        return Math.max(left, Math.min(right, origin + offset));
    }

    static boolean secondRow(String rows, boolean statusAligned, boolean hasSecondary) {
        if (statusAligned || "single".equals(rows)) return false;
        return !"auto".equals(rows) || hasSecondary;
    }
}
