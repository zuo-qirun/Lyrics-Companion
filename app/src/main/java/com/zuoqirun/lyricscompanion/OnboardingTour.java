package com.zuoqirun.lyricscompanion;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import com.google.android.material.button.MaterialButton;

/** An Activity-local spotlight over the real controls. It never applies appearance choices. */
@android.annotation.SuppressLint("SetTextI18n")
final class OnboardingTour {
    static final int STEP_COUNT = 8;
    private static final String ACTIVE = "onboarding_active";
    private static final String ORIGINAL_SECTION = "onboarding_original_section";
    private static final String ORIGINAL_SCROLL = "onboarding_original_scroll";
    static final String[] TITLES = {"认识页面导航", "选择设置模式", "音乐读取权限", "悬浮窗权限",
            "打开主屏歌词", "调整显示与外观", "查看实时预览", "遇到问题先看诊断"};

    interface Host {
        View target(int step);
        void prepare(int step, Runnable ready);
        String description(int step);
        String actionLabel(int step);
        void performAction(int step);
        int section();
        int scrollY();
        void closed(int section, int scrollY);
    }

    private final AppCompatActivity activity;
    private final Host host;
    private final FrameLayout root;
    private final OnBackPressedCallback back;
    private Spotlight layer;
    private boolean active, resumed;
    private int step, originalSection, originalScroll;

