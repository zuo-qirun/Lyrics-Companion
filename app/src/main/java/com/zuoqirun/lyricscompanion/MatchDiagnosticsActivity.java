package com.zuoqirun.lyricscompanion;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.shape.MaterialShapeDrawable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 匹配诊断页：展示当前曲目可见的播放器元数据、内置/已启用规则对当前曲目的逐条应用结果、
 * {@link MatchTrace} 的查询与命中记录，并提供「复制 JSON」「清除修正记忆」。
 *
 * <p>字段缺省时显示「播放器未提供」；全部文本可滚动、可复制，不依赖网络。</p>
 */
public final class MatchDiagnosticsActivity extends AppCompatActivity {

    private static final int COLOR_BACKGROUND = 0xFF07111F;
    private static final int COLOR_CARD_BG = 0xFF101E31;
    private static final int COLOR_CARD_BORDER = 0xFF1E2F45;
    private static final int COLOR_ACCENT = 0xFF6EE7F2;
    private static final int COLOR_TEXT_PRIMARY = 0xFFF1F5FA;
    private static final int COLOR_TEXT_SECONDARY = 0xFFD7E1EE;
    private static final int COLOR_TEXT_HINT = 0xFF91A1B7;
    private static final int COLOR_TEXT_HELP = 0xFF8392A8;
    private static final int COLOR_WARNING = 0xFFFF8A80;

    private static final String MISSING = "播放器未提供";

    private LinearLayout diagnosticsContainer;
    private String lastDiagnosticsJson = "";

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
        buildActionRow(root);

        diagnosticsContainer = new LinearLayout(this);
        diagnosticsContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(diagnosticsContainer, new LinearLayout.LayoutParams(-1, -2));

