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
        new Thread(this::renderFixture, "folia-render-fixture").start();
    }

    @androidx.annotation.WorkerThread
    private void renderFixture() {
        int width = Math.max(200, Math.min(2400, getIntent().getIntExtra("width", 800)));
        int height = Math.max(150, Math.min(1600, getIntent().getIntExtra("height", 600)));
        long at = Math.max(0, getIntent().getLongExtra("time", 6700));
        int fps = getIntent().getIntExtra("fps", 30);
        boolean translations = getIntent().getBooleanExtra("translations", true);
        int tracking = Math.max(0, Math.min(30, getIntent().getIntExtra("tracking", 0)));
        boolean nextLine = getIntent().getBooleanExtra("nextLine", true);
        int nextScale = getIntent().getIntExtra("nextScale", 100);
        String selected = getIntent().getStringExtra("mode");
        List<LrcTimeline.Line> lines = new ArrayList<>();
        for (int i = 0; i < TEXT.length; i++) {
            String[] glyphs = FoliaGlyphTimeline.split(TEXT[i]);
            List<LrcTimeline.Word> words = new ArrayList<>();
            for (int g = 0; g < glyphs.length; g++) {
                long start = i * 3000L + g * 3000L / glyphs.length;
                long end = i * 3000L + (g + 1) * 3000L / glyphs.length;
                words.add(new LrcTimeline.Word(start, end - start, glyphs[g]));
            }
            lines.add(new LrcTimeline.Line(i * 3000L, 3000, TEXT[i], "The light stays with you", words));
        }
        LrcTimeline timeline = LrcTimeline.fromTimedLines(lines);
        Bitmap cover = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        for (int y = 0; y < 300; y++) for (int x = 0; x < 300; x++) cover.setPixel(x, y,
                Color.rgb(40 + x * 160 / 300, 80 + y * 100 / 300, 180 - x * 80 / 300));
        MusicSnapshot snapshot = new MusicSnapshot(true, false, "Fixture", "微光", "Folia",
                cover, 18000, at, true, true, "Fixture", timeline.at(at), timeline.renderLines());
        Bitmap shown = null;
        for (String mode : selected == null ? FoliaModeCatalog.VALUES : new String[]{FoliaModeCatalog.normalize(selected)}) {
            Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(image);
            canvas.drawColor(0xFF09090B);
            FoliaCanvasRenderer renderer = new FoliaCanvasRenderer();
            renderer.configure(mode, null, 1, 50, fps, 1, 1.65f, 45, true, tracking, translations, false);
            renderer.configureNextLine(nextLine, nextScale / 100f);
            renderer.draw(canvas, snapshot, at, at, 1, 0xFFF5F8FF, 0xFFFF984B, 0xFFB9BBC5);
            write(image, mode + '-' + at + (fps == 0 ? "-static" : "") + (translations ? "" : "-no-translation")
                    + "-tracking" + tracking + "-next" + (nextLine ? nextScale : "hidden") + ".png");
            if (getIntent().getBooleanExtra("verifyPause", false)) {
                Bitmap later = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas laterCanvas = new Canvas(later); laterCanvas.drawColor(0xFF09090B);
                renderer.draw(laterCanvas, snapshot, at, at + 5000, 1, 0xFFF5F8FF, 0xFFFF984B, 0xFFB9BBC5);
                if (!image.sameAs(later)) throw new IllegalStateException("Paused frame changed: " + mode);
                android.util.Log.i("FoliaFixture", "Paused frame stable: " + mode);
                later.recycle();
            }
            renderer.clear();
            if (shown != null) shown.recycle();
            shown = image;
        }
        final Bitmap displayed = shown;
        android.util.Log.i("FoliaFixture", "Fixture complete at " + at + " ms, fps=" + fps);
        runOnUiThread(() -> setContentView(new View(this) {
            private final android.graphics.Rect bounds = new android.graphics.Rect();
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(0xFF09090B);
                bounds.set(0, 0, getWidth(), getHeight());
                if (displayed != null) canvas.drawBitmap(displayed, null, bounds, null);
            }
        }));
    }

    private void write(Bitmap image, String name) {
        File directory = new File(getCacheDir(), "folia-render-fixture");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("Cannot create fixture output");
        try (FileOutputStream stream = new FileOutputStream(new File(directory, name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, stream);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
}
