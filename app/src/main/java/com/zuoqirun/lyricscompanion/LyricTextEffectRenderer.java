package com.zuoqirun.lyricscompanion;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import java.util.HashMap;
import java.util.Map;

/** Effects retain the caller's karaoke clipping, colours, layout and playback clock. */
final class LyricTextEffectRenderer {
    private static final int[] RAINBOW = {0xFFFF697A, 0xFFFFBA65, 0xFFE3ED6D,
            0xFF69E6AA, 0xFF68D8FF, 0xFF9E94FF, 0xFFFF697A};
    private final Paint paint = new Paint();
    private final Matrix matrix = new Matrix();
    private final RectF bounds = new RectF();
    private final Map<Integer, LinearGradient> gradients = new HashMap<>();
    private final LyricGlyphCache glyphCache = new LyricGlyphCache();
    private LyricEffectCatalog.Effect effect = LyricEffectCatalog.Effect.NONE;
    private float strength;
    private int speed;

    void configure(String effect, int strengthPercent, int speedPercent) {
        this.effect = LyricEffectCatalog.find(effect);
        strength = Math.max(0, Math.min(100, strengthPercent)) / 100f;
        speed = speedPercent;
        gradients.clear();
        glyphCache.clear();
    }

    void draw(Canvas canvas, String text, float x, float y, Paint source,
              long positionMs, long entryAgeMs, float regionWidth) {
        if (text == null || text.isEmpty()) return;
        paint.set(source);
        float size = source.getTextSize();
        boolean fill = source.getStyle() == Paint.Style.FILL;
        switch (effect) {
            case CINEMA:
                if (entryAgeMs >= 0L) y += size * 0.30f * strength * LyricEffectRules.entry(entryAgeMs, speed);
                break;
            case BOUNCE:
                y += size * 0.18f * strength * LyricEffectRules.bounce(entryAgeMs, speed);
                break;
            case FLOAT:
                y += size * 0.07f * strength * LyricEffectRules.wave(positionMs, speed, 0f);
                break;
            case RIPPLE:
            case SPARKLE:
            case ORBIT:
                drawGlyphs(canvas, text, x, y, source, positionMs);
                return;
            case MIRROR:
                canvas.drawText(text, x, y, paint);
                if (fill) drawReflection(canvas, text, x, y, source);
                return;
            case FROST:
                if (fill) drawFrost(canvas, text, x, y, source);
                paint.set(source);
                break;
            case GLITCH:
                if (fill) drawGlitch(canvas, text, x, y, source, positionMs);
                paint.set(source);
                break;
            case KARAOKE:
            case NEON:
                if (fill) {
                    float pulse = effect == LyricEffectCatalog.Effect.NEON
                            ? LyricEffectRules.pulse(positionMs, speed) : 0.5f;
                    paint.setShadowLayer(size * (0.08f + 0.28f * strength * pulse), 0f, 0f,
                            alpha(source.getColor(), Math.round(source.getAlpha() * strength * pulse)));
                }
                break;
            case SWEEP:
            case RAINBOW:
            case GRADIENT:
            case FLAME:
                if (fill) {
                    if (effect == LyricEffectCatalog.Effect.FLAME) {
                        drawFireWisps(canvas, text, x, y, source, positionMs);
                        paint.set(source);
                    }
                    applyGradient(source, y, positionMs, regionWidth);
                }
                break;
            default:
                break;
        }
        canvas.drawText(text, x, y, paint);
    }

    private void drawReflection(Canvas canvas, String text, float x, float y, Paint source) {
        float size = source.getTextSize();
        paint.clearShadowLayer();
        paint.setAlpha(Math.round(source.getAlpha() * strength * 0.36f));
        LinearGradient gradient = gradient(source.getColor());
        matrix.setScale(1f, size);
        gradient.setLocalMatrix(matrix);
        paint.setShader(gradient);
        int save = canvas.save();
        // Compressed to the current row so reflection shares the original karaoke clip.
        canvas.translate(0f, y + size * 0.08f);
        canvas.scale(1f, -0.18f);
        canvas.drawText(text, x, 0f, paint);
        canvas.restoreToCount(save);
    }

