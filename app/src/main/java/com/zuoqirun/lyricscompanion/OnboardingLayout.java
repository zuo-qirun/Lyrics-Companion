package com.zuoqirun.lyricscompanion;

/** Places a coach mark beside its target, with a shorter scrollable card on small screens. */
final class OnboardingLayout {
    static final int CENTER = 0, RIGHT = 1, LEFT = 2, BELOW = 3, ABOVE = 4;
    static final class Placement {
        final int left, top, width, height, side;
        Placement(int left, int top, int width, int height, int side) {
            this.left = left; this.top = top; this.width = width; this.height = height; this.side = side;
        }
    }
    private OnboardingLayout() { }

    static Placement place(int viewportWidth, int viewportHeight, int targetLeft, int targetTop,
                           int targetRight, int targetBottom, int desiredWidth, int desiredHeight,
                           int requestedMargin, int requestedGap) {
        int width = Math.max(1, viewportWidth), height = Math.max(1, viewportHeight);
        int margin = Math.min(Math.max(0, requestedMargin), Math.max(0, (Math.min(width, height) - 1) / 2));
        int gap = Math.max(0, requestedGap);
        int cardWidth = Math.max(1, Math.min(desiredWidth, width - margin * 2));
        int cardHeight = Math.max(1, Math.min(desiredHeight, height - margin * 2));
        int left = clamp(targetLeft, 0, width), right = clamp(targetRight, 0, width);
        int top = clamp(targetTop, 0, height), bottom = clamp(targetBottom, 0, height);
        if (right <= left || bottom <= top) {
            return new Placement((width - cardWidth) / 2, (height - cardHeight) / 2, cardWidth, cardHeight, CENTER);
        }
        int after = width - margin - right - gap, before = left - gap - margin;
        int centerX = left + (right - left) / 2, centerY = top + (bottom - top) / 2;
        if (after >= cardWidth || before >= cardWidth) {
            boolean useRight = after >= cardWidth && (after >= before || before < cardWidth);
            return new Placement(useRight ? right + gap : left - gap - cardWidth,
                    clamp(centerY - cardHeight / 2, margin, height - margin - cardHeight),
                    cardWidth, cardHeight, useRight ? RIGHT : LEFT);
        }
        int below = Math.max(0, height - margin - bottom - gap);
        int above = Math.max(0, top - gap - margin);
        if (below > 0 || above > 0) {
            boolean useBelow = below >= cardHeight || above < cardHeight && below >= above;
            cardHeight = Math.max(1, Math.min(cardHeight, useBelow ? below : above));
            return new Placement(clamp(centerX - cardWidth / 2, margin, width - margin - cardWidth),
                    useBelow ? bottom + gap : top - gap - cardHeight,
                    cardWidth, cardHeight, useBelow ? BELOW : ABOVE);
        }
        return new Placement((width - cardWidth) / 2, (height - cardHeight) / 2, cardWidth, cardHeight, CENTER);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(Math.max(minimum, maximum), value));
    }
}
