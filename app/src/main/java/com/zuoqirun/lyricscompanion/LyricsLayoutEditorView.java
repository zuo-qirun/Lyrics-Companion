package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.LinkedHashMap;
import java.util.Map;

final class LyricsLayoutEditorView extends View {
    interface OnLayoutChangedListener { void onLayoutChanged(); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF preview = new RectF();
    private final RectF palette = new RectF();
    private final Rect bitmapSource = new Rect();
    private final Map<LyricsLayoutConfig.Item, RectF> hitRects = new LinkedHashMap<>();
    private LyricsLayoutConfig config;
    private LyricsLayoutConfig.Item dragging;
    private OnLayoutChangedListener listener;
    private float density;

    LyricsLayoutEditorView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        config = LyricsLayoutConfig.load(context);
        setClickable(true);
        setFocusable(true);
        setContentDescription("歌词悬浮窗拖拽布局编辑器");
    }

    void setOnLayoutChangedListener(OnLayoutChangedListener listener) { this.listener = listener; }

    void reset() {
        config = LyricsLayoutConfig.defaults();
        hitRects.clear();
        config.save(getContext());
        invalidate();
        if (listener != null) listener.onLayoutChanged();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Typeface customTypeface = CustomFontStore.load(getContext());
        paint.setTypeface(customTypeface == null ? Typeface.DEFAULT : customTypeface);
        float margin = dp(16);
        boolean wideLayout = isWideLayout();
        if (wideLayout) {
            float contentWidth = getWidth() - margin * 2f;
            float previewRight = margin + contentWidth * 0.60f - dp(7);
            preview.set(margin, margin, previewRight, getHeight() - margin);
            palette.set(previewRight + dp(14), margin, getWidth() - margin,
                    getHeight() - margin);
        } else {
            preview.set(margin, margin, getWidth() - margin, getHeight() * 0.62f);
            palette.set(margin, getHeight() * 0.68f, getWidth() - margin,
                    getHeight() - margin);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF101820);
        canvas.drawRoundRect(preview, dp(24), dp(24), paint);
        paint.setColor(0xFF1D252D);
        canvas.drawRoundRect(palette, dp(20), dp(20), paint);

        paint.setTextSize(dp(12));
        paint.setColor(0xFF8ED7F8);
        canvas.drawText(wideLayout ? "模拟区域 · 拖动定位" : "模拟渲染区域 · 拖动调整位置",
                preview.left + dp(14),
                preview.top + dp(22), paint);
        canvas.drawText(wideLayout ? "备选区 · 拖入隐藏" : "备选区域 · 拖到这里即隐藏",
                palette.left + dp(14),
                palette.top + dp(24), paint);

        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(getContext()));
        int hiddenCount = 0;
        for (LyricsLayoutConfig.Item item : config.items()) {
            if (!item.enabled) hiddenCount++;
        }
        int hiddenIndex = 0;
        for (LyricsLayoutConfig.Item item : config.items()) {
            RectF bounds = hitRects.get(item);
            if (bounds == null) {
                bounds = new RectF();
                hitRects.put(item, bounds);
            }
            if (item.enabled) {
                float x = preview.left + item.x * preview.width();
                float y = preview.top + item.y * preview.height();
                componentBounds(item.id, x, y, preview, bounds);
            } else {
                if (wideLayout) {
                    int rows = Math.max(1, (int) ((palette.height() - dp(42)) / dp(46)));
                    int columns = Math.max(1, (hiddenCount + rows - 1) / rows);
                    int column = hiddenIndex / rows;
                    int row = hiddenIndex % rows;
                    float cellWidth = (palette.width() - dp(24)
                            - dp(6) * (columns - 1)) / columns;
                    float left = palette.left + dp(12) + column * (cellWidth + dp(6));
                    float top = palette.top + dp(38) + row * dp(46);
                    bounds.set(left, top, left + cellWidth, top + dp(36));
                } else {
                    int column = hiddenIndex % 3;
                    int row = hiddenIndex / 3;
                    float cellWidth = (palette.width() - dp(36)) / 3f;
                    float left = palette.left + dp(12) + column * (cellWidth + dp(6));
                    float top = palette.top + dp(38) + row * dp(52);
                    bounds.set(left, top, left + cellWidth, top + dp(40));
                }
                hiddenIndex++;
            }
            drawComponent(canvas, item, bounds, snapshot);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = findAt(event.getX(), event.getY());
                if (dragging == null) return false;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging == null) return false;
                updateDragged(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging == null) return false;
                updateDragged(event.getX(), event.getY());
                config.save(getContext());
                dragging = null;
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                if (listener != null) listener.onLayoutChanged();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void updateDragged(float x, float y) {
        if (preview.contains(x, y)) {
            dragging.enabled = true;
            dragging.x = clamp((x - preview.left) / preview.width());
            dragging.y = clamp((y - preview.top) / preview.height());
        } else if (palette.contains(x, y)) {
            dragging.enabled = false;
        }
        invalidate();
    }

