package com.zuoqirun.lyricscompanion;

import java.util.HashMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class OnboardingStateTest {
    @Test public void onlyAnEmptyInstallationStartsSetupAutomatically() {
        HashMap<String, Object> values = new HashMap<>();
        assertFalse(OnboardingState.existingInstallation(values));
        values.put(AppPreferences.KEY_MAIN_OVERLAY, false);
        assertTrue(OnboardingState.existingInstallation(values));
        assertFalse(ConfigurationCodec.shareableKey(OnboardingState.FINISHED));
        assertFalse(ConfigurationCodec.shareableKey(OnboardingState.STEP));
    }
}
