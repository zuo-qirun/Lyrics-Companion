package com.zuoqirun.lyricscompanion;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.BadParcelableException;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.TextView;

/** DUMP-protected runtime regressions for oversized composition and malformed vendor parcels. */
public final class CrashRegressionFixtureActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Context isolated = new ContextWrapper(this) {
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(name + "_crash_regression", mode);
            }
        };
        new Thread(() -> {
            try {
                Bitmap output = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888);
                FoliaTemperaGpuInversion inversion = new FoliaTemperaGpuInversion();
                for (int i = 0; i < 3; i++) {
                    inversion.draw(new Canvas(output), "shot-" + i, 1920, 720,
                            Color.BLACK, Color.WHITE, canvas -> canvas.drawColor(Color.BLUE),
                            canvas -> canvas.drawColor(Color.WHITE));
                }
                if (output.isRecycled() || Color.alpha(output.getPixel(100, 100)) != 255) {
                    throw new AssertionError("oversized CPU inversion lost its frame");
                }
                inversion.clear();
                RenderBufferCache<Bitmap> masks = new RenderBufferCache<>(512 * 1024, false);
                masks.put("large-mask", output, output.getByteCount());
                if (output.isRecycled() || masks.sizeBytes() != 0) throw new AssertionError("mask ownership");
                // Simulates a vendor-only Parcelable whose class/creator cannot be decoded here.
                Parcel parcel = Parcel.obtain();
                Intent copy;
                try {
                    new Intent(BluetoothAvrcpReceiver.ACTIONS[0]).putExtra("vendor", new UnreadablePayload())
                            .writeToParcel(parcel, 0);
                    parcel.setDataPosition(0);
                    copy = Intent.CREATOR.createFromParcel(parcel);
                } finally { parcel.recycle(); }
                new BluetoothAvrcpReceiver().onReceive(isolated, copy);
                checkBackgroundShaders();
                android.util.Log.i("CrashRegression", "PASS oversized CPU frames, live mask ownership, malformed AVRCP parcel");
                runOnUiThread(() -> {
                    TextView result = new TextView(this);
                    result.setText("PASS: crash regressions");
                    setContentView(result);
                });
            } catch (Throwable error) {
                android.util.Log.e("CrashRegression", "FAIL", error);
                runOnUiThread(() -> { throw new IllegalStateException("Crash regression failed", error); });
            }
        }, "crash-regression").start();
    }

    private static void checkBackgroundShaders() throws Exception {
        FoliaOriginalBackgroundRenderer renderer = new FoliaOriginalBackgroundRenderer();
        Bitmap target = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(target);
        renderer.draw(canvas, 320, 240, 1000, "latent", null, Color.WHITE, Color.BLUE, Color.GRAY, 1);
        java.lang.reflect.Field field = FoliaOriginalBackgroundRenderer.class.getDeclaredField("latentShaders");
        field.setAccessible(true);
        Object[] before = ((Object[]) field.get(renderer)).clone();
        renderer.draw(canvas, 320, 240, 2000, "latent", null, Color.WHITE, Color.BLUE, Color.GRAY, 1);
        Object[] after = (Object[]) field.get(renderer);
        for (int i = 0; i < before.length; i++) if (before[i] != after[i]) throw new AssertionError("per-frame shader allocation");
        renderer.clear();
        for (Object shader : (Object[]) field.get(renderer)) if (shader != null) throw new AssertionError("retained shader on clear");
        android.util.Log.i("CrashRegression", "PASS latent shader reuse and release");
    }

    public static final class UnreadablePayload implements Parcelable {
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel destination, int flags) { destination.writeInt(1); }
        public static final Creator<UnreadablePayload> CREATOR = new Creator<UnreadablePayload>() {
            @Override public UnreadablePayload createFromParcel(Parcel source) {
                throw new BadParcelableException("unavailable vendor PlaybackStateCompat");
            }
            @Override public UnreadablePayload[] newArray(int size) { return new UnreadablePayload[size]; }
        };
    }
}
