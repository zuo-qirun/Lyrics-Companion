package com.zuoqirun.lyricscompanion;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConfigurationCodecTest {
    @Test public void privacySensitiveAndRuntimeKeysAreNotShareable() {
        assertFalse(ConfigurationCodec.shareableKey(AppPreferences.KEY_COMMUNITY_CLIENT_ID));
        assertFalse(ConfigurationCodec.shareableKey(AppPreferences.KEY_FEEDBACK_TICKETS));
        assertFalse(ConfigurationCodec.shareableKey(AppPreferences.KEY_LOCAL_LYRIC_DIRECTORY_URI));
        assertFalse(ConfigurationCodec.shareableKey(AppPreferences.KEY_CUSTOM_FONT_FILE));
        assertFalse(ConfigurationCodec.shareableKey(AppPreferences.KEY_SERVICE_STOPPED_BY_USER));
        assertFalse(ConfigurationCodec.shareableKey("player_package_lyric_catalog_secret"));
    }

    @Test public void visualAndFeatureSettingsAreShareable() {
        assertTrue(ConfigurationCodec.shareableKey(AppPreferences.KEY_MAIN_OVERLAY_STYLE));
        assertTrue(ConfigurationCodec.shareableKey(AppPreferences.KEY_LYRIC_COLOR + "_main"));
        assertTrue(ConfigurationCodec.shareableKey(AppPreferences.KEY_BOTTOM_SPECTRUM));
        assertTrue(ConfigurationCodec.shareableKey(AppPreferences.KEY_AVRCP_ENABLED));
    }
}
