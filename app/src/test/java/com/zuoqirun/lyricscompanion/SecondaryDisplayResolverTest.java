package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public class SecondaryDisplayResolverTest {
    @Test public void missingRememberedIdFallsBackWithoutChangingThePreference() {
        assertEquals(19, SecondaryDisplayResolver.selectId(17, new int[]{19}, new int[]{0, 19}));
        assertEquals(9, SecondaryDisplayResolver.selectId(17, new int[0], new int[]{0, 9}));
        assertEquals(-1, SecondaryDisplayResolver.selectId(17, new int[0], new int[]{0}));
    }

    @Test public void exactIdWinsEvenWhenTwoRealPanelsHaveTheSameName() {
        assertEquals(2, SecondaryDisplayResolver.selectId(2, new int[]{1, 2}, new int[]{0, 1, 2}));
        assertEquals(1, SecondaryDisplayResolver.selectId(-1, new int[]{0, 1, 2}, new int[]{0, 1, 2}));
    }
}
