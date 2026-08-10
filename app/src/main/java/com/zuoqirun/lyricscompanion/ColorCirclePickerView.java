package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** A touch-friendly HSV color circle: hue around the edge, white at the center. */
final class ColorCirclePickerView extends View {
    interface Listener { void onColorChanged(int color); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap wheel;
    private float centerX;
    private float centerY;
    private float radius;
    private int selectedColor = Color.WHITE;
    private Listener listener;

    ColorCirclePickerView(Context context) {
        super(context);
        setContentDescription("圆形调色盘");
        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(dp(2));
        markerPaint.setColor(Color.WHITE);
        markerPaint.setShadowLayer(dp(2), 0f, dp(1), Color.BLACK);
        setLayerType(LAYER_TYPE_SOFTWARE, markerPaint);
    }

    void setColor(int color) {
        selectedColor = color | 0xFF000000;
        invalidate();
    }

    void setListener(Listener value) { listener = value; }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        centerX = width * 0.5f;
        centerY = height * 0.5f;
        radius = Math.max(1f, Math.min(width, height) * 0.5f - dp(5));
        if (wheel != null) wheel.recycle();
        wheel = buildWheel(Math.max(2, width), Math.max(2, height));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (wheel != null) canvas.drawBitmap(wheel, 0f, 0f, paint);
        float[] hsv = new float[3];
        Color.colorToHSV(selectedColor, hsv);
        float angle = (float) Math.toRadians(-hsv[0]);
        float distance = radius * hsv[1];
        float x = centerX + (float) Math.cos(angle) * distance;
        float y = centerY + (float) Math.sin(angle) * distance;
        markerPaint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, dp(9), markerPaint);
        markerPaint.setColor(Color.argb(190, 0, 0, 0));
        markerPaint.setStrokeWidth(dp(1));
        canvas.drawCircle(x, y, dp(10), markerPaint);
        markerPaint.setStrokeWidth(dp(2));
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                // The palette uses vertical drags for hue/saturation, so keep its gesture
                // owned by this view instead of allowing the settings ScrollView to move.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                pick(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                if (event.getActionMasked() == MotionEvent.ACTION_CANCEL) return true;
                performClick();
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private void pick(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        float saturation = Math.min(1f, distance / Math.max(1f, radius));
        float hue = (float) ((Math.toDegrees(-Math.atan2(dy, dx)) + 360d) % 360d);
        int color = Color.HSVToColor(new float[]{hue, saturation, 1f});
        if (color == selectedColor) return;
        selectedColor = color;
        invalidate();
        if (listener != null) listener.onColorChanged(color);
    }

    private Bitmap buildWheel(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float dx = x + .5f - centerX;
                float dy = y + .5f - centerY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                int index = y * width + x;
                if (distance > radius) {
                    pixels[index] = Color.TRANSPARENT;
                    continue;
                }
                float hue = (float) ((Math.toDegrees(-Math.atan2(dy, dx)) + 360d) % 360d);
                float saturation = Math.min(1f, distance / radius);
                pixels[index] = Color.HSVToColor(new float[]{hue, saturation, 1f});
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    @Override protected void onDetachedFromWindow() {
        if (wheel != null) { wheel.recycle(); wheel = null; }
        super.onDetachedFromWindow();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
