// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (c) 2026 chthollyphile and Folia contributors.
// Native Canvas shader/CPU translations of lumiere/light/{rig,lightFieldShader}.ts.
package com.zuoqirun.lyricscompanion;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.RectF;
import android.graphics.RuntimeShader;
import android.os.Build;

/** Full-resolution AGSL on Android 13+, bounded CPU raster on legacy/software Canvas. */
final class FoliaLumiereField {
    static final class Frame {
        final float[] a = new float[24], b = new float[24], c = new float[24], d = new float[24];
        final float[] e = new float[24], f = new float[24];
        final float[] fogA = new float[4], fogB = new float[4], color = new float[3];
        final float[] glare = new float[4], glareB = new float[4];
        final float[] caustic = new float[4], causticFloor = new float[4], causticB = new float[4];
        final float[] wave = new float[4], waveB = new float[4], shield = new float[4], shieldB = new float[4];
        int count;
        double time;
        int identity;

        double beam(int index, double x, double y) {
            int j = index * 4;
            double px = x - a[j], py = y - a[j + 1], along = px * a[j + 2] + py * a[j + 3];
            if (along <= 0) return 0;
            double perp = px * -a[j + 3] + py * a[j + 2];
            double half = Math.abs(b[j] + along * b[j + 1]) + 0.0001;
            double s = Math.abs(perp) / half;
            double edge = FoliaLumiereMath.step(1, 1 - Math.max(b[j + 2], 0.02), s);
            if (edge <= 0) return 0;
            double signed = s * Math.signum(perp);
            double streak = 1 - c[j + 1] + c[j + 1] * FoliaLumiereMath.noise1(signed * c[j + 2] + c[j + 3]);
            double core = 1 - d[j + 3] + d[j + 3] * (1 - s * s);
            double falloff = Math.exp(-along / Math.max(b[j + 3], 0.001));
            double gobo = FoliaLumiereMath.gobo((int) e[j], e[j + 1], e[j + 2], e[j + 3], signed, along);
            double reach = f[j + 1] > 0 ? 1 - FoliaLumiereMath.step(f[j + 1] - Math.max(0.04, half * 2.5), f[j + 1], along) : 1;
            double start = FoliaLumiereMath.step(0, Math.abs(b[j]) * 2 + 0.01, along);
            return edge * streak * core * falloff * gobo * reach * start * c[j];
        }

        double light(double x, double y) {
            double total = 0;
            for (int i = 0; i < count; i++) total += beam(i, x, y);
            return 1 - Math.exp(-total);
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF bounds = new RectF();
    private Bitmap raster;
    private int[] pixels;
    private Gpu gpu;
    private boolean shaderUnavailable;
    private double lastTime = Double.NaN;
    private int lastIdentity;
    private final Path beamPath = new Path();

    void clear() {
        // Canvas may still hold the preceding bitmap's display list; let GC release it safely.
        raster = null; pixels = null; lastTime = Double.NaN;
    }

    void draw(Canvas canvas, float width, float height, Frame frame, boolean lowResource) {
        if (Build.VERSION.SDK_INT >= 33 && canvas.isHardwareAccelerated() && !shaderUnavailable) {
            try {
                if (gpu == null) gpu = new Gpu();
                gpu.draw(canvas, width, height, frame);
                return;
            } catch (IllegalArgumentException | IllegalStateException error) {
                shaderUnavailable = true;
                android.util.Log.w("FoliaOriginal", "Lumiere AGSL unavailable; using bounded native raster", error);
            }
        }
        boolean hardware = canvas.isHardwareAccelerated();
        // Legacy devices still composite with the GPU: CPU only prepares a small fog tile at 10Hz;
        // moving beams, their gradients and glare are native hardware Canvas paths every frame.
        int h = hardware ? 48 : Math.max(32, Math.min(lowResource ? 80 : 144, Math.round(height / (lowResource ? 6 : 4))));
        int w = Math.max(32, Math.min(lowResource ? 160 : 288, Math.round(width / height * h)));
        if (raster == null || raster.getWidth() != w || raster.getHeight() != h) {
            raster = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            pixels = new int[w * h]; lastTime = Double.NaN;
        }
        double sampledTime = hardware ? Math.floor(frame.time * (lowResource ? 5 : 10)) / (lowResource ? 5 : 10) : frame.time;
        if (sampledTime != lastTime || frame.identity != lastIdentity) {
            int beamCount = frame.count;
            float glareIntensity = frame.glare[3];
            double actualTime = frame.time;
            if (hardware) { frame.count = 0; frame.glare[3] = 0; frame.time = sampledTime; }
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                pixels[y * w + x] = sample(frame, (x + 0.5) / w * width / height, (y + 0.5) / h,
                        x * width / w, y * height / h, hardware || lowResource ? 3 : 5);
            }
            raster.setPixels(pixels, 0, w, 0, 0, w, h);
            frame.count = beamCount; frame.glare[3] = glareIntensity; frame.time = actualTime;
            lastTime = sampledTime; lastIdentity = frame.identity;
        }
        bounds.set(-width * 0.02f, -height * 0.02f, width * 1.02f, height * 1.02f);
        canvas.drawBitmap(raster, null, bounds, paint);
        if (hardware) drawNativeBeams(canvas, width, height, frame);
    }

