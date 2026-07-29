package com.zuoqirun.lyricscompanion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.os.Build;
import android.util.LruCache;
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
    private final LruCache<TextLayoutKey, List<WrappedChunk>> wrappedTextCache =
            new LruCache<>(96);
    private final LruCache<TextLayoutKey, String> ellipsizedTextCache =
            new LruCache<>(96);
    private float[] refinedLineHeights = new float[8];
    private float[] refinedLineTops = new float[8];
    private float textScale = 1f;
    private float coverScale = 1f;
    private int opacity = 88;
    private int lyricOffsetMs;
    private int backgroundBlur;
    private int backgroundDim;
    private int lyricLineCount;
    private String overlayStyle;
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

    LyricsPanelView(Context context) { this(context, false); }

    LyricsPanelView(Context context, boolean secondary) {
        super(context);
        this.secondary = secondary;
        reloadStyle();
    }

    void reloadStyle() {
        textScale = AppPreferences.textScale(getContext());
        coverScale = AppPreferences.styleCoverScale(getContext());
        opacity = AppPreferences.opacity(getContext());
        lyricOffsetMs = AppPreferences.lyricOffsetMs(getContext());
        backgroundBlur = AppPreferences.styleBlur(getContext());
        backgroundDim = AppPreferences.styleDim(getContext());
        lyricLineCount = AppPreferences.styleLyricLines(getContext());
        overlayStyle = AppPreferences.overlayStyle(getContext());
        refinedDisplayMode = AppPreferences.refinedDisplayMode(getContext());
        refinedColorScheme = AppPreferences.refinedColorScheme(getContext());
        refinedAccentVariant = AppPreferences.refinedAccentVariant(getContext());
        refinedTextEffect = AppPreferences.refinedTextEffect(getContext());
        refinedProgressBottom = AppPreferences.refinedProgressBottom(getContext());
        refinedCoverHorizontal = AppPreferences.refinedCoverHorizontal(getContext());
        refinedCoverVertical = AppPreferences.refinedCoverVertical(getContext());
        refinedRectangleCover = AppPreferences.refinedRectangleCover(getContext());
        refinedCoverShadow = AppPreferences.refinedCoverShadow(getContext());
        refinedBackgroundType = AppPreferences.refinedBackgroundType(getContext());
        refinedStaticFluid = AppPreferences.refinedStaticFluid(getContext());
        refinedDynamicGradient = AppPreferences.refinedDynamicGradient(getContext());
        refinedLyricFontSize = AppPreferences.refinedLyricFontSize(getContext());
        refinedOriginalBold = AppPreferences.refinedOriginalBold(getContext());
        refinedLyricFade = AppPreferences.refinedLyricFade(getContext());
        refinedLyricZoom = AppPreferences.refinedLyricZoom(getContext());
        refinedLyricBlur = AppPreferences.refinedLyricBlur(getContext());
        refinedLyricRotate = AppPreferences.refinedLyricRotate(getContext());
        refinedRotateCurvature = AppPreferences.refinedRotateCurvature(getContext());
        refinedKaraokeAnimation = AppPreferences.refinedKaraokeAnimation(getContext());
        refinedCurrentAlign = AppPreferences.refinedCurrentAlign(getContext());
        refinedShowTranslation = AppPreferences.refinedShowTranslation(getContext());
        refinedLyricGlow = AppPreferences.refinedLyricGlow(getContext());
        setLayerType("refined".equals(overlayStyle) && refinedLyricBlur
                ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_NONE, null);
        layoutConfig = LyricsLayoutConfig.load(getContext());
        if (blurPreview != null && blurPreview != blurSource && !blurPreview.isRecycled()) {
            blurPreview.recycle();
        }
        blurPreview = null;
        blurSource = null;
        clearTextCaches();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float density = getResources().getDisplayMetrics().density;
        float width = getWidth();
        float height = getHeight();
        if (width <= 2f || height <= 2f) return;
        panelRect.set(1f, 1f, width - 1f, height - 1f);
        long now = SystemClock.elapsedRealtime();
        updateBrowseSpring(now);
        if (!browsingLyrics && browseUntilElapsedMs > 0L && now >= browseUntilElapsedMs) {
            browseUntilElapsedMs = 0L;
            browseSettling = false;
            browseVisualOffsetPx = 0f;
            browseVelocityPxPerSecond = 0f;
        }
        MusicSnapshot snapshot = browsingLyrics || browseUntilElapsedMs > now
                ? MusicStateStore.snapshotForLyricBrowse(lyricOffsetMs, browsePositionMs)
                : MusicStateStore.snapshot(lyricOffsetMs);

        if ("refined".equals(overlayStyle)) {
            drawRefined(canvas, snapshot, density);
        } else if ("pip".equals(overlayStyle)) {
            drawPip(canvas, snapshot, density);
        } else if ("custom".equals(overlayStyle)) {
            drawCustom(canvas, snapshot, density);
        } else {
            drawDefault(canvas, snapshot, density);
        }
        if (browsingLyrics || browseUntilElapsedMs > now) {
            drawBrowseIndicator(canvas, snapshot, density);
        }
        postInvalidateDelayed(nextFrameDelay(snapshot, now));
    }

    private long nextFrameDelay(MusicSnapshot snapshot, long nowElapsedMs) {
        if (browsingLyrics || browseSettling) return 16L;
        if (lyricScrollAnimationStartedMs > 0L
                && nowElapsedMs - lyricScrollAnimationStartedMs < 500L) return 16L;
        if (browseUntilElapsedMs > nowElapsedMs) {
            return Math.max(16L, Math.min(250L, browseUntilElapsedMs - nowElapsedMs));
        }
        if (!snapshot.active) return 750L;
        if (!snapshot.playing) return 400L;
        if (snapshot.lyrics.interlude) return 33L;
        if (snapshot.lyrics.wordTimed && snapshot.lyrics.wordDurationMs > 0L
                && !snapshot.lyrics.currentWord.isEmpty()) {
            int codePoints = snapshot.lyrics.currentWord.codePointCount(
                    0, snapshot.lyrics.currentWord.length());
            int revealed = LrcTimeline.revealedCodePointCount(
                    snapshot.lyrics.currentWord, snapshot.lyrics.wordProgressPermille);
            if (codePoints > 0 && revealed < codePoints) {
                long nextOffset = (snapshot.lyrics.wordDurationMs * (revealed + 1L)
                        + codePoints - 1L) / codePoints;
                long lyricPosition = snapshot.positionMs + lyricOffsetMs;
                long untilNextCharacter = snapshot.lyrics.wordStartMs
                        + nextOffset - lyricPosition;
                return Math.max(16L, Math.min(100L, untilNextCharacter));
            }
        }
        return 100L;
    }

    boolean isLyricGestureRegion(float x, float y) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        MusicSnapshot snapshot = MusicStateStore.snapshot(lyricOffsetMs);
        if (!snapshot.lyricAvailable) return false;
        if ("refined".equals(overlayStyle)) {
            return !"cover".equals(refinedDisplayMode)
                    && ("lyrics".equals(refinedDisplayMode) || x >= getWidth() * 0.46f);
        }
        if ("pip".equals(overlayStyle)) return y >= getHeight() * 0.34f;
        if ("custom".equals(overlayStyle)) return y >= getHeight() * 0.28f;
        return y >= getHeight() * 0.24f && y <= getHeight() * 0.86f;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
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
        if ("refined".equals(overlayStyle) && lastRefinedBrowseStepPx > 1f) {
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

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void drawDefault(Canvas canvas, MusicSnapshot snapshot, float density) {
        float pad = 18f * density;
        float width = getWidth();
        float height = getHeight();
        drawPanelShadow(canvas, 24f * density, Color.argb(Math.round(opacity * 2.55f), 6, 15, 27));

        paint.setColor(0x406EE7F2);
        workRect.set(pad, 10f * density, width - pad, 13f * density);
        canvas.drawRoundRect(workRect, 2f * density, 2f * density, paint);

        float usableWidth = Math.max(1f, width - pad * 2f);
        float previewShift = browseVisualOffsetPx;
        float y = 33f * density;
        float unit = secondary ? 1.12f : 1f;
        String lyricSource = snapshot.lyricSourceName.isEmpty()
                ? "" : "  ·  歌词/" + snapshot.lyricSourceName;
        String status = snapshot.active
                ? snapshot.sourceName + (snapshot.playing ? "  ·  播放中" : "  ·  已暂停")
                + lyricSource : "歌词伴侣  ·  等待音乐";
        drawCentered(canvas, status, y, 11f * density * textScale * unit,
                snapshot.playing ? 0xFF6EE7F2 : 0xFF8392A8, usableWidth, Typeface.BOLD);

        y += 24f * density * unit;
        drawCentered(canvas, snapshot.active ? snapshot.title : "打开音乐播放器并开始播放", y,
                15f * density * textScale * unit, 0xFFF6F9FF, usableWidth, Typeface.BOLD);
        y += 27f * density * unit;
        drawCentered(canvas, snapshot.lyrics.previousLyric, y + previewShift,
                12f * density * textScale * unit, 0xFF68778C, usableWidth, Typeface.NORMAL);
        y += 32f * density * unit;
        if (snapshot.lyrics.interlude) {
            float dotRadius = 22f * density * textScale * unit * 0.35f;
            float dotWidth = interludeDotsWidth(dotRadius);
            drawInterludeDots(canvas, snapshot, width / 2f - dotWidth / 2f,
                    y + previewShift - dotRadius, dotRadius, 0xFFFFCA66);
        } else {
            drawKaraoke(canvas, snapshot, currentText(snapshot), width / 2f,
                    y + previewShift,
                    22f * density * textScale * unit, usableWidth, Paint.Align.CENTER,
                    0xFFB1BCCB, 0xFFFFCA66);
        }
        if (!snapshot.lyrics.translatedLyric.isEmpty()) {
            y += 24f * density * unit;
            drawCentered(canvas, snapshot.lyrics.translatedLyric, y + previewShift,
                    12f * density * textScale * unit, 0xFFB8C5D8, usableWidth, Typeface.NORMAL);
        }
        drawCentered(canvas, snapshot.lyrics.nextLyric,
                height - 37f * density + previewShift,
                12f * density * textScale * unit, 0xFF68778C, usableWidth, Typeface.NORMAL);
        drawProgress(canvas, pad, height - 17f * density, width - pad, 3f * density,
                snapshot, 0x354B5F78, 0xFFFFCA66);
    }

    /** Native rendering of Refined Now Playing's 45% / 45% two-column layout. */
    private void drawRefined(Canvas canvas, MusicSnapshot snapshot, float density) {
        float width = getWidth();
        float height = getHeight();
        updatePalette(snapshot.albumArt);
        boolean light = refinedUsesLightColors();
        int accent = refinedAccentColor();
        int primaryText = light ? mix(accent, Color.BLACK, 0.72f)
                : mix(accent, Color.WHITE, 0.78f);
        int secondaryText = withAlpha(primaryText, 150);
        drawRefinedBackground(canvas, snapshot.albumArt, light, accent, snapshot.playing);
        int contentSave = canvas.save();
        clipPath.reset();
        float panelRadius = Math.min(width, height) * 0.075f;
        clipPath.addRoundRect(panelRect, panelRadius, panelRadius, Path.Direction.CW);
        canvas.clipPath(clipPath);

        float pad = Math.max(12f * density, width * 0.035f);
        boolean lyricsOnly = "lyrics".equals(refinedDisplayMode);
        boolean coverOnly = "cover".equals(refinedDisplayMode);
        float leftColumnWidth = lyricsOnly ? 0f : width * 0.45f;
        float lyricLeft = lyricsOnly ? pad : Math.max(width * 0.50f, leftColumnWidth + pad);
        float lyricWidth = Math.max(1f, width - lyricLeft - pad);

        if (!lyricsOnly) {
            drawRefinedSongInfo(canvas, snapshot, density, leftColumnWidth, pad,
                    primaryText, secondaryText, accent);
        }
        if (!coverOnly) {
            drawRefinedLyrics(canvas, snapshot, density, lyricLeft, lyricWidth,
                    primaryText, secondaryText);
        }
        float progressY = refinedProgressBottom ? height - 2f * density
                : height - 10f * density;
        drawProgress(canvas, pad, progressY, width - pad, 2f * density,
                snapshot, withAlpha(primaryText, 48), withAlpha(primaryText, 225));
        canvas.restoreToCount(contentSave);
    }

    private void drawRefinedSongInfo(Canvas canvas, MusicSnapshot snapshot, float density,
                                     float columnWidth, float pad, int primaryText,
                                     int secondaryText, int accent) {
        float height = getHeight();
        float coverSize = Math.min(columnWidth * 0.56f, height * 0.46f) * coverScale;
        coverSize = Math.max(46f * density, Math.min(coverSize, height * 0.54f));
        float titleSize = Math.max(16f * density,
                Math.min(34f * density, height * 0.075f)) * textScale;
        float metaSize = Math.max(9f * density, titleSize * 0.42f);
        float groupHeight = coverSize + 18f * density + titleSize
                + metaSize * 3.2f;
        float top = "middle".equals(refinedCoverVertical)
                ? Math.max(pad, (height - groupHeight) / 2f)
                : Math.max(pad, height - pad - 8f * density - groupHeight);
        float left = "center".equals(refinedCoverHorizontal)
                ? Math.max(pad, (columnWidth - coverSize) / 2f) : pad;
        coverRect.set(left, top, left + coverSize, top + coverSize);
        RectF cover = coverRect;
        float radius = refinedRectangleCover ? 16f * density : coverSize / 2f;

        if (refinedCoverShadow && snapshot.albumArt != null
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
        drawCover(canvas, snapshot.albumArt, cover, radius,
                mix(accent, Color.DKGRAY, 0.55f));

        float textLeft = "center".equals(refinedCoverHorizontal) ? pad : left;
        float textWidth = Math.max(1f, columnWidth - textLeft - pad);
        Paint.Align align = "center".equals(refinedCoverHorizontal)
                ? Paint.Align.CENTER : Paint.Align.LEFT;
        float anchor = align == Paint.Align.CENTER ? columnWidth / 2f : textLeft;
        float y = cover.bottom + 18f * density + titleSize;
        drawRefinedText(canvas, snapshot.active ? snapshot.title : "等待音乐",
                anchor, y, titleSize, primaryText, textWidth, align, Typeface.NORMAL, 255);
        y += metaSize * 1.55f;
        drawRefinedText(canvas, snapshot.artist, anchor, y, metaSize,
                secondaryText, textWidth, align, Typeface.NORMAL, 205);
        y += metaSize * 1.38f;
        drawRefinedText(canvas, snapshot.sourceName + sourceSuffix(snapshot), anchor, y,
                metaSize * 0.88f, secondaryText, textWidth, align, Typeface.NORMAL, 145);
    }

    private void drawRefinedLyrics(Canvas canvas, MusicSnapshot snapshot, float density,
                                   float left, float width, int primaryText,
                                   int secondaryText) {
        float fontSize = refinedLyricFontSize * density * textScale
                * (secondary ? 1.03f : 1f);
        float currentY = getHeight() * (refinedCurrentAlign / 100f);
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
                if (refinedShowTranslation && !line.translated.isEmpty()) {
                    heights[i] += fontSize * 0.18f
                            + wrappedTextHeight(line.translated, translationSize, width, 2);
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
            float centerY = top + heights[i] / 2f;
            float scale = refinedLyricZoom ? refinedScaleForOffset(offset) : 1f;
            float opacity = offset == 0 ? 1f : 0.40f;
            if (refinedLyricFade && Math.abs(offset) > 1) {
                opacity *= Math.max(0f, 1f - 0.4f * (Math.abs(offset) - 1));
            }
            float edge = Math.min(centerY / Math.max(1f, getHeight()),
                    (getHeight() - centerY) / Math.max(1f, getHeight()));
            opacity *= clamp(edge * 8f);
            if (opacity <= 0.01f) continue;
            int save = canvas.save();
            if (refinedLyricRotate && offset != 0) {
                float angle = offset * refinedRotateCurvature * 0.10f;
                canvas.rotate(angle, left, centerY);
            }
            canvas.scale(scale, scale, left, centerY);
            if (refinedLyricBlur && offset != 0) {
                paint.setMaskFilter(new BlurMaskFilter(
                        Math.min(4.5f * density, (0.5f + Math.abs(offset)) * density),
                        BlurMaskFilter.Blur.NORMAL));
            }
            if (line.interlude) {
                drawInterludeDots(canvas, snapshot, left, top + fontSize * 0.30f,
                        fontSize * 0.35f,
                        withAlpha(primaryText, Math.round(225f * opacity)));
            } else if (offset == 0) {
                drawWrappedKaraoke(canvas, snapshot, currentText(snapshot), left, top,
                        fontSize, width, primaryText, 3);
            } else {
                drawWrappedText(canvas, line.text, left, top, fontSize,
                        withAlpha(secondaryText, Math.round(opacity * 255f)), width,
                        refinedOriginalBold ? Typeface.BOLD : Typeface.NORMAL, 3);
            }
            if (!line.interlude && refinedShowTranslation && !line.translated.isEmpty()) {
                float originalHeight = wrappedTextHeight(line.text, fontSize, width, 3);
                drawWrappedText(canvas, line.translated, left,
                        top + originalHeight + fontSize * 0.18f, translationSize,
                        withAlpha(secondaryText, Math.round(opacity
                                * (offset == 0 ? 205f : 180f))), width,
                        Typeface.NORMAL, 2);
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
        if (lineStartMs < 0L) return 0f;
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

    private void drawRefinedBackground(Canvas canvas, Bitmap art, boolean light,
                                       int accent, boolean playing) {
        String type = refinedBackgroundType == null ? "blur" : refinedBackgroundType;
        if ("none".equals(type)) return;
        int layer = canvas.saveLayerAlpha(panelRect,
                Math.round(clamp(opacity / 100f) * 255f));
        int save = canvas.save();
        float radius = Math.min(getWidth(), getHeight()) * 0.075f;
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(null);

        if ("solid".equals(type)) {
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
                drawBitmapCrop(canvas, blurredPreview(art), panelRect, 105);
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
            drawBitmapCrop(canvas, blurredPreview(art), panelRect, 255);
        } else {
            paint.setShader(new LinearGradient(0f, 0f, getWidth(), getHeight(),
                    palette[0], palette[3], Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
        }
        paint.setShader(null);
        int dim = Math.round(clamp(backgroundDim / 100f) * 255f);
        paint.setColor(light ? Color.argb(dim, 255, 255, 255)
                : Color.argb(dim, 0, 0, 0));
        canvas.drawRect(panelRect, paint);
        canvas.restoreToCount(save);
        canvas.restoreToCount(layer);
        paint.setShader(null);
        paint.setAlpha(255);
    }

    private boolean refinedUsesLightColors() {
        if ("light".equals(refinedColorScheme)) return true;
        if ("dark".equals(refinedColorScheme)) return false;
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
        float width = getWidth();
        float height = getHeight();
        drawArtworkBackground(canvas, snapshot.albumArt, 0xFFE9DFD0, 0xFFB6A892, false);
        float pad = Math.max(13f * density, width * 0.035f);
        float coverSize = Math.min(height * 0.32f, width * 0.18f) * coverScale;
        coverSize = Math.max(46f * density, Math.min(coverSize, height * 0.42f));
        coverRect.set(pad, pad, pad + coverSize, pad + coverSize);
        RectF cover = coverRect;
        drawCover(canvas, snapshot.albumArt, cover, 9f * density, 0xFFD2C4B2);

        float metaLeft = cover.right + 13f * density;
        float metaWidth = width - metaLeft - pad;
        drawLeft(canvas, snapshot.active ? snapshot.title : "等待音乐", metaLeft,
                cover.top + 20f * density, 16f * density * textScale,
                0xFF25211D, metaWidth, Typeface.BOLD);
        drawLeft(canvas, snapshot.artist, metaLeft, cover.top + 40f * density,
                11f * density * textScale, 0xB85A5148, metaWidth, Typeface.NORMAL);
        drawLeft(canvas, snapshot.sourceName + sourceSuffix(snapshot), metaLeft,
                cover.top + 58f * density, 9.5f * density * textScale,
                0x985A5148, metaWidth, Typeface.NORMAL);
        float progressY = Math.max(cover.bottom + 11f * density, height * 0.38f);
        drawProgress(canvas, pad, progressY, width - pad, 2f * density,
                snapshot, 0x405A5148, 0xFF4D453E);

        float lyricY = progressY + 34f * density + browseVisualOffsetPx;
        float lyricWidth = width - pad * 2f;
        if (lyricLineCount >= 3) {
            drawLeft(canvas, snapshot.lyrics.previousLyric, pad, lyricY,
                    12f * density * textScale, 0x705A5148, lyricWidth, Typeface.BOLD);
            lyricY += 25f * density;
        }
        float pipLyricSize = 20f * density * textScale * (secondary ? 1.06f : 1f);
        float currentHeight;
        if (snapshot.lyrics.interlude) {
            drawInterludeDots(canvas, snapshot, pad, lyricY - pipLyricSize * 0.55f,
                    pipLyricSize * 0.35f, 0xFF181513);
            currentHeight = pipLyricSize * 1.22f;
        } else {
            currentHeight = drawWrappedKaraoke(canvas, snapshot, currentText(snapshot), pad,
                    lyricY - pipLyricSize, pipLyricSize, lyricWidth, 0xFF181513, 2);
        }
        if (!snapshot.lyrics.translatedLyric.isEmpty()) {
            drawLeft(canvas, snapshot.lyrics.translatedLyric, pad,
                    lyricY - pipLyricSize + currentHeight + 14f * density,
                    11f * density * textScale, 0xA85A5148, lyricWidth, Typeface.NORMAL);
            lyricY += 18f * density;
        }
        if (lyricLineCount >= 2) {
            drawLeft(canvas, snapshot.lyrics.nextLyric, pad,
                    lyricY - pipLyricSize + currentHeight + 36f * density,
                    14f * density * textScale, 0x985A5148, lyricWidth, Typeface.BOLD);
        }
    }

    private void drawCustom(Canvas canvas, MusicSnapshot snapshot, float density) {
        drawArtworkBackground(canvas, snapshot.albumArt, 0xFF101822, 0xFF050A10, true);
        float width = getWidth();
        float height = getHeight();
        float pad = 10f * density;
        for (LyricsLayoutConfig.Item item : layoutConfig.items()) {
            if (!item.enabled) continue;
            float x = clamp(item.x) * width;
            float y = clamp(item.y) * height;
            if (isCustomLyricItem(item.id)) y += browseVisualOffsetPx;
            float maxWidth = Math.max(40f * density, width - x - pad);
            switch (item.id) {
                case LyricsLayoutConfig.COVER:
                    float size = Math.min(width * 0.30f, height * 0.42f) * coverScale;
                    coverRect.set(x, y, x + size, y + size);
                    drawCover(canvas, snapshot.albumArt, coverRect, 12f * density, 0xFF293442);
                    break;
                case LyricsLayoutConfig.SOURCE:
                    drawLeft(canvas, snapshot.sourceName + sourceSuffix(snapshot), x, y,
                            10f * density * textScale, 0xC86EE7F2, maxWidth, Typeface.BOLD);
                    break;
                case LyricsLayoutConfig.TITLE:
                    drawLeft(canvas, snapshot.active ? snapshot.title : "等待音乐", x, y,
                            17f * density * textScale, Color.WHITE, maxWidth, Typeface.BOLD);
                    break;
                case LyricsLayoutConfig.ARTIST:
                    drawLeft(canvas, snapshot.artist, x, y,
                            11f * density * textScale, 0xB8D4DCE7, maxWidth, Typeface.NORMAL);
                    break;
                case LyricsLayoutConfig.PREVIOUS:
                    drawLeft(canvas, snapshot.lyrics.previousLyric, x, y,
                            12f * density * textScale, 0x7FFFFFFF, maxWidth, Typeface.NORMAL);
                    break;
                case LyricsLayoutConfig.CURRENT:
                    drawKaraoke(canvas, snapshot, currentText(snapshot), x, y,
                            22f * density * textScale, maxWidth, Paint.Align.LEFT,
                            0xFFB1BCCB, 0xFFFFCA66);
                    break;
                case LyricsLayoutConfig.TRANSLATION:
                    drawLeft(canvas, snapshot.lyrics.translatedLyric, x, y,
                            11f * density * textScale, 0xB8D4DCE7, maxWidth, Typeface.NORMAL);
                    break;
                case LyricsLayoutConfig.NEXT:
                    drawLeft(canvas, snapshot.lyrics.nextLyric, x, y,
                            12f * density * textScale, 0x7FFFFFFF, maxWidth, Typeface.NORMAL);
                    break;
                case LyricsLayoutConfig.PROGRESS:
                    drawProgress(canvas, x, y, Math.min(width - pad, x + width * 0.38f),
                            3f * density, snapshot, 0x48FFFFFF, 0xFFFFCA66);
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

    private void drawPanelShadow(Canvas canvas, float radius, int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setShadowLayer(radius * 0.75f, 0f, radius * 0.25f, 0x70000000);
        canvas.drawRoundRect(panelRect, radius, radius, paint);
        paint.clearShadowLayer();
    }

    private void drawArtworkBackground(Canvas canvas, Bitmap art, int fallbackA, int fallbackB,
                                       boolean dark) {
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        float radius = Math.min(getWidth(), getHeight()) * 0.075f;
        int alphaLayer = canvas.saveLayerAlpha(panelRect,
                Math.round(clamp(opacity / 100f) * 255f));
        int save = canvas.save();
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        if (art != null && !art.isRecycled()) {
            Bitmap preview = blurredPreview(art);
            drawBitmapCrop(canvas, preview, panelRect);
        } else {
            paint.setShader(new LinearGradient(0f, 0f, getWidth(), getHeight(), fallbackA,
                    fallbackB, Shader.TileMode.CLAMP));
            canvas.drawRect(panelRect, paint);
            paint.setShader(null);
        }
        int dim = Math.round(clamp(backgroundDim / 100f) * 220f);
        if (dark) dim = Math.max(dim, 70);
        paint.setColor(dark ? Color.argb(dim, 4, 7, 12)
                : Color.argb(Math.min(190, dim + 65), 238, 226, 208));
        canvas.drawRect(panelRect, paint);
        canvas.restoreToCount(save);
        canvas.restoreToCount(alphaLayer);
        paint.setShader(null);
    }

    private Bitmap blurredPreview(Bitmap art) {
        if (art == blurSource && blurPreview != null && !blurPreview.isRecycled()) return blurPreview;
        if (blurPreview != null && blurPreview != blurSource && !blurPreview.isRecycled()) {
            blurPreview.recycle();
        }
        blurSource = art;
        int sample = 6 + Math.round(backgroundBlur * 0.46f);
        int width = Math.max(2, art.getWidth() / sample);
        int height = Math.max(2, art.getHeight() / sample);
        blurPreview = Bitmap.createScaledBitmap(art, width, height, true);
        return blurPreview;
    }

    @Override protected void onDetachedFromWindow() {
        if (blurPreview != null && blurPreview != blurSource && !blurPreview.isRecycled()) {
            blurPreview.recycle();
        }
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
        paint.setAlpha(Math.max(0, Math.min(255, alpha)));
        canvas.drawBitmap(bitmap, sourceRect, destination, paint);
        paint.setAlpha(255);
    }

    private void drawProgress(Canvas canvas, float left, float top, float right, float height,
                              MusicSnapshot snapshot, int trackColor, int activeColor) {
        if (right <= left) return;
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
        if (!snapshot.lyricLoaded && !snapshot.lyricAvailable) return "正在匹配歌词…";
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
        List<WrappedChunk> chunks = wrapText(value.replace('\n', ' '), maxWidth, maxLines);
        float lineHeight = size * 1.22f;
        int highlightEnd;
        LrcTimeline.At at = snapshot.lyrics;
        if (!snapshot.lyricAvailable || at.lyric.isEmpty()) {
            highlightEnd = 0;
        } else if (!at.wordTimed) {
            highlightEnd = value.length();
        } else {
            int revealed = LrcTimeline.revealedCodePointCount(
                    at.currentWord, at.wordProgressPermille);
            int currentEnd = at.currentWord.offsetByCodePoints(0, revealed);
            highlightEnd = Math.min(value.length(),
                    at.completedLyric.length() + currentEnd);
        }
        paint.setTextAlign(Paint.Align.LEFT);
        for (int i = 0; i < chunks.size(); i++) {
            WrappedChunk chunk = chunks.get(i);
            float baseline = top + size + i * lineHeight;
            paint.setColor(withAlpha(activeColor, 105));
            canvas.drawText(chunk.text, x, baseline, paint);
            int activeChars = Math.max(0, Math.min(chunk.end, highlightEnd) - chunk.start);
            if (activeChars <= 0) continue;
            int safeChars = Math.min(activeChars, chunk.text.length());
            float activeWidth = safeChars >= chunk.text.length()
                    ? paint.measureText(chunk.text)
                    : paint.measureText(chunk.text.substring(0, safeChars));
            int save = canvas.save();
            canvas.clipRect(x, baseline - size * 1.18f,
                    x + activeWidth, baseline + size * 0.30f);
            paint.setColor(activeColor);
            if (!"refined".equals(overlayStyle) || refinedLyricGlow) {
                paint.setShadowLayer(Math.max(3f, size * 0.24f), 0f, 0f,
                        withAlpha(activeColor, 90));
            }
            canvas.drawText(chunk.text, x, baseline, paint);
            paint.clearShadowLayer();
            canvas.restoreToCount(save);
        }
        return chunks.size() * lineHeight;
    }

    private float drawWrappedText(Canvas canvas, String value, float x, float top, float size,
                                  int color, float maxWidth, int style, int maxLines) {
        if (value == null || value.isEmpty()) return 0f;
        setTextPaint(size, style);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(color);
        List<WrappedChunk> chunks = wrapText(value.replace('\n', ' '), maxWidth, maxLines);
        float lineHeight = size * 1.22f;
        for (int i = 0; i < chunks.size(); i++) {
            canvas.drawText(chunks.get(i).text, x, top + size + i * lineHeight, paint);
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

    /** Word timing is intentionally quantized to whole Unicode characters. */
    private void drawKaraoke(Canvas canvas, MusicSnapshot snapshot, String value, float anchorX,
                              float y, float requestedSize, float maxWidth, Paint.Align align,
                              int baseColor, int activeColor) {
        if (value == null || value.isEmpty()) return;
        float size = fitSize(value, requestedSize, maxWidth, Typeface.BOLD);
        setTextPaint(size, Typeface.BOLD);
        paint.setTextAlign(align);
        String text = ellipsize(value.replace('\n', ' '), maxWidth);
        float textWidth = paint.measureText(text);
        float left = align == Paint.Align.CENTER ? anchorX - textWidth / 2f : anchorX;
        paint.setColor(baseColor);
        canvas.drawText(text, anchorX, y, paint);
        if (!snapshot.lyricAvailable || snapshot.lyrics.lyric.isEmpty()) return;

        LrcTimeline.At at = snapshot.lyrics;
        if (!at.wordTimed) {
            paint.setColor(activeColor);
            if ("refined".equals(overlayStyle)) {
                applyRefinedTextEffect(size, activeColor, 255);
            }
            canvas.drawText(text, anchorX, y, paint);
            paint.clearShadowLayer();
            return;
        }
        int revealed = LrcTimeline.revealedCodePointCount(
                at.currentWord, at.wordProgressPermille);
        int charEnd = at.currentWord.offsetByCodePoints(0, revealed);
        float highlightedWidth = paint.measureText(at.completedLyric)
                + paint.measureText(at.currentWord.substring(0, charEnd));
        int save = canvas.save();
        float activeY = "refined".equals(overlayStyle)
                && "float".equals(refinedKaraokeAnimation) ? y - size * 0.06f : y;
        canvas.clipRect(left, y - size * 1.25f,
                left + Math.min(textWidth, highlightedWidth), y + size * 0.35f);
        paint.setColor(activeColor);
        boolean glow = !"refined".equals(overlayStyle) || refinedLyricGlow;
        if (glow) {
            paint.setShadowLayer(Math.max(4f, size * 0.35f), 0f, 0f,
                    Color.argb(100, Color.red(activeColor), Color.green(activeColor),
                            Color.blue(activeColor)));
        }
        canvas.drawText(text, anchorX, activeY, paint);
        paint.clearShadowLayer();
        canvas.restoreToCount(save);
    }

    private void drawCentered(Canvas canvas, String value, float y, float requestedSize,
                              int color, float maxWidth, int style) {
        if (value == null || value.isEmpty()) return;
        float size = fitSize(value, requestedSize, maxWidth, style);
        setTextPaint(size, style);
        paint.setTextAlign(Paint.Align.CENTER);
        String text = ellipsize(value.replace('\n', ' '), maxWidth);
        paint.setColor(color);
        canvas.drawText(text, getWidth() / 2f, y, paint);
    }

    private void drawLeft(Canvas canvas, String value, float x, float y, float requestedSize,
                          int color, float maxWidth, int style) {
        if (value == null || value.isEmpty()) return;
        float size = fitSize(value, requestedSize, maxWidth, style);
        setTextPaint(size, style);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(color);
        canvas.drawText(ellipsize(value.replace('\n', ' '), maxWidth), x, y, paint);
    }

    private float fitSize(String value, float requested, float maxWidth, int style) {
        setTextPaint(requested, style);
        float measured = paint.measureText(value == null ? "" : value);
        if (measured <= maxWidth || measured <= 0f) return requested;
        return Math.max(requested * 0.62f, requested * maxWidth / measured);
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
        paint.setTypeface(style == Typeface.BOLD ? SANS_BOLD : SANS_NORMAL);
    }

    private void clearTextCaches() {
        wrappedTextCache.evictAll();
        ellipsizedTextCache.evictAll();
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

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
