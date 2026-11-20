package com.zuoqirun.lyricscompanion;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * 「匹配规则编辑器」：用图形向导或 JSON 代码编写个人匹配规则包，预览后保存启用或提交到规则市场。
 *
 * <p>本界面只依赖 docs/MATCH_RULES_MARKET_SPEC.md §2.1 / §4.1 的稳定签名：
 * {@code MatchInput}、{@code MatchOutput}、{@code MatchRulePack}、{@code MatchRuleStore}、
 * {@code BuiltinMatchRules}、{@code MatchRuleEngine} 与 {@code MatchRulesClient}。
 * 全部界面用 Java 代码构建（仓库没有 Activity XML 布局），API 19 安全。</p>
 */
public final class MatchRuleEditorActivity extends AppCompatActivity {

    // ── 配色（规格要求：深色底 + 浅色字，不依赖系统主题的浅色值）────────────
    private static final int COLOR_BACKGROUND = 0xFF07111F;
    private static final int COLOR_CARD_BG = 0xFF101E31;
    private static final int COLOR_CARD_BORDER = 0xFF1E2F45;
    private static final int COLOR_ACCENT = 0xFF6EE7F2;
    private static final int COLOR_ON_ACCENT = 0xFF07111F;
    private static final int COLOR_TEXT_PRIMARY = 0xFFF1F5FA;
    private static final int COLOR_TEXT = 0xFFD7E1EE;
    private static final int COLOR_TEXT_SECONDARY = 0xFFA9B6C8;
    private static final int COLOR_TEXT_HINT = 0xFF8392A8;
    private static final int COLOR_INPUT_BG = 0xFF0B1A2C;
    private static final int COLOR_BUTTON_BG = 0xFF25364D;
    private static final int COLOR_BOTTOM_BAR_BG = 0xFF0B1A2C;

    // ── 规则文档 / 模板仓库（社区规则）────────────────────────────────────
    private static final String RULE_WIKI_URL =
            "https://github.com/zuo-qirun/lyrics-companion-match-rules/wiki";
    private static final String RULE_REPO_URL =
            "https://github.com/zuo-qirun/lyrics-companion-match-rules";

    // ── 图形向导的字段 / 操作取值（与 spec §1 保持一致）────────────────────
    private static final String[] FIELD_VALUES = {"title", "artist", "album", "package", "source"};
    private static final String[] FIELD_LABELS = {
            "歌名 title", "歌手 artist", "专辑 album", "包名 package", "来源 source"
    };
    private static final String[] TARGET_FIELDS = {"title", "artist", "album"};
    private static final String[] TARGET_FIELD_LABELS = {"歌名 title", "歌手 artist", "专辑 album"};
    private static final String[] CONDITION_OPS = {
            "equals", "equalsAny", "contains", "containsAny",
            "regex", "startsWith", "endsWith", "empty"
    };
    private static final String[] CONDITION_OP_LABELS = {
            "equals 全等", "equalsAny 任一全等", "contains 包含", "containsAny 任一包含",
            "regex 正则", "startsWith 开头", "endsWith 结尾", "empty 为空"
    };
    private static final String[] ACTION_OPS = {
            "extract", "replace", "set", "clear", "trim", "stripSuffix", "aliasArtist"
    };
    private static final String[] ACTION_OP_LABELS = {
            "extract 提取分组", "replace 正则替换", "set 设为字面量", "clear 清空字段",
            "trim 去首尾空白", "stripSuffix 去括号数字后缀", "aliasArtist 歌手别名"
    };

    private static final String[] FOLDER_ARTISTS = {
            "车载音乐", "车载", "未知", "未知歌手", "群星", "网络歌手", "热门音乐",
            "本地音乐", "文件夹", "Various Artists", "Unknown", "Music"
    };
    private static final String DASH_SPLIT_PATTERN = "(.{1,40})\\s*[-–—]\\s*(.{1,80})";
    private static final String NUMBER_SUFFIX_PATTERN =
            "[（(【\\[]\\s*\\d{1,3}\\s*[)）】\\]]\\s*$";

    // ── 当前曲目快照 ─────────────────────────────────────────────────────
    private boolean trackActive = false;
    private String trackPlayerName = "";
    private String trackTitle = "";
    private String trackArtist = "";
    private String trackPackage = "";
    private String trackSource = "";
    private String builtinPreviewLine = "";

    // ── 根布局与面板 ─────────────────────────────────────────────────────
    private LinearLayout rootLayout;
    private LinearLayout wizardPanel;
    private LinearLayout codePanel;

    // ── 顶部曲目卡片 ─────────────────────────────────────────────────────
    private TextView trackInfoText;
    private TextView builtinPreviewText;

    // ── 模式切换 ─────────────────────────────────────────────────────────
    private MaterialButton wizardModeButton;
    private MaterialButton codeModeButton;
    private TextView packInfoText;
    private boolean codeMode = false;

    // ── 图形向导 ─────────────────────────────────────────────────────────
    private Spinner conditionFieldSpinner;
    private Spinner conditionOpSpinner;
    private EditText conditionValueInput;
    private TextView conditionsSummary;

    private Spinner actionOpSpinner;
    private TextView actionHintText;
    private LinearLayout extractRow;
    private EditText extractPatternInput;
    private Spinner extractTargetFieldSpinner;
    private EditText extractTargetGroupInput;
    private LinearLayout replaceRow;
    private EditText replacePatternInput;
    private EditText replaceReplacementInput;
    private LinearLayout fieldRow;
    private Spinner actionFieldSpinner;
    private LinearLayout setValueRow;
    private EditText setValueInput;
    private LinearLayout stripPatternRow;
    private EditText stripSuffixPatternInput;
    private LinearLayout aliasRow;
    private EditText aliasMapInput;
    private TextView actionsSummary;

    private final List<JSONObject> pendingConditions = new ArrayList<>();
    private final List<JSONObject> pendingActions = new ArrayList<>();

    // ── JSON 代码面板 ────────────────────────────────────────────────────
    private EditText codeEditor;
    private TextView previewResultText;

    private boolean submitBusy = false;

