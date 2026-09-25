package com.zuoqirun.lyricscompanion;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.shape.MaterialShapeDrawable;

/** A focused parameter page for exactly one overlay display. */
@SuppressLint("SetTextI18n")
public final class DisplaySettingsActivity extends AppCompatActivity implements DisplaySlotHost {
    static final String EXTRA_SECONDARY = "secondary";

    private boolean secondary;
    /** Which screen this page edits: 0 = 主屏, 1 = 副屏, 2+ = an extra screen. */
    private int displaySlot;
    private LyricsPanelView preview;

    @Override public SharedPreferences displaySlotPreferences() {
        return DisplaySlotContext.preferencesFor(this, displaySlot);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        secondary = getIntent().getBooleanExtra(EXTRA_SECONDARY, false);
        displaySlot = DisplaySlotContext.slotFrom(getIntent(), secondary);
        secondary = displaySlot > DisplaySlotRegistry.MAIN_SLOT;

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(0xFF07111F);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(-1, -2));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle(DisplaySlotRegistry.slotLabel(this, displaySlot) + "显示参数");
        // 正在编辑哪一屏、哪一个样式（issue #39）：参数按 屏 × 样式 各存一份。
        toolbar.setSubtitle(DisplaySlotRegistry.slotLabel(this, displaySlot) + " · " + styleName()
                + " — 仅影响这一屏的这个样式");
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFFA9B6C8);
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(70)));

        preview = new LyricsPanelView(this, secondary);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, dp(190));
        previewParams.topMargin = dp(8);
        root.addView(preview, previewParams);

        // 每个样式各存一份参数（issue #39）：这里给一个批量入口，省得逐样式重调。
        LinearLayout styleScope = card("按样式保存");
        MaterialButton copyToStyles = new MaterialButton(this);
        copyToStyles.setText("把「" + styleName() + "」的参数应用到本屏其它样式");
        copyToStyles.setTextSize(13f);
        copyToStyles.setTextColor(0xFFF1F5FA);
        copyToStyles.setAllCaps(false);
        copyToStyles.setCornerRadius(dp(15));
        copyToStyles.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF25364D));
        copyToStyles.setOnClickListener(v -> {
            int copied = AppPreferences.applyCurrentStyleToOtherStyles(this, secondary);
            SafeToast.show(this, copied == 0
                            ? "当前样式还没有单独改过的参数，其它样式继续沿用本屏共用值"
                            : "已把 " + copied + " 项参数复制到本屏其它样式",
                    Toast.LENGTH_LONG);
            changed();
        });
        styleScope.addView(copyToStyles, new LinearLayout.LayoutParams(-1, dp(48)));
        styleScope.addView(text("颜色、字号、行数、不透明度、粒子与逐字、对齐、圆角、封面与匹配动画"
                + "现在都按「屏幕 × 样式」各存一份：在某个样式下调好的参数，切到别的样式不会带过去。"
                + "没调过的项沿用本屏原来的共用值（升级后观感不变），只有你在这个样式下改过的项"
                + "才会被单独保存——上面的按钮只会复制这些项。", 12, 0xFFD7E1EE, false));
        addCard(root, styleScope);

        LinearLayout panel = card("悬浮窗尺寸与文字");
        Point screenDp = targetScreenSizeDp();
        int maximumWidth = Math.max(AppPreferences.minimumPanelWidthDp(this, secondary), screenDp.x);
        int maximumHeight = Math.max(AppPreferences.minimumPanelHeightDp(this, secondary), screenDp.y);
        addSeek(panel, "悬浮窗宽度", AppPreferences.minimumPanelWidthDp(this, secondary),
                maximumWidth,
                AppPreferences.panelWidthDp(this, secondary), " dp",
                value -> AppPreferences.setPanelWidthDp(this, secondary, value));
        addSeek(panel, "悬浮窗高度", AppPreferences.minimumPanelHeightDp(this, secondary),
                maximumHeight,
                AppPreferences.panelHeightDp(this, secondary), " dp",
                value -> AppPreferences.setPanelHeightDp(this, secondary, value));
        addSeek(panel, "字号", 75, 220,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_TEXT_SCALE, 100), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_TEXT_SCALE, value));
        addSeek(panel, "歌名与歌手字号", 60, 180,
                AppPreferences.titleScale(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_TITLE_SCALE, value));
        addSeek(panel, "下一句字号", 45, 160,
                AppPreferences.nextLyricScale(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_NEXT_LYRIC_SCALE, value));
        addSeek(panel, "下一句不透明度", 20, 100,
                AppPreferences.nextLyricOpacity(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_NEXT_LYRIC_OPACITY, value));
        addSeek(panel, "上一句字号（经典样式）", 45, 160,
                AppPreferences.previousLyricScale(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_PREVIOUS_LYRIC_SCALE, value));
        addSeek(panel, "上一句不透明度", 0, 100,
                AppPreferences.previousLyricOpacity(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_PREVIOUS_LYRIC_OPACITY, value));
        addChoice(panel, "内容垂直对齐（经典 / 纯净）",
                new String[]{"跟随样式（默认）", "顶部", "居中", "底部"},
                new String[]{"", "top", "center", "bottom"},
                AppPreferences.contentAlign(this, secondary),
                value -> AppPreferences.putDisplayString(this, secondary,
                        AppPreferences.KEY_CONTENT_ALIGN, value));
        panel.addView(text("把悬浮窗拖到屏幕顶端后选「顶部」，歌词才会真正贴到面板上沿；「跟随样式」保持原来的摆法。", 12, 0xFFD7E1EE, false));
        addChoice(panel, "歌词水平对齐（经典 / 紧凑 / 纯净）",
                new String[]{"跟随样式（默认）", "居中", "居左"},
                new String[]{"", "center", "left"},
                AppPreferences.lyricAlign(this, secondary),
                value -> AppPreferences.putDisplayString(this, secondary,
                        AppPreferences.KEY_LYRIC_ALIGN, value));
        // 粒子量 and 逐字歌词及时擦除 belong to the dissolve: they are only offered while it is
        // switched on, and go away with it. The holder exists because the master toggle is built
        // before the rows it controls.
        final View[][] dependents = new View[1][];
        MaterialSwitch dissolve = addToggle(panel, "本句结束粒子消散",
                AppPreferences.KEY_PREVIOUS_LYRIC_PARTICLES,
                AppPreferences.previousLyricParticles(this, secondary),
                () -> setDependentVisibility(
                        AppPreferences.previousLyricParticles(this, secondary), dependents[0]));
        View[] amountRow = addSeekRow(panel, "粒子量", 20, 300,
                AppPreferences.particleAmountPercent(this, secondary), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_PARTICLE_AMOUNT, value));
        MaterialSwitch wordErase = addToggle(panel, "逐字歌词及时擦除",
                AppPreferences.KEY_WORD_DISSOLVE,
                AppPreferences.wordDissolve(this, secondary), null);
        dependents[0] = new View[] { amountRow[0], amountRow[1], wordErase };
        setDependentVisibility(dissolve.isChecked(), dependents[0]);
        addToggle(panel, "显示播放器与歌词来源状态行", AppPreferences.KEY_SHOW_PLAYER_STATUS,
                AppPreferences.showPlayerStatus(this, secondary));
        addToggle(panel, "显示进度条", AppPreferences.KEY_SHOW_PROGRESS,
                AppPreferences.showProgress(this, secondary));
        addToggle(panel, "平滑滚动换句", AppPreferences.KEY_SMOOTH_LYRIC_SCROLL,
                AppPreferences.smoothLyricScroll(this, secondary));
        addToggle(panel, "尾部拖长音重音", AppPreferences.KEY_TRAILING_ACCENT,
                AppPreferences.trailingAccent(this, secondary));
        MaterialSwitch lockToggle = addToggle(panel, "锁定位置并穿透（保留播放控制按键）",
                AppPreferences.KEY_OVERLAY_POSITION_LOCKED,
                AppPreferences.overlayPositionLocked(this, secondary), null);
        panel.addView(text("锁定后点击会穿透到下面的应用，只保留播放控制按键可点；歌词旁出现 × 手柄，"
                + "点第一下只提示、再点一下才解除（避免想操作歌词时误触）。与下面的「锁定并触摸穿透」"
                + "不能同时开启。Android 12 及以上会把整窗不透明度压到 79% 才能穿透，歌词会比平时略暗。",
                12, 0xFF8392A8, false));
        addTouchThroughToggle(panel, lockToggle);
        addSeek(panel, "歌词显示行数", 1, 7,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_LYRIC_LINES, 3), " 行",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_STYLE_LYRIC_LINES, value));
        panel.addView(text("经典样式最多摆三行（上一句 / 本句 / 下一句）：2 行＝本句 + 下一句，1 行＝只画本句；纯净与极简按设置的行数显示。", 12, 0xFFD7E1EE, false));
        // 无逐字时间轴时估算逐字进度（issue #21）。
        addToggle(panel, "无逐字时间轴时按本句时长估算逐字进度", AppPreferences.KEY_ESTIMATED_WORD_KARAOKE,
                AppPreferences.estimatedWordKaraoke(this, secondary), null);
        panel.addView(text("普通 .lrc 只有行时间轴，原本整句一次性变色；打开后按「本句已播放比例」由白到蓝"
                + "逐字推进，紧凑 / HUD / 顶部条 / 经典 / Refined 观感统一。长音、拖腔与行内停顿会让"
                + "估算提前或滞后（只影响观感，不影响同步），所以默认关闭。", 12, 0xFFD7E1EE, false));
        // 「正在匹配歌词」的呈现方式（issue #45）。
        final View[][] matchingDependents = new View[1][];
        MaterialSwitch matching = addToggle(panel, "匹配歌词时显示动画",
                AppPreferences.KEY_MATCHING_ANIMATION,
                AppPreferences.matchingAnimation(this, secondary),
                () -> setDependentVisibility(
                        AppPreferences.matchingAnimation(this, secondary), matchingDependents[0]));
        View[] matchingDelayRow = addSeekRow(panel, "匹配超过多久才显示动画", 0, 10,
                AppPreferences.matchingAnimationDelayMs(this, secondary) / 1_000, " 秒",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_MATCHING_ANIMATION_DELAY, value * 1_000));
        matchingDependents[0] = new View[] { matchingDelayRow[0], matchingDelayRow[1] };
        setDependentVisibility(matching.isChecked(), matchingDependents[0]);
        panel.addView(text("匹配期间歌名照常显示，面板底部给一排从中心向两侧发散的小点；关闭后回到原来"
                + "「正在匹配歌词…」的静止文字。默认匹配超过 3 秒才出现，避免瞬间匹配成功时闪一下；"
                + "动画期间会保持约 30 fps，老旧车机可以关掉省电。", 12, 0xFFD7E1EE, false));
        if ("pure".equals(AppPreferences.overlayStyle(this, secondary))) {
            addToggle(panel, "纯净歌词显示翻译", AppPreferences.KEY_PURE_SHOW_TRANSLATION,
                    AppPreferences.pureShowTranslation(this, secondary));
        }
        addSeek(panel, "歌词时间校正", -5000, 5000,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_LYRIC_OFFSET, 0), " ms",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_LYRIC_OFFSET, value));
        addCard(root, panel);

        LinearLayout spectrum = card("律动与频谱");
        addToggle(spectrum, "在当前歌词模式显示律动", AppPreferences.KEY_SPECTRUM_ENABLED,
                AppPreferences.spectrumEnabled(this, secondary));
        addToggle(spectrum, "使用真实音频频谱（关闭为虚拟律动）",
                AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM,
                AppPreferences.compactUseRealSpectrum(this, secondary));
        addChoice(spectrum, "频谱样式",
                new String[]{"经典柱状", "中心镜像", "胶囊律动", "点阵跳动", "连续波形"},
                new String[]{"bars", "mirror", "capsule", "dots", "wave"},
                AppPreferences.spectrumStyle(this, secondary),
                value -> AppPreferences.putDisplayString(this, secondary,
                        AppPreferences.KEY_SPECTRUM_STYLE, value));
        addChoice(spectrum, "频谱颜色",
                new String[]{"跟随歌词", "自定义单色", "HSL 彩虹", "跟随封面"},
                new String[]{"lyric", "custom", "rainbow", "artwork"},
                AppPreferences.spectrumColorMode(this, secondary),
                value -> AppPreferences.putDisplayString(this, secondary,
                        AppPreferences.KEY_SPECTRUM_COLOR_MODE, value));
        addCard(root, spectrum);

        if (!secondary) {
            LinearLayout controls = card("播放控制与全屏按钮");
            addSeek(controls, "播放按钮大小", 60, 160,
                    AppPreferences.playbackControlScale(this), "%",
                    value -> AppPreferences.get(this).edit()
                            .putInt(AppPreferences.KEY_PLAYBACK_CONTROL_SCALE, value).apply());
            addSeek(controls, "播放按钮水平位置", -40, 40,
                    AppPreferences.playbackControlX(this), "%",
                    value -> AppPreferences.get(this).edit()
                            .putInt(AppPreferences.KEY_PLAYBACK_CONTROL_X, value).apply());
            addSeek(controls, "播放按钮垂直位置", -40, 40,
                    AppPreferences.playbackControlY(this), "%",
                    value -> AppPreferences.get(this).edit()
                            .putInt(AppPreferences.KEY_PLAYBACK_CONTROL_Y, value).apply());
            addChoice(controls, "全屏右上角关闭按钮",
                    new String[]{"自动弱化", "始终显示", "隐藏"},
                    new String[]{"fade", "always", "hidden"},
                    AppPreferences.fullscreenCloseMode(this),
                    value -> AppPreferences.get(this).edit()
                            .putString(AppPreferences.KEY_FULLSCREEN_CLOSE_MODE, value).apply());
            addChoice(controls, "桌面歌词右上角叉",
                    new String[]{"弱化显示", "始终显示", "隐藏", "自动弱化（2 秒）", "自动隐藏（2 秒）"},
                    new String[]{"fade", "always", "hidden", "auto_fade", "auto_hide"},
                    AppPreferences.overlayCloseMode(this),
                    value -> AppPreferences.get(this).edit()
                            .putString(AppPreferences.KEY_OVERLAY_CLOSE_MODE, value).apply());
            addCard(root, controls);
        } else {
            LinearLayout controls = card("副屏播放控制");
            addToggle(controls, "显示上一首 / 播放暂停 / 下一首按钮",
                    AppPreferences.KEY_SECONDARY_PLAYBACK_CONTROLS,
                    AppPreferences.showPlaybackControls(this, true));
            addCard(root, controls);
        }

        LinearLayout sourceCorrection = card("按播放器校正");
        addSourceCorrection(sourceCorrection);
        addCard(root, sourceCorrection);

        LinearLayout trackCorrection = card("当前歌曲校正");
        addTrackCorrection(trackCorrection);
        addCard(root, trackCorrection);

        LinearLayout cache = card("歌词缓存");
        addChoice(cache, "缓存保留方式",
                new String[]{"保留 30 天", "永久保留", "按容量自动淘汰（默认）"},
                new String[]{"30d", "forever", "capacity"},
                AppPreferences.lyricCachePolicy(this),
                value -> AppPreferences.get(this).edit()
                        .putString(AppPreferences.KEY_LYRIC_CACHE_POLICY, value).apply());
        addSeek(cache, "容量上限（仅自动淘汰）", 16, 512,
                AppPreferences.lyricCacheLimitMb(this), " MB",
                value -> AppPreferences.get(this).edit()
                        .putInt(AppPreferences.KEY_LYRIC_CACHE_LIMIT_MB, value).apply());
        cache.addView(text("匹配成功后保存歌词、翻译和逐字时间轴；再次播放优先读取本地缓存，无需联网搜索。默认上限 128 MB；手动重新匹配会跳过歌曲匹配缓存。", 12, 0xFFD7E1EE, false));
        // 缓存占用与清除入口（issue #20）。
        TextView cacheUsage = text(cacheUsageLabel(), 13, 0xFF6EE7F2, false);
        cacheUsage.setPadding(0, dp(10), 0, dp(6));
        cache.addView(cacheUsage);
        MaterialButton clearCache = new MaterialButton(this);
        clearCache.setText("清除歌词缓存");
        clearCache.setTextSize(13f);
        clearCache.setTextColor(0xFFF1F5FA);
        clearCache.setAllCaps(false);
        clearCache.setCornerRadius(dp(15));
        clearCache.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF25364D));
        clearCache.setOnClickListener(v -> confirmClearLyricCache(cacheUsage));
        cache.addView(clearCache, new LinearLayout.LayoutParams(-1, dp(48)));
        cache.addView(text("「按容量自动淘汰」（默认）现在也带 30 天上限：歌少、总量到不了上限时，"
                + "旧缓存不会再一直命中，「永久保留」不受影响。清除会同时删掉歌词与匹配结果缓存，"
                + "下次播放重新联网匹配；缓存是全部屏幕共用的，任一屏清除都会清掉同一份。", 12,
                0xFFD7E1EE, false));
        addCard(root, cache);

        LinearLayout artwork = card("背景与封面");
        addSeek(artwork, "背景不透明度", 0, 100,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_OPACITY, 88), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_OPACITY, value));
        addSeek(artwork, "封面大小", 60, 150,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_COVER_SIZE, 100), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_STYLE_COVER_SIZE, value));
        addSeek(artwork, "封面背景柔化", 0, 128,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_BLUR, 128), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_STYLE_BLUR, value));
        addSeek(artwork, "封面背景遮罩", 0, 80,
                AppPreferences.displayInt(this, secondary, AppPreferences.KEY_STYLE_DIM, 38), "%",
                value -> AppPreferences.putDisplayInt(this, secondary, AppPreferences.KEY_STYLE_DIM, value));
        // 面板圆角（issue #37）：0 = 直角矩形，可以把面板当成一整块实心色板。
        addSeek(artwork, "面板圆角（0 = 直角）", 0, 50, displayedCornerPercent(), "%",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_CORNER_RADIUS_PERCENT, value));
        artwork.addView(text("圆角按面板短边的百分比计算；0% 就是直角矩形，配合「背景不透明度」100% "
                + "可以把面板整块盖住后面的原车界面。没调过时显示的是当前样式原本的圆角。", 12,
                0xFFD7E1EE, false));
        // 圆形封面（紧凑 / AMLL，issue #22）+ 碟片旋转。
        addToggle(artwork, "圆形封面（紧凑 / AMLL）", AppPreferences.KEY_ROUND_COVER,
                AppPreferences.roundCover(this, secondary), null);
        artwork.addView(text("打开后紧凑歌词与 AMLL 的专辑封面改为正圆（Refined 用它自己的"
                + "「方形专辑封面」开关，关掉即圆形）。「碟片旋转」只对圆形封面生效。", 12,
                0xFFD7E1EE, false));
        final View[][] coverDependents = new View[1][];
        MaterialSwitch coverSpin = addToggle(artwork, "圆形封面碟片旋转",
                AppPreferences.KEY_COVER_ROTATION,
                AppPreferences.coverRotation(this, secondary),
                () -> setDependentVisibility(AppPreferences.coverRotation(this, secondary),
                        coverDependents[0]));
        View[] periodRow = addSeekRow(artwork, "转一圈的秒数", 3, 60,
                AppPreferences.coverRotationPeriodSeconds(this, secondary), " 秒",
                value -> AppPreferences.putDisplayInt(this, secondary,
                        AppPreferences.KEY_COVER_ROTATION_PERIOD_SECONDS, value));
        coverDependents[0] = new View[] { periodRow[0], periodRow[1] };
        setDependentVisibility(coverSpin.isChecked(), coverDependents[0]);
        artwork.addView(text("只对圆形封面生效（紧凑 / AMLL 需先打开上面的「圆形封面」，Refined 关掉"
                + "「方形专辑封面」）：按播放进度旋转，暂停即停、拖动进度会跟着跳，不会因为系统时钟"
                + "在暂停后继续转；方形与圆角封面不变。默认 20 秒一圈 ≈ 3 转/分。旋转期间会按屏幕刷新"
                + "率（约 60 fps）重绘，比平时费电：车机较弱、觉得卡顿时关掉即可。", 12,
                0xFFD7E1EE, false));
        addCard(root, artwork);

        setContentView(scroll);
        CustomFontStore.applyToViewTree(this, scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private void addTouchThroughToggle(LinearLayout parent, MaterialSwitch lockToggle) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText("锁定并触摸穿透");
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(10), 0, dp(2));
        toggle.setChecked(AppPreferences.overlayTouchThrough(this, secondary));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.get(this).edit().putBoolean(secondary
                    ? AppPreferences.KEY_SECONDARY_OVERLAY_TOUCH_THROUGH
                    : AppPreferences.KEY_MAIN_OVERLAY_TOUCH_THROUGH, checked).apply();
            if (checked) {
                // This mode replaces the position lock rather than stacking on it, which is what
                // the service does from the overlay's own menu. Leaving the lock on would keep the
                // overlay locked after touch-through is switched off again, so the switch above
                // would read "off" while the window stayed frozen (issue #36).
                AppPreferences.putDisplayBoolean(this, secondary,
                        AppPreferences.KEY_OVERLAY_POSITION_LOCKED, false);
                if (lockToggle != null && lockToggle.isChecked()) lockToggle.setChecked(false);
            }
            // The service installs the safety unlock handle when it restores this overlay.
            // Leaving the settings page is enough; no hidden long-press gesture is required.
            changed();
        });
        parent.addView(toggle);
        TextView note = text("开启后点击会穿透到下面的应用，穿透期间歌词也拖不动（窗口不再接受触摸）；"
                + "通过本页关闭即可恢复交互。它与上面「锁定位置并穿透（保留播放控制按键）」是二选一，"
                + "开这个会关掉那个。锁定后歌词旁会出现 × 手柄：点第一下只提示，再点一下才解除。"
                + "Android 12 及以上为了能穿透，系统要求整窗不透明度低于 80%（当前实现为 79%），"
                + "所以歌词会比平时略暗一点，关闭穿透即恢复。", 12,
                0xFF8392A8, false);
        note.setPadding(0, 0, 0, dp(4));
        parent.addView(note);
    }

    /** 当前这一屏正在编辑的样式，中文名用于标题与按钮（issue #39）。 */
    private String styleName() {
        return styleName(AppPreferences.overlayStyle(this, secondary));
    }

    private static String styleName(String style) {
        if ("default".equals(style)) return "经典";
        if ("refined".equals(style)) return "Refined";
        if ("amll".equals(style)) return "AMLL";
        if ("compact".equals(style)) return "紧凑";
        if ("pip".equals(style)) return "极简";
        if ("pure".equals(style)) return "纯净";
        if ("custom".equals(style)) return "自定义";
        return style;
    }

    /** 「当前占用 x MB · n 条」，缓存是全部屏幕共用的（issue #20）。 */
    private String cacheUsageLabel() {
        return "当前占用 " + formatBytes(LyricCache.cachedBytes(this)) + " · "
                + LyricCache.cachedEntries(this) + " 条";
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024f * 1024f));
        }
        if (bytes >= 1024L) {
            return String.format(java.util.Locale.ROOT, "%.0f KB", bytes / 1024f);
        }
        return bytes + " B";
    }

    private void confirmClearLyricCache(TextView usage) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("清除歌词缓存")
                .setMessage("将删除所有已缓存的歌词、翻译、逐字时间轴与歌曲匹配结果；"
                        + "下次播放会重新联网搜索。已保存的设置不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> {
                    int removed = LyricCache.clearAll(this);
                    usage.setText(cacheUsageLabel());
                    SafeToast.show(this, removed == 0 ? "缓存本来就是空的"
                            : "已清除 " + removed + " 条歌词缓存", Toast.LENGTH_LONG);
                })
                .show();
    }

    /** 圆角设置还没动过时，显示当前样式原本的圆角百分比（issue #37）。 */
    private int displayedCornerPercent() {
        int stored = AppPreferences.cornerRadiusPercent(this, secondary);
        if (stored >= 0) return stored;
        String style = AppPreferences.overlayStyle(this, secondary);
        if ("compact".equals(style)) return 18;
        if ("pure".equals(style) || "pip".equals(style)) return 22;
        if ("classic".equals(style)) return 7;
        return 8;
    }

    private Point targetScreenSizeDp() {
        Display display = targetDisplay();
        Point pixels = new Point();
        if (display != null) display.getRealSize(pixels);
        android.content.Context displayContext = display == null ? this : createDisplayContext(display);
        float density = Math.max(0.1f,
                displayContext.getResources().getDisplayMetrics().density);
        return new Point(Math.max(1, Math.round(pixels.x / density)),
                Math.max(1, Math.round(pixels.y / density)));
    }

    private Display targetDisplay() {
        DisplayManager manager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
        if (!secondary || manager == null) return getWindowManager().getDefaultDisplay();
        int preferredId = AppPreferences.displayId(this);
        if (preferredId >= 0) {
            Display preferred = manager.getDisplay(preferredId);
            if (preferred != null && preferred.getDisplayId() != Display.DEFAULT_DISPLAY) {
                return preferred;
            }
        }
        for (Display display : manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)) {
            if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) return display;
        }
        for (Display display : manager.getDisplays()) {
            if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) return display;
        }
        return getWindowManager().getDefaultDisplay();
    }

    private LinearLayout card(String title) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(15));
        MaterialShapeDrawable surface = new MaterialShapeDrawable();
        surface.setFillColor(android.content.res.ColorStateList.valueOf(0xFF101E31));
        surface.setCornerSize(dp(20));
        card.setBackground(surface);
        card.addView(text(title, 13, 0xFF6EE7F2, true));
        return card;
    }

    private void addCard(LinearLayout root, LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        root.addView(card, params);
    }

    private void addSeek(LinearLayout parent, String title, int min, int max,
                         int initial, String suffix, IntConsumer consumer) {
        addSeekRow(parent, title, min, max, initial, suffix, consumer);
    }

    /**
     * Adds a seek row and hands back the views it created, so a setting that only applies while
     * another one is on can be hidden together with its label and value.
     *
     * @return the label row and the bar, in that order
     */
    private View[] addSeekRow(LinearLayout parent, String title, int min, int max,
                              int initial, String suffix, IntConsumer consumer) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, 0);
        row.addView(text(title, 14, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text(formatValue(initial, suffix), 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(clamp(initial, min, max) - min);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF6EE7F2));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFCA66));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int selected = min + progress;
                value.setText(formatValue(selected, suffix));
                if (!fromUser) return;
                consumer.accept(selected);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
        return new View[] { row, seek };
    }

    private void addToggle(LinearLayout parent, String title, String key, boolean initial) {
        addToggle(parent, title, key, initial, null);
    }

    private MaterialSwitch addToggle(LinearLayout parent, String title, String key,
                                     boolean initial, Runnable onToggled) {
        MaterialSwitch toggle = new MaterialSwitch(this);
        toggle.setText(title);
        toggle.setTextColor(0xFFF1F5FA);
        toggle.setTextSize(14f);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setPadding(0, dp(10), 0, 0);
        toggle.setChecked(initial);
        toggle.setOnCheckedChangeListener((button, checked) -> {
            AppPreferences.putDisplayBoolean(this, secondary, key, checked);
            if (checked && AppPreferences.KEY_COMPACT_USE_REAL_SPECTRUM.equals(key)
                    && android.os.Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                SafeToast.show(this,
                        "请在首页“使用权限”中授予录音频谱权限",
                        android.widget.Toast.LENGTH_LONG);
            }
            if (onToggled != null) onToggled.run();
            changed();
        });
        parent.addView(toggle);
        return toggle;
    }

    /** Shows or hides settings that only mean anything while another one is switched on. */
    private static void setDependentVisibility(boolean visible, View... views) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        for (View view : views) {
            if (view != null) view.setVisibility(visibility);
        }
    }

    private void addChoice(LinearLayout parent, String title, String[] labels, String[] values,
                           String initial, StringConsumer consumer) {
        TextView label = text(title, 14, 0xFFD7E1EE, true);
        label.setPadding(0, dp(12), 0, dp(4));
        parent.addView(label);
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(initial)) { spinner.setSelection(i, false); break; }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            String selected = initial;
            @Override public void onItemSelected(AdapterView<?> parentView, View view,
                                                  int position, long id) {
                if (values[position].equals(selected)) return;
                selected = values[position];
                consumer.accept(values[position]);
                changed();
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(44)));
    }

    private void addSourceCorrection(LinearLayout parent) {
        TextView description = text("在全局时间校正的基础上，为不同播放器单独微调。", 12,
                0xFF8392A8, false);
        description.setPadding(0, dp(10), 0, dp(4));
        parent.addView(description);
        String[] labels = {"网易云音乐", "QQ 音乐", "酷狗音乐", "酷我音乐", "汽水音乐", "咪咕音乐",
                "喜马拉雅", "东风皓瀚播放器", "其他播放器"};
        String[] sourceIds = {"netease", "qqmusic", "kugou", "kuwo", "soda", "migu",
                "ximalaya", "dftc_media", "media"};
        String active = MusicStateStore.activeSourceId();
        int initialIndex = sourceIndex(sourceIds, active);
        final String[] selectedSource = {sourceIds[initialIndex]};

        Spinner picker = new Spinner(this);
        picker.setAdapter(new ThemedSpinnerAdapter<>(this, labels));
        picker.setSelection(initialIndex);
        parent.addView(picker, new LinearLayout.LayoutParams(-1, dp(44)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, 0);
        row.addView(text("所选播放器额外校正", 14, 0xFFD7E1EE, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView value = text("0 ms", 13, 0xFF6EE7F2, true);
        row.addView(value);
        parent.addView(row);
        SeekBar seek = new SeekBar(this);
        seek.setMax(10_000);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            seek.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF6EE7F2));
            seek.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFCA66));
        }
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int offsetMs = progress - 5_000;
                value.setText(formatValue(offsetMs, " ms"));
                if (!fromUser) return;
                AppPreferences.putLyricSourceOffsetMs(DisplaySettingsActivity.this, secondary,
                        selectedSource[0], offsetMs);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
        picker.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parentView, android.view.View view,
                                                 int position, long id) {
                selectedSource[0] = sourceIds[position];
                int offsetMs = AppPreferences.lyricSourceOffsetMs(DisplaySettingsActivity.this,
                        secondary, selectedSource[0]);
                seek.setProgress(offsetMs + 5_000);
            }
            @Override public void onNothingSelected(AdapterView<?> parentView) { }
        });
        seek.setProgress(AppPreferences.lyricSourceOffsetMs(this, secondary, selectedSource[0])
                + 5_000);
    }

    private void addTrackCorrection(LinearLayout parent) {
        boolean hasTrack = !MusicStateStore.activeLyricOffsetKey().isEmpty();
        TextView description = text(hasTrack
                ? "仅为当前歌曲额外校正；切换歌曲后自动读取对应记录。"
                : "播放歌曲后可为这首歌单独校正。", 12, 0xFF8392A8, false);
        parent.addView(description);
        TextView value = text(formatValue(AppPreferences.lyricTrackOffsetMs(this), " ms"),
                13, 0xFF6EE7F2, true);
        parent.addView(value);
        SeekBar seek = new SeekBar(this);
        seek.setMax(10_000);
        seek.setEnabled(hasTrack);
        seek.setProgress(AppPreferences.lyricTrackOffsetMs(this) + 5_000);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int offsetMs = progress - 5_000;
                value.setText(formatValue(offsetMs, " ms"));
                if (!fromUser) return;
                AppPreferences.putLyricTrackOffsetMs(DisplaySettingsActivity.this, offsetMs);
                changed();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(38)));
    }

    private static int sourceIndex(String[] sourceIds, String sourceId) {
        for (int i = 0; i < sourceIds.length; i++) {
            if (sourceIds[i].equals(sourceId)) return i;
        }
        return sourceIds.length - 1;
    }

    private void changed() {
        preview.reloadStyle();
        AppPreferences.changed(this);
        AudioSpectrumSource.sync(this);
        LyricsDisplayService.setSettingsVisible(this, true);
        if (secondary) LyricsDisplayService.refreshSecondary(this);
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String formatValue(int value, String suffix) {
        return (value > 0 && " ms".equals(suffix) ? "+" : "") + value + suffix;
    }

    private interface IntConsumer { void accept(int value); }
    private interface StringConsumer { void accept(String value); }
}
