package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Map;

/** Installation-local progress; exported appearance configurations cannot skip setup. */
final class OnboardingState {
    static final String FINISHED = "onboarding_finished_v1";
    static final String STEP = "onboarding_step";
    private OnboardingState() { }

    static void initialize(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(AppPreferences.FILE, Context.MODE_PRIVATE);
        if (!preferences.contains(FINISHED)) {
            preferences.edit().putBoolean(FINISHED, existingInstallation(preferences.getAll())).apply();
        }
    }

    static boolean existingInstallation(Map<String, ?> values) {
        return !values.isEmpty();
    }

    static boolean pending(Context context) {
        return !AppPreferences.get(context).getBoolean(FINISHED, false);
    }
}
