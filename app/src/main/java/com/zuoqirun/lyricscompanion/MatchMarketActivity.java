package com.zuoqirun.lyricscompanion;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 规则市场页：列出服务器上的规则包，支持下载并启用 / 停用 / 移除，以及本机提交记录与
 * 审核状态刷新。
 *
 * <p>全部界面用 Java 代码构建（API 19 兼容），配色与设置页保持一致：背景 0xFF07111F、
 * 卡片 0xFF101E31、强调色 0xFF6EE7F2。服务器不可达时只显示中文错误与「重试」按钮，
 * 不会崩溃。</p>
 */
public final class MatchMarketActivity extends AppCompatActivity {

    private static final int COLOR_BACKGROUND = 0xFF07111F;
    private static final int COLOR_CARD_BG = 0xFF101E31;
    private static final int COLOR_CARD_BORDER = 0xFF1E2F45;
    private static final int COLOR_ACCENT = 0xFF6EE7F2;
    private static final int COLOR_TEXT_PRIMARY = 0xFFF1F5FA;
    private static final int COLOR_TEXT_SECONDARY = 0xFFD7E1EE;
    private static final int COLOR_TEXT_HINT = 0xFF91A1B7;
    private static final int COLOR_TEXT_HELP = 0xFF8392A8;
    private static final int COLOR_WARNING = 0xFFFF8A80;
    private static final int COLOR_PENDING = 0xFFFFCA66;
    private static final int COLOR_SUCCESS = 0xFF7CE38B;
    private static final int MAX_STATUS_REFRESH = 20;

    private static final String RULE_WIKI_URL =
            "https://github.com/zuo-qirun/lyrics-companion-match-rules/wiki";
    private static final String RULE_REPO_URL =
            "https://github.com/zuo-qirun/lyrics-companion-match-rules";

    private final List<MatchRulesClient.CatalogItem> catalog = new ArrayList<>();
    private final AtomicInteger statusFailures = new AtomicInteger();

