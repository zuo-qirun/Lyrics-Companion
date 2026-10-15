package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Select screens, styles and categories, then review the exact overwrite before applying. */
final class SettingsCopyDialog {
    private static final SettingsCatalog.Category[] GROUPS = {
            SettingsCatalog.Category.TEXT, SettingsCatalog.Category.BACKGROUND,
            SettingsCatalog.Category.COLORS, SettingsCatalog.Category.EFFECTS};
    private final Context context;
    private final int sourceSlot;
    private final boolean secondary;
    private final String sourceStyle;
    private final Map<String, ?> source;
    private final List<Integer> slots = new ArrayList<>();
    private final List<String> screenLabels = new ArrayList<>();
    private final boolean[] selectedScreens;
    private final boolean[] selectedStyles = new boolean[AppPreferences.OVERLAY_STYLES.length];
    private final boolean[] selectedGroups = {true, true, true, true};

    private SettingsCopyDialog(Context context, int sourceSlot, boolean secondary) {
        this.context = context;
        this.sourceSlot = sourceSlot;
        this.secondary = secondary;
        sourceStyle = AppPreferences.overlayStyle(context, secondary);
        source = new LinkedHashMap<>(DisplaySlotContext.preferencesFor(context, sourceSlot).getAll());
        slots.add(0);
        slots.add(1);
        for (int i = 0; i < DisplaySlotRegistry.entries(context).size(); i++) slots.add(DisplaySlotRegistry.slotFor(i));
        if (!slots.contains(sourceSlot)) slots.add(sourceSlot);
        for (int slot : slots) screenLabels.add(DisplaySlotRegistry.slotLabel(context, slot));
        selectedScreens = new boolean[slots.size()];
        selectedScreens[slots.indexOf(sourceSlot)] = true;
        for (int i = 0; i < selectedStyles.length; i++) {
            selectedStyles[i] = sourceStyle.equals(AppPreferences.OVERLAY_STYLES[i]);
        }
    }

    static void show(Context context, int sourceSlot, boolean secondary) {
        new SettingsCopyDialog(context, sourceSlot, secondary).screens();
    }

    private void screens() {
        new MaterialAlertDialogBuilder(context).setTitle("选择目标屏幕（1/3）")
                .setMultiChoiceItems(screenLabels.toArray(new String[0]), selectedScreens,
                        (dialog, index, checked) -> selectedScreens[index] = checked)
                .setNegativeButton("取消", null)
                .setPositiveButton("下一步", (dialog, which) -> styles()).show();
    }

    private void styles() {
        String[] labels = new String[selectedStyles.length];
        for (int i = 0; i < labels.length; i++) labels[i] = DisplaySettingsActivity.styleName(AppPreferences.OVERLAY_STYLES[i]);
        new MaterialAlertDialogBuilder(context).setTitle("选择目标样式（2/3）")
                .setMultiChoiceItems(labels, selectedStyles,
                        (dialog, index, checked) -> selectedStyles[index] = checked)
                .setNegativeButton("上一步", (dialog, which) -> screens())
                .setPositiveButton("下一步", (dialog, which) -> groups()).show();
    }

    private void groups() {
        String[] labels = new String[GROUPS.length];
        for (int i = 0; i < GROUPS.length; i++) labels[i] = GROUPS[i].title;
        new MaterialAlertDialogBuilder(context).setTitle("选择复制内容（3/3）")
                .setMultiChoiceItems(labels, selectedGroups,
                        (dialog, index, checked) -> selectedGroups[index] = checked)
                .setNegativeButton("上一步", (dialog, which) -> styles())
                .setPositiveButton("查看覆盖范围", (dialog, which) -> review()).show();
    }

    private void review() {
        Set<SettingsCatalog.Category> categories = EnumSet.noneOf(SettingsCatalog.Category.class);
        StringBuilder categoryText = new StringBuilder();
        for (int i = 0; i < GROUPS.length; i++) if (selectedGroups[i]) {
            categories.add(GROUPS[i]);
            if (categoryText.length() > 0) categoryText.append("、");
            categoryText.append(GROUPS[i].title);
        }
        Map<Integer, Map<String, Object>> writes = new LinkedHashMap<>();
        StringBuilder targets = new StringBuilder();
        int count = 0;
        for (int screen = 0; screen < slots.size(); screen++) {
            if (!selectedScreens[screen]) continue;
            int slot = slots.get(screen);
            for (int i = 0; i < selectedStyles.length; i++) {
                if (!selectedStyles[i]) continue;
                String style = AppPreferences.OVERLAY_STYLES[i];
                if (slot == sourceSlot && style.equals(sourceStyle)) continue;
                Map<String, Object> plan = SettingsCopyPlan.create(source, secondary, sourceStyle,
                        slot > 0, style, categories);
                if (plan.isEmpty()) continue;
                Map<String, Object> screenWrites = writes.get(slot);
                if (screenWrites == null) { screenWrites = new LinkedHashMap<>(); writes.put(slot, screenWrites); }
                screenWrites.putAll(plan);
                targets.append("\n• ").append(screenLabels.get(screen)).append(" · ")
                        .append(DisplaySettingsActivity.styleName(style)).append("：").append(plan.size()).append(" 项");
                count += plan.size();
            }
        }
        if (count == 0) {
            new MaterialAlertDialogBuilder(context).setTitle("没有可复制的设置")
                    .setMessage("请选择其它屏幕或样式，以及已有单独修改的外观分类。仅复制来源明确保存过的项；原位置、窗口尺寸和全局行为保持不变。")
                    .setNegativeButton("取消", null).setPositiveButton("重新选择", (dialog, which) -> screens()).show();
            return;
        }
        final int total = count;
        new MaterialAlertDialogBuilder(context).setTitle("确认覆盖 " + count + " 项外观设置")
                .setMessage("来源：" + DisplaySlotRegistry.slotLabel(context, sourceSlot) + " · "
                        + DisplaySettingsActivity.styleName(sourceStyle) + "\n分类：" + categoryText
                        + "\n目标：" + targets + "\n\n仅覆盖上述分类中来源已单独保存、且目标样式支持的项。不会切换样式，也不复制窗口尺寸、位置或歌词校正。")
                .setNegativeButton("返回修改", (dialog, which) -> groups())
                .setPositiveButton("确认复制", (dialog, which) -> {
                    for (Map.Entry<Integer, Map<String, Object>> screen : writes.entrySet()) {
                        SharedPreferences.Editor editor = DisplaySlotContext.preferencesFor(context, screen.getKey()).edit();
                        for (Map.Entry<String, Object> value : screen.getValue().entrySet()) {
                            AppPreferences.putPreference(editor, value.getKey(), value.getValue());
                        }
                        editor.apply();
                    }
                    AppPreferences.changed(context);
                    SafeToast.show(context, "已复制 " + total + " 项外观设置", Toast.LENGTH_LONG);
                }).show();
    }
}