    private void drawNativeBeams(Canvas canvas, float width, float height, Frame frame) {
        for (int i = 0; i < frame.count; i++) {
            int j = i * 4;
            float ox = frame.a[j] * height, oy = frame.a[j + 1] * height;
            float dx = frame.a[j + 2], dy = frame.a[j + 3];
            float length = frame.f[j + 1] > 0 ? frame.f[j + 1] * height : Math.min(Math.max(width, height) * 2, frame.b[j + 3] * height * 2);
            float startHalf = Math.abs(frame.b[j]) * height;
            float endHalf = Math.abs(frame.b[j] * height + length * frame.b[j + 1]);
            int rgb = android.graphics.Color.rgb(Math.min(255, Math.round(frame.d[j] * 255)),
                    Math.min(255, Math.round(frame.d[j + 1] * 255)), Math.min(255, Math.round(frame.d[j + 2] * 255)));
            float alpha = (float) (1 - Math.exp(-frame.c[j])) * 0.42f;
            int core = (rgb & 0x00ffffff) | (Math.round(alpha * 255) << 24), clear = rgb & 0x00ffffff;
            for (int layer = 0; layer < 3; layer++) {
                float inner = layer == 0 ? 1 : layer == 1 ? 0.58f : 0.22f;
                beamPath.rewind(); beamPath.moveTo(ox - dy * startHalf * inner, oy + dx * startHalf * inner);
                beamPath.lineTo(ox + dx * length - dy * endHalf * inner, oy + dy * length + dx * endHalf * inner);
                beamPath.lineTo(ox + dx * length + dy * endHalf * inner, oy + dy * length - dx * endHalf * inner);
                beamPath.lineTo(ox + dy * startHalf * inner, oy - dx * startHalf * inner); beamPath.close();
                paint.setAlpha(255); paint.setShader(new LinearGradient(ox, oy, ox + dx * length, oy + dy * length,
                        new int[]{clear, core, clear}, new float[]{0, 0.06f, 1}, Shader.TileMode.CLAMP));
                canvas.drawPath(beamPath, paint);
            }
        }
        if (frame.glare[3] > 0) {
            float x = frame.glare[0] * height, y = frame.glare[1] * height, r = Math.max(1, frame.glare[2] * height * 4);
            int alpha = Math.min(255, Math.round((float) (1 - Math.exp(-frame.glare[3])) * 255));
            int rgb = android.graphics.Color.rgb(Math.round(frame.color[0] * 255), Math.round(frame.color[1] * 255), Math.round(frame.color[2] * 255));
            paint.setShader(new RadialGradient(x, y, r, new int[]{(rgb & 0x00ffffff) | (alpha << 24), rgb & 0x00ffffff}, null, Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, r, paint);
        }
        paint.setShader(null); paint.setAlpha(255);
    }

    private static int sample(Frame v, double x, double y, double pixelX, double pixelY, int octaves) {
        double qx = (x - v.fogB[0] * v.time) * v.fogA[2];
        double qy = (y - v.fogB[1] * v.time) * v.fogA[2];
        double wx = FoliaLumiereMath.fbm(qx, qy + v.time * 0.07, 3);
        double wy = FoliaLumiereMath.fbm(qx + 5.2 - v.time * 0.05, qy + 1.3, 3);
        double n = FoliaLumiereMath.fbm(qx + v.fogA[3] * (wx - 0.5) * 2,
                qy + v.fogA[3] * (wy - 0.5) * 2, octaves);
        double density = FoliaLumiereMath.step(0.28, 0.82, n);
        double tyndall = v.fogA[1] + (1 - v.fogA[1]) * density * v.fogA[0];
        double caustic = v.caustic[3] > 0.5 ? caustic(x, y, v.time * v.caustic[1] + 23, v.caustic[0]) : 0;
        double causticLift = FoliaLumiereMath.mix(1, 0.25 + 1.5 * caustic, v.caustic[3] > 0.5 ? v.caustic[2] : 0);
        double red = 0, green = 0, blue = 0;
        for (int i = 0; i < v.count; i++) {
            int j = i * 4;
            double mask = v.beam(i, x, y);
            double r = v.d[j], g = v.d[j + 1], b = v.d[j + 2];
            if (v.f[j] > 0) {
                double px = x - v.a[j], py = y - v.a[j + 1], along = px * v.a[j + 2] + py * v.a[j + 3];
                double s = (px * -v.a[j + 3] + py * v.a[j + 2]) / (Math.abs(v.b[j] + along * v.b[j + 1]) + 0.0001);
                double hue = FoliaLumiereMath.clamp(0.5 + s * 0.5) * 0.8, peak = Math.max(r, Math.max(g, b));
                r = FoliaLumiereMath.mix(r, spectrum(hue, 0) * peak, v.f[j]);
                g = FoliaLumiereMath.mix(g, spectrum(hue, 2.0 / 3) * peak, v.f[j]);
                b = FoliaLumiereMath.mix(b, spectrum(hue, 1.0 / 3) * peak, v.f[j]);
            }
            red += r * mask; green += g * mask; blue += b * mask;
        }
        red *= tyndall * causticLift; green *= tyndall * causticLift; blue *= tyndall * causticLift;
        double ambient = density * v.fogB[2];
        if (v.caustic[3] > 0.5 && v.causticB[0] > 0) {
            double ex = (x - v.causticFloor[0]) / Math.max(0.001, v.causticFloor[2]);
            double ey = (y - v.causticFloor[1]) / Math.max(0.001, v.causticFloor[3]);
            ambient += caustic * v.causticB[0] * FoliaLumiereMath.step(1, 0.6, Math.hypot(ex, ey));
        }
        if (v.wave[0] > 0.5) ambient += wave(v, x, y);
        if (v.glare[3] > 0) {
            double gx = x - v.glare[0], gy = y - v.glare[1], radius = Math.max(0.001, v.glare[2]);
            double dist = Math.hypot(gx, gy);
            double glare = Math.exp(-dist / radius) * 0.55 + Math.exp(-dist * dist / (radius * radius * 0.03));
            double streak = Math.exp(-Math.abs(gy) / (radius * 0.035)) * Math.exp(-Math.abs(gx) / (radius * 7)) * v.glareB[0];
            ambient += (glare + streak) * v.glare[3];
        }
        red += v.color[0] * ambient; green += v.color[1] * ambient; blue += v.color[2] * ambient;
        double peak = Math.max(red, Math.max(green, blue)), mapped = 1 - Math.exp(-peak);
        double k = peak > 0.00001 ? mapped / peak : 0, whiten = 0.3 * mapped * mapped * mapped;
        double noise = (FoliaLumiereMath.hash12(pixelX, pixelY) - 0.5) / 255;
        red = FoliaLumiereMath.clamp(FoliaLumiereMath.mix(red * k, mapped, whiten) + noise);
        green = FoliaLumiereMath.clamp(FoliaLumiereMath.mix(green * k, mapped, whiten) + noise);
        blue = FoliaLumiereMath.clamp(FoliaLumiereMath.mix(blue * k, mapped, whiten) + noise);
        double alpha = Math.max(red, Math.max(green, blue));
        if (alpha < 0.0001) return 0;
        // Bitmap.setPixels accepts straight ARGB; Android premultiplies it for Canvas.
        return ((int) Math.round(alpha * 255) << 24) | ((int) Math.round(red / alpha * 255) << 16)
                | ((int) Math.round(green / alpha * 255) << 8) | (int) Math.round(blue / alpha * 255);
    }

    private static double spectrum(double hue, double offset) {
        double k = FoliaLumiereMath.clamp(Math.abs(FoliaLumiereMath.fract(hue + offset) * 6 - 3) - 1);
        return FoliaLumiereMath.mix(1, k, 0.85);
    }
    private static double caustic(double x, double y, double time, double scale) {
        double qx = x * scale * Math.PI * 2 - 250, qy = y * scale * Math.PI * 2 - 250;
        double ix = qx, iy = qy, c = 1;
        for (int i = 0; i < 4; i++) {
            double tt = time * (1 - 3.5 / (i + 1));
            double nx = qx + Math.cos(tt - ix) + Math.sin(tt + iy);
            iy = qy + Math.sin(tt - iy) + Math.cos(tt + ix); ix = nx;
            c += 1 / Math.hypot(qx / (Math.sin(ix + tt) / 0.005), qy / (Math.cos(iy + tt) / 0.005));
        }
        c = 1.17 - Math.pow(c / 4, 1.4);
        return Math.max(0, Math.min(3, Math.pow(Math.abs(c), 8)));
    }
    private static double wave(Frame v, double x, double y) {
        double dx = x - v.waveB[0], dy = y - v.waveB[1], radius = Math.max(0.001, v.waveB[2]);
        double r = Math.hypot(dx, dy) / radius, mask = FoliaLumiereMath.step(1, 0.55, r);
        double f = v.wave[1], phase = v.time * v.wave[2], value;
        if (v.wave[0] < 1.5) value = 0.5 + 0.5 * Math.cos(r * r * f * 6 - phase);
        else if (v.wave[0] < 2.5) {
            double a = dx / radius, b = dy / radius, xc = a * (1 + 0.35 * b * b);
            value = Math.pow(Math.cos(xc * f * 3 - phase * 0.3), 2) * Math.exp(-xc * xc * 2.5);
            mask = FoliaLumiereMath.step(1, 0.25, Math.hypot(a * 0.85, b * 1.5));
        } else if (v.wave[0] < 3.5) {
            double d1 = Math.hypot(dx + v.waveB[3] / 2, dy), d2 = Math.hypot(dx - v.waveB[3] / 2, dy);
            value = (0.5 + 0.5 * Math.cos((d1 - d2) * f * 20)) * (0.5 + 0.5 * Math.cos((d1 + d2) * f * 6 - phase));
        } else {
            double a = r * f * 4 + 0.001, sc = Math.sin(a) / a; value = Math.min(sc * sc * 4, 1.5);
        }
        double qx = (x - v.shield[0]) / Math.max(0.001, v.shield[2]), qy = (y - v.shield[1]) / Math.max(0.001, v.shield[3]);
        return value * mask * (1 - v.shieldB[0] * FoliaLumiereMath.step(1, 0.35, Math.hypot(qx, qy))) * v.wave[3];
    }

    @android.annotation.TargetApi(33)
    private static final class Gpu {
        private final RuntimeShader shader = new RuntimeShader(FoliaLumiereShader.SOURCE);
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Gpu() { paint.setShader(shader); }
        void draw(Canvas canvas, float width, float height, Frame v) {
            shader.setFloatUniform("uBeamA", v.a); shader.setFloatUniform("uBeamB", v.b);
            shader.setFloatUniform("uBeamC", v.c); shader.setFloatUniform("uBeamD", v.d);
            shader.setFloatUniform("uBeamE", v.e); shader.setFloatUniform("uBeamF", v.f);
            shader.setFloatUniform("uFrame", width, height, (float) v.time, v.count);
            shader.setFloatUniform("uFogA", v.fogA); shader.setFloatUniform("uFogB", v.fogB);
            shader.setFloatUniform("uFogColor", v.color); shader.setFloatUniform("uGlareColor", v.color);
            shader.setFloatUniform("uGlare", v.glare); shader.setFloatUniform("uGlareB", v.glareB);
            shader.setFloatUniform("uCaustic", v.caustic); shader.setFloatUniform("uCausticFloor", v.causticFloor);
            shader.setFloatUniform("uCausticB", v.causticB); shader.setFloatUniform("uWave", v.wave);
            shader.setFloatUniform("uWaveB", v.waveB); shader.setFloatUniform("uShield", v.shield);
            shader.setFloatUniform("uShieldB", v.shieldB); shader.setFloatUniform("uDark", 0, 0, 0, 0);
            canvas.drawRect(-width * 0.02f, -height * 0.02f, width * 1.02f, height * 1.02f, paint);
        }
    }
}