    private LinearLayout marketContainer;
    private LinearLayout rulesContainer;
    private TextView rulesSummary;
    private MaterialButton rulesToggleAll;
    private LinearLayout submissionsContainer;
    private TextView marketStatus;
    private TextView submissionsSummary;
    private TextView submissionsNote;
    private MaterialButton refreshButton;
    private MaterialButton retryButton;
    private boolean loadingCatalog;
    private int pendingStatusRefreshes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(COLOR_BACKGROUND);
            getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        buildToolbar(root);
        buildRefreshArea(root);
        buildRulesSection(root);
        buildMarketSection(root);
        buildSubmissionsSection(root);
        buildFooter(root);

        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, scroll);

        renderRules();
        renderSubmissions();
        refreshCatalog(false);
    }

    private void buildToolbar(LinearLayout root) {
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("规则市场");
        toolbar.setSubtitle("下载并启用社区分享的匹配规则");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(76)));
    }

    private void buildRefreshArea(LinearLayout root) {
        refreshButton = primaryButton("刷新市场");
        refreshButton.setOnClickListener(v -> refreshCatalog(true));
        root.addView(refreshButton, marginParams(-1, dp(46), 8));

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        marketStatus = text("正在加载规则市场…", 12, COLOR_TEXT_HINT, false);
        marketStatus.setLineSpacing(dp(2), 1f);
        statusRow.addView(marketStatus, new LinearLayout.LayoutParams(0, -2, 1f));

        retryButton = secondaryButton("重试");
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(v -> refreshCatalog(true));
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(dp(84), dp(38));
        retryParams.leftMargin = dp(8);
        statusRow.addView(retryButton, retryParams);

        root.addView(statusRow, marginParams(-1, -2, 6));
    }

    private void buildRulesSection(LinearLayout root) {
        LinearLayout card = card("本机规则");
        rulesSummary = text("", 12, COLOR_TEXT_HINT, false);
        rulesSummary.setLineSpacing(dp(2), 1f);
        card.addView(rulesSummary, marginParams(-1, -2, 8));

        rulesContainer = new LinearLayout(this);
        rulesContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(rulesContainer, new LinearLayout.LayoutParams(-1, -2));

        rulesToggleAll = secondaryButton("");
        rulesToggleAll.setOnClickListener(v -> toggleAllMarketPacks());
        card.addView(rulesToggleAll, marginParams(-1, dp(44), 10));

        addCard(root, card);
    }

    /** 渲染「本机规则」：内置、已安装市场规则（可逐个或一键启停）、个人规则。 */
    private void renderRules() {
        if (rulesContainer == null) return;
        rulesContainer.removeAllViews();
        List<MatchRulePack> builtin = BuiltinMatchRules.packs();
        List<MatchRulePack> market = MatchRuleStore.marketPacks(this);
        List<MatchRulePack> user = MatchRuleStore.userPacks(this);
        int enabledCount = MatchRuleStore.enabledMarketPackCount(this);
        if (rulesSummary != null) {
            rulesSummary.setText("市场规则启用 " + enabledCount + " / " + market.size()
                    + " 条；内置规则始终启用，个人规则始终参与匹配。");
        }
        if (rulesToggleAll != null) {
            boolean allDisabled = !market.isEmpty() && enabledCount == 0;
            rulesToggleAll.setText(allDisabled ? "启用全部市场规则" : "停用全部市场规则");
            rulesToggleAll.setEnabled(!market.isEmpty());
        }

        rulesContainer.addView(text("内置规则", 12, COLOR_ACCENT, true),
                marginParams(-1, -2, 6));
        for (MatchRulePack pack : builtin) {
            rulesContainer.addView(text("· " + safePackName(pack) + "  v" + pack.version
                    + " · " + pack.rules.size() + " 条 · 始终启用",
                    12, COLOR_TEXT_SECONDARY, false), marginParams(-1, -2, 4));
        }

        rulesContainer.addView(text("市场规则", 12, COLOR_ACCENT, true),
                marginParams(-1, -2, 12));
        for (MatchRulePack pack : market) {
            rulesContainer.addView(buildInstalledPackRow(pack));
        }
        if (market.isEmpty()) {
            TextView empty = text("还没有安装市场规则。在下方「市场规则」里选择「下载并启用」后，"
                    + "会在这里显示并可以随时停用或移除。", 12, COLOR_TEXT_HELP, false);
            empty.setLineSpacing(dp(2), 1f);
            rulesContainer.addView(empty, marginParams(-1, -2, 6));
        }

        if (!user.isEmpty()) {
            rulesContainer.addView(text("个人规则", 12, COLOR_ACCENT, true),
                    marginParams(-1, -2, 12));
            for (MatchRulePack pack : user) {
                rulesContainer.addView(text("· " + safePackName(pack) + "  v" + pack.version
                        + " · " + pack.rules.size() + " 条", 12, COLOR_TEXT_SECONDARY, false),
                        marginParams(-1, -2, 4));
            }
        }
    }

    private View buildInstalledPackRow(final MatchRulePack pack) {
        boolean enabled = false;
        try {
            enabled = MatchRuleStore.isMarketPackEnabled(this, pack.id);
        } catch (Throwable ignored) {
            enabled = false;
        }
        final boolean packEnabled = enabled;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, 0);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(safePackName(pack), 13, COLOR_TEXT_PRIMARY, true);
        titleRow.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        titleRow.addView(text(packEnabled ? "已启用" : "已停用", 11,
                packEnabled ? COLOR_ACCENT : COLOR_TEXT_HELP, true));
        row.addView(titleRow);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton toggle = secondaryButton(packEnabled ? "停用" : "启用");
        toggle.setOnClickListener(v -> changeInstalledPackEnabled(pack, packEnabled));
        MaterialButton remove = secondaryButton("移除");
        LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
        removeParams.leftMargin = dp(8);
        remove.setOnClickListener(v -> confirmRemoveInstalledPack(pack));
        actions.addView(toggle, new LinearLayout.LayoutParams(0, dp(40), 1f));
        actions.addView(remove, removeParams);
        row.addView(actions, marginParams(-1, -2, 8));
        return row;
    }

    private void changeInstalledPackEnabled(MatchRulePack pack, boolean currentlyEnabled) {
        try {
            MatchRuleStore.setMarketPackEnabled(this, pack.id, !currentlyEnabled);
            SafeToast.show(this, (currentlyEnabled ? "已停用：" : "已启用：") + safePackName(pack),
                    Toast.LENGTH_SHORT);
        } catch (Throwable error) {
            SafeToast.show(this, "操作失败：" + describe(error), Toast.LENGTH_LONG);
        }
        renderRules();
        renderMarketRows();
    }

    private void toggleAllMarketPacks() {
        boolean disableAll = MatchRuleStore.enabledMarketPackCount(this) > 0;
        try {
            MatchRuleStore.setAllMarketPacksEnabled(this, !disableAll);
            SafeToast.show(this, disableAll ? "已停用全部市场规则" : "已启用全部市场规则",
                    Toast.LENGTH_SHORT);
        } catch (Throwable error) {
            SafeToast.show(this, "操作失败：" + describe(error), Toast.LENGTH_LONG);
        }
        renderRules();
        renderMarketRows();
    }

    private void confirmRemoveInstalledPack(final MatchRulePack pack) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("移除市场规则")
                .setMessage("确定从本机移除「" + safePackName(pack) + "」吗？移除后该规则不再参与匹配，"
                        + "需要时可以重新下载。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    try {
                        MatchRuleStore.removeMarketPack(this, pack.id);
                        SafeToast.show(this, "已移除：" + safePackName(pack), Toast.LENGTH_SHORT);
                    } catch (Throwable error) {
                        SafeToast.show(this, "移除失败：" + describe(error), Toast.LENGTH_LONG);
                    }
                    renderRules();
                    renderMarketRows();
                })
                .show();
    }

    private static String safePackName(MatchRulePack pack) {
        if (pack == null) return "规则包";
        String name = pack.name == null ? "" : pack.name.trim();
        return name.isEmpty() ? pack.id : name;
    }

    private void buildMarketSection(LinearLayout root) {
        LinearLayout card = card("市场规则");
        marketContainer = new LinearLayout(this);
        marketContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(marketContainer, new LinearLayout.LayoutParams(-1, -2));
        addCard(root, card);
    }

    private void buildSubmissionsSection(LinearLayout root) {
        LinearLayout card = card("我的提交");
        submissionsSummary = text("", 12, COLOR_TEXT_HINT, false);
        submissionsSummary.setLineSpacing(dp(2), 1f);
        card.addView(submissionsSummary, marginParams(-1, -2, 8));

        submissionsContainer = new LinearLayout(this);
        submissionsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(submissionsContainer, new LinearLayout.LayoutParams(-1, -2));

        submissionsNote = text("", 11, COLOR_TEXT_HELP, false);
        submissionsNote.setLineSpacing(dp(2), 1f);
        card.addView(submissionsNote, marginParams(-1, -2, 6));

        MaterialButton refreshStatuses = secondaryButton("刷新审核状态");
        refreshStatuses.setOnClickListener(v -> refreshSubmissionStatuses());
        card.addView(refreshStatuses, marginParams(-1, dp(44), 10));

        addCard(root, card);
    }

    private void buildFooter(LinearLayout root) {
        TextView footer = text(
                "市场规则只有下载并启用后才会参与本机歌词匹配；内置规则始终启用。"
                        + "提交的规则由服务器审核后上线，审核状态可在「我的提交」里刷新。",
                11, COLOR_TEXT_HELP, false);
        footer.setLineSpacing(dp(2), 1f);
        root.addView(footer, marginParams(-1, -2, 14));

        LinearLayout links = new LinearLayout(this);
        links.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton wikiButton = secondaryButton("规则文档（Wiki）");
        wikiButton.setOnClickListener(v -> openUrl(RULE_WIKI_URL));
        MaterialButton repoButton = secondaryButton("模板仓库");
        repoButton.setOnClickListener(v -> openUrl(RULE_REPO_URL));
        LinearLayout.LayoutParams left =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        left.rightMargin = dp(8);
        links.addView(wikiButton, left);
        links.addView(repoButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        root.addView(links, marginParams(-1, -2, 10));
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException e) {
            Toast.makeText(this, "没有可打开链接的应用：" + url, Toast.LENGTH_SHORT).show();
        }
    }

    // ── 市场目录 ─────────────────────────────────────────────────

    private void refreshCatalog(boolean userTriggered) {
        if (loadingCatalog) return;
        loadingCatalog = true;
        if (marketStatus != null) {
            marketStatus.setText("正在加载规则市场…");
            marketStatus.setTextColor(COLOR_TEXT_HINT);
        }
        if (retryButton != null) retryButton.setVisibility(View.GONE);
        if (refreshButton != null) refreshButton.setEnabled(false);
        MatchRulesClient.fetchCatalog(result -> runOnUiThread(() -> {
            if (!isAlive()) {
                loadingCatalog = false;
                return;
            }
            loadingCatalog = false;
            if (refreshButton != null) refreshButton.setEnabled(true);
            if (result == null || !result.ok) {
                String message = result == null ? "刷新失败，请稍后重试" : result.message;
                if (marketStatus != null) {
                    marketStatus.setText("无法加载规则市场：" + message);
                    marketStatus.setTextColor(COLOR_WARNING);
                }
                if (retryButton != null) retryButton.setVisibility(View.VISIBLE);
                return;
            }
            catalog.clear();
            if (result.value != null) catalog.addAll(result.value);
            if (marketStatus != null) {
                marketStatus.setText(catalog.isEmpty()
                        ? "规则市场暂时没有可用规则，可稍后再刷新"
                        : "共 " + catalog.size() + " 条市场规则，下载后才会参与本机匹配");
                marketStatus.setTextColor(COLOR_TEXT_HINT);
            }
            if (retryButton != null) retryButton.setVisibility(View.GONE);
            renderMarketRows();
            if (userTriggered) SafeToast.show(this, "规则市场已刷新", Toast.LENGTH_SHORT);
        }));
    }

    private void renderMarketRows() {
        if (marketContainer == null) return;
        marketContainer.removeAllViews();
        if (catalog.isEmpty()) return;
        Map<String, PackInfo> installed = readInstalledPacks();
        for (MatchRulesClient.CatalogItem item : catalog) {
            PackInfo local = findInstalled(installed, item);
            boolean enabled = false;
            if (local != null) {
                try {
                    enabled = MatchRuleStore.isMarketPackEnabled(this, local.packId);
                } catch (Throwable ignored) {
                    enabled = false;
                }
            }
            marketContainer.addView(buildMarketItem(item, local, enabled),
                    marginParams(-1, -2, 10));
        }
    }

    private Map<String, PackInfo> readInstalledPacks() {
        Map<String, PackInfo> installed = new HashMap<>();
        try {
            Map<String, String> recordIds = MatchRuleStore.marketRecordIds(this);
            List<MatchRulePack> packs = MatchRuleStore.marketPacks(this);
            if (packs != null) {
                for (MatchRulePack pack : packs) {
                    PackInfo info = readPackInfo(pack);
                    if (info == null) continue;
                    installed.put(info.packId, info);
                    String recordId = recordIds.get(info.packId);
                    if (recordId != null && !recordId.trim().isEmpty()) {
                        installed.put(recordId.trim(), info);
                    }
                }
            }
        } catch (Throwable error) {
            if (marketStatus != null) {
                marketStatus.setText("读取本机已安装规则失败：" + describe(error));
                marketStatus.setTextColor(COLOR_WARNING);
            }
        }
        return installed;
    }

    /**
     * 找目录条目对应的本机已安装规则。
     *
     * <p>新安装会记录「服务器记录 id → 规则包 id」映射；旧版本安装的包没有映射，按名称
     * （优先同版本）回退匹配，避免升级后仍显示「未安装」。</p>
     */
    private PackInfo findInstalled(Map<String, PackInfo> installed,
                                   MatchRulesClient.CatalogItem item) {
        if (installed == null || item == null) return null;
        PackInfo direct = installed.get(item.id);
        if (direct != null) return direct;
        PackInfo sameName = null;
        for (PackInfo info : installed.values()) {
            if (info == null || !info.name.equals(item.name)) continue;
            if (info.version == item.version) return info;
            if (sameName == null) sameName = info;
        }
        return sameName;
    }

    private View buildMarketItem(MatchRulesClient.CatalogItem item, PackInfo local,
                                 boolean enabled) {
        LinearLayout itemView = new LinearLayout(this);
        itemView.setOrientation(LinearLayout.VERTICAL);
        itemView.setPadding(dp(14), dp(13), dp(14), dp(13));
        MaterialShapeDrawable surface = new MaterialShapeDrawable();
        surface.setFillColor(android.content.res.ColorStateList.valueOf(COLOR_CARD_BG));
        surface.setCornerSize(dp(18));
        surface.setStroke(dp(1), COLOR_CARD_BORDER);
        itemView.setBackground(surface);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(item.name, 14, COLOR_TEXT_PRIMARY, true);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        titleRow.addView(text("v" + item.version, 11, COLOR_ACCENT, true));
        itemView.addView(titleRow);

        StringBuilder meta = new StringBuilder();
        meta.append("作者：").append(item.author.isEmpty() ? "未填写" : item.author);
        meta.append(" · ").append(formatSize(item.size));
        meta.append(" · 下载 ").append(item.downloads).append(" 次");
        itemView.addView(text(meta.toString(), 11, COLOR_TEXT_HINT, false),
                marginParams(-1, -2, 3));

        if (!item.description.isEmpty()) {
            TextView description = text(item.description, 12, COLOR_TEXT_SECONDARY, false);
            description.setLineSpacing(dp(2), 1f);
            itemView.addView(description, marginParams(-1, -2, 6));
        }

        TextView state = text("", 11, COLOR_TEXT_HELP, true);
        boolean installed = local != null;
        if (!installed) {
            state.setText("未安装");
            state.setTextColor(COLOR_TEXT_HELP);
        } else if (local.version < item.version) {
            state.setText("已安装 v" + local.version + (enabled ? " · 已启用" : " · 未启用")
                    + "，可更新到 v" + item.version);
            state.setTextColor(COLOR_PENDING);
        } else {
            state.setText("已安装 v" + local.version + (enabled ? " · 已启用" : " · 未启用"));
            state.setTextColor(enabled ? COLOR_ACCENT : COLOR_TEXT_HELP);
        }
        itemView.addView(state, marginParams(-1, -2, 8));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        boolean canUpdate = !installed || local.version < item.version;
        if (canUpdate) {
            MaterialButton install = primaryButton(installed ? "更新并启用" : "下载并启用");
            install.setOnClickListener(v -> downloadAndEnable(item, install));
            actions.addView(install, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        if (installed) {
            MaterialButton toggle = secondaryButton(enabled ? "停用" : "启用");
            LinearLayout.LayoutParams toggleParams =
                    new LinearLayout.LayoutParams(0, dp(42), 1f);
            toggleParams.leftMargin = canUpdate ? dp(8) : 0;
            toggle.setOnClickListener(v -> setPackEnabled(local, enabled));
            actions.addView(toggle, toggleParams);

            MaterialButton remove = secondaryButton("移除");
            LinearLayout.LayoutParams removeParams =
                    new LinearLayout.LayoutParams(0, dp(42), 1f);
            removeParams.leftMargin = dp(8);
            remove.setOnClickListener(v -> confirmRemove(local));
            actions.addView(remove, removeParams);
        }
        itemView.addView(actions, marginParams(-1, -2, 10));
        return itemView;
    }

    private void downloadAndEnable(final MatchRulesClient.CatalogItem item,
                                   final MaterialButton trigger) {
        trigger.setEnabled(false);
        trigger.setText("下载中…");
        MatchRulesClient.downloadPack(item.id, item.version, result -> runOnUiThread(() -> {
            if (!isAlive()) return;
            if (result == null || !result.ok || result.value == null) {
                String message = result == null ? "下载失败，请稍后重试" : result.message;
                SafeToast.show(this, "下载失败：" + message, Toast.LENGTH_LONG);
                renderMarketRows();
                return;
            }
            try {
                MatchRuleStore.installMarketPack(this, result.value, item.id);
                MatchRuleStore.setMarketPackEnabled(this, result.value.id, true);
                SafeToast.show(this, "已下载并启用：" + item.name, Toast.LENGTH_SHORT);
            } catch (Throwable error) {
                SafeToast.show(this, "安装规则失败：" + describe(error), Toast.LENGTH_LONG);
            }
            renderRules();
            renderMarketRows();
        }));
    }

    private void setPackEnabled(PackInfo local, boolean currentlyEnabled) {
        if (local == null) return;
        try {
            MatchRuleStore.setMarketPackEnabled(this, local.packId, !currentlyEnabled);
            SafeToast.show(this, (currentlyEnabled ? "已停用：" : "已启用：") + local.name,
                    Toast.LENGTH_SHORT);
        } catch (Throwable error) {
            SafeToast.show(this, "操作失败：" + describe(error), Toast.LENGTH_LONG);
        }
        renderRules();
        renderMarketRows();
    }

    private void confirmRemove(final PackInfo local) {
        if (local == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("移除市场规则")
                .setMessage("确定从本机移除「" + local.name + "」吗？移除后该规则不再参与匹配，"
                        + "需要时可以重新下载。")
                .setNegativeButton("取消", null)
                .setPositiveButton("移除", (dialog, which) -> {
                    try {
                        MatchRuleStore.removeMarketPack(this, local.packId);
                        SafeToast.show(this, "已移除：" + local.name, Toast.LENGTH_SHORT);
                    } catch (Throwable error) {
                        SafeToast.show(this, "移除失败：" + describe(error), Toast.LENGTH_LONG);
                    }
                    renderRules();
                    renderMarketRows();
                })
                .show();
    }

    // ── 我的提交 ─────────────────────────────────────────────────

    private void renderSubmissions() {
        if (submissionsContainer == null) return;
        submissionsContainer.removeAllViews();
        List<MatchRulesClient.Submission> submissions = readSubmissions();
        if (submissionsSummary != null) {
            if (submissions.isEmpty()) {
                submissionsSummary.setText("还没有提交记录。在「匹配规则」页编辑规则后点"
                        + "「提交到市场」即可在此查看审核状态。");
            } else {
                submissionsSummary.setText("共 " + submissions.size()
                        + " 条提交记录，仅保存在本机；点「刷新审核状态」向服务器查询最新结果。");
            }
        }
        if (submissionsNote != null) submissionsNote.setText("");
        for (int index = 0; index < submissions.size(); index++) {
            if (index > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(COLOR_CARD_BORDER);
                submissionsContainer.addView(divider, marginParams(-1, dp(1), 10));
            }
            submissionsContainer.addView(buildSubmissionRow(submissions.get(index)));
        }
    }

    private View buildSubmissionRow(MatchRulesClient.Submission submission) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        String name = submission.name.isEmpty() ? "未命名规则" : submission.name;
        header.addView(text(name, 13, COLOR_TEXT_PRIMARY, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(text(statusLabel(submission.status), 11,
                statusColor(submission.status), true));
        row.addView(header);

        StringBuilder meta = new StringBuilder("编号 ").append(submission.id);
        if (submission.submittedAt > 0) {
            meta.append(" · ").append(formatTime(submission.submittedAt));
        }
        row.addView(text(meta.toString(), 11, COLOR_TEXT_HELP, false),
                marginParams(-1, -2, 3));
        return row;
    }

    private void refreshSubmissionStatuses() {
        final List<MatchRulesClient.Submission> submissions = readSubmissions();
        List<MatchRulesClient.Submission> queryable = new ArrayList<>();
        for (MatchRulesClient.Submission submission : submissions) {
            if (submission == null || submission.id.isEmpty() || submission.token.isEmpty()) {
                continue;
            }
            if (queryable.size() >= MAX_STATUS_REFRESH) break;
            queryable.add(submission);
        }
        if (queryable.isEmpty()) {
            SafeToast.show(this, submissions.isEmpty()
                    ? "还没有提交记录" : "提交记录缺少查询令牌，无法刷新状态", Toast.LENGTH_LONG);
            return;
        }
        pendingStatusRefreshes = queryable.size();
        statusFailures.set(0);
        final List<String> notes = Collections.synchronizedList(new ArrayList<String>());
        if (submissionsSummary != null) {
            submissionsSummary.setText("正在刷新 " + queryable.size() + " 条提交的审核状态…");
        }
        for (final MatchRulesClient.Submission submission : queryable) {
            MatchRulesClient.status(submission.id, submission.token, result -> {
                boolean failed = result == null || !result.ok || result.value == null;
                String latestReply = "";
                if (!failed) {
                    try {
                        MatchRulesClient.updateSubmissionStatus(this, submission.id,
                                result.value.status);
                    } catch (Throwable ignored) {
                        // 本地写入失败不影响刷新结果展示。
                    }
                    if (result.value.messages != null && !result.value.messages.isEmpty()) {
                        latestReply = result.value.messages.get(
                                result.value.messages.size() - 1).trim();
                    }
                }
                final boolean statusFailed = failed;
                final String statusReply = latestReply;
                runOnUiThread(() -> {
                    if (!isAlive()) return;
                    if (statusFailed) {
                        statusFailures.incrementAndGet();
                    } else if (!statusReply.isEmpty()) {
                        notes.add((submission.name == null || submission.name.isEmpty()
                                ? submission.id : submission.name)
                                + "：" + statusReply);
                    }
                    pendingStatusRefreshes--;
                    if (pendingStatusRefreshes > 0) return;
                    renderSubmissions();
                    if (submissionsNote != null && !notes.isEmpty()) {
                        StringBuilder joined = new StringBuilder();
                        for (String item : notes) {
                            if (joined.length() > 0) joined.append('\n');
                            joined.append("审核回复：").append(item);
                        }
                        submissionsNote.setText(joined.toString());
                    }
                    int failures = statusFailures.get();
                    SafeToast.show(this, failures > 0
                                    ? "有 " + failures + " 条提交状态刷新失败，请检查网络后重试"
                                    : "审核状态已更新",
                            failures > 0 ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
                });
            });
        }
    }

    private List<MatchRulesClient.Submission> readSubmissions() {
        try {
            return MatchRulesClient.submissions(this);
        } catch (Throwable error) {
            return new ArrayList<>();
        }
    }

    private static String statusLabel(String status) {
        String value = status == null ? "" : status.trim().toLowerCase(Locale.US);
        switch (value) {
            case "pending":
                return "待审核";
            case "approved":
                return "已通过";
            case "rejected":
                return "已拒绝";
            case "disabled":
                return "已下架";
            case "":
                return "状态未知";
            default:
                return status.trim();
        }
    }

    private static int statusColor(String status) {
        String value = status == null ? "" : status.trim().toLowerCase(Locale.US);
        switch (value) {
            case "approved":
                return COLOR_SUCCESS;
            case "pending":
                return COLOR_PENDING;
            case "rejected":
            case "disabled":
                return COLOR_WARNING;
            default:
                return COLOR_TEXT_HINT;
        }
    }

    // ── 规则包信息 ───────────────────────────────────────────────

    private static final class PackInfo {
        final String packId;
        final String name;
        final int version;

        PackInfo(String packId, String name, int version) {
            this.packId = packId;
            this.name = name;
            this.version = version;
        }
    }

    private PackInfo readPackInfo(MatchRulePack pack) {
        if (pack == null || pack.id == null || pack.id.trim().isEmpty()) return null;
        String name = pack.name == null || pack.name.trim().isEmpty() ? pack.id : pack.name;
        return new PackInfo(pack.id, name, pack.version);
    }

    // ── 通用视图 ─────────────────────────────────────────────────

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(15));
        MaterialShapeDrawable surface = new MaterialShapeDrawable();
        surface.setFillColor(android.content.res.ColorStateList.valueOf(COLOR_CARD_BG));
        surface.setCornerSize(dp(20));
        card.setBackground(surface);
        card.addView(text(title, 13, COLOR_ACCENT, true));
        return card;
    }

    private void addCard(LinearLayout root, LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(card, params);
    }

    private MaterialButton primaryButton(String label) {
        MaterialButton button = baseButton(label);
        button.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(COLOR_ACCENT));
        button.setTextColor(COLOR_BACKGROUND);
        return button;
    }

    private MaterialButton secondaryButton(String label) {
        MaterialButton button = baseButton(label);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF25364D));
        button.setTextColor(COLOR_TEXT_PRIMARY);
        return button;
    }

    private MaterialButton baseButton(String label) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setCornerRadius(dp(15));
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        return button;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(topMarginDp);
        return params;
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "大小未知";
        if (bytes < 1024) return bytes + " B";
        double kilobytes = bytes / 1024.0;
        if (kilobytes < 1024) return String.format(Locale.US, "%.1f KB", kilobytes);
        return String.format(Locale.US, "%.1f MB", kilobytes / 1024.0);
    }

    private static String formatTime(long millis) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(millis));
        } catch (Throwable error) {
            return "时间未知";
        }
    }

    private static String describe(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) return message;
        return error.getClass().getSimpleName();
    }

    private boolean isAlive() {
        return !isFinishing() && (Build.VERSION.SDK_INT < 17 || !isDestroyed());
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
