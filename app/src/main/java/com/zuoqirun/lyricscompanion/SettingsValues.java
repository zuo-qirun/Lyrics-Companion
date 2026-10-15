package com.zuoqirun.lyricscompanion;

import android.content.Context;

/** Reads effective preferences through the same screen/style helpers as the renderer. */
final class SettingsValues implements SettingsCatalog.Values {
    private final Context context;
    private final boolean secondary;

    SettingsValues(Context context, boolean secondary) {
        this.context = context;
        this.secondary = secondary;
    }

    @Override public boolean enabled(String key) {
        switch (key) {
            case AppPreferences.KEY_SPECTRUM_ENABLED:
                return AppPreferences.spectrumEnabled(context, secondary);
            case AppPreferences.KEY_ISLAND_AUTO_ROWS:
                return AppPreferences.islandMainRowPercent(context, secondary) == 0;
            case AppPreferences.KEY_CURRENT_LYRIC_OUTLINE:
                return AppPreferences.lyricOutline(context, secondary, true);
            case AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE:
                return AppPreferences.lyricOutline(context, secondary, false);
            case AppPreferences.KEY_LIVE_LYRIC_ANIMATION:
                return AppPreferences.liveLyricAnimation(context, secondary);
            case AppPreferences.KEY_OVERLAY_ALLOW_OFFSCREEN:
                return AppPreferences.overlayAllowOffscreen(context, secondary);
            case AppPreferences.KEY_PREVIOUS_LYRIC_PARTICLES:
                return AppPreferences.previousLyricParticles(context, secondary);
            case AppPreferences.KEY_COVER_ROTATION:
                return AppPreferences.coverRotation(context, secondary);
            case AppPreferences.KEY_MATCHING_ANIMATION:
                return AppPreferences.matchingAnimation(context, secondary);
            case AppPreferences.KEY_HIDE_MATCHING_PLACEHOLDER:
                return AppPreferences.hideMatchingPlaceholder(context, secondary);
            case AppPreferences.KEY_PLAYBACK_FADE:
                return AppPreferences.get(context).getBoolean(key, false);
            default:
                return AppPreferences.displayBoolean(context, secondary, key, false);
        }
    }

    @Override public String choice(String key) {
        if (AppPreferences.KEY_REFINED_BACKGROUND_TYPE.equals(key)) {
            return AppPreferences.refinedBackgroundType(context, secondary);
        }
        if (AppPreferences.KEY_LYRIC_CACHE_POLICY.equals(key)) return AppPreferences.lyricCachePolicy(context);
        return AppPreferences.displayString(context, secondary, key, "none");
    }
}
