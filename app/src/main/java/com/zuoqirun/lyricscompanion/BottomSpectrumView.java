package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

/** A touch-through spectrum surface pinned to the physical bottom edge. */
final class BottomSpectrumView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SpectrumMath.BarTracker tracker =
            new SpectrumMath.BarTracker(SpectrumMath.BAND_COUNT);
    private final float[] virtual = new float[SpectrumMath.BAND_COUNT];
    private final float[] displayed = new float[SpectrumMath.BAND_COUNT];
    private final int[] palette = {0xFF6EE7F2, 0xFFFFCA66, 0xFFFF7E9D, 0xFF9B8CFF};

    BottomSpectrumView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.elapsedRealtime();
        MusicSnapshot snapshot = MusicStateStore.snapshot(AppPreferences.lyricOffsetMs(getContext()));
        AudioSpectrumSource.Frame frame = AudioSpectrumSource.latestFrame();
        boolean useReal = AppPreferences.compactUseRealSpectrum(getContext(), false);
        float[] target = useReal && frame.live ? frame.levels : virtualSpectrum(snapshot, now);
        tracker.update(target, now);
        for (int index = 0; index < displayed.length; index++) displayed[index] = tracker.barAt(index);

        float density = getResources().getDisplayMetrics().density;
        paint.setColor(0x3A07111F);
        canvas.drawRect(0f, 0f, getWidth(), getHeight(), paint);
        float inset = Math.max(5f * density, getWidth() * 0.012f);
        RectF area = new RectF(inset, Math.max(2f * density, getHeight() * 0.08f),
                getWidth() - inset, getHeight() - Math.max(2f * density, getHeight() * 0.06f));
        int lyricColor = AppPreferences.lyricColor(getContext(), false);
        if (lyricColor == 0) lyricColor = 0xFF6EE7F2;
        SpectrumRenderer.draw(canvas, paint, area, displayed,
                AppPreferences.spectrumStyle(getContext(), false),
                AppPreferences.spectrumColorMode(getContext(), false), lyricColor,
                AppPreferences.compactSpectrumColor(getContext(), false), palette);
        postInvalidateDelayed(snapshot.playing ? 50L : 500L);
    }

    private float[] virtualSpectrum(MusicSnapshot snapshot, long now) {
        long step = now / 220L;
        float fraction = (now % 220L) / 220f;
        fraction = fraction * fraction * (3f - 2f * fraction);
        for (int index = 0; index < virtual.length; index++) {
            float from = snapshot.playing ? pulse(index, step) : 0.08f;
            float to = snapshot.playing ? pulse(index, step + 1L) : 0.08f;
            float shape = 0.32f + 0.68f * (float) Math.sin((index + 0.5f)
                    / virtual.length * Math.PI);
            virtual[index] = (from + (to - from) * fraction) * shape;
        }
        return virtual;
    }

    private static float pulse(int band, long step) {
        long value = (step + 3L) * 0x9E3779B97F4A7C15L + (band + 17L) * 0xBF58476D1CE4E5B9L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        return 0.16f + (value & 0xFFFFL) / 65535f * 0.84f;
    }
}