    private LyricsLayoutConfig.Item findAt(float x, float y) {
        for (Map.Entry<LyricsLayoutConfig.Item, RectF> entry : hitRects.entrySet()) {
            if (entry.getValue().contains(x, y)) return entry.getKey();
        }
        return null;
    }

    private void componentBounds(String id, float x, float y, RectF container, RectF result) {
        float width = LyricsLayoutConfig.COVER.equals(id) ? dp(92)
                : LyricsLayoutConfig.PROGRESS.equals(id) ? dp(180) : dp(150);
        float height = LyricsLayoutConfig.COVER.equals(id) ? dp(92)
                : LyricsLayoutConfig.CURRENT.equals(id) ? dp(48) : dp(34);
        float left = Math.max(container.left + dp(4),
                Math.min(x, container.right - width - dp(4)));
        float top = Math.max(container.top + dp(30),
                Math.min(y, container.bottom - height - dp(4)));
        result.set(left, top, left + width, top + height);
    }

    private void drawComponent(Canvas canvas, LyricsLayoutConfig.Item item, RectF bounds,
                               MusicSnapshot snapshot) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(item.enabled ? 0x1A8ED7F8 : 0x18FFFFFF);
        canvas.drawRoundRect(bounds, dp(8), dp(8), paint);
        if (item.enabled && LyricsLayoutConfig.COVER.equals(item.id)
                && snapshot.albumArt != null && !snapshot.albumArt.isRecycled()) {
            bitmapSource.set(0, 0, snapshot.albumArt.getWidth(), snapshot.albumArt.getHeight());
            int save = canvas.save();
            canvas.clipRect(bounds);
            paint.setAlpha(210);
            canvas.drawBitmap(snapshot.albumArt, bitmapSource, bounds, paint);
            paint.setAlpha(255);
            canvas.restoreToCount(save);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(4)}, 0f));
        paint.setColor(item.enabled ? 0xFF8ED7F8 : 0xFF7B858C);
        canvas.drawRoundRect(bounds, dp(8), dp(8), paint);
        paint.setPathEffect(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(dp(LyricsLayoutConfig.CURRENT.equals(item.id) ? 15 : 11));
        paint.setColor(item.enabled ? Color.WHITE : 0xFFAFB6BB);
        String value = previewValue(item, snapshot);
        canvas.drawText(trimToWidth(value, bounds.width() - dp(16)), bounds.left + dp(8),
                bounds.centerY() + dp(4), paint);
    }

    private String previewValue(LyricsLayoutConfig.Item item, MusicSnapshot snapshot) {
        if (!item.enabled) return item.label;
        switch (item.id) {
            case LyricsLayoutConfig.COVER: return "♪  封面";
            case LyricsLayoutConfig.SOURCE: return snapshot.sourceName + " · 歌词源";
            case LyricsLayoutConfig.TITLE: return snapshot.title.isEmpty() ? "歌曲名" : snapshot.title;
            case LyricsLayoutConfig.ARTIST: return snapshot.artist.isEmpty() ? "歌手" : snapshot.artist;
            case LyricsLayoutConfig.PREVIOUS:
                return snapshot.lyrics.previousLyric.isEmpty() ? "上一句歌词" : snapshot.lyrics.previousLyric;
            case LyricsLayoutConfig.CURRENT:
                return snapshot.lyrics.lyric.isEmpty() ? "当前逐字歌词" : snapshot.lyrics.lyric;
            case LyricsLayoutConfig.TRANSLATION:
                return snapshot.lyrics.translatedLyric.isEmpty() ? "翻译歌词" : snapshot.lyrics.translatedLyric;
            case LyricsLayoutConfig.NEXT:
                return snapshot.lyrics.nextLyric.isEmpty() ? "下一句歌词" : snapshot.lyrics.nextLyric;
            case LyricsLayoutConfig.PROGRESS: return "━━━━━━  播放进度";
            default: return item.label;
        }
    }

    private String trimToWidth(String value, float width) {
        if (paint.measureText(value) <= width) return value;
        String suffix = "…";
        int end = value.length();
        while (end > 0 && paint.measureText(value.substring(0, end) + suffix) > width) end--;
        return value.substring(0, end) + suffix;
    }

    private boolean isWideLayout() {
        float widthDp = getWidth() / density;
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && widthDp >= 600f;
    }

    private float dp(float value) { return value * density; }
    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