    // ── 生命周期 ─────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 21) {
            getWindow().setStatusBarColor(COLOR_BACKGROUND);
            getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        }
        getWindow().getDecorView().setBackgroundColor(COLOR_BACKGROUND);
        setTitle("匹配规则编辑器");

        loadTrackData();
        String initialJson = buildSkeletonPackJson();
        buildUi(initialJson);

        // 与其它设置/编辑界面一致地套用自定义字体与界面缩放；
        // 之后再单独恢复代码编辑器的等宽字体，避免代码被主题字体覆盖。
        try {
            CustomFontStore.applyToViewTree(this, rootLayout);
        } catch (Throwable ignored) {
            // 字体应用失败不应影响规则编辑。
        }
        if (codeEditor != null) {
            codeEditor.setTypeface(Typeface.MONOSPACE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTrackCard();
    }

    // ── 界面构建 ─────────────────────────────────────────────────────────

    private void buildUi(String initialJson) {
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(COLOR_BACKGROUND);
        setContentView(rootLayout);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(12), dp(16), dp(4));
        TextView headerTitle = makeText("匹配规则编辑器", 18, COLOR_TEXT_PRIMARY, true);
        header.addView(headerTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        MaterialButton backButton = makeButton("返回", false);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        header.addView(backButton, new LinearLayout.LayoutParams(dp(78), dp(40)));
        rootLayout.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView subtitle = makeText(
                "图形向导 / JSON 代码  ·  规则保存后立即参与歌词匹配",
                12, COLOR_TEXT_SECONDARY, false);
        subtitle.setPadding(dp(16), 0, dp(16), dp(8));
        rootLayout.addView(subtitle, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        rootLayout.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(6), dp(12), dp(14));
        scrollView.addView(content, new FrameLayout.LayoutParams(-1, -2));

        buildTrackCard(content);
        buildModeCard(content);
        buildWizardCard(content);
        buildCodeCard(content, initialJson);
        buildBottomBar();

        refreshBuilderSummaries();
        applyActionParamVisibility(ACTION_OPS[0]);
        updatePackInfo();
        renderTrackCard();
        applyModeUi();
    }

    private void buildTrackCard(LinearLayout content) {
        LinearLayout card = SettingsStyleHelper.createCard(this, "🎵 当前曲目");
        content.addView(card, cardParams());

        trackInfoText = makeText("正在读取当前曲目…", 13, COLOR_TEXT, false);
        trackInfoText.setLineSpacing(0f, 1.25f);
        card.addView(trackInfoText);

        card.addView(SettingsStyleHelper.createGroupLabel(
                this, "内置规则对当前曲目的清洗结果"));
        builtinPreviewText = makeText("", 12, COLOR_TEXT_SECONDARY, false);
        builtinPreviewText.setLineSpacing(0f, 1.25f);
        card.addView(builtinPreviewText);

        MaterialButton refreshButton = makeButton("刷新当前曲目", false);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadTrackData();
                renderTrackCard();
                toast("已刷新当前曲目");
            }
        });
        card.addView(refreshButton, topMarginParams(10));

        card.addView(SettingsStyleHelper.createHelpText(this,
                "规则预览与「从当前歌曲生成」都以这里的曲目为对象；没有播放曲目时仍可编写规则。"));
    }

    private void buildModeCard(LinearLayout content) {
        LinearLayout card = SettingsStyleHelper.createCard(this, "🧭 编辑方式");
        content.addView(card, cardParams());

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        wizardModeButton = makeButton("图形向导", true);
        codeModeButton = makeButton("JSON 代码", false);
        wizardModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCodeMode(false);
            }
        });
        codeModeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setCodeMode(true);
            }
        });
        addWeighted(buttonRow, wizardModeButton, 1f, 8);
        addWeighted(buttonRow, codeModeButton, 1f, 0);
        card.addView(buttonRow);

        packInfoText = makeText("", 12, COLOR_TEXT_SECONDARY, false);
        packInfoText.setLineSpacing(0f, 1.2f);
        card.addView(packInfoText, topMarginParams(8));
    }

    private void buildWizardCard(LinearLayout content) {
        LinearLayout card = SettingsStyleHelper.createCard(this, "🧩 图形向导");
        wizardPanel = card;
        content.addView(card, cardParams());

        card.addView(SettingsStyleHelper.createHelpText(this,
                "条件全部满足（AND）才执行动作；动作按顺序执行，失败的动作为跳过。"
                        + "点「生成规则」会把当前条件/动作追加到下方 JSON 包，已有规则保持不变。"));

        // 条件区
        card.addView(SettingsStyleHelper.createGroupLabel(this, "条件（最多 6 条）"));
        LinearLayout spinnerRow = new LinearLayout(this);
        spinnerRow.setOrientation(LinearLayout.HORIZONTAL);
        conditionFieldSpinner = makeSpinner(FIELD_LABELS, 0);
        conditionOpSpinner = makeSpinner(CONDITION_OP_LABELS, 2);
        addWeighted(spinnerRow, conditionFieldSpinner, 1f, 8);
        addWeighted(spinnerRow, conditionOpSpinner, 1f, 0);
        card.addView(spinnerRow, topMarginParams(4));

        conditionValueInput = makeInput("条件值", false, false);
        card.addView(conditionValueInput, topMarginParams(8));
        conditionOpSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                updateConditionValueHint();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 无需处理。
            }
        });
        updateConditionValueHint();

        LinearLayout conditionButtons = new LinearLayout(this);
        conditionButtons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton addConditionButton = makeButton("添加条件", true);
        MaterialButton clearConditionButton = makeButton("清空条件", false);
        addConditionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddCondition();
            }
        });
        clearConditionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingConditions.clear();
                refreshBuilderSummaries();
                toast("已清空条件");
            }
        });
        addWeighted(conditionButtons, addConditionButton, 1f, 8);
        addWeighted(conditionButtons, clearConditionButton, 1f, 0);
        card.addView(conditionButtons, topMarginParams(8));

        conditionsSummary = makeText("", 12, COLOR_TEXT_SECONDARY, false);
        conditionsSummary.setLineSpacing(0f, 1.25f);
        card.addView(conditionsSummary, topMarginParams(6));

        card.addView(SettingsStyleHelper.createDivider(this));

        // 动作区
        card.addView(SettingsStyleHelper.createGroupLabel(this, "动作（1–8 条）"));
        actionOpSpinner = makeSpinner(ACTION_OP_LABELS, 0);
        card.addView(actionOpSpinner, topMarginParams(4));
        actionOpSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                if (position >= 0 && position < ACTION_OPS.length) {
                    applyActionParamVisibility(ACTION_OPS[position]);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 无需处理。
            }
        });

        actionHintText = SettingsStyleHelper.createHelpText(this, "");
        card.addView(actionHintText);

        LinearLayout paramsContainer = new LinearLayout(this);
        paramsContainer.setOrientation(LinearLayout.VERTICAL);

        // extract 参数
        extractRow = new LinearLayout(this);
        extractRow.setOrientation(LinearLayout.VERTICAL);
        extractRow.addView(SettingsStyleHelper.createLabel(this, "提取正则 pattern（≤200 字符）"));
        extractPatternInput = makeInput("例如 (.{1,40})\\s*[-–—]\\s*(.{1,80})", false, true);
        extractRow.addView(extractPatternInput, topMarginParams(4));
        extractRow.addView(SettingsStyleHelper.createLabel(this, "写入目标字段 / 分组号（1–9）"),
                topMarginParams(8));
        LinearLayout extractTargetsRow = new LinearLayout(this);
        extractTargetsRow.setOrientation(LinearLayout.HORIZONTAL);
        extractTargetFieldSpinner = makeSpinner(TARGET_FIELD_LABELS, 1);
        extractTargetGroupInput = makeInput("组号", false, false);
        addWeighted(extractTargetsRow, extractTargetFieldSpinner, 1f, 8);
        extractTargetsRow.addView(extractTargetGroupInput,
                new LinearLayout.LayoutParams(dp(96), -2));
        extractRow.addView(extractTargetsRow, topMarginParams(4));
        paramsContainer.addView(extractRow, topMarginParams(10));

        // replace 参数
        replaceRow = new LinearLayout(this);
        replaceRow.setOrientation(LinearLayout.VERTICAL);
        replaceRow.addView(SettingsStyleHelper.createLabel(this, "匹配正则 pattern"));
        replacePatternInput = makeInput("例如 \\s*\\(.*?\\)", false, true);
        replaceRow.addView(replacePatternInput, topMarginParams(4));
        replaceRow.addView(SettingsStyleHelper.createLabel(this, "替换文本 replacement（支持 $1–$9）"),
                topMarginParams(8));
        replaceReplacementInput = makeInput("例如 $1", false, false);
        replaceRow.addView(replaceReplacementInput, topMarginParams(4));
        paramsContainer.addView(replaceRow, topMarginParams(10));

        // 公共作用字段（set / clear / trim / stripSuffix）
        fieldRow = new LinearLayout(this);
        fieldRow.setOrientation(LinearLayout.VERTICAL);
        fieldRow.addView(SettingsStyleHelper.createLabel(this, "作用字段"));
        actionFieldSpinner = makeSpinner(TARGET_FIELD_LABELS, 0);
        fieldRow.addView(actionFieldSpinner, topMarginParams(4));
        paramsContainer.addView(fieldRow, topMarginParams(10));

        // set 参数
        setValueRow = new LinearLayout(this);
        setValueRow.setOrientation(LinearLayout.VERTICAL);
        setValueRow.addView(SettingsStyleHelper.createLabel(this, "字面量 value"));
        setValueInput = makeInput("例如 未知歌手", false, false);
        setValueRow.addView(setValueInput, topMarginParams(4));
        paramsContainer.addView(setValueRow, topMarginParams(10));

        // stripSuffix 参数
        stripPatternRow = new LinearLayout(this);
        stripPatternRow.setOrientation(LinearLayout.VERTICAL);
        stripPatternRow.addView(SettingsStyleHelper.createLabel(
                this, "后缀正则 pattern（留空使用默认：末尾括号数字）"));
        stripSuffixPatternInput = makeInput("例如 \\s*[（(]\\d{1,3}[)）]\\s*$", false, true);
        stripPatternRow.addView(stripSuffixPatternInput, topMarginParams(4));
        paramsContainer.addView(stripPatternRow, topMarginParams(10));

        // aliasArtist 参数
        aliasRow = new LinearLayout(this);
        aliasRow.setOrientation(LinearLayout.VERTICAL);
        aliasRow.addView(SettingsStyleHelper.createLabel(
                this, "别名映射（每行一个 别名=规范名，最多 50 项）"));
        aliasMapInput = makeInput("例如\nA-Lin=黄丽玲\nG.E.M.=邓紫棋", true, false);
        aliasMapInput.setMinHeight(dp(96));
        aliasRow.addView(aliasMapInput, topMarginParams(4));
        paramsContainer.addView(aliasRow, topMarginParams(10));

        card.addView(paramsContainer, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout actionButtons = new LinearLayout(this);
        actionButtons.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton addActionButton = makeButton("添加动作", true);
        MaterialButton clearActionButton = makeButton("清空动作", false);
        addActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAddAction();
            }
        });
        clearActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pendingActions.clear();
                refreshBuilderSummaries();
                toast("已清空动作");
            }
        });
        addWeighted(actionButtons, addActionButton, 1f, 8);
        addWeighted(actionButtons, clearActionButton, 1f, 0);
        card.addView(actionButtons, topMarginParams(12));

        actionsSummary = makeText("", 12, COLOR_TEXT_SECONDARY, false);
        actionsSummary.setLineSpacing(0f, 1.25f);
        card.addView(actionsSummary, topMarginParams(6));

        card.addView(SettingsStyleHelper.createDivider(this));

        MaterialButton generateButton = makeButton("生成规则（追加到 JSON 包）", true);
        generateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onGenerateRule();
            }
        });
        card.addView(generateButton);

        card.addView(SettingsStyleHelper.createHelpText(this,
                "生成时会按 spec 校验整个规则包，失败原因会以中文提示；成功后请记得保存启用。"));
    }

    private void buildCodeCard(LinearLayout content, String initialJson) {
        LinearLayout card = SettingsStyleHelper.createCard(this, "🧾 JSON 代码");
        codePanel = card;
        content.addView(card, cardParams());

        card.addView(SettingsStyleHelper.createHelpText(this,
                "JSON 是规则的唯一事实来源；向导生成的规则会直接写回这里。"
                        + "保存前请先「校验」，失败原因会以中文提示。"));

        codeEditor = makeInput("规则包 JSON", true, true);
        codeEditor.setTextSize(12.5f);
        codeEditor.setHorizontallyScrolling(false);
        codeEditor.setText(initialJson);
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-1, dp(300));
        codeParams.topMargin = dp(8);
        card.addView(codeEditor, codeParams);

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton validateButton = makeButton("校验", false);
        MaterialButton formatButton = makeButton("格式化", false);
        validateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validateJson();
            }
        });
        formatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                formatJson();
            }
        });
        addWeighted(rowOne, validateButton, 1f, 8);
        addWeighted(rowOne, formatButton, 1f, 0);
        card.addView(rowOne, topMarginParams(8));

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton sampleButton = makeButton("插入示例", false);
        MaterialButton templateButton = makeButton("从当前歌曲生成", false);
        sampleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insertSample();
            }
        });
        templateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insertSongTemplate();
            }
        });
        addWeighted(rowTwo, sampleButton, 1f, 8);
        addWeighted(rowTwo, templateButton, 1f, 0);
        card.addView(rowTwo, topMarginParams(8));

        MaterialButton previewButton = makeButton("预览（当前曲目 + 当前 JSON 包）", true);
        previewButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                previewJson();
            }
        });
        card.addView(previewButton, topMarginParams(8));

        card.addView(SettingsStyleHelper.createGroupLabel(this, "预览 / 校验结果"));
        previewResultText = makeText("尚未预览。", 12, COLOR_TEXT_SECONDARY, false);
        previewResultText.setLineSpacing(0f, 1.25f);
        card.addView(previewResultText);
    }

    private void buildBottomBar() {
        View divider = new View(this);
        divider.setBackgroundColor(COLOR_CARD_BORDER);
        rootLayout.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(COLOR_BOTTOM_BAR_BG);
        bar.setPadding(dp(12), dp(10), dp(12), dp(12));
        rootLayout.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout rowOne = new LinearLayout(this);
        rowOne.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton saveButton = makeButton("保存并启用", true);
        MaterialButton submitButton = makeButton("提交到市场", false);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSave();
            }
        });
        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onSubmitToMarket();
            }
        });
        addWeighted(rowOne, saveButton, 1f, 8);
        addWeighted(rowOne, submitButton, 1f, 0);
        bar.addView(rowOne);

        LinearLayout rowTwo = new LinearLayout(this);
        rowTwo.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton copyButton = makeButton("复制 JSON", false);
        MaterialButton importButton = makeButton("导入 JSON", false);
        copyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCopyJson();
            }
        });
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onImportJson();
            }
        });
        addWeighted(rowTwo, copyButton, 1f, 8);
        addWeighted(rowTwo, importButton, 1f, 0);
        bar.addView(rowTwo, topMarginParams(8));

        LinearLayout rowThree = new LinearLayout(this);
        rowThree.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton wikiButton = makeButton("规则文档", false);
        MaterialButton repoButton = makeButton("模板仓库", false);
        wikiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(RULE_WIKI_URL);
            }
        });
        repoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openUrl(RULE_REPO_URL);
            }
        });
        addWeighted(rowThree, wikiButton, 1f, 8);
        addWeighted(rowThree, repoButton, 1f, 0);
        bar.addView(rowThree, topMarginParams(8));
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (RuntimeException e) {
            toast("没有可打开链接的应用：" + url);
        }
    }

    // ── 视图工厂 ─────────────────────────────────────────────────────────

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private TextView makeText(String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) {
            view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        return view;
    }

    private MaterialButton makeButton(String label, boolean primary) {
        MaterialButton button = new MaterialButton(this);
        button.setText(label);
        button.setTextSize(13f);
        button.setAllCaps(false);
        button.setCornerRadius(dp(15));
        button.setMinHeight(dp(44));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        if (primary) {
            button.setBackgroundTintList(ColorStateList.valueOf(COLOR_ACCENT));
            button.setTextColor(COLOR_ON_ACCENT);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(COLOR_BUTTON_BG));
            button.setTextColor(COLOR_TEXT);
        }
        return button;
    }

    private EditText makeInput(String hint, boolean multiline, boolean monospace) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setHintTextColor(COLOR_TEXT_HINT);
        input.setTextColor(COLOR_TEXT_PRIMARY);
        input.setTextSize(14f);
        input.setMinHeight(dp(44));
        if (multiline) {
            input.setSingleLine(false);
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
            input.setMinLines(3);
        } else {
            input.setSingleLine(true);
            input.setGravity(Gravity.CENTER_VERTICAL);
            input.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        }
        if (monospace) {
            input.setTypeface(Typeface.MONOSPACE);
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_INPUT_BG);
        background.setCornerRadius(dp(10));
        background.setStroke(Math.max(1, dp(1)), COLOR_CARD_BORDER);
        input.setBackground(background);
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        return input;
    }

    private Spinner makeSpinner(String[] labels, int selection) {
        Spinner spinner = new Spinner(this, Spinner.MODE_DIALOG);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        if (selection >= 0 && selection < labels.length) {
            spinner.setSelection(selection);
        }
        return spinner;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams topMarginParams(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(topDp);
        return params;
    }

    private void addWeighted(LinearLayout row, View view, float weight, int rightMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, weight);
        params.rightMargin = dp(rightMarginDp);
        row.addView(view, params);
    }

    // ── 当前曲目 + 内置规则预览 ───────────────────────────────────────────

    private void loadTrackData() {
        trackActive = false;
        trackPlayerName = "";
        trackTitle = "";
        trackArtist = "";
        trackPackage = "";
        trackSource = "";
        try {
            MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(this));
            if (snapshot != null) {
                trackActive = snapshot.active;
                trackPlayerName = safe(snapshot.sourceName);
                trackTitle = safe(snapshot.title);
                trackArtist = safe(snapshot.artist);
            }
            trackSource = safe(MusicStateStore.activeSourceId());
            // 按 spec §2.1 由 core 增加的稳定签名（当前播放器包名，无则空串）。
            trackPackage = safe(MusicStateStore.activeSourcePackage());
        } catch (Throwable ignored) {
            // 读取播放状态失败时保持空数据，仍允许编辑规则。
        }
        builtinPreviewLine = computeBuiltinPreview();
    }

    private void renderTrackCard() {
        if (trackInfoText == null) {
            return;
        }
        trackInfoText.setText(buildTrackInfoText());
        if (builtinPreviewText != null) {
            builtinPreviewText.setText(builtinPreviewLine);
        }
    }

    private void refreshTrackCard() {
        loadTrackData();
        renderTrackCard();
    }

    private String buildTrackInfoText() {
        StringBuilder builder = new StringBuilder();
        builder.append("播放器：")
                .append(safe(trackPlayerName).isEmpty() ? "（未识别）" : trackPlayerName);
        if (!safe(trackPackage).isEmpty()) {
            builder.append("（").append(trackPackage).append("）");
        }
        builder.append('\n');
        builder.append("来源：")
                .append(safe(trackSource).isEmpty() ? "（未知）" : trackSource)
                .append("  ·  状态：").append(trackActive ? "播放中" : "未在播放");
        builder.append('\n');
        if (safe(trackTitle).isEmpty() && safe(trackArtist).isEmpty()) {
            builder.append("原始：当前没有正在播放的曲目（仍可编写规则）");
        } else {
            builder.append("原始：").append(displayValue(trackTitle))
                    .append(" / ").append(displayValue(trackArtist));
        }
        return builder.toString();
    }

    private String computeBuiltinPreview() {
        try {
            MatchOutput output = MatchRuleEngine.apply(BuiltinMatchRules.packs(), buildMatchInput());
            if (output == null) {
                return "内置规则暂不可用：引擎没有返回结果。";
            }
            StringBuilder builder = new StringBuilder();
            builder.append("primary：").append(displayValue(output.primaryTitle))
                    .append(" / ").append(displayValue(output.primaryArtist));
            if (!safe(output.primaryAlbum).isEmpty()) {
                builder.append(" / ").append(output.primaryAlbum);
            }
            builder.append('\n');
            builder.append(output.changed ? "（与原始值不同）" : "（与原始值相同）");
            builder.append('\n');
            appendCandidates(builder, output.candidates, 6);
            return builder.toString();
        } catch (Throwable error) {
            return "内置规则暂不可用：" + messageOf(error);
        }
    }

    private MatchInput buildMatchInput() {
        // TODO(MatchInput.album)：MusicSnapshot 目前不暴露 album（spec §2.1 只要求现有字段），先传空串。
        return new MatchInput(safe(trackTitle), safe(trackArtist), "",
                safe(trackPackage), safe(trackSource));
    }

    private void appendCandidates(StringBuilder builder, List<MatchOutput.Candidate> candidates,
                                  int limit) {
        if (candidates == null || candidates.isEmpty()) {
            builder.append("候选：无");
            return;
        }
        builder.append("候选 ").append(candidates.size()).append(" 条：\n");
        int count = Math.min(candidates.size(), limit);
        for (int i = 0; i < count; i++) {
            MatchOutput.Candidate candidate = candidates.get(i);
            if (candidate == null) {
                continue;
            }
            builder.append("  ").append(i + 1).append(". ")
                    .append(displayValue(candidate.title)).append(" / ")
                    .append(displayValue(candidate.artist));
            String ruleId = safe(candidate.ruleId);
            if (!ruleId.isEmpty()) {
                builder.append("（").append(ruleId).append("）");
            }
            builder.append('\n');
        }
        if (candidates.size() > count) {
            builder.append("  … 共 ").append(candidates.size()).append(" 条\n");
        }
    }

    // ── 模式切换 ─────────────────────────────────────────────────────────

    private void setCodeMode(boolean code) {
        if (codeMode == code) {
            return;
        }
        codeMode = code;
        applyModeUi();
        updatePackInfo();
    }

    private void applyModeUi() {
        if (wizardPanel == null || codePanel == null) {
            return;
        }
        wizardPanel.setVisibility(codeMode ? View.GONE : View.VISIBLE);
        codePanel.setVisibility(codeMode ? View.VISIBLE : View.GONE);
        styleModeButton(wizardModeButton, !codeMode);
        styleModeButton(codeModeButton, codeMode);
    }

    private void styleModeButton(MaterialButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setBackgroundTintList(ColorStateList.valueOf(
                selected ? COLOR_ACCENT : COLOR_BUTTON_BG));
        button.setTextColor(selected ? COLOR_ON_ACCENT : COLOR_TEXT);
    }

    private void updatePackInfo() {
        if (packInfoText == null || codeEditor == null) {
            return;
        }
        String text = currentJson();
        if (text.isEmpty()) {
            packInfoText.setText("当前 JSON 为空，可以先在「图形向导」生成规则。");
            return;
        }
        try {
            JSONObject pack = new JSONObject(text);
            String name = pack.optString("name", "");
            String id = pack.optString("id", "");
            JSONArray rules = pack.optJSONArray("rules");
            int count = rules == null ? 0 : rules.length();
            StringBuilder builder = new StringBuilder();
            builder.append("当前规则包：").append(name.isEmpty() ? "未命名" : name)
                    .append("  ·  ").append(count).append(" 条规则")
                    .append("  ·  id=").append(id.isEmpty() ? "（缺失）" : id);
            if (count == 0) {
                builder.append("（尚未添加规则）");
            } else {
                try {
                    MatchRulePack.parse(text);
                    builder.append("  ·  校验通过");
                } catch (IllegalArgumentException error) {
                    builder.append("  ·  校验未通过：").append(messageOf(error));
                }
            }
            packInfoText.setText(builder.toString());
        } catch (Exception error) {
            packInfoText.setText("当前 JSON 还不是合法规则包：" + messageOf(error));
        }
    }

    // ── 图形向导：条件 ───────────────────────────────────────────────────

    private void onAddCondition() {
        try {
            if (pendingConditions.size() >= 6) {
                toast("条件最多 6 个");
                return;
            }
            int fieldPosition = conditionFieldSpinner.getSelectedItemPosition();
            int opPosition = conditionOpSpinner.getSelectedItemPosition();
            if (fieldPosition < 0 || fieldPosition >= FIELD_VALUES.length
                    || opPosition < 0 || opPosition >= CONDITION_OPS.length) {
                toast("请先选择字段和操作");
                return;
            }
            String field = FIELD_VALUES[fieldPosition];
            String op = CONDITION_OPS[opPosition];
            String raw = conditionValueInput.getText().toString().trim();

            JSONObject condition = new JSONObject();
            condition.put("field", field);
            condition.put("op", op);
            if ("equalsAny".equals(op) || "containsAny".equals(op)) {
                List<String> values = splitConditionValues(raw);
                if (values.isEmpty()) {
                    toast("该操作需要至少一个值，多个值用逗号分隔");
                    return;
                }
                if (values.size() > 20) {
                    toast("值最多 20 个");
                    return;
                }
                JSONArray array = new JSONArray();
                for (String value : values) {
                    array.put(value);
                }
                condition.put("value", array);
            } else if (!"empty".equals(op)) {
                if (raw.isEmpty()) {
                    toast("请填写条件值");
                    return;
                }
                if ("regex".equals(op) && raw.length() > 200) {
                    toast("正则最长 200 个字符");
                    return;
                }
                condition.put("value", raw);
            }
            pendingConditions.add(condition);
            conditionValueInput.setText("");
            refreshBuilderSummaries();
            toast("已添加条件 " + pendingConditions.size());
        } catch (Exception error) {
            toast("添加条件失败：" + messageOf(error));
        }
    }

    private List<String> splitConditionValues(String raw) {
        List<String> values = new ArrayList<>();
        if (raw == null) {
            return values;
        }
        String[] parts = raw.split("[,，、;；\\n]");
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String value = part.trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private void updateConditionValueHint() {
        if (conditionValueInput == null || conditionOpSpinner == null) {
            return;
        }
        int position = conditionOpSpinner.getSelectedItemPosition();
        if (position < 0 || position >= CONDITION_OPS.length) {
            return;
        }
        String op = CONDITION_OPS[position];
        String hint;
        if ("equalsAny".equals(op) || "containsAny".equals(op)) {
            hint = "多个值用逗号分隔，例如：车载音乐,车载";
        } else if ("empty".equals(op)) {
            hint = "empty 操作不需要值，留空即可";
        } else if ("regex".equals(op)) {
            hint = "正则表达式（≤200 字符），例如 (.{1,40})\\s*[-–—]\\s*(.{1,80})";
        } else if ("equals".equals(op)) {
            hint = "忽略大小写、去首尾空白后全等";
        } else if ("startsWith".equals(op)) {
            hint = "以该值开头";
        } else if ("endsWith".equals(op)) {
            hint = "以该值结尾";
        } else {
            hint = "包含该值";
        }
        conditionValueInput.setHint(hint);
    }

    // ── 图形向导：动作 ───────────────────────────────────────────────────

    private void onAddAction() {
        try {
            if (pendingActions.size() >= 8) {
                toast("动作最多 8 个");
                return;
            }
            int opPosition = actionOpSpinner.getSelectedItemPosition();
            if (opPosition < 0 || opPosition >= ACTION_OPS.length) {
                toast("请先选择动作类型");
                return;
            }
            String op = ACTION_OPS[opPosition];
            JSONObject action = new JSONObject();
            action.put("type", op);

            if ("extract".equals(op)) {
                String pattern = extractPatternInput.getText().toString().trim();
                if (pattern.isEmpty()) {
                    throw new IllegalArgumentException("extract 需要提取正则 pattern");
                }
                if (pattern.length() > 200) {
                    throw new IllegalArgumentException("正则最长 200 个字符");
                }
                int group = parseGroupNumber(
                        extractTargetGroupInput.getText().toString().trim());
                JSONObject targets = new JSONObject();
                int targetPosition = extractTargetFieldSpinner.getSelectedItemPosition();
                if (targetPosition < 0 || targetPosition >= TARGET_FIELDS.length) {
                    throw new IllegalArgumentException("请选择写入的目标字段");
                }
                targets.put(TARGET_FIELDS[targetPosition], group);
                action.put("pattern", pattern);
                action.put("targets", targets);
            } else if ("replace".equals(op)) {
                String pattern = replacePatternInput.getText().toString().trim();
                if (pattern.isEmpty()) {
                    throw new IllegalArgumentException("replace 需要匹配正则 pattern");
                }
                if (pattern.length() > 200) {
                    throw new IllegalArgumentException("正则最长 200 个字符");
                }
                action.put("pattern", pattern);
                action.put("replacement", replaceReplacementInput.getText().toString());
            } else if ("set".equals(op)) {
                String value = setValueInput.getText().toString();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("set 需要字面量 value");
                }
                action.put("field", selectedActionField());
                action.put("value", value);
            } else if ("clear".equals(op) || "trim".equals(op)) {
                action.put("field", selectedActionField());
            } else if ("stripSuffix".equals(op)) {
                action.put("field", selectedActionField());
                String pattern = stripSuffixPatternInput.getText().toString().trim();
                if (!pattern.isEmpty()) {
                    if (pattern.length() > 200) {
                        throw new IllegalArgumentException("正则最长 200 个字符");
                    }
                    action.put("pattern", pattern);
                }
            } else if ("aliasArtist".equals(op)) {
                action.put("map", parseAliasMap(aliasMapInput.getText().toString()));
            } else {
                throw new IllegalArgumentException("未知动作类型：" + op);
            }
            pendingActions.add(action);
            refreshBuilderSummaries();
            toast("已添加动作 " + pendingActions.size() + "：" + op);
        } catch (IllegalArgumentException error) {
            toast(messageOf(error));
        } catch (Exception error) {
            toast("添加动作失败：" + messageOf(error));
        }
    }

    private String selectedActionField() {
        int position = actionFieldSpinner.getSelectedItemPosition();
        if (position < 0 || position >= TARGET_FIELDS.length) {
            return TARGET_FIELDS[0];
        }
        return TARGET_FIELDS[position];
    }

    private int parseGroupNumber(String raw) {
        if (raw.isEmpty()) {
            return 1;
        }
        int group;
        try {
            group = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("分组号必须是 1–9 的数字");
        }
        if (group < 1 || group > 9) {
            throw new IllegalArgumentException("分组号必须是 1–9 的数字");
        }
        return group;
    }

    private JSONObject parseAliasMap(String text) throws JSONException {
        JSONObject map = new JSONObject();
        String[] lines = text == null ? new String[0] : text.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            int index = line.indexOf('=');
            if (index < 0) {
                index = line.indexOf('→');
            }
            if (index < 0) {
                throw new IllegalArgumentException(
                        "第 " + (i + 1) + " 行格式应为 别名=规范名");
            }
            String alias = line.substring(0, index).trim();
            String canonical = line.substring(index + 1).trim();
            if (alias.isEmpty() || canonical.isEmpty()) {
                throw new IllegalArgumentException(
                        "第 " + (i + 1) + " 行别名和规范名都不能为空");
            }
            if (alias.length() > 40 || canonical.length() > 40) {
                throw new IllegalArgumentException(
                        "第 " + (i + 1) + " 行别名或规范名超过 40 个字符");
            }
            if (map.length() >= 50) {
                throw new IllegalArgumentException("别名映射最多 50 项");
            }
            map.put(alias, canonical);
        }
        if (map.length() == 0) {
            throw new IllegalArgumentException("请填写别名映射，每行一个 别名=规范名");
        }
        return map;
    }

    private void applyActionParamVisibility(String op) {
        boolean extract = "extract".equals(op);
        boolean replace = "replace".equals(op);
        boolean set = "set".equals(op);
        boolean clear = "clear".equals(op);
        boolean trim = "trim".equals(op);
        boolean stripSuffix = "stripSuffix".equals(op);
        boolean alias = "aliasArtist".equals(op);
        if (extractRow != null) {
            extractRow.setVisibility(extract ? View.VISIBLE : View.GONE);
        }
        if (replaceRow != null) {
            replaceRow.setVisibility(replace ? View.VISIBLE : View.GONE);
        }
        if (fieldRow != null) {
            fieldRow.setVisibility(set || clear || trim || stripSuffix
                    ? View.VISIBLE : View.GONE);
        }
        if (setValueRow != null) {
            setValueRow.setVisibility(set ? View.VISIBLE : View.GONE);
        }
        if (stripPatternRow != null) {
            stripPatternRow.setVisibility(stripSuffix ? View.VISIBLE : View.GONE);
        }
        if (aliasRow != null) {
            aliasRow.setVisibility(alias ? View.VISIBLE : View.GONE);
        }
        if (actionHintText != null) {
            actionHintText.setText(actionHint(op));
        }
    }

    private String actionHint(String op) {
        if ("extract".equals(op)) {
            return "用正则从歌名/歌手/专辑中提取编号分组，写入目标字段。";
        }
        if ("replace".equals(op)) {
            return "在 title 中替换正则匹配的文本（可用 $1–$9 引用分组）。";
        }
        if ("set".equals(op)) {
            return "把目标字段设为固定值。";
        }
        if ("clear".equals(op)) {
            return "清空目标字段。";
        }
        if ("trim".equals(op)) {
            return "去掉目标字段首尾空白。";
        }
        if ("stripSuffix".equals(op)) {
            return "去掉歌名末尾的括号数字后缀（默认规则覆盖（01）/(1)/【02】/[3] 等）。";
        }
        if ("aliasArtist".equals(op)) {
            return "按 / , 、 & ; 与 feat./ft. 拆分歌手串，逐段映射别名后以 / 连接。";
        }
        return "";
    }

    private void refreshBuilderSummaries() {
        if (conditionsSummary != null) {
            StringBuilder builder = new StringBuilder();
            if (pendingConditions.isEmpty()) {
                builder.append("尚未添加条件：无条件时该规则对所有曲目生效。");
            } else {
                builder.append("已添加条件（全部满足）：");
                for (int i = 0; i < pendingConditions.size(); i++) {
                    builder.append('\n').append(i + 1).append(". ")
                            .append(describeCondition(pendingConditions.get(i)));
                }
            }
            conditionsSummary.setText(builder.toString());
        }
        if (actionsSummary != null) {
            StringBuilder builder = new StringBuilder();
            if (pendingActions.isEmpty()) {
                builder.append("尚未添加动作：至少添加 1 个动作才能生成规则。");
            } else {
                builder.append("已添加动作（按顺序执行）：");
                for (int i = 0; i < pendingActions.size(); i++) {
                    builder.append('\n').append(i + 1).append(". ")
                            .append(describeAction(pendingActions.get(i)));
                }
            }
            actionsSummary.setText(builder.toString());
        }
    }

    private String describeCondition(JSONObject condition) {
        if (condition == null) {
            return "";
        }
        String field = condition.optString("field", "");
        String op = condition.optString("op", "");
        String valueText = describeValue(condition.opt("value"));
        StringBuilder builder = new StringBuilder();
        builder.append(field).append(' ').append(op);
        if (!valueText.isEmpty()) {
            builder.append(' ').append(valueText);
        }
        return builder.toString();
    }

    private String describeAction(JSONObject action) {
        if (action == null) {
            return "";
        }
        String op = action.optString("type", "");
        StringBuilder builder = new StringBuilder(op);
        if ("extract".equals(op)) {
            builder.append(" pattern=").append(shorten(action.optString("pattern", ""), 48));
            JSONObject targets = action.optJSONObject("targets");
            if (targets != null) {
                Iterator<String> keys = targets.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    builder.append(" → ").append(key).append('#')
                            .append(targets.optInt(key, 1));
                }
            }
        } else if ("replace".equals(op)) {
            builder.append(" pattern=").append(shorten(action.optString("pattern", ""), 40))
                    .append(" → ").append(action.optString("replacement", ""));
        } else if ("set".equals(op)) {
            builder.append(' ').append(action.optString("field", "title"))
                    .append(" = ").append(action.optString("value", ""));
        } else if ("aliasArtist".equals(op)) {
            JSONObject map = action.optJSONObject("map");
            builder.append(" ").append(map == null ? 0 : map.length()).append(" 条映射");
        } else {
            String field = action.optString("field", "");
            if (!field.isEmpty()) {
                builder.append(' ').append(field);
            }
            if ("stripSuffix".equals(op) && !action.optString("pattern", "").isEmpty()) {
                builder.append(" pattern=").append(shorten(action.optString("pattern", ""), 40));
            }
        }
        return builder.toString();
    }

    private String describeValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                if (i > 0) {
                    builder.append(" / ");
                }
                builder.append(array.optString(i, ""));
            }
            return builder.toString();
        }
        return String.valueOf(value);
    }

    // ── 生成规则 ─────────────────────────────────────────────────────────

    private void onGenerateRule() {
        try {
            if (pendingConditions.isEmpty() && pendingActions.isEmpty()) {
                toast("请先添加至少一个条件或动作");
                return;
            }
            if (pendingActions.isEmpty()) {
                toast("至少需要 1 个动作才能生成规则");
                return;
            }
            String current = currentJson();
            if (current.isEmpty()) {
                current = buildSkeletonPackJson();
            }
            JSONObject pack = new JSONObject(current);
            JSONArray rules = pack.optJSONArray("rules");
            if (rules == null) {
                rules = new JSONArray();
                pack.put("rules", rules);
            }
            if (rules.length() >= 64) {
                toast("一个规则包最多 64 条规则");
                return;
            }

            JSONObject rule = new JSONObject();
            rule.put("id", nextRuleId(rules));
            JSONArray when = new JSONArray();
            for (JSONObject condition : pendingConditions) {
                when.put(condition);
            }
            rule.put("when", when);
            JSONArray then = new JSONArray();
            for (JSONObject action : pendingActions) {
                then.put(action);
            }
            rule.put("then", then);
            rules.put(rule);

            String candidate = pack.toString(2);
            // 用核心的 MatchRulePack.parse 校验整包，失败消息为中文，可直接展示。
            MatchRulePack.parse(candidate);

            codeEditor.setText(candidate);
            codeEditor.setSelection(0);
            pendingConditions.clear();
            pendingActions.clear();
            refreshBuilderSummaries();
            updatePackInfo();
            toast("已生成第 " + rules.length() + " 条规则，记得「保存并启用」");
        } catch (IllegalArgumentException error) {
            toast("规则校验失败：" + messageOf(error));
        } catch (JSONException error) {
            toast("当前 JSON 无法解析，请先切到「JSON 代码」修复：" + messageOf(error));
        } catch (Exception error) {
            toast("生成规则失败：" + messageOf(error));
        }
    }

    private String nextRuleId(JSONArray rules) {
        int index = (rules == null ? 0 : rules.length()) + 1;
        return "rule-" + (System.currentTimeMillis() / 1000L) + "-" + index;
    }

    // ── JSON 代码模式 ────────────────────────────────────────────────────

    private String currentJson() {
        return codeEditor == null ? "" : codeEditor.getText().toString().trim();
    }

    private void validateJson() {
        String text = currentJson();
        if (text.isEmpty()) {
            toast("当前 JSON 为空");
            return;
        }
        try {
            MatchRulePack pack = MatchRulePack.parse(text);
            int count = pack.rules == null ? 0 : pack.rules.size();
            previewResultText.setText("校验通过：" + safe(pack.name)
                    + "  ·  " + count + " 条规则  ·  id=" + safe(pack.id));
            updatePackInfo();
            toast("JSON 校验通过：" + count + " 条规则");
        } catch (IllegalArgumentException error) {
            previewResultText.setText("校验失败：" + messageOf(error));
            toast("JSON 校验失败：" + messageOf(error));
        } catch (Exception error) {
            previewResultText.setText("校验失败：" + messageOf(error));
            toast("JSON 校验失败：" + messageOf(error));
        }
    }

    private void formatJson() {
        String text = currentJson();
        if (text.isEmpty()) {
            toast("当前 JSON 为空");
            return;
        }
        try {
            JSONObject json = new JSONObject(text);
            codeEditor.setText(json.toString(2));
            codeEditor.setSelection(0);
            updatePackInfo();
            toast("已格式化 JSON");
        } catch (JSONException error) {
            toast("格式化失败：JSON 语法错误，" + messageOf(error));
        } catch (Exception error) {
            toast("格式化失败：" + messageOf(error));
        }
    }

    private void insertSample() {
        String sample = buildSampleJson();
        if (sample.isEmpty()) {
            toast("示例生成失败，请重试");
            return;
        }
        replaceJsonWithConfirm("插入示例", sample,
                "示例包限定只对 com.example.echo 生效；可在 scope.packages 中改成当前播放器包名。");
    }

    private void insertSongTemplate() {
        String template = buildSongTemplateJson();
        if (template.isEmpty()) {
            toast("模板生成失败，请重试");
            return;
        }
        replaceJsonWithConfirm("从当前歌曲生成", template,
                "已生成内置规则同款模板；当前曲目仅用于预览，模板不会自动绑定这首歌。");
    }

    private void replaceJsonWithConfirm(final String title, final String json,
                                        final String successNote) {
        String current = currentJson();
        boolean hasRules = false;
        if (!current.isEmpty()) {
            try {
                JSONObject jsonObject = new JSONObject(current);
                JSONArray rules = jsonObject.optJSONArray("rules");
                hasRules = rules != null && rules.length() > 0;
            } catch (Exception ignored) {
                hasRules = true;
            }
        }
        if (!hasRules) {
            applyJsonReplacement(json, successNote);
            return;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage("当前 JSON 包已有规则，继续会替换未保存的编辑。确定吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("替换", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        applyJsonReplacement(json, successNote);
                    }
                })
                .show();
        if (dialog == null) {
            applyJsonReplacement(json, successNote);
        }
    }

    private void applyJsonReplacement(String json, String successNote) {
        codeEditor.setText(json);
        codeEditor.setSelection(0);
        setCodeMode(true);
        updatePackInfo();
        previewResultText.setText("已载入新 JSON，尚未保存。");
        toastLong(successNote);
    }

    private void previewJson() {
        String text = currentJson();
        if (text.isEmpty()) {
            toast("当前 JSON 为空");
            return;
        }
        try {
            MatchRulePack pack = MatchRulePack.parse(text);
            List<MatchRulePack> packs = new ArrayList<>();
            List<MatchRulePack> builtinPacks = BuiltinMatchRules.packs();
            if (builtinPacks != null) {
                packs.addAll(builtinPacks);
            }
            packs.add(pack);
            MatchOutput output = MatchRuleEngine.apply(packs, buildMatchInput());
            if (output == null) {
                toast("预览失败：引擎没有返回结果");
                return;
            }
            previewResultText.setText(describePreview(safe(pack.name), output));
            toast("预览已更新");
        } catch (IllegalArgumentException error) {
            previewResultText.setText("预览失败：" + messageOf(error));
            toast("预览失败：" + messageOf(error));
        } catch (Exception error) {
            previewResultText.setText("预览失败：" + messageOf(error));
            toast("预览失败：" + messageOf(error));
        }
    }

    private String describePreview(String packName, MatchOutput output) {
        StringBuilder builder = new StringBuilder();
        builder.append("预览规则包：").append(packName.isEmpty() ? "未命名" : packName)
                .append("（内置规则 + 当前包）");
        builder.append('\n');
        builder.append("原始：").append(displayValue(trackTitle))
                .append(" / ").append(displayValue(trackArtist));
        builder.append('\n');
        builder.append("清洗后：").append(displayValue(output.primaryTitle))
                .append(" / ").append(displayValue(output.primaryArtist));
        if (!safe(output.primaryAlbum).isEmpty()) {
            builder.append(" / ").append(output.primaryAlbum);
        }
        builder.append('\n');
        builder.append(output.changed ? "结果有变化。" : "结果与原始值相同。");
        builder.append('\n');
        appendCandidates(builder, output.candidates, 8);
        return builder.toString();
    }

    // ── 保存 / 提交 / 复制 / 导入 ────────────────────────────────────────

    private void onSave() {
        String text = currentJson();
        if (text.isEmpty()) {
            toast("当前没有可保存的 JSON");
            return;
        }
        try {
            MatchRulePack pack = MatchRulePack.parse(text);
            List<MatchRulePack> userPacks = MatchRuleStore.userPacks(this);
            List<MatchRulePack> packs = new ArrayList<>();
            if (userPacks != null) {
                packs.addAll(userPacks);
            }
            boolean replaced = false;
            for (int i = 0; i < packs.size(); i++) {
                MatchRulePack existing = packs.get(i);
                if (existing != null && safe(existing.id).equals(safe(pack.id))) {
                    packs.set(i, pack);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                packs.add(pack);
            }
            MatchRuleStore.saveUserPacks(this, packs);
            // 个人规则包保存后即启用；重载当前歌词让新规则立刻参与匹配。
            try {
                MusicStateStore.reloadLyrics(this);
            } catch (Throwable ignored) {
                // 没有播放曲目或重载失败不影响保存结果。
            }
            updatePackInfo();
            toastLong((replaced ? "已更新并启用：" : "已保存并启用：")
                    + safe(pack.name) + "（共 " + packs.size() + " 个个人规则包）");
        } catch (IllegalArgumentException error) {
            toast("保存失败：" + messageOf(error));
        } catch (Exception error) {
            toast("保存失败：" + messageOf(error));
        }
    }

    private void onSubmitToMarket() {
        String text = currentJson();
        if (text.isEmpty()) {
            toast("当前没有可提交的 JSON");
            return;
        }
        final MatchRulePack pack;
        try {
            pack = MatchRulePack.parse(text);
        } catch (IllegalArgumentException error) {
            toast("提交失败：规则包校验未通过，" + messageOf(error));
            return;
        } catch (Exception error) {
            toast("提交失败：" + messageOf(error));
            return;
        }

        final EditText authorInput = makeInput("作者名（最多 40 字，会展示在规则市场）",
                false, false);
        authorInput.setText(safe(pack.author));
        final EditText descriptionInput = makeInput("提交说明（可选，最多 200 字）", true, false);
        descriptionInput.setText(safe(pack.description));
        descriptionInput.setMinHeight(dp(96));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(4), dp(16), 0);
        container.addView(authorInput);
        container.addView(descriptionInput, topMarginParams(8));

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("提交到规则市场")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", null)
                .show();
        if (dialog == null) {
            toast("无法打开提交对话框");
            return;
        }
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String author = authorInput.getText().toString().trim();
                        String description = descriptionInput.getText().toString().trim();
                        if (author.isEmpty()) {
                            toast("请填写作者名");
                            return;
                        }
                        if (author.length() > 40) {
                            toast("作者名最多 40 个字符");
                            return;
                        }
                        if (description.length() > 200) {
                            toast("提交说明最多 200 个字符");
                            return;
                        }
                        dialog.dismiss();
                        submitPack(pack, description, author);
                    }
                });
    }

    private void submitPack(final MatchRulePack pack, final String description,
                            final String author) {
        if (submitBusy) {
            toast("正在提交，请稍候…");
            return;
        }
        submitBusy = true;
        toastLong("正在提交到规则市场，请稍候…");
        try {
            // TODO(MatchRulesClient)：按 spec §4.1 的 4 参签名 submit(pack, description,
            //  author, Callback) 与嵌套 Callback/Result/SubmitResult 调用；若并行实现采用
            //  3 参版本，需要在这里同步调整。
            MatchRulesClient.submit(pack, description, author,
                    new MatchRulesClient.Callback<MatchRulesClient.SubmitResult>() {
                        @Override
                        public void complete(
                                final MatchRulesClient.Result<MatchRulesClient.SubmitResult> result) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    onSubmitResult(pack, result);
                                }
                            });
                        }
                    });
        } catch (Throwable error) {
            submitBusy = false;
            toast("提交失败：" + messageOf(error));
        }
    }

    private void onSubmitResult(MatchRulePack pack,
                                MatchRulesClient.Result<MatchRulesClient.SubmitResult> result) {
        submitBusy = false;
        if (isFinishing()) {
            return;
        }
        if (result == null) {
            toast("提交失败：服务器没有返回结果");
            return;
        }
        if (!result.ok) {
            String message = safe(result.message);
            toastLong("提交失败：" + (message.isEmpty() ? "服务器拒绝了这次提交" : message));
            return;
        }
        MatchRulesClient.SubmitResult value = result.value;
        if (value == null) {
            toast("提交失败：服务器没有返回提交编号");
            return;
        }
        String id = safe(value.id);
        String token = safe(value.token);
        String status = safe(value.status);
        if (status.isEmpty()) {
            status = "pending";
        }
        try {
            MatchRulesClient.rememberSubmission(this, id, token, safe(pack.name));
        } catch (Throwable ignored) {
            // 本地记录失败不阻断提交成功提示。
        }
        toastLong("提交成功：编号 " + id + "，当前状态：" + statusLabel(status)
                + "。可在「规则市场」查看审核进度。");
    }

    private String statusLabel(String status) {
        if ("pending".equals(status)) {
            return "待审核";
        }
        if ("approved".equals(status)) {
            return "已通过";
        }
        if ("rejected".equals(status)) {
            return "已拒绝";
        }
        if ("disabled".equals(status)) {
            return "已下架";
        }
        return safe(status).isEmpty() ? "未知" : status;
    }

    private void onCopyJson() {
        String text = currentJson();
        if (text.isEmpty()) {
            toast("当前 JSON 为空");
            return;
        }
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (manager == null) {
                toast("系统剪贴板不可用");
                return;
            }
            manager.setPrimaryClip(ClipData.newPlainText("匹配规则 JSON", text));
            toast("已复制 JSON 到剪贴板");
        } catch (Exception error) {
            toast("复制失败：" + messageOf(error));
        }
    }

    private void onImportJson() {
        final EditText importInput = makeInput("粘贴规则包 JSON", true, true);
        importInput.setMinHeight(dp(180));
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(4), dp(16), 0);
        container.addView(importInput, new LinearLayout.LayoutParams(-1, dp(220)));

        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("导入规则 JSON")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("导入", null)
                .show();
        if (dialog == null) {
            toast("无法打开导入对话框");
            return;
        }
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String text = importInput.getText().toString().trim();
                        if (text.isEmpty()) {
                            toast("请先粘贴 JSON");
                            return;
                        }
                        try {
                            MatchRulePack pack = MatchRulePack.parse(text);
                            String pretty = prettyJson(text);
                            codeEditor.setText(pretty);
                            codeEditor.setSelection(0);
                            dialog.dismiss();
                            setCodeMode(true);
                            updatePackInfo();
                            previewResultText.setText("已导入：" + safe(pack.name)
                                    + "（尚未保存）");
                            toastLong("已导入规则包：" + safe(pack.name) + "，请确认后保存启用");
                        } catch (IllegalArgumentException error) {
                            toast("导入失败：" + messageOf(error));
                        } catch (Exception error) {
                            toast("导入失败：" + messageOf(error));
                        }
                    }
                });
    }

    private String prettyJson(String text) {
        try {
            return new JSONObject(text).toString(2);
        } catch (Exception ignored) {
            return text;
        }
    }

    // ── 规则包 JSON 骨架与示例 ───────────────────────────────────────────

    private String buildSkeletonPackJson() {
        try {
            return basePackObject(buildPackId(), defaultPackName()).toString(2);
        } catch (Exception error) {
            return "{\"schemaVersion\":1,\"rules\":[]}";
        }
    }

    private JSONObject basePackObject(String id, String name) throws JSONException {
        JSONObject pack = new JSONObject();
        pack.put("schemaVersion", 1);
        pack.put("id", id);
        pack.put("name", name);
        pack.put("version", 1);
        pack.put("author", "");
        pack.put("description", "");
        pack.put("minVersionCode", 0);
        JSONObject scope = new JSONObject();
        JSONArray packages = new JSONArray();
        if (!safe(trackPackage).isEmpty()) {
            packages.put(trackPackage);
        }
        scope.put("packages", packages);
        scope.put("sources", new JSONArray());
        pack.put("scope", scope);
        pack.put("rules", new JSONArray());
        return pack;
    }

    private String buildPackId() {
        StringBuilder suffix = new StringBuilder(8);
        String packageName = safe(trackPackage).toLowerCase(Locale.US);
        for (int i = packageName.length() - 1; i >= 0 && suffix.length() < 8; i--) {
            char character = packageName.charAt(i);
            if ((character >= 'a' && character <= 'z')
                    || (character >= '0' && character <= '9')) {
                suffix.insert(0, character);
            }
        }
        if (suffix.length() == 0) {
            suffix.append("local");
        }
        return "user." + suffix + "." + (System.currentTimeMillis() / 1000L);
    }

    private String defaultPackName() {
        int index = 1;
        try {
            List<MatchRulePack> packs = MatchRuleStore.userPacks(this);
            if (packs != null) {
                index = packs.size() + 1;
            }
        } catch (Throwable ignored) {
            // 读取已有个人规则失败时退回「我的规则 1」。
        }
        return "我的规则 " + index;
    }

    private String buildSampleJson() {
        try {
            JSONObject pack = basePackObject("echo-artist-title-split", "回声：歌手名塞进歌名");
            pack.put("author", "zuoqirun");
            pack.put("description", "把「歌手 - 歌名」拆开，并忽略「车载音乐」这类文件夹名歌手");
            JSONObject scope = new JSONObject();
            JSONArray packages = new JSONArray();
            packages.put("com.example.echo");
            scope.put("packages", packages);
            scope.put("sources", new JSONArray());
            pack.put("scope", scope);

            JSONArray when = new JSONArray();
            when.put(conditionObject("title", "regex", DASH_SPLIT_PATTERN));
            when.put(conditionArrayObject("artist", "equalsAny", FOLDER_ARTISTS));

            JSONObject extract = actionObject("extract");
            extract.put("pattern", DASH_SPLIT_PATTERN);
            JSONObject targets = new JSONObject();
            targets.put("artist", 1);
            targets.put("title", 2);
            extract.put("targets", targets);
            JSONArray then = new JSONArray();
            then.put(extract);

            JSONArray rules = new JSONArray();
            rules.put(ruleObject("split-dash", when, then));
            pack.put("rules", rules);

            String json = pack.toString(2);
            MatchRulePack.parse(json);
            return json;
        } catch (Exception error) {
            return "";
        }
    }

    private String buildSongTemplateJson() {
        try {
            JSONObject pack = basePackObject(buildPackId(), "内置规则同款模板");
            pack.put("description", truncate(
                    "参考内置规则：拆分「歌手 - 歌名」、去掉括号数字后缀、统一歌手别名。当前曲目："
                            + trackSummary(), 200));
            JSONArray rules = pack.getJSONArray("rules");

            // 规则 1：文件夹名歌手 + 标题分隔符 → 拆分。
            JSONArray whenFolder = new JSONArray();
            whenFolder.put(conditionObject("title", "regex", DASH_SPLIT_PATTERN));
            whenFolder.put(conditionArrayObject("artist", "equalsAny", FOLDER_ARTISTS));
            rules.put(ruleObject("split-folder-artist", whenFolder,
                    extractDashThen()));

            // 规则 2：歌手为空 + 标题分隔符 → 拆分（通知栏/车机场景）。
            JSONArray whenEmptyArtist = new JSONArray();
            whenEmptyArtist.put(conditionObject("title", "regex", DASH_SPLIT_PATTERN));
            whenEmptyArtist.put(conditionObject("artist", "empty", null));
            rules.put(ruleObject("split-empty-artist", whenEmptyArtist,
                    extractDashThen()));

            // 规则 3：去掉末尾括号数字后缀。
            JSONArray whenSuffix = new JSONArray();
            whenSuffix.put(conditionObject("title", "regex", NUMBER_SUFFIX_PATTERN));
            JSONArray thenSuffix = new JSONArray();
            thenSuffix.put(actionObject("stripSuffix"));
            rules.put(ruleObject("strip-number-suffix", whenSuffix, thenSuffix));

            // 规则 4：歌手别名统一。
            JSONObject aliasAction = actionObject("aliasArtist");
            JSONObject map = new JSONObject();
            map.put("A-Lin", "黄丽玲");
            map.put("alin", "黄丽玲");
            map.put("G.E.M.", "邓紫棋");
            map.put("GEM", "邓紫棋");
            map.put("黄霄雲", "黄霄云");
            aliasAction.put("map", map);
            JSONArray thenAlias = new JSONArray();
            thenAlias.put(aliasAction);
            rules.put(ruleObject("alias-artist", new JSONArray(), thenAlias));

            String json = pack.toString(2);
            MatchRulePack.parse(json);
            return json;
        } catch (Exception error) {
            return "";
        }
    }

    private JSONArray extractDashThen() throws JSONException {
        JSONObject extract = actionObject("extract");
        extract.put("pattern", DASH_SPLIT_PATTERN);
        JSONObject targets = new JSONObject();
        targets.put("artist", 1);
        targets.put("title", 2);
        extract.put("targets", targets);
        JSONArray then = new JSONArray();
        then.put(extract);
        return then;
    }

    private JSONObject conditionObject(String field, String op, String value)
            throws JSONException {
        JSONObject condition = new JSONObject();
        condition.put("field", field);
        condition.put("op", op);
        if (value != null) {
            condition.put("value", value);
        }
        return condition;
    }

    private JSONObject conditionArrayObject(String field, String op, String[] values)
            throws JSONException {
        JSONObject condition = new JSONObject();
        condition.put("field", field);
        condition.put("op", op);
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        condition.put("value", array);
        return condition;
    }

    private JSONObject actionObject(String type) throws JSONException {
        JSONObject action = new JSONObject();
        action.put("type", type);
        return action;
    }

    private JSONObject ruleObject(String id, JSONArray when, JSONArray then)
            throws JSONException {
        JSONObject rule = new JSONObject();
        rule.put("id", id);
        rule.put("when", when == null ? new JSONArray() : when);
        rule.put("then", then);
        return rule;
    }

    private String trackSummary() {
        String title = safe(trackTitle);
        String artist = safe(trackArtist);
        if (title.isEmpty() && artist.isEmpty()) {
            return "当前无播放曲目";
        }
        return (title.isEmpty() ? "（空歌名）" : title) + " / "
                + (artist.isEmpty() ? "（空歌手）" : artist);
    }

    // ── 通用工具 ─────────────────────────────────────────────────────────

    private void toast(String message) {
        SafeToast.show(this, message);
    }

    private void toastLong(String message) {
        SafeToast.show(this, message, Toast.LENGTH_LONG);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String displayValue(String value) {
        return value == null || value.isEmpty() ? "（空）" : value;
    }

    private String messageOf(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error.getClass().getSimpleName();
        }
        return message;
    }

    private String shorten(String value, int max) {
        String text = safe(value);
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private String truncate(String value, int max) {
        String text = safe(value);
        return text.length() <= max ? text : text.substring(0, max);
    }
}
