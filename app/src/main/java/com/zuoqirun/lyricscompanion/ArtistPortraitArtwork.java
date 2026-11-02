package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import java.util.List;

/** One lazy photo at a time per panel, with a reusable crossfade surface and album fallback. */
final class ArtistPortraitArtwork {
    private final Context context;
    private final boolean secondary;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF destination = new RectF();
    private String artist = "", request = "";
    private volatile int generation;
    private volatile Loaded loaded;
    private Bitmap front, previous, composite;
    private Canvas compositor;
    private Loaded accepted;
    private long transitionAt, lastFrame, carouselElapsed;
    private boolean transitioning;
    private static final class Loaded {
        final Bitmap bitmap;
        Loaded(Bitmap bitmap) { this.bitmap = bitmap; }
    }

    ArtistPortraitArtwork(Context context, boolean secondary) {
        this.context = context.getApplicationContext(); this.secondary = secondary;
    }

    MusicSnapshot resolve(Context preferences, MusicSnapshot snapshot, long now) {
        if (!"portrait".equals(AppPreferences.displayString(preferences, secondary, ArtistPortraitRepository.SOURCE, "album"))
                || !snapshot.active || snapshot.artist.trim().isEmpty()) {
            if (!artist.isEmpty()) clear();
            return snapshot;
        }
        if (!artist.equals(snapshot.artist)) {
            clear(); artist = snapshot.artist; lastFrame = now;
        }
        if (snapshot.playing) carouselElapsed += Math.max(0L, Math.min(1000L, now - lastFrame));
        lastFrame = now;
        List<ArtistPortraitRepository.Photo> photos = ArtistPortraitRepository.find(preferences, artist);
        if (photos.isEmpty()) return snapshot;
        boolean low = AppPreferences.lowResourceMode(preferences);
        long interval = Math.max(5, Math.min(60, AppPreferences.displayInt(preferences, secondary, ArtistPortraitRepository.INTERVAL, 10))) * 1000L;
        int index = low ? 0 : (int) (carouselElapsed / interval % photos.size());
        ArtistPortraitRepository.Photo photo = photos.get(index);
        if (!request.equals(photo.url)) {
            request = photo.url;
            int expected = ++generation;
            try {
                ArtistPortraitRepository.EXECUTOR.execute(() -> {
                    Bitmap bitmap = null;
                    try { bitmap = ArtistPortraitRepository.loadImage(context, photo); } catch (Exception ignored) { }
                    if (generation == expected && bitmap != null && !bitmap.isRecycled()) loaded = new Loaded(bitmap);
                });
            } catch (java.util.concurrent.RejectedExecutionException ignored) { request = ""; }
        }
        Loaded ready = loaded;
        if (ready != null && ready != accepted) {
            previous = front == null ? snapshot.albumArt : front;
            front = ready.bitmap; accepted = ready; transitionAt = now;
            transitioning = true;
        }
        if (front == null || front.isRecycled()) return snapshot;
        boolean contain = !"fill".equals(AppPreferences.displayString(preferences, secondary, ArtistPortraitRepository.FIT, "contain"));
        float progress = low || previous == null ? 1f : Math.min(1f, (now - transitionAt) / 450f);
        if (progress >= 1f && !contain) { previous = null; return snapshot.withArtwork(front); }
        if (progress >= 1f && transitioning) {
            // A new final surface lets artwork-colour caches observe the completed photo.
            composite = null; compositor = null; transitioning = false;
        }
        int side = Math.min(ResourceProfile.artSide(low), Math.max(front.getWidth(), front.getHeight()));
        if (composite == null || composite.getWidth() != side) {
            // Do not recycle the old surface: artwork palettes may still hold a weak reference.
            composite = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
            compositor = new Canvas(composite);
        }
        compositor.drawColor(Color.BLACK, PorterDuff.Mode.SRC);
        if (progress < 1f && previous != null && !previous.isRecycled()) draw(previous, 255, contain, side);
        draw(front, Math.round(progress * 255), contain, side);
        if (progress >= 1f) previous = null;
        return snapshot.withArtwork(composite);
    }

    private void draw(Bitmap bitmap, int alpha, boolean contain, int side) {
        float ratio = contain ? Math.min(side / (float) bitmap.getWidth(), side / (float) bitmap.getHeight())
                : Math.max(side / (float) bitmap.getWidth(), side / (float) bitmap.getHeight());
        float width = bitmap.getWidth() * ratio, height = bitmap.getHeight() * ratio;
        destination.set((side - width) * .5f, (side - height) * .5f, (side + width) * .5f, (side + height) * .5f);
        paint.setAlpha(alpha);
        compositor.drawBitmap(bitmap, null, destination, paint);
    }

    void clear() {
        generation++;
        artist = ""; request = ""; loaded = null; accepted = null;
        front = null; previous = null; composite = null; compositor = null;
        carouselElapsed = 0;
    }
}
