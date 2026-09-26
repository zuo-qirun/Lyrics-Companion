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
    /**
     * 明度（0–1，issue #60）：原来固定 1，圆盘拖不出纯黑与深色。由外面的「亮度」滑杆驱动，
     * 只改颜色本身，不动 alpha。
     */
    private float value = 1f;
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
        float[] hsv = new float[3];
        Color.colorToHSV(selectedColor, hsv);
        value = hsv[2];
        invalidate();
    }

    /** 亮度轴（issue #60）：0 = 纯黑，1 = 原色。重画圆盘，让整盘跟着变暗。 */
    void setValue(float newValue) {
        float clamped = Math.max(0f, Math.min(1f, newValue));
        if (Math.abs(clamped - value) < 0.001f) return;
        value = clamped;
        if (wheel != null) {
            wheel.recycle();
            wheel = buildWheel(Math.max(2, getWidth()), Math.max(2, getHeight()));
        }
        invalidate();
    }

    float value() { return value; }

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
                if (!isInsideWheel(event.getX(), event.getY())) {
                    // Do not claim the transparent corners of this square View.  This keeps
                    // normal settings-page scrolling available beside the circular palette.
                    return false;
                }
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                pick(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                // The palette uses vertical drags for hue/saturation, so keep its gesture
                // owned by this view instead of allowing the settings ScrollView to move.
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                if (isInsideWheel(event.getX(), event.getY())) pick(event.getX(), event.getY());
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
        // 明度用当前亮度轴的取值（issue #60）：拖到纯黑之后，圆盘上任何位置都给深色。
        int color = ColorWheelMath.rgbFor(ColorWheelMath.hueFor(dx, dy),
                ColorWheelMath.saturationFor(dx, dy, radius), value);
        if (color == selectedColor) return;
        selectedColor = color;
        invalidate();
        if (listener != null) listener.onColorChanged(color);
    }

    private boolean isInsideWheel(float x, float y) {
        float dx = x - centerX;
        float dy = y - centerY;
        return dx * dx + dy * dy <= radius * radius;
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
                float hue = ColorWheelMath.hueFor(dx, dy);
                float saturation = ColorWheelMath.saturationFor(dx, dy, radius);
                // 圆盘整体跟着亮度轴变暗，这样「拖到的地方就是会得到的颜色」（issue #60）。
                pixels[index] = ColorWheelMath.rgbFor(hue, saturation, value);
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