    private void drawFrost(Canvas canvas, String text, float x, float y, Paint source) {
        float size = source.getTextSize();
        float width = source.measureText(text);
        float left = origin(x, width, source.getTextAlign());
        bounds.set(left - size * 0.12f, y + source.ascent(), left + width + size * 0.12f,
                y + Math.max(source.descent(), size * 0.15f));
        paint.setShader(null);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.FILL);
        int tint = Color.red(source.getColor()) + Color.green(source.getColor())
                + Color.blue(source.getColor()) > 384 ? 0xFF253344 : 0xFFF1F6FA;
        paint.setColor(alpha(tint, Math.round(source.getAlpha() * strength * 0.22f)));
        canvas.drawRoundRect(bounds, size * 0.18f, size * 0.18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(0.6f, size * 0.012f));
        paint.setColor(alpha(Color.WHITE, Math.round(source.getAlpha() * strength * 0.30f)));
        canvas.drawRoundRect(bounds, size * 0.18f, size * 0.18f, paint);
    }

    private void drawGlitch(Canvas canvas, String text, float x, float y, Paint source, long time) {
        long tick = (long) (LyricEffectRules.phase(time, speed) * 24f);
        float noise = LyricEffectRules.noise(tick + text.hashCode());
        if (noise > 0.22f) return;
        float size = source.getTextSize();
        float displacement = size * strength * (0.035f + noise * 0.20f);
        float width = source.measureText(text);
        float left = origin(x, width, source.getTextAlign());
        paint.clearShadowLayer();
        paint.setShader(null);
        int save = canvas.save();
        canvas.clipRect(left - size, y - size * 0.70f, left + width + size, y - size * 0.23f);
        paint.setColor(alpha(blend(source.getColor(), 0xFF49EFFF, strength),
                Math.round(source.getAlpha() * strength * 0.45f)));
        canvas.drawText(text, x - displacement, y, paint);
        paint.setColor(alpha(blend(source.getColor(), 0xFFFF579E, strength),
                Math.round(source.getAlpha() * strength * 0.45f)));
        canvas.drawText(text, x + displacement, y, paint);
        canvas.restoreToCount(save);
    }

    private void drawGlyphs(Canvas canvas, String text, float x, float y, Paint source, long time) {
        if (effect == LyricEffectCatalog.Effect.SPARKLE) {
            canvas.drawText(text, x, y, paint);
            if (source.getStyle() != Paint.Style.FILL) return;
        }
        LyricGlyphCache.Layout layout = glyphCache.get(text, source);
        float left = origin(x, layout.width, source.getTextAlign());
        float size = source.getTextSize();
        boolean fill = source.getStyle() == Paint.Style.FILL;
        for (int i = 0; i < layout.glyphs.length; i++) {
            float glyphX = left + layout.offsets[i];
            float wave = LyricEffectRules.wave(time, speed, glyphX / Math.max(1f, size * 5f));
            float glyphY = y;
            float scale = 1f;
            paint.set(source);
            paint.setTextAlign(Paint.Align.LEFT);
            if (effect == LyricEffectCatalog.Effect.RIPPLE) {
                glyphY += wave * size * 0.10f * strength;
            } else if (effect == LyricEffectCatalog.Effect.ORBIT) {
                glyphY += wave * size * 0.055f * strength;
                scale += wave * 0.025f * strength;
                if (fill && layout.visible[i]) {
                    paint.setColor(alpha(source.getColor(), Math.round(source.getAlpha() * strength * 0.20f)));
                    canvas.drawText(layout.glyphs[i], glyphX + size * 0.035f, glyphY + size * 0.035f, paint);
                    paint.set(source);
                    paint.setTextAlign(Paint.Align.LEFT);
                }
            }
            int save = canvas.save();
            canvas.scale(scale, scale, glyphX + layout.widths[i] * 0.5f, glyphY);
            if (effect != LyricEffectCatalog.Effect.SPARKLE) {
                canvas.drawText(layout.glyphs[i], glyphX, glyphY, paint);
            }
            if (fill && layout.visible[i] && (effect == LyricEffectCatalog.Effect.SPARKLE
                    || effect == LyricEffectCatalog.Effect.ORBIT)) {
                float flicker = Math.max(0f, wave - 0.45f) / 0.55f;
                float sparkleX = glyphX + layout.widths[i] * (0.2f + 0.6f * LyricEffectRules.noise(i + text.hashCode()));
                float sparkleY = glyphY - size * (0.35f + 0.42f * LyricEffectRules.noise(i * 17L + text.hashCode()));
                drawStar(canvas, sparkleX, sparkleY, size * 0.055f * strength,
                        blend(source.getColor(), Color.WHITE, 0.8f),
                        Math.round(source.getAlpha() * strength * flicker));
            }
            canvas.restoreToCount(save);
        }
    }

    private void drawStar(Canvas canvas, float x, float y, float radius, int color, int opacity) {
        if (opacity <= 0 || radius <= 0f) return;
        paint.setShader(null);
        paint.clearShadowLayer();
        paint.setColor(alpha(color, opacity));
        paint.setStrokeWidth(Math.max(0.5f, radius * 0.3f));
        canvas.drawLine(x - radius, y, x + radius, y, paint);
        canvas.drawLine(x, y - radius, x, y + radius, paint);
    }

    private void drawFireWisps(Canvas canvas, String text, float x, float y, Paint source, long time) {
        float size = source.getTextSize();
        float width = source.measureText(text);
        float left = origin(x, width, source.getTextAlign());
        int count = Math.max(1, Math.min(12, text.length()));
        paint.setShader(null);
        paint.clearShadowLayer();
        paint.setStrokeWidth(Math.max(0.6f, size * 0.025f));
        for (int i = 0; i < count; i++) {
            float phase = LyricEffectRules.phase(time + i * 193L, speed);
            float wispX = left + width * LyricEffectRules.noise(i * 53L + text.hashCode());
            float top = y - size * (0.60f + phase * 0.38f);
            paint.setColor(alpha(blend(source.getColor(), 0xFFFFAC56, strength),
                    Math.round(source.getAlpha() * strength * (1f - phase) * 0.40f)));
            canvas.drawLine(wispX, top, wispX + size * 0.035f * LyricEffectRules.wave(time, speed, i),
                    top + size * 0.09f * strength, paint);
        }
    }

    private void applyGradient(Paint source, float y, long time, float regionWidth) {
        LinearGradient gradient = gradient(source.getColor());
        if (effect == LyricEffectCatalog.Effect.FLAME) {
            matrix.setScale(1f, source.getTextSize());
            matrix.postTranslate(0f, y - source.getTextSize() * (0.90f
                    + 0.035f * LyricEffectRules.wave(time, speed, 0f)));
        } else {
            float width = Math.max(1f, regionWidth);
            matrix.setScale(width, 1f);
            float phase = LyricEffectRules.phase(time, speed);
            matrix.postTranslate(width * (effect == LyricEffectCatalog.Effect.SWEEP
                    ? phase * 2f - 1f : phase), 0f);
        }
        gradient.setLocalMatrix(matrix);
        paint.setShader(gradient);
    }

    private LinearGradient gradient(int sourceColor) {
        int color = sourceColor | 0xFF000000;
        LinearGradient cached = gradients.get(color);
        if (cached != null) return cached;
        if (gradients.size() >= 16) gradients.clear();
        LinearGradient result;
        if (effect == LyricEffectCatalog.Effect.MIRROR) {
            result = new LinearGradient(0f, -1f, 0f, 0f, color & 0xFFFFFF, color, Shader.TileMode.CLAMP);
        } else if (effect == LyricEffectCatalog.Effect.FLAME) {
            result = new LinearGradient(0f, 0f, 0f, 1f,
                    new int[]{blend(color, 0xFFFFF2AF, strength), blend(color, 0xFFFFB147, strength),
                            blend(color, 0xFFFF553D, strength)}, null, Shader.TileMode.CLAMP);
        } else if (effect == LyricEffectCatalog.Effect.RAINBOW) {
            int[] colors = new int[RAINBOW.length];
            for (int i = 0; i < colors.length; i++) colors[i] = blend(color, RAINBOW[i], strength);
            result = new LinearGradient(0f, 0f, 1f, 0f, colors, null, Shader.TileMode.REPEAT);
        } else if (effect == LyricEffectCatalog.Effect.GRADIENT) {
            result = new LinearGradient(0f, 0f, 1f, 0f,
                    new int[]{color, blend(color, 0xFF65DAFF, strength), blend(color, 0xFFFF7DB8, strength), color},
                    null, Shader.TileMode.REPEAT);
        } else {
            result = new LinearGradient(0f, 0f, 1f, 0f,
                    new int[]{color, blend(color, Color.WHITE, strength * 0.75f), color},
                    new float[]{0.35f, 0.5f, 0.65f}, Shader.TileMode.CLAMP);
        }
        gradients.put(color, result);
        return result;
    }

    private static float origin(float anchor, float width, Paint.Align align) {
        return align == Paint.Align.CENTER ? anchor - width * 0.5f
                : align == Paint.Align.RIGHT ? anchor - width : anchor;
    }

    private static int alpha(int color, int opacity) {
        return (color & 0x00FFFFFF) | (Math.max(0, Math.min(255, opacity)) << 24);
    }

    private static int blend(int from, int to, float amount) {
        float weight = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * weight),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * weight),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * weight));
    }
}
