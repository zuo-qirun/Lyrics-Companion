package com.zuoqirun.lyricscompanion;

import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/** Fixed scope header, common controls, category detail pages and indexed search. */
@android.annotation.SuppressLint("SetTextI18n")
final class SettingsNavigator {
    static final String EXTRA_QUERY = "settings_query";
    private static final String STATE_CATEGORY = "settings_category";
    private static final String STATE_QUERY = "settings_query";
    private static final String STATE_SCROLL = "settings_scroll";
    private static final String VALUE_TAG = "settings_value";
    final LinearLayout content;
    final LinearLayout layout;
    final MaterialToolbar toolbar;
    private final AppCompatActivity activity;
    private final String title;
    private final String style;
    private final SettingsCatalog.Values values;
    private final ScrollView scroll;
    private final EditText search;
    private final LinearLayout links;
    private final LinearLayout results;
    private final List<Section> sections = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<SearchLink> searchLinks = new ArrayList<>();
    private final EnumMap<SettingsCatalog.Category, MaterialButton> categoryButtons =
            new EnumMap<>(SettingsCatalog.Category.class);
    private SettingsCatalog.Category selected = SettingsCatalog.Category.COMMON;
    private View highlighted;

    SettingsNavigator(AppCompatActivity activity, String title, String scope, String style,
                      SettingsCatalog.Values values) {
        this.activity = activity;
        this.title = title;
        this.style = style;
        this.values = values;
        layout = vertical();
        layout.setBackgroundColor(0xFF07111F);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            activity.getWindow().setStatusBarColor(0xFF07111F);
            activity.getWindow().setNavigationBarColor(0xFF07111F);
        }
        toolbar = new MaterialToolbar(activity);
        toolbar.setTitle(title);
        toolbar.setSubtitle(scope);
        toolbar.setTitleTextColor(0xFFF1F5FA);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_revert);
        toolbar.setNavigationContentDescription("返回");
        toolbar.setNavigationOnClickListener(v -> { if (!back()) activity.finish(); });
        layout.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(0xFFF1F5FA);
        search.setHintTextColor(0xFF91A1B7);
        search.setHint("搜索设置，如透明度、封面、换行");
        search.setContentDescription("搜索设置");
        search.setPadding(dp(16), 0, dp(16), 0);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(48));
        searchParams.setMargins(dp(16), 0, dp(16), dp(4));
        layout.addView(search, searchParams);
        scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        content = vertical();
        content.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        results = vertical();
        results.setVisibility(View.GONE);
        content.addView(results);
        links = vertical();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refresh(); }
            @Override public void afterTextChanged(Editable editable) { }
        });
        // Do not pop the keyboard when opening a settings page.
        layout.setFocusableInTouchMode(true);
        layout.requestFocus();
    }

    void addSection(LinearLayout card, SettingsCatalog.Category category, String label) {
        sections.add(new Section(card, category, label));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(12);
        content.addView(card, params);
    }

    LinearLayout row(LinearLayout parent, String key, String label) {
        LinearLayout row = vertical();
        row.setPadding(0, dp(10), 0, dp(8));
        row.setContentDescription(label + "，" + SettingsCatalog.scope(key));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        rows.add(new Row(key, label, row));
        return row;
    }

    void scopeNote(LinearLayout row, String key) {
        row.addView(label(SettingsCatalog.scope(key), 11, 0xFF91A1B7));
    }

    void value(TextView value) { value.setTag(VALUE_TAG); }

    void addLink(String label, String summary, Runnable action) {
        searchLinks.add(new SearchLink(label, summary, action));
        MaterialButton button = button(label + "  ›\n" + summary);
        button.setOnClickListener(v -> action.run());
        links.addView(button, linkParams());
    }

    void finish(Bundle state) {
        arrangeCommonControls();
        for (SettingsCatalog.Category category : SettingsCatalog.Category.values()) {
            if (category == SettingsCatalog.Category.COMMON || !hasCategory(category)) continue;
            MaterialButton button = button(category.title);
            button.setOnClickListener(v -> show(category));
            categoryButtons.put(category, button);
            links.addView(button, linkParams());
        }
        content.addView(links);
        if (state != null) {
            try { selected = SettingsCatalog.Category.valueOf(state.getString(STATE_CATEGORY, "COMMON")); }
            catch (IllegalArgumentException ignored) { selected = SettingsCatalog.Category.COMMON; }
            search.setText(state.getString(STATE_QUERY, ""));
            int y = state.getInt(STATE_SCROLL, 0);
            scroll.post(() -> scroll.scrollTo(0, y));
        } else if (activity.getIntent().hasExtra(EXTRA_QUERY)) {
            search.setText(activity.getIntent().getStringExtra(EXTRA_QUERY));
        }
        refresh();
        activity.setContentView(layout);
        CustomFontStore.applyToViewTree(activity, layout);
    }

    private void arrangeCommonControls() {
        float widthDp = activity.getResources().getDisplayMetrics().widthPixels
                / activity.getResources().getDisplayMetrics().density;
        if (widthDp < 600) return;
        for (Section section : sections) {
            if (section.category != SettingsCatalog.Category.COMMON) continue;
            List<Row> directRows = new ArrayList<>();
            for (Row row : rows) if (row.view.getParent() == section.view) directRows.add(row);
            if (directRows.size() < 4) continue;
            int index = section.view.indexOfChild(directRows.get(0).view);
            LinearLayout columns = new LinearLayout(activity);
            columns.setBaselineAligned(false);
            LinearLayout left = vertical();
            LinearLayout right = vertical();
            columns.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 1f);
            rightParams.leftMargin = dp(20);
            columns.addView(right, rightParams);
            for (int i = 0; i < directRows.size(); i++) {
                View row = directRows.get(i).view;
                section.view.removeView(row);
                (i < (directRows.size() + 1) / 2 ? left : right).addView(row);
            }
            section.view.addView(columns, index);
        }
    }

    void save(Bundle state) {
        state.putString(STATE_CATEGORY, selected.name());
        state.putString(STATE_QUERY, search.getText().toString());
        state.putInt(STATE_SCROLL, scroll.getScrollY());
    }

    boolean back() {
        if (search.length() > 0) { search.setText(""); hideKeyboard(); return true; }
        if (selected != SettingsCatalog.Category.COMMON) { show(SettingsCatalog.Category.COMMON); return true; }
        return false;
    }

    void refresh() {
        for (Row row : rows) row.view.setVisibility(reason(row).isEmpty() ? View.VISIBLE : View.GONE);
        String query = search.getText().toString().trim();
        boolean searching = !query.isEmpty();
        toolbar.setTitle(searching ? "搜索设置" : selected == SettingsCatalog.Category.COMMON
                ? title : selected.title);
        for (Section section : sections) {
            section.view.setVisibility(!searching && section.category == selected ? View.VISIBLE : View.GONE);
        }
        links.setVisibility(!searching && selected == SettingsCatalog.Category.COMMON ? View.VISIBLE : View.GONE);
        results.setVisibility(searching ? View.VISIBLE : View.GONE);
        for (SettingsCatalog.Category category : categoryButtons.keySet()) {
            categoryButtons.get(category).setText(category.title + "  ›\n" + summary(category));
        }
        if (searching) showResults(query);
    }

    private void showResults(String query) {
        results.removeAllViews();
        int count = 0;
        for (Row row : rows) {
            Section section = section(row.view);
            if (section == null || !SettingsCatalog.matches(row.title + " " + choiceLabels(row.view), section.category.title, query)) continue;
            count++;
            String reason = reason(row);
            MaterialButton result = button(row.title + "\n" + section.category.title + " · "
                    + SettingsCatalog.scope(row.key) + (reason.isEmpty() ? "" : "\n" + reason));
            result.setOnClickListener(v -> {
                search.setText("");
                hideKeyboard();
                show(section.category);
                if (!reason.isEmpty()) {
                    SafeToast.show(activity, reason, android.widget.Toast.LENGTH_LONG);
                    return;
                }
                if (highlighted != null) highlighted.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                highlighted = row.view;
                highlighted.setBackgroundColor(0xFF253F55);
                scroll.post(() -> {
                    Rect bounds = new Rect();
                    row.view.getDrawingRect(bounds);
                    content.offsetDescendantRectToMyCoords(row.view, bounds);
                    scroll.scrollTo(0, Math.max(0, bounds.top - dp(12)));
                    row.view.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
                });
            });
            results.addView(result, linkParams());
        }
        for (SearchLink link : searchLinks) {
            if (!SettingsCatalog.matches(link.title, link.summary, query)) continue;
            count++;
            MaterialButton result = button(link.title + "  ›\n" + link.summary);
            result.setOnClickListener(v -> { hideKeyboard(); link.action.run(); });
            results.addView(result, linkParams());
        }
        if (count == 0) results.addView(label("没有找到相关设置，试试“字号”“封面”或“换行”。", 14, 0xFFA9B6C8));
        CustomFontStore.applyToViewTree(activity, results);
    }

    private String reason(Row row) {
        return SettingsCatalog.unavailableReason(row.key, style, values);
    }

    private String summary(SettingsCatalog.Category category) {
        StringBuilder summary = new StringBuilder();
        int count = 0;
        for (Row row : rows) {
            Section section = section(row.view);
            if (section == null || section.category != category || !reason(row).isEmpty()) continue;
            if (count++ >= 2) break;
            if (summary.length() > 0) summary.append(" · ");
            String value = currentValue(row.view);
            summary.append(row.title).append(value.isEmpty() ? "" : " " + value);
        }
        return summary.length() == 0 ? "查看详细选项" : summary.toString();
    }

    private String currentValue(View view) {
        if (view instanceof MaterialSwitch) return ((MaterialSwitch) view).isChecked() ? "开" : "关";
        if (view instanceof Spinner) {
            Object item = ((Spinner) view).getSelectedItem();
            return item == null ? "" : item.toString();
        }
        if (view instanceof TextView && VALUE_TAG.equals(view.getTag())) return ((TextView) view).getText().toString();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                String value = currentValue(group.getChildAt(i));
                if (!value.isEmpty()) return value;
            }
        }
        return "";
    }

    private String choiceLabels(View view) {
        StringBuilder labels = new StringBuilder();
        if (view instanceof Spinner) {
            android.widget.SpinnerAdapter adapter = ((Spinner) view).getAdapter();
            if (adapter != null) for (int i = 0; i < adapter.getCount(); i++) labels.append(' ').append(adapter.getItem(i));
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) labels.append(' ').append(choiceLabels(group.getChildAt(i)));
        }
        return labels.toString();
    }

    private boolean hasCategory(SettingsCatalog.Category category) {
        for (Section section : sections) if (section.category == category) return true;
        return false;
    }

    private Section section(View view) {
        for (View current = view; current != null; ) {
            for (Section section : sections) if (section.view == current) return section;
            current = current.getParent() instanceof View ? (View) current.getParent() : null;
        }
        return null;
    }

    private void show(SettingsCatalog.Category category) {
        selected = category;
        if (highlighted != null) { highlighted.setBackgroundColor(android.graphics.Color.TRANSPARENT); highlighted = null; }
        refresh();
        scroll.scrollTo(0, 0);
    }

    private void hideKeyboard() {
        search.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private MaterialButton button(String text) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(0xFFF1F5FA);
        button.setAllCaps(false);
        button.setMaxLines(3);
        button.setEllipsize(android.text.TextUtils.TruncateAt.END);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(64));
        button.setPadding(dp(16), dp(10), dp(16), dp(10));
        button.setCornerRadius(dp(16));
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF101E31));
        return button;
    }

    private LinearLayout.LayoutParams linkParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        return params;
    }

    private TextView label(String text, int size, int color) {
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private LinearLayout vertical() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private static final class Section {
        final LinearLayout view;
        final SettingsCatalog.Category category;
        final String title;
        Section(LinearLayout view, SettingsCatalog.Category category, String title) {
            this.view = view; this.category = category; this.title = title;
        }
    }

    private static final class Row {
        final String key;
        final String title;
        final LinearLayout view;
        Row(String key, String title, LinearLayout view) { this.key = key; this.title = title; this.view = view; }
    }

    private static final class SearchLink {
        final String title;
        final String summary;
        final Runnable action;
        SearchLink(String title, String summary, Runnable action) {
            this.title = title; this.summary = summary; this.action = action;
        }
    }
}
