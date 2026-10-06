package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import java.util.HashMap;
import java.util.Map;

/** Reusable glyph effects. The caller owns lyric timing, clipping, layout and frame scheduling. */
final class LyricTextEffectRenderer {
    private final Paint paint = new Paint();
    private final Matrix matrix = new Matrix();
    private final Map<Integer, LinearGradient> gradients = new HashMap<>();
    private String effect = "none";
    private float strength;
    private int speed;

    void configure(String effect, int strengthPercent, int speedPercent) {
        this.effect = LyricEffectRules.normalize(effect);
        strength = Math.max(0, Math.min(100, strengthPercent)) / 100f;
        speed = speedPercent;
        gradients.clear();
    }

    void draw(Canvas canvas, String text, float x, float y, Paint source,
              long positionMs, float entryProgress, float regionWidth) {
        paint.set(source);
        float size = source.getTextSize();
        if ("cinema".equals(effect)) {
            y += size * 0.30f * strength * entryProgress;
        } else if (source.getStyle() == Paint.Style.FILL && "neon".equals(effect)) {
            float pulse = LyricEffectRules.pulse(positionMs, speed);
            int glow = (source.getColor() & 0x00FFFFFF)
                    | (Math.round(source.getAlpha() * strength * pulse) << 24);
            paint.setShadowLayer(size * (0.08f + 0.28f * strength * pulse), 0f, 0f, glow);
        } else if (source.getStyle() == Paint.Style.FILL && "sweep".equals(effect)) {
            int color = source.getColor() | 0xFF000000;
            LinearGradient gradient = gradients.get(color);
            if (gradient == null) {
                if (gradients.size() >= 16) gradients.clear();
                float amount = strength * 0.75f;
                int highlight = Color.rgb(Math.round(Color.red(color) + (255 - Color.red(color)) * amount),
                        Math.round(Color.green(color) + (255 - Color.green(color)) * amount),
                        Math.round(Color.blue(color) + (255 - Color.blue(color)) * amount));
                gradient = new LinearGradient(0f, 0f, 1f, 0f, new int[]{color, highlight, color},
                        new float[]{0.35f, 0.5f, 0.65f}, Shader.TileMode.CLAMP);
                gradients.put(color, gradient);
            }
            float width = Math.max(1f, regionWidth);
            matrix.setScale(width, 1f);
            matrix.postTranslate(width * (LyricEffectRules.phase(positionMs, speed) * 2f - 1f), 0f);
            gradient.setLocalMatrix(matrix);
            paint.setShader(gradient);
        }
        canvas.drawText(text, x, y, paint);
    }
}
