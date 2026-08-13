package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

/** Keeps both the selected value and popup rows legible on the app's dark surfaces. */
final class WhiteSpinnerAdapter<T> extends ArrayAdapter<T> {
    WhiteSpinnerAdapter(Context context, T[] values) {
        super(context, android.R.layout.simple_spinner_dropdown_item, values);
    }

    WhiteSpinnerAdapter(Context context, List<T> values) {
        super(context, android.R.layout.simple_spinner_dropdown_item, values);
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        return style((TextView) super.getView(position, convertView, parent));
    }

    @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return style((TextView) super.getDropDownView(position, convertView, parent));
    }

    private TextView style(TextView view) {
        view.setTextColor(0xFFF5F8FF);
        view.setTextSize(14f);
        view.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        int horizontal = Math.round(14f * getContext().getResources().getDisplayMetrics().density);
        int vertical = Math.round(10f * getContext().getResources().getDisplayMetrics().density);
        view.setPadding(horizontal, vertical, horizontal, vertical);
        return view;
    }
}
