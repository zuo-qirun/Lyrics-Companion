package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Virtualized shared row renderer for installed-app pickers. */
final class AppChoiceListAdapter extends BaseAdapter {
    private static final ExecutorService ICON_EXECUTOR = Executors.newFixedThreadPool(2);
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<InstalledAppListCache.AppChoice> allApps;
    private final List<InstalledAppListCache.AppChoice> visibleApps;
    private final Set<String> selected;

    AppChoiceListAdapter(Context context, List<InstalledAppListCache.AppChoice> apps,
                         Set<String> selected) {
        this.context = context;
        this.allApps = new ArrayList<>(apps);
        this.visibleApps = new ArrayList<>(apps);
        this.selected = selected;
    }

    @Override public int getCount() { return visibleApps.size(); }

    @Override public InstalledAppListCache.AppChoice getItem(int position) {
        return visibleApps.get(position);
    }

    @Override public long getItemId(int position) { return position; }

    void setQuery(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        visibleApps.clear();
        for (InstalledAppListCache.AppChoice app : allApps) {
            if (normalized.isEmpty()
                    || app.label.toLowerCase(Locale.ROOT).contains(normalized)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(normalized)) {
                visibleApps.add(app);
            }
        }
        notifyDataSetChanged();
    }

    @Override public View getView(int position, View convertView, ViewGroup parent) {
        RowHolder holder;
        if (convertView == null) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            row.setBackgroundColor(0xFF101E31);
            ImageView icon = new ImageView(context);
            row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
            ProgressBar iconLoading = new ProgressBar(context);
            iconLoading.setIndeterminate(true);
            LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(22), dp(22));
            spinnerParams.leftMargin = dp(-29);
            spinnerParams.rightMargin = dp(7);
            row.addView(iconLoading, spinnerParams);
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView label = text(14, 0xFFF3F7FC, true);
            labels.addView(label);
            TextView packageLabel = text(11, 0xFFA9B6C8, false);
            labels.addView(packageLabel);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            CheckBox check = new CheckBox(context);
            row.addView(check, new LinearLayout.LayoutParams(-2, -2));
            holder = new RowHolder(icon, iconLoading, label, packageLabel, check);
            row.setTag(holder);
            row.setOnClickListener(v -> check.setChecked(!check.isChecked()));
            convertView = row;
        } else {
            holder = (RowHolder) convertView.getTag();
        }
        InstalledAppListCache.AppChoice app = getItem(position);
        holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);
        holder.icon.setTag(app.packageName);
        holder.iconLoading.setVisibility(View.VISIBLE);
        holder.label.setText(app.label);
        holder.packageLabel.setText(app.packageName);
        holder.check.setOnCheckedChangeListener(null);
        holder.check.setChecked(selected.contains(app.packageName));
        holder.check.setContentDescription(app.label);
        holder.check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selected.add(app.packageName); else selected.remove(app.packageName);
        });
        loadIcon(holder.icon, holder.iconLoading, app.packageName);
        return convertView;
    }

    private void loadIcon(ImageView icon, ProgressBar loading, String packageName) {
        ICON_EXECUTOR.execute(() -> {
            Drawable drawable = null;
            try {
                ApplicationInfo info = context.getPackageManager().getApplicationInfo(packageName, 0);
                drawable = info.loadIcon(context.getPackageManager());
            } catch (Throwable ignored) { }
            Drawable result = drawable;
            mainHandler.post(() -> {
                if (!packageName.equals(icon.getTag())) return;
                loading.setVisibility(View.GONE);
                if (result != null) icon.setImageDrawable(result);
            });
        });
    }

    private TextView text(int sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static final class RowHolder {
        final ImageView icon;
        final ProgressBar iconLoading;
        final TextView label;
        final TextView packageLabel;
        final CheckBox check;

        RowHolder(ImageView icon, ProgressBar iconLoading, TextView label,
                  TextView packageLabel, CheckBox check) {
            this.icon = icon;
            this.iconLoading = iconLoading;
            this.label = label;
            this.packageLabel = packageLabel;
            this.check = check;
        }
    }
}
