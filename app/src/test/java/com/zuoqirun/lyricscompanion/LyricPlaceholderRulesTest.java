package com.zuoqirun.lyricscompanion;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LyricPlaceholderRulesTest {
    @Test public void onlyAnActivePendingMatchCanBeBlanked() {
        assertTrue(LyricPlaceholderRules.blankMatching(true, true, false, false));
        assertFalse(LyricPlaceholderRules.blankMatching(true, false, false, false));
        assertFalse(LyricPlaceholderRules.blankMatching(true, true, true, false));
        assertFalse(LyricPlaceholderRules.blankMatching(true, true, false, true));
        assertFalse(LyricPlaceholderRules.blankMatching(true, true, true, true));
    }
    @Test public void defaultDisabledPreservesTheExistingMatchingPrompt() {
        assertFalse(LyricPlaceholderRules.blankMatching(false, true, false, false));
    }
}
