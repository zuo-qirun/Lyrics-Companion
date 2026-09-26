package com.zuoqirun.lyricscompanion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.os.Build;
import android.util.LruCache;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Canvas renderer shared by the preview, the phone overlay and the secondary display. */
final class LyricsPanelView extends View {
    private static final Typeface SANS_NORMAL = Typeface.create("sans", Typeface.NORMAL);
    private static final Typeface SANS_BOLD = Typeface.create("sans", Typeface.BOLD);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private final Rect sourceRect = new Rect();
    private final RectF panelRect = new RectF();
    private final RectF workRect = new RectF();
    private final RectF progressRect = new RectF();
    private final RectF coverRect = new RectF();
    private final RectF shadowRect = new RectF();
    private final Path clipPath = new Path();
    private final Path iconPath = new Path();
    private final LruCache<TextLayoutKey, List<WrappedChunk>> wrappedTextCache =
            new LruCache<>(96);
    private final LruCache<TextLayoutKey, String> ellipsizedTextCache =
            new LruCache<>(96);
    private final LruCache<Integer, BlurMaskFilter> blurMaskCache = new LruCache<>(16);
    /**
     * 模糊歌词的离屏位图（issue #59 后续）：同一行 + 同字号 / 颜色 / 宽度 / 模糊半径只渲染一次，
     * 之后每帧只贴一次图。逐字消散中的行缓存不住，用 {@link #blurScratch} 逐帧重画。
     */
    private final LruCache<String, Bitmap> blurredLineCache = new LruCache<>(6);
    private Bitmap blurScratch;
    /** 星空底色（夜幕 + 星云）预渲染成的位图，只在尺寸或调色板变化时重建。 */
    private Bitmap starfieldBackdrop;
    private String starfieldBackdropKey = "";
    private float[] refinedLineHeights = new float[8];
    private float[] refinedLineTops = new float[8];
    private float textScale = 1f;
    private float titleScale = 1f;
    private float coverScale = 1f;
    private int opacity = 88;
    private int lyricOffsetMs;
    private int lyricColor;
    private int lyricLightColor;
    private int lyricDarkColor;
    private int currentLyricColor;
    private int inactiveLyricColor;
    private int currentLyricLightColor;
    private int currentLyricDarkColor;
    private int inactiveLyricLightColor;
    private int inactiveLyricDarkColor;
    private boolean currentLyricOutline;
    private int currentLyricOutlineColor;
    private int currentLyricOutlineAlphaPercent = 88;
    private int currentLyricOutlineWidthPercent = 8;
    private boolean inactiveLyricOutline;
    private int inactiveLyricOutlineColor;
    private int inactiveLyricOutlineAlphaPercent = 88;
    private int inactiveLyricOutlineWidthPercent = 8;
    private boolean trailingAccent;
    private int titleColor;
    private int artistColor;
    private int playerColor;
    private int lyricSourceColor;
    private String frameTitle = "";
    private String frameArtist = "";
    private String frameSourceName = "";
    private String frameLyricSourceName = "";
    private float nextLyricScale = 0.70f;
    private int nextLyricOpacity = 100;
    private int previousLyricOpacity = 100;
    /** 上一句字号, as a share of the current line; 55% reproduces the old hard-coded size. */
    private float previousLyricScale = ClassicLayoutMath.LEGACY_PREVIOUS_SCALE;
    /** 内容垂直对齐: "" follows the style, otherwise top / center / bottom (issue #41). */
    private String contentAlign = "";
    /**
     * 经典样式的行表（issue #53/#64）：一行一个字号，排布交给 {@link ClassicLayoutMath#pack}。这些
     * 数组与 {@code classicBlock} 逐帧复用，避免每帧新建对象。
     */
    private static final int CLASSIC_ROW_STATUS = 0;
    private static final int CLASSIC_ROW_TITLE = 1;
    private static final int CLASSIC_ROW_LYRIC = 2;
    private static final int CLASSIC_ROW_TRANSLATION = 3;
    private static final int CLASSIC_ROW_CAPACITY = 11;
    private final float[] classicRowSizes = new float[CLASSIC_ROW_CAPACITY];
    private final boolean[] classicRowScales = new boolean[CLASSIC_ROW_CAPACITY];
    private final float[] classicRowMinGaps = new float[CLASSIC_ROW_CAPACITY];
    private final boolean[] classicRowUniform = new boolean[CLASSIC_ROW_CAPACITY];
    private final int[] classicRowKinds = new int[CLASSIC_ROW_CAPACITY];
    private final int[] classicRowOffsets = new int[CLASSIC_ROW_CAPACITY];
    private final ClassicLayoutMath.Block classicBlock =
            new ClassicLayoutMath.Block(CLASSIC_ROW_CAPACITY);
    /** 星空背景的固定种子：同一片星空每帧都一样（issue #59）。 */
    private static final int STARFIELD_SEED = 0x5EED1234;
    /** 面板圆角占短边百分比; -1 keeps each style's own radius, 0 is a square corner (#37). */
    private int cornerRadiusPercent = -1;
    /** 圆形封面（紧凑 / AMLL，issue #22），以及按播放进度旋转的开关与一圈时长。 */
    private boolean roundCover;
    private boolean coverRotation;
    private int coverRotationPeriodSeconds = 20;
    /** 「正在匹配歌词」动画 (#45): whether it runs, and how long a match may take first. */
    private boolean matchingAnimationEnabled = true;
    private int matchingAnimationDelayMs = 3_000;
    /** 无逐字时间轴时按本句时长估算逐字进度（issue #21）。 */
    private boolean estimatedWordKaraoke;
    /** Identity of the track that is being matched, and when that match started. */
    private String matchingTrackKey = "";
    private long matchingSinceMs;
    /** This frame's playback position and state, so the rotating cover has a clock. */
    private long framePositionMs = -1L;
    private boolean framePlaying;
    /** 本帧播放器给没给封面：决定「无封面时」设置要不要生效（issue #50）。 */
    private boolean frameAlbumArtMissing;
    /** 无封面时收起封面区域，版面跟着内收（issue #50）。 */
    private boolean hideCoverWithoutArt;
    /** 星空背景的四个参数（issue #59）：密度、速度、星点大小、跟随封面取色、静止省电档。 */
    private int starfieldDensityPercent = 60;
    private int starfieldSpeedPercent = 100;
    private int starfieldSizePercent = 100;
    private boolean starfieldFollowCover = true;
    private boolean starfieldStill;
    /** 星空渲染帧率（fps，issue #59）：决定星空的帧间隔。 */
    private int starfieldFps = StarfieldField.DEFAULT_FPS;
    /** 歌手字号（歌名的百分比，-1 = 沿用各样式原比例，issue #63）。 */
    private int artistScalePercent = MetadataTypeScaleMath.UNSET;
    /** 面板边缘阴影强度（-1 = 各样式原样，issue #56）与它的一次性柔化位图。 */
    private int panelShadowPercent = PanelShadowMath.UNSET;
    private Bitmap panelShadowHaloBitmap;
    private String panelShadowHaloKey = "";
    /** 背景遮罩档位与亮度（issue #58）：auto / off，以及 -100..100 的亮度轴。 */
    private String styleMaskMode = ArtworkBackgroundMath.MODE_AUTO;
    private int styleBrightnessPercent;
    private android.graphics.ColorFilter backgroundBrightnessFilter;
    private int backgroundBrightnessPercent;
    /** 内容留白比例（issue #49）：-1 = 各样式原本的内边距。 */
    private int contentPaddingPercent = ContentPaddingMath.UNSET;
    /** 长句显示方式（issue #47）：marquee（默认）/ shrink / wrap。 */
    private String longLineMode = LongLineLayout.MODE_MARQUEE;
    /**
     * 动态背景按帧率排下一帧用的 vsync 回调（issue #59）。{@code backgroundFramePosted} 防止同一帧
     * 里重复挂回调（歌词更新等其它原因也会触发 onDraw）。
     */
    private final Choreographer.FrameCallback backgroundFrameCallback = frameTimeNanos -> {
        backgroundFramePosted = false;
        invalidate();
    };
    private boolean backgroundFramePosted;
    /** Last time the scheduled theme was re-checked; see {@link #refreshScheduledTheme(long)}. */
    private long lastThemeCheckMs;
    private boolean previousLyricParticles = true;
    private boolean wordDissolve;
    private int particleAmountPercent = 100;
    /** Set only while panel metadata (title / artist / source) is being drawn. */
    private boolean drawingMetadata;
    private boolean showPlayerStatus = true;
    private boolean showProgress = true;
    private boolean smoothLyricScroll = true;
    private int backgroundBlur;
    private int backgroundDim;
    private int lyricLineCount;
    private boolean pureShowTranslation;
    private String overlayStyle;
    private String themeMode;
    private boolean lyricsFollowTheme;
    private String refinedDisplayMode;
    private String refinedColorScheme;
    private String refinedAccentVariant;
    private String refinedTextEffect;
    private boolean refinedProgressBottom;
    private String refinedCoverHorizontal;
    private String refinedCoverVertical;
    private boolean refinedRectangleCover;
    private boolean refinedCoverShadow;
    private String refinedBackgroundType;
    private boolean refinedStaticFluid;
    private boolean refinedDynamicGradient;
    private int refinedLyricFontSize;
    private boolean refinedOriginalBold;
    private boolean refinedLyricFade;
    private boolean refinedLyricZoom;
    private boolean refinedLyricBlur;
    private boolean refinedLyricRotate;
    private int refinedRotateCurvature;
    private String refinedKaraokeAnimation;
    private int refinedCurrentAlign;
    private boolean refinedShowTranslation;
    private boolean refinedLyricGlow;
    private LyricsLayoutConfig layoutConfig;
    private Bitmap blurSource;
    private Bitmap blurPreview;
    private Bitmap paletteSource;
    private int[] palette = new int[]{0xFF62798A, 0xFF33495C, 0xFF8A6D72,
            0xFF1C2933, 0xFF9BAEB8, 0xFF536A77};
    private boolean secondary;
    private boolean browsingLyrics;
    private boolean browseMoved;
    private float browseLastY;
    private float browseTravelPx;
    private float browseVisualOffsetPx;
    private float browseVelocityPxPerSecond;
    private long browseLastEventTimeMs;
    private boolean browseSettling;
    private long browseSettleLastFrameMs;
    private float lastRefinedBrowseStepPx;
    private long browsePositionMs;
    private long browseUntilElapsedMs;
    private long lastRenderedLineStartMs = Long.MIN_VALUE;
    private long lyricScrollAnimationStartedMs;
    private int lyricScrollDirection;
    private long lastBasicLineStartMs = Long.MIN_VALUE;
    private long basicLyricScrollAnimationStartedMs;
    private long lastAmllLineStartMs = Long.MIN_VALUE;
    private long amllScrollAnimationStartedMs;
    private int amllScrollDirection;
    private Typeface customTypeface;
    /** Avoid repeatedly entering vendor Typeface code when the requested face has not changed. */
    private Typeface appliedTextTypeface;
    private String compactMarqueeText = "";
    private long compactMarqueeElapsedMs;
    private long compactMarqueeLastFrameMs;
    private boolean compactMarqueeActive;
    private final SpectrumMath.BarTracker compactSpectrumBars =
            new SpectrumMath.BarTracker(SpectrumMath.BAND_COUNT);
    private final float[] compactVirtualSpectrum = new float[SpectrumMath.BAND_COUNT];
    private final float[] compactDisplayedSpectrum = new float[SpectrumMath.BAND_COUNT];
    private final RectF spectrumRect = new RectF();
    private final RectF dissolveBoxRect = new RectF();
    private final Matrix dissolveBoxMatrix = new Matrix();
    private boolean compactSpectrumAnimating;
    private final LyricDissolveEffect previousLyricDissolve = new LyricDissolveEffect();
    private final boolean fullscreen;
    private final boolean compactTextOnly;
    private boolean topWindowBlurActive;

    LyricsPanelView(Context context) { this(context, false, false, false); }

    LyricsPanelView(Context context, boolean secondary) {
        this(context, secondary, false, false);
    }

    LyricsPanelView(Context context, boolean secondary, boolean fullscreen) {
        this(context, secondary, fullscreen, false);
    }

    /** Compact text-only mode backs the transparent top lyric strip. */
    LyricsPanelView(Context context, boolean secondary, boolean fullscreen, boolean compactTextOnly) {
        super(context);
        this.secondary = secondary;
        this.fullscreen = fullscreen;
        this.compactTextOnly = compactTextOnly;
        reloadStyle();
    }

    void setTopWindowBlurActive(boolean active) {
        if (topWindowBlurActive == active) return;
        topWindowBlurActive = active;
        invalidate();
    }

    void reloadStyle() {
        textScale = compactTextOnly ? AppPreferences.topLyricFontScale(getContext()) / 100f
                : AppPreferences.textScale(getContext(), secondary);
        titleScale = AppPreferences.titleScale(getContext(), secondary) / 100f;
        coverScale = AppPreferences.styleCoverScale(getContext(), secondary);
        opacity = AppPreferences.opacity(getContext(), secondary);
        lyricOffsetMs = AppPreferences.lyricOffsetMs(getContext(), secondary);
        lyricColor = compactTextOnly ? AppPreferences.statusLyricColor(getContext())
                : AppPreferences.lyricColor(getContext(), secondary);
        lyricLightColor = compactTextOnly ? AppPreferences.statusLyricLightColor(getContext())
                : AppPreferences.lyricLightColor(getContext(), secondary);
        lyricDarkColor = compactTextOnly ? AppPreferences.statusLyricDarkColor(getContext())
                : AppPreferences.lyricDarkColor(getContext(), secondary);
        currentLyricColor = compactTextOnly ? 0
                : AppPreferences.currentLyricColor(getContext(), secondary);
        inactiveLyricColor = compactTextOnly ? 0
                : AppPreferences.inactiveLyricColor(getContext(), secondary);
        currentLyricLightColor = compactTextOnly ? 0
                : AppPreferences.currentLyricLightColor(getContext(), secondary);
        currentLyricDarkColor = compactTextOnly ? 0
                : AppPreferences.currentLyricDarkColor(getContext(), secondary);
        inactiveLyricLightColor = compactTextOnly ? 0
                : AppPreferences.inactiveLyricLightColor(getContext(), secondary);
        inactiveLyricDarkColor = compactTextOnly ? 0
                : AppPreferences.inactiveLyricDarkColor(getContext(), secondary);
        currentLyricOutline = !compactTextOnly
                && AppPreferences.lyricOutline(getContext(), secondary, true);
        currentLyricOutlineColor = compactTextOnly ? 0
                : AppPreferences.lyricOutlineColor(getContext(), secondary, true);
        currentLyricOutlineAlphaPercent = AppPreferences.lyricOutlineAlphaPercent(getContext(),
                secondary, true);
        currentLyricOutlineWidthPercent = AppPreferences.lyricOutlineWidthPercent(getContext(),
                secondary, true);
        inactiveLyricOutline = !compactTextOnly
                && AppPreferences.lyricOutline(getContext(), secondary, false);
        inactiveLyricOutlineColor = compactTextOnly ? 0
                : AppPreferences.lyricOutlineColor(getContext(), secondary, false);
        inactiveLyricOutlineAlphaPercent = AppPreferences.lyricOutlineAlphaPercent(getContext(),
                secondary, false);
        inactiveLyricOutlineWidthPercent = AppPreferences.lyricOutlineWidthPercent(getContext(),
                secondary, false);
        trailingAccent = AppPreferences.trailingAccent(getContext(), secondary);
        titleColor = AppPreferences.titleColor(getContext(), secondary);
        artistColor = AppPreferences.artistColor(getContext(), secondary);
        playerColor = AppPreferences.playerColor(getContext(), secondary);
        lyricSourceColor = AppPreferences.lyricSourceColor(getContext(), secondary);
        // 顶部歌词条有自己的「下一句字号」；未在那一页调过时沿用主屏，升级后观感不变（issue #19）。
        nextLyricScale = (compactTextOnly
                ? AppPreferences.topLyricNextFontScale(getContext())
                : AppPreferences.nextLyricScale(getContext(), secondary)) / 100f;
        cornerRadiusPercent = AppPreferences.cornerRadiusPercent(getContext(), secondary);
        coverRotation = AppPreferences.coverRotation(getContext(), secondary);
        coverRotationPeriodSeconds = AppPreferences.coverRotationPeriodSeconds(getContext(),
                secondary);
        roundCover = AppPreferences.roundCover(getContext(), secondary);
        matchingAnimationEnabled = AppPreferences.matchingAnimation(getContext(), secondary);
        matchingAnimationDelayMs = AppPreferences.matchingAnimationDelayMs(getContext(), secondary);
        estimatedWordKaraoke = AppPreferences.estimatedWordKaraoke(getContext(), secondary);
        nextLyricOpacity = AppPreferences.nextLyricOpacity(getContext(), secondary);
        previousLyricScale = AppPreferences.previousLyricScale(getContext(), secondary) / 100f;
        contentAlign = AppPreferences.contentAlign(getContext(), secondary);
        // The strip has its own dissolve settings: it is a separate output object, so its
        // particles, dust amount and word erase do not move when the main overlay is adjusted.
        previousLyricOpacity = compactTextOnly
                ? AppPreferences.topLyricPreviousOpacity(getContext())
                : AppPreferences.previousLyricOpacity(getContext(), secondary);
        previousLyricParticles = compactTextOnly
                ? AppPreferences.topLyricParticles(getContext())
                : AppPreferences.previousLyricParticles(getContext(), secondary);
        wordDissolve = compactTextOnly
                ? AppPreferences.topLyricWordDissolve(getContext())
                : AppPreferences.wordDissolve(getContext(), secondary);
        particleAmountPercent = compactTextOnly
                ? AppPreferences.topLyricParticleAmount(getContext())
                : AppPreferences.particleAmountPercent(getContext(), secondary);
        previousLyricDissolve.setParticleAmount(particleAmountPercent);
        showPlayerStatus = AppPreferences.showPlayerStatus(getContext(), secondary);
        showProgress = AppPreferences.showProgress(getContext(), secondary);
        smoothLyricScroll = AppPreferences.smoothLyricScroll(getContext(), secondary);
        backgroundBlur = AppPreferences.styleBlur(getContext(), secondary);
        backgroundDim = AppPreferences.styleDim(getContext(), secondary);
        lyricLineCount = AppPreferences.styleLyricLines(getContext(), secondary);
        pureShowTranslation = AppPreferences.pureShowTranslation(getContext(), secondary);
        overlayStyle = compactTextOnly ? "compact" : AppPreferences.overlayStyle(getContext(), secondary);
        // 时间段配色（issue #34）在读取时就换算成具体深浅色，样式与主题的判定逻辑无需知道时间表。
        themeMode = AppPreferences.resolvedThemeMode(getContext());
        lyricsFollowTheme = compactTextOnly ? AppPreferences.statusLyricFollowTheme(getContext())
                : AppPreferences.lyricsFollowTheme(getContext());
        refinedDisplayMode = AppPreferences.refinedDisplayMode(getContext(), secondary);
        refinedColorScheme = AppPreferences.refinedColorScheme(getContext(), secondary);
        refinedAccentVariant = AppPreferences.refinedAccentVariant(getContext(), secondary);
        refinedTextEffect = AppPreferences.refinedTextEffect(getContext(), secondary);
        refinedProgressBottom = AppPreferences.refinedProgressBottom(getContext(), secondary);
        refinedCoverHorizontal = AppPreferences.refinedCoverHorizontal(getContext(), secondary);
        refinedCoverVertical = AppPreferences.refinedCoverVertical(getContext(), secondary);
        refinedRectangleCover = AppPreferences.refinedRectangleCover(getContext(), secondary);
        refinedCoverShadow = AppPreferences.refinedCoverShadow(getContext(), secondary);
        refinedBackgroundType = AppPreferences.refinedBackgroundType(getContext(), secondary);
        hideCoverWithoutArt = AppPreferences.hideCoverWithoutArt(getContext(), secondary);
        starfieldDensityPercent = AppPreferences.starfieldDensityPercent(getContext(), secondary);
        starfieldSpeedPercent = AppPreferences.starfieldSpeedPercent(getContext(), secondary);
        starfieldSizePercent = AppPreferences.starfieldSizePercent(getContext(), secondary);
        starfieldFollowCover = AppPreferences.starfieldFollowCover(getContext(), secondary);
        starfieldStill = AppPreferences.starfieldStill(getContext(), secondary);
        starfieldFps = AppPreferences.starfieldFps(getContext(), secondary);
        artistScalePercent = AppPreferences.artistScale(getContext(), secondary);
        panelShadowPercent = AppPreferences.panelShadowPercent(getContext(), secondary);
        styleMaskMode = AppPreferences.styleMaskMode(getContext(), secondary);
        styleBrightnessPercent = AppPreferences.styleBrightness(getContext(), secondary);
        contentPaddingPercent = AppPreferences.contentPaddingPercent(getContext(), secondary);
        longLineMode = AppPreferences.longLineMode(getContext(), secondary);
        refinedStaticFluid = AppPreferences.refinedStaticFluid(getContext(), secondary);
        refinedDynamicGradient = AppPreferences.refinedDynamicGradient(getContext(), secondary);
        refinedLyricFontSize = AppPreferences.refinedLyricFontSize(getContext(), secondary);
        refinedOriginalBold = AppPreferences.refinedOriginalBold(getContext(), secondary);
        refinedLyricFade = AppPreferences.refinedLyricFade(getContext(), secondary);
        refinedLyricZoom = AppPreferences.refinedLyricZoom(getContext(), secondary);
        refinedLyricBlur = AppPreferences.refinedLyricBlur(getContext(), secondary);
        refinedLyricRotate = AppPreferences.refinedLyricRotate(getContext(), secondary);
        refinedRotateCurvature = AppPreferences.refinedRotateCurvature(getContext(), secondary);
        refinedKaraokeAnimation = AppPreferences.refinedKaraokeAnimation(getContext(), secondary);
        refinedCurrentAlign = AppPreferences.refinedCurrentAlign(getContext(), secondary);
        refinedShowTranslation = AppPreferences.refinedShowTranslation(getContext(), secondary);
        refinedLyricGlow = AppPreferences.refinedLyricGlow(getContext(), secondary);
        customTypeface = CustomFontStore.load(getContext());
        // 模糊歌词改走离屏位图（见 drawBlurredLine），面板本身不再需要软件渲染：以前 Refined / 紧凑
        // 打开「歌词模糊」、AMLL 一律把整块面板丢给 CPU，帧率再高也拉不回来（issue #59 后续）。
        setLayerType(LAYER_TYPE_NONE, null);
        layoutConfig = LyricsLayoutConfig.load(getContext(), secondary);
        recycleBlurPreview();
        blurPreview = null;
        blurSource = null;
        clearTextCaches();
        previousLyricDissolve.reset();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        compactSpectrumAnimating = false;
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        if (width <= 2f || height <= 2f) return;
        panelRect.set(fullscreen ? 0f : 1f, fullscreen ? 0f : 1f,
                fullscreen ? width : width - 1f, fullscreen ? height : height - 1f);
        long now = SystemClock.elapsedRealtime();
        updateBrowseSpring(now);
        if (!browsingLyrics && browseUntilElapsedMs > 0L && now >= browseUntilElapsedMs) {
            browseUntilElapsedMs = 0L;
            browseSettling = false;
            browseVisualOffsetPx = 0f;
            browseVelocityPxPerSecond = 0f;
        }
        lyricOffsetMs = AppPreferences.lyricOffsetMs(getContext(), secondary,
                MusicStateStore.activeSourceId());
        MusicSnapshot snapshot = browsingLyrics || browseUntilElapsedMs > now
                ? MusicStateStore.snapshotForLyricBrowse(lyricOffsetMs, browsePositionMs)
                : MusicStateStore.snapshot(lyricOffsetMs);
        frameTitle = snapshot.title;
        frameArtist = snapshot.artist;
        frameSourceName = snapshot.sourceName;
        frameLyricSourceName = snapshot.lyricSourceName;
        // 本帧的播放位置供碟片旋转使用（issue #22），并在同一处推进「正在匹配」的状态（issue #45）。
        framePositionMs = snapshot.positionMs;
        framePlaying = snapshot.playing;
        frameAlbumArtMissing = snapshot.albumArt == null || snapshot.albumArt.isRecycled();
        updateMatchingState(snapshot, now);
        refreshScheduledTheme(now);

        // The dissolve timeline has to be advanced before the layouts draw: they ask it, glyph
        // by glyph, how much of the previous line is still there this frame.
        boolean browsing = browsingLyrics || browseSettling || browseUntilElapsedMs > now;
        if (previousLyricParticles && snapshot.active) {
            previousLyricDissolve.sync(snapshot.lyrics.lineStartMs, snapshot.lyrics.previousLyric,
                    snapshot.playing, browsing, snapshot.positionMs, now);
        } else {
            previousLyricDissolve.reset();
        }
        // Runs after sync(): a line change clears the per-word erase, then the new line picks up
        // however much of itself has already been sung. The erase is part of the dissolve, so it
        // only runs while that is switched on.
        //
        // It follows the *playing* line even while the user is scrolling through the sheet: the
        // erase is scoped to that one line identity, so the words keep coming apart as they are
        // sung (instead of being restored and re-eaten around every scroll), while the lines the
        // user scrolled to are drawn whole because the erase does not belong to them.
        MusicSnapshot playingLine = browsing ? MusicStateStore.snapshot(lyricOffsetMs) : snapshot;
        previousLyricDissolve.syncWordErase(playingLine.lyrics.lineStartMs,
                playingLine.lyrics.completedLyric, playingLine.lyrics.lyric.length(),
                wordDissolve && previousLyricParticles && playingLine.active,
                playingLine.lyrics.wordTimed, now);

        if ("amll".equals(overlayStyle)) {
            drawAmll(canvas, snapshot, density);
        } else if ("refined".equals(overlayStyle)) {
            drawRefined(canvas, snapshot, density);
        } else if ("compact".equals(overlayStyle)) {
            // Transparent compact overlays must not retain pixels from a previous style
            // or frame, otherwise the old next-line lyric can show through the current line.
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            drawCompact(canvas, snapshot, density);
        } else if ("pure".equals(overlayStyle)) {
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            drawPure(canvas, snapshot, density);
        } else if ("island".equals(overlayStyle)) {
            // 灵动岛也是「悬浮小条」：先清一帧，避免上一帧的像素残留（issue #54）。
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            drawIsland(canvas, snapshot, density);
        } else if ("pip".equals(overlayStyle)) {
            drawPip(canvas, snapshot, density);
        } else if ("custom".equals(overlayStyle)) {
            drawCustom(canvas, snapshot, density);
        } else {
            drawDefault(canvas, snapshot, density);
        }
        // 面板边缘阴影画在内容之后、控件之前（issue #56）：只压到边缘留白，不盖文字与按键。
        drawPanelEdgeShadow(canvas);
        if (browsingLyrics || browseUntilElapsedMs > now) {
            drawBrowseIndicator(canvas, snapshot, density);
        }
        if (!"compact".equals(overlayStyle) && !"pure".equals(overlayStyle)
                && AppPreferences.spectrumEnabled(getContext(), secondary)) {
            drawSharedSpectrum(canvas, snapshot, density);
        }
        if (AppPreferences.showPlaybackControls(getContext(), secondary)
                && !compactTextOnly && !"pure".equals(overlayStyle)) {
            drawPlaybackControls(canvas, snapshot, density);
        }
        drawPreviousLyricDust(canvas, now);
        drawMatchingIndicator(canvas, density, now);
        scheduleNextFrame(nextFrameDelay(snapshot, now));
    }

    private void scheduleNextFrame(long delayMs) {
        // A fixed 16 ms delay is not synchronized with the display. On some devices the
        // runnable lands just after VSync, causing the first part of a lyric transition to
        // alternate between frames. Let the compositor schedule animation frames instead.
        // 17 ms 也是「一帧」的量级（60 Hz ≈ 16.7 ms），星空的 60 fps 档同样走这条路。
        if (delayMs <= 17L) {
            postInvalidateOnAnimation();
            return;
        }
        // 星空 / 流体这类按帧率跑的背景：固定延时落在两个刷新周期之间时，画面会在「差一帧」和
        // 「差两帧」之间来回跳，看起来就是一顿一顿。改由 Choreographer 按刷新周期对齐（issue #59）。
        if (hasAnimatedRefinedBackground()) {
            scheduleBackgroundFrame(delayMs);
            return;
        }
        postInvalidateDelayed(delayMs);
    }

    /**
     * 按用户设的帧率排下一帧：把间隔换算成整数个刷新周期，再用 {@link Choreographer} 在对应的
     * vsync 上触发重绘。既保住「帧率可调」（省电），又不会像 {@code postInvalidateDelayed} 那样
     * 和 vsync 错位（issue #59）。
     */
    private void scheduleBackgroundFrame(long delayMs) {
        if (backgroundFramePosted) return;
        long period = displayFramePeriodMs();
        long frames = Math.max(1L, Math.round(delayMs / (float) period));
        if (frames <= 1L) {
            postInvalidateOnAnimation();
            return;
        }
        backgroundFramePosted = true;
        Choreographer.getInstance().postFrameCallbackDelayed(backgroundFrameCallback,
                frames * period);
    }

    /** 这一块屏幕一个刷新周期多少毫秒；取不到时按 60 Hz 算。 */
    private long displayFramePeriodMs() {
        android.view.Display display = getDisplay();
        float refreshRate = display == null ? 60f : display.getRefreshRate();
        if (!(refreshRate > 1f) || refreshRate > 240f) refreshRate = 60f;
        return Math.max(4L, Math.round(1_000f / refreshRate));
    }

    private long nextFrameDelay(MusicSnapshot snapshot, long nowElapsedMs) {
        if (browsingLyrics || browseSettling) return 16L;
        if (compactSpectrumAnimating) return 16L;
        if (compactMarqueeActive && snapshot.playing) return 16L;
        if (lyricScrollAnimationStartedMs > 0L
                && nowElapsedMs - lyricScrollAnimationStartedMs < 500L) return 16L;
        if (basicLyricScrollAnimationStartedMs > 0L
                && nowElapsedMs - basicLyricScrollAnimationStartedMs < 360L) return 16L;
        if (amllScrollAnimationStartedMs > 0L
                && nowElapsedMs - amllScrollAnimationStartedMs < 620L) return 16L;
        if (browseUntilElapsedMs > nowElapsedMs) {
            return Math.max(16L, Math.min(250L, browseUntilElapsedMs - nowElapsedMs));
        }
        // 匹配动画与碟片旋转都要连续帧：16 ms 走 postInvalidateOnAnimation，跟屏幕刷新对齐，
        // 30 fps 的固定延迟会在某些车机上和 vsync 打架，看起来一顿一顿（issue #22/#45）。
        if (matchingIndicatorVisible(nowElapsedMs)) return 16L;
        if (coverRotation && snapshot.playing && coverCanRotate()) return 16L;
        if (snapshot.active) {
            if (previousLyricDissolve.isAnimating(nowElapsedMs)) return 16L;
            if (wordDissolve && previousLyricParticles
                    && previousLyricDissolve.isEraseAnimating(snapshot.lyrics.lineStartMs)) {
                return 16L;
            }
            if (snapshot.lyrics.wordTimed && snapshot.lyrics.wordDurationMs > 0L
                    && !snapshot.lyrics.currentWord.isEmpty()) return 16L;
        }
        if ("amll".equals(overlayStyle)) return 33L;
        // Fluid, dynamic-gradient and starfield backgrounds are time-based, and they keep their own
        // cadence whether or not music is playing: gating them behind 播放中 made the starfield step
        // at 2.5 fps while paused, which is exactly the 「一顿一顿」 this setting is meant to fix.
        if (hasAnimatedRefinedBackground()) return animatedBackgroundFrameDelay();
        if (!snapshot.active) return 750L;
        if (!snapshot.playing) return 400L;
        if (snapshot.lyrics.interlude) return 33L;
        return 100L;
    }