    OnboardingTour(AppCompatActivity activity, Host host) {
        this.activity = activity; this.host = host;
        root = activity.findViewById(android.R.id.content);
        back = new OnBackPressedCallback(false) {
            @Override public void handleOnBackPressed() {
                if (step > 0) show(step - 1); else finish();
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, back);
    }

    void restore(Bundle state, boolean firstLaunch) {
        if (state != null && state.getBoolean(ACTIVE, false)) {
            active = true;
            step = normalizeStep(state.getInt(OnboardingState.STEP, 0));
            originalSection = state.getInt(ORIGINAL_SECTION, 0);
            originalScroll = Math.max(0, state.getInt(ORIGINAL_SCROLL, 0));
        } else if (firstLaunch) start();
    }

    static int normalizeStep(int value) { return Math.max(0, Math.min(STEP_COUNT - 1, value)); }
    static boolean restoring(Bundle state) { return state != null && state.getBoolean(ACTIVE, false); }

    void start() {
        if (!active) { originalSection = host.section(); originalScroll = host.scrollY(); }
        active = true;
        show(0);
    }

    boolean isActive() { return active; }

    void save(Bundle state) {
        state.putBoolean(ACTIVE, active);
        state.putInt(OnboardingState.STEP, step);
        state.putInt(ORIGINAL_SECTION, originalSection);
        state.putInt(ORIGINAL_SCROLL, originalScroll);
    }

    void resume() {
        resumed = true;
        if (active) show(step);
    }

    void pause() {
        resumed = false;
        back.setEnabled(false);
        if (layer != null) { layer.observe(false); layer.setVisibility(View.GONE); }
    }

    void destroy() {
        pause();
        if (layer != null) root.removeView(layer);
        layer = null;
    }

    void refreshStatus() {
        if (active && resumed && layer != null) layer.refreshDescription();
    }

    private void show(int next) {
        step = normalizeStep(next);
        if (!active || !resumed || activity.isFinishing() || activity.isDestroyed()) return;
        back.setEnabled(true);
        if (layer == null) {
            layer = new Spotlight();
            root.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        }
        layer.setVisibility(View.VISIBLE);
        layer.bringToFront();
        layer.bind();
        layer.observe(true);
        host.prepare(step, () -> {
            if (!active || !resumed || layer == null) return;
            layer.updatePosition();
            ViewCompat.setAccessibilityPaneTitle(layer.card, TITLES[step] + "，第" + (step + 1) + "步");
        });
    }

    private void finish() {
        if (!active) return;
        active = false;
        back.setEnabled(false);
        if (layer != null) { layer.observe(false); root.removeView(layer); layer = null; }
        AppPreferences.get(activity).edit().putBoolean(OnboardingState.FINISHED, true).apply();
        host.closed(originalSection, originalScroll);
    }

    private int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private final class Spotlight extends FrameLayout implements ViewTreeObserver.OnPreDrawListener {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Rect localVisible = new Rect();
        private final RectF hole = new RectF();
        private final RectF previousHole = new RectF(-1, -1, -1, -1);
        private final int[] targetLocation = new int[2], rootLocation = new int[2];
        private final LinearLayout card, headingRow, footer;
        private final TextView heading, body, progress;
        private final ScrollView bodyScroll;
        private final MaterialButton previous, next, action;
        private ViewTreeObserver observer;
        private OnboardingLayout.Placement placement;
        private boolean needsPlacement = true;
        private int previousWidth, previousHeight;
        private View accessibilityContent;
        private int previousAccessibility;

        Spotlight() {
            super(activity);
            setWillNotDraw(false);
            setClickable(true);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(10), dp(16), dp(10));
            card.setClickable(true);
            card.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            GradientDrawable background = new GradientDrawable();
            background.setColor(0xFFF0F4FC); background.setCornerRadius(dp(14));
            card.setBackground(background);
            if (Build.VERSION.SDK_INT >= 21 && !AppPreferences.lowResourceMode(activity)) card.setElevation(dp(6));
            headingRow = new LinearLayout(activity);
            headingRow.setGravity(Gravity.CENTER_VERTICAL);
            heading = text(18, 0xFF17263D);
            heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            heading.setMaxLines(2);
            headingRow.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
            TextView close = text(26, 0xFF5F7089);
            close.setText("×"); close.setGravity(Gravity.CENTER);
            close.setContentDescription("跳过引导");
            close.setOnClickListener(v -> finish());
            headingRow.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
            card.addView(headingRow, new LinearLayout.LayoutParams(-1, -2));
            body = text(14, 0xFF465A76);
            body.setLineSpacing(dp(3), 1.05f);
            body.setPadding(0, dp(3), 0, dp(7));
            bodyScroll = new ScrollView(activity);
            bodyScroll.addView(body, new ScrollView.LayoutParams(-1, -2));
            card.addView(bodyScroll, new LinearLayout.LayoutParams(-1, 0, 1));
            action = button(false);
            action.setOnClickListener(v -> host.performAction(step));
            card.addView(action, new LinearLayout.LayoutParams(-1, dp(44)));
            footer = new LinearLayout(activity);
            footer.setGravity(Gravity.CENTER_VERTICAL);
            progress = text(12, 0xFF70829B);
            footer.addView(progress, new LinearLayout.LayoutParams(0, -2, 1));
            previous = button(false); previous.setText("上一步");
            previous.setOnClickListener(v -> show(step - 1));
            footer.addView(previous, new LinearLayout.LayoutParams(dp(76), dp(48)));
            next = button(true);
            next.setOnClickListener(v -> { if (step == STEP_COUNT - 1) finish(); else show(step + 1); });
            LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(dp(84), dp(48));
            nextParams.leftMargin = dp(6);
            footer.addView(next, nextParams);
            card.addView(footer, new LinearLayout.LayoutParams(-1, dp(48)));
            addView(card, new FrameLayout.LayoutParams(-1, -2));
        }

        void bind() {
            heading.setText(TITLES[step]);
            progress.setText((step + 1) + " / " + STEP_COUNT);
            previous.setEnabled(step > 0);
            previous.setAlpha(step > 0 ? 1f : .35f);
            next.setText(step == STEP_COUNT - 1 ? "完成" : "下一步");
            String label = host.actionLabel(step);
            action.setVisibility(label == null ? GONE : VISIBLE);
            action.setText(label == null ? "" : label);
            bodyScroll.scrollTo(0, 0);
            refreshDescription();
            needsPlacement = true;
            requestLayout(); invalidate();
        }

        void refreshDescription() {
            String description = host.description(step);
            if (!description.contentEquals(body.getText())) {
                body.setText(description);
                needsPlacement = true;
                requestLayout(); invalidate();
            }
        }

        void observe(boolean enable) {
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            ViewTreeObserver current = getViewTreeObserver();
            if (current.isAlive() && current != observer) current.removeOnPreDrawListener(this);
            observer = null;
            if (enable) {
                observer = getViewTreeObserver(); observer.addOnPreDrawListener(this);
                if (accessibilityContent == null && root.getChildCount() > 0) {
                    accessibilityContent = root.getChildAt(0);
                    previousAccessibility = accessibilityContent.getImportantForAccessibility();
                    accessibilityContent.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                }
            } else if (accessibilityContent != null) {
                accessibilityContent.setImportantForAccessibility(previousAccessibility);
                accessibilityContent = null;
            }
        }

        @Override public boolean onPreDraw() { return !updatePosition(); }

        @android.annotation.SuppressLint("RtlHardcoded") // Placement uses absolute viewport coordinates.
        boolean updatePosition() {
            int width = getWidth(), height = getHeight();
            if (width < dp(64) || height < dp(64)) return false;
            View target = host.target(step);
            hole.setEmpty();
            if (target != null && target.isShown() && target.getLocalVisibleRect(localVisible)) {
                target.getLocationOnScreen(targetLocation); getLocationOnScreen(rootLocation);
                localVisible.offset(targetLocation[0] - rootLocation[0], targetLocation[1] - rootLocation[1]);
                hole.set(localVisible); hole.inset(-dp(5), -dp(5));
                if (!hole.intersect(dp(3), dp(3), width - dp(3), height - dp(3))) hole.setEmpty();
            }
            if (!needsPlacement && width == previousWidth && height == previousHeight && hole.equals(previousHole)) return false;
            previousWidth = width; previousHeight = height; previousHole.set(hole); needsPlacement = false;
            int cardWidth = Math.min(dp(360), width - dp(24));
            int innerWidth = Math.max(1, cardWidth - dp(32));
            body.measure(MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            headingRow.measure(MeasureSpec.makeMeasureSpec(innerWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int preferredHeight = dp(68) + headingRow.getMeasuredHeight() + Math.min(dp(160), body.getMeasuredHeight())
                    + (action.getVisibility() == VISIBLE ? dp(44) : 0);
            placement = OnboardingLayout.place(width, height, Math.round(hole.left), Math.round(hole.top),
                    Math.round(hole.right), Math.round(hole.bottom), cardWidth, preferredHeight, dp(12), dp(12));
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
            params.width = placement.width; params.height = placement.height;
            params.leftMargin = placement.left; params.topMargin = placement.top;
            params.gravity = Gravity.TOP | Gravity.LEFT;
            card.setLayoutParams(params);
            invalidate();
            return true;
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            paint.setStyle(Paint.Style.FILL); paint.setColor(0xB8000000);
            path.reset(); path.setFillType(Path.FillType.EVEN_ODD);
            path.addRect(0, 0, getWidth(), getHeight(), Path.Direction.CW);
            if (!hole.isEmpty()) path.addRoundRect(hole, dp(9), dp(9), Path.Direction.CW);
            canvas.drawPath(path, paint);
            if (!hole.isEmpty()) {
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(0xFF9CCBFF);
                canvas.drawRoundRect(hole, dp(9), dp(9), paint);
            }
            drawArrow(canvas);
        }

        private void drawArrow(Canvas canvas) {
            if (placement == null || hole.isEmpty() || placement.side == OnboardingLayout.CENTER) return;
            float x = Math.max(placement.left + dp(18), Math.min(placement.left + placement.width - dp(18), hole.centerX()));
            float y = Math.max(placement.top + dp(18), Math.min(placement.top + placement.height - dp(18), hole.centerY()));
            int size = dp(8);
            path.reset();
            switch (placement.side) {
                case OnboardingLayout.RIGHT:
                    x = placement.left; path.moveTo(x, y - size); path.lineTo(x - size, y); path.lineTo(x, y + size); break;
                case OnboardingLayout.LEFT:
                    x = placement.left + placement.width; path.moveTo(x, y - size); path.lineTo(x + size, y); path.lineTo(x, y + size); break;
                case OnboardingLayout.BELOW:
                    y = placement.top; path.moveTo(x - size, y); path.lineTo(x, y - size); path.lineTo(x + size, y); break;
                default:
                    y = placement.top + placement.height; path.moveTo(x - size, y); path.lineTo(x, y + size); path.lineTo(x + size, y); break;
            }
            path.close(); paint.setStyle(Paint.Style.FILL); paint.setColor(0xFFF0F4FC); canvas.drawPath(path, paint);
        }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && !hole.isEmpty()
                    && hole.contains(event.getX(), event.getY())
                    && !(event.getX() >= card.getLeft() && event.getX() < card.getRight()
                    && event.getY() >= card.getTop() && event.getY() < card.getBottom())) return false;
            return super.dispatchTouchEvent(event);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return true;
        }

        @Override public boolean performClick() { super.performClick(); return true; }

        private TextView text(int size, int color) {
            TextView view = new TextView(activity); view.setTextSize(size); view.setTextColor(color); return view;
        }

        private MaterialButton button(boolean primary) {
            MaterialButton button = new MaterialButton(activity);
            button.setAllCaps(false); button.setTextSize(13); button.setCornerRadius(dp(9));
            button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(6), 0, dp(6), 0);
            button.setTextColor(primary ? Color.WHITE : 0xFF3568A5);
            button.setBackgroundTintList(ColorStateList.valueOf(primary ? 0xFF386EBA : Color.TRANSPARENT));
            return button;
        }
    }
}
