package com.zuoqirun.lyricscompanion;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Debug-only, DUMP-protected fixed-time Canvas fixture for upstream screenshot comparisons. */
public final class FoliaRenderFixtureActivity extends Activity {
    private static final String[] TEXT = {"在无人的世界里", "听见风穿过树梢", "所有微光 汇成星河",
            "让这首歌陪你远行", "Until the morning comes", "我们仍然向前走"};

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (getIntent().getBooleanExtra("hardware", false)) {
            showHardwareFixture();
            return;
        }
        new Thread(this::renderFixture, "folia-render-fixture").start();
    }

    /** Exercise the actual hardware Canvas; bitmap fixtures only verify the software fallback. */
    private void showHardwareFixture() {
        List<LrcTimeline.Line> lines = new ArrayList<>();
        for (int i = 0; i < TEXT.length; i++) {
            String[] glyphs = FoliaGlyphTimeline.split(TEXT[i]);
            List<LrcTimeline.Word> words = new ArrayList<>();
            for (int g = 0; g < glyphs.length; g++) {
                long start = i * 3000L + g * 3000L / glyphs.length;
                words.add(new LrcTimeline.Word(start, 3000L / glyphs.length, glyphs[g]));
            }
            lines.add(new LrcTimeline.Line(i * 3000L, 3000, TEXT[i], "The light stays with you", words));
        }
        LrcTimeline timeline = LrcTimeline.fromTimedLines(lines);
        Bitmap cover = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        Canvas art = new Canvas(cover);
        android.graphics.Paint coverPaint = new android.graphics.Paint();
        coverPaint.setShader(new android.graphics.LinearGradient(0, 0, 300, 300,
                0xFF2850B4, 0xFFC8B464, android.graphics.Shader.TileMode.CLAMP));
        art.drawRect(0, 0, 300, 300, coverPaint);
        boolean original = "original".equals(getIntent().getStringExtra("renderer"));
        boolean playing = getIntent().getBooleanExtra("playing", true);
        int fps = getIntent().getIntExtra("fps", original ? 60 : 30);
        long start = Math.max(0, getIntent().getLongExtra("time", 0));
        String mode = FoliaModeCatalog.normalize(getIntent().getStringExtra("mode"));
        FixtureRenderer renderer = new FixtureRenderer(getAssets(), original, mode,
                getIntent().getBooleanExtra("lowResource", false), false, fps, 0, true, true, 100,
                getIntent().getStringExtra("seed"), getIntent().getStringExtra("themeName"), getIntent());
        long started = android.os.SystemClock.elapsedRealtime();
        View view = new View(this) {
            private boolean logged;
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(0xFF09090B);
                long clock = android.os.SystemClock.elapsedRealtime() - started;
                long at = start + (playing ? clock : 0);
                renderer.draw(canvas, snapshot(timeline, cover, at, playing), at, clock);
                if (!logged) {
                    logged = true;
                    android.util.Log.i("FoliaFixture", "Hardware Canvas=" + canvas.isHardwareAccelerated()
                            + " renderer=" + (original ? "original" : "low") + " mode=" + mode);
                }
                if (isShown() && playing && fps > 0) {
                    if (fps >= 60) postInvalidateOnAnimation();
                    else postInvalidateDelayed(Math.round(1000f / fps));
                }
            }
            @Override protected void onDetachedFromWindow() { renderer.clear(); super.onDetachedFromWindow(); }
        };
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(view, new android.widget.FrameLayout.LayoutParams(
                Math.max(200, getIntent().getIntExtra("width", 800)),
                Math.max(150, getIntent().getIntExtra("height", 600))));
        setContentView(root);
    }

    @androidx.annotation.WorkerThread
    private void renderFixture() {
        int width = Math.max(200, Math.min(3840, getIntent().getIntExtra("width", 800)));
        int height = Math.max(150, Math.min(4096, getIntent().getIntExtra("height", 600)));
        long at = Math.max(0, getIntent().getLongExtra("time", 6700));
        int fps = getIntent().getIntExtra("fps", 30);
        FoliaOriginalCanvasRenderer.debugLayout = getIntent().getBooleanExtra("debugLayout", false);
        boolean translations = getIntent().getBooleanExtra("translations", true);
        int tracking = Math.max(0, Math.min(30, getIntent().getIntExtra("tracking", 0)));
        boolean nextLine = getIntent().getBooleanExtra("nextLine", true);
        boolean low = getIntent().getBooleanExtra("lowResource", false);
        boolean randomColors = getIntent().getBooleanExtra("randomColors", false);
        int nextScale = getIntent().getIntExtra("nextScale", 100);
        boolean original = "original".equals(getIntent().getStringExtra("renderer"));
        boolean history = getIntent().getBooleanExtra("history", false);
        boolean playing = getIntent().getBooleanExtra("playing", false);
        int historyStep = Math.max(8, Math.min(100, getIntent().getIntExtra("historyStep", 16)));
        int samples = Math.max(0, Math.min(360, getIntent().getIntExtra("samples", 0)));
        int sampleStep = Math.max(8, Math.min(1000, getIntent().getIntExtra("sampleStep", 16)));
        String selected = getIntent().getStringExtra("mode");
        boolean longGap = getIntent().getBooleanExtra("longGap", false);
        boolean interludeLine = getIntent().getBooleanExtra("interludeLine", false);
        List<LrcTimeline.Line> lines = new ArrayList<>();
        if (interludeLine) {
            addFixtureLines(lines,
                    new long[]{0, 3000, 6000, 6050, 12500, 15500, 18500},
                    new long[]{3000, 3000, 3000, 6400, 3000, 3000, 3000},
                    new String[]{TEXT[0], TEXT[1], TEXT[2], "......", TEXT[3], TEXT[4], TEXT[5]});
        } else if (longGap) {
            addFixtureLines(lines,
                    new long[]{0, 3000, 6000, 10500, 13500, 16500},
                    new long[]{3000, 3000, 600, 3000, 3000, 3000},
                    TEXT);
        } else {
            long[] starts = new long[TEXT.length];
            long[] durations = new long[TEXT.length];
            for (int i = 0; i < TEXT.length; i++) { starts[i] = i * 3000L; durations[i] = 3000; }
            addFixtureLines(lines, starts, durations, TEXT);
        }
        LrcTimeline timeline = LrcTimeline.fromTimedLines(lines);
        Bitmap cover = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 300; y++) for (int x = 0; x < 300; x++) cover.setPixel(x, y,
                Color.rgb(40 + x * 160 / 300, 80 + y * 100 / 300, 180 - x * 80 / 300));
        MusicSnapshot snapshot = snapshot(timeline, cover, at, playing);
        Bitmap shown = null;
        for (String mode : selected == null ? FoliaModeCatalog.VALUES : new String[]{FoliaModeCatalog.normalize(selected)}) {
            Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            canvas.drawColor(0xFF09090B);
            FixtureRenderer renderer = new FixtureRenderer(getAssets(), original, mode, low, randomColors, fps,
                    tracking, translations, nextLine, nextScale, getIntent().getStringExtra("seed"),
                    getIntent().getStringExtra("themeName"), getIntent());
            if (history) {
                for (long time = 0; time < at; time += historyStep) {
                    canvas.drawColor(0xFF09090B);
                    renderer.draw(canvas, snapshot(timeline, cover, time, true), time, time);
                }
                canvas.drawColor(0xFF09090B);
            }
            renderer.draw(canvas, snapshot, at, at);
            String name = (original ? "original-" : "") + mode + '-' + at
                    + (history ? "-history" : "") + (playing ? "-playing" : "")
                    + (fps == 0 ? "-static" : "") + (translations ? "" : "-no-translation")
                    + "-tracking" + tracking + "-next" + (nextLine ? nextScale : "hidden")
                    + (low ? "-low" : "") + (randomColors ? "-random" : "");
            write(image, name + ".png");
            if (getIntent().getBooleanExtra("verifyPause", false)) {
                Bitmap later = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas laterCanvas = new Canvas(later); laterCanvas.drawColor(0xFF09090B);
                renderer.draw(laterCanvas, snapshot(timeline, cover, at, false), at, at + 5000);
                if (!image.sameAs(later)) throw new IllegalStateException("Paused frame changed: " + mode);
                android.util.Log.i("FoliaFixture", "Paused frame stable: " + mode);
                later.recycle();
            }
            if (samples > 0) {
                Bitmap sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas sampleCanvas = new Canvas(sample);
                for (int index = 1; index <= samples; index++) {
                    long clock = at + index * (long) sampleStep;
                    long position = playing ? clock : at;
                    sampleCanvas.drawColor(0xFF09090B);
                    renderer.draw(sampleCanvas, snapshot(timeline, cover, position, playing), position, clock);
                    write(sample, name + String.format(java.util.Locale.US, "-frame%04d.png", index));
                }
                sample.recycle();
            }
            renderer.clear();
            if (shown != null) shown.recycle();
            shown = image;
        }
        final Bitmap displayed = shown;
        android.util.Log.i("FoliaFixture", "Fixture complete renderer=" + (original ? "original" : "low")
                + " at=" + at + " ms, fps=" + fps + " history=" + history + " samples=" + samples);
        runOnUiThread(() -> setContentView(new View(this) {
            private final android.graphics.Rect bounds = new android.graphics.Rect();
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(0xFF09090B);
                bounds.set(0, 0, getWidth(), getHeight());
                if (displayed != null) canvas.drawBitmap(displayed, null, bounds, null);
            }
        }));
    }

    private static MusicSnapshot snapshot(LrcTimeline timeline, Bitmap cover, long at, boolean playing) {
        return new MusicSnapshot(true, playing, "Fixture", "微光", "Folia", cover, 18000,
                at, true, true, "Fixture", timeline.at(at), timeline.renderLines());
    }

    /** Builds fixture lines; "......" mirrors the interlude rows inserted by the upstream parser. */
    private static void addFixtureLines(List<LrcTimeline.Line> lines, long[] starts, long[] durations, String[] texts) {
        for (int i = 0; i < texts.length; i++) {
            boolean interlude = "......".equals(texts[i]);
            String[] glyphs = FoliaGlyphTimeline.split(texts[i]);
            long span = interlude ? Math.min(durations[i], 500) : durations[i];
            List<LrcTimeline.Word> words = new ArrayList<>();
            for (int g = 0; g < glyphs.length; g++) {
                long start = starts[i] + g * span / glyphs.length;
                long end = starts[i] + (g + 1) * span / glyphs.length;
                words.add(new LrcTimeline.Word(start, end - start, glyphs[g]));
            }
            lines.add(new LrcTimeline.Line(starts[i], durations[i], texts[i],
                    interlude ? "" : "The light stays with you", words));
        }
    }

    /** Both styles receive identical pixels, settings, timeline and clock histories. */
    private static void applyOriginalOptions(FoliaOriginalCanvasRenderer renderer, android.content.Intent intent) {
        renderer.setOriginalOptions(
                intent.getIntExtra("originalBackground", 100),
                intent.getBooleanExtra("originalWordRotation", true),
                intent.getIntExtra("originalFloating", 100),
                intent.getIntExtra("originalWordSpacing", 100),
                intent.getIntExtra("originalSubtitleScale", 100),
                intent.getBooleanExtra("originalHeader", true));
    }

    private static final class FixtureRenderer {
        private final FoliaCanvasRenderer low;
        private final FoliaOriginalCanvasRenderer original;

        FixtureRenderer(android.content.res.AssetManager assets, boolean useOriginal, String mode, boolean lowResource, boolean random,
                        int fps, int tracking, boolean translations, boolean nextLine, int nextScale, String seed,
                        String themeName, android.content.Intent intent) {
            low = useOriginal ? null : new FoliaCanvasRenderer();
            original = useOriginal ? new FoliaOriginalCanvasRenderer() : null;
            if (original != null) {
                original.setAssets(assets);
                original.setGeometrySeed(seed);
                original.setThemeName(themeName);
                original.setLowResourceMode(lowResource);
                original.setLyricColorOverrides(random, random);
                original.configure(mode, null, 1, 50, fps, 1, 1.65f, 45, true, tracking, translations, false);
                original.configureNextLine(nextLine, nextScale / 100f);
                applyOriginalOptions(original, intent);
            } else {
                low.setLowResourceMode(lowResource);
                low.setLyricColorOverrides(random, random);
                low.configure(mode, null, 1, 50, fps, 1, 1.65f, 45, true, tracking, translations, false);
                low.configureNextLine(nextLine, nextScale / 100f);
            }
        }

        void draw(Canvas canvas, MusicSnapshot snapshot, long at, long clock) {
            if (original != null) original.draw(canvas, snapshot, at, clock, 1,
                    0xFFF5F8FF, 0xFFFF984B, 0xFFB9BBC5);
            else low.draw(canvas, snapshot, at, clock, 1, 0xFFF5F8FF, 0xFFFF984B, 0xFFB9BBC5);
        }

        void clear() {
            if (original != null) original.clear();
            else low.clear();
        }
    }

    private void write(Bitmap image, String name) {
        File directory = new File(getCacheDir(), "folia-render-fixture");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create fixture output");
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
}
