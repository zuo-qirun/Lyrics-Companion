package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/**
 * Material 3 设置控件的 XML 模板工厂。
 *
 * <p>设置页以前全部手工 new View 并写死颜色。这里统一从 {@code res/layout/item_setting_*.xml} 与
 * {@code view_settings_card.xml} 膨胀控件，颜色、圆角、字号都交给主题，页面代码只负责绑定行为。
 */
final class SettingsUi {
    private final Context context;
    private final LayoutInflater inflater;

    SettingsUi(Context context) {
        this.context = context;
        this.inflater = LayoutInflater.from(context);
    }

    /** 空卡片（不带标题）。返回内容容器，调用方直接把行加进去。 */
    LinearLayout card(ViewGroup parent) {
        return card(parent, null);
    }

    LinearLayout card(ViewGroup parent, CharSequence title) {
        MaterialCardView card = (MaterialCardView) inflater.inflate(
                R.layout.view_settings_card, parent, false);
        LinearLayout content = card.findViewById(R.id.settings_card_content);
        if (title != null && title.length() > 0) {
            sectionLabel(content, title);
        }
        parent.addView(card);
        return content;
    }

    /** 行卡片实际挂在父布局里的那一层（M3 卡片是 MaterialCardView 包一层）。 */
    static View containerOf(LinearLayout content) {
        ViewParent parent = content.getParent();
        return parent instanceof MaterialCardView ? (View) parent : content;
    }

    void sectionLabel(ViewGroup parent, CharSequence text) {
        TextView label = (TextView) inflater.inflate(R.layout.item_setting_section, parent, false);
        label.setText(text);
        parent.addView(label);
    }

    void note(ViewGroup parent, CharSequence text) {
        TextView note = (TextView) inflater.inflate(R.layout.item_setting_note, parent, false);
        note.setText(text);
        parent.addView(note);
    }

    MaterialSwitch addToggle(ViewGroup parent, CharSequence title, CharSequence subtitle,
                             boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = (LinearLayout) inflater.inflate(R.layout.item_setting_toggle, parent, false);
        ((TextView) row.findViewById(R.id.settings_row_title)).setText(title);
        TextView subtitleView = row.findViewById(R.id.settings_row_subtitle);
        if (subtitle != null && subtitle.length() > 0) {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        }
        MaterialSwitch toggle = row.findViewById(R.id.settings_row_switch);
        toggle.setChecked(checked);
        if (listener != null) toggle.setOnCheckedChangeListener(listener);
        parent.addView(row);
        return toggle;
    }

    /** API 19 没有 {@code java.util.function}，这里用自定义接口接住 lambda（不依赖 desugar）。 */
    interface IntReceiver {
        void accept(int value);
    }

    SeekBar addSeek(ViewGroup parent, CharSequence title, int max, int progress, String suffix,
                    IntReceiver onChange) {
        LinearLayout row = (LinearLayout) inflater.inflate(R.layout.item_setting_seek, parent, false);
        ((TextView) row.findViewById(R.id.settings_row_title)).setText(title);
        final TextView value = row.findViewById(R.id.settings_row_value);
        final SeekBar seek = row.findViewById(R.id.settings_row_seek);
        seek.setMax(max);
        seek.setProgress(progress);
        value.setText(progress + (suffix == null ? "" : suffix));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int newValue, boolean fromUser) {
                value.setText(newValue + (suffix == null ? "" : suffix));
                if (fromUser && onChange != null) onChange.accept(newValue);
            }

            @Override public void onStartTrackingTouch(SeekBar bar) { }

            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(row);
        return seek;
    }

    Spinner addChoice(ViewGroup parent, CharSequence title, String[] labels, int selected,
                      AdapterView.OnItemSelectedListener listener) {
        LinearLayout row = (LinearLayout) inflater.inflate(R.layout.item_setting_choice, parent, false);
        ((TextView) row.findViewById(R.id.settings_row_title)).setText(title);
        Spinner spinner = row.findViewById(R.id.settings_row_spinner);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(context, labels));
        spinner.setSelection(selected, false);
        if (listener != null) spinner.setOnItemSelectedListener(listener);
        parent.addView(row);
        return spinner;
    }

    MaterialButton actionButton(ViewGroup parent, CharSequence text, View.OnClickListener listener) {
        MaterialButton button = (MaterialButton) inflater.inflate(
                R.layout.item_setting_action, parent, false);
        button.setText(text);
        button.setOnClickListener(listener);
        parent.addView(button);
        return button;
    }

    MaterialButton linkButton(ViewGroup parent, CharSequence title, CharSequence summary,
                              View.OnClickListener listener) {
        MaterialButton button = (MaterialButton) inflater.inflate(
                R.layout.item_setting_link, parent, false);
        button.setText(linkText(title, summary));
        button.setOnClickListener(listener);
        parent.addView(button);
        return button;
    }

    static CharSequence linkText(CharSequence title, CharSequence summary) {
        if (summary == null || summary.length() == 0) return title;
        return title + "  ›\n" + summary;
    }

    int dp(float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
