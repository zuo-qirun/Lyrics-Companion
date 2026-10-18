// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native Canvas rendition of lumiere/light/sprites.ts at 9279251.
package com.zuoqirun.lyricscompanion;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;

/** Three tiny immutable textures are generated once; every animated draw is a GPU bitmap quad. */
final class FoliaLumiereSprites {
    private FoliaLumiereSprites() { }
    static final Bitmap DOT = dot();
    static final Bitmap STAR = star();
    static final Bitmap BOKEH = bokeh();
    private static int white(double alpha) { return ((int) Math.round(alpha * 255) << 24) | 0xffffff; }
    private static Bitmap bitmap(int size) { return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888); }
    private static Bitmap dot() {
        Bitmap bitmap = bitmap(128); Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(64, 64, 64, new int[]{white(1), white(.72), white(.2), white(0)},
                new float[]{0, .18f, .45f, 1}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 128, 128, paint); return bitmap;
    }
    private static Bitmap bokeh() {
        Bitmap bitmap = bitmap(128); Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(64, 64, 64 * .96f, new int[]{white(.35), white(.42), white(.75), white(0)},
                new float[]{0, .78f, .9f, 1}, Shader.TileMode.CLAMP));
        canvas.drawCircle(64, 64, 64 * .96f, paint); return bitmap;
    }
    private static Bitmap star() {
        Bitmap bitmap = bitmap(256); Canvas canvas = new Canvas(bitmap); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(128, 128, 64, new int[]{white(1), white(.8), white(.14), white(0)},
                new float[]{0, .12f, .4f, 1}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, 256, 256, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        Path ray = new Path();
        for (int diagonal = 0; diagonal < 2; diagonal++) for (int i = 0; i < 4; i++) {
            float length = 128 * (diagonal == 0 ? .98f : .42f), thickness = 256 * (diagonal == 0 ? .018f : .01f);
            int saved = canvas.save(); canvas.translate(128, 128); canvas.rotate(i * 90 + diagonal * 45);
            paint.setShader(new LinearGradient(0, 0, length, 0, white(diagonal == 0 ? .95 : .45), white(0), Shader.TileMode.CLAMP));
            ray.rewind(); ray.moveTo(0, -thickness); ray.lineTo(length, 0); ray.lineTo(0, thickness); ray.close();
            canvas.drawPath(ray, paint); canvas.restoreToCount(saved);
        }
        return bitmap;
    }
}
