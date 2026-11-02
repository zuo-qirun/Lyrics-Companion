package com.zuoqirun.lyricscompanion;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import java.lang.reflect.Method;
import java.util.List;

/** Android-only checks of actual Canvas output and independent preference stores. */
public final class BatchRegressionFixtureActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Context isolated = new ContextWrapper(this) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(name + "_batch20261102", mode);
            }
            @Override public java.io.File getCacheDir() {
                java.io.File directory = new java.io.File(super.getCacheDir(), "batch-fixture");
                directory.mkdirs(); return directory;
            }
        };
        try {
            AppPreferences.get(isolated).edit().clear().apply();
            OnboardingState.initialize(isolated);
            require(OnboardingState.pending(isolated), "Fresh installation skipped onboarding");
            AppPreferences.get(isolated).edit().putBoolean(OnboardingState.FINISHED, true).apply();
            OnboardingState.initialize(isolated);
            require(!OnboardingState.pending(isolated), "Completed onboarding returned");
            checkMemories(isolated);
            checkTextAndRotation(isolated);
            checkGradient();
            if (android.os.Build.VERSION.SDK_INT >= 21) NotificationFixture.check(isolated);
            setContentView(new Gallery(isolated));
            Log.i("BatchFixture", "PASS: memory isolation, preset scope, real font sizing, rotation hit targets, gradient alignment/phase");
            if (getIntent().getBooleanExtra("portrait", false)) new Thread(() -> {
                try {
                    Method method = ArtistPortraitRepository.class.getDeclaredMethod("loadMetadata", Context.class, String.class);
                    method.setAccessible(true);
                    @SuppressWarnings("unchecked") List<ArtistPortraitRepository.Photo> photos =
                            (List<ArtistPortraitRepository.Photo>) method.invoke(null, isolated, "Jay Chou");
                    if (photos.isEmpty()) throw new IllegalStateException("No portraits returned");
                    Bitmap bitmap = ArtistPortraitRepository.loadImage(isolated, photos.get(0));
                    require(bitmap != null && bitmap.getWidth() > 0, "Portrait decode failed");
                    Log.i("BatchFixture", "PASS: live public portrait metadata photos=" + photos.size()
                            + " bitmap=" + bitmap.getWidth() + "x" + bitmap.getHeight()
                            + " attribution=" + !photos.get(0).credit.isEmpty());
                    checkPortraitComposition(isolated);
                } catch (Exception error) { Log.e("BatchFixture", "PORTRAIT FAILED " + error + " cause=" + error.getCause(), error); }
            }, "portrait-smoke").start();
        } catch (Exception error) { throw new IllegalStateException("Batch regression failed", error); }
    }

    private void checkMemories(Context context) {
        AppPreferences.setOverlayStyle(context, false, "pure");
        AppPreferences.setOverlayStyle(context, true, "pure");
        AppPreferences.putOverlayPosition(context, false, AppPreferences.overlayPositionKey(false, "pure", true), 70);
        AppPreferences.putOverlayPosition(context, false, AppPreferences.overlayPositionKey(false, "pure", false), 120);
        AppPreferences.savePositionMemory(context, false, 1, 0, 0);
        AppPreferences.savePositionMemory(context, true, 1, 50, 60);
        require(!AppPreferences.restorePositionMemory(context, false, 2), "Empty memory must not move the window");
        AppPreferences.putOverlayPosition(context, false, AppPreferences.overlayPositionKey(false, "pure", true), 180);
        AppPreferences.restorePositionMemory(context, false, 1);
        require(AppPreferences.overlayPosition(context, false, "pure", true, 0) == 70, "Main memory failed");
        AppPreferences.restorePositionMemory(context, true, 1);
        require(AppPreferences.overlayPosition(context, true, "pure", true, 0) == 50, "Secondary memory failed");
        Context extra = new DisplaySlotContext(context, 2);
        Context other = new DisplaySlotContext(context, 3);
        AppPreferences.setOverlayStyle(extra, true, "pure");
        AppPreferences.setOverlayStyle(other, true, "pure");
        AppPreferences.savePositionMemory(extra, true, 1, 300, 200);
        require(!AppPreferences.restorePositionMemory(other, true, 1), "Extra memories leaked between slots");
        AppearancePresets.apply(context, false, 1, 700, 400);
        require(AppPreferences.backgroundDarkColor(context, false) == 0, "Immersive preset hid artwork behind a solid colour");
        require("pure".equals(AppPreferences.overlayStyle(context, true)), "Preset changed secondary style");
        require(AppPreferences.overlayPosition(context, true, "pure", true, 0) == 50, "Preset moved secondary");
    }

    private void checkPortraitComposition(Context context) {
        AppPreferences.setOverlayStyle(context, false, "refined");
        AppPreferences.putDisplayString(context, false, ArtistPortraitRepository.SOURCE, "portrait");
        Bitmap album = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888);
        album.eraseColor(Color.RED);
        MusicSnapshot snapshot = new MusicSnapshot(true, true, "Fixture", "Song", "Jay Chou", album,
                240000L, 90000L, false, false, "", LrcTimeline.At.EMPTY);
        ArtistPortraitArtwork artwork = new ArtistPortraitArtwork(context, false);
        MusicSnapshot first = snapshot;
        long now = android.os.SystemClock.elapsedRealtime();
        for (int i = 0; i < 60 && first.albumArt == album; i++) {
            first = artwork.resolve(context, snapshot, now);
            android.os.SystemClock.sleep(50L);
        }
        require(first.albumArt != album, "Portrait pipeline did not replace album art");
        MusicSnapshot finalFrame = artwork.resolve(context, snapshot, now + 500L);
        require(finalFrame.albumArt.getWidth() == finalFrame.albumArt.getHeight(), "Contain mode must preserve photo in a square surface");
        require(!first.albumArt.sameAs(finalFrame.albumArt), "Portrait crossfade did not advance");
        AppPreferences.putDisplayString(context, false, ArtistPortraitRepository.SOURCE, "album");
        require(artwork.resolve(context, snapshot, now + 1000L).albumArt == album, "Disabling portraits lost album art");
        artwork.clear(); album.recycle();
        Log.i("BatchFixture", "PASS: portrait composition, crossfade, contain framing, disabling restores album art");
    }

    private void checkTextAndRotation(Context context) throws Exception {
        AppPreferences.setOverlayStyle(context, false, "pure");
        AppPreferences.putDisplayString(context, false, AppPreferences.KEY_LONG_LINE_MODE, "ellipsize");
        LyricsPanelView pure = new LyricsPanelView(context, false);
        Method fit = LyricsPanelView.class.getDeclaredMethod("fitSize", String.class, float.class, float.class, int.class);
        fit.setAccessible(true);
        float size = (Float) fit.invoke(pure, "这是一句需要保持字号并截断的超长歌词", 42f, 100f, 0);
        require(size == 42f, "Ellipsis shrank the adjacent row");
        AppPreferences.setOverlayStyle(context, true, "compact");
        AppPreferences.putDisplayBoolean(context, true, AppPreferences.KEY_SECONDARY_PLAYBACK_CONTROLS, true);
        AppPreferences.putDisplayBoolean(context, true, AppPreferences.KEY_SHOW_PREVIOUS_BUTTON, true);
        AppPreferences.putDisplayBoolean(context, true, AppPreferences.KEY_SHOW_PLAY_PAUSE_BUTTON, true);
        AppPreferences.putDisplayBoolean(context, true, AppPreferences.KEY_SHOW_NEXT_BUTTON, true);
        LyricsPanelView panel = new LyricsPanelView(context, true);
        panel.layout(0, 0, 800, 400);
        Rect normal = panel.playbackControlsBounds();
        require(normal != null, "No playback target in fixture");
        MediaControlAction before = panel.playbackControlAt(normal.centerX(), normal.centerY());
        AppPreferences.putDisplayString(context, true, AppPreferences.KEY_OVERLAY_ROTATION, "180");
        panel.reloadStyle();
        Rect rotated = panel.playbackControlsBounds();
        require(rotated != null && rotated.left == 800 - normal.right && rotated.top == 400 - normal.bottom,
                "Playback bounds did not rotate");
        require(before == panel.playbackControlAt(800 - normal.centerX(), 400 - normal.centerY()), "Rotated hit target changed action");
    }

    private void checkGradient() {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setTextSize(50); paint.setColor(Color.WHITE);
        String text = "短句渐变";
        float width = paint.measureText(text);
        for (long clock : new long[]{0L, 800L, 1600L}) {
            Bitmap left = Bitmap.createBitmap(800, 110, Bitmap.Config.ARGB_8888);
            Bitmap centred = Bitmap.createBitmap(800, 110, Bitmap.Config.ARGB_8888);
            LyricTextEffectRenderer renderer = new LyricTextEffectRenderer(); renderer.configure("gradient", 100, 100);
            paint.setTextAlign(Paint.Align.LEFT);
            renderer.draw(new Canvas(left), text, 30, 80, paint, clock, 0);
            paint.setTextAlign(Paint.Align.CENTER);
            renderer.draw(new Canvas(centred), text, 430 + width * .5f, 80, paint, clock, 0);
            int compared = 0;
            java.util.Set<Integer> opaqueColors = new java.util.HashSet<>();
            for (int x = 30; x < 30 + (int) width; x++) for (int y = 20; y < 90; y++) {
                int a = left.getPixel(x, y), b = centred.getPixel(x + 400, y);
                if (Color.alpha(a) > 250) { opaqueColors.add(a); compared++; }
                require(a == b, "Gradient depends on panel position or alignment");
            }
            require(compared > 100 && opaqueColors.size() > 20, "Short line became solid at phase " + clock);
            left.recycle(); centred.recycle();
        }
    }

    private static void require(boolean value, String message) { if (!value) throw new IllegalStateException(message); }
    @android.annotation.TargetApi(21)
    private static final class NotificationFixture {
        static void check(Context context) {
            android.media.session.MediaSession session = new android.media.session.MediaSession(context, "batch-progress");
            try {
                session.setMetadata(new android.media.MediaMetadata.Builder().putString(
                        android.media.MediaMetadata.METADATA_KEY_TITLE, "Fixture song").putLong(
                        android.media.MediaMetadata.METADATA_KEY_DURATION, 240000L).build());
                android.app.Notification.Builder builder = new android.app.Notification.Builder(context)
                        .setContentTitle("Fixture song").setSmallIcon(android.R.drawable.ic_media_play)
                        .setStyle(new android.app.Notification.MediaStyle().setMediaSession(session.getSessionToken()));
                NotificationTrackParser.Track track = new NotificationTrackParser.Track("Fixture song", "Artist", "");
                MusicPlaybackData data = NotificationPlaybackReader.read(context, context.getPackageName(),
                        builder.setProgress(240, 90, false).build(), track);
                require(data != null && data.positionMs == 90000L, "Notification duration-scaled progress failed");
                require(NotificationPlaybackReader.read(context, context.getPackageName(),
                        builder.setProgress(100, 50, false).build(), track) == null, "Arbitrary percentage became a playback clock");
                session.setPlaybackState(new android.media.session.PlaybackState.Builder().setState(
                        android.media.session.PlaybackState.STATE_PLAYING, 40000L, 1f).build());
                data = NotificationPlaybackReader.read(context, context.getPackageName(), builder.build(), track);
                require(data != null && data.positionMs >= 40000L && data.positionMs < 41000L,
                        "Media token clock was ignored");
                Log.i("BatchFixture", "PASS: standard notification media token and bounded progress fallback");
            } finally { session.release(); }
        }
    }
    private static final class Gallery extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final LyricTextEffectRenderer text = new LyricTextEffectRenderer();
        private final AudioVisualRenderer audio = new AudioVisualRenderer();
        private final RectF area = new RectF();
        private final float[] levels = {.1f, .8f, .4f, .9f};
        Gallery(Context context) { super(context); text.configure("gradient", 100, 100); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xFF07111F); paint.setColor(Color.WHITE); paint.setTextSize(48);
            canvas.drawText("PASS · 20261102", 50, 100, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            text.draw(canvas, "短句 · 居中渐变", getWidth() * .5f, 240, paint, 800, 0);
            text.draw(canvas, "这一行的渐变覆盖实际文字宽度", getWidth() * .5f, 350, paint, 1600, 0);
            area.set(20, 440, getWidth() - 20, 900);
            audio.draw(canvas, area, "ribbons", levels, true,
                    true, 1500L, 1f, 60, 5f, "multi", Color.WHITE, Color.WHITE, Color.WHITE);
            area.set(20, 950, getWidth() - 20, 1450);
            audio.draw(canvas, area, "particles", levels, true,
                    true, 1500L, 1f, 70, 5f, "multi", Color.WHITE, Color.WHITE, Color.WHITE);
        }
    }
}