        TextView footer = monoText(
                "诊断信息只在本机生成，不会上传；规则匹配在点击「刷新诊断」时重新计算一次。",
                11, COLOR_TEXT_HELP);
        footer.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(14);
        root.addView(footer, footerParams);

        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, scroll);
        renderDiagnostics();
    }

    private void buildToolbar(LinearLayout root) {
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("匹配诊断");
        toolbar.setSubtitle("当前曲目匹配过程与播放器元数据");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(76)));
    }

    private void buildActionRow(LinearLayout root) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        MaterialButton refresh = secondaryButton("刷新诊断");
        refresh.setOnClickListener(v -> {
            renderDiagnostics();
            SafeToast.show(this, "诊断信息已刷新", Toast.LENGTH_SHORT);
        });
        row.addView(refresh, new LinearLayout.LayoutParams(0, dp(44), 1f));

        MaterialButton copy = primaryButton("复制 JSON");
        copy.setOnClickListener(v -> copyDiagnosticsJson());
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        copyParams.leftMargin = dp(8);
        row.addView(copy, copyParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(8);
        root.addView(row, rowParams);
    }

    // ── 诊断内容 ─────────────────────────────────────────────────

    private void renderDiagnostics() {
        if (diagnosticsContainer == null) return;
        diagnosticsContainer.removeAllViews();

        String detailsText = "";
        try {
            detailsText = safe(MusicStateStore.diagnosticDetails());
        } catch (Throwable ignored) {
            detailsText = "";
        }
        Map<String, String> details = parseDetails(detailsText);

        MusicSnapshot snapshot = MusicSnapshot.EMPTY;
        try {
            MusicSnapshot current = MusicStateStore.snapshot(0);
            if (current != null) snapshot = current;
        } catch (Throwable ignored) {
            snapshot = MusicSnapshot.EMPTY;
        }
        String sourceId = "";
        String sourcePackage = "";
        try {
            sourceId = safe(MusicStateStore.activeSourceId());
            sourcePackage = safe(MusicStateStore.activeSourcePackage());
        } catch (Throwable ignored) {
            // 核心尚未提供 activeSourcePackage 时保持空串，页面显示「播放器未提供」。
        }
        String rawTitle = firstNonEmpty(details.get("rawTitle"), snapshot.title);
        String rawArtist = firstNonEmpty(details.get("rawArtist"), snapshot.artist);
        String rawAlbum = firstNonEmpty(details.get("rawAlbum"), details.get("album"));
        String mediaId = firstNonEmpty(details.get("mediaId"), details.get("activeMediaId"));
        String mediaIdPresent = safe(details.get("mediaIdPresent"));

        MatchCorrectionStore.Correction correction = null;
        int correctionCount = 0;
        try {
            correction = MatchCorrectionStore.find(this, sourcePackage, rawTitle, rawArtist);
            correctionCount = MatchCorrectionStore.count(this);
        } catch (Throwable ignored) {
            correctionCount = 0;
        }

        List<MatchRulePack> packs = new ArrayList<>();
        String packError = "";
        try {
            List<MatchRulePack> enabled = MatchRuleStore.enabledPacks(this);
            if (enabled != null) packs.addAll(enabled);
        } catch (Throwable error) {
            packError = describe(error);
        }

        MatchOutput output = MatchOutput.empty();
        String engineError = "";
        MatchInput input = new MatchInput(rawTitle, rawArtist, rawAlbum, sourcePackage, sourceId);
        try {
            output = MatchRuleEngine.apply(packs, input);
        } catch (Throwable error) {
            engineError = describe(error);
        }
        if (output == null) output = MatchOutput.empty();

        String traceText = "";
        try {
            traceText = safe(MatchTrace.describe());
        } catch (Throwable ignored) {
            traceText = "";
        }

        // 当前曲目
        LinearLayout trackCard = card("当前曲目");
        List<String> trackLines = new ArrayList<>();
        trackLines.add("播放器名称：" + orMissing(snapshot.sourceName));
        trackLines.add("播放器包名：" + orMissing(sourcePackage));
        trackLines.add("来源标识：" + orMissing(sourceId));
        trackLines.add("mediaId：" + describeMediaId(mediaId, mediaIdPresent));
        trackLines.add("原始标题：" + orMissing(rawTitle));
        trackLines.add("原始歌手：" + orMissing(rawArtist));
        trackLines.add("原始专辑：" + orMissing(rawAlbum));
        trackLines.add("时长：" + formatDuration(snapshot.durationMs));
        trackLines.add("播放进度：" + formatDuration(snapshot.positionMs));
        trackLines.add("播放状态：" + describePlayback(snapshot));
        trackLines.add("歌词语义：" + describeLyric(snapshot));
        if (correction != null) {
            trackLines.add("已记住修正：标题=" + safe(correction.title)
                    + "，歌手=" + orMissing(correction.artist));
        }
        addMonoBlock(trackCard, trackLines, 12, COLOR_TEXT_SECONDARY);
        addCard(trackCard);

        // 规则匹配结果
        LinearLayout matchCard = card("规则匹配结果");
        List<String> matchLines = new ArrayList<>();
        matchLines.add("已启用规则包：" + packs.size() + " 个（内置 + 已启用市场包 + 个人规则）");
        int appliesCount = 0;
        for (MatchRulePack pack : packs) {
            if (pack == null) continue;
            boolean applies = false;
            try {
                applies = pack.appliesTo(sourcePackage, sourceId);
            } catch (Throwable ignored) {
                applies = false;
            }
            if (applies) appliesCount++;
            matchLines.add("· " + safe(pack.name) + "（" + safe(pack.id) + " v" + pack.version
                    + "）：" + pack.rules.size() + " 条规则，"
                    + (applies ? "适用于当前曲目" : "不适用于当前曲目"));
        }
        if (!packError.isEmpty()) matchLines.add("读取已启用规则失败：" + packError);
        matchLines.add("实际适用规则包：" + appliesCount + " 个");
        matchLines.add("");
        matchLines.add("引擎输入：title=" + orMissing(rawTitle)
                + " | artist=" + orMissing(rawArtist)
                + " | album=" + orMissing(rawAlbum)
                + " | package=" + orMissing(sourcePackage)
                + " | source=" + orMissing(sourceId));
        if (!engineError.isEmpty()) {
            matchLines.add("规则引擎执行失败：" + engineError);
        }
        matchLines.add("主结果标题：" + orMissing(output.primaryTitle));
        matchLines.add("主结果歌手：" + orMissing(output.primaryArtist));
        matchLines.add("主结果专辑：" + orMissing(output.primaryAlbum));
        matchLines.add("是否改写：" + (output.changed ? "是" : "否"));
        matchLines.add("");
        matchLines.add("候选结果（" + output.candidates.size() + " 条）：");
        if (output.candidates.isEmpty()) {
            matchLines.add("· 无候选项：当前曲目没有被已启用规则改写");
        } else {
            int index = 1;
            for (MatchOutput.Candidate candidate : output.candidates) {
                matchLines.add("· [" + index + "] 标题=" + orMissing(candidate.title)
                        + "，歌手=" + orMissing(candidate.artist)
                        + "，ruleId=" + orMissing(candidate.ruleId));
                index++;
            }
        }
        matchLines.add("");
        matchLines.add("逐条规则动作轨迹（" + output.trace.size() + " 条）：");
        if (output.trace.isEmpty()) {
            matchLines.add("· 无动作命中：没有规则条件匹配当前曲目");
        } else {
            for (MatchOutput.TraceEntry entry : output.trace) {
                matchLines.add("· " + orMissing(entry.ruleId) + "." + orMissing(entry.action)
                        + "：" + orMissing(entry.field) + " 「" + orMissing(entry.before)
                        + "」→「" + orMissing(entry.after) + "」");
            }
        }
        addMonoBlock(matchCard, matchLines, 12, COLOR_TEXT_SECONDARY);
        addCard(matchCard);

        // MatchTrace
        LinearLayout traceCard = card("MatchTrace 查询与命中");
        TextView traceView = monoText(traceText.isEmpty()
                ? "暂无匹配记录：先播放一首歌，或点「刷新诊断」重新读取。"
                : traceText, 11, COLOR_TEXT_HINT);
        traceView.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams traceParams = new LinearLayout.LayoutParams(-1, -2);
        traceParams.topMargin = dp(8);
        traceCard.addView(traceView, traceParams);
        addCard(traceCard);

        // 修正记忆
        LinearLayout correctionCard = card("修正记忆");
        TextView correctionInfo = monoText(
                correctionCount == 0
                        ? "当前没有已记住的歌曲信息修正。"
                        : "已记住 " + correctionCount + " 条修正（按播放器包名 + 原始标题/歌手匹配）。"
                        + (correction != null ? "当前曲目命中 1 条。" : ""),
                12, COLOR_TEXT_SECONDARY);
        correctionInfo.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams correctionInfoParams = new LinearLayout.LayoutParams(-1, -2);
        correctionInfoParams.topMargin = dp(8);
        correctionCard.addView(correctionInfo, correctionInfoParams);
        MaterialButton clear = secondaryButton("清除修正记忆");
        clear.setOnClickListener(v -> confirmClearCorrections());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-1, dp(44));
        clearParams.topMargin = dp(10);
        correctionCard.addView(clear, clearParams);
        addCard(correctionCard);

        // 完整诊断信息
        LinearLayout detailsCard = card("完整诊断信息");
        TextView detailsView = monoText(detailsText.isEmpty() ? MISSING : detailsText,
                11, COLOR_TEXT_HINT);
        detailsView.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams detailsParams = new LinearLayout.LayoutParams(-1, -2);
        detailsParams.topMargin = dp(8);
        detailsCard.addView(detailsView, detailsParams);
        addCard(detailsCard);

        lastDiagnosticsJson = buildDiagnosticsJson(detailsText, sourceId, sourcePackage, mediaId,
                mediaIdPresent, snapshot, rawTitle, rawArtist, rawAlbum, packs, output, traceText,
                correctionCount, correction);
    }

    private String buildDiagnosticsJson(String detailsText, String sourceId, String sourcePackage,
                                        String mediaId, String mediaIdPresent,
                                        MusicSnapshot snapshot, String rawTitle, String rawArtist,
                                        String rawAlbum, List<MatchRulePack> packs,
                                        MatchOutput output, String traceText, int correctionCount,
                                        MatchCorrectionStore.Correction correction) {
        try {
            JSONObject root = new JSONObject();
            root.put("capturedAt", formatTimestamp());

            JSONObject source = new JSONObject();
            source.put("id", sourceId);
            source.put("package", sourcePackage);
            source.put("name", safe(snapshot.sourceName));
            root.put("source", source);

            JSONObject track = new JSONObject();
            track.put("mediaId", mediaId);
            track.put("mediaIdPresent", "true".equals(mediaIdPresent));
            track.put("title", rawTitle);
            track.put("artist", rawArtist);
            track.put("album", rawAlbum);
            track.put("durationMs", snapshot.durationMs);
            track.put("positionMs", snapshot.positionMs);
            track.put("active", snapshot.active);
            track.put("playing", snapshot.playing);
            track.put("lyricLoaded", snapshot.lyricLoaded);
            track.put("lyricAvailable", snapshot.lyricAvailable);
            track.put("lyricSourceName", safe(snapshot.lyricSourceName));
            root.put("track", track);

            JSONArray packArray = new JSONArray();
            for (MatchRulePack pack : packs) {
                if (pack == null) continue;
                JSONObject packJson = new JSONObject();
                packJson.put("id", safe(pack.id));
                packJson.put("name", safe(pack.name));
                packJson.put("version", pack.version);
                boolean applies = false;
                try {
                    applies = pack.appliesTo(sourcePackage, sourceId);
                } catch (Throwable ignored) {
                    applies = false;
                }
                packJson.put("applies", applies);
                packJson.put("ruleCount", pack.rules.size());
                packArray.put(packJson);
            }
            root.put("enabledPacks", packArray);

            JSONObject match = new JSONObject();
            JSONObject input = new JSONObject();
            input.put("title", rawTitle);
            input.put("artist", rawArtist);
            input.put("album", rawAlbum);
            input.put("package", sourcePackage);
            input.put("source", sourceId);
            match.put("input", input);
            match.put("primaryTitle", safe(output.primaryTitle));
            match.put("primaryArtist", safe(output.primaryArtist));
            match.put("primaryAlbum", safe(output.primaryAlbum));
            match.put("changed", output.changed);
            JSONArray candidates = new JSONArray();
            for (MatchOutput.Candidate candidate : output.candidates) {
                JSONObject item = new JSONObject();
                item.put("title", safe(candidate.title));
                item.put("artist", safe(candidate.artist));
                item.put("ruleId", safe(candidate.ruleId));
                item.put("kind", safe(candidate.kind));
                candidates.put(item);
            }
            match.put("candidates", candidates);
            JSONArray traceEntries = new JSONArray();
            for (MatchOutput.TraceEntry entry : output.trace) {
                JSONObject item = new JSONObject();
                item.put("ruleId", safe(entry.ruleId));
                item.put("action", safe(entry.action));
                item.put("field", safe(entry.field));
                item.put("before", safe(entry.before));
                item.put("after", safe(entry.after));
                traceEntries.put(item);
            }
            match.put("trace", traceEntries);
            root.put("match", match);

            root.put("matchTrace", traceText);
            root.put("diagnosticDetails", detailsText);
            root.put("correctionCount", correctionCount);
            if (correction != null) {
                JSONObject fixed = new JSONObject();
                fixed.put("title", safe(correction.title));
                fixed.put("artist", safe(correction.artist));
                root.put("correction", fixed);
            }
            return root.toString(2);
        } catch (JSONException error) {
            String fallback = detailsText
                    + "\n\nmatchTrace:\n" + traceText
                    + "\nprimary=" + safe(output.primaryTitle)
                    + "\nartist=" + safe(output.primaryArtist);
            SafeToast.show(this, "诊断 JSON 生成失败：" + describe(error), Toast.LENGTH_LONG);
            return fallback;
        }
    }

    private void copyDiagnosticsJson() {
        if (lastDiagnosticsJson.isEmpty()) {
            SafeToast.show(this, "诊断数据尚未生成，请先刷新诊断", Toast.LENGTH_SHORT);
            return;
        }
        try {
            ClipboardManager manager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null) {
                SafeToast.show(this, "剪贴板不可用", Toast.LENGTH_SHORT);
                return;
            }
            manager.setPrimaryClip(ClipData.newPlainText("匹配诊断 JSON", lastDiagnosticsJson));
            SafeToast.show(this, "诊断 JSON 已复制到剪贴板", Toast.LENGTH_SHORT);
        } catch (Throwable error) {
            SafeToast.show(this, "复制失败：" + describe(error), Toast.LENGTH_LONG);
        }
    }

    private void confirmClearCorrections() {
        int count;
        try {
            count = MatchCorrectionStore.count(this);
        } catch (Throwable error) {
            SafeToast.show(this, "读取修正记录失败：" + describe(error), Toast.LENGTH_LONG);
            return;
        }
        if (count <= 0) {
            SafeToast.show(this, "当前没有已记住的修正", Toast.LENGTH_SHORT);
            return;
        }
        final int remembered = count;
        new MaterialAlertDialogBuilder(this)
                .setTitle("清除修正记忆")
                .setMessage("当前已记住 " + remembered + " 条歌曲信息修正。清除后这些歌曲会重新按"
                        + "播放器提供的原始信息匹配，确定继续吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> {
                    try {
                        MatchCorrectionStore.clearAll(this);
                        SafeToast.show(this, "已清除 " + remembered + " 条修正记忆",
                                Toast.LENGTH_SHORT);
                    } catch (Throwable error) {
                        SafeToast.show(this, "清除失败：" + describe(error), Toast.LENGTH_LONG);
                    }
                    renderDiagnostics();
                })
                .show();
    }

    // ── 字段描述 ─────────────────────────────────────────────────

    private static String describeMediaId(String mediaId, String mediaIdPresent) {
        if (!mediaId.isEmpty()) return mediaId;
        if ("true".equalsIgnoreCase(mediaIdPresent)) {
            // TODO: MusicStateStore 目前只暴露 mediaIdPresent 布尔值，原始 mediaId 值待核心提供
            //  getter（或 diagnosticDetails 增加 mediaId= 行）后改为直接显示。
            return "播放器已提供（核心暂未暴露原始值）";
        }
        return MISSING;
    }

    private static String describePlayback(MusicSnapshot snapshot) {
        if (!snapshot.active) return "无活动会话";
        return snapshot.playing ? "播放中" : "已暂停（有活动会话）";
    }

    private static String describeLyric(MusicSnapshot snapshot) {
        if (!snapshot.lyricLoaded) return "尚未加载歌词";
        if (!snapshot.lyricAvailable) return "已加载但不可用";
        return "可用" + (snapshot.lyricSourceName.isEmpty()
                ? "" : "（来源：" + snapshot.lyricSourceName + "）");
    }

    private static Map<String, String> parseDetails(String text) {
        Map<String, String> details = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) return details;
        String[] lines = text.split("\n");
        for (String line : lines) {
            int index = line.indexOf('=');
            if (index <= 0) continue;
            String key = line.substring(0, index).trim();
            String value = line.substring(index + 1).trim();
            if (!key.isEmpty()) details.put(key, value);
        }
        return details;
    }

    private static String firstNonEmpty(String first, String second) {
        String value = safe(first);
        return value.isEmpty() ? safe(second) : value;
    }

    private static String orMissing(String value) {
        String trimmed = safe(value);
        return trimmed.isEmpty() ? MISSING : trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static String describe(Throwable error) {
        if (error == null) return "未知错误";
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) return message;
        return error.getClass().getSimpleName();
    }

    private static String formatDuration(long millis) {
        if (millis < 0) return "未知（" + millis + "ms）";
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d（%dms）", hours, minutes, seconds, millis);
        }
        return String.format(Locale.US, "%d:%02d（%dms）", minutes, seconds, millis);
    }

    private static String formatTimestamp() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date());
        } catch (Throwable error) {
            return "";
        }
    }

    // ── 通用视图 ─────────────────────────────────────────────────

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(15));
        MaterialShapeDrawable surface = new MaterialShapeDrawable();
        surface.setFillColor(android.content.res.ColorStateList.valueOf(COLOR_CARD_BG));
        surface.setCornerSize(dp(20));
        surface.setStroke(dp(1), COLOR_CARD_BORDER);
        card.setBackground(surface);
        card.addView(text(title, 13, COLOR_ACCENT, true));
        return card;
    }

    private void addCard(LinearLayout card) {
        if (diagnosticsContainer == null) return;
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        diagnosticsContainer.addView(card, params);
    }

    private void addMonoBlock(LinearLayout card, List<String> lines, int sp, int color) {
        TextView view = monoText(join(lines), sp, color);
        view.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        card.addView(view, params);
    }

    private static String join(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(line);
        }
        return builder.toString();
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

    private TextView monoText(String value, int sp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.MONOSPACE);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
