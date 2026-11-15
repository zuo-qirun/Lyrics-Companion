package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.shape.MaterialShapeDrawable;

/** 设置界面公共样式助手——统一管理所有 Settings Activity 的颜色、圆角、间距和工厂方法。 */
final class SettingsStyleHelper {

    // ── 颜色令牌 ──────────────────────────────────────────────────
    static final int COLOR_BACKGROUND       = 0xFF07111F;
    static final int COLOR_CARD_BG          = 0xFF101E31;
    static final int COLOR_CARD_BORDER      = 0xFF1E2F45;
    static final int COLOR_TITLE            = 0xFF6EE7F2;
    static final int COLOR_TEXT_PRIMARY     = 0xFFF1F5FA;
    static final int COLOR_TEXT_SECONDARY   = 0xFFD7E1EE;
    static final int COLOR_TEXT_HINT        = 0xFF91A1B7;
    static final int COLOR_HELP_TEXT        = 0xFF8392A8;
    static final int COLOR_VALUE            = 0xFF6EE7F2;
    static final int COLOR_DIVIDER          = 0xFF1E2F45;

    // ── 尺寸令牌（dp） ───────────────────────────────────────────
    static final float CARD_CORNER_DP      = 18f;
    static final int   CARD_PAD_H_DP      = 15;
    static final int   CARD_PAD_TOP_DP    = 14;
    static final int   CARD_PAD_BOT_DP    = 15;
    static final int   CARD_GAP_DP        = 10;

    private SettingsStyleHelper() { }

    // ── dp 工具 ──────────────────────────────────────────────────

    static int dp(Context context, float dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }

    // ── 卡片工厂 ─────────────────────────────────────────────────

    /**
     * 创建标准设置卡片，带圆角背景和标题。
     * title 建议带 emoji 前缀，例如 "⚡ 快速调节"。
     */
    static LinearLayout createCard(Context context, String title) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int d = dp(context, 1);
        card.setPadding(
                CARD_PAD_H_DP * d,
                CARD_PAD_TOP_DP * d,
                CARD_PAD_H_DP * d,
                CARD_PAD_BOT_DP * d
        );

        MaterialShapeDrawable bg = new MaterialShapeDrawable();
        bg.setFillColor(ColorStateList.valueOf(COLOR_CARD_BG));
        bg.setCornerSize(CARD_CORNER_DP * d);
        bg.setStroke(d, COLOR_CARD_BORDER);
        card.setBackground(bg);

        if (title != null && !title.isEmpty()) {
            TextView tv = createText(context, title, 13, COLOR_TITLE, true);
            tv.setPadding(0, 0, 0, dp(context, 6));
            card.addView(tv);
        }
        return card;
    }

    /**
     * 创建突出卡片（高频操作区），边框用强调色，圆角略大。
     */
    static LinearLayout createAccentCard(Context context, String title) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        int d = dp(context, 1);
        card.setPadding(
                CARD_PAD_H_DP * d,
                CARD_PAD_TOP_DP * d,
                CARD_PAD_H_DP * d,
                CARD_PAD_BOT_DP * d
        );

        MaterialShapeDrawable bg = new MaterialShapeDrawable();
        bg.setFillColor(ColorStateList.valueOf(0xFF0D1E34));
        bg.setCornerSize(22f * d);
        bg.setStroke(Math.round(1.5f * d), 0xFF2A6496);
        card.setBackground(bg);

        if (title != null && !title.isEmpty()) {
            TextView tv = createText(context, title, 13, 0xFF82CFEA, true);
            tv.setPadding(0, 0, 0, dp(context, 6));
            card.addView(tv);
        }
        return card;
    }

    // ── 文本工厂 ─────────────────────────────────────────────────

    static TextView createText(Context context, String text, int sizeSp,
                               int color, boolean bold) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    static TextView createLabel(Context context, String text) {
        return createText(context, text, 13, COLOR_TEXT_SECONDARY, true);
    }

    static TextView createValueText(Context context, String text) {
        return createText(context, text, 12, COLOR_VALUE, true);
    }

    static TextView createHelpText(Context context, String text) {
        TextView tv = createText(context, text, 12, COLOR_HELP_TEXT, false);
        int d = dp(context, 1);
        tv.setPadding(4 * d, 7 * d, 4 * d, 0);
        tv.setLineSpacing(0f, 1.25f);
        return tv;
    }

    /** 卡片内二级分组标题，比卡片标题稍小、颜色偏暗。 */
    static TextView createGroupLabel(Context context, String text) {
        TextView tv = createText(context, text, 11, COLOR_TEXT_HINT, true);
        tv.setPadding(0, dp(context, 10), 0, dp(context, 3));
        return tv;
    }

    // ── 分隔线 ───────────────────────────────────────────────────

    static android.view.View createDivider(Context context) {
        android.view.View v = new android.view.View(context);
        v.setBackgroundColor(COLOR_DIVIDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(context, 1));
        lp.topMargin    = dp(context, 8);
        lp.bottomMargin = dp(context, 4);
        v.setLayoutParams(lp);
        return v;
    }

    // ── Toggle 样式 ──────────────────────────────────────────────

    static void styleToggle(Context context,
                            com.google.android.material.materialswitch.MaterialSwitch toggle) {
        toggle.setTextColor(COLOR_TEXT_PRIMARY);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(context, 10), 0, 0);
        toggle.setMinHeight(dp(context, 48));
    }
}
