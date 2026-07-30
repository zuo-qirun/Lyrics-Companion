package com.zuoqirun.lyricscompanion;

import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public final class LayoutEditorActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101418);

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("个性化显示内容");
        toolbar.setSubtitle(useTwoColumnLayout()
                ? "拖入左侧显示，拖回右侧隐藏"
                : "拖入上方显示，拖回下方隐藏");
        toolbar.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(72)));

        LyricsLayoutEditorView editor = new LyricsLayoutEditorView(this);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(dp(16), dp(8), dp(16), dp(16));
        MaterialButton reset = new MaterialButton(this);
        reset.setText("恢复默认布局");
        reset.setOnClickListener(v -> editor.reset());
        actions.addView(reset, new LinearLayout.LayoutParams(0, dp(52), 1f));
        MaterialButton done = new MaterialButton(this);
        done.setText("完成");
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        doneParams.leftMargin = dp(12);
        actions.addView(done, doneParams);
        root.addView(actions);
        editor.setOnLayoutChangedListener(() -> AppPreferences.changed(this));
        setContentView(root);
        CustomFontStore.applyToViewTree(this, root);
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private boolean useTwoColumnLayout() {
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        return getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                && widthDp >= 600f;
    }
}
