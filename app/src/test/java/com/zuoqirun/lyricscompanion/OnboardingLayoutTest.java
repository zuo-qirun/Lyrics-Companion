package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class OnboardingLayoutTest {
    @Test public void landscapeSidebarsKeepTheCardBesideTheHighlight() {
        OnboardingLayout.Placement left = place(1200, 600, 0, 0, 120, 600);
        assertEquals(OnboardingLayout.RIGHT, left.side);
        assertTrue(left.left >= 132);
        OnboardingLayout.Placement right = place(1200, 600, 1080, 0, 1200, 600);
        assertEquals(OnboardingLayout.LEFT, right.side);
        assertTrue(right.left + right.width <= 1068);
    }

    @Test public void portraitBottomNavigationGetsACardAboveIt() {
        OnboardingLayout.Placement card = place(400, 800, 0, 728, 400, 800);
        assertEquals(OnboardingLayout.ABOVE, card.side);
        assertTrue(card.top + card.height <= 716);
    }

    @Test public void fullWidthRowsChooseVerticalSpaceAndClampToViewport() {
        OnboardingLayout.Placement card = place(400, 800, 20, 24, 380, 80);
        assertEquals(OnboardingLayout.BELOW, card.side);
        assertTrue(card.top >= 92);
        inside(card, 400, 800);
    }

    @Test public void shortScreensReduceTheScrollableCardWithoutCoveringTheTarget() {
        OnboardingLayout.Placement card = place(640, 360, 20, 130, 620, 250);
        assertEquals(OnboardingLayout.ABOVE, card.side);
        assertEquals(106, card.height);
        assertTrue(card.top + card.height <= 118);
        inside(card, 640, 360);
    }

    @Test public void missingOrClippedTargetsNeverPushControlsOutsideTheWindow() {
        for (int[] viewport : new int[][]{{320, 240}, {400, 800}, {1000, 360}, {1, 1}}) {
            OnboardingLayout.Placement card = place(viewport[0], viewport[1], 0, 0, 0, 0);
            inside(card, viewport[0], viewport[1]);
            assertEquals(OnboardingLayout.CENTER, card.side);
        }
        inside(place(400, 800, -500, -200, 100, 200), 400, 800);
    }

    @Test public void staleStepsStayWithinTheActualTour() {
        assertEquals(0, OnboardingTour.normalizeStep(-10));
        assertEquals(OnboardingTour.STEP_COUNT - 1, OnboardingTour.normalizeStep(100));
        assertEquals(4, OnboardingTour.normalizeStep(4));
    }

    private OnboardingLayout.Placement place(int width, int height, int left, int top, int right, int bottom) {
        return OnboardingLayout.place(width, height, left, top, right, bottom, 360, 260, 12, 12);
    }

    private void inside(OnboardingLayout.Placement card, int width, int height) {
        assertTrue(card.width > 0 && card.height > 0);
        assertTrue(card.left >= 0 && card.top >= 0);
        assertTrue(card.left + card.width <= width);
        assertTrue(card.top + card.height <= height);
    }
}
