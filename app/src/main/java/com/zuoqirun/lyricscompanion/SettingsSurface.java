package com.zuoqirun.lyricscompanion;

import android.os.Build;
import android.view.View;
import com.google.android.material.shape.MaterialShapeDrawable;

/** Light card definition, with a flat low-resource fallback and no extra bitmap effects. */
final class SettingsSurface {
    private SettingsSurface() { }
    static void apply(View view) {
        float density = view.getResources().getDisplayMetrics().density;
        boolean low = AppPreferences.lowResourceMode(view.getContext());
        if (view.getBackground() instanceof MaterialShapeDrawable) {
            MaterialShapeDrawable surface = (MaterialShapeDrawable) view.getBackground();
            surface.setStroke(Math.max(1f, density * .5f), 0xFF2B3A50);
            surface.setElevation(low ? 0f : density * 2f);
        }
        if (Build.VERSION.SDK_INT >= 21) view.setElevation(low ? 0f : density * 2f);
    }
}
