package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageButton;

/** Full-bleed host for the currently selected main-display lyric style. */
public final class FullscreenLyricsActivity extends AppCompatActivity {
    private LyricsPanelView panel;
    private AppCompatImageButton exitButton;
    private final Handler closeHandler = new Handler(Looper.getMainLooper());
    private final Runnable weakenClose = () -> {
        if (exitButton != null && "fade".equals(AppPreferences.fullscreenCloseMode(this))) {
            exitButton.animate().alpha(0.15f).setDuration(420L).start();
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        MusicStateStore.initialize(this);

        FullscreenGestureLayout root = new FullscreenGestureLayout(this);
        root.setBackgroundColor(Color.BLACK);
        panel = new LyricsPanelView(this, false, true);
        root.setPanel(panel);
        root.setInteractionListener(this::showCloseTemporarily);
        root.addView(panel, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        addExitButton(root);
        setContentView(root);
        CustomFontStore.applyToViewTree(this, root);
    }

    private void addExitButton(FrameLayout root) {
        AppCompatImageButton exit = new AppCompatImageButton(this);
        exitButton = exit;
        exit.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        exit.setColorFilter(Color.WHITE);
        exit.setBackground(closeButtonBackground());
        exit.setContentDescription("退出全屏");
        exit.setPadding(dp(13), dp(13), dp(13), dp(13));
        exit.setOnClickListener(v -> returnToSettings());
        if (Build.VERSION.SDK_INT >= 21) exit.setElevation(dp(6));

        int margin = dp(16);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(48), dp(48),
                Gravity.TOP | Gravity.END);
        params.setMargins(margin, margin, margin, margin);
        root.addView(exit, params);
        applyCloseMode();
        if (Build.VERSION.SDK_INT >= 28) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.view.DisplayCutout cutout = insets.getDisplayCutout();
                int safeTop = cutout == null ? 0 : cutout.getSafeInsetTop();
                int safeRight = cutout == null ? 0 : cutout.getSafeInsetRight();
                FrameLayout.LayoutParams buttonParams =
                        (FrameLayout.LayoutParams) exit.getLayoutParams();
                buttonParams.topMargin = margin + safeTop;
                buttonParams.rightMargin = margin + safeRight;
                exit.setLayoutParams(buttonParams);
                return insets;
            });
        }
    }

    private StateListDrawable closeButtonBackground() {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed},
                oval(0x8A000000));
        states.addState(new int[0], oval(0x52000000));
        return states;
    }

    private static GradientDrawable oval(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(1, 0x48FFFFFF);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemUi();
        if (panel != null) panel.reloadStyle();
        AudioSpectrumSource.sync(this, true);
        LyricsDisplayService.setSettingsVisible(this, true);
        applyCloseMode();
    }

    @Override protected void onPause() {
        closeHandler.removeCallbacks(weakenClose);
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private void applyCloseMode() {
        if (exitButton == null) return;
        closeHandler.removeCallbacks(weakenClose);
        String mode = AppPreferences.fullscreenCloseMode(this);
        exitButton.setVisibility("hidden".equals(mode) ? View.GONE : View.VISIBLE);
        exitButton.setAlpha(1f);
        if ("fade".equals(mode)) closeHandler.postDelayed(weakenClose, 3_000L);
    }

    private void showCloseTemporarily() {
        if (exitButton == null || !"fade".equals(AppPreferences.fullscreenCloseMode(this))) return;
        closeHandler.removeCallbacks(weakenClose);
        exitButton.setVisibility(View.VISIBLE);
        exitButton.animate().alpha(1f).setDuration(140L).start();
        closeHandler.postDelayed(weakenClose, 3_000L);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(params);
        }
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void returnToSettings() {
        if (isFinishing()) return;
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    private static final class FullscreenGestureLayout extends FrameLayout {
        private final GestureDetector detector;
        private LyricsPanelView panel;
        private Runnable interactionListener;

        FullscreenGestureLayout(Context context) {
            super(context);
            setClickable(true);
            detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override public boolean onDown(MotionEvent event) { return true; }

                @Override public boolean onSingleTapUp(MotionEvent event) {
                    if (panel == null) return false;
                    MediaControlAction action = panel.playbackControlAt(event.getX(), event.getY());
                    if (action == null) return false;
                    MusicNotificationListener.requestPlaybackControl(getContext(), action);
                    return true;
                }
            });
        }

        void setPanel(LyricsPanelView panel) { this.panel = panel; }
        void setInteractionListener(Runnable listener) { interactionListener = listener; }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN && interactionListener != null) {
                interactionListener.run();
            }
            detector.onTouchEvent(event);
            return super.dispatchTouchEvent(event);
        }
    }
}
