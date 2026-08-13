package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.List;

/** Keeps spinner text and surfaces readable across system, app and vendor themes. */
final class ThemedSpinnerAdapter<T> extends ArrayAdapter<T> {
    ThemedSpinnerAdapter(Context context, T[] values) {
        super(context, android.R.layout.simple_spinner_dropdown_item, values);
    }

    ThemedSpinnerAdapter(Context context, List<T> values) {
        super(context, android.R.layout.simple_spinner_dropdown_item, values);
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        return style((TextView) super.getView(position, convertView, parent), false);
    }

    @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return style((TextView) super.getDropDownView(position, convertView, parent), true);
    }

    private TextView style(TextView view, boolean dropdown) {
        int onSurface = themeColor(com.google.android.material.R.attr.colorOnSurface,
                0xFF17212E, 0xFFE1E3E7);
        int surface = themeColor(dropdown
                        ? com.google.android.material.R.attr.colorSurface
                        : com.google.android.material.R.attr.colorSurfaceContainer,
                dropdown ? 0xFFF7F9FC : 0xFFEAF0F6,
                dropdown ? 0xFF101418 : 0xFF1C2024);
        view.setTextColor(onSurface);
        view.setBackgroundColor(surface);
        view.setTextSize(14f);
        view.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        int horizontal = dp(14f);
        int vertical = dp(10f);
        view.setPadding(horizontal, vertical, horizontal, vertical);
        view.setMinHeight(dp(44f));
        return view;
    }

    private int themeColor(int attribute, int lightFallback, int darkFallback) {
        TypedValue value = new TypedValue();
        if (getContext().getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                try {
                    return ContextCompat.getColor(getContext(), value.resourceId);
                } catch (android.content.res.Resources.NotFoundException ignored) {
                    // Some vendor themes expose an invalid resource; use the mode fallback below.
                }
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        int nightMode = getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES ? darkFallback : lightFallback;
    }

    private int dp(float value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }
}
