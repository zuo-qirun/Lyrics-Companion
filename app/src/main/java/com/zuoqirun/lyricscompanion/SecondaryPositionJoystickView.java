package com.zuoqirun.lyricscompanion;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Position controller adapted from the interaction used by the author's AMap Companion. */
final class SecondaryPositionJoystickView extends View {
    interface Listener { void onMove(int dx, int dy); }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Runnable movementTick = new Runnable() {
        @Override public void run() {
            if (!pressed) return;
            float magnitude = (float) Math.hypot(knobX, knobY);
            if (magnitude > radius * 0.12f) {
                float strength = Math.min(1f, (magnitude - radius * 0.12f) / (radius * 0.88f));
                int maximum = Math.max(1, Math.round(dp(9)));
                int dx = Math.round(knobX / Math.max(1f, radius) * maximum * strength);
                int dy = Math.round(knobY / Math.max(1f, radius) * maximum * strength);
                if (listener != null && (dx != 0 || dy != 0)) listener.onMove(dx, dy);
            }
            postDelayed(this, 33L);
        }
    };
    private Listener listener;
    private float centerX;
    private float centerY;
    private float radius;
    private float knobX;
    private float knobY;
    private boolean pressed;
    private ValueAnimator returnAnimator;

    SecondaryPositionJoystickView(Context context) {
        super(context);
        setClickable(true);
        setContentDescription("副屏悬浮窗位置摇杆");
    }

    void setListener(Listener listener) { this.listener = listener; }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        centerX = width / 2f;
        centerY = height / 2f;
        radius = Math.max(1f, Math.min(width, height) * 0.37f);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x191DCCFF);
        canvas.drawCircle(centerX, centerY, radius * 1.18f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(0x557ADAF4);
        canvas.drawCircle(centerX, centerY, radius, paint);
        canvas.drawLine(centerX - radius * 0.72f, centerY,
                centerX + radius * 0.72f, centerY, paint);
        canvas.drawLine(centerX, centerY - radius * 0.72f,
                centerX, centerY + radius * 0.72f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(pressed ? 0xFFFFCA66 : 0xFF6EE7F2);
        paint.setShadowLayer(dp(9), 0f, dp(3), 0x55000000);
        canvas.drawCircle(centerX + knobX, centerY + knobY, radius * 0.34f, paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(0xCFFFFFFF);
        canvas.drawCircle(centerX + knobX, centerY + knobY, radius * 0.23f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (returnAnimator != null) returnAnimator.cancel();
                pressed = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateKnob(event.getX(), event.getY());
                removeCallbacks(movementTick);
                post(movementTick);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                updateKnob(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
                pressed = false;
                removeCallbacks(movementTick);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                performClick();
                animateHome();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                removeCallbacks(movementTick);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                animateHome();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(movementTick);
        if (returnAnimator != null) returnAnimator.cancel();
        super.onDetachedFromWindow();
    }

    private void updateKnob(float touchX, float touchY) {
        float x = touchX - centerX;
        float y = touchY - centerY;
        float length = (float) Math.hypot(x, y);
        if (length > radius) {
            x = x / length * radius;
            y = y / length * radius;
        }
        knobX = x;
        knobY = y;
        invalidate();
    }

    private void animateHome() {
        float startX = knobX;
        float startY = knobY;
        returnAnimator = ValueAnimator.ofFloat(0f, 1f);
        returnAnimator.setDuration(180L);
        returnAnimator.setInterpolator(new DecelerateInterpolator());
        returnAnimator.addUpdateListener(animation -> {
            float remaining = 1f - (float) animation.getAnimatedValue();
            knobX = startX * remaining;
            knobY = startY * remaining;
            invalidate();
        });
        returnAnimator.start();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
