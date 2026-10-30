package com.zuoqirun.lyricscompanion;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

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
    private final AppCompatActivity activity;
    private final SettingsUi ui;
    private final String title;
    final LinearLayout content;
    final LinearLayout layout;
    final MaterialToolbar toolbar;
    private final ScrollView scroll;
    private final TextInputEditText search;
    private final LinearLayout links;
    private final LinearLayout results;
    private final List<Section> sections = new ArrayList<>();
    /** 已挂到 content 上的卡片容器；原地重建时按这个列表摘掉旧内容。 */
    private final List<View> sectionViews = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<SearchLink> searchLinks = new ArrayList<>();
    private final EnumMap<SettingsCatalog.Category, MaterialButton> categoryButtons =
            new EnumMap<>(SettingsCatalog.Category.class);
    private SettingsCatalog.Category selected = SettingsCatalog.Category.COMMON;
    private View highlighted;
    /** 本页自己的 style / values 会随「编辑区域」「当前样式」变化，原地重建时要能更新。 */
    private String style;
    private SettingsCatalog.Values values;
    /** 已经 setContentView 过一次：之后的重建只换 content，不再动窗口。 */
    private boolean attached;

    SettingsNavigator(AppCompatActivity activity, String title, String scope, String style,
                      SettingsCatalog.Values values) {
        this.activity = activity;
        this.ui = new SettingsUi(activity);
        this.title = title;
        this.style = style;
        this.values = values;
        layout = (LinearLayout) activity.getLayoutInflater()
                .inflate(R.layout.settings_shell, null, false);
        if (Build.VERSION.SDK_INT >= 21) {
            int background = ContextCompat.getColor(activity, R.color.lc_background);
            activity.getWindow().setStatusBarColor(background);
            activity.getWindow().setNavigationBarColor(background);
        }
        toolbar = layout.findViewById(R.id.settings_toolbar);
        toolbar.setTitle(title);
        toolbar.setSubtitle(scope);
        toolbar.setNavigationOnClickListener(v -> {
            if (!back()) activity.finish();
        });
        activity.getOnBackPressedDispatcher().addCallback(activity, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (back()) return;
                setEnabled(false);
                activity.getOnBackPressedDispatcher().onBackPressed();
            }
        });
        search = layout.findViewById(R.id.settings_search);
        scroll = layout.findViewById(R.id.settings_scroll);
        content = layout.findViewById(R.id.settings_content);
        results = layout.findViewById(R.id.settings_results);
        links = layout.findViewById(R.id.settings_links);
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
        View container = SettingsUi.containerOf(card);
        sections.add(new Section(card, container, category, label));
        sectionViews.add(container);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = ui.dp(12);
        // Cards must stay above the category shortcuts: insert right before the links block
        // instead of appending, otherwise the shortcuts float above the whole page.
        int linkIndex = content.indexOfChild(links);
        content.addView(container, linkIndex < 0 ? content.getChildCount() : linkIndex, params);
    }

    LinearLayout row(LinearLayout parent, String key, String label) {
        LinearLayout row = (LinearLayout) activity.getLayoutInflater()
                .inflate(R.layout.item_setting_row, parent, false);
        row.setContentDescription(label + "，" + SettingsCatalog.scope(key));
        parent.addView(row);
        rows.add(new Row(key, label, row));
        return row;
    }

    void scopeNote(LinearLayout row, String key) {
        ui.note(row, SettingsCatalog.scope(key));
    }

    void value(TextView value) { value.setTag(VALUE_TAG); }

    void addLink(String label, String summary, Runnable action) {
        searchLinks.add(new SearchLink(label, summary, action));
        ui.linkButton(links, label, summary, v -> action.run());
    }

    /** 页面作用域 / 当前样式变了（切换编辑区域、返回本页后发现样式被改）：同步副标题与判定条件。 */
    void updateScope(String scope, String style, SettingsCatalog.Values values) {
        toolbar.setSubtitle(scope);
        this.style = style;
        this.values = values;
    }

    /**
     * 同一页原地重建：只摘掉旧卡片，保留 toolbar、搜索框、ScrollView 和滚动位置。
     *
     * <p>以前每次点击都 new 一个 SettingsNavigator 再 setContentView，整棵树重建后的第一帧按
     * 滚动位置 0 绘制，随后才由 {@code scroll.post} 跳回原位，所以界面上会“闪一下顶部画面”。
     */
    void resetForRebuild() {
        for (View container : sectionViews) content.removeView(container);
        sectionViews.clear();
        sections.clear();
        rows.clear();
        searchLinks.clear();
        categoryButtons.clear();
        highlighted = null;
        results.removeAllViews();
        links.removeAllViews();
    }

    void finish(Bundle state) {
        arrangeCommonControls();
        for (SettingsCatalog.Category category : SettingsCatalog.Category.values()) {
            if (category == SettingsCatalog.Category.COMMON || !hasCategory(category)) continue;
            MaterialButton button = ui.linkButton(links, category.title, summary(category),
                    v -> show(category));
            categoryButtons.put(category, button);
        }
        if (!attached) {
            if (state != null) {
                try { selected = SettingsCatalog.Category.valueOf(state.getString(STATE_CATEGORY, "COMMON")); }
                catch (IllegalArgumentException ignored) { selected = SettingsCatalog.Category.COMMON; }
                search.setText(state.getString(STATE_QUERY, ""));
                ScrollRestore.beforeFirstDraw(scroll, state.getInt(STATE_SCROLL, 0));
            } else if (activity.getIntent().hasExtra(EXTRA_QUERY)) {
                search.setText(activity.getIntent().getStringExtra(EXTRA_QUERY));
            }
        }
        refresh();
        if (attached) {
            CustomFontStore.applyToViewTree(activity, content);
            return;
        }
        attached = true;
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
            LinearLayout left = new LinearLayout(activity);
            left.setOrientation(LinearLayout.VERTICAL);
            LinearLayout right = new LinearLayout(activity);
            right.setOrientation(LinearLayout.VERTICAL);
            columns.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
            LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 1f);
            rightParams.leftMargin = ui.dp(20);
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
            section.container.setVisibility(!searching && section.category == selected
                    ? View.VISIBLE : View.GONE);
        }
        links.setVisibility(!searching && selected == SettingsCatalog.Category.COMMON
                ? View.VISIBLE : View.GONE);
        results.setVisibility(searching ? View.VISIBLE : View.GONE);
        for (SettingsCatalog.Category category : categoryButtons.keySet()) {
            categoryButtons.get(category).setText(
                    SettingsUi.linkText(category.title, summary(category)));
        }
        if (searching) showResults(query);
    }

    private void showResults(String query) {
        results.removeAllViews();
        int count = 0;
        for (Row row : rows) {
            Section section = section(row.view);
            if (section == null || !SettingsCatalog.matches(row.title + " " + choiceLabels(row.view),
                    section.category.title, query)) continue;
            count++;
            String reason = reason(row);
            String resultSummary = section.category.title + " · "
                    + SettingsCatalog.scope(row.key) + (reason.isEmpty() ? "" : "\n" + reason);
            MaterialButton result = ui.linkButton(results, row.title, resultSummary, v -> {
                search.setText("");
                hideKeyboard();
                show(section.category);
                if (!reason.isEmpty()) {
                    SafeToast.show(activity, reason, android.widget.Toast.LENGTH_LONG);
                    return;
                }
                if (highlighted != null) highlighted.setBackgroundColor(android.graphics.Color.TRANSPARENT);
                highlighted = row.view;
                highlighted.setBackgroundColor(themeColor(
                        com.google.android.material.R.attr.colorSurfaceContainerHighest, 0xFF1B3149));
                scroll.post(() -> {
                    Rect bounds = new Rect();
                    row.view.getDrawingRect(bounds);
                    content.offsetDescendantRectToMyCoords(row.view, bounds);
                    scroll.scrollTo(0, Math.max(0, bounds.top - ui.dp(12)));
                    row.view.sendAccessibilityEvent(
                            android.view.accessibility.AccessibilityEvent.TYPE_VIEW_FOCUSED);
                });
            });
            result.setContentDescription(row.title + "，" + section.category.title);
        }
        for (SearchLink link : searchLinks) {
            if (!SettingsCatalog.matches(link.title, link.summary, query)) continue;
            count++;
            MaterialButton result = ui.linkButton(results, link.title, link.summary, v -> {
                hideKeyboard();
                link.action.run();
            });
            result.setContentDescription(link.title);
        }
        if (count == 0) ui.note(results, "没有找到相关设置，试试“字号”“封面”或“换行”。");
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
        if (view instanceof TextView && VALUE_TAG.equals(view.getTag())) {
            return ((TextView) view).getText().toString();
        }
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
            if (adapter != null) {
                for (int i = 0; i < adapter.getCount(); i++) labels.append(' ').append(adapter.getItem(i));
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                labels.append(' ').append(choiceLabels(group.getChildAt(i)));
            }
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
        if (highlighted != null) {
            highlighted.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            highlighted = null;
        }
        refresh();
        scroll.scrollTo(0, 0);
    }

    private void hideKeyboard() {
        search.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(
                android.content.Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
    }

    private int themeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (activity.getTheme().resolveAttribute(attr, value, true)) return value.data;
        return fallback;
    }

    private static final class Section {
        final LinearLayout view;
        final View container;
        final SettingsCatalog.Category category;
        final String title;
        Section(LinearLayout view, View container, SettingsCatalog.Category category, String title) {
            this.view = view;
            this.container = container;
            this.category = category;
            this.title = title;
        }
    }

    private static final class Row {
        final String key;
        final String title;
        final LinearLayout view;
        Row(String key, String title, LinearLayout view) {
            this.key = key;
            this.title = title;
            this.view = view;
        }
    }

    private static final class SearchLink {
        final String title;
        final String summary;
        final Runnable action;
        SearchLink(String title, String summary, Runnable action) {
            this.title = title;
            this.summary = summary;
            this.action = action;
        }
    }
}