    private boolean hasAnimatedRefinedBackground() {
        if (!usesRefinedVisualStyle()) return false;
        // 顶部歌词条画自己的背景（drawTopLyricBackground），不吃「背景类型」，也就没必要为了它保持帧率。
        if (compactTextOnly) return false;
        return ("fluid".equals(refinedBackgroundType) && !refinedStaticFluid)
                || ("gradient".equals(refinedBackgroundType) && refinedDynamicGradient)
                // 星空要连续帧才会流动；「静止」是定格星点，不需要（issue #59）。
                || ("starfield".equals(refinedBackgroundType) && !starfieldStill);
    }

    /**
     * 动态背景的一帧间隔：星空按用户设的帧率（issue #59 的「星空帧率」，最低 5 fps 最省电），流体与
     * 动态渐变沿用 30 fps。逐字歌词、碟片旋转这些跟歌词同步的动画仍然优先走 16 ms，星空跟着一起画。
     */
    private long animatedBackgroundFrameDelay() {
        if (!"starfield".equals(refinedBackgroundType) || starfieldStill) return 33L;
        return StarfieldField.frameDelayMs(starfieldFps);
    }

    boolean isLyricGestureRegion(float x, float y) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        if (playbackControlAt(x, y) != null) return false;
        MusicSnapshot snapshot = MusicStateStore.snapshot(effectiveLyricOffsetMs());
        if (!snapshot.lyricAvailable) return false;
        if ("amll".equals(overlayStyle)) {
            return x >= getWidth() * 0.45f;
        }
        if ("refined".equals(overlayStyle)) {
            return !"cover".equals(refinedDisplayMode)
                    && ("lyrics".equals(refinedDisplayMode) || x >= getWidth() * 0.46f);
        }
        // Compact and pure layouts have no spare metadata area. Reserve their full surface for
        // moving the overlay instead of swallowing every touch as lyric browsing.
        if (OverlayStyleInteraction.reservesSurfaceForWindowDrag(overlayStyle)) return false;
        if ("pip".equals(overlayStyle)) return y >= getHeight() * 0.34f;
        if ("custom".equals(overlayStyle)) return y >= getHeight() * 0.28f;
        return y >= getHeight() * 0.24f && y <= getHeight() * 0.86f;
    }

    /**
     * Union of the playback buttons that are currently shown, in this view's own coordinates, or
     * {@code null} when none are. The overlay service keeps a transparent touch pad over exactly
     * this area while the window itself has stopped accepting touches.
     */
    android.graphics.Rect playbackControlsBounds() {
        if (!AppPreferences.showPlaybackControls(getContext(), secondary)
                || getWidth() <= 0 || getHeight() <= 0) return null;
        float density = getResources().getDisplayMetrics().density;
        PlaybackControlLayout layout = playbackControlLayout(density);
        updatePlaybackButtons(layout);
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int index = 0; index < playbackButtonShown.length; index++) {
            if (!playbackButtonShown[index]) continue;
            float radius = playbackButtonRadius(layout, index);
            left = Math.min(left, playbackButtonX[index] - radius);
            right = Math.max(right, playbackButtonX[index] + radius);
            top = Math.min(top, layout.centerY - radius);
            bottom = Math.max(bottom, layout.centerY + radius);
        }
        if (right <= left) return null;
        // A little slack so a fingertip aimed at a button edge still lands on it.
        float slack = layout.radius * .5f;
        android.graphics.Rect bounds = new android.graphics.Rect(
                (int) Math.floor(left - slack), (int) Math.floor(top - slack),
                (int) Math.ceil(right + slack), (int) Math.ceil(bottom + slack));
        return bounds.intersect(0, 0, getWidth(), getHeight()) ? bounds : null;
    }

    MediaControlAction playbackControlAt(float x, float y) {
        if (!AppPreferences.showPlaybackControls(getContext(), secondary)
                || getWidth() <= 0 || getHeight() <= 0) return null;
        float density = getResources().getDisplayMetrics().density;
        PlaybackControlLayout layout = playbackControlLayout(density);
        updatePlaybackButtons(layout);
        if (playbackButtonShown[PLAYBACK_BUTTON_PREVIOUS]
                && insideCircle(x, y, playbackButtonX[PLAYBACK_BUTTON_PREVIOUS],
                layout.centerY, layout.radius)) {
            return MediaControlAction.PREVIOUS;
        }
        if (playbackButtonShown[PLAYBACK_BUTTON_PLAY_PAUSE]
                && insideCircle(x, y, playbackButtonX[PLAYBACK_BUTTON_PLAY_PAUSE],
                layout.centerY, layout.radius * 1.12f)) {
            return MediaControlAction.TOGGLE_PLAY_PAUSE;
        }
        if (playbackButtonShown[PLAYBACK_BUTTON_NEXT]
                && insideCircle(x, y, playbackButtonX[PLAYBACK_BUTTON_NEXT],
                layout.centerY, layout.radius)) {
            return MediaControlAction.NEXT;
        }
        return null;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lyricOffsetMs = effectiveLyricOffsetMs();
                MusicSnapshot live = MusicStateStore.snapshot(lyricOffsetMs);
                if (!live.lyricAvailable) return false;
                long now = SystemClock.elapsedRealtime();
                updateBrowseSpring(now);
                browseSettling = false;
                browsePositionMs = LyricsBrowseState.startingPosition(now,
                        browseUntilElapsedMs, browsePositionMs,
                        live.positionMs + lyricOffsetMs);
                browsingLyrics = true;
                browseMoved = false;
                browseTravelPx = 0f;
                browseLastY = event.getY();
                browseLastEventTimeMs = event.getEventTime();
                browseVelocityPxPerSecond = 0f;
                browseUntilElapsedMs = 0L;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!browsingLyrics) return false;
                float delta = event.getY() - browseLastY;
                long eventTime = event.getEventTime();
                long elapsed = Math.max(1L, eventTime - browseLastEventTimeMs);
                float instantVelocity = delta * 1_000f / elapsed;
                browseVelocityPxPerSecond = browseVelocityPxPerSecond * 0.28f
                        + instantVelocity * 0.72f;
                browseLastY = event.getY();
                browseLastEventTimeMs = eventTime;
                browseTravelPx += Math.abs(delta);
                browseMoved = browseTravelPx >= 6f
                        * getResources().getDisplayMetrics().density;
                browseVisualOffsetPx += delta;
                consumeBrowseSteps(browseStepPx());
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!browsingLyrics) return false;
                browsingLyrics = false;
                long releaseTime = SystemClock.elapsedRealtime();
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    projectBrowseRelease(browseStepPx());
                } else {
                    browseVelocityPxPerSecond = 0f;
                }
                browseUntilElapsedMs = releaseTime + 2_500L;
                if (animationsEnabled() && (Math.abs(browseVisualOffsetPx) > 0.35f
                        || Math.abs(browseVelocityPxPerSecond) > 4f)) {
                    browseSettling = true;
                    browseSettleLastFrameMs = releaseTime;
                } else {
                    browseSettling = false;
                    browseVisualOffsetPx = 0f;
                    browseVelocityPxPerSecond = 0f;
                }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                if (!browseMoved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    performClick();
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    void cancelLyricBrowseForOverlayLock() {
        browsingLyrics = false;
        browseMoved = false;
        browseUntilElapsedMs = 0L;
        browseSettling = false;
        browseVisualOffsetPx = 0f;
        browseVelocityPxPerSecond = 0f;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    private void consumeBrowseSteps(float stepPx) {
        if (stepPx <= 0f) return;
        while (browseVisualOffsetPx <= -stepPx) {
            long shifted = MusicStateStore.shiftLyricPosition(browsePositionMs, 1);
            if (shifted == browsePositionMs) {
                float overshoot = -(browseVisualOffsetPx + stepPx);
                browseVisualOffsetPx = -stepPx
                        - LyricPreviewMotion.rubberBand(overshoot, stepPx * 2f);
                return;
            }
            browsePositionMs = shifted;
            browseVisualOffsetPx += stepPx;
        }
        while (browseVisualOffsetPx >= stepPx) {
            long shifted = MusicStateStore.shiftLyricPosition(browsePositionMs, -1);
            if (shifted == browsePositionMs) {
                float overshoot = browseVisualOffsetPx - stepPx;
                browseVisualOffsetPx = stepPx
                        + LyricPreviewMotion.rubberBand(overshoot, stepPx * 2f);
                return;
            }
            browsePositionMs = shifted;
            browseVisualOffsetPx -= stepPx;
        }
    }

    private void projectBrowseRelease(float stepPx) {
        int lineDelta = LyricPreviewMotion.projectedLineDelta(browseVisualOffsetPx,
                browseVelocityPxPerSecond, stepPx);
        int direction = Integer.compare(lineDelta, 0);
        for (int i = 0; i < Math.abs(lineDelta); i++) {
            long shifted = MusicStateStore.shiftLyricPosition(browsePositionMs, direction);
            if (shifted == browsePositionMs) break;
            browsePositionMs = shifted;
            browseVisualOffsetPx += direction > 0 ? stepPx : -stepPx;
        }
    }

    private void updateBrowseSpring(long nowElapsedMs) {
        if (!browseSettling) return;
        float deltaSeconds = (nowElapsedMs - browseSettleLastFrameMs) / 1_000f;
        browseSettleLastFrameMs = nowElapsedMs;
        LyricPreviewMotion.SpringState state = LyricPreviewMotion.stepCritical(
                browseVisualOffsetPx, browseVelocityPxPerSecond, deltaSeconds, 0.38f);
        browseVisualOffsetPx = state.position;
        browseVelocityPxPerSecond = state.velocity;
        browseSettling = !state.settled;
    }

    private float browseStepPx() {
        if (("refined".equals(overlayStyle) || "amll".equals(overlayStyle))
                && lastRefinedBrowseStepPx > 1f) {
            return lastRefinedBrowseStepPx;
        }
        return 34f * getResources().getDisplayMetrics().density;
    }

    private boolean manualPreviewActive() {
        return browsingLyrics || browseSettling
                || browseUntilElapsedMs > SystemClock.elapsedRealtime();
    }

    private static boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || ValueAnimator.areAnimatorsEnabled();
    }

    private boolean usesRefinedVisualStyle() {
        return "refined".equals(overlayStyle) || "compact".equals(overlayStyle);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    /** 这一屏实际显示的播放按键与它们的圆心（issue #72）：三个调用点共用一份排布，避免各算各的。 */
    private static final int PLAYBACK_BUTTON_PREVIOUS = 0;
    private static final int PLAYBACK_BUTTON_PLAY_PAUSE = 1;
    private static final int PLAYBACK_BUTTON_NEXT = 2;
    private final boolean[] playbackButtonShown = new boolean[3];
    private final float[] playbackButtonX = new float[3];

    /** 按这一屏的开关算出每个按钮画在哪：只显示 1~2 个时整组围绕 {@code layout.centerX} 居中。 */
    private void updatePlaybackButtons(PlaybackControlLayout layout) {
        playbackButtonShown[PLAYBACK_BUTTON_PREVIOUS] =
                AppPreferences.showPreviousButton(getContext(), secondary);
        playbackButtonShown[PLAYBACK_BUTTON_PLAY_PAUSE] =
                AppPreferences.showPlayPauseButton(getContext(), secondary);
        playbackButtonShown[PLAYBACK_BUTTON_NEXT] =
                AppPreferences.showNextButton(getContext(), secondary);
        int count = PlaybackControlLayoutMath.visibleCount(
                playbackButtonShown[PLAYBACK_BUTTON_PREVIOUS],
                playbackButtonShown[PLAYBACK_BUTTON_PLAY_PAUSE],
                playbackButtonShown[PLAYBACK_BUTTON_NEXT]);
        int slot = 0;
        for (int index = 0; index < playbackButtonShown.length; index++) {
            playbackButtonX[index] = playbackButtonShown[index]
                    ? layout.centerX + PlaybackControlLayoutMath.slotOffset(slot++, count,
                            layout.spacing)
                    : layout.centerX;
        }
    }

    private float playbackButtonRadius(PlaybackControlLayout layout, int index) {
        return index == PLAYBACK_BUTTON_PLAY_PAUSE ? layout.radius * 1.12f : layout.radius;
    }

    private void drawPlaybackControls(Canvas canvas, MusicSnapshot snapshot, float density) {
        PlaybackControlLayout layout = playbackControlLayout(density);
        updatePlaybackButtons(layout);
        boolean showPrevious = playbackButtonShown[PLAYBACK_BUTTON_PREVIOUS];
        boolean showPlayPause = playbackButtonShown[PLAYBACK_BUTTON_PLAY_PAUSE];
        boolean showNext = playbackButtonShown[PLAYBACK_BUTTON_NEXT];
        if (!showPrevious && !showPlayPause && !showNext) return;

        int fill = snapshot.active ? 0xC92B405A : 0x8A26384E;
        int icon = snapshot.active ? 0xFFF5F9FF : 0xFF9AAABB;
        int accent = snapshot.playing ? 0xFFFFCA66 : 0xFF6EE7F2;
        int primaryFill = snapshot.active ? 0xE0445D78 : fill;
        if ("refined".equals(overlayStyle)) {
            fill = snapshot.active ? 0x8C243B52 : 0x62243852;
            primaryFill = snapshot.active ? 0xC13A5872 : fill;
        } else if ("compact".equals(overlayStyle)) {
            // 底板按实际显示的按钮收窄（issue #72）：三键时与改动前的宽度完全一致。
            float extent = 0f;
            for (int index = 0; index < playbackButtonShown.length; index++) {
                if (!playbackButtonShown[index]) continue;
                extent = Math.max(extent, Math.abs(playbackButtonX[index] - layout.centerX)
                        + playbackButtonRadius(layout, index));
            }
            float halfWidth = PlaybackControlLayoutMath.backdropHalfWidth(extent, layout.radius);
            workRect.set(layout.centerX - halfWidth, layout.centerY - layout.radius * 1.42f,
                    layout.centerX + halfWidth, layout.centerY + layout.radius * 1.42f);
            paint.setColor(snapshot.active ? 0xC51A2535 : 0x8A1A2535);
            canvas.drawRoundRect(workRect, layout.radius * 1.45f, layout.radius * 1.45f, paint);
            fill = 0x00000000;
            icon = snapshot.active ? 0xFFE9F2FA : 0xFF9AAABB;
            primaryFill = snapshot.active ? 0xC43B5B78 : fill;
        } else if ("pip".equals(overlayStyle)) {
            fill = snapshot.active ? 0xD6F3E7D7 : 0x96E7D8C5;
            icon = 0xFF312820;
            accent = snapshot.playing ? 0xFF7B3F20 : 0xFF4D453E;
            primaryFill = snapshot.active ? 0xFFE2BA8C : fill;
        } else if ("custom".equals(overlayStyle)) {
            fill = snapshot.active ? 0xA31B3048 : 0x641B3048;
            primaryFill = snapshot.active ? 0xD0375A78 : fill;
        } else if ("amll".equals(overlayStyle)) {
            fill = snapshot.active ? 0x3DFFFFFF : 0x1FFFFFFF;
            icon = snapshot.active ? 0xF2FFFFFF : 0x88FFFFFF;
            accent = 0xFFFFFFFF;
            primaryFill = snapshot.active ? 0x66FFFFFF : fill;
        }

        if (showPrevious) {
            drawPlaybackButton(canvas, playbackButtonX[PLAYBACK_BUTTON_PREVIOUS], layout.centerY,
                    layout.radius, fill, icon, MediaControlAction.PREVIOUS, false);
        }
        if (showPlayPause) {
            drawPlaybackButton(canvas, playbackButtonX[PLAYBACK_BUTTON_PLAY_PAUSE],
                    layout.centerY, layout.radius * 1.12f, primaryFill, accent,
                    MediaControlAction.TOGGLE_PLAY_PAUSE, snapshot.playing);
        }
        if (showNext) {
            drawPlaybackButton(canvas, playbackButtonX[PLAYBACK_BUTTON_NEXT], layout.centerY,
                    layout.radius, fill, icon, MediaControlAction.NEXT, false);
        }
    }

    private void drawPlaybackButton(Canvas canvas, float centerX, float centerY, float radius,
                                    int fill, int icon, MediaControlAction action,
                                    boolean playing) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fill);
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, radius * 0.075f));
        paint.setColor(withAlpha(icon, 125));
        canvas.drawCircle(centerX, centerY, radius, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(icon);
        float iconHalf = radius * 0.34f;
        if (action == MediaControlAction.TOGGLE_PLAY_PAUSE) {
            if (playing) {
                float barWidth = Math.max(2f, radius * 0.19f);
                float gap = radius * 0.12f;
                workRect.set(centerX - gap - barWidth, centerY - iconHalf,
                        centerX - gap, centerY + iconHalf);
                canvas.drawRoundRect(workRect, barWidth, barWidth, paint);
                workRect.set(centerX + gap, centerY - iconHalf,
                        centerX + gap + barWidth, centerY + iconHalf);
                canvas.drawRoundRect(workRect, barWidth, barWidth, paint);
            } else {
                iconPath.reset();
                iconPath.moveTo(centerX - iconHalf * 0.52f, centerY - iconHalf);
                iconPath.lineTo(centerX - iconHalf * 0.52f, centerY + iconHalf);
                iconPath.lineTo(centerX + iconHalf, centerY);
                iconPath.close();
                canvas.drawPath(iconPath, paint);
            }
            return;
        }
        boolean previous = action == MediaControlAction.PREVIOUS;
        float direction = previous ? -1f : 1f;
        float baseX = centerX - direction * iconHalf;
        iconPath.reset();
        iconPath.moveTo(baseX, centerY - iconHalf);
        iconPath.lineTo(baseX, centerY + iconHalf);
        iconPath.lineTo(centerX + direction * iconHalf * 0.78f, centerY);
        iconPath.close();
        canvas.drawPath(iconPath, paint);
        float barX = centerX + direction * iconHalf * 1.05f;
        canvas.drawRect(barX - radius * 0.075f, centerY - iconHalf,
                barX + radius * 0.075f, centerY + iconHalf, paint);
    }

    private PlaybackControlLayout playbackControlLayout(float density) {
        float width = getWidth();
        float height = getHeight();
        float contentScale = styleCanvasScale(density);
        float controlScale = AppPreferences.playbackControlScale(getContext()) / 100f;
        float radius = 16f * density * contentScale;
        float spacing = radius * 2.85f;
        float centerX = width * 0.5f;
        float centerY = height - radius - 7f * density * contentScale;
        if (fullscreen && "refined".equals(overlayStyle)) {
            radius = 14f * density;
            spacing = radius * 3.1f;
            centerX = width * 0.5f;
            centerY = height - Math.max(24f * density, height * 0.05f);
        } else if (fullscreen && "amll".equals(overlayStyle)) {
            radius = 15f * density;
            spacing = radius * 3.0f;
            centerX = width < height ? width * 0.5f : width * 0.225f;
            centerY = width < height ? height - 42f * density : height * 0.83f;
        } else if (fullscreen && "pip".equals(overlayStyle)) {
            radius = 13f * density;
            spacing = radius * 2.6f;
            centerX = width - spacing - radius - 14f * density;
            centerY = height - radius - 12f * density;
        } else if ("refined".equals(overlayStyle)) {
            radius = 16f * density * contentScale;
            spacing = radius * 2.7f;
            centerX = width * 0.75f;
            centerY = radius + 18f * density * contentScale;
        } else if ("compact".equals(overlayStyle)) {
            radius = 8.8f * density * contentScale;
            spacing = radius * 2.45f;
            centerX = width * 0.38f;
            centerY = height - radius - 11f * density * contentScale;
        } else if ("pip".equals(overlayStyle)) {
            radius = 13f * density * contentScale;
            spacing = radius * 2.6f;
            centerX = width - (spacing + radius + 14f * density * contentScale);
            centerY = height - radius - 12f * density * contentScale;
        } else if ("custom".equals(overlayStyle)) {
            radius = 14f * density * contentScale;
            spacing = radius * 2.7f;
            centerX = width - (spacing + radius + 14f * density * contentScale);
            centerY = height - radius - 12f * density * contentScale;
        } else if ("amll".equals(overlayStyle)) {
            radius = 14f * density * contentScale;
            spacing = radius * 2.75f;
            centerX = width * 0.225f;
            centerY = height - radius - 11f * density * contentScale;
        }
        radius *= controlScale;
        spacing *= controlScale;
        // 偏移按面板尺寸的百分比算、按屏读（issue #73）；夹取用「这一组按钮实际占的半宽/半高」，
        // 而不是写死的三键 inset——只留 1 个键时也能真的贴到面板边上。
        centerX = PlaybackControlLayoutMath.resolvedCenter(centerX, width,
                AppPreferences.playbackControlX(getContext(), secondary));
        centerY = PlaybackControlLayoutMath.resolvedCenter(centerY, height,
                AppPreferences.playbackControlY(getContext(), secondary));
        float halfWidth = playbackButtonsHalfExtent(radius, spacing, true)
                + 4f * density * contentScale;
        float halfHeight = radius * 1.12f + 4f * density * contentScale;
        centerX = PlaybackControlLayoutMath.clampCenter(centerX, width, halfWidth);
        centerY = PlaybackControlLayoutMath.clampCenter(centerY, height, halfHeight);
        return new PlaybackControlLayout(centerX, centerY, radius, spacing);
    }

    /**
     * 这一屏实际显示的按钮组在某个方向上的半尺寸（横向含间距，纵向只有半径）。用于把整组夹在面板
     * 内：只显示 1 个键时它比三键小得多，以前写死的 inset 会让按钮离边缘很远（issue #73）。
     */
    private float playbackButtonsHalfExtent(float radius, float spacing, boolean horizontal) {
        boolean previous = AppPreferences.showPreviousButton(getContext(), secondary);
        boolean playPause = AppPreferences.showPlayPauseButton(getContext(), secondary);
        boolean next = AppPreferences.showNextButton(getContext(), secondary);
        int count = PlaybackControlLayoutMath.visibleCount(previous, playPause, next);
        if (count == 0) return radius;
        int slot = 0;
        float extent = radius;
        for (int index = 0; index < 3; index++) {
            boolean shown = index == 0 ? previous : index == 1 ? playPause : next;
            if (!shown) continue;
            float buttonRadius = index == PLAYBACK_BUTTON_PLAY_PAUSE ? radius * 1.12f : radius;
            float offset = horizontal
                    ? Math.abs(PlaybackControlLayoutMath.slotOffset(slot, count, spacing)) : 0f;
            extent = Math.max(extent, offset + buttonRadius);
            slot++;
        }
        return extent;
    }

    private static final class PlaybackControlLayout {
        final float centerX;
        final float centerY;
        final float radius;
        final float spacing;

        PlaybackControlLayout(float centerX, float centerY, float radius, float spacing) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.spacing = spacing;
        }
    }

    private static boolean insideCircle(float x, float y, float centerX, float centerY,
                                        float radius) {
        float dx = x - centerX;
        float dy = y - centerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    /** 内容留白（issue #49）：未设置（-1）时原样返回各样式写死的值，0% = 文字贴边。 */
    private float contentPad(float legacyPadPx) {
        return ContentPaddingMath.padPx(legacyPadPx, contentPaddingPercent);
    }

    private void drawDefault(Canvas canvas, MusicSnapshot snapshot, float density) {
        float width = getWidth();
        float height = getHeight();
        float contentScale = ClassicLayoutMath.contentScale(width, height, density);
        float pad = contentPad(18f * density * contentScale);
        if (opacity > 0) {
            drawPanelShadow(canvas, 24f * density * contentScale,
                    Color.argb(Math.round(opacity * 2.55f), 6, 15, 27));
            paint.setColor(withAlpha(0x406EE7F2,
                    Math.round(Color.alpha(0x406EE7F2) * opacity / 100f)));
            workRect.set(pad, 10f * density * contentScale,
                    width - pad, 13f * density * contentScale);
            canvas.drawRoundRect(workRect, 2f * density * contentScale,
                    2f * density * contentScale, paint);
        }

        float usableWidth = Math.max(1f, width - pad * 2f);
        float previewShift = browseVisualOffsetPx;
        float unit = (secondary ? 1.12f : 1f) * contentScale;
        float densityUnit = density * unit;
        String translation = secondaryLyric(snapshot);
        boolean hasTranslation = !translation.isEmpty();
        // 「歌词显示行数」：经典样式最多摆 7 行歌词（上一句 ×3 / 本句 / 下一句 ×3）。1 行时只画本句，
        // 2 行时画本句与下一句，之后本句两侧交替补行，空行位保留着不动（issue #26 / #64）。
        int lyricRows = ClassicLayoutMath.visibleRowCount(lyricLineCount);
        int followingRows = ClassicLayoutMath.followingRowCount(lyricRows);
        int precedingRows = ClassicLayoutMath.precedingRowCount(lyricRows);
        float controlReserve = AppPreferences.showPlaybackControls(getContext(), secondary)
                ? 31f * density * contentScale : 0f;
        // 可用区：所有行都排在这条线以上。频谱打开时它再往上让出频谱那一段。
        float areaBottom = height - 21f * density * contentScale - controlReserve;
        if (AppPreferences.spectrumEnabled(getContext(), secondary)) {
            float spectrumHeight = SpectrumLayoutMath.heightPx(
                    AppPreferences.spectrumHeightPercent(getContext(), secondary), density,
                    height, SpectrumLayoutMath.LEGACY_MIN_DP, SpectrumLayoutMath.LEGACY_MAX_DP,
                    SpectrumLayoutMath.LEGACY_PANEL_RATIO);
            boolean controlsVisible = AppPreferences.showPlaybackControls(getContext(), secondary)
                    && (AppPreferences.showPreviousButton(getContext(), secondary)
                    || AppPreferences.showPlayPauseButton(getContext(), secondary)
                    || AppPreferences.showNextButton(getContext(), secondary));
            float spectrumBottom = height - SpectrumLayoutMath.bottomInsetPx(density,
                    controlsVisible, AppPreferences.spectrumGapDp(getContext(), secondary));
            areaBottom = spectrumBottom - spectrumHeight - 5f * density * contentScale;
        }
        float areaTop = pad;
        float availableHeight = Math.max(1f, areaBottom - areaTop);

        // 行表：字号先行，行距由相邻两行的字号推出来，主行再用同一份行距排齐（issue #53/#64）。
        int rowCount = 0;
        if (showPlayerStatus) {
            classicRowKinds[rowCount] = CLASSIC_ROW_STATUS;
            classicRowOffsets[rowCount] = 0;
            classicRowSizes[rowCount] = 11f;
            classicRowScales[rowCount] = true;
            classicRowMinGaps[rowCount] = 0f;
            classicRowUniform[rowCount] = true;
            rowCount++;
        }
        int titleIndex = rowCount;
        classicRowKinds[titleIndex] = CLASSIC_ROW_TITLE;
        classicRowOffsets[titleIndex] = 0;
        classicRowSizes[titleIndex] = 15f * titleScale;
        // 歌名走「歌名与歌手字号」，不再乘「字号」，所以不参与整体缩放（issue #38）。
        classicRowScales[titleIndex] = false;
        classicRowMinGaps[titleIndex] = titleIndex > 0 ? ClassicLayoutMath.MIN_LYRIC_GAP_DP : 0f;
        classicRowUniform[titleIndex] = true;
        rowCount++;
        for (int offset = -precedingRows; offset <= followingRows; offset++) {
            boolean current = offset == 0;
            classicRowKinds[rowCount] = CLASSIC_ROW_LYRIC;
            classicRowOffsets[rowCount] = offset;
            classicRowSizes[rowCount] = current
                    ? 22f
                    : 22f * (offset < 0 ? previousLyricScale : nextLyricScale);
            classicRowScales[rowCount] = true;
            // 歌名下面那一行沿用历史下限（上一句 27dp / 本句 24dp），歌词行之间 24dp。
            classicRowMinGaps[rowCount] = rowCount == 0 ? 0f
                    : (rowCount == titleIndex + 1 && offset < 0
                            ? ClassicLayoutMath.MIN_TITLE_GAP_DP
                            : ClassicLayoutMath.MIN_LYRIC_GAP_DP);
            classicRowUniform[rowCount] = true;
            rowCount++;
            if (current && hasTranslation) {
                classicRowKinds[rowCount] = CLASSIC_ROW_TRANSLATION;
                classicRowOffsets[rowCount] = 0;
                classicRowSizes[rowCount] = 12f;
                classicRowScales[rowCount] = true;
                classicRowMinGaps[rowCount] = ClassicLayoutMath.MIN_TRANSLATION_GAP_DP;
                // 翻译贴着本句走，不参与统一行距，否则多行时它会离本句很远。
                classicRowUniform[rowCount] = false;
                rowCount++;
            }
        }
        ClassicLayoutMath.pack(classicBlock, rowCount, classicRowSizes, classicRowScales,
                classicRowMinGaps, classicRowUniform, densityUnit, textScale, availableHeight);
        float classicTextScale = classicBlock.scale;
        float blockTop;
        if (contentAlign.isEmpty()) {
            // 歌名与各歌词行平均分配行距，整块在面板里居中：面板调高只是上下留白更多，行距不再被
            // 面板高度拉开，歌名也不会再和相邻歌词挤在一起（issue #53）。
            blockTop = areaTop + Math.max(0f, availableHeight - classicBlock.heightPx) * 0.5f;
        } else {
            // 「内容垂直对齐」：整块歌词在面板里靠上 / 居中 / 靠下（issue #41）。
            blockTop = ClassicLayoutMath.alignedRowShift(contentAlign, 0f, classicBlock.heightPx,
                    areaTop, areaBottom);
        }
        float statusSize = 11f * densityUnit * classicTextScale;
        float titleSize = 15f * densityUnit * titleScale;
        float currentSize = 22f * densityUnit * classicTextScale;
        float translationSize = 12f * densityUnit * classicTextScale;
        String lyricSource = snapshot.lyricSourceName.isEmpty()
                ? "" : "  ·  歌词/" + snapshot.lyricSourceName;
        String status = snapshot.active
                ? snapshot.sourceName + (snapshot.playing ? "  ·  播放中" : "  ·  已暂停")
                + lyricSource : "歌词伴侣  ·  等待音乐";
        float basicScrollShift = basicLyricEntryShift(snapshot.lyrics.lineStartMs,
                32f * density * unit);
        // Every lyric row of this style shares one column and one alignment, so the current line,
        // its translation and the neighbouring lines never drift apart.
        Paint.Align lyricAlign = lyricTextAlign(Paint.Align.CENTER);
        float lyricAnchor = lyricAnchorX(pad, usableWidth, lyricAlign);
        int classicTextSave = canvas.save();
        canvas.clipRect(0f, 0f, width, Math.max(1f, areaBottom));
        for (int index = 0; index < rowCount; index++) {
            float baseline = blockTop + classicBlock.baselinesPx[index]
                    + previewShift + basicScrollShift;
            int kind = classicRowKinds[index];
            if (kind == CLASSIC_ROW_STATUS) {
                // Panel metadata: its own colours, and never an outline.
                drawingMetadata = true;
                drawCentered(canvas, status, baseline, statusSize,
                        snapshot.playing ? 0xFF6EE7F2 : 0xFF8392A8, usableWidth, Typeface.BOLD);
                drawingMetadata = false;
                continue;
            }
            if (kind == CLASSIC_ROW_TITLE) {
                drawingMetadata = true;
                drawCentered(canvas, snapshot.active ? snapshot.title : "打开音乐播放器并开始播放",
                        baseline, titleSize, 0xFFF6F9FF, usableWidth, Typeface.BOLD);
                drawingMetadata = false;
                continue;
            }
            if (kind == CLASSIC_ROW_TRANSLATION) {
                drawAlignedLyric(canvas, translation, pad, usableWidth, baseline,
                        translationSize, currentLyricColor(0xFFB8C5D8), Typeface.NORMAL);
                continue;
            }
            int offset = classicRowOffsets[index];
            if (offset == 0) {
                if (snapshot.lyrics.interlude) {
                    float dotRadius = currentSize * 0.35f;
                    float dotWidth = interludeDotsWidth(dotRadius);
                    drawInterludeDots(canvas, snapshot, width / 2f - dotWidth / 2f,
                            baseline - dotRadius, dotRadius, currentLyricColor(0xFFFFCA66));
                } else {
                    drawKaraoke(canvas, snapshot, currentText(snapshot), lyricAnchor, baseline,
                            currentSize, usableWidth, lyricAlign,
                            inactiveLyricColor(0xFFB1BCCB), currentLyricColor(0xFFFFCA66));
                }
                continue;
            }
            LrcTimeline.NearbyLine line = classicNearbyLine(snapshot, offset);
            String text = line != null ? line.text : classicFallbackText(snapshot, offset);
            if (text.isEmpty()) continue;
            float size = 22f * densityUnit * classicTextScale
                    * (offset < 0 ? previousLyricScale : nextLyricScale);
            int color = classicAdjacentColor(offset);
            if (offset < 0) {
                drawAlignedDissolving(canvas, text, pad, usableWidth, baseline, size, color,
                        Typeface.NORMAL, line == null || line.timeMs <= 0L
                                ? LyricDissolveEffect.UNKNOWN_LINE : line.timeMs);
            } else {
                drawAlignedLyric(canvas, text, pad, usableWidth, baseline, size, color,
                        Typeface.NORMAL);
            }
        }
        canvas.restoreToCount(classicTextSave);
        drawProgress(canvas, pad, height - 17f * density * contentScale,
                width - pad, 3f * density * contentScale,
                snapshot, 0x354B5F78, 0xFFFFCA66);
    }

    /** Native rendering of Refined Now Playing's 45% / 45% two-column layout. */
    private void drawRefined(Canvas canvas, MusicSnapshot snapshot, float density) {
        if (fullscreen) {
            drawRefinedFullscreen(canvas, snapshot, density);
            return;
        }
        float width = getWidth();
        float height = getHeight();
        updatePalette(snapshot.albumArt);
        boolean light = refinedUsesLightColors();
        int accent = refinedAccentColor();
        int primaryText = compactTextOnly ? 0xFFF5F8FF
                : lyricUsesLightColors() ? mix(accent, Color.BLACK, 0.72f)
                : mix(accent, Color.WHITE, 0.78f);
        int secondaryText = withAlpha(primaryText, 150);
        drawRefinedBackground(canvas, snapshot.albumArt, light, accent, snapshot.playing);
        int contentSave = canvas.save();
        clipPath.reset();
        float panelRadius = Math.min(width, height) * panelCornerRadiusRatio(0.075f);
        clipPath.addRoundRect(panelRect, panelRadius, panelRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        float contentScale = canvasAreaScale(560f, 300f, density);
        float pad = contentPad(Math.max(12f * density * contentScale, width * 0.035f));
        boolean lyricsOnly = "lyrics".equals(refinedDisplayMode);
        boolean coverOnly = "cover".equals(refinedDisplayMode);
        float leftColumnWidth = lyricsOnly ? 0f : width * 0.45f;
        float lyricLeft = lyricsOnly ? pad : Math.max(width * 0.50f, leftColumnWidth + pad);
        float lyricWidth = Math.max(1f, width - lyricLeft - pad);

        if (!lyricsOnly) {
            drawRefinedSongInfo(canvas, snapshot, density, leftColumnWidth, pad,
                    primaryText, secondaryText, accent, contentScale);
        }
        if (!coverOnly) {
            drawRefinedLyrics(canvas, snapshot, density, lyricLeft, lyricWidth,
                    lyricColor(primaryText), lyricColor(secondaryText), contentScale);
        }
        float progressY = refinedProgressBottom ? height - 2f * density * contentScale
                : height - 10f * density * contentScale;
        drawProgress(canvas, pad, progressY, width - pad, 2f * density * contentScale,
                snapshot, withAlpha(primaryText, 48), withAlpha(primaryText, 225));
        canvas.restoreToCount(contentSave);
    }

    /**
     * Pure and PiP are both minimal text styles; they share one size anchor so the same
     * text-scale setting renders the same current-line glyph size in either style. PiP's
     * layout offsets scale with its own area factor, so the larger anchor does not overflow.
     */
    private static final float MINIMAL_STYLE_LYRIC_DP = 29f;
    private static final float MINIMAL_STYLE_REFERENCE_WIDTH_DP = 430f;
    private static final float MINIMAL_STYLE_REFERENCE_HEIGHT_DP = 190f;

    private float minimalStyleLyricSize(float density) {
        return Math.max(16f * density, MINIMAL_STYLE_LYRIC_DP * density * textScale
                * canvasAreaScale(MINIMAL_STYLE_REFERENCE_WIDTH_DP,
                MINIMAL_STYLE_REFERENCE_HEIGHT_DP, density));
    }

    /** Text-only desktop style: no cover, player metadata, progress or transport controls. */
    /**
     * 灵动岛样式（issue #54）：一颗胶囊，左边圆形封面、右边歌名（可选）与单行当前歌词。
     *
     * <p>高度由 {@link IslandLayoutMath} 夹在 40–72dp 之间、角半径取高度一半（真胶囊）；矮胶囊里
     * 只显示歌词，够高才分两行放歌名。整块面板都可以拖动（{@code OverlayStyleInteraction}），
     * 与紧凑 / 纯净一致。
     */
    private void drawIsland(Canvas canvas, MusicSnapshot snapshot, float density) {
        float width = getWidth();
        float height = getHeight();
        float capsuleHeight = IslandLayoutMath.capsuleHeightPx(height, density);
        float radius = IslandLayoutMath.capsuleRadiusPx(capsuleHeight);
        float top = (height - capsuleHeight) * 0.5f;
        float side = IslandLayoutMath.sidePaddingPx(capsuleHeight);
        float coverSize = IslandLayoutMath.coverSizePx(capsuleHeight);
        float gap = IslandLayoutMath.coverGapPx(capsuleHeight);
        float centerY = top + capsuleHeight * 0.5f;

        // 胶囊底：默认半透明深色玻璃；用户设了背景色 / 不透明度时按设置来。
        int configured = configuredBackgroundColor();
        int capsuleColor = configured != 0 ? withAlpha(configured, Math.round(opacity * 2.55f))
                : (snapshot.active ? 0xD91A2130 : 0xA61A2130);
        if (capsuleColor != 0) {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(capsuleColor);
            workRect.set(0f, top, width, top + capsuleHeight);
            canvas.drawRoundRect(workRect, radius, radius, paint);
        }

        float coverLeft = side;
        float coverTop = centerY - coverSize * 0.5f;
        coverRect.set(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize);
        drawCoverRotated(canvas, snapshot.albumArt, coverRect, coverSize * 0.5f,
                mix(refinedAccentColor(), Color.DKGRAY, 0.55f));

        float textLeft = coverLeft + coverSize + gap;
        float textWidth = IslandLayoutMath.textWidthPx(width, capsuleHeight);
        boolean showTitle = IslandLayoutMath.showsTitleRow(capsuleHeight, textWidth, density)
                && snapshot.active && !snapshot.title.isEmpty();
        float lyricSize = IslandLayoutMath.lyricSizePx(capsuleHeight) * textScale;
        float titleSize = IslandLayoutMath.titleSizePx(capsuleHeight, lyricSize) * titleScale;
        drawingMetadata = true;
        if (showTitle) {
            drawRefinedText(canvas, snapshot.title, textLeft, centerY - lyricSize * 0.30f,
                    titleSize, 0xFFF5F8FF, textWidth, Paint.Align.LEFT, Typeface.NORMAL, 235);
            drawRefinedText(canvas, snapshot.artist, textLeft, centerY + lyricSize * 0.78f,
                    MetadataTypeScaleMath.artistSize(titleSize, 0.72f, artistScalePercent),
                    0xFFC3CEDD, textWidth, Paint.Align.LEFT, Typeface.NORMAL, 175);
        }
        drawingMetadata = false;
        float lyricBaseline = showTitle ? centerY + lyricSize * 0.42f : centerY + lyricSize * 0.34f;
        if (snapshot.lyrics.interlude) {
            float dotRadius = lyricSize * 0.22f;
            drawInterludeDots(canvas, snapshot, textLeft, lyricBaseline - lyricSize * 0.85f,
                    dotRadius, currentLyricColor(0xFFFFFFFF));
        } else {
            // 单行：跑马灯负责长句（与紧凑 / 纯净同一条路径，顶部条除外）。
            drawCompactMarqueeKaraoke(canvas, snapshot, currentText(snapshot), textLeft,
                    lyricBaseline, lyricSize, textWidth, density,
                    inactiveLyricColor(0x99FFFFFF), currentLyricColor(0xFFFFFFFF),
                    capsuleHeight);
        }
    }

    private void drawPure(Canvas canvas, MusicSnapshot snapshot, float density) {
        int background = configuredBackgroundColor();
        if (background != 0 && opacity > 0) {
            paint.setColor(withAlpha(background, Math.round(opacity * 2.55f)));
            float radius = Math.min(getWidth(), getHeight()) * panelCornerRadiusRatio(0.10f);
            canvas.drawRoundRect(panelRect, radius, radius, paint);
        }
        float width = getWidth();
        float height = getHeight();
        float requestedSize = minimalStyleLyricSize(density);
        List<LrcTimeline.NearbyLine> nearby = snapshot.lyrics.nearbyLines;
        if (nearby.isEmpty()) {
            List<LrcTimeline.NearbyLine> fallback = new ArrayList<>();
            if (!snapshot.lyrics.previousLyric.isEmpty()) {
                fallback.add(new LrcTimeline.NearbyLine(snapshot.lyrics.previousLyric, "", -1,
                        0L, 0L, false));
            }
            fallback.add(new LrcTimeline.NearbyLine(snapshot.lyrics.lyric,
                    secondaryLyric(snapshot), 0,
                    snapshot.lyrics.lineStartMs, snapshot.lyrics.lineDurationMs,
                    snapshot.lyrics.interlude));
            if (!snapshot.lyrics.nextLyric.isEmpty()) {
                fallback.add(new LrcTimeline.NearbyLine(snapshot.lyrics.nextLyric, "", 1,
                        0L, 0L, false));
            }
            nearby = fallback;
        }
        int requestedCount = Math.max(1, Math.min(7, lyricLineCount));
        int currentIndex = 0;
        for (int index = 0; index < nearby.size(); index++) {
            if (nearby.get(index).offset == 0) { currentIndex = index; break; }
        }
        // 行位固定：窗口永远按「歌词显示行数」摆同样多的行位，本句始终落在同一行上，歌曲开头与
        // 结尾不足的行位留空。以前窗口会随着已有行数缩水，第一句因此被顶到窗口最上面那一行，
        // 正好压住车机的车道信息（issue #31）。
        int count = requestedCount;
        int firstLineIndex = PureLyricLayout.windowStart(currentIndex, count);
        int translatedLineCount = 0;
        boolean currentTranslated = false;
        if (pureShowTranslation) {
            for (int slot = 0; slot < count; slot++) {
                LrcTimeline.NearbyLine line = pureWindowLine(nearby, firstLineIndex + slot);
                if (line == null) continue;
                boolean translated = PureLyricLayout.hasDistinctTranslation(
                        line.text, secondaryLyric(line));
                if (translated) translatedLineCount++;
                if (line.offset == 0) currentTranslated = translated;
            }
        }
        float availableHeight = Math.max(1f, height - 16f * density);
        float size = PureLyricLayout.constrainedCurrentSize(requestedSize, availableHeight,
                count, translatedLineCount, currentTranslated, nextLyricScale);
        size = Math.max(1f, size);
        float secondarySize = size * nextLyricScale;
        float gap = size * PureLyricLayout.entryGapRatio(count);
        float groupHeight = size + (count - 1) * secondarySize + (count - 1) * gap;
        if (translatedLineCount > 0) {
            groupHeight += (currentTranslated ? size : 0f)
                    * (PureLyricLayout.TRANSLATION_SCALE
                    + PureLyricLayout.TRANSLATION_GAP_RATIO);
            groupHeight += (translatedLineCount - (currentTranslated ? 1 : 0))
                    * secondarySize * (PureLyricLayout.TRANSLATION_SCALE
                    + PureLyricLayout.TRANSLATION_GAP_RATIO);
        }
        // 「内容垂直对齐」：留空沿用原本的居中，选顶部时整组歌词真正贴到面板上方（issue #41）。
        float top = contentAlign.isEmpty()
                ? Math.max(4f * density, (height - groupHeight) * 0.5f)
                : ClassicLayoutMath.alignedRowShift(contentAlign, 0f, groupHeight,
                        4f * density, Math.max(4f * density, height - 4f * density));
        float maxWidth = Math.max(1f, width - 24f * density);
        Paint.Align lyricAlign = lyricTextAlign(Paint.Align.CENTER);
        // maxWidth is symmetric, so the centred rows and the left-aligned ones share one column.
        float lyricLeft = (width - maxWidth) * 0.5f;
        int save = canvas.save();
        canvas.clipRect(0f, 0f, width, height);
        boolean drewCurrent = false;
        float lineTop = top;
        for (int slot = 0; slot < count; slot++) {
            LrcTimeline.NearbyLine line = pureWindowLine(nearby, firstLineIndex + slot);
            boolean current = line != null && line.offset == 0;
            float lineSize = current ? size : secondarySize;
            float baseline = lineTop + lineSize;
            boolean showTranslation = line != null && pureShowTranslation
                    && PureLyricLayout.hasDistinctTranslation(line.text, secondaryLyric(line));
            if (current) {
                drewCurrent = true;
                if (line.interlude || snapshot.lyrics.interlude) {
                    compactMarqueeActive = false;
                    compactMarqueeText = "";
                    compactMarqueeElapsedMs = 0L;
                    float radius = size * 0.22f;
                    drawInterludeDots(canvas, snapshot,
                            lyricAlign == Paint.Align.LEFT ? lyricLeft
                                    : (width - interludeDotsWidth(radius)) * 0.5f,
                            baseline - size * 0.72f, radius, lyricColor(0xFFFFFFFF));
                } else {
                    drawCompactMarqueeKaraoke(canvas, snapshot, currentText(snapshot),
                            lyricLeft, baseline, size, maxWidth, density,
                            inactiveLyricColor(0x99FFFFFF), currentLyricColor(0xFFFFFFFF),
                            Math.max(1f, height - 16f * density));
                }
            } else if (line != null) {
                int distance = Math.min(3, Math.abs(line.offset));
                int alpha = Math.max(72, 184 - distance * 30);
                int lineColor = adjacentLyricColor(inactiveLyricColor(withAlpha(0xFFFFFFFF, alpha)),
                        line.offset);
                // 所有已经唱过的行都要走消散路径，不能只认紧邻的那一句：消散完的行上移一行后
                // 会被当成普通行整句重画回来（issue #40），多行歌词时更早的行也会生硬消失
                // （issue #29）。
                if (line.offset < 0) {
                    drawAlignedDissolving(canvas, line.text, lyricLeft, maxWidth, baseline,
                            secondarySize, lineColor, Typeface.NORMAL, line.timeMs);
                } else {
                    drawAlignedLyric(canvas, line.text, lyricLeft, maxWidth, baseline, secondarySize,
                            lineColor, Typeface.NORMAL);
                }
            }
            float lineBlockHeight = lineSize;
            if (showTranslation) {
                float translationSize = lineSize * PureLyricLayout.TRANSLATION_SCALE;
                float translationBaseline = baseline
                        + lineSize * PureLyricLayout.TRANSLATION_GAP_RATIO
                        + translationSize;
                int translationAlpha = current ? 190
                        : Math.max(56, 148 - Math.min(3, Math.abs(line.offset)) * 24);
                drawAlignedLyric(canvas, secondaryLyric(line), lyricLeft, maxWidth, translationBaseline,
                        translationSize,
                        adjacentLyricColor(lyricColor(withAlpha(0xFFFFFFFF, translationAlpha)),
                                line.offset),
                        Typeface.NORMAL);
                lineBlockHeight += lineSize * PureLyricLayout.TRANSLATION_GAP_RATIO
                        + translationSize;
            }
            lineTop += lineBlockHeight + (slot + 1 < count ? gap : 0f);
        }
        if (!drewCurrent) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
        }
        canvas.restoreToCount(save);
    }

    /**
     * The line that belongs in one window slot, or {@code null} for a slot before the first line
     * or past the last one. Empty slots keep every row where it is (issue #31).
     */
    private static LrcTimeline.NearbyLine pureWindowLine(List<LrcTimeline.NearbyLine> nearby,
                                                         int index) {
        return index < 0 || index >= nearby.size() ? null : nearby.get(index);
    }

    /** Source-derived fullscreen layout from Refined Now Playing's styles.scss. */
    private void drawRefinedFullscreen(Canvas canvas, MusicSnapshot snapshot, float density) {
        float width = getWidth();
        float height = getHeight();
        updatePalette(snapshot.albumArt);
        boolean light = refinedUsesLightColors();
        int accent = refinedAccentColor();
        int primaryText = lyricUsesLightColors() ? mix(accent, Color.BLACK, 0.72f)
                : mix(accent, Color.WHITE, 0.78f);
        int secondaryText = withAlpha(primaryText, 150);
        drawRefinedBackground(canvas, snapshot.albumArt, light, accent, snapshot.playing);

        float viewportGutter = Math.max(50f * density, width * 0.05f);
        float contentWidth = Math.min(Math.max(width * 0.80f, 1500f * density),
                width - viewportGutter);
        contentWidth = Math.max(width * 0.72f, Math.min(width, contentWidth));
        float contentLeft = (width - contentWidth) * 0.5f;
        float bottom = clampRange(height * 0.05f, 30f * density, 60f * density);
        boolean lyricsOnly = "lyrics".equals(refinedDisplayMode);
        boolean coverOnly = "cover".equals(refinedDisplayMode);
        float infoWidth = contentWidth * 0.45f;

        if (!lyricsOnly) {
            drawRefinedFullscreenSongInfo(canvas, snapshot, density, contentLeft,
                    infoWidth, bottom, primaryText, secondaryText, accent);
        }
        if (!coverOnly) {
            float lyricLeft = lyricsOnly ? contentLeft + 30f * density
                    : Math.min(width * 0.50f, contentLeft + contentWidth * 0.60f);
            float lyricWidth = lyricsOnly ? contentWidth - 60f * density
                    : contentWidth * 0.45f - 10f * density;
            lyricWidth = Math.max(1f, Math.min(width - lyricLeft - 20f * density, lyricWidth));
            float lyricBottom = height - bottom - 10f * density;
            float lyricHeight = Math.max(1f,
                    height - 72f * density - 120f * density);
            float lyricTop = Math.max(0f, lyricBottom - lyricHeight);
            int lyricSave = canvas.save();
            canvas.clipRect(lyricLeft - 30f * density, lyricTop,
                    lyricLeft + lyricWidth + 30f * density, lyricBottom);
            float currentY = lyricTop + lyricHeight * (refinedCurrentAlign / 100f);
            drawRefinedLyrics(canvas, snapshot, density, lyricLeft, lyricWidth,
                    lyricColor(primaryText), lyricColor(secondaryText), 1.25f, currentY);
            canvas.restoreToCount(lyricSave);
        }
        drawProgress(canvas, 0f, height - 2f * density, width, 2f * density,
                snapshot, withAlpha(primaryText, 48), withAlpha(primaryText, 225));
    }

    private void drawRefinedFullscreenSongInfo(Canvas canvas, MusicSnapshot snapshot,
                                               float density, float contentLeft,
                                               float infoWidth, float bottom,
                                               int primaryText, int secondaryText, int accent) {
        float height = getHeight();
        float coverSize = clampRange(getWidth() * 0.20f,
                200f * density, 500f * density) * coverScale;
        coverSize = Math.min(coverSize, Math.max(1f, infoWidth - 70f * density));
        float titleSize = clampRange(height * 0.05f,
                45f * density, 600f * density) * titleScale;
        float metaSize = 16f * density * titleScale;
        // 歌手字号可单独调（issue #63）：Refined 全屏的歌名按面板高度算，所以这里只有「用户调过」
        // 时才按百分比走，没调过仍是写死的 16dp（观感不变）。
        float artistSize = artistScalePercent < 0 ? metaSize
                : MetadataTypeScaleMath.artistSize(titleSize, 1f, artistScalePercent);
        float metaUnit = Math.max(metaSize, artistSize);
        float groupHeight = coverSize + 18f * density + titleSize + metaUnit * 3.0f;
        float top = Math.max(24f * density, height - bottom - groupHeight);
        float left = contentLeft + Math.min(50f * density,
                Math.max(0f, infoWidth - coverSize));
        coverRect.set(left, top, left + coverSize, top + coverSize);
        float radius = refinedRectangleCover ? Math.max(8f * density, coverSize * 0.035f)
                : coverSize * 0.5f;
        if (refinedCoverShadow && snapshot.albumArt != null
                && !snapshot.albumArt.isRecycled()) {
            shadowRect.set(coverRect.left - coverSize * 0.04f,
                    coverRect.top + coverSize * 0.025f,
                    coverRect.right + coverSize * 0.04f,
                    coverRect.bottom + coverSize * 0.10f);
            int save = canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(shadowRect, radius, radius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            drawBitmapCrop(canvas, blurredPreview(snapshot.albumArt), shadowRect, 135);
            canvas.restoreToCount(save);
        }
        drawCoverRotated(canvas, snapshot.albumArt, coverRect, radius,
                mix(accent, Color.DKGRAY, 0.55f));

        float textLeft = contentLeft;
        float textWidth = Math.max(1f, infoWidth);
        float y = coverRect.bottom + 18f * density + titleSize;
        drawingMetadata = true;
        drawRefinedText(canvas, snapshot.active ? snapshot.title : "等待音乐",
                textLeft, y, titleSize, primaryText, textWidth,
                Paint.Align.LEFT, Typeface.NORMAL, 255);
        y += metaUnit * 1.8f;
        drawRefinedText(canvas, snapshot.artist, textLeft, y, artistSize,
                secondaryText, textWidth, Paint.Align.LEFT, Typeface.NORMAL, 205);
        y += metaUnit * 1.55f;
        if (showPlayerStatus) {
            drawRefinedText(canvas, snapshot.sourceName + sourceSuffix(snapshot), textLeft, y,
                    metaSize, secondaryText, textWidth, Paint.Align.LEFT, Typeface.NORMAL, 145);
        }
        drawingMetadata = false;
    }

    /** Immersive native interpretation of Apple Music-like Lyrics as a floating window. */
    private void drawAmll(Canvas canvas, MusicSnapshot snapshot, float density) {
        if (fullscreen) {
            drawAmllFullscreen(canvas, snapshot, density);
            return;
        }
        float width = getWidth();
        float height = getHeight();
        updatePalette(snapshot.albumArt);
        drawAmllBackground(canvas, snapshot.albumArt, snapshot.playing);

        int contentSave = canvas.save();
        float panelRadius = Math.min(width, height) * panelCornerRadiusRatio(0.075f);
        clipPath.reset();
        clipPath.addRoundRect(panelRect, panelRadius, panelRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        float contentScale = canvasAreaScale(620f, 350f, density);
        float pad = contentPad(Math.max(12f * density * contentScale, width * 0.028f));
        float leftWidth = width * 0.45f;
        float lyricLeft = leftWidth + Math.max(6f * density * contentScale,
                width * 0.015f);
        float lyricWidth = Math.max(1f, width - lyricLeft - pad);
        float currentY = height * 0.34f;
        drawAmllSongInfo(canvas, snapshot, density, leftWidth, pad, contentScale);
        drawAmllLyrics(canvas, snapshot, density, lyricLeft, lyricWidth, currentY,
                height - (secondary ? 10f : 42f) * density * contentScale, contentScale);

        drawProgress(canvas, pad, height - 3f * density * contentScale, leftWidth - pad,
                1.5f * density * contentScale, snapshot, 0x28FFFFFF, 0xD9FFFFFF);
        canvas.restoreToCount(contentSave);
    }

    /** Direct mapping of react-full's auto, horizontal and vertical layout source. */
    private void drawAmllFullscreen(Canvas canvas, MusicSnapshot snapshot, float density) {
        float width = getWidth();
        float height = getHeight();
        updatePalette(snapshot.albumArt);
        drawAmllBackground(canvas, snapshot.albumArt, snapshot.playing);
        boolean vertical = width < height;
        // 无封面且选了「隐藏封面区域」：AMLL 也把封面那块收掉，文字与歌词往上顶（issue #50）。
        boolean hideCover = coverAreaHidden();
        if (vertical) {
            float side = width <= 480f * density ? 20f * density : 3f * 16f * density;
            float coverSize = hideCover ? 0f : (width <= 480f * density ? 4.5f : 6f)
                    * 16f * density * coverScale;
            coverSize = Math.min(coverSize, width * 0.24f);
            float coverTop = 60f * density;
            coverRect.set(side, coverTop, side + coverSize, coverTop + coverSize);
            if (!hideCover) {
                drawCoverWithAmllShadow(canvas, snapshot, density, coverRect,
                        Math.max(4f * density, coverSize * 0.02f));
            }
            float infoSize = Math.max(height * 0.02f, 16f * density) * titleScale;
            float infoLeft = coverRect.right + 16f * density;
            float infoWidth = Math.max(1f, width - infoLeft - side);
            drawAmllSingleLine(canvas, snapshot.active ? snapshot.title : "等待播放",
                    infoLeft, coverRect.centerY(), infoSize, Color.WHITE,
                    infoWidth, Typeface.BOLD, 230);
            drawAmllSingleLine(canvas, snapshot.artist,
                    infoLeft, coverRect.centerY() + infoSize * 1.25f,
                    MetadataTypeScaleMath.artistSize(infoSize, 0.78f, artistScalePercent),
                    Color.WHITE, infoWidth, Typeface.NORMAL, 115);

            float lyricTop = coverRect.bottom + 18f * density;
            float lyricBottom = height;
            float lyricFont = Math.max(width * 0.08f, 12f * density) * textScale;
            int save = canvas.save();
            canvas.clipRect(0f, lyricTop, width, lyricBottom);
            drawAmllLyricsAtSize(canvas, snapshot, density, side,
                    Math.max(1f, width - side * 2f),
                    Math.max(lyricTop + lyricFont, height * 0.34f), lyricBottom,
                    lyricFont);
            canvas.restoreToCount(save);
        } else {
            float leftWidth = width * 0.45f;
            float lyricLeft = leftWidth;
            float lyricRightPadding = (width <= 1600f * density
                    || height <= 1000f * density) ? width * 0.08f : width * 0.15f;
            float lyricWidth = Math.max(1f, width - lyricLeft - lyricRightPadding);
            float coverSize = hideCover ? 0f
                    : Math.min(height * 0.45f, width * 0.38f) * coverScale;
            float coverTop = Math.max(30f * density, height * 0.075f);
            float coverLeft = hideCover ? leftWidth * 0.08f : (leftWidth - coverSize) * 0.5f;
            coverRect.set(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize);
            if (!hideCover) {
                drawCoverWithAmllShadow(canvas, snapshot, density, coverRect,
                        Math.max(5f * density, coverSize * 0.02f));
            }
            float infoSize = Math.max(height * 0.02f, 16f * density) * titleScale;
            float infoWidth = hideCover ? Math.max(1f, leftWidth - coverLeft * 2f) : coverSize;
            drawAmllSingleLine(canvas, snapshot.active ? snapshot.title : "等待播放",
                    coverLeft, coverRect.bottom + infoSize * 1.55f, infoSize,
                    Color.WHITE, infoWidth, Typeface.BOLD, 230);
            drawAmllSingleLine(canvas, snapshot.artist, coverLeft,
                    coverRect.bottom + infoSize * 2.75f,
                    MetadataTypeScaleMath.artistSize(infoSize, 0.78f, artistScalePercent),
                    Color.WHITE, infoWidth, Typeface.NORMAL, 115);
            float progressY = coverRect.bottom + infoSize * 3.35f;
            drawProgress(canvas, coverLeft, progressY, coverLeft + infoWidth,
                    Math.max(2f, height * 0.004f), snapshot, 0x38FFFFFF, 0xD9FFFFFF);

            float lyricFont = Math.max(height * 0.05f, width * 0.025f) * textScale;
            int save = canvas.save();
            canvas.clipRect(lyricLeft, 0f, width, height);
            drawAmllLyricsAtSize(canvas, snapshot, density, lyricLeft, lyricWidth,
                    height * 0.34f, height, lyricFont);
            canvas.restoreToCount(save);
        }
    }

    private void drawCoverWithAmllShadow(Canvas canvas, MusicSnapshot snapshot, float density,
                                         RectF cover, float radius) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x4D000000);
        paint.setShadowLayer(Math.max(12f * density, cover.width() * 0.045f), 0f,
                Math.max(6f * density, cover.width() * 0.025f), 0x8C000000);
        canvas.drawRoundRect(cover, radius, radius, paint);
        paint.clearShadowLayer();
        drawCoverRotated(canvas, snapshot.albumArt, cover, radius, palette[0]);
    }

    private void drawAmllSongInfo(Canvas canvas, MusicSnapshot snapshot, float density,
                                  float columnWidth, float pad, float contentScale) {
        float height = getHeight();
        float top = Math.max(pad, height * 0.075f);
        float titleSize = 16f * density * contentScale * titleScale;
        // 无封面且选了「隐藏封面区域」：封面收掉，歌名 / 歌手从列顶往下排（issue #50）。
        boolean hideCover = coverAreaHidden();
        float coverSize = hideCover ? 0f : 158f * density * contentScale * coverScale;
        float left = (columnWidth - coverSize) * 0.5f;
        coverRect.set(left, top, left + coverSize, top + coverSize);
        // 「圆形封面」打开后 AMLL 也用正圆——碟片旋转只对圆形封面生效（issue #22）。
        float coverRadius = roundCover ? coverSize * 0.5f : 8f * density;
        if (!hideCover) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x4D000000);
            paint.setShadowLayer(18f * density * contentScale, 0f,
                    8f * density * contentScale, 0x8C000000);
            canvas.drawRoundRect(coverRect, coverRadius, coverRadius, paint);
            paint.clearShadowLayer();
            drawCoverRotated(canvas, snapshot.albumArt, coverRect, coverRadius, palette[0]);
        }

        float textLeft = hideCover ? pad : Math.max(pad, left);
        float textWidth = hideCover ? Math.max(1f, columnWidth - pad * 2f)
                : Math.min(columnWidth - textLeft - pad, coverSize);
        float y = coverRect.bottom + titleSize * 1.45f;
        drawAmllSingleLine(canvas, snapshot.active ? snapshot.title : "等待播放",
                textLeft, y, titleSize, 0xFFFFFFFF, textWidth,
                Typeface.BOLD, 230);
        y += titleSize * 1.25f;
        // 歌手字号可单独调（issue #63）；没调过时仍是写死的 72%。
        drawAmllSingleLine(canvas, snapshot.artist, textLeft, y,
                MetadataTypeScaleMath.artistSize(titleSize, 0.72f, artistScalePercent),
                0xFFFFFFFF, textWidth, Typeface.NORMAL, 115);
    }

    private void drawAmllLyrics(Canvas canvas, MusicSnapshot snapshot, float density,
                                float left, float width, float currentY, float bottom,
                                float contentScale) {
        float fontSize = 23f * density * contentScale * textScale;
        drawAmllLyricsAtSize(canvas, snapshot, density, left, width, currentY, bottom, fontSize);
    }

    private void drawAmllLyricsAtSize(Canvas canvas, MusicSnapshot snapshot, float density,
                                      float left, float width, float currentY, float bottom,
                                      float fontSize) {
        if (snapshot.lyrics.nearbyLines.isEmpty()) {
            drawAmllWrappedKaraoke(canvas, snapshot, currentText(snapshot), left,
                    currentY - fontSize + browseVisualOffsetPx, fontSize, width, 3,
                    currentLyricColor(0xFFFFFFFF));
            return;
        }

        List<LrcTimeline.NearbyLine> lines = snapshot.lyrics.nearbyLines;
        ensureRefinedLineCapacity(lines.size());
        int current = 0;
        float translationSize = fontSize * 0.50f;
        for (int i = 0; i < lines.size(); i++) {
            LrcTimeline.NearbyLine line = lines.get(i);
            if (line.offset == 0) current = i;
            if (line.interlude) {
                refinedLineHeights[i] = fontSize * 0.92f;
            } else {
                refinedLineHeights[i] = wrappedTextHeight(line.text, fontSize, width, 3);
                if (!secondaryLyric(line).isEmpty()) {
                    refinedLineHeights[i] += fontSize * 0.12f
                            + wrappedTextHeight(secondaryLyric(line), translationSize, width, 2);
                }
            }
        }
        float gap = fontSize * 0.42f;
        lastRefinedBrowseStepPx = Math.max(1f, refinedLineHeights[current] + gap);
        refinedLineTops[current] = currentY - Math.min(fontSize, refinedLineHeights[current] * 0.42f);
        for (int i = current + 1; i < lines.size(); i++) {
            refinedLineTops[i] = refinedLineTops[i - 1] + refinedLineHeights[i - 1] + gap;
        }
        for (int i = current - 1; i >= 0; i--) {
            refinedLineTops[i] = refinedLineTops[i + 1] - refinedLineHeights[i] - gap;
        }

        boolean previewing = manualPreviewActive();
        float scrollShift = animatedAmllScrollShift(snapshot.lyrics.lineStartMs,
                refinedLineHeights[current] + gap) + browseVisualOffsetPx;
        for (int i = 0; i < lines.size(); i++) {
            LrcTimeline.NearbyLine line = lines.get(i);
            int offset = line.offset;
            if (Math.abs(offset) > 3) continue;
            float top = refinedLineTops[i] + scrollShift;
            float centerY = top + refinedLineHeights[i] * 0.5f;
            float edgeFade = clamp(Math.min((centerY + fontSize) / Math.max(1f, currentY),
                    (bottom - centerY + fontSize) / Math.max(1f, bottom - currentY)) * 1.7f);
            float opacityValue = AmllStyleMotion.lineOpacity(offset, snapshot.playing, previewing)
                    * edgeFade;
            if (offset > 0) opacityValue *= nextLyricOpacity / 100f;
            else if (offset < 0) opacityValue *= previousLyricOpacity / 100f;
            if (opacityValue <= 0.01f || centerY < -fontSize || top > bottom + fontSize) continue;

            float scale = AmllStyleMotion.lineScale(offset);
            if (offset == 1) scale *= nextLyricScale;
            int save = canvas.save();
            canvas.scale(scale, scale, left, centerY);
            // AMLL 的远近模糊同样改走离屏位图：面板整体回到硬件渲染（issue #59 后续）。
            float blurRadius = AmllStyleMotion.lineBlur(offset, snapshot.playing, previewing) * density;
            int lineColor = withAlpha(inactiveLyricColor(0xFFFFFFFF), 255);
            if (line.interlude) {
                drawInterludeDots(canvas, snapshot, left, top + fontSize * 0.24f,
                        fontSize * 0.105f, withAlpha(lineColor, Math.round(opacityValue * 255f)));
            } else if (offset == 0) {
                drawAmllWrappedKaraoke(canvas, snapshot, currentText(snapshot), left, top,
                        fontSize, width, 3, currentLyricColor(0xFFFFFFFF));
            } else if (offset < 0) {
                // 已经唱过的每一行都走消散路径：消散完的行上移一行后不能整句复活（issue #40）。
                drawBlurredLine(canvas, line.text, left, top, fontSize, lineColor, opacityValue,
                        width, Typeface.BOLD, 3, blurRadius,
                        previousLyricDissolve.affects(line.timeMs), line.timeMs);
            } else {
                drawBlurredLine(canvas, line.text, left, top, fontSize, lineColor, opacityValue,
                        width, Typeface.BOLD, 3, blurRadius, false, line.timeMs);
            }
            if (!line.interlude && !secondaryLyric(line).isEmpty()) {
                float originalHeight = wrappedTextHeight(line.text, fontSize, width, 3);
                float translationAlpha = opacityValue * (offset == 0 ? 96f : 72f) / 255f;
                drawBlurredLine(canvas, secondaryLyric(line), left,
                        top + originalHeight + fontSize * 0.12f, translationSize,
                        withAlpha(lyricColor(0xFFFFFFFF), 255), translationAlpha, width,
                        Typeface.NORMAL, 2, blurRadius, false, line.timeMs);
            }
            paint.setMaskFilter(null);
            canvas.restoreToCount(save);
        }
    }

    private float animatedAmllScrollShift(long lineStartMs, float stepHeight) {
        if (lineStartMs < 0L || !smoothLyricScroll) return 0f;
        if (manualPreviewActive()) {
            lastAmllLineStartMs = lineStartMs;
            amllScrollAnimationStartedMs = 0L;
            amllScrollDirection = 0;
            return 0f;
        }
        if (lastAmllLineStartMs == Long.MIN_VALUE) {
            lastAmllLineStartMs = lineStartMs;
            return 0f;
        }
        if (lineStartMs != lastAmllLineStartMs) {
            amllScrollDirection = lineStartMs > lastAmllLineStartMs ? 1 : -1;
            lastAmllLineStartMs = lineStartMs;
            amllScrollAnimationStartedMs = SystemClock.elapsedRealtime();
        }
        long elapsed = SystemClock.elapsedRealtime() - amllScrollAnimationStartedMs;
        return amllScrollDirection * stepHeight
                * AmllStyleMotion.scrollRemainder(elapsed, 620L);
    }

    private void drawAmllBackground(Canvas canvas, Bitmap art, boolean playing) {
        int alphaLayer = saveLayerAlphaCompat(canvas, panelRect,
                fullscreen ? 255 : Math.round(clamp(opacity / 100f) * 255f));
        int save = canvas.save();
        float radius = fullscreen ? 0f : Math.min(getWidth(), getHeight()) * panelCornerRadiusRatio(0.075f);
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setAlpha(255);
        int configured = configuredBackgroundColor();
        paint.setColor(configured == 0 ? mix(palette[0], Color.BLACK, 0.76f) : configured);
        canvas.drawRect(panelRect, paint);
        if (configured == 0 && art != null && !art.isRecycled()) {
            drawBitmapCrop(canvas, blurredPreview(art), panelRect, 225,
                    backgroundBrightnessFilter());
        }

        float phase = playing ? (SystemClock.elapsedRealtime() % 100_000L) / 100_000f : 0.18f;
        float gradientRadius = Math.max(getWidth(), getHeight()) * 0.78f;
        for (int i = 0; configured == 0 && i < 3; i++) {
            double angle = phase * Math.PI * 2d + i * Math.PI * 2d / 3d;
            float cx = getWidth() * (0.5f + 0.42f * (float) Math.cos(angle));
            float cy = getHeight() * (0.5f + 0.36f * (float) Math.sin(angle));
            paint.setShader(new RadialGradient(cx, cy, gradientRadius,
                    withAlpha(palette[i], 92), withAlpha(palette[i], 0),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
        }
        paint.setShader(null);
        // 遮罩 0 就是「不压暗」（issue #58）；AMLL 原本恒 ≥105，现在只在遮罩 > 0 时才落那个下限。
        int dim = ArtworkBackgroundMath.amllMaskAlpha(backgroundDim, styleMaskMode);
        if (dim > 0) {
            paint.setColor(Color.argb(dim, 0, 0, 0));
            canvas.drawRect(panelRect, paint);
        }
        paint.setShader(new LinearGradient(0f, 0f, 0f, getHeight(),
                new int[]{0x33000000, 0x00000000, 0x70000000},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(panelRect, paint);
        paint.setShader(null);
        canvas.restoreToCount(save);
        canvas.restoreToCount(alphaLayer);
        paint.setAlpha(255);
    }

    private void drawAmllSingleLine(Canvas canvas, String value, float x, float baseline,
                                    float requestedSize, int color, float maxWidth,
                                    int style, int alpha) {
        if (value == null || value.isEmpty()) return;
        // Only the title and artist come through here, so it declares itself as metadata rather
        // than making every caller remember to.
        boolean wasMetadata = drawingMetadata;
        drawingMetadata = true;
        try {
            float size = fitSize(value, requestedSize, maxWidth, style);
            setTextPaintForValue(size, style, value);
            paint.setTextAlign(Paint.Align.LEFT);
            if (drawSplitSourceMetadata(canvas, value, x, baseline, maxWidth,
                    Paint.Align.LEFT, alpha)) return;
            color = resolveMetadataColor(value, color);
            paint.setColor(withAlpha(color, alpha));
            canvas.drawText(ellipsize(value.replace('\n', ' '), maxWidth), x, baseline, paint);
        } finally {
            drawingMetadata = wasMetadata;
        }
    }

    /** A small horizontal media strip: lyric-led, with cover artwork as a side anchor. */
    private void drawCompact(Canvas canvas, MusicSnapshot snapshot, float density) {
        float width = getWidth();
        float height = getHeight();
        updatePalette(snapshot.albumArt);
        boolean light = refinedUsesLightColors();
        int accent = refinedAccentColor();
        int primaryText = lyricUsesLightColors() ? mix(accent, Color.BLACK, 0.72f)
                : mix(accent, Color.WHITE, 0.78f);
        // A zero-opacity compact panel must not enter the background saveLayer path. Some
        // hardware renderers composite an empty alpha layer as an opaque black rectangle.
        if (compactTextOnly && !"transparent".equals(AppPreferences.topLyricBackground(getContext()))) {
            drawTopLyricBackground(canvas, snapshot, density);
        } else if (!compactTextOnly && opacity > 0) {
            drawRefinedBackground(canvas, snapshot.albumArt, light, accent, snapshot.playing);
        }

        int save = canvas.save();
        float radius = Math.min(width, height) * panelCornerRadiusRatio(0.18f);
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        float referenceArea = 320f * 104f * density * density;
        // The top strip exposes a width-only region control. Its font scale must stay tied to
        // the fixed strip height, otherwise narrowing the region makes lyrics shrink as well.
        float responsiveScale = compactTextOnly
                ? (float) Math.sqrt(Math.max(0.01f, height / (104f * density)))
                : (float) Math.sqrt(Math.max(0.01f, width * height / referenceArea));
        float pad = contentPad(Math.max(2f * density, 7f * density * responsiveScale));
        // 播放器没给封面又选了「隐藏封面区域」时，右侧那一整块留给歌词（issue #50）。
        boolean showCover = !compactTextOnly && AppPreferences.compactShowCover(getContext(), secondary)
                && !coverAreaHidden();
        boolean showBars = compactTextOnly ? AppPreferences.topLyricSpectrum(getContext())
                : AppPreferences.spectrumEnabled(getContext(), secondary);
        float barsHeight = showBars ? 22f * density * responsiveScale : 0f;
        float barsTop = showBars ? height - pad * 0.42f - barsHeight : height - pad;
        float coverLeft = width - pad;
        if (showCover) {
            float coverTop = pad * 0.62f;
            float titleSize = 10.5f * density * responsiveScale * titleScale;
            // 歌手字号可单独调（issue #63）：没调过时仍是写死的 8.5dp（比例与改动前一致）。
            float artistSize = MetadataTypeScaleMath.artistSize(titleSize,
                    8.5f / 10.5f, artistScalePercent);
            float metadataHeight = titleSize + artistSize + 5f * density;
            float naturalCoverSize = 58f * density * responsiveScale * coverScale;
            float availableCoverHeight = Math.max(1f, barsTop - coverTop);
            float coverSize = naturalCoverSize;
            boolean overlayMetadata = availableCoverHeight - coverSize < metadataHeight;
            coverLeft = width - pad - coverSize;
            coverRect.set(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize);
            // 紧凑样式的「圆形封面」（issue #22）：打开后封面是正圆，碟片旋转才会生效。
            float coverRadius = roundCover ? coverSize * 0.5f : 10f * density;
            drawCoverRotated(canvas, snapshot.albumArt, coverRect, coverRadius,
                    mix(accent, Color.DKGRAY, 0.55f));
            if (overlayMetadata) {
                paint.setShader(new LinearGradient(0f,
                        coverRect.bottom - metadataHeight * 1.65f, 0f, coverRect.bottom,
                        0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
                canvas.drawRoundRect(coverRect, coverRadius, coverRadius, paint);
                paint.setShader(null);
            }
            float titleY = overlayMetadata
                    ? coverRect.bottom - artistSize - 5f * density
                    : coverRect.bottom + 2f * density + titleSize;
            float artistY = overlayMetadata
                    ? coverRect.bottom - 3f * density
                    : titleY + artistSize + 3f * density;
            int metadataColor = overlayMetadata ? Color.WHITE : primaryText;
            drawingMetadata = true;
            drawRefinedText(canvas, snapshot.active ? snapshot.title : "等待音乐",
                    coverRect.centerX(), titleY, titleSize, metadataColor, coverRect.width(),
                    Paint.Align.CENTER, Typeface.BOLD, 255);
            drawRefinedText(canvas, snapshot.artist, coverRect.centerX(), artistY, artistSize,
                    withAlpha(metadataColor, 200), coverRect.width(), Paint.Align.CENTER,
                    Typeface.NORMAL, 175);
            drawingMetadata = false;
        }

        float lyricLeft = pad;
        float lyricWidth = Math.max(1f, coverLeft - lyricLeft - 4f * density);
        float lyricTop = showCover ? coverRect.top : pad;
        float lyricBottom = barsTop - 3f * density;
        float lyricStageHeight = Math.max(1f, lyricBottom - lyricTop);
        boolean preferTranslation = compactTextOnly
                ? AppPreferences.topLyricShowTranslation(getContext())
                : AppPreferences.refinedShowTranslation(getContext(), secondary);
        boolean showTranslation = preferTranslation && !secondaryLyric(snapshot).isEmpty();
        boolean showNextLine = (!preferTranslation || !showTranslation)
                && (compactTextOnly || AppPreferences.compactShowNextLine(getContext(), secondary))
                && !snapshot.lyrics.nextLyric.isEmpty();
        // Window area supplies the automatic scale; the user's lyric percentage remains an
        // independent multiplier. Only the available stage height limits the final result.
        float requestedLyricSize = refinedLyricFontSize * density * textScale
                * 1.50f * responsiveScale;
        float lyricSize = (showNextLine || showTranslation)
                ? requestedLyricSize * 0.78f : requestedLyricSize;
        float secondaryLineSize = lyricSize * nextLyricScale;
        setTextPaint(lyricSize, Typeface.BOLD);
        float originalAscent = paint.ascent();
        float originalDescent = paint.descent();
        setTextPaint(secondaryLineSize, Typeface.NORMAL);
        float secondaryAscent = paint.ascent();
        float secondaryDescent = paint.descent();
        float lineGap = Math.max(3f * density, lyricSize * 0.12f);
        boolean showSecondaryLine = showNextLine || showTranslation;
        float groupHeight = showSecondaryLine
                ? originalDescent - originalAscent + lineGap
                + secondaryDescent - secondaryAscent
                : originalDescent - originalAscent;
        float groupTop = lyricTop + Math.max(0f, (lyricStageHeight - groupHeight) * 0.5f);
        float baseline = groupTop - originalAscent;
        float secondaryBaseline = baseline + originalDescent + lineGap - secondaryAscent;
        // Center the current/secondary line pair in the stage to the left of the cover.
        String secondaryText = showSecondaryLine ? (showNextLine
                ? snapshot.lyrics.nextLyric : secondaryLyric(snapshot)) : "";
        // The current line, the line leaving it and the row under it all share this column and
        // alignment, so the dissolve eraser sweeps exactly over the glyphs it is replacing.
        Paint.Align lyricAlign = lyricTextAlign(Paint.Align.CENTER);
        float lyricAnchor = lyricAnchorX(lyricLeft, lyricWidth, lyricAlign);
        if (showNextLine) {
            drawRefinedText(canvas, secondaryText,
                    lyricAnchor, secondaryBaseline, secondaryLineSize,
                    lyricColor(primaryText), lyricWidth, lyricAlign, Typeface.NORMAL,
                    135);
        }
        // This strip has no previous-line row, so the line that just stopped being current
        // crumbles away where it was. The new line is only painted where the eraser has already
        // passed, which is what keeps it from appearing before the old one is gone.
        String leaving = snapshot.lyrics.previousLyric;
        float eraseFront = Float.NaN;
        if (!snapshot.lyrics.interlude && !leaving.isEmpty()) {
            eraseFront = previousLineEraseFront(leaving, lyricAnchor, baseline,
                    lyricSize, lyricWidth, Typeface.BOLD, lyricAlign);
        }
        if (!Float.isNaN(eraseFront)) {
            int reveal = canvas.save();
            canvas.clipRect(0f, 0f, eraseFront, height);
            drawCompactCurrentLine(canvas, snapshot, density, lyricLeft, lyricWidth, baseline,
                    lyricSize, secondaryBaseline, secondaryLineSize, secondaryText,
                    showTranslation, lyricColor(withAlpha(primaryText, 120)),
                    lyricColor(primaryText), lyricColor(withAlpha(primaryText, 165)));
            canvas.restoreToCount(reveal);
            drawDissolvingLine(canvas, leaving, lyricAnchor, baseline, lyricSize,
                    lyricColor(primaryText), lyricWidth, Typeface.BOLD, lyricAlign,
                    LyricDissolveEffect.UNKNOWN_LINE, true);
        } else {
            drawCompactCurrentLine(canvas, snapshot, density, lyricLeft, lyricWidth, baseline,
                    lyricSize, secondaryBaseline, secondaryLineSize, secondaryText,
                    showTranslation, lyricColor(withAlpha(primaryText, 120)),
                    lyricColor(primaryText), lyricColor(withAlpha(primaryText, 165)));
        }
        if (showBars) {
            drawCompactPlaybackBars(canvas, snapshot, lyricLeft, barsTop,
                    width - pad, barsHeight, lyricColor(primaryText),
                    AppPreferences.compactSpectrumColor(getContext(), secondary));
        }
        canvas.restoreToCount(save);
    }

    /**
     * Where the eraser currently is on the line that is leaving, or {@link Float#NaN} when that
     * line is not dissolving <em>or</em> has already come apart completely. Measured with the
     * exact size and layout the ghost is drawn with, which for the strip is the size the line
     * really had — never a shrunk one.
     *
     * <p>{@link LyricDissolveEffect#affects} stays true for a line that was consumed, and the
     * dissolve clock keeps running after the last glyph faded, so "the line is still dissolving"
     * is not the same question as "there is still something to hide the new line behind". When
     * nothing of the old line is left, the reveal clip has to go away: otherwise the clip ends at
     * the old line's right edge and eats the tail of the new line whenever the new one is longer
     * than the line before it.
     */
    private float previousLineEraseFront(String value, float anchorX, float y, float size,
                                         float maxWidth, int style, Paint.Align align) {
        if (!previousLyricDissolve.affects(LyricDissolveEffect.UNKNOWN_LINE)) return Float.NaN;
        setTextPaintForValue(size, style, value);
        paint.setTextAlign(Paint.Align.LEFT);
        String text = ellipsize(value.replace('\n', ' '), maxWidth);
        float left = align == Paint.Align.CENTER ? anchorX - paint.measureText(text) * .5f : anchorX;
        int charCount = text.codePointCount(0, text.length());
        if (!previousLyricDissolve.hasVisibleCharacter(LyricDissolveEffect.UNKNOWN_LINE,
                charCount)) {
            return Float.NaN;
        }
        float cursor = 0f;
        int charIndex = 0;
        for (int offset = 0; offset < text.length(); ) {
            int glyphChars = Character.charCount(text.codePointAt(offset));
            if (charIndex >= charCount) break;
            if (previousLyricDissolve.characterAlpha(LyricDissolveEffect.UNKNOWN_LINE, charIndex,
                    charCount) > LyricDissolveEffect.VISIBLE_ALPHA) {
                return left + cursor;
            }
            cursor += paint.measureText(text.substring(offset, offset + glyphChars));
            offset += glyphChars;
            charIndex++;
        }
        return Float.NaN;
    }

    /** The current row of the compact strip: the sung line plus whatever sits under it. */
    private void drawCompactCurrentLine(Canvas canvas, MusicSnapshot snapshot, float density,
                                        float lyricLeft, float lyricWidth, float baseline,
                                        float lyricSize, float secondaryBaseline,
                                        float secondaryLineSize, String secondaryText,
                                        boolean showTranslation, int baseColor, int activeColor,
                                        int translationColor) {
        if (snapshot.lyrics.interlude) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            float dotRadius = lyricSize * 0.20f;
            float dotWidth = interludeDotsWidth(dotRadius);
            drawInterludeDots(canvas, snapshot, lyricLeft + (lyricWidth - dotWidth) * 0.5f,
                    baseline - lyricSize * 0.72f, dotRadius, activeColor);
            return;
        }
        // 换行档要按可用高度决定最多几行；这里用面板高度减边距当近似（上限 3 行，且块居中在原行位置）。
        float marqueeOffset = drawCompactMarqueeKaraoke(canvas, snapshot,
                currentText(snapshot), lyricLeft, baseline, lyricSize, lyricWidth, density,
                baseColor, activeColor, Math.max(1f, getHeight() - 16f * density));
        if (showTranslation) {
            drawCompactFollowingTranslation(canvas, secondaryText, lyricLeft, secondaryBaseline,
                    secondaryLineSize, lyricWidth, marqueeOffset, translationColor);
        }
    }

    /** Draws real FFT bands when permitted, otherwise the user-selected virtual or static mode. */
    private void drawCompactPlaybackBars(Canvas canvas, MusicSnapshot snapshot, float left,
                                         float top, float right, float height, int lyricColor,
                                         int spectrumColor) {
        if (right <= left || height <= 0f) return;
        int color = spectrumColor == 0 ? lyricColor : spectrumColor;
        float width = right - left;
        int count = SpectrumMath.BAND_COUNT;
        boolean useRealSpectrum = AppPreferences.compactUseRealSpectrum(getContext(), secondary);
        AudioSpectrumSource.Frame frame = AudioSpectrumSource.latestFrame();
        boolean realSpectrumLive = useRealSpectrum && frame.live;
        boolean virtualSpectrum = !useRealSpectrum || !realSpectrumLive;
        long now = SystemClock.elapsedRealtime();
        float progress = snapshot.durationMs > 0L
                ? clamp(snapshot.positionMs / (float) snapshot.durationMs) : 0f;
        float[] targets = null;
        if (realSpectrumLive) {
            targets = frame.levels;
        } else if (virtualSpectrum) {
            long step = now / 180L;
            float stepProgress = (now % 180L) / 180f;
            stepProgress = stepProgress * stepProgress * (3f - 2f * stepProgress);
            for (int i = 0; i < count; i++) {
                float normalized = (i + 0.5f) / count;
                float pulse;
                if (snapshot.playing) {
                    float from = virtualPulse(i, step);
                    float to = virtualPulse(i, step + 1L);
                    pulse = from + (to - from) * stepProgress;
                } else {
                    pulse = 0.42f + 0.58f * (float) Math.abs(Math.sin(i * 0.73f));
                }
                float shape = 0.34f + 0.66f * (float) Math.sin(normalized * Math.PI);
                compactVirtualSpectrum[i] = pulse * shape;
            }
            targets = compactVirtualSpectrum;
        }
        if (targets == null) {
            compactSpectrumBars.reset();
        } else {
            compactSpectrumBars.update(targets, now);
        }
        for (int i = 0; i < count; i++) compactDisplayedSpectrum[i] = targets == null
                ? 0.10f : compactSpectrumBars.barAt(i);
        spectrumRect.set(left, top, right, top + height);
        SpectrumRenderer.draw(canvas, paint, spectrumRect, compactDisplayedSpectrum,
                AppPreferences.spectrumStyle(getContext(), secondary),
                AppPreferences.spectrumColorMode(getContext(), secondary), color,
                spectrumColor, palette);
        float density = getResources().getDisplayMetrics().density;
        float trackHeight = Math.max(1.5f * density, height * 0.035f);
        // Keep the continuous progress indicator against the card edge instead of sharing the
        // spectrum baseline, so its position stays visually stable while bars fluctuate.
        float trackBottom = getHeight() - 1f;
        float trackTop = trackBottom - trackHeight;
        paint.setColor(withAlpha(color, 42));
        workRect.set(left, trackTop, right, trackBottom);
        canvas.drawRoundRect(workRect, trackHeight * 0.5f, trackHeight * 0.5f, paint);
        paint.setColor(withAlpha(color, 235));
        workRect.set(left, trackTop, left + width * progress, trackBottom);
        canvas.drawRoundRect(workRect, trackHeight * 0.5f, trackHeight * 0.5f, paint);
        compactSpectrumAnimating = (realSpectrumLive
                && (snapshot.playing || compactSpectrumBars.hasVisibleBar()))
                || (virtualSpectrum && snapshot.playing);
    }

    /** Deterministic noise keeps virtual bars lively without frame-to-frame jitter. */
    private static float virtualPulse(int band, long step) {
        long value = (step + 1L) * 0x9E3779B97F4A7C15L + (band + 11L) * 0xBF58476D1CE4E5B9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        float unit = (value >>> 40) / (float) 0xFFFFFF;
        return 0.24f + 0.76f * unit;
    }

    /**
     * Keeps a word-timed lyric's active progress in view. This mirrors the display renderer:
     * karaoke follows its highlighted text while a plain LRC line consumes its own duration.
     */
    private float drawCompactMarqueeKaraoke(Canvas canvas, MusicSnapshot snapshot, String value,
                                           float x, float y, float requestedSize, float maxWidth,
                                           float density, int baseColor, int activeColor) {
        return drawCompactMarqueeKaraoke(canvas, snapshot, value, x, y, requestedSize, maxWidth,
                density, baseColor, activeColor, 0f);
    }

    /**
     * @param availableHeight 这一行的可用高度（用于「换行」档决定最多排几行；0 = 按单行处理）
     */
    private float drawCompactMarqueeKaraoke(Canvas canvas, MusicSnapshot snapshot, String value,
                                           float x, float y, float requestedSize, float maxWidth,
                                           float density, int baseColor, int activeColor,
                                           float availableHeight) {
        if (value == null || value.isEmpty()) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            return 0f;
        }
        String text = value.replace('\n', ' ');
        setTextPaint(requestedSize, Typeface.BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        float textWidth = paint.measureText(text);
        if (textWidth <= maxWidth) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            // A line that fits sits on the panel's chosen alignment; one that overflows always
            // scrolls from the column's left edge.
            Paint.Align align = lyricTextAlign(Paint.Align.CENTER);
            drawKaraoke(canvas, snapshot, text, lyricAnchorX(x, maxWidth, align), y, requestedSize,
                    maxWidth, align,
                    baseColor, activeColor);
            return 0f;
        }
        // 长句显示方式（issue #47）：默认仍是跑马灯；另两档在下面就地处理，版面预算不变。
        String mode = LongLineLayout.resolveMode(longLineMode, compactTextOnly);
        if (LongLineLayout.MODE_SHRINK.equals(mode)) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            float size = LongLineLayout.shrinkSize(requestedSize, textWidth, maxWidth);
            Paint.Align align = lyricTextAlign(Paint.Align.CENTER);
            drawKaraoke(canvas, snapshot, text, lyricAnchorX(x, maxWidth, align), y, size,
                    maxWidth, align, baseColor, activeColor);
            return 0f;
        }
        if (LongLineLayout.MODE_WRAP.equals(mode)) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            float lineHeight = requestedSize * 1.22f;
            int lines = LongLineLayout.wrapMaxLines(availableHeight, lineHeight, 3);
            // 把整块居中在原行位置上：不动调用方的版面预算（换行会吃掉相邻行位，这是该档的代价）。
            float blockHeight = wrappedTextHeight(text, requestedSize, maxWidth, lines);
            float blockTop = y - requestedSize
                    - Math.max(0f, (blockHeight - lineHeight) * 0.5f);
            drawWrappedKaraoke(canvas, snapshot, text, x, blockTop, requestedSize, maxWidth,
                    activeColor, lines);
            return 0f;
        }

        long now = SystemClock.elapsedRealtime();
        if (!text.equals(compactMarqueeText)) {
            compactMarqueeText = text;
            compactMarqueeElapsedMs = 0L;
            compactMarqueeLastFrameMs = now;
        } else if (snapshot.playing) {
            compactMarqueeElapsedMs += Math.max(0L, now - compactMarqueeLastFrameMs);
            compactMarqueeLastFrameMs = now;
        } else {
            compactMarqueeLastFrameMs = now;
        }
        compactMarqueeActive = snapshot.playing;
        float overflow = textWidth - maxWidth;
        float offset;
        if (snapshot.lyrics.wordTimed) {
            LrcTimeline.At at = snapshot.lyrics;
            float highlightedWidth = karaokeHighlightWidth(text, at);
            // Follow the active syllable instead of the wall clock, keeping it comfortably
            // inside the viewport even for a long line.
            float anchor = Math.max(12f * density, maxWidth * 0.64f);
            offset = Math.max(0f, Math.min(overflow, highlightedWidth - anchor));
        } else if (snapshot.lyrics.lineStartMs >= 0L
                && snapshot.lyrics.lineDurationMs > 0L) {
            float lineProgress = clamp((snapshot.positionMs + lyricOffsetMs
                    - snapshot.lyrics.lineStartMs) / (float) snapshot.lyrics.lineDurationMs);
            // Preserve a readable lead-in, then make the final words visible before the
            // following line is due, matching the TFT's timed scrolling behavior.
            float scrollProgress = clamp((lineProgress - 0.06f) / 0.82f);
            offset = overflow * scrollProgress;
        } else {
            // Metadata or untimed fallback: move briskly, but never use this path for lyrics
            // that already have position information.
            long travelMs = Math.max(240L, Math.round(overflow / (72f * density) * 1_000f));
            long cycleMs = 400L + travelMs + 700L;
            long cyclePosition = compactMarqueeElapsedMs % cycleMs;
            offset = cyclePosition <= 400L ? 0f
                    : cyclePosition >= 400L + travelMs ? overflow
                    : overflow * (cyclePosition - 400L) / (float) travelMs;
        }

        int save = canvas.save();
        canvas.clipRect(x, y - requestedSize * 1.25f, x + maxWidth, y + requestedSize * 0.35f);
        float drawX = x - offset;
        // The ending highlight is a glow under the glyphs, so it goes first.
        drawTrailingGlowInLine(canvas, snapshot.lyricAvailable ? snapshot.lyrics : null, text,
                drawX, y, requestedSize, activeColor);
        // 逐字歌词及时擦除 has to work here too: this path scrolls a long single line sideways
        // instead of going through drawKaraoke(), so the sung prefix is withheld with a clip in
        // the scrolled coordinate space, which keeps the eraser glued to its own glyphs.
        long lineId = snapshot.lyrics.lineStartMs;
        float sungWidth = sungPrefixWidth(text, lineId);
        int eraseSave = -1;
        if (sungWidth > 0f) {
            eraseSave = canvas.save();
            canvas.clipRect(drawX + sungWidth, y - requestedSize * 1.25f, x + maxWidth,
                    y + requestedSize * 0.35f);
        }
        drawLyricText(canvas, text, drawX, y, requestedSize, baseColor);
        if (eraseSave >= 0) canvas.restoreToCount(eraseSave);
        if (!snapshot.lyricAvailable || snapshot.lyrics.lyric.isEmpty()) {
            drawLyricOutline(canvas, text, drawX, y, requestedSize, activeColor, true);
            paint.setColor(activeColor);
            canvas.drawText(text, drawX, y, paint);
        } else if (!snapshot.lyrics.wordTimed) {
            drawLyricOutline(canvas, text, drawX, y, requestedSize, activeColor, true);
            paint.setColor(activeColor);
            applyLyricTextEffect(requestedSize, activeColor, 255);
            canvas.drawText(text, drawX, y, paint);
            paint.clearShadowLayer();
        } else {
            LrcTimeline.At at = snapshot.lyrics;
            float highlightedWidth = karaokeHighlightWidth(text, at);
            int highlightSave = canvas.save();
            canvas.clipRect(drawX + sungWidth, y - requestedSize * 1.25f,
                    drawX + Math.min(textWidth, highlightedWidth), y + requestedSize * 0.35f);
            drawLyricOutline(canvas, text, drawX, y, requestedSize, activeColor, true);
            paint.setColor(activeColor);
            applyLyricTextEffect(requestedSize, activeColor, 255);
            canvas.drawText(text, drawX, y, paint);
            paint.clearShadowLayer();
            canvas.restoreToCount(highlightSave);
        }
        drawSungGhost(canvas, text, text, drawX, y, requestedSize, activeColor, lineId);
        canvas.restoreToCount(save);
        return offset;
    }

    private void drawCompactFollowingTranslation(Canvas canvas, String value, float x, float y,
                                                  float size, float maxWidth, float sourceOffset,
                                                  int color) {
        if (value == null || value.isEmpty()) return;
        String text = value.replace('\n', ' ');
        setTextPaint(size, Typeface.NORMAL);
        paint.setTextAlign(Paint.Align.LEFT);
        float textWidth = paint.measureText(text);
        if (textWidth <= maxWidth) {
            Paint.Align align = lyricTextAlign(Paint.Align.CENTER);
            paint.setTextAlign(align);
            paint.setColor(color);
            canvas.drawText(text, lyricAnchorX(x, maxWidth, align), y, paint);
            return;
        }
        float offset = Math.min(Math.max(0f, textWidth - maxWidth), Math.max(0f, sourceOffset));
        int save = canvas.save();
        canvas.clipRect(x, y - size * 1.25f, x + maxWidth, y + size * 0.35f);
        paint.setColor(color);
        canvas.drawText(text, x - offset, y, paint);
        canvas.restoreToCount(save);
    }

    private void drawRefinedSongInfo(Canvas canvas, MusicSnapshot snapshot, float density,
                                     float columnWidth, float pad, int primaryText,
                                     int secondaryText, int accent, float contentScale) {
        float height = getHeight();
        // 无封面且选了「隐藏封面区域」：封面整块收掉，歌名 / 歌手 / 来源往上顶（issue #50）。
        boolean hideCover = coverAreaHidden();
        float coverSize = hideCover ? 0f : 138f * density * contentScale * coverScale;
        float titleSize = 22.5f * density * contentScale * titleScale;
        float metaSize = titleSize * 0.42f;
        // 歌手字号可单独调（issue #63）；没调过时就是原来的 metaSize（42%）。
        float artistSize = MetadataTypeScaleMath.artistSize(titleSize, 0.42f, artistScalePercent);
        // 版面预算按两者较大者算，歌手调大也不会压到来源行或歌词。
        float metaUnit = Math.max(metaSize, artistSize);
        float groupHeight = (hideCover ? 0f : coverSize + 18f * density * contentScale)
                + titleSize + metaUnit * 3.2f;
        float top = "middle".equals(refinedCoverVertical)
                ? Math.max(pad, (height - groupHeight) / 2f)
                : Math.max(pad, height - pad - 8f * density * contentScale - groupHeight);
        float left = "center".equals(refinedCoverHorizontal)
                ? Math.max(pad, (columnWidth - coverSize) / 2f) : pad;
        coverRect.set(left, top, left + coverSize, top + coverSize);
        RectF cover = coverRect;
        float radius = refinedRectangleCover ? 16f * density * contentScale : coverSize / 2f;
        if (hideCover) clearCoverRect(left, top);

        if (!hideCover && refinedCoverShadow && snapshot.albumArt != null
                && !snapshot.albumArt.isRecycled()) {
            shadowRect.set(cover.left - coverSize * 0.06f,
                    cover.top + coverSize * 0.02f,
                    cover.right + coverSize * 0.06f,
                    cover.bottom + coverSize * 0.11f);
            RectF shadow = shadowRect;
            int save = canvas.save();
            clipPath.reset();
            clipPath.addRoundRect(shadow, radius, radius, Path.Direction.CW);
            canvas.clipPath(clipPath);
            drawBitmapCrop(canvas, blurredPreview(snapshot.albumArt), shadow, 145);
            canvas.restoreToCount(save);
        }
        if (!hideCover) {
            drawCoverRotated(canvas, snapshot.albumArt, cover, radius,
                    mix(accent, Color.DKGRAY, 0.55f));
        }
        float textLeft = "center".equals(refinedCoverHorizontal) ? pad : left;
        float textWidth = Math.max(1f, columnWidth - textLeft - pad);
        Paint.Align align = "center".equals(refinedCoverHorizontal)
                ? Paint.Align.CENTER : Paint.Align.LEFT;
        float anchor = align == Paint.Align.CENTER ? columnWidth / 2f : textLeft;
        float y = cover.bottom + (hideCover ? titleSize : 18f * density * contentScale + titleSize);
        drawingMetadata = true;
        drawRefinedText(canvas, snapshot.active ? snapshot.title : "等待音乐",
                anchor, y, titleSize, primaryText, textWidth, align, Typeface.NORMAL, 255);
        y += metaUnit * 1.55f;
        drawRefinedText(canvas, snapshot.artist, anchor, y, artistSize,
                secondaryText, textWidth, align, Typeface.NORMAL, 205);
        y += metaUnit * 1.38f;
        if (showPlayerStatus) {
            drawRefinedText(canvas, snapshot.sourceName + sourceSuffix(snapshot), anchor, y,
                    metaSize * 0.88f, secondaryText, textWidth, align, Typeface.NORMAL, 145);
        }
        drawingMetadata = false;
    }

    private void drawRefinedLyrics(Canvas canvas, MusicSnapshot snapshot, float density,
                                    float left, float width, int primaryText,
                                    int secondaryText, float contentScale) {
        drawRefinedLyrics(canvas, snapshot, density, left, width, primaryText,
                secondaryText, contentScale, getHeight() * (refinedCurrentAlign / 100f));
    }

    private void drawRefinedLyrics(Canvas canvas, MusicSnapshot snapshot, float density,
                                   float left, float width, int primaryText,
                                   int secondaryText, float contentScale, float currentY) {
        float fontSize = refinedLyricFontSize * density * contentScale * textScale
                * (secondary ? 1.03f : 1f);
        if (snapshot.lyrics.nearbyLines.isEmpty()) {
            drawWrappedKaraoke(canvas, snapshot, currentText(snapshot), left,
                    currentY - fontSize + browseVisualOffsetPx,
                    fontSize, width, primaryText, 3);
            return;
        }
        List<LrcTimeline.NearbyLine> lines = snapshot.lyrics.nearbyLines;
        int current = 0;
        ensureRefinedLineCapacity(lines.size());
        float[] heights = refinedLineHeights;
        float translationSize = fontSize * 0.62f;
        for (int i = 0; i < lines.size(); i++) {
            LrcTimeline.NearbyLine line = lines.get(i);
            if (line.offset == 0) current = i;
            if (line.interlude) {
                heights[i] = fontSize * 1.75f;
            } else {
                heights[i] = wrappedTextHeight(line.text, fontSize, width, 3);
                if (refinedShowTranslation && !secondaryLyric(line).isEmpty()) {
                    heights[i] += fontSize * 0.18f
                            + wrappedTextHeight(secondaryLyric(line), translationSize, width, 2);
                }
            }
        }
        float gap = fontSize * 0.52f;
        lastRefinedBrowseStepPx = Math.max(1f, heights[current] + gap);
        float[] tops = refinedLineTops;
        tops[current] = currentY - Math.min(fontSize, heights[current] * 0.45f);
        for (int i = current + 1; i < lines.size(); i++) {
            tops[i] = tops[i - 1] + heights[i - 1] + gap;
        }
        for (int i = current - 1; i >= 0; i--) {
            tops[i] = tops[i + 1] - heights[i] - gap;
        }
        float scrollShift = animatedLyricScrollShift(snapshot.lyrics.lineStartMs,
                heights[current] + gap) + browseVisualOffsetPx;
        for (int i = 0; i < lines.size(); i++) {
            LrcTimeline.NearbyLine line = lines.get(i);
            int offset = line.offset;
            if (Math.abs(offset) > 3) continue;
            float top = tops[i] + scrollShift;
            RefinedLyricCurve.Transform curve = refinedLyricRotate
                    ? RefinedLyricCurve.calculate(tops[current] - top, heights[i],
                    getHeight(), density, refinedRotateCurvature)
                    : RefinedLyricCurve.Transform.IDENTITY;
            float lineLeft = left + curve.translationX;
            top += curve.translationY;
            float centerY = top + heights[i] / 2f;
            float scale = refinedLyricZoom ? refinedScaleForOffset(offset) : 1f;
            if (offset == 1) scale *= nextLyricScale;
            float opacity = offset == 0 ? 1f : 0.40f;
            if (offset > 0) opacity *= nextLyricOpacity / 100f;
            else if (offset < 0) opacity *= previousLyricOpacity / 100f;
            if (refinedLyricFade && Math.abs(offset) > 1) {
                opacity *= Math.max(0f, 1f - 0.4f * (Math.abs(offset) - 1));
            }
            float edge = Math.min(centerY / Math.max(1f, getHeight()),
                    (getHeight() - centerY) / Math.max(1f, getHeight()));
            opacity *= clamp(edge * 8f) * curve.opacity;
            if (opacity <= 0.01f) continue;
            int save = canvas.save();
            if (refinedLyricRotate && Math.abs(curve.rotationDegrees) > 0.001f) {
                canvas.rotate(curve.rotationDegrees, lineLeft, centerY);
            }
            canvas.scale(scale, scale, lineLeft, centerY);
            // 「歌词模糊」只作用在非当前行：走离屏位图，面板本身仍是硬件渲染（issue #59 后续）。
            boolean blurLine = refinedLyricBlur && offset != 0;
            float blurRadius = blurLine
                    ? Math.min(4.5f * density, (0.5f + Math.abs(offset)) * density) : 0f;
            int lineStyle = refinedOriginalBold ? Typeface.BOLD : Typeface.NORMAL;
            if (line.interlude) {
                drawInterludeDots(canvas, snapshot, lineLeft, top + fontSize * 0.30f,
                        fontSize * 0.35f,
                        withAlpha(primaryText, Math.round(225f * opacity)));
            } else if (offset == 0) {
                drawWrappedKaraoke(canvas, snapshot, currentText(snapshot), lineLeft, top,
                        fontSize, width, currentLyricColor(primaryText), 3);
            } else if (offset < 0) {
                // 同 drawPure：消散完的行不能被当成普通行重画回来（issue #40）。
                drawBlurredLine(canvas, line.text, lineLeft, top, fontSize,
                        withAlpha(inactiveLyricColor(secondaryText), 255), opacity, width,
                        lineStyle, 3, blurRadius,
                        previousLyricDissolve.affects(line.timeMs), line.timeMs);
            } else {
                drawBlurredLine(canvas, line.text, lineLeft, top, fontSize,
                        withAlpha(inactiveLyricColor(secondaryText), 255), opacity, width,
                        lineStyle, 3, blurRadius, false, line.timeMs);
            }
            if (!line.interlude && refinedShowTranslation && !secondaryLyric(line).isEmpty()) {
                float originalHeight = wrappedTextHeight(line.text, fontSize, width, 3);
                drawBlurredLine(canvas, secondaryLyric(line), lineLeft,
                        top + originalHeight + fontSize * 0.18f, translationSize,
                        withAlpha(secondaryText, 255),
                        opacity * (offset == 0 ? 205f : 180f) / 255f, width,
                        Typeface.NORMAL, 2, blurRadius, false, line.timeMs);
            }
            paint.setMaskFilter(null);
            canvas.restoreToCount(save);
        }
    }

    private void ensureRefinedLineCapacity(int count) {
        if (refinedLineHeights.length >= count) return;
        int capacity = Math.max(count, refinedLineHeights.length * 2);
        refinedLineHeights = new float[capacity];
        refinedLineTops = new float[capacity];
    }

    private float animatedLyricScrollShift(long lineStartMs, float stepHeight) {
        if (lineStartMs < 0L || !smoothLyricScroll) return 0f;
        if (manualPreviewActive()) {
            lastRenderedLineStartMs = lineStartMs;
            lyricScrollAnimationStartedMs = 0L;
            lyricScrollDirection = 0;
            return 0f;
        }
        if (lastRenderedLineStartMs == Long.MIN_VALUE) {
            lastRenderedLineStartMs = lineStartMs;
            return 0f;
        }
        if (lineStartMs != lastRenderedLineStartMs) {
            lyricScrollDirection = lineStartMs > lastRenderedLineStartMs ? 1 : -1;
            lastRenderedLineStartMs = lineStartMs;
            lyricScrollAnimationStartedMs = SystemClock.elapsedRealtime();
        }
        float progress = clamp((SystemClock.elapsedRealtime()
                - lyricScrollAnimationStartedMs) / 500f);
        float eased = 1f - (float) Math.pow(1f - progress, 3d);
        return lyricScrollDirection * stepHeight * (1f - eased);
    }

    /** Animates the default card's old current line up while the new line enters below it. */
    private float basicLyricEntryShift(long lineStartMs, float stepHeight) {
        if (lineStartMs < 0L || !smoothLyricScroll || manualPreviewActive()) {
            lastBasicLineStartMs = lineStartMs;
            return 0f;
        }
        if (lastBasicLineStartMs == Long.MIN_VALUE) {
            lastBasicLineStartMs = lineStartMs;
            return 0f;
        }
        if (lineStartMs != lastBasicLineStartMs) {
            lastBasicLineStartMs = lineStartMs;
            basicLyricScrollAnimationStartedMs = SystemClock.elapsedRealtime();
        }
        float progress = clamp((SystemClock.elapsedRealtime()
                - basicLyricScrollAnimationStartedMs) / 360f);
        float eased = 1f - (float) Math.pow(1f - progress, 3d);
        return stepHeight * (1f - eased);
    }

    private float nextLyricSize(float currentLyricSize) {
        return currentLyricSize * nextLyricScale;
    }

    private int nextLyricColor(int color) {
        return withAlpha(color, Math.round(Color.alpha(color) * nextLyricOpacity / 100f));
    }

    /**
     * 时间段配色在运行中也要按时换（issue #34）：每半分钟对一次本地时钟，生效的深浅色变了就重新
     * 加载样式。检查很轻（读一个偏好 + 系统时钟），而且只在主题模式为「按时间段」时才做。
     */
    private void refreshScheduledTheme(long nowElapsedMs) {
        if (nowElapsedMs - lastThemeCheckMs < 30_000L) return;
        lastThemeCheckMs = nowElapsedMs;
        if (!AppPreferences.themeScheduleEnabled(getContext())) return;
        String resolved = AppPreferences.resolvedThemeMode(getContext());
        if (resolved.equals(themeMode)) return;
        reloadStyle();
    }

    /**
     * 「正在匹配歌词」（issue #45）：匹配期间面板不再只有一行静止的文字——歌名照常显示，底部给一排
     * 从中心向两侧发散的小点。默认匹配超过 3 秒才出现，避免瞬间匹配成功时闪一下；老旧车机可以整个
     * 关掉，退回原来的静止文字。
     */
    private void updateMatchingState(MusicSnapshot snapshot, long nowElapsedMs) {
        boolean matching = snapshot.active && !snapshot.lyricLoaded && !snapshot.lyricAvailable;
        if (!matching) {
            matchingTrackKey = "";
            matchingSinceMs = 0L;
            return;
        }
        String key = snapshot.title + "\u0000" + snapshot.artist;
        if (!key.equals(matchingTrackKey)) {
            matchingTrackKey = key;
            matchingSinceMs = nowElapsedMs;
        }
    }

    /** True while the 「正在匹配」 indicator should be on screen. */
    private boolean matchingIndicatorVisible(long nowElapsedMs) {
        if (!matchingAnimationEnabled || matchingSinceMs <= 0L) return false;
        return nowElapsedMs - matchingSinceMs >= matchingAnimationDelayMs;
    }

    private void drawMatchingIndicator(Canvas canvas, float density, long nowElapsedMs) {
        if (!matchingIndicatorVisible(nowElapsedMs)) return;
        int dots = 7;
        float gap = 6f * density;
        float centerX = getWidth() * 0.5f;
        float centerY = getHeight() - 9f * density;
        float phase = (nowElapsedMs % 1_100L) / 1_100f;
        int highlight = currentLyricColor(0xFFFFCA66);
        int savedColor = paint.getColor();
        Paint.Style savedStyle = paint.getStyle();
        paint.setStyle(Paint.Style.FILL);
        for (int index = 0; index < dots; index++) {
            int distance = Math.abs(index - (dots - 1) / 2);
            float local = (phase * 2f - distance * 0.20f) % 1f;
            if (local < 0f) local += 1f;
            float radius = 1.7f * density * (0.55f + 0.75f * (float) Math.sin(Math.PI * local));
            int alpha = Math.round(70f + 165f * (1f - local));
            paint.setColor(withAlpha(highlight, alpha));
            canvas.drawCircle(centerX + (index - (dots - 1) / 2f) * gap * 2f, centerY, radius, paint);
        }
        paint.setColor(savedColor);
        paint.setStyle(savedStyle);
    }

    /**
     * 这个样式当前会不会真的转（issue #22）：只有圆形封面才转，所以只有这种情况才需要为旋转
     * 保持帧率——方形 / 圆角封面开着开关也不该白耗电。
     */
    private boolean coverCanRotate() {
        if ("refined".equals(overlayStyle)) return !refinedRectangleCover;
        if ("amll".equals(overlayStyle) || "compact".equals(overlayStyle)) return roundCover;
        return false;
    }

    /**
     * 播放器没给封面、用户又选了「隐藏封面区域」：这一帧不画封面，版面跟着内收（issue #50）。
     */
    private boolean coverAreaHidden() {
        return hideCoverWithoutArt && frameAlbumArtMissing;
    }

    /**
     * 空封面矩形：宽高都为 0 时，后面按 {@code cover.bottom} / {@code cover.right} 推出来的行位
     * 自然收到封面该在的位置，不需要另写一套版面（issue #50）。
     */
    private void clearCoverRect(float left, float top) {
        coverRect.set(left, top, left, top);
    }

    /**
     * Draws the dust left behind by the glyphs that already went. The glyphs own the schedule
     * and the dust outlives them, so this only has to paint whatever is still in the air.
     */
    private void drawPreviousLyricDust(Canvas canvas, long nowMs) {
        if (!previousLyricParticles) return;
        // While the user scrolls the lyric sheet, leave the frame alone: no dust over it.
        if (previousLyricDissolve.isBrowsing()) return;
        int savedColor = paint.getColor();
        Paint.Style savedStyle = paint.getStyle();
        paint.setStyle(Paint.Style.FILL);
        for (int slot = 0; slot < LyricDissolveEffect.DUST_CAPACITY; slot++) {
            if (!previousLyricDissolve.dustAlive(slot, nowMs)) continue;
            int dustColor = previousLyricDissolve.dustColor(slot);
            int alpha = Math.round(previousLyricDissolve.dustAlpha(slot, nowMs)
                    * Color.alpha(dustColor) / 255f);
            if (alpha <= 0) continue;
            paint.setColor(withAlpha(dustColor, alpha));
            canvas.drawCircle(previousLyricDissolve.dustX(slot, nowMs),
                    previousLyricDissolve.dustY(slot, nowMs),
                    previousLyricDissolve.dustRadius(slot, nowMs), paint);
        }
        paint.setColor(savedColor);
        paint.setStyle(savedStyle);
    }

    /**
     * One glyph of a dissolving line: same outline-then-fill treatment as
     * {@link #drawLyricText}, but tinted with that glyph's own alpha.
     */
    private void drawDissolvingGlyph(Canvas canvas, String line, String glyph, float x, float y,
                                     float size, int resolvedColor) {        if (shouldOutlineLyric(line, false)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(outlineStrokeWidth(size, inactiveLyricOutlineWidthPercent));
            paint.setColor(outlineStrokeColor(resolvedColor, false));
            canvas.drawText(glyph, x, y, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        paint.setColor(resolvedColor);
        canvas.drawText(glyph, x, y, paint);
    }

    /**
     * Walks one drawn line glyph by glyph. Each glyph fades on its own clock and, once it has
     * started, reports its box so the dust leaves from the character that is actually going.
     *
     * @return the character index the next chunk should continue from
     */
    /**
     * One glyph of a line that is still the current line: the outline and glow the singer just
     * had, not the muted "previous line" treatment.
     */
    private void drawActiveGlyph(Canvas canvas, String line, String glyph, float x, float y,
                                 float size, int color) {
        if (shouldOutlineLyric(line, true)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(outlineStrokeWidth(size, currentLyricOutlineWidthPercent));
            paint.setColor(outlineStrokeColor(color, true));
            canvas.drawText(glyph, x, y, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        paint.setColor(color);
        applyLyricTextEffect(size, color, Color.alpha(color));
        canvas.drawText(glyph, x, y, paint);
        paint.clearShadowLayer();
    }

    /**
     * Width of the already-sung prefix of a single-line lyric, 0 when nothing has been erased.
     * Only the line the erase belongs to is ever affected, so scrolling to another line shows it
     * whole. The caller must have configured the paint for the line it is about to draw.
     */
    private float sungPrefixWidth(String text, long lineId) {
        if (!previousLyricDissolve.isErasingWords(lineId) || text == null || text.isEmpty()) {
            return 0f;
        }
        int sung = Math.min(previousLyricDissolve.sungUnits(lineId), text.length());
        return sung <= 0 ? 0f : paint.measureText(text, 0, sung);
    }

    /** Width of the already-sung prefix inside one wrapped chunk. */
    private float sungWidthInChunk(WrappedChunk chunk, long lineId) {
        if (!previousLyricDissolve.isErasingWords(lineId)) return 0f;
        int local = Math.min(chunk.text.length(),
                previousLyricDissolve.sungUnits(lineId) - chunk.start);
        return local <= 0 ? 0f : paint.measureText(chunk.text, 0, local);
    }

    /**
     * Paints the units sitting in the erase zone — they are already sung, so they come apart
     * with their own alpha and shed their dust from their own glyph boxes. UTF-16 units are
     * walked in glyph-sized steps so a surrogate pair is never drawn as two broken halves.
     */
    private void drawErasingUnits(Canvas canvas, String line, String text, float left,
                                  float baseline, float size, int color, int firstUnit,
                                  int lastUnit, long lineId) {
        float cursor = left;
        for (int unit = firstUnit; unit < lastUnit && unit < text.length(); ) {
            int glyphChars = Character.isHighSurrogate(text.charAt(unit)) && unit + 1 < text.length()
                    ? 2 : 1;
            String glyph = text.substring(unit, Math.min(text.length(), unit + glyphChars));
            float advance = paint.measureText(glyph);
            float alpha = previousLyricDissolve.currentLineAlpha(lineId, unit);
            if (alpha > LyricDissolveEffect.VISIBLE_ALPHA) {
                drawActiveGlyph(canvas, line, glyph, cursor, baseline, size,
                        withAlpha(color, Math.round(Color.alpha(color) * alpha)));
            }
            if (alpha < 1f) {
                // Dust is painted after the layout transforms are restored, so report the box
                // in that final space.
                canvas.getMatrix(dissolveBoxMatrix);
                dissolveBoxRect.set(cursor, baseline - size * .78f, cursor + advance,
                        baseline + size * .24f);
                dissolveBoxMatrix.mapRect(dissolveBoxRect);
                previousLyricDissolve.emitFromCurrentLine(lineId, unit, dissolveBoxRect.left,
                        dissolveBoxRect.top, dissolveBoxRect.width(), dissolveBoxRect.height(),
                        dissolveBoxMatrix.mapRadius(size), color);
            }
            cursor += advance;
            unit += glyphChars;
        }
    }

    /** The erase zone of a single-line lyric, drawn where the renderer stopped painting. */
    private void drawSungGhost(Canvas canvas, String line, String text, float left, float baseline,
                               float size, int color, long lineId) {
        if (!previousLyricDissolve.isErasingWords(lineId)) return;
        int sung = Math.min(previousLyricDissolve.sungUnits(lineId), text.length());
        if (sung <= 0) return;
        int first = previousLyricDissolve.firstVisibleUnit(lineId, 0, sung);
        if (first >= sung) return;
        drawErasingUnits(canvas, line, text, left + paint.measureText(text, 0, first), baseline,
                size, color, first, sung, lineId);
    }

    /** The erase zone of one wrapped chunk of the line being sung. */
    private void drawSungGhostChunk(Canvas canvas, String line, WrappedChunk chunk, float x,
                                    float baseline, float size, int color, long lineId) {
        if (!previousLyricDissolve.isErasingWords(lineId)) return;
        int localEnd = Math.min(chunk.text.length(),
                previousLyricDissolve.sungUnits(lineId) - chunk.start);
        if (localEnd <= 0) return;
        int first = previousLyricDissolve.firstVisibleUnit(lineId, chunk.start,
                chunk.start + localEnd);
        int firstLocal = Math.max(0, first - chunk.start);
        if (firstLocal >= localEnd) return;
        drawErasingUnits(canvas, line, chunk.text, x + paint.measureText(chunk.text, 0, firstLocal),
                baseline, size, color, firstLocal, localEnd, lineId);
    }

    private int drawDissolvingGlyphs(Canvas canvas, String line, String text, float x, float y,
                                     float size, int resolvedColor, long lineId,
                                     int charIndexBase, int charCount, boolean currentStyle) {
        paint.setTextAlign(Paint.Align.LEFT);
        float cursor = x;
        int charIndex = charIndexBase;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            int glyphChars = Character.charCount(codePoint);
            String glyph = text.substring(offset, offset + glyphChars);
            float advance = paint.measureText(glyph);
            float alpha = previousLyricDissolve.characterAlpha(lineId, charIndex, charCount);
            if (alpha > LyricDissolveEffect.VISIBLE_ALPHA) {
                int glyphColor = withAlpha(resolvedColor,
                        Math.round(Color.alpha(resolvedColor) * alpha));
                if (currentStyle) {
                    drawActiveGlyph(canvas, line, glyph, cursor, y, size, glyphColor);
                } else {
                    drawDissolvingGlyph(canvas, line, glyph, cursor, y, size, glyphColor);
                }
            }
            if (alpha < 1f) {
                // The layout may still have a scale or rotation on the canvas — the refined
                // curve does by default. Dust is painted after those are restored, so report
                // the glyph box in that final space or the dust misses the glyph it came from.
                canvas.getMatrix(dissolveBoxMatrix);
                dissolveBoxRect.set(cursor, y - size * .78f, cursor + advance, y + size * .24f);
                dissolveBoxMatrix.mapRect(dissolveBoxRect);
                previousLyricDissolve.emitFromCharacter(lineId, charIndex,
                        dissolveBoxRect.left, dissolveBoxRect.top, dissolveBoxRect.width(),
                        dissolveBoxRect.height(), dissolveBoxMatrix.mapRadius(size), resolvedColor);
            }
            cursor += advance;
            offset += glyphChars;
            charIndex++;
        }
        return charIndex;
    }

    /**
     * Draws one line glyph by glyph so each glyph can fade on its own clock. The size is the
     * final one: callers that shrink text to fit must do it themselves, so a ghost always
     * matches the size its line was really drawn at.
     *
     * @return false when this line is not dissolving, so the caller can take its normal path
     */
    private boolean drawDissolvingLine(Canvas canvas, String value, float anchorX, float y,
                                       float size, int color, float maxWidth, int style,
                                       Paint.Align align, long lineId, boolean currentStyle) {
        if (value == null || value.isEmpty()) return false;
        if (!previousLyricDissolve.affects(lineId)) return false;
        setTextPaintForValue(size, style, value);
        paint.setTextAlign(Paint.Align.LEFT);
        String text = ellipsize(value.replace('\n', ' '), maxWidth);
        if (drawSplitSourceMetadata(canvas, text, anchorX, y, maxWidth, align, 255)) return true;
        int resolved = resolveMetadataColor(value, color);
        // Lay the finished string out, then let the glyphs eat into it from the left.
        float left = align == Paint.Align.CENTER ? anchorX - paint.measureText(text) * .5f : anchorX;
        drawDissolvingGlyphs(canvas, value, text, left, y, size, resolved, lineId, 0,
                text.codePointCount(0, text.length()), currentStyle);
        return true;
    }

    private void drawLeftDissolving(Canvas canvas, String value, float x, float y,
                                    float requestedSize, int color, float maxWidth, int style,
                                    long lineId) {
        if (drawDissolvingLine(canvas, value, x, y,
                fitSize(value, requestedSize, maxWidth, style), color, maxWidth, style,
                Paint.Align.LEFT, lineId, false)) return;
        drawLeft(canvas, value, x, y, requestedSize, color, maxWidth, style);
    }

    /**
     * The user's horizontal alignment for the lyric rows, or {@code fallback} when the setting is
     * left on the style default. Only the single-column styles (classic, compact, pure) draw their
     * lyrics through the helpers below; Refined, AMLL and PiP are two-column layouts whose lyrics
     * are left-aligned by construction.
     */
    private Paint.Align lyricTextAlign(Paint.Align fallback) {
        String value = AppPreferences.lyricAlign(getContext(), secondary);
        if (value.isEmpty()) return fallback;
        return "left".equals(value) ? Paint.Align.LEFT : Paint.Align.CENTER;
    }

    /** Anchor X that puts a row drawn with {@code align} inside {@code [left, left + maxWidth]}. */
    private static float lyricAnchorX(float left, float maxWidth, Paint.Align align) {
        return align == Paint.Align.CENTER ? left + maxWidth * 0.5f : left;
    }

    /** One lyric row (translation, next line, …) with the panel's chosen alignment. */
    private void drawAlignedLyric(Canvas canvas, String value, float left, float maxWidth, float y,
                                  float requestedSize, int color, int style) {
        if (lyricTextAlign(Paint.Align.CENTER) == Paint.Align.LEFT) {
            drawLeft(canvas, value, left, y, requestedSize, color, maxWidth, style);
        } else {
            drawCentered(canvas, value, y, requestedSize, color, maxWidth, style);
        }
    }

    /** The dissolving form of {@link #drawAlignedLyric}. */
    private void drawAlignedDissolving(Canvas canvas, String value, float left, float maxWidth,
                                       float y, float requestedSize, int color, int style,
                                       long lineId) {
        if (lyricTextAlign(Paint.Align.CENTER) == Paint.Align.LEFT) {
            drawLeftDissolving(canvas, value, left, y, requestedSize, color, maxWidth, style,
                    lineId);
        } else {
            drawCenteredDissolving(canvas, value, y, requestedSize, color, maxWidth, style, lineId);
        }
    }

    private void drawCenteredDissolving(Canvas canvas, String value, float y, float requestedSize,
                                        int color, float maxWidth, int style, long lineId) {
        if (drawDissolvingLine(canvas, value, getWidth() / 2f, y,
                fitSize(value, requestedSize, maxWidth, style), color, maxWidth, style,
                Paint.Align.CENTER, lineId, false)) return;
        drawCentered(canvas, value, y, requestedSize, color, maxWidth, style);
    }

    private float drawWrappedTextDissolving(Canvas canvas, String value, float x, float top,
                                            float size, int color, float maxWidth, int style,
                                            int maxLines, long lineId) {
        if (value == null || value.isEmpty()) return 0f;
        if (!previousLyricDissolve.affects(lineId)) {
            return drawWrappedText(canvas, value, x, top, size, color, maxWidth, style, maxLines);
        }
        android.graphics.MaskFilter maskFilter = paint.getMaskFilter();
        setTextPaintForValue(size, style, value);
        paint.setMaskFilter(maskFilter);
        paint.setTextAlign(Paint.Align.LEFT);
        int resolved = resolveMetadataColor(value, color);
        List<WrappedChunk> chunks = wrapText(value.replace('\n', ' '), maxWidth, maxLines);
        int total = 0;
        for (int index = 0; index < chunks.size(); index++) {
            String chunk = chunks.get(index).text;
            total += chunk.codePointCount(0, chunk.length());
        }
        float lineHeight = size * 1.22f;
        int charIndex = 0;
        for (int index = 0; index < chunks.size(); index++) {
            charIndex = drawDissolvingGlyphs(canvas, value, chunks.get(index).text, x,
                    top + size + index * lineHeight, size, resolved, lineId, charIndex, total,
                    false);
        }
        return chunks.size() * lineHeight;
    }

    private int adjacentLyricColor(int color, int offset) {
        int opacity = offset < 0 ? previousLyricOpacity : nextLyricOpacity;
        return withAlpha(color, Math.round(Color.alpha(color) * opacity / 100f));
    }

    /** 经典样式里 offset 那一行的内容；时间轴缺失时退回上一句 / 本句 / 下一句。 */
    private static LrcTimeline.NearbyLine classicNearbyLine(MusicSnapshot snapshot, int offset) {
        for (LrcTimeline.NearbyLine line : snapshot.lyrics.nearbyLines) {
            if (line.offset == offset) return line;
        }
        return null;
    }

    private static String classicFallbackText(MusicSnapshot snapshot, int offset) {
        if (offset == -1) return snapshot.lyrics.previousLyric;
        if (offset == 1) return snapshot.lyrics.nextLyric;
        return "";
    }

    /**
     * 相邻歌词行的颜色：上一句 / 下一句各自的不透明度照旧，更远的行再淡一档，多行时后排不会和本句
     * 抢注意力（issue #64）。
     */
    private int classicAdjacentColor(int offset) {
        int base = inactiveLyricColor(0xFF68778C);
        int color = offset < 0 ? adjacentLyricColor(base, offset) : nextLyricColor(base);
        int distance = Math.abs(offset);
        if (distance <= 1) return color;
        return withAlpha(color,
                Math.round(Color.alpha(color) * Math.max(0.40f, 1f - 0.22f * (distance - 1))));
    }

    private int lyricColor(int fallback) {
        int selected = lyricColor;
        if (lyricsFollowTheme) {
            selected = lyricEnvironmentUsesLightColors() ? lyricLightColor : lyricDarkColor;
        }
        return selected == 0 ? fallback : withAlpha(selected, Color.alpha(fallback));
    }

    private int currentLyricColor(int fallback) {
        return slotLyricColor(currentLyricColor, currentLyricLightColor,
                currentLyricDarkColor, fallback);
    }

    private int inactiveLyricColor(int fallback) {
        return slotLyricColor(inactiveLyricColor, inactiveLyricLightColor,
                inactiveLyricDarkColor, fallback);
    }

    /** Current/inactive slots track the light-dark pair while theme following is on. */
    private int slotLyricColor(int flat, int light, int dark, int fallback) {
        int selected = AppPreferences.resolveThemedSlotColor(lyricsFollowTheme,
                lyricEnvironmentUsesLightColors(), flat, light, dark);
        return selected == 0 ? lyricColor(fallback) : withAlpha(selected, Color.alpha(fallback));
    }

    private void drawTopLyricBackground(Canvas canvas, MusicSnapshot snapshot, float density) {
        String mode = AppPreferences.topLyricBackground(getContext());
        float radius = Math.min(getWidth(), getHeight()) * panelCornerRadiusRatio(0.22f);
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        if ("compact".equals(mode)) {
            if (snapshot.albumArt != null && !snapshot.albumArt.isRecycled()) {
                drawBitmapCrop(canvas, blurredPreview(snapshot.albumArt), panelRect, 150);
            }
            paint.setColor(0x76314255);
            canvas.drawRoundRect(panelRect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setColor(0x55FFFFFF);
            canvas.drawRoundRect(panelRect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        } else if ("blur".equals(mode)) {
            // A real cross-window blur only needs a very light material tint. When the ROM
            // disables blur, keep a subtle translucent glass fallback instead of the old dark
            // 40% overlay that looked like a black filter over the wallpaper.
            paint.setColor(topWindowBlurActive
                    ? (lyricUsesLightColors() ? 0x22FFFFFF : 0x16FFFFFF)
                    : (lyricUsesLightColors() ? 0x30FFFFFF : 0x24FFFFFF));
            canvas.drawRoundRect(panelRect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setColor(topWindowBlurActive ? 0x45FFFFFF : 0x38FFFFFF);
            canvas.drawRoundRect(panelRect, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        } else {
            paint.setShader(new LinearGradient(0f, 0f, getWidth(), getHeight(),
                    0xDE101E31, 0xD827405A, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(panelRect, radius, radius, paint);
            paint.setShader(null);
        }
        canvas.restoreToCount(save);
    }

    private void drawSharedSpectrum(Canvas canvas, MusicSnapshot snapshot, float density) {
        boolean useReal = AppPreferences.compactUseRealSpectrum(getContext(), secondary);
        AudioSpectrumSource.Frame frame = AudioSpectrumSource.latestFrame();
        long now = SystemClock.elapsedRealtime();
        float[] targets = frame.live && useReal ? frame.levels : virtualSpectrum(snapshot, now);
        compactSpectrumBars.update(targets, now);
        for (int i = 0; i < compactDisplayedSpectrum.length; i++) {
            compactDisplayedSpectrum[i] = compactSpectrumBars.barAt(i);
        }
        float inset = Math.max(8f * density, getWidth() * 0.04f);
        // 与 drawDefault 的预留区共用同一份几何（issue #57）：改一处两处一起变。
        float height = SpectrumLayoutMath.heightPx(
                AppPreferences.spectrumHeightPercent(getContext(), secondary), density,
                getHeight(), SpectrumLayoutMath.LEGACY_MIN_DP, SpectrumLayoutMath.LEGACY_MAX_DP,
                SpectrumLayoutMath.LEGACY_PANEL_RATIO);
        boolean controlsVisible = AppPreferences.showPlaybackControls(getContext(), secondary)
                && (AppPreferences.showPreviousButton(getContext(), secondary)
                || AppPreferences.showPlayPauseButton(getContext(), secondary)
                || AppPreferences.showNextButton(getContext(), secondary));
        float bottom = getHeight() - SpectrumLayoutMath.bottomInsetPx(density, controlsVisible,
                AppPreferences.spectrumGapDp(getContext(), secondary));
        spectrumRect.set(inset, bottom - height, getWidth() - inset, bottom);
        SpectrumRenderer.draw(canvas, paint, spectrumRect, compactDisplayedSpectrum,
                AppPreferences.spectrumStyle(getContext(), secondary),
                AppPreferences.spectrumColorMode(getContext(), secondary), lyricColor(0xFFFFCA66),
                AppPreferences.compactSpectrumColor(getContext(), secondary), palette);
        compactSpectrumAnimating = snapshot.playing;
    }

    private float[] virtualSpectrum(MusicSnapshot snapshot, long now) {
        long step = now / 180L;
        float fraction = (now % 180L) / 180f;
        fraction = fraction * fraction * (3f - 2f * fraction);
        for (int i = 0; i < compactVirtualSpectrum.length; i++) {
            float from = snapshot.playing ? virtualPulse(i, step) : 0.18f;
            float to = snapshot.playing ? virtualPulse(i, step + 1L) : 0.18f;
            compactVirtualSpectrum[i] = (from + (to - from) * fraction)
                    * (0.34f + 0.66f * (float) Math.sin((i + 0.5f)
                    / compactVirtualSpectrum.length * Math.PI));
        }
        return compactVirtualSpectrum;
    }

    private int effectiveLyricOffsetMs() {
        return AppPreferences.lyricOffsetMs(getContext(), secondary,
                MusicStateStore.activeSourceId());
    }

    private float refinedScaleForOffset(int offset) {
        float value = Math.max(1f - Math.abs(offset) * 0.2f, 0f);
        return value * value * value * 0.3f + 0.7f;
    }

    private void drawRefinedText(Canvas canvas, String value, float anchorX, float y,
                                 float requestedSize, int color, float maxWidth,
                                 Paint.Align align, int style, int alpha) {
        if (value == null || value.isEmpty() || alpha <= 0) return;
        android.graphics.MaskFilter maskFilter = paint.getMaskFilter();
        float size = fitSize(value, requestedSize, maxWidth, style);
        setTextPaint(size, style);
        paint.setMaskFilter(maskFilter);
        paint.setTextAlign(align);
        if (drawSplitSourceMetadata(canvas, value, anchorX, y, maxWidth, align, alpha)) {
            paint.clearShadowLayer();
            return;
        }
        color = resolveMetadataColor(value, color);
        paint.setColor(withAlpha(color, alpha));
        applyRefinedTextEffect(size, color, alpha);
        canvas.drawText(ellipsize(value.replace('\n', ' '), maxWidth), anchorX, y, paint);
        paint.clearShadowLayer();
    }

    private void applyRefinedTextEffect(float size, int color, int alpha) {
        if ("shadow".equals(refinedTextEffect)) {
            paint.setShadowLayer(Math.max(2f, size * 0.16f), 0f, size * 0.10f,
                    Color.argb(Math.min(115, alpha), 0, 0, 0));
        } else if ("glow".equals(refinedTextEffect)) {
            paint.setShadowLayer(Math.max(3f, size * 0.24f), 0f, 0f,
                    withAlpha(color, Math.min(95, alpha)));
        }
    }

    /**
     * 「文字效果」现在是通用外观项（issue #59）：Refined 之外也能选。默认「无」时保持各样式原本的
     * 观感——Refined 看它自己的「当前歌词辉光」开关，其它样式保留一直以来的那点辉光。
     */
    private void applyLyricTextEffect(float size, int color, int alpha) {
        if ("shadow".equals(refinedTextEffect) || "glow".equals(refinedTextEffect)) {
            applyRefinedTextEffect(size, color, alpha);
            return;
        }
        // 默认「无」：Refined / 紧凑还是看它们自己的「当前歌词辉光」，其它样式保留原本那点辉光。
        if (usesRefinedVisualStyle()) {
            if (refinedLyricGlow) {
                paint.setShadowLayer(Math.max(3f, size * 0.24f), 0f, 0f,
                        withAlpha(color, Math.min(90, alpha)));
            }
            return;
        }
        paint.setShadowLayer(Math.max(4f, size * 0.35f), 0f, 0f,
                Color.argb(Math.min(100, alpha), Color.red(color), Color.green(color),
                        Color.blue(color)));
    }

    private void drawRefinedBackground(Canvas canvas, Bitmap art, boolean light,
                                       int accent, boolean playing) {
        int configured = configuredBackgroundColor();
        String type = refinedBackgroundType == null ? "blur" : refinedBackgroundType;
        if ("none".equals(type) && configured == 0) return;
        int layer = saveLayerAlphaCompat(canvas, panelRect,
                fullscreen ? 255 : Math.round(clamp(opacity / 100f) * 255f));
        int save = canvas.save();
        float radius = fullscreen ? 0f : Math.min(getWidth(), getHeight()) * panelCornerRadiusRatio(0.075f);
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(null);

        if (configured != 0) {
            paint.setColor(configured);
            canvas.drawRect(panelRect, paint);
        } else if ("starfield".equals(type)) {
            // 夜幕与星云预渲染在位图里：每帧只贴一次，比「一次线性渐变 + 两次径向渐变」便宜得多，
            // 弱车机上才跟得上帧率（issue #59）。
            paint.setShader(null);
            canvas.drawBitmap(starfieldBackdrop(art), 0f, 0f, paint);
        } else if ("solid".equals(type)) {
            paint.setColor(mix(accent, light ? Color.WHITE : Color.BLACK,
                    light ? 0.78f : 0.72f));
            canvas.drawRect(panelRect, paint);
        } else if ("gradient".equals(type)) {
            float phase = refinedDynamicGradient && playing
                    ? (SystemClock.elapsedRealtime() % 120_000L) / 120_000f : 0.125f;
            float endX = getWidth() * (0.2f + phase * 0.8f);
            float endY = getHeight() * (1f - phase * 0.6f);
            paint.setShader(new LinearGradient(0f, getHeight(), endX, endY,
                    palette, null, Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
        } else if ("fluid".equals(type)) {
            paint.setColor(mix(accent, light ? Color.WHITE : Color.BLACK, 0.62f));
            canvas.drawRect(panelRect, paint);
            if (art != null && !art.isRecycled()) {
                drawBitmapCrop(canvas, blurredPreview(art), panelRect, 105,
                        backgroundBrightnessFilter());
            }
            float phase = refinedStaticFluid || !playing ? 0.23f
                    : (SystemClock.elapsedRealtime() % 150_000L) / 150_000f;
            float radiusValue = Math.max(getWidth(), getHeight()) * 0.72f;
            for (int index = 0; index < 4; index++) {
                double angle = phase * Math.PI * 2d + index * Math.PI / 2d;
                float cx = getWidth() * 0.5f + (float) Math.cos(angle) * getWidth() * 0.33f;
                float cy = getHeight() * 0.5f + (float) Math.sin(angle) * getHeight() * 0.28f;
                paint.setShader(new RadialGradient(cx, cy, radiusValue,
                        withAlpha(palette[index], 205), withAlpha(palette[index], 0),
                        Shader.TileMode.CLAMP));
                canvas.drawRect(panelRect, paint);
            }
        } else if (art != null && !art.isRecycled()) {
            drawBitmapCrop(canvas, blurredPreview(art), panelRect, 255,
                    backgroundBrightnessFilter());
        } else {
            paint.setShader(new LinearGradient(0f, 0f, getWidth(), getHeight(),
                    palette[0], palette[3], Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
        }
        paint.setShader(null);
        // 遮罩 0 就是「不压暗」（issue #58）；这一路原本就尊重 0，这里只是统一三处口径。
        int dim = ArtworkBackgroundMath.refinedMaskAlpha(backgroundDim, styleMaskMode);
        if (dim > 0) {
            paint.setColor(light ? Color.argb(dim, 255, 255, 255)
                    : Color.argb(dim, 0, 0, 0));
            canvas.drawRect(panelRect, paint);
        }
        // 星点画在暗化之后：背景暗化不该把星空一起糊掉（issue #59）。
        if ("starfield".equals(type)) drawStarfield(canvas, art);
        canvas.restoreToCount(save);
        canvas.restoreToCount(layer);
        paint.setShader(null);
        paint.setAlpha(255);
    }

    /**
     * 星空底色：夜幕渐变 + 两团跟随封面取色的星云，预渲染成一张位图（issue #59）。
     *
     * <p>只有面板尺寸或封面调色板变了才重建，之后每帧一次贴图——以前每帧都要新建三个 Shader
     * 并做三次全屏渐变填充，弱车机上光这一项就吃掉了整帧预算。
     */
    private Bitmap starfieldBackdrop(Bitmap art) {
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        boolean tint = starfieldFollowCover && art != null && !art.isRecycled();
        boolean light = refinedUsesLightColors();
        int deep = tint ? mix(palette[0], Color.BLACK, 0.86f) : 0xFF060A14;
        String key = width + "x" + height + ":" + Integer.toHexString(deep) + ":"
                + Integer.toHexString(palette[1]) + ":" + Integer.toHexString(palette[2])
                + ":" + tint + ":" + light;
        if (starfieldBackdrop != null && key.equals(starfieldBackdropKey)
                && !starfieldBackdrop.isRecycled()) {
            return starfieldBackdrop;
        }
        starfieldBackdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        starfieldBackdropKey = key;
        Canvas offscreen = new Canvas(starfieldBackdrop);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(new LinearGradient(0f, 0f, width, height, deep,
                mix(deep, Color.WHITE, light ? 0.10f : 0.05f), Shader.TileMode.CLAMP));
        offscreen.drawRect(0f, 0f, width, height, paint);
        if (tint) {
            for (int index = 0; index < 2; index++) {
                int color = palette[index + 1];
                float cx = width * (index == 0 ? 0.24f : 0.76f);
                float cy = height * (index == 0 ? 0.22f : 0.80f);
                float glow = Math.max(width, height) * 0.55f;
                paint.setShader(new RadialGradient(cx, cy, glow, withAlpha(color, 58),
                        withAlpha(color, 0), Shader.TileMode.CLAMP));
                offscreen.drawRect(0f, 0f, width, height, paint);
            }
        }
        paint.setShader(null);
        return starfieldBackdrop;
    }

    /**
     * 动态星空 / 星尘背景（issue #59）。
     *
     * <p>星点位置由 {@link StarfieldField} 从固定种子算出来，只有闪烁与漂移跟着时钟走：每帧只画
     * {@code count} 个小圆（底色与星云来自缓存位图），不逐帧新建对象，设置页的预览和悬浮窗看到的是
     * 同一片星空。「省电档」把时钟冻住，星点静止。
     */
    private void drawStarfield(Canvas canvas, Bitmap art) {
        float width = getWidth();
        float height = getHeight();
        float density = getResources().getDisplayMetrics().density;
        long elapsed = starfieldStill ? 0L : SystemClock.elapsedRealtime();
        float driftX = StarfieldField.driftX(elapsed, starfieldSpeedPercent, starfieldStill);
        float driftY = StarfieldField.driftY(elapsed, starfieldSpeedPercent, starfieldStill);
        float sizeScale = starfieldSizePercent / 100f;
        float radiusUnit = Math.max(0.8f, density * 0.85f);
        boolean tint = starfieldFollowCover && art != null && !art.isRecycled();
        int count = StarfieldField.starCount(starfieldDensityPercent);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setAlpha(255);
        for (int index = 0; index < count; index++) {
            float x = StarfieldField.positioned(StarfieldField.starX(index, STARFIELD_SEED), driftX);
            float y = StarfieldField.positioned(StarfieldField.starY(index, STARFIELD_SEED), driftY);
            float twinkle = StarfieldField.twinkle(index, STARFIELD_SEED, elapsed, starfieldStill);
            float radius = StarfieldField.starRadius(index, STARFIELD_SEED) * sizeScale * radiusUnit;
            int color = tint ? mix(palette[index % palette.length], Color.WHITE, 0.55f)
                    : 0xFFEAF2FF;
            paint.setColor(withAlpha(color, Math.round(240f * twinkle)));
            canvas.drawCircle(x * width, y * height, radius, paint);
        }
        paint.setAlpha(255);
    }

    private boolean refinedUsesLightColors() {
        if ("light".equals(refinedColorScheme)) return true;
        if ("dark".equals(refinedColorScheme)) return false;
        if ("light".equals(themeMode)) return true;
        if ("dark".equals(themeMode)) return false;
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode != Configuration.UI_MODE_NIGHT_YES;
    }

    /** Overlay lyrics only follow the Material light/dark choice when explicitly enabled. */
    private boolean lyricUsesLightColors() {
        return lyricsFollowTheme && lyricEnvironmentUsesLightColors();
    }

    private boolean lyricEnvironmentUsesLightColors() {
        if (!compactTextOnly) return refinedUsesLightColors();
        if ("light".equals(themeMode)) return true;
        if ("dark".equals(themeMode)) return false;
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode != Configuration.UI_MODE_NIGHT_YES;
    }

    private int refinedAccentColor() {
        if ("off".equals(refinedAccentVariant)) return 0xFF969696;
        if ("secondary".equals(refinedAccentVariant)) return mix(palette[0], palette[1], 0.5f);
        if ("tertiary".equals(refinedAccentVariant)) {
            float[] hsv = new float[3];
            Color.colorToHSV(palette[0], hsv);
            hsv[0] = (hsv[0] + 58f) % 360f;
            hsv[1] = Math.max(0.30f, Math.min(0.78f, hsv[1]));
            hsv[2] = Math.max(0.55f, hsv[2]);
            return Color.HSVToColor(hsv);
        }
        return palette[0];
    }

    private void updatePalette(Bitmap art) {
        if (art == paletteSource) return;
        paletteSource = art;
        if (art == null || art.isRecycled()) return;
        int[] result = new int[6];
        for (int index = 0; index < result.length; index++) {
            int column = index % 3;
            int row = index / 3;
            result[index] = averageRegion(art, column / 3f, row / 2f,
                    (column + 1) / 3f, (row + 1) / 2f);
        }
        for (int left = 0; left < result.length; left++) {
            for (int right = left + 1; right < result.length; right++) {
                if (saturation(result[right]) > saturation(result[left])) {
                    int swap = result[left];
                    result[left] = result[right];
                    result[right] = swap;
                }
            }
        }
        palette = result;
    }

    private static int averageRegion(Bitmap bitmap, float left, float top,
                                     float right, float bottom) {
        long red = 0L, green = 0L, blue = 0L, count = 0L;
        for (int yIndex = 0; yIndex < 6; yIndex++) {
            int y = Math.min(bitmap.getHeight() - 1, Math.max(0,
                    Math.round((top + (bottom - top) * (yIndex + 0.5f) / 6f)
                            * (bitmap.getHeight() - 1))));
            for (int xIndex = 0; xIndex < 6; xIndex++) {
                int x = Math.min(bitmap.getWidth() - 1, Math.max(0,
                        Math.round((left + (right - left) * (xIndex + 0.5f) / 6f)
                                * (bitmap.getWidth() - 1))));
                int color = bitmap.getPixel(x, y);
                if (Color.alpha(color) < 96) continue;
                red += Color.red(color);
                green += Color.green(color);
                blue += Color.blue(color);
                count++;
            }
        }
        if (count == 0L) return 0xFF6F7E89;
        int color = Color.rgb((int) (red / count), (int) (green / count),
                (int) (blue / count));
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0.30f, Math.min(0.80f, hsv[1]));
        hsv[2] = Math.max(0.42f, Math.min(0.84f, hsv[2]));
        return Color.HSVToColor(hsv);
    }

    private static float saturation(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        return hsv[1];
    }

    /** Compact warm layout inspired by PiPWindow. */
    private void drawPip(Canvas canvas, MusicSnapshot snapshot, float density) {
        if (fullscreen) {
            drawPipFullscreen(canvas, snapshot, density);
            return;
        }
        float width = getWidth();
        float height = getHeight();
        float contentScale = canvasAreaScale(440f, 220f, density);
        drawArtworkBackground(canvas, snapshot.albumArt, 0xFFE9DFD0, 0xFFB6A892, false);
        float pad = contentPad(Math.max(13f * density * contentScale, width * 0.035f));
        // 极简样式：无封面时把封面那块收掉，歌名 / 歌手直接靠左（issue #50）。
        boolean hideCover = coverAreaHidden();
        float coverSize = hideCover ? 0f : 70f * density * contentScale * coverScale;
        coverRect.set(pad, pad, pad + coverSize, pad + coverSize);
        RectF cover = coverRect;
        if (!hideCover) {
            drawCoverRotated(canvas, snapshot.albumArt, cover, 9f * density * contentScale,
                    0xFFD2C4B2);
        }

        float metaLeft = hideCover ? pad : cover.right + 13f * density * contentScale;
        float metaWidth = width - metaLeft - pad;
        drawingMetadata = true;
        drawLeft(canvas, snapshot.active ? snapshot.title : "等待音乐", metaLeft,
                cover.top + 20f * density * contentScale,
                16f * density * contentScale * titleScale,
                0xFF25211D, metaWidth, Typeface.BOLD);
        drawLeft(canvas, snapshot.artist, metaLeft,
                cover.top + 40f * density * contentScale,
                11f * density * contentScale * titleScale, 0xB85A5148,
                metaWidth, Typeface.NORMAL);
        if (showPlayerStatus) {
            drawLeft(canvas, snapshot.sourceName + sourceSuffix(snapshot), metaLeft,
                    cover.top + 58f * density * contentScale,
                    9.5f * density * contentScale * titleScale,
                    0x985A5148, metaWidth, Typeface.NORMAL);
        }
        drawingMetadata = false;
        float progressY = Math.max(cover.bottom + 11f * density * contentScale,
                height * 0.38f);
        drawProgress(canvas, pad, progressY, width - pad, 2f * density * contentScale,
                snapshot, 0x405A5148, 0xFF4D453E);

        float lyricY = progressY + 34f * density * contentScale + browseVisualOffsetPx;
        float lyricWidth = width - pad * 2f;
        if (lyricLineCount >= 3) {
            drawLeftDissolving(canvas, snapshot.lyrics.previousLyric, pad, lyricY,
                    12f * density * contentScale * textScale,
                    inactiveLyricColor(0x705A5148), lyricWidth,
                    Typeface.BOLD, LyricDissolveEffect.UNKNOWN_LINE);
            lyricY += 25f * density * contentScale;
        }
        float pipLyricSize = minimalStyleLyricSize(density) * (secondary ? 1.06f : 1f);
        float currentHeight;
        if (snapshot.lyrics.interlude) {
            drawInterludeDots(canvas, snapshot, pad, lyricY - pipLyricSize * 0.55f,
                    pipLyricSize * 0.35f, currentLyricColor(0xFF181513));
            currentHeight = pipLyricSize * 1.22f;
        } else {
            currentHeight = drawWrappedKaraoke(canvas, snapshot, currentText(snapshot), pad,
                    lyricY - pipLyricSize, pipLyricSize, lyricWidth,
                    currentLyricColor(0xFF181513), 2);
        }
        if (!secondaryLyric(snapshot).isEmpty()) {
            drawLeft(canvas, secondaryLyric(snapshot), pad,
                    lyricY - pipLyricSize + currentHeight + 14f * density * contentScale,
                    11f * density * contentScale * textScale,
                    lyricColor(0xA85A5148), lyricWidth,
                    Typeface.NORMAL);
            lyricY += 18f * density * contentScale;
        }
        if (lyricLineCount >= 2) {
            drawLeft(canvas, snapshot.lyrics.nextLyric, pad,
                    lyricY - pipLyricSize + currentHeight + 36f * density * contentScale,
                    nextLyricSize(pipLyricSize),
                    nextLyricColor(inactiveLyricColor(0x985A5148)), lyricWidth,
                    Typeface.BOLD);
        }
    }

    private void drawCustom(Canvas canvas, MusicSnapshot snapshot, float density) {
        drawArtworkBackground(canvas, snapshot.albumArt, 0xFF101822, 0xFF050A10, true);
        float width = getWidth();
        float height = getHeight();
        float contentScale = canvasAreaScale(460f, 260f, density);
        float pad = 10f * density * contentScale;
        for (LyricsLayoutConfig.Item item : layoutConfig.items()) {
            if (!item.enabled) continue;
            float x = clamp(item.x) * width;
            float y = clamp(item.y) * height;
            if (isCustomLyricItem(item.id)) y += browseVisualOffsetPx;
            float maxWidth = Math.max(40f * density, width - x - pad);
            switch (item.id) {
                case LyricsLayoutConfig.COVER:
                    if (coverAreaHidden()) break;
                    float size = 109f * density * contentScale * coverScale;
                    coverRect.set(x, y, x + size, y + size);
                    drawCoverRotated(canvas, snapshot.albumArt, coverRect,
                            12f * density * contentScale, 0xFF293442);
                    break;
                case LyricsLayoutConfig.SOURCE:
                    drawingMetadata = true;
                    if (showPlayerStatus) {
                        drawLeft(canvas, snapshot.sourceName + sourceSuffix(snapshot), x, y,
                                10f * density * contentScale * textScale,
                                0xC86EE7F2, maxWidth, Typeface.BOLD);
                    }
                    drawingMetadata = false;
                    break;
                case LyricsLayoutConfig.TITLE:
                    drawingMetadata = true;
                    drawLeft(canvas, snapshot.active ? snapshot.title : "等待音乐", x, y,
                            17f * density * contentScale * titleScale, Color.WHITE,
                            maxWidth, Typeface.BOLD);
                    drawingMetadata = false;
                    break;
                case LyricsLayoutConfig.ARTIST:
                    drawingMetadata = true;
                    drawLeft(canvas, snapshot.artist, x, y,
                            11f * density * contentScale * titleScale, 0xB8D4DCE7,
                            maxWidth, Typeface.NORMAL);
                    drawingMetadata = false;
                    break;
                case LyricsLayoutConfig.PREVIOUS:
                    drawLeftDissolving(canvas, snapshot.lyrics.previousLyric, x, y,
                            12f * density * contentScale * textScale,
                            lyricColor(0x7FFFFFFF), maxWidth,
                            Typeface.NORMAL, LyricDissolveEffect.UNKNOWN_LINE);
                    break;
                case LyricsLayoutConfig.CURRENT:
                    drawKaraoke(canvas, snapshot, currentText(snapshot), x, y,
                            22f * density * contentScale * textScale,
                            maxWidth, Paint.Align.LEFT,
                            lyricColor(0xFFB1BCCB), lyricColor(0xFFFFCA66));
                    break;
                case LyricsLayoutConfig.TRANSLATION:
                    drawLeft(canvas, secondaryLyric(snapshot), x, y,
                            11f * density * contentScale * textScale,
                            lyricColor(0xB8D4DCE7), maxWidth,
                            Typeface.NORMAL);
                    break;
                case LyricsLayoutConfig.NEXT:
                    drawLeft(canvas, snapshot.lyrics.nextLyric, x, y,
                            nextLyricSize(22f * density * contentScale * textScale),
                            nextLyricColor(lyricColor(0x7FFFFFFF)), maxWidth,
                            Typeface.NORMAL);
                    break;
                case LyricsLayoutConfig.PROGRESS:
                    drawProgress(canvas, x, y, Math.min(width - pad, x + width * 0.38f),
                            3f * density * contentScale,
                            snapshot, 0x48FFFFFF, 0xFFFFCA66);
                    break;
                default:
                    break;
            }
        }
    }

    private static boolean isCustomLyricItem(String itemId) {
        return LyricsLayoutConfig.PREVIOUS.equals(itemId)
                || LyricsLayoutConfig.CURRENT.equals(itemId)
                || LyricsLayoutConfig.TRANSLATION.equals(itemId)
                || LyricsLayoutConfig.NEXT.equals(itemId);
    }

    /**
     * 面板圆角（issue #37）。
     *
     * <p>用户设置的是"占面板短边的百分比"，{@code 0} 就是直角矩形——把「背景不透明度」拉到 100%
     * 时面板就是一块实心色板，可以完全盖住后面的原车界面。没有设置过（-1）时返回样式原本的取值，
     * 所以升级后观感不变。各样式原本的口径不同（经典是 dp，其余是短边比例），这里统一成比例，
     * 于是同一个设置在任何样式下都是"同样的圆角观感"。
     */
    private float panelCornerRadius(float styleRadiusPx) {
        if (cornerRadiusPercent < 0) return styleRadiusPx;
        return Math.min(getWidth(), getHeight()) * cornerRadiusPercent / 100f;
    }

    /** 比例口径的样式：{@code styleRatio} 是它原本占短边的比例。 */
    private float panelCornerRadiusRatio(float styleRatio) {
        return cornerRadiusPercent < 0 ? styleRatio : cornerRadiusPercent / 100f;
    }

    /**
     * 面板边缘阴影（issue #56）：向内柔化的圆角描边，位图化后每帧只贴一次。
     *
     * <p>不用 {@code setShadowLayer}：硬件画布在 API 28 以下不认形状的阴影，改成软件渲染又会把整块
     * 面板拖给 CPU（#59 刚封掉那条路）。悬浮窗只有 1px 余量，向外扩散的光晕也会被窗口裁掉，所以做成
     * 向内的柔和边缘。
     */
    private void drawPanelEdgeShadow(Canvas canvas) {
        int percent = panelShadowPercent;
        if (fullscreen || opacity <= 0 || !PanelShadowMath.enabled(percent)) return;
        float density = getResources().getDisplayMetrics().density;
        float radius = panelCornerRadius(Math.min(getWidth(), getHeight())
                * panelCornerRadiusRatio(0.075f));
        Bitmap halo = panelShadowHalo(percent, radius, density);
        if (halo == null) return;
        paint.setShader(null);
        paint.setMaskFilter(null);
        canvas.drawBitmap(halo, 0f, 0f, paint);
    }

    private Bitmap panelShadowHalo(int percent, float cornerRadius, float density) {
        float blur = PanelShadowMath.blurRadiusPx(percent, density);
        int width = Math.max(1, getWidth());
        int height = Math.max(1, getHeight());
        String key = width + "x" + height + ":" + Math.round(cornerRadius) + ":"
                + Math.round(blur) + ":" + percent;
        if (panelShadowHaloBitmap != null && key.equals(panelShadowHaloKey)
                && !panelShadowHaloBitmap.isRecycled()) {
            return panelShadowHaloBitmap;
        }
        panelShadowHaloBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        panelShadowHaloKey = key;
        Canvas offscreen = new Canvas(panelShadowHaloBitmap);
        float inset = blur;
        workRect.set(inset, inset, width - inset, height - inset);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, blur * 2f));
        paint.setMaskFilter(blurMask(Math.max(1f, blur * 0.9f)));
        paint.setShader(null);
        paint.setColor(Color.argb(PanelShadowMath.alpha(percent), 0, 0, 0));
        float radius = Math.max(0f, cornerRadius - inset);
        offscreen.drawRoundRect(workRect, radius, radius, paint);
        paint.setMaskFilter(null);
        paint.setStyle(Paint.Style.FILL);
        return panelShadowHaloBitmap;
    }

    private void drawPanelShadow(Canvas canvas, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        int configured = configuredBackgroundColor();
        paint.setColor(configured == 0 ? color : withAlpha(configured, Color.alpha(color)));
        radius = panelCornerRadius(radius);
        paint.setShadowLayer(radius * 0.75f, 0f, radius * 0.25f, 0x70000000);
        canvas.drawRoundRect(panelRect, radius, radius, paint);
        paint.clearShadowLayer();
    }

    private void drawArtworkBackground(Canvas canvas, Bitmap art, int fallbackA, int fallbackB,
                                       boolean dark) {
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        float radius = fullscreen ? 0f : Math.min(getWidth(), getHeight()) * panelCornerRadiusRatio(0.075f);
        int alphaLayer = saveLayerAlphaCompat(canvas, panelRect,
                fullscreen ? 255 : Math.round(clamp(opacity / 100f) * 255f));
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        int configured = configuredBackgroundColor();
        if (configured != 0) {
            paint.setShader(null);
            paint.setColor(configured);
            canvas.drawRect(panelRect, paint);
        } else if (art != null && !art.isRecycled()) {
            Bitmap preview = blurredPreview(art);
            drawBitmapCrop(canvas, preview, panelRect);
        } else {
            paint.setShader(new LinearGradient(0f, 0f, getWidth(), getHeight(), fallbackA,
                    fallbackB, Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
            paint.setShader(null);
        }
        // 遮罩 0 就是「不压暗」（issue #58）；默认 38 时的下限与改动前一致。
        int dim = ArtworkBackgroundMath.pipMaskAlpha(backgroundDim, dark, styleMaskMode);
        if (dim > 0) {
            paint.setColor(ArtworkBackgroundMath.pipMaskColor(backgroundDim, dark, styleMaskMode));
            canvas.drawRect(panelRect, paint);
        }
        canvas.restoreToCount(save);
        canvas.restoreToCount(alphaLayer);
        paint.setShader(null);
    }

    private int configuredBackgroundColor() {
        return lyricEnvironmentUsesLightColors()
                ? AppPreferences.backgroundLightColor(getContext(), secondary)
                : AppPreferences.backgroundDarkColor(getContext(), secondary);
    }

    private Bitmap blurredPreview(Bitmap art) {
        if (art == blurSource && blurPreview != null && !blurPreview.isRecycled()) return blurPreview;
        recycleBlurPreview();
        blurSource = art;
        // A tiny thumbnail scaled back up is fast but turns smooth cover artwork into a
        // visible checkerboard. Keep a reasonably dense working image and blur that image
        // once when artwork/settings change; onDraw then only samples the cached bitmap.
        int sourceLongestSide = Math.max(art.getWidth(), art.getHeight());
        int targetLongestSide = Math.max(144,
                Math.min(512, Math.round(512f - backgroundBlur * 2.8f)));
        float scale = Math.min(1f, targetLongestSide / (float) sourceLongestSide);
        int width = Math.max(2, Math.round(art.getWidth() * scale));
        int height = Math.max(2, Math.round(art.getHeight() * scale));
        Bitmap working = Bitmap.createScaledBitmap(art, width, height, true);
        int radius = Math.round(backgroundBlur * 0.12f);
        int passes = Math.min(3, Math.max(0, Math.round(backgroundBlur / 43f)));
        blurPreview = radius > 0 && passes > 0
                ? boxBlur(working, radius, passes) : working;
        if (working != blurPreview && working != art && !working.isRecycled()) {
            working.recycle();
        }
        return blurPreview;
    }

    private void recycleBlurPreview() {
        if (blurPreview != null && blurPreview != blurSource && !blurPreview.isRecycled()) {
            blurPreview.recycle();
        }
    }

    /** Port of PiPWindow's height-relative Canvas drawing routine. */
    private void drawPipFullscreen(Canvas canvas, MusicSnapshot snapshot, float density) {
        float width = getWidth();
        float r = getHeight();
        updatePalette(snapshot.albumArt);
        drawArtworkBackground(canvas, snapshot.albumArt, 0xFFE9DFD0, 0xFFB6A892, false);

        float o2 = r / 240f;
        float o3 = r / 160f;
        float o5 = r / 96f;
        float o6 = r / 80f;
        float o9 = r / 53.3333f;
        float o10 = r / 48f;
        float o12 = r / 40f;
        float o15 = r / 32f;
        float o20 = r / 24f;
        float o21p5 = r / 22.3256f;
        float o25 = r / 19.2f;
        float o30 = r / 16f;
        float o30p5 = r / 15.7377f;
        float o35 = r / 13.7143f;
        float o45 = r / 10.6667f;
        float o55 = r / 8.7272f;
        float o60 = r / 8f;
        float o105 = r / 4.57143f;
        float o150 = r / 3.2f;
        float coverSize = coverAreaHidden() ? 0f : r / 3f * coverScale;
        float textLeft = coverSize > 0f ? coverSize + o10 : o10;
        int text = lyricColor(0xFF25211D);
        int text56 = lyricColor(withAlpha(text, 143));
        int text42 = lyricColor(withAlpha(text, 107));
        int text31 = lyricColor(withAlpha(text, 79));

        coverRect.set(0f, 0f, coverSize, coverSize);
        if (coverSize > 0f) {
            drawCoverRotated(canvas, snapshot.albumArt, coverRect, o12, 0xFFD2C4B2);
        }
        drawingMetadata = true;
        drawLeft(canvas, snapshot.active ? snapshot.title : "等待音乐", textLeft, o60,
                o55 * titleScale, text, Math.max(1f, width - textLeft - o15), Typeface.NORMAL);
        drawLeft(canvas, snapshot.artist, textLeft, o105,
                o35 * titleScale, text56, Math.max(1f, width - textLeft - o15),
                Typeface.NORMAL);
        drawingMetadata = false;

        String time = formatClock(snapshot.positionMs) + " / " + formatClock(snapshot.durationMs);
        setTextPaint(o30, Typeface.NORMAL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(text56);
        canvas.drawText(time, o15, coverSize + o35, paint);
        float progressLeft = o15 + paint.measureText(time) + o30p5;
        drawProgress(canvas, progressLeft, coverSize + o21p5, width,
                o5, snapshot, withAlpha(text, 33), mix(palette[0], text, 0.35f));

        float lyricTop = coverSize + o45;
        float lyricSize = o55 * textScale;
        float[] lineY = new float[]{
                lyricTop + lyricSize,
                lyricTop + lyricSize * 2f + o10,
                lyricTop + lyricSize * 3f + o12,
                lyricTop + lyricSize * 4f + o10,
                lyricTop + lyricSize * 5f + o2
        };
        drawPipSourceKaraoke(canvas, snapshot, currentText(snapshot), o15, lineY[0],
                lyricSize, Math.max(1f, width - o15), text42, text, o150);

        String currentTranslation = secondaryLyric(snapshot);
        if (!currentTranslation.isEmpty()) {
            drawPipSourceLine(canvas, currentTranslation, o15, lineY[1] - o10,
                    lyricSize - o5, text56, width - o15);
            drawPipSourceLine(canvas, nearbyLine(snapshot, 1, false), o12, lineY[2],
                    lyricSize - o10, text56, width - o12);
            String nextTranslation = nearbyLine(snapshot, 1, true);
            if (!nextTranslation.isEmpty()) {
                drawPipSourceLine(canvas, nextTranslation, o12, lineY[3] - o10,
                        lyricSize - o15, text31, width - o12);
                drawPipSourceLine(canvas, nearbyLine(snapshot, 2, false), o9, lineY[4],
                        lyricSize - o15, text56, width - o9);
            } else {
                drawPipSourceLine(canvas, nearbyLine(snapshot, 2, false), o9, lineY[3],
                        lyricSize - o15, text56, width - o9);
                drawPipSourceLine(canvas, nearbyLine(snapshot, 3, false), o6, lineY[4],
                        lyricSize - o20, text56, width - o6);
            }
        } else {
            drawPipSourceLine(canvas, nearbyLine(snapshot, 1, false), o12, lineY[1],
                    lyricSize - o10, text56, width - o12);
            String nextTranslation = nearbyLine(snapshot, 1, true);
            if (!nextTranslation.isEmpty()) {
                drawPipSourceLine(canvas, nextTranslation, o12, lineY[2] - o10,
                        lyricSize - o15, text31, width - o12);
                drawPipSourceLine(canvas, nearbyLine(snapshot, 2, false), o9, lineY[3],
                        lyricSize - o15, text56, width - o9);
                drawPipSourceLine(canvas, nearbyLine(snapshot, 3, false), o6, lineY[4],
                        lyricSize - o20, text56, width - o6);
            } else {
                drawPipSourceLine(canvas, nearbyLine(snapshot, 2, false), o9, lineY[2],
                        lyricSize - o15, text56, width - o9);
                drawPipSourceLine(canvas, nearbyLine(snapshot, 3, false), o6, lineY[3],
                        lyricSize - o20, text56, width - o6);
                drawPipSourceLine(canvas, nearbyLine(snapshot, 4, false), o3, lineY[4],
                        lyricSize - o25, text56, width - o3);
            }
        }
    }

    private void drawPipSourceKaraoke(Canvas canvas, MusicSnapshot snapshot, String value,
                                      float left, float baseline, float size, float maxWidth,
                                      int baseColor, int activeColor, float lookAhead) {
        if (value == null || value.isEmpty()) return;
        String text = value.replace('\n', ' ');
        setTextPaintForValue(size, Typeface.BOLD, value);
        paint.setTextAlign(Paint.Align.LEFT);
        float textWidth = paint.measureText(text);
        float highlighted = snapshot.lyrics.wordTimed
                ? karaokeHighlightWidth(text, snapshot.lyrics) : textWidth;
        float drawLeft = left;
        if (textWidth > maxWidth && highlighted + lookAhead > maxWidth) {
            drawLeft = getWidth() - highlighted - lookAhead;
        }
        int save = canvas.save();
        canvas.clipRect(0f, baseline - size * 1.25f,
                getWidth(), baseline + size * 0.35f);
        paint.setColor(baseColor);
        canvas.drawText(text, drawLeft, baseline, paint);
        int highlightSave = canvas.save();
        canvas.clipRect(drawLeft, baseline - size * 1.25f,
                drawLeft + Math.max(0f, highlighted), baseline + size * 0.35f);
        paint.setColor(activeColor);
        canvas.drawText(text, drawLeft, baseline, paint);
        canvas.restoreToCount(highlightSave);
        canvas.restoreToCount(save);
    }

    private void drawPipSourceLine(Canvas canvas, String value, float left, float baseline,
                                   float size, int color, float right) {
        if (value == null || value.isEmpty() || size <= 0f) return;
        int save = canvas.save();
        canvas.clipRect(left, baseline - size * 1.25f, right, baseline + size * 0.35f);
        setTextPaintForValue(size, Typeface.BOLD, value);
        paint.setTextAlign(Paint.Align.LEFT);
        // Despite the name this draws the surrounding lyric lines, never the source metadata.
        paint.setColor(color);
        canvas.drawText(value.replace('\n', ' '), left, baseline, paint);
        canvas.restoreToCount(save);
    }

    private static String secondaryLyric(MusicSnapshot snapshot) {
        return snapshot.lyrics.translatedLyric.isEmpty()
                ? snapshot.lyrics.romajiLyric : snapshot.lyrics.translatedLyric;
    }

    private static String secondaryLyric(LrcTimeline.NearbyLine line) {
        return line.translated.isEmpty() ? line.romaji : line.translated;
    }

    private static String nearbyLine(MusicSnapshot snapshot, int offset, boolean translated) {
        for (LrcTimeline.NearbyLine line : snapshot.lyrics.nearbyLines) {
            if (line.offset == offset) return translated ? secondaryLyric(line) : line.text;
        }
        return offset == 1 && !translated ? snapshot.lyrics.nextLyric : "";
    }

    private static String formatClock(long milliseconds) {
        long seconds = Math.max(0L, milliseconds) / 1_000L;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60L, seconds % 60L);
    }

    /**
     * A separable box blur applied to a small cached bitmap. Repeating it produces a smooth
     * Gaussian-like background without asking the whole View to enter a software layer.
     */
    private static Bitmap boxBlur(Bitmap source, int radius, int passes) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        int[] scratch = new int[pixels.length];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int pass = 0; pass < passes; pass++) {
            boxBlurHorizontal(pixels, scratch, width, height, radius);
            boxBlurVertical(scratch, pixels, width, height, radius);
        }
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    private static void boxBlurHorizontal(int[] source, int[] destination, int width,
                                          int height, int radius) {
        int window = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            long alpha = 0L, red = 0L, green = 0L, blue = 0L;
            for (int x = -radius; x <= radius; x++) {
                int color = source[row + Math.max(0, Math.min(width - 1, x))];
                alpha += color >>> 24;
                red += (color >>> 16) & 0xFF;
                green += (color >>> 8) & 0xFF;
                blue += color & 0xFF;
            }
            for (int x = 0; x < width; x++) {
                destination[row + x] = Color.argb((int) (alpha / window),
                        (int) (red / window), (int) (green / window), (int) (blue / window));
                int removed = source[row + Math.max(0, x - radius)];
                int added = source[row + Math.min(width - 1, x + radius + 1)];
                alpha += (added >>> 24) - (removed >>> 24);
                red += ((added >>> 16) & 0xFF) - ((removed >>> 16) & 0xFF);
                green += ((added >>> 8) & 0xFF) - ((removed >>> 8) & 0xFF);
                blue += (added & 0xFF) - (removed & 0xFF);
            }
        }
    }

    private static void boxBlurVertical(int[] source, int[] destination, int width,
                                        int height, int radius) {
        int window = radius * 2 + 1;
        for (int x = 0; x < width; x++) {
            long alpha = 0L, red = 0L, green = 0L, blue = 0L;
            for (int y = -radius; y <= radius; y++) {
                int color = source[Math.max(0, Math.min(height - 1, y)) * width + x];
                alpha += color >>> 24;
                red += (color >>> 16) & 0xFF;
                green += (color >>> 8) & 0xFF;
                blue += color & 0xFF;
            }
            for (int y = 0; y < height; y++) {
                destination[y * width + x] = Color.argb((int) (alpha / window),
                        (int) (red / window), (int) (green / window), (int) (blue / window));
                int removed = source[Math.max(0, y - radius) * width + x];
                int added = source[Math.min(height - 1, y + radius + 1) * width + x];
                alpha += (added >>> 24) - (removed >>> 24);
                red += ((added >>> 16) & 0xFF) - ((removed >>> 16) & 0xFF);
                green += ((added >>> 8) & 0xFF) - ((removed >>> 8) & 0xFF);
                blue += (added & 0xFF) - (removed & 0xFF);
            }
        }
    }

    @Override protected void onDetachedFromWindow() {
        // 排给动态背景的 vsync 回调必须撤掉，否则视图已经不在窗口上还会被唤醒一次。
        Choreographer.getInstance().removeFrameCallback(backgroundFrameCallback);
        backgroundFramePosted = false;
        blurredLineCache.evictAll();
        blurScratch = null;
        starfieldBackdrop = null;
        starfieldBackdropKey = "";
        recycleBlurPreview();
        blurPreview = null;
        blurSource = null;
        paletteSource = null;
        clearTextCaches();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (width != oldWidth || height != oldHeight) clearTextCaches();
        super.onSizeChanged(width, height, oldWidth, oldHeight);
    }

    /**
     * 碟片旋转（issue #22）：圆形封面按播放进度转，暂停就停、拖进度会跟着跳。
     *
     * <p>角度由播放位置推导而不是系统时钟，所以暂停后不会继续转、seek 之后也不会突然跳到别的角度。
     * 只有圆形封面（半径达到短边一半）才转；方形/圆角封面照常绘制。转速取「转一圈的秒数」，
     * 默认 20 秒 ≈ 3 转/分，接近黑胶的 33 转手感。
     */
    private void drawCoverRotated(Canvas canvas, Bitmap bitmap, RectF destination, float radius,
                                  int fallbackColor) {
        float degrees = coverRotationDegrees(destination, radius);
        if (degrees == 0f) {
            drawCover(canvas, bitmap, destination, radius, fallbackColor);
            return;
        }
        int save = canvas.save();
        canvas.rotate(degrees, destination.centerX(), destination.centerY());
        drawCover(canvas, bitmap, destination, radius, fallbackColor);
        canvas.restoreToCount(save);
    }

    private float coverRotationDegrees(RectF destination, float radius) {
        if (!coverRotation || framePositionMs < 0L) return 0f;
        // 半径没到短边一半就不是圆形，转起来只会露出空角。
        if (radius < Math.min(destination.width(), destination.height()) * 0.5f - 0.5f) return 0f;
        long periodMs = Math.max(3_000L, coverRotationPeriodSeconds * 1_000L);
        return (framePositionMs % periodMs) * 360f / periodMs;
    }

    private void drawCover(Canvas canvas, Bitmap bitmap, RectF destination, float radius,
                           int fallbackColor) {
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(destination, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        if (bitmap == null || bitmap.isRecycled()) {
            paint.setShader(new LinearGradient(destination.left, destination.top,
                    destination.right, destination.bottom, fallbackColor,
                    lighten(fallbackColor, 34), Shader.TileMode.CLAMP));
            canvas.drawRect(destination, paint);
            paint.setShader(null);
            setTextPaint(Math.min(destination.width(), destination.height()) * 0.24f,
                    Typeface.BOLD);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(0x75FFFFFF);
            canvas.drawText("♪", destination.centerX(),
                    destination.centerY() - (paint.ascent() + paint.descent()) / 2f, paint);
        } else {
            drawBitmapCrop(canvas, bitmap, destination);
        }
        canvas.restoreToCount(save);
    }

    private void drawBitmapCrop(Canvas canvas, Bitmap bitmap, RectF destination) {
        drawBitmapCrop(canvas, bitmap, destination, 255);
    }

    private void drawBitmapCrop(Canvas canvas, Bitmap bitmap, RectF destination, int alpha) {
        drawBitmapCrop(canvas, bitmap, destination, alpha, null);
    }

    /**
     * 背景亮度（issue #58）：只在非 0 时返回滤镜，默认一像素都不改。滤镜对象缓存，不在 onDraw 里 new。
     */
    private android.graphics.ColorFilter backgroundBrightnessFilter() {
        int percent = ArtworkBackgroundMath.normalizeBrightness(styleBrightnessPercent);
        if (!ArtworkBackgroundMath.hasBrightness(percent)) return null;
        if (backgroundBrightnessFilter != null && backgroundBrightnessPercent == percent) {
            return backgroundBrightnessFilter;
        }
        float[] parts = ArtworkBackgroundMath.brightnessMatrixParts(percent);
        float scale = parts[0];
        float translate = parts[1];
        android.graphics.ColorMatrix matrix = new android.graphics.ColorMatrix(new float[]{
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f});
        backgroundBrightnessFilter = new android.graphics.ColorMatrixColorFilter(matrix);
        backgroundBrightnessPercent = percent;
        return backgroundBrightnessFilter;
    }

    private void drawBitmapCrop(Canvas canvas, Bitmap bitmap, RectF destination, int alpha,
                                android.graphics.ColorFilter filter) {
        float sourceRatio = bitmap.getWidth() / (float) bitmap.getHeight();
        float targetRatio = destination.width() / Math.max(1f, destination.height());
        if (sourceRatio > targetRatio) {
            int cropWidth = Math.round(bitmap.getHeight() * targetRatio);
            int left = (bitmap.getWidth() - cropWidth) / 2;
            sourceRect.set(left, 0, left + cropWidth, bitmap.getHeight());
        } else {
            int cropHeight = Math.round(bitmap.getWidth() / targetRatio);
            int top = (bitmap.getHeight() - cropHeight) / 2;
            sourceRect.set(0, top, bitmap.getWidth(), top + cropHeight);
        }
        paint.setShader(null);
        paint.setColorFilter(filter);
        paint.setAlpha(Math.max(0, Math.min(255, alpha)));
        canvas.drawBitmap(bitmap, sourceRect, destination, paint);
        paint.setAlpha(255);
        paint.setColorFilter(null);
    }

    private void drawProgress(Canvas canvas, float left, float top, float right, float height,
                              MusicSnapshot snapshot, int trackColor, int activeColor) {
        if (!showProgress || right <= left) return;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setColor(trackColor);
        progressRect.set(left, top, right, top + height);
        canvas.drawRoundRect(progressRect, height, height, paint);
        float progress = snapshot.durationMs > 0L
                ? clamp(snapshot.positionMs / (float) snapshot.durationMs) : 0f;
        paint.setColor(activeColor);
        progressRect.right = left + (right - left) * progress;
        canvas.drawRoundRect(progressRect, height, height, paint);
    }

    private String currentText(MusicSnapshot snapshot) {
        if (!snapshot.active) return "等待播放";
        if (!snapshot.lyricLoaded && !snapshot.lyricAvailable) {
            // 匹配动画出现后这个位置显示歌名，匹配进度交给底部的发散小点（issue #45）；
            // 动画关闭或还没到延迟时，仍然是原来的静止文字。
            if (matchingIndicatorVisible(SystemClock.elapsedRealtime()) && !snapshot.title.isEmpty()) {
                return snapshot.title;
            }
            return "正在匹配歌词…";
        }
        // 「无歌词时不要占位文案」（issue #67）：只在确定没有可用歌词时留白；匹配中的文案保留。
        if (AppPreferences.hideNoLyricPlaceholder(getContext(), secondary) && !snapshot.lyricAvailable) {
            return "";
        }
        if (!snapshot.lyricAvailable) return "暂无匹配歌词";
        if (snapshot.lyrics.interlude) return "♪  ·  ·  ·";
        if (snapshot.lyrics.lyric.isEmpty()) return "即将开始";
        return snapshot.lyrics.lyric;
    }

    private String sourceSuffix(MusicSnapshot snapshot) {
        return snapshot.lyricSourceName.isEmpty() ? "" : "  ·  " + snapshot.lyricSourceName;
    }

    private void drawBrowseIndicator(Canvas canvas, MusicSnapshot snapshot, float density) {
        long seconds = Math.max(0L, browsePositionMs) / 1_000L;
        String label = String.format(Locale.ROOT, "浏览歌词  %d:%02d  ·  松手后返回",
                seconds / 60L, seconds % 60L);
        setTextPaint(10.5f * density, Typeface.BOLD);
        float horizontal = 9f * density;
        float height = 25f * density;
        float width = paint.measureText(label) + horizontal * 2f;
        float right = getWidth() - 10f * density;
        workRect.set(right - width, 9f * density, right, 9f * density + height);
        paint.setColor(0xA8141B24);
        canvas.drawRoundRect(workRect, height / 2f, height / 2f, paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(0xE8FFFFFF);
        canvas.drawText(label, workRect.centerX(), workRect.centerY()
                - (paint.ascent() + paint.descent()) / 2f, paint);
    }

    private void drawInterludeDots(Canvas canvas, MusicSnapshot snapshot, float x, float top,
                                   float radius, int color) {
        LrcTimeline.At at = snapshot.lyrics;
        long elapsed = Math.max(0L, snapshot.positionMs + lyricOffsetMs - at.lineStartMs);
        paint.setStyle(Paint.Style.FILL);
        float gap = radius * (24f / 7f);
        int save = canvas.save();
        float breath = RefinedInterludeAnimation.breathScale(elapsed);
        canvas.scale(breath, breath, x, top + radius);
        for (int i = 0; i < 3; i++) {
            RefinedInterludeAnimation.DotState state =
                    RefinedInterludeAnimation.dotState(elapsed, at.lineDurationMs, i);
            paint.setColor(withAlpha(color,
                    Math.round(Color.alpha(color) * state.opacity)));
            float dotRadius = radius * state.scale;
            canvas.drawCircle(x + radius + i * gap, top + radius, dotRadius, paint);
        }
        canvas.restoreToCount(save);
    }

    private static float interludeDotsWidth(float radius) {
        return radius * 2f + radius * (24f / 7f) * 2f;
    }

    private float drawWrappedKaraoke(Canvas canvas, MusicSnapshot snapshot, String value,
                                      float x, float top, float size, float maxWidth,
                                      int activeColor, int maxLines) {
        if (value == null || value.isEmpty()) return 0f;
        setTextPaint(size, Typeface.BOLD);
        String plain = value.replace('\n', ' ');
        List<WrappedChunk> chunks = wrapText(plain, maxWidth, maxLines);
        float lineHeight = size * 1.22f;
        LrcTimeline.At at = snapshot.lyrics;
        float estimatedWidth = -1f;
        if (!snapshot.lyricAvailable || at.lyric.isEmpty()) {
            at = LrcTimeline.At.EMPTY;
        } else if (!at.wordTimed) {
            at = null;
            // 没有逐字时间轴：估算整句高亮宽度，分块渲染时再按块偏移裁剪（issue #21）。
            estimatedWidth = estimatedKaraokeWidth(plain, snapshot);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < chunks.size(); i++) {
            WrappedChunk chunk = chunks.get(i);
            float baseline = top + size + i * lineHeight;
            float chunkWidth = paint.measureText(chunk.text);
            // 逐字歌词及时擦除: the sung part of this chunk is withheld, then repainted by
            // drawSungGhostChunk() while it comes apart.
            float sung = Math.min(chunkWidth, sungWidthInChunk(chunk, snapshot.lyrics.lineStartMs));
            int eraseClip = -1;
            if (sung > 0f) {
                if (sung >= chunkWidth) {
                    drawSungGhostChunk(canvas, value, chunk, x, baseline, size, activeColor, snapshot.lyrics.lineStartMs);
                    continue;
                }
                eraseClip = canvas.save();
                canvas.clipRect(x + sung, baseline - size * 1.18f, x + chunkWidth,
                        baseline + size * 0.30f);
            }
            drawTrailingGlowInChunk(canvas, at, chunk, x, baseline, size, activeColor);
            drawLyricText(canvas, chunk.text, x, baseline, size, withAlpha(activeColor, 105));
            float activeWidth;
            if (at != null) {
                activeWidth = karaokeHighlightWidth(chunk, at);
            } else if (estimatedWidth >= 0f) {
                float chunkOffset = chunk.start <= 0 ? 0f
                        : paint.measureText(plain, 0, Math.min(plain.length(), chunk.start));
                activeWidth = Math.max(0f, Math.min(chunkWidth, estimatedWidth - chunkOffset));
            } else {
                activeWidth = chunkWidth;
            }
            if (activeWidth <= sung) {
                if (eraseClip >= 0) canvas.restoreToCount(eraseClip);
                drawSungGhostChunk(canvas, value, chunk, x, baseline, size, activeColor, snapshot.lyrics.lineStartMs);
                continue;
            }
            int save = canvas.save();
            canvas.clipRect(x + Math.max(0f, sung), baseline - size * 1.18f,
                    x + activeWidth, baseline + size * 0.30f);
            drawLyricOutline(canvas, chunk.text, x, baseline, size, activeColor, true);
            paint.setColor(activeColor);
            applyLyricTextEffect(size, activeColor, 255);
            canvas.drawText(chunk.text, x, baseline, paint);
            paint.clearShadowLayer();
            canvas.restoreToCount(save);
            if (eraseClip >= 0) canvas.restoreToCount(eraseClip);
            drawSungGhostChunk(canvas, value, chunk, x, baseline, size, activeColor, snapshot.lyrics.lineStartMs);
        }
        return chunks.size() * lineHeight;
    }

    private float drawAmllWrappedKaraoke(Canvas canvas, MusicSnapshot snapshot, String value,
                                         float x, float top, float size, float maxWidth,
                                         int maxLines, int activeColor) {
        if (value == null || value.isEmpty()) return 0f;
        setTextPaint(size, Typeface.BOLD);
        List<WrappedChunk> chunks = wrapText(value.replace('\n', ' '), maxWidth, maxLines);
        float lineHeight = size * 1.22f;
        LrcTimeline.At at = snapshot.lyrics;
        int completedEnd;
        KaraokeProgress.Boundary boundary;
        if (!snapshot.lyricAvailable || at.lyric.isEmpty() || !at.wordTimed) {
            completedEnd = value.length();
            boundary = KaraokeProgress.Boundary.EMPTY;
        } else {
            completedEnd = Math.min(value.length(), at.completedLyric.length());
            boundary = KaraokeProgress.boundary(
                    at.currentWord, at.wordProgressPermille);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < chunks.size(); i++) {
            WrappedChunk chunk = chunks.get(i);
            float baseline = top + size + i * lineHeight;
            float chunkWidth = paint.measureText(chunk.text);
            // 逐字歌词及时擦除: the sung part of this chunk is withheld, including the
            // completed-word highlight that would otherwise paint it back in.
            float sung = Math.min(chunkWidth, sungWidthInChunk(chunk, snapshot.lyrics.lineStartMs));
            if (sung >= chunkWidth && sung > 0f) {
                drawSungGhostChunk(canvas, value, chunk, x, baseline, size, activeColor, snapshot.lyrics.lineStartMs);
                continue;
            }
            int eraseClip = -1;
            if (sung > 0f) {
                eraseClip = canvas.save();
                canvas.clipRect(x + sung, baseline - size * 1.18f, x + chunkWidth,
                        baseline + size * 0.30f);
            }
            drawTrailingGlowInChunk(canvas, at, chunk, x, baseline, size, activeColor);
            drawLyricText(canvas, chunk.text, x, baseline, size, withAlpha(activeColor, 76));
            drawAmllHighlightRange(canvas, chunk, x, baseline, size,
                    0, completedEnd, completedEnd, 0f,
                    activeColor, 235, 0f, false);
            if (eraseClip >= 0) canvas.restoreToCount(eraseClip);
            drawAmllHighlightRange(canvas, chunk, x, baseline, size,
                    completedEnd,
                    Math.min(value.length(), completedEnd + boundary.completeEnd),
                    Math.min(value.length(), completedEnd + boundary.partialEnd),
                    boundary.partialFraction, activeColor, 255, 0f, true);
            drawSungGhostChunk(canvas, value, chunk, x, baseline, size, activeColor, snapshot.lyrics.lineStartMs);
        }
        return chunks.size() * lineHeight;
    }

    private void drawAmllHighlightRange(Canvas canvas, WrappedChunk chunk, float x,
                                        float baseline, float size, int rangeStart,
                                        int completeEnd, int partialEnd, float partialFraction,
                                        int color, int alpha, float lift,
                                        boolean glow) {
        float startWidth = textWidthToGlobalIndex(chunk, rangeStart);
        float endWidth = textWidthToGlobalIndex(chunk, completeEnd);
        if (partialFraction > 0f && partialEnd > completeEnd) {
            float partialWidth = textWidthToGlobalIndex(chunk, partialEnd);
            endWidth += (partialWidth - endWidth) * partialFraction;
        }
        if (endWidth <= startWidth) return;
        int save = canvas.save();
        canvas.clipRect(x + startWidth - 1f, baseline - size * 1.45f,
                x + endWidth + 1f, baseline + size * 0.38f);
        drawLyricOutline(canvas, chunk.text, x, baseline - lift, size, color, true);
        paint.setColor(withAlpha(color, alpha));
        if (glow) {
            paint.setShadowLayer(Math.max(2f, size * 0.18f), 0f, -lift * 0.25f,
                    withAlpha(color, 118));
        }
        canvas.drawText(chunk.text, x, baseline - lift, paint);
        paint.clearShadowLayer();
        canvas.restoreToCount(save);
    }

    private float drawWrappedText(Canvas canvas, String value, float x, float top, float size,
                                  int color, float maxWidth, int style, int maxLines) {
        if (value == null || value.isEmpty()) return 0f;
        android.graphics.MaskFilter maskFilter = paint.getMaskFilter();
        setTextPaintForValue(size, style, value);
        paint.setMaskFilter(maskFilter);
        paint.setTextAlign(Paint.Align.LEFT);
        int resolved = resolveMetadataColor(value, color);
        List<WrappedChunk> chunks = wrapText(value.replace('\n', ' '), maxWidth, maxLines);
        float lineHeight = size * 1.22f;
        for (int i = 0; i < chunks.size(); i++) {
            drawLyricText(canvas, chunks.get(i).text, x, top + size + i * lineHeight,
                    size, resolved);
        }
        return chunks.size() * lineHeight;
    }

    private float wrappedTextHeight(String value, float size, float maxWidth, int maxLines) {
        if (value == null || value.isEmpty()) return 0f;
        setTextPaint(size, Typeface.BOLD);
        return wrapText(value.replace('\n', ' '), maxWidth, maxLines).size() * size * 1.22f;
    }

    private List<WrappedChunk> wrapText(String value, float maxWidth, int maxLines) {
        if (value == null || value.isEmpty() || maxWidth <= 0f || maxLines <= 0) {
            return Collections.emptyList();
        }
        TextLayoutKey key = TextLayoutKey.fromPaint(value, paint, maxWidth, maxLines);
        List<WrappedChunk> cached = wrappedTextCache.get(key);
        if (cached != null) return cached;
        List<WrappedChunk> result = new ArrayList<>();
        int start = 0;
        while (start < value.length() && result.size() < maxLines) {
            while (start < value.length() && value.charAt(start) == ' ') start++;
            if (start >= value.length()) break;
            int count = paint.breakText(value, start, value.length(), true, maxWidth, null);
            if (count <= 0) count = Character.charCount(value.codePointAt(start));
            int end = Math.min(value.length(), start + count);
            if (end < value.length() && end > start
                    && Character.isHighSurrogate(value.charAt(end - 1))) end--;
            if (end < value.length()) {
                int space = value.lastIndexOf(' ', end - 1);
                if (space > start + Math.max(1, (end - start) / 2)) end = space;
            }
            if (end <= start) end = Math.min(value.length(),
                    start + Character.charCount(value.codePointAt(start)));
            String text = value.substring(start, end).trim();
            int mappedEnd = end;
            boolean truncated = result.size() == maxLines - 1 && end < value.length();
            if (truncated) text = ellipsize(text + "…", maxWidth);
            result.add(new WrappedChunk(text, start, mappedEnd));
            start = end;
        }
        wrappedTextCache.put(key, result);
        return result;
    }

    private static final class WrappedChunk {
        final String text;
        final int start;
        final int end;

        WrappedChunk(String text, int start, int end) {
            this.text = text;
            this.start = start;
            this.end = end;
        }
    }

    private void drawKaraoke(Canvas canvas, MusicSnapshot snapshot, String value, float anchorX,
                              float y, float requestedSize, float maxWidth, Paint.Align align,
                              int baseColor, int activeColor) {
        if (value == null || value.isEmpty()) return;
        float size = fitSize(value, requestedSize, maxWidth, Typeface.BOLD);
        setTextPaintForValue(size, Typeface.BOLD, value);
        paint.setTextAlign(align);
        String text = ellipsize(value.replace('\n', ' '), maxWidth);
        float textWidth = paint.measureText(text);
        float left = align == Paint.Align.CENTER ? anchorX - textWidth / 2f : anchorX;
        // The ending highlight is a glow under the glyphs, so it is painted before them.
        drawTrailingGlowInLine(canvas, snapshot.lyricAvailable ? snapshot.lyrics : null, text,
                left, y, size, activeColor);
        // With 逐字歌词及时擦除 the already-sung prefix is withheld here and repainted by
        // drawSungGhost(), which is the only place a half-gone glyph is allowed to appear.
        float sungWidth = Math.min(textWidth, sungPrefixWidth(text, snapshot.lyrics.lineStartMs));
        if (sungWidth < textWidth) {
            int baseSave = -1;
            if (sungWidth > 0f) {
                baseSave = canvas.save();
                canvas.clipRect(left + sungWidth, y - size * 1.25f, left + textWidth,
                        y + size * 0.35f);
            }
            drawLyricText(canvas, text, anchorX, y, size, baseColor);
            if (baseSave >= 0) canvas.restoreToCount(baseSave);
        }
        if (!snapshot.lyricAvailable || snapshot.lyrics.lyric.isEmpty()) return;

        LrcTimeline.At at = snapshot.lyrics;
        // 没有逐字时间轴时按本句时长估算进度（issue #21）；估算不可用就保持原来的整句高亮。
        float estimated = at.wordTimed ? -1f : estimatedKaraokeWidth(text, snapshot);
        if (!at.wordTimed && estimated < 0f) {
            drawLyricOutline(canvas, text, anchorX, y, size, activeColor, true);
            paint.setColor(activeColor);
            applyLyricTextEffect(size, activeColor, 255);
            canvas.drawText(text, anchorX, y, paint);
            paint.clearShadowLayer();
            return;
        }
        float highlightedWidth = estimated >= 0f ? estimated : karaokeHighlightWidth(text, at);
        if (highlightedWidth > sungWidth) {
            int save = canvas.save();
            canvas.clipRect(left + sungWidth, y - size * 1.25f,
                    left + Math.min(textWidth, highlightedWidth), y + size * 0.35f);
            drawLyricOutline(canvas, text, anchorX, y, size, activeColor, true);
            paint.setColor(activeColor);
            applyLyricTextEffect(size, activeColor, 255);
            canvas.drawText(text, anchorX, y, paint);
            paint.clearShadowLayer();
            canvas.restoreToCount(save);
        }
        drawSungGhost(canvas, value, text, left, y, size, activeColor, snapshot.lyrics.lineStartMs);
    }

    /**
     * Refined Now Playing's ending highlight, transplanted: the held word is never restyled,
     * recoloured or rescaled, it simply blooms. The bloom is a text shadow painted *under* the
     * glyphs, so the word keeps its own colour, its karaoke split and its stroke, and no pixel is
     * ever painted twice — which is what used to double the neighbours and blank the tail.
     *
     * <p>A shadow layer rather than a blurred stroke: a mask filter forces a software layer whose
     * bounds are the glyph geometry, and a bloom far wider than the glyphs is cut off at those
     * bounds, which left a hard rectangular bite out of the end of the line.
     */
    private void drawTrailingGlow(Canvas canvas, String word, float wordLeft, float baseline,
                                  float size, int color, float wordProgress) {
        if (word == null || word.isEmpty()) return;
        float intensity = TrailingAccentEffect.intensity(wordProgress);
        if (intensity <= 0f) return;
        android.graphics.MaskFilter previousFilter = paint.getMaskFilter();
        Paint.Style previousStyle = paint.getStyle();
        int previousColor = paint.getColor();
        Paint.Align previousAlign = paint.getTextAlign();
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setStyle(Paint.Style.FILL);
        // The fill only tints; the shadow does the blooming. Keep the shadow's own layer free of
        // the line's blur filter so the two never fight over the same draw.
        paint.setMaskFilter(null);
        paint.setShadowLayer(Math.max(3f, size * .5f), 0f, 0f,
                withAlpha(color, Math.round(200 * intensity)));
        paint.setColor(withAlpha(color, Math.round(70 * intensity)));
        canvas.drawText(word, wordLeft, baseline, paint);
        paint.clearShadowLayer();
        paint.setMaskFilter(previousFilter);
        paint.setStyle(previousStyle);
        paint.setColor(previousColor);
        paint.setTextAlign(previousAlign);
    }

    /** Blooms the trailing word of a single-line lyric, before its glyphs are painted. */
    private void drawTrailingGlowInLine(Canvas canvas, LrcTimeline.At at, String text, float left,
                                        float baseline, float size, int color) {
        if (!trailingAccent || at == null || !at.trailingWord || at.currentWord.isEmpty()
                || text == null || text.isEmpty()) return;
        int start = Math.min(text.length(), at.completedLyric.length());
        int end = Math.min(text.length(), start + at.currentWord.length());
        if (end <= start) return;
        drawTrailingGlow(canvas, text.substring(start, end),
                left + paint.measureText(text, 0, start), baseline, size, color,
                at.wordProgressPermille / 1000f);
    }

    /** Blooms the trailing word of one wrapped chunk, before that chunk's glyphs are painted. */
    private void drawTrailingGlowInChunk(Canvas canvas, LrcTimeline.At at, WrappedChunk chunk,
                                         float x, float baseline, float size, int color) {
        if (!trailingAccent || at == null || !at.trailingWord || at.currentWord.isEmpty()) return;
        int start = at.completedLyric.length();
        int[] range = trailingWordInChunk(chunk.start, chunk.text.length(), start,
                start + at.currentWord.length());
        if (range == null) return;
        drawTrailingGlow(canvas, chunk.text.substring(range[0], range[1]),
                x + paint.measureText(chunk.text, 0, range[0]), baseline, size, color,
                at.wordProgressPermille / 1000f);
    }

    /**
     * Local {@code [start, end)} of the trailing word inside one wrapped chunk, or {@code null}
     * when the word does not reach this chunk. The word can straddle a wrap boundary, in which
     * case each chunk blooms only its own share of it.
     */
    static int[] trailingWordInChunk(int chunkStart, int chunkLength, int wordStart, int wordEnd) {
        if (chunkLength <= 0 || wordEnd <= wordStart) return null;
        int localStart = Math.max(0, wordStart - chunkStart);
        int localEnd = Math.min(chunkLength, wordEnd - chunkStart);
        return localEnd <= localStart ? null : new int[] { localStart, localEnd };
    }

    /**
     * 无逐字时间轴时按本句时长估算的高亮宽度（issue #21）。
     *
     * <p>普通 .lrc 只有行时间轴，原来整句一次性点亮；打开开关后按"已播放比例 × 文本宽度"推进，
     * 让经典 / 紧凑 / 顶部条 / Refined 也有逐字变色的观感。返回负值表示不估算（开关关闭、没有本句
     * 时长、位置还没到本句），调用方保持整句高亮。估算在长音、拖腔与行内停顿上会提前或滞后，
     * 只影响观感，不影响歌词同步。
     */
    private float estimatedKaraokeWidth(String text, MusicSnapshot snapshot) {
        if (!estimatedWordKaraoke || text == null || text.isEmpty()) return -1f;
        LrcTimeline.At at = snapshot.lyrics;
        float fraction = KaraokeProgress.estimatedFraction(snapshot.positionMs + lyricOffsetMs,
                at.lineStartMs, at.lineDurationMs);
        if (fraction < 0f) return -1f;
        return paint.measureText(text) * fraction;
    }

    private float karaokeHighlightWidth(String text, LrcTimeline.At at) {
        if (text == null || text.isEmpty()) return 0f;
        KaraokeProgress.Boundary boundary = KaraokeProgress.boundary(
                at.currentWord, at.wordProgressPermille);
        int completedEnd = Math.min(text.length(), at.completedLyric.length());
        int completeEnd = Math.min(text.length(), completedEnd + boundary.completeEnd);
        int partialEnd = Math.min(text.length(), completedEnd + boundary.partialEnd);
        float width = completeEnd <= 0 ? 0f : paint.measureText(text, 0, completeEnd);
        if (boundary.partialFraction > 0f && partialEnd > completeEnd) {
            width += paint.measureText(text, completeEnd, partialEnd)
                    * boundary.partialFraction;
        }
        return width;
    }

    private float karaokeHighlightWidth(WrappedChunk chunk, LrcTimeline.At at) {
        KaraokeProgress.Boundary boundary = KaraokeProgress.boundary(
                at.currentWord, at.wordProgressPermille);
        int completedEnd = at.completedLyric.length();
        int completeEnd = completedEnd + boundary.completeEnd;
        int partialEnd = completedEnd + boundary.partialEnd;
        float width = textWidthToGlobalIndex(chunk, completeEnd);
        if (boundary.partialFraction > 0f && partialEnd > completeEnd) {
            float partialWidth = textWidthToGlobalIndex(chunk, partialEnd);
            width += (partialWidth - width) * boundary.partialFraction;
        }
        return width;
    }

    private float textWidthToGlobalIndex(WrappedChunk chunk, int globalIndex) {
        int localEnd = Math.max(0,
                Math.min(chunk.text.length(), globalIndex - chunk.start));
        return localEnd <= 0 ? 0f : paint.measureText(chunk.text, 0, localEnd);
    }

    private void drawCentered(Canvas canvas, String value, float y, float requestedSize,
                              int color, float maxWidth, int style) {
        if (value == null || value.isEmpty()) return;
        float size = fitSize(value, requestedSize, maxWidth, style);
        setTextPaintForValue(size, style, value);
        paint.setTextAlign(Paint.Align.CENTER);
        String text = ellipsize(value.replace('\n', ' '), maxWidth);
        if (drawSplitSourceMetadata(canvas, text, getWidth() / 2f, y, maxWidth,
                Paint.Align.CENTER, 255)) return;
        paint.setColor(resolveMetadataColor(value, color));
        int resolved = resolveMetadataColor(value, color);
        if (shouldOutlineLyric(text, false)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(outlineStrokeWidth(size, inactiveLyricOutlineWidthPercent));
            paint.setColor(outlineStrokeColor(resolved, false));
            canvas.drawText(text, getWidth() / 2f, y, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        paint.setColor(resolved);
        canvas.drawText(text, getWidth() / 2f, y, paint);
    }

    private void drawLeft(Canvas canvas, String value, float x, float y, float requestedSize,
                          int color, float maxWidth, int style) {
        if (value == null || value.isEmpty()) return;
        float size = fitSize(value, requestedSize, maxWidth, style);
        setTextPaintForValue(size, style, value);
        paint.setTextAlign(Paint.Align.LEFT);
        if (drawSplitSourceMetadata(canvas, value, x, y, maxWidth,
                Paint.Align.LEFT, 255)) return;
        int resolved = resolveMetadataColor(value, color);
        if (shouldOutlineLyric(value, false)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(outlineStrokeWidth(size, inactiveLyricOutlineWidthPercent));
            paint.setColor(outlineStrokeColor(resolved, false));
            canvas.drawText(ellipsize(value.replace('\n', ' '), maxWidth), x, y, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        paint.setColor(resolved);
        canvas.drawText(ellipsize(value.replace('\n', ' '), maxWidth), x, y, paint);
    }

    /**
     * Whether a line should be stroked. Lyric text follows the current / inactive lyric outline
     * setting; panel metadata is never stroked.
     */
    private boolean shouldOutlineLyric(boolean current) {
        return current ? currentLyricOutline : inactiveLyricOutline;
    }

    /**
     * Stroke decision for a string that <em>might</em> be metadata. Only the metadata drawing
     * paths may take this route: a lyric whose text happens to equal the song title is still a
     * lyric, and matching on text alone used to strip its outline.
     */
    private boolean shouldOutlineLyric(String value, boolean current) {
        if (!drawingMetadata) return shouldOutlineLyric(current);
        return shouldOutlineLyric(current)
                && value != null && !frameTitle.isEmpty() && !value.equals(frameTitle)
                && !frameArtist.isEmpty() && !value.equals(frameArtist)
                && !value.equals(frameSourceName) && !value.equals(frameLyricSourceName);
    }

    /** Outline width in px, clamped to a sane band so extreme percents stay readable. */
    static float outlineStrokeWidth(float sizePx, int percentWidth) {
        float scaled = sizePx * Math.max(1, Math.min(40, percentWidth)) / 100f;
        return Math.max(1f, scaled);
    }

    /** Auto outline color contrasts against the glyph so either environment stays readable. */
    private int outlineStrokeColor(int textColor, boolean current) {
        int color = current ? currentLyricOutlineColor : inactiveLyricOutlineColor;
        if (color == 0) {
            double luminance = (0.299 * Color.red(textColor)
                    + 0.587 * Color.green(textColor) + 0.114 * Color.blue(textColor))
                    * (Color.alpha(textColor) / 255.0);
            color = luminance >= 128.0 ? 0xFF000000 : 0xFFFFFFFF;
        }
        int percent = current ? currentLyricOutlineAlphaPercent : inactiveLyricOutlineAlphaPercent;
        int alpha = Math.round(255f * Math.max(0, Math.min(100, percent)) / 100f);
        return withAlpha(color | 0xFF000000, alpha);
    }

    /**
     * Shared lyric text pass: optional configurable outline under the fill. Every wrapped and
     * karaoke path routes through this so 纯净/精致/AMLL/PiP outlines behave like classic.
     */
    private void drawLyricText(Canvas canvas, String text, float x, float y, float size,
                               int resolvedColor) {
        drawLyricOutline(canvas, text, x, y, size, resolvedColor, false);
        paint.setColor(resolvedColor);
        canvas.drawText(text, x, y, paint);
    }

    private void drawLyricOutline(Canvas canvas, String text, float x, float y, float size,
                                  int resolvedColor, boolean current) {
        if (!shouldOutlineLyric(text, current)) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(outlineStrokeWidth(size, current
                ? currentLyricOutlineWidthPercent : inactiveLyricOutlineWidthPercent));
        paint.setColor(outlineStrokeColor(resolvedColor, current));
        canvas.drawText(text, x, y, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private float fitSize(String value, float requested, float maxWidth, int style) {
        setTextPaintForValue(requested, style, value);
        float measured = paint.measureText(value == null ? "" : value);
        if (measured <= maxWidth || measured <= 0f) return requested;
        return Math.max(requested * 0.62f, requested * maxWidth / measured);
    }

    /**
     * Metadata keeps its own colours. Lyric paths pass through here too, so anything that is not
     * actually being drawn as panel metadata gets the caller's colour untouched — otherwise a
     * lyric line that reads exactly like the song title would take the title colour.
     */
    private int resolveMetadataColor(String value, int fallback) {
        if (!drawingMetadata || value == null || value.isEmpty()) return fallback;
        int selected = 0;
        if (!frameTitle.isEmpty() && value.equals(frameTitle)) selected = titleColor;
        else if (!frameArtist.isEmpty() && value.equals(frameArtist)) selected = artistColor;
        else if (!frameLyricSourceName.isEmpty() && value.equals(frameLyricSourceName)) {
            selected = lyricSourceColor;
        } else if (!frameSourceName.isEmpty() && value.startsWith(frameSourceName)) {
            selected = playerColor;
        }
        return selected == 0 ? fallback : withAlpha(selected, Color.alpha(fallback));
    }

    private boolean drawSplitSourceMetadata(Canvas canvas, String value, float anchorX, float y,
                                            float maxWidth, Paint.Align align, int alpha) {
        if (!drawingMetadata) return false;
        if ((playerColor == 0 && lyricSourceColor == 0) || frameSourceName.isEmpty()
                || frameLyricSourceName.isEmpty() || value == null
                || !value.startsWith(frameSourceName)) return false;
        int split = value.indexOf(frameLyricSourceName, frameSourceName.length());
        if (split < 0 || paint.measureText(value) > maxWidth) return false;
        String prefix = value.substring(0, split);
        String suffix = value.substring(split);
        float total = paint.measureText(value);
        float left = align == Paint.Align.CENTER ? anchorX - total * 0.5f
                : align == Paint.Align.RIGHT ? anchorX - total : anchorX;
        Paint.Align previous = paint.getTextAlign();
        paint.setTextAlign(Paint.Align.LEFT);
        int prefixColor = playerColor == 0 ? lyricColor(0xFFB8C5D8) : playerColor;
        int suffixColor = lyricSourceColor == 0 ? prefixColor : lyricSourceColor;
        paint.setColor(withAlpha(prefixColor, alpha));
        canvas.drawText(prefix, left, y, paint);
        paint.setColor(withAlpha(suffixColor, alpha));
        canvas.drawText(suffix, left + paint.measureText(prefix), y, paint);
        paint.setTextAlign(previous);
        return true;
    }

    private float canvasAreaScale(float referenceWidthDp, float referenceHeightDp,
                                  float density) {
        float referenceArea = referenceWidthDp * referenceHeightDp * density * density;
        return (float) Math.sqrt(Math.max(0.01f,
                getWidth() * getHeight() / referenceArea));
    }

    private float styleCanvasScale(float density) {
        if ("refined".equals(overlayStyle)) return canvasAreaScale(560f, 300f, density);
        if ("compact".equals(overlayStyle)) return canvasAreaScale(320f, 104f, density);
        if ("pure".equals(overlayStyle)) return canvasAreaScale(430f, 190f, density);
        if ("amll".equals(overlayStyle)) return canvasAreaScale(620f, 350f, density);
        if ("pip".equals(overlayStyle)) return canvasAreaScale(440f, 220f, density);
        if ("custom".equals(overlayStyle)) return canvasAreaScale(460f, 260f, density);
        if ("island".equals(overlayStyle)) return canvasAreaScale(420f, 72f, density);
        return canvasAreaScale(390f, 226f, density);
    }

    private String ellipsize(String value, float maxWidth) {
        if (paint.measureText(value) <= maxWidth) return value;
        TextLayoutKey key = TextLayoutKey.fromPaint(value, paint, maxWidth, -1);
        String cached = ellipsizedTextCache.get(key);
        if (cached != null) return cached;
        String suffix = "…";
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (paint.measureText(value.substring(0, mid) + suffix) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        if (low > 0 && Character.isHighSurrogate(value.charAt(low - 1))) low--;
        String result = value.substring(0, low) + suffix;
        ellipsizedTextCache.put(key, result);
        return result;
    }

    private void setTextPaint(float size, int style) {
        paint.setShader(null);
        paint.setAlpha(255);
        paint.setStyle(Paint.Style.FILL);
        paint.setMaskFilter(null);
        paint.clearShadowLayer();
        paint.setTextSize(size);
        Typeface target = customTypeface == null
                ? (style == Typeface.BOLD ? SANS_BOLD : SANS_NORMAL)
                : Typeface.create(customTypeface, style);
        if (paint.getTypeface() != target && appliedTextTypeface != target) {
            paint.setTypeface(target);
        }
        appliedTextTypeface = target;
    }

    private void setTextPaintForValue(float size, int style, String value) {
        setTextPaint(size, style);
        if (customTypeface != null && !CustomFontStore.canRender(value, paint.getTypeface())) {
            Typeface fallback = style == Typeface.BOLD ? SANS_BOLD : SANS_NORMAL;
            if (paint.getTypeface() != fallback) paint.setTypeface(fallback);
            appliedTextTypeface = fallback;
        }
    }

    private void clearTextCaches() {
        wrappedTextCache.evictAll();
        ellipsizedTextCache.evictAll();
        // 模糊歌词位图与星空底色位图都跟字号 / 尺寸 / 调色板绑死，尺寸或设置一变就丢掉重画。
        blurredLineCache.evictAll();
        blurScratch = null;
        starfieldBackdrop = null;
        starfieldBackdropKey = "";
        panelShadowHaloBitmap = null;
        panelShadowHaloKey = "";
    }

    private static final class TextLayoutKey {
        final String value;
        final int textSizeBits;
        final int widthBits;
        final int maxLines;
        final int typefaceStyle;

        private TextLayoutKey(String value, int textSizeBits, int widthBits,
                              int maxLines, int typefaceStyle) {
            this.value = value;
            this.textSizeBits = textSizeBits;
            this.widthBits = widthBits;
            this.maxLines = maxLines;
            this.typefaceStyle = typefaceStyle;
        }

        static TextLayoutKey fromPaint(String value, Paint paint, float maxWidth,
                                       int maxLines) {
            Typeface typeface = paint.getTypeface();
            return new TextLayoutKey(value, Float.floatToIntBits(paint.getTextSize()),
                    Float.floatToIntBits(maxWidth), maxLines,
                    typeface == null ? Typeface.NORMAL : typeface.getStyle());
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TextLayoutKey)) return false;
            TextLayoutKey key = (TextLayoutKey) other;
            return textSizeBits == key.textSizeBits && widthBits == key.widthBits
                    && maxLines == key.maxLines && typefaceStyle == key.typefaceStyle
                    && value.equals(key.value);
        }

        @Override public int hashCode() {
            int result = value.hashCode();
            result = 31 * result + textSizeBits;
            result = 31 * result + widthBits;
            result = 31 * result + maxLines;
            return 31 * result + typefaceStyle;
        }
    }

    private static int lighten(int color, int amount) {
        return Color.rgb(Math.min(255, Color.red(color) + amount),
                Math.min(255, Color.green(color) + amount),
                Math.min(255, Color.blue(color) + amount));
    }

    private static int mix(int first, int second, float secondAmount) {
        float amount = clamp(secondAmount);
        return Color.rgb(
                Math.round(Color.red(first) * (1f - amount) + Color.red(second) * amount),
                Math.round(Color.green(first) * (1f - amount) + Color.green(second) * amount),
                Math.round(Color.blue(first) * (1f - amount) + Color.blue(second) * amount));
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color),
                Color.green(color), Color.blue(color));
    }

    @SuppressWarnings("deprecation")
    private static int saveLayerAlphaCompat(Canvas canvas, RectF bounds, int alpha) {
        // 完全不透明时不必开离屏层：星空这类每帧重绘的背景就少一次全屏合成，弱车机上差别明显。
        if (alpha >= 255) {
            int save = canvas.save();
            canvas.clipRect(bounds);
            return save;
        }
        if (Build.VERSION.SDK_INT >= 21) return canvas.saveLayerAlpha(bounds, alpha);
        return canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom,
                alpha, Canvas.ALL_SAVE_FLAG);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }

    private static float clampRange(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private BlurMaskFilter blurMask(float radius) {
        int key = Float.floatToIntBits(radius);
        BlurMaskFilter cached = blurMaskCache.get(key);
        if (cached != null) return cached;
        BlurMaskFilter created = new BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL);
        blurMaskCache.put(key, created);
        return created;
    }

    /**
     * 画一行模糊歌词（issue #59 后续）。
     *
     * <p>{@code BlurMaskFilter} 只有软件画布上才可靠，但让整块面板退回 {@code LAYER_TYPE_SOFTWARE}
     * 的代价太大——Refined / 紧凑一旦打开「歌词模糊」、AMLL 一律，整块面板都改由 CPU 绘制，帧率再高
     * 也拉不回来。这里改成：把这一行单独画进一张离屏位图（软件 Canvas，模糊照常生效），再把位图贴回
     * 硬件画布。面板始终是硬件渲染，CPU 只为这一行付一次钱——同一行 + 同参数还会缓存下来，之后每帧
     * 只贴一次图。
     *
     * @param dissolving 逐字消散中的行：每帧内容都在变，缓存不住，改用复用的小位图逐帧重画
     */
    private void drawBlurredLine(Canvas canvas, String value, float left, float top, float size,
                                 int baseColor, float frameAlpha, float maxWidth, int style,
                                 int maxLines, float blurRadius, boolean dissolving, long lineId) {
        if (value == null || value.isEmpty()) return;
        int alpha = Math.round(Color.alpha(baseColor) * clamp(frameAlpha));
        if (alpha <= 0) return;
        if (blurRadius <= 0.5f) {
            int color = withAlpha(baseColor, alpha);
            if (dissolving) {
                drawWrappedTextDissolving(canvas, value, left, top, size, color, maxWidth,
                        style, maxLines, lineId);
            } else {
                drawWrappedText(canvas, value, left, top, size, color, maxWidth, style, maxLines);
            }
            return;
        }
        setTextPaintForValue(size, style, value);
        List<WrappedChunk> chunks = wrapText(value.replace('\n', ' '), maxWidth, maxLines);
        if (chunks.isEmpty()) return;
        float contentWidth = 0f;
        for (WrappedChunk chunk : chunks) {
            contentWidth = Math.max(contentWidth, paint.measureText(chunk.text));
        }
        float blockHeight = chunks.size() * size * 1.22f;
        int pad = (int) Math.ceil(blurRadius) + 2;
        int bitmapWidth = Math.max(1, (int) Math.ceil(contentWidth) + pad * 2);
        int bitmapHeight = Math.max(1, (int) Math.ceil(blockHeight) + pad * 2);
        int solidColor = withAlpha(baseColor, 255);
        String key = bitmapWidth + "x" + bitmapHeight + ":" + Float.floatToIntBits(blurRadius)
                + ":" + solidColor + ":" + style + ":" + maxLines + ":" + value;
        Bitmap bitmap = null;
        if (!dissolving) {
            Bitmap cached = blurredLineCache.get(key);
            if (cached != null && !cached.isRecycled()) bitmap = cached;
        }
        if (bitmap == null) {
            if (dissolving) {
                if (blurScratch == null || blurScratch.isRecycled()
                        || blurScratch.getWidth() < bitmapWidth
                        || blurScratch.getHeight() < bitmapHeight) {
                    blurScratch = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                            Bitmap.Config.ARGB_8888);
                }
                bitmap = blurScratch;
            } else {
                bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight,
                        Bitmap.Config.ARGB_8888);
            }
            Canvas offscreen = new Canvas(bitmap);
            offscreen.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            android.graphics.MaskFilter previousMask = paint.getMaskFilter();
            paint.setMaskFilter(blurMask(blurRadius));
            offscreen.translate(pad, pad);
            if (dissolving) {
                drawWrappedTextDissolving(offscreen, value, 0f, 0f, size, solidColor, maxWidth,
                        style, maxLines, lineId);
            } else {
                drawWrappedText(offscreen, value, 0f, 0f, size, solidColor, maxWidth, style,
                        maxLines);
            }
            paint.setMaskFilter(previousMask);
            if (!dissolving) blurredLineCache.put(key, bitmap);
        }
        paint.setMaskFilter(null);
        int previousAlpha = paint.getAlpha();
        paint.setAlpha(alpha);
        canvas.drawBitmap(bitmap, left - pad, top - pad, paint);
        paint.setAlpha(previousAlpha);
    }
}
