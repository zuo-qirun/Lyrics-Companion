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
    private int lyricColor;
    private float nextLyricScale = 0.70f;
    private int nextLyricOpacity = 100;
    private boolean smoothLyricScroll = true;
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
    private long lastBasicLineStartMs = Long.MIN_VALUE;
    private long basicLyricScrollAnimationStartedMs;
    private Typeface customTypeface;
    private String compactMarqueeText = "";
    private long compactMarqueeElapsedMs;
    private long compactMarqueeLastFrameMs;
    private boolean compactMarqueeActive;
    private final SpectrumMath.BarTracker compactSpectrumBars =
            new SpectrumMath.BarTracker(SpectrumMath.BAND_COUNT);
    private final float[] compactVirtualSpectrum = new float[SpectrumMath.BAND_COUNT];
    private boolean compactSpectrumAnimating;

    LyricsPanelView(Context context) { this(context, false); }

    LyricsPanelView(Context context, boolean secondary) {
        super(context);
        this.secondary = secondary;
        reloadStyle();
    }

    void reloadStyle() {
        textScale = AppPreferences.textScale(getContext(), secondary);
        coverScale = AppPreferences.styleCoverScale(getContext(), secondary);
        opacity = AppPreferences.opacity(getContext(), secondary);
        lyricOffsetMs = AppPreferences.lyricOffsetMs(getContext(), secondary);
        lyricColor = AppPreferences.lyricColor(getContext(), secondary);
        nextLyricScale = AppPreferences.nextLyricScale(getContext(), secondary) / 100f;
        nextLyricOpacity = AppPreferences.nextLyricOpacity(getContext(), secondary);
        smoothLyricScroll = AppPreferences.smoothLyricScroll(getContext(), secondary);
        backgroundBlur = AppPreferences.styleBlur(getContext(), secondary);
        backgroundDim = AppPreferences.styleDim(getContext(), secondary);
        lyricLineCount = AppPreferences.styleLyricLines(getContext(), secondary);
        overlayStyle = AppPreferences.overlayStyle(getContext(), secondary);
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
        setLayerType(usesRefinedVisualStyle() && refinedLyricBlur
                ? LAYER_TYPE_SOFTWARE : LAYER_TYPE_NONE, null);
        layoutConfig = LyricsLayoutConfig.load(getContext(), secondary);
        recycleBlurPreview();
        blurPreview = null;
        blurSource = null;
        clearTextCaches();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        compactSpectrumAnimating = false;
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
        lyricOffsetMs = AppPreferences.lyricOffsetMs(getContext(), secondary,
                MusicStateStore.activeSourceId());
        MusicSnapshot snapshot = browsingLyrics || browseUntilElapsedMs > now
                ? MusicStateStore.snapshotForLyricBrowse(lyricOffsetMs, browsePositionMs)
                : MusicStateStore.snapshot(lyricOffsetMs);

        if ("refined".equals(overlayStyle)) {
            drawRefined(canvas, snapshot, density);
        } else if ("compact".equals(overlayStyle)) {
            drawCompact(canvas, snapshot, density);
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
        if (!secondary) drawPlaybackControls(canvas, snapshot, density);
        scheduleNextFrame(nextFrameDelay(snapshot, now));
    }

    private void scheduleNextFrame(long delayMs) {
        // A fixed 16 ms delay is not synchronized with the display. On some devices the
        // runnable lands just after VSync, causing the first part of a lyric transition to
        // alternate between frames. Let the compositor schedule animation frames instead.
        if (delayMs <= 16L) {
            postInvalidateOnAnimation();
        } else {
            postInvalidateDelayed(delayMs);
        }
    }

    private long nextFrameDelay(MusicSnapshot snapshot, long nowElapsedMs) {
        if (browsingLyrics || browseSettling) return 16L;
        if (compactSpectrumAnimating) return 16L;
        if (compactMarqueeActive && snapshot.playing) return 16L;
        if (lyricScrollAnimationStartedMs > 0L
                && nowElapsedMs - lyricScrollAnimationStartedMs < 500L) return 16L;
        if (basicLyricScrollAnimationStartedMs > 0L
                && nowElapsedMs - basicLyricScrollAnimationStartedMs < 360L) return 16L;
        if (browseUntilElapsedMs > nowElapsedMs) {
            return Math.max(16L, Math.min(250L, browseUntilElapsedMs - nowElapsedMs));
        }
        if (!snapshot.active) return 750L;
        if (!snapshot.playing) return 400L;
        // Fluid and dynamic-gradient backgrounds are time-based.  The old 100 ms idle
        // cadence made them visibly step at 10 fps even when no word-timed lyric was active.
        if (hasAnimatedRefinedBackground()) return 33L;
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

    private boolean hasAnimatedRefinedBackground() {
        if (!usesRefinedVisualStyle()) return false;
        return ("fluid".equals(refinedBackgroundType) && !refinedStaticFluid)
                || ("gradient".equals(refinedBackgroundType) && refinedDynamicGradient);
    }

    boolean isLyricGestureRegion(float x, float y) {
        if (getWidth() <= 0 || getHeight() <= 0) return false;
        if (playbackControlAt(x, y) != null) return false;
        MusicSnapshot snapshot = MusicStateStore.snapshot(effectiveLyricOffsetMs());
        if (!snapshot.lyricAvailable) return false;
        if ("refined".equals(overlayStyle)) {
            return !"cover".equals(refinedDisplayMode)
                    && ("lyrics".equals(refinedDisplayMode) || x >= getWidth() * 0.46f);
        }
        // The compact layout has no spare non-lyric area. Reserve its full surface for moving
        // the overlay instead of swallowing every touch as lyric browsing.
        if ("compact".equals(overlayStyle)) return false;
        if ("pip".equals(overlayStyle)) return y >= getHeight() * 0.34f;
        if ("custom".equals(overlayStyle)) return y >= getHeight() * 0.28f;
        return y >= getHeight() * 0.24f && y <= getHeight() * 0.86f;
    }

    MediaControlAction playbackControlAt(float x, float y) {
        if (secondary || getWidth() <= 0 || getHeight() <= 0) return null;
        float density = getResources().getDisplayMetrics().density;
        PlaybackControlLayout layout = playbackControlLayout(density);
        if (AppPreferences.showPreviousButton(getContext())
                && insideCircle(x, y, layout.centerX - layout.spacing, layout.centerY,
                layout.radius)) {
            return MediaControlAction.PREVIOUS;
        }
        if (AppPreferences.showPlayPauseButton(getContext())
                && insideCircle(x, y, layout.centerX, layout.centerY, layout.radius * 1.12f)) {
            return MediaControlAction.TOGGLE_PLAY_PAUSE;
        }
        if (AppPreferences.showNextButton(getContext())
                && insideCircle(x, y, layout.centerX + layout.spacing, layout.centerY,
                layout.radius)) {
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

    private boolean usesRefinedVisualStyle() {
        return "refined".equals(overlayStyle) || "compact".equals(overlayStyle);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void drawPlaybackControls(Canvas canvas, MusicSnapshot snapshot, float density) {
        boolean showPrevious = AppPreferences.showPreviousButton(getContext());
        boolean showPlayPause = AppPreferences.showPlayPauseButton(getContext());
        boolean showNext = AppPreferences.showNextButton(getContext());
        if (!showPrevious && !showPlayPause && !showNext) return;

        PlaybackControlLayout layout = playbackControlLayout(density);
        int fill = snapshot.active ? 0xC92B405A : 0x8A26384E;
        int icon = snapshot.active ? 0xFFF5F9FF : 0xFF9AAABB;
        int accent = snapshot.playing ? 0xFFFFCA66 : 0xFF6EE7F2;
        int primaryFill = snapshot.active ? 0xE0445D78 : fill;
        if ("refined".equals(overlayStyle)) {
            fill = snapshot.active ? 0x8C243B52 : 0x62243852;
            primaryFill = snapshot.active ? 0xC13A5872 : fill;
        } else if ("compact".equals(overlayStyle)) {
            workRect.set(layout.centerX - layout.spacing - layout.radius * 1.65f,
                    layout.centerY - layout.radius * 1.42f,
                    layout.centerX + layout.spacing + layout.radius * 1.65f,
                    layout.centerY + layout.radius * 1.42f);
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
        }

        if (showPrevious) {
            drawPlaybackButton(canvas, layout.centerX - layout.spacing, layout.centerY,
                    layout.radius, fill, icon, MediaControlAction.PREVIOUS, false);
        }
        if (showPlayPause) {
            drawPlaybackButton(canvas, layout.centerX, layout.centerY, layout.radius * 1.12f,
                    primaryFill, accent, MediaControlAction.TOGGLE_PLAY_PAUSE, snapshot.playing);
        }
        if (showNext) {
            drawPlaybackButton(canvas, layout.centerX + layout.spacing, layout.centerY,
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
                Path triangle = new Path();
                triangle.moveTo(centerX - iconHalf * 0.52f, centerY - iconHalf);
                triangle.lineTo(centerX - iconHalf * 0.52f, centerY + iconHalf);
                triangle.lineTo(centerX + iconHalf, centerY);
                triangle.close();
                canvas.drawPath(triangle, paint);
            }
            return;
        }
        boolean previous = action == MediaControlAction.PREVIOUS;
        float direction = previous ? -1f : 1f;
        float baseX = centerX - direction * iconHalf;
        Path triangle = new Path();
        triangle.moveTo(baseX, centerY - iconHalf);
        triangle.lineTo(baseX, centerY + iconHalf);
        triangle.lineTo(centerX + direction * iconHalf * 0.78f, centerY);
        triangle.close();
        canvas.drawPath(triangle, paint);
        float barX = centerX + direction * iconHalf * 1.05f;
        canvas.drawRect(barX - radius * 0.075f, centerY - iconHalf,
                barX + radius * 0.075f, centerY + iconHalf, paint);
    }

    private PlaybackControlLayout playbackControlLayout(float density) {
        float width = getWidth();
        float height = getHeight();
        float radius = Math.max(10f * density, Math.min(16f * density, height * 0.095f));
        float spacing = radius * 2.85f;
        float centerX = width * 0.5f;
        float centerY = height - radius - 7f * density;
        if ("refined".equals(overlayStyle)) {
            radius = Math.max(11f * density, Math.min(16f * density, height * 0.07f));
            spacing = radius * 2.7f;
            centerX = width * 0.75f;
            centerY = radius + 18f * density;
        } else if ("compact".equals(overlayStyle)) {
            radius = Math.max(7f * density, Math.min(10f * density, height * 0.085f));
            spacing = radius * 2.45f;
            centerX = width * 0.38f;
            centerY = height - radius - 11f * density;
        } else if ("pip".equals(overlayStyle)) {
            radius = Math.max(9f * density, Math.min(13f * density, height * 0.075f));
            spacing = radius * 2.6f;
            centerX = width - (spacing + radius + 14f * density);
            centerY = height - radius - 12f * density;
        } else if ("custom".equals(overlayStyle)) {
            radius = Math.max(9f * density, Math.min(14f * density, height * 0.085f));
            spacing = radius * 2.7f;
            centerX = width - (spacing + radius + 14f * density);
            centerY = height - radius - 12f * density;
        }
        float horizontalInset = spacing + radius * 1.18f;
        centerX = Math.max(horizontalInset, Math.min(width - horizontalInset, centerX));
        centerY = Math.max(radius + 4f * density,
                Math.min(height - radius - 4f * density, centerY));
        return new PlaybackControlLayout(centerX, centerY, radius, spacing);
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

    private void drawDefault(Canvas canvas, MusicSnapshot snapshot, float density) {
        float pad = 18f * density;
        float width = getWidth();
        float height = getHeight();
        if (opacity > 0) {
            drawPanelShadow(canvas, 24f * density,
                    Color.argb(Math.round(opacity * 2.55f), 6, 15, 27));
            paint.setColor(withAlpha(0x406EE7F2,
                    Math.round(Color.alpha(0x406EE7F2) * opacity / 100f)));
            workRect.set(pad, 10f * density, width - pad, 13f * density);
            canvas.drawRoundRect(workRect, 2f * density, 2f * density, paint);
        }

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
        float basicScrollShift = basicLyricEntryShift(snapshot.lyrics.lineStartMs,
                32f * density * unit);
        drawCentered(canvas, snapshot.lyrics.previousLyric, y + previewShift + basicScrollShift,
                12f * density * textScale * unit, lyricColor(0xFF68778C), usableWidth,
                Typeface.NORMAL);
        y += 32f * density * unit;
        if (snapshot.lyrics.interlude) {
            float dotRadius = 22f * density * textScale * unit * 0.35f;
            float dotWidth = interludeDotsWidth(dotRadius);
            drawInterludeDots(canvas, snapshot, width / 2f - dotWidth / 2f,
                    y + previewShift + basicScrollShift - dotRadius, dotRadius,
                    lyricColor(0xFFFFCA66));
        } else {
            drawKaraoke(canvas, snapshot, currentText(snapshot), width / 2f,
                    y + previewShift + basicScrollShift,
                    22f * density * textScale * unit, usableWidth, Paint.Align.CENTER,
                    lyricColor(0xFFB1BCCB), lyricColor(0xFFFFCA66));
        }
        if (!snapshot.lyrics.translatedLyric.isEmpty()) {
            y += 24f * density * unit;
            drawCentered(canvas, snapshot.lyrics.translatedLyric,
                    y + previewShift + basicScrollShift,
                    12f * density * textScale * unit, lyricColor(0xFFB8C5D8), usableWidth,
                    Typeface.NORMAL);
        }
        float controlReserve = secondary ? 0f : 31f * density;
        drawCentered(canvas, snapshot.lyrics.nextLyric,
                height - 37f * density - controlReserve + previewShift,
                nextLyricSize(22f * density * textScale * unit),
                nextLyricColor(lyricColor(0xFF68778C)), usableWidth, Typeface.NORMAL);
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
                    lyricColor(primaryText), lyricColor(secondaryText));
        }
        float progressY = refinedProgressBottom ? height - 2f * density
                : height - 10f * density;
        drawProgress(canvas, pad, progressY, width - pad, 2f * density,
                snapshot, withAlpha(primaryText, 48), withAlpha(primaryText, 225));
        canvas.restoreToCount(contentSave);
    }

    /** A small horizontal media strip: lyric-led, with cover artwork as a side anchor. */
    private void drawCompact(Canvas canvas, MusicSnapshot snapshot, float density) {
        float width = getWidth();
        float height = getHeight();
        updatePalette(snapshot.albumArt);
        boolean light = refinedUsesLightColors();
        int accent = refinedAccentColor();
        int primaryText = light ? mix(accent, Color.BLACK, 0.72f)
                : mix(accent, Color.WHITE, 0.78f);
        drawRefinedBackground(canvas, snapshot.albumArt, light, accent, snapshot.playing);

        int save = canvas.save();
        float radius = Math.min(width, height) * 0.18f;
        clipPath.reset();
        clipPath.addRoundRect(panelRect, radius, radius, Path.Direction.CW);
        canvas.clipPath(clipPath);
        float pad = Math.max(7f * density, width * 0.028f);
        boolean showCover = AppPreferences.compactShowCover(getContext(), secondary);
        boolean showBars = AppPreferences.compactShowBars(getContext(), secondary);
        float barsHeight = showBars ? Math.min(height * 0.28f,
                Math.max(22f * density, height * 0.20f)) : 0f;
        float barsTop = showBars ? height - pad * 0.42f - barsHeight : height - pad;
        float coverLeft = width - pad;
        if (showCover) {
            float coverTop = pad * 0.62f;
            // Reserve the area below the cover for the title and artist. This keeps the
            // compact layout readable even at its minimum allowed height.
            float titleSize = Math.max(8f * density, Math.min(10.5f * density, height * 0.10f));
            float artistSize = Math.max(7f * density, Math.min(8.5f * density, height * 0.08f));
            float metadataHeight = titleSize + artistSize + 5f * density;
            float coverSize = Math.min(barsTop - coverTop - metadataHeight,
                    Math.min(width * 0.23f, 58f * density));
            coverSize = Math.max(30f * density, coverSize);
            coverLeft = width - pad - coverSize;
            coverRect.set(coverLeft, coverTop, coverLeft + coverSize, coverTop + coverSize);
            drawCover(canvas, snapshot.albumArt, coverRect, 10f * density,
                    mix(accent, Color.DKGRAY, 0.55f));
            float titleY = coverRect.bottom + 2f * density + titleSize;
            float artistY = titleY + artistSize + 3f * density;
            drawRefinedText(canvas, snapshot.active ? snapshot.title : "等待音乐",
                    coverRect.centerX(), titleY, titleSize, primaryText, coverRect.width(),
                    Paint.Align.CENTER, Typeface.BOLD, 255);
            drawRefinedText(canvas, snapshot.artist, coverRect.centerX(), artistY, artistSize,
                    withAlpha(primaryText, 175), coverRect.width(), Paint.Align.CENTER,
                    Typeface.NORMAL, 175);
        }

        float lyricLeft = pad;
        float lyricWidth = Math.max(1f, coverLeft - lyricLeft - 4f * density);
        float lyricTop = showCover ? coverRect.top : pad;
        float lyricBottom = barsTop - 3f * density;
        float lyricStageHeight = Math.max(1f, lyricBottom - lyricTop);
        // The compact card is still a lyric surface, not a metadata row. Its main lyric uses
        // a dedicated large type scale; overflow is handled by the tracked karaoke viewport
        // below instead of shrinking the letters to fit.
        float lyricSize = Math.max(22f * density,
                Math.min(refinedLyricFontSize * density * textScale * 1.50f,
                        lyricStageHeight * 0.56f));
        boolean showTranslation = refinedShowTranslation
                && !snapshot.lyrics.translatedLyric.isEmpty();
        float translationSize = lyricSize * 0.48f;
        setTextPaint(lyricSize, Typeface.BOLD);
        float originalAscent = paint.ascent();
        float originalDescent = paint.descent();
        setTextPaint(translationSize, Typeface.NORMAL);
        float translationAscent = paint.ascent();
        float translationDescent = paint.descent();
        float lineGap = Math.max(3f * density, lyricSize * 0.12f);
        float groupHeight = showTranslation
                ? originalDescent - originalAscent + lineGap
                + translationDescent - translationAscent
                : originalDescent - originalAscent;
        float groupTop = lyricTop + Math.max(0f, (lyricStageHeight - groupHeight) * 0.5f);
        float baseline = groupTop - originalAscent;
        float translationBaseline = baseline + originalDescent + lineGap - translationAscent;
        // Preserve the familiar lyric-then-translation reading order and center the complete
        // pair in the stage to the left of the cover.
        if (showTranslation) {
            drawRefinedText(canvas, snapshot.lyrics.translatedLyric,
                    lyricLeft + lyricWidth * 0.5f, translationBaseline, translationSize,
                    lyricColor(primaryText), lyricWidth, Paint.Align.CENTER, Typeface.NORMAL, 165);
        }
        if (snapshot.lyrics.interlude) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            float dotRadius = lyricSize * 0.20f;
            float dotWidth = interludeDotsWidth(dotRadius);
            drawInterludeDots(canvas, snapshot, lyricLeft + (lyricWidth - dotWidth) * 0.5f,
                    baseline - lyricSize * 0.72f,
                    dotRadius, lyricColor(primaryText));
        } else {
            drawCompactMarqueeKaraoke(canvas, snapshot, currentText(snapshot), lyricLeft,
                    baseline, lyricSize, lyricWidth, density,
                    lyricColor(withAlpha(primaryText, 120)), lyricColor(primaryText));
        }
        if (showBars) {
            drawCompactPlaybackBars(canvas, snapshot, lyricLeft, barsTop,
                    width - pad, barsHeight, lyricColor(primaryText));
        }
        canvas.restoreToCount(save);
    }

    /** Draws real FFT bands when permitted, otherwise the user-selected virtual or static mode. */
    private void drawCompactPlaybackBars(Canvas canvas, MusicSnapshot snapshot, float left,
                                         float top, float right, float height, int color) {
        if (right <= left || height <= 0f) return;
        float width = right - left;
        int count = SpectrumMath.BAND_COUNT;
        float slot = width / count;
        float barWidth = Math.max(1f, slot * 0.58f);
        boolean useRealSpectrum = AppPreferences.compactUseRealSpectrum(getContext(), secondary);
        AudioSpectrumSource.Frame frame = AudioSpectrumSource.latestFrame();
        boolean realSpectrumLive = useRealSpectrum && frame.live;
        boolean virtualSpectrum = !useRealSpectrum;
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
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        for (int i = 0; i < count; i++) {
            float level = targets == null ? 0.10f : compactSpectrumBars.barAt(i);
            float barHeight = Math.max(1f, height * level);
            float segmentStart = i / (float) count;
            float segmentFill = clamp((progress - segmentStart) * count);
            int alpha = Math.round(72f + (220f - 72f) * segmentFill);
            if (targets == null) alpha = Math.min(alpha, 76);
            paint.setColor(withAlpha(color, alpha));
            float x = left + i * slot + (slot - barWidth) * 0.5f;
            progressRect.set(x, top + height - barHeight, x + barWidth, top + height);
            canvas.drawRoundRect(progressRect, barWidth * 0.5f, barWidth * 0.5f, paint);
        }
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
    private void drawCompactMarqueeKaraoke(Canvas canvas, MusicSnapshot snapshot, String value,
                                           float x, float y, float requestedSize, float maxWidth,
                                           float density, int baseColor, int activeColor) {
        if (value == null || value.isEmpty()) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            return;
        }
        String text = value.replace('\n', ' ');
        setTextPaint(requestedSize, Typeface.BOLD);
        paint.setTextAlign(Paint.Align.LEFT);
        float textWidth = paint.measureText(text);
        if (textWidth <= maxWidth) {
            compactMarqueeActive = false;
            compactMarqueeText = "";
            compactMarqueeElapsedMs = 0L;
            drawKaraoke(canvas, snapshot, text, x + maxWidth * 0.5f, y, requestedSize,
                    maxWidth, Paint.Align.CENTER,
                    baseColor, activeColor);
            return;
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
            int revealed = LrcTimeline.revealedCodePointCount(
                    at.currentWord, at.wordProgressPermille);
            int charEnd = at.currentWord.offsetByCodePoints(0, revealed);
            float highlightedWidth = paint.measureText(at.completedLyric)
                    + paint.measureText(at.currentWord.substring(0, charEnd));
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
        paint.setColor(baseColor);
        canvas.drawText(text, drawX, y, paint);
        if (!snapshot.lyricAvailable || snapshot.lyrics.lyric.isEmpty()) {
            paint.setColor(activeColor);
            canvas.drawText(text, drawX, y, paint);
        } else if (!snapshot.lyrics.wordTimed) {
            paint.setColor(activeColor);
            applyRefinedTextEffect(requestedSize, activeColor, 255);
            canvas.drawText(text, drawX, y, paint);
            paint.clearShadowLayer();
        } else {
            LrcTimeline.At at = snapshot.lyrics;
            int revealed = LrcTimeline.revealedCodePointCount(
                    at.currentWord, at.wordProgressPermille);
            int charEnd = at.currentWord.offsetByCodePoints(0, revealed);
            float highlightedWidth = paint.measureText(at.completedLyric)
                    + paint.measureText(at.currentWord.substring(0, charEnd));
            int highlightSave = canvas.save();
            canvas.clipRect(drawX, y - requestedSize * 1.25f,
                    drawX + Math.min(textWidth, highlightedWidth), y + requestedSize * 0.35f);
            paint.setColor(activeColor);
            if (refinedLyricGlow) {
                paint.setShadowLayer(Math.max(3f, requestedSize * 0.24f), 0f, 0f,
                        withAlpha(activeColor, 90));
            }
            canvas.drawText(text, drawX, y, paint);
            paint.clearShadowLayer();
            canvas.restoreToCount(highlightSave);
        }
        canvas.restoreToCount(save);
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
            if (offset == 1) opacity *= nextLyricOpacity / 100f;
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
            if (refinedLyricBlur && offset != 0) {
                paint.setMaskFilter(new BlurMaskFilter(
                        Math.min(4.5f * density, (0.5f + Math.abs(offset)) * density),
                        BlurMaskFilter.Blur.NORMAL));
            }
            if (line.interlude) {
                drawInterludeDots(canvas, snapshot, lineLeft, top + fontSize * 0.30f,
                        fontSize * 0.35f,
                        withAlpha(primaryText, Math.round(225f * opacity)));
            } else if (offset == 0) {
                drawWrappedKaraoke(canvas, snapshot, currentText(snapshot), lineLeft, top,
                        fontSize, width, primaryText, 3);
            } else {
                drawWrappedText(canvas, line.text, lineLeft, top, fontSize,
                        withAlpha(secondaryText, Math.round(opacity * 255f)), width,
                        refinedOriginalBold ? Typeface.BOLD : Typeface.NORMAL, 3);
            }
            if (!line.interlude && refinedShowTranslation && !line.translated.isEmpty()) {
                float originalHeight = wrappedTextHeight(line.text, fontSize, width, 3);
                drawWrappedText(canvas, line.translated, lineLeft,
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

    private int lyricColor(int fallback) {
        return lyricColor == 0 ? fallback : withAlpha(lyricColor, Color.alpha(fallback));
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
        int layer = saveLayerAlphaCompat(canvas, panelRect,
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
                    12f * density * textScale, lyricColor(0x705A5148), lyricWidth,
                    Typeface.BOLD);
            lyricY += 25f * density;
        }
        float pipLyricSize = 20f * density * textScale * (secondary ? 1.06f : 1f);
        float currentHeight;
        if (snapshot.lyrics.interlude) {
            drawInterludeDots(canvas, snapshot, pad, lyricY - pipLyricSize * 0.55f,
                    pipLyricSize * 0.35f, lyricColor(0xFF181513));
            currentHeight = pipLyricSize * 1.22f;
        } else {
            currentHeight = drawWrappedKaraoke(canvas, snapshot, currentText(snapshot), pad,
                    lyricY - pipLyricSize, pipLyricSize, lyricWidth,
                    lyricColor(0xFF181513), 2);
        }
        if (!snapshot.lyrics.translatedLyric.isEmpty()) {
            drawLeft(canvas, snapshot.lyrics.translatedLyric, pad,
                    lyricY - pipLyricSize + currentHeight + 14f * density,
                    11f * density * textScale, lyricColor(0xA85A5148), lyricWidth,
                    Typeface.NORMAL);
            lyricY += 18f * density;
        }
        if (lyricLineCount >= 2) {
            drawLeft(canvas, snapshot.lyrics.nextLyric, pad,
                    lyricY - pipLyricSize + currentHeight + 36f * density,
                    nextLyricSize(pipLyricSize), nextLyricColor(lyricColor(0x985A5148)), lyricWidth,
                    Typeface.BOLD);
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
                            12f * density * textScale, lyricColor(0x7FFFFFFF), maxWidth,
                            Typeface.NORMAL);
                    break;
                case LyricsLayoutConfig.CURRENT:
                    drawKaraoke(canvas, snapshot, currentText(snapshot), x, y,
                            22f * density * textScale, maxWidth, Paint.Align.LEFT,
                            lyricColor(0xFFB1BCCB), lyricColor(0xFFFFCA66));
                    break;
                case LyricsLayoutConfig.TRANSLATION:
                    drawLeft(canvas, snapshot.lyrics.translatedLyric, x, y,
                            11f * density * textScale, lyricColor(0xB8D4DCE7), maxWidth,
                            Typeface.NORMAL);
                    break;
                case LyricsLayoutConfig.NEXT:
                    drawLeft(canvas, snapshot.lyrics.nextLyric, x, y,
                            nextLyricSize(22f * density * textScale),
                            nextLyricColor(lyricColor(0x7FFFFFFF)), maxWidth,
                            Typeface.NORMAL);
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
        int alphaLayer = saveLayerAlphaCompat(canvas, panelRect,
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
            if (usesRefinedVisualStyle()) {
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
        float activeY = usesRefinedVisualStyle()
                && "float".equals(refinedKaraokeAnimation) ? y - size * 0.06f : y;
        canvas.clipRect(left, y - size * 1.25f,
                left + Math.min(textWidth, highlightedWidth), y + size * 0.35f);
        paint.setColor(activeColor);
        boolean glow = !usesRefinedVisualStyle() || refinedLyricGlow;
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
        paint.setTypeface(customTypeface == null
                ? (style == Typeface.BOLD ? SANS_BOLD : SANS_NORMAL)
                : Typeface.create(customTypeface, style));
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

    @SuppressWarnings("deprecation")
    private static int saveLayerAlphaCompat(Canvas canvas, RectF bounds, int alpha) {
        if (Build.VERSION.SDK_INT >= 21) return canvas.saveLayerAlpha(bounds, alpha);
        return canvas.saveLayerAlpha(bounds.left, bounds.top, bounds.right, bounds.bottom,
                alpha, Canvas.ALL_SAVE_FLAG);
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
