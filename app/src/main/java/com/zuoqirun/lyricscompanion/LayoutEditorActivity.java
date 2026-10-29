package com.zuoqirun.lyricscompanion;

import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.FrameLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

public final class LayoutEditorActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_layout_editor);

        MaterialToolbar toolbar = findViewById(R.id.layout_editor_toolbar);
        toolbar.setTitle("个性化显示内容");
        toolbar.setSubtitle(useTwoColumnLayout()
                ? "拖入左侧显示，拖回右侧隐藏"
                : "拖入上方显示，拖回下方隐藏");
        toolbar.setNavigationOnClickListener(v -> finish());

        LyricsLayoutEditorView editor = new LyricsLayoutEditorView(this);
        FrameLayout host = findViewById(R.id.layout_editor_host);
        host.addView(editor, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        MaterialButton reset = findViewById(R.id.layout_editor_reset);
        reset.setText("恢复默认布局");
        reset.setOnClickListener(v -> editor.reset());
        MaterialButton done = findViewById(R.id.layout_editor_done);
        done.setText("完成");
        done.setOnClickListener(v -> finish());

        editor.setOnLayoutChangedListener(() -> AppPreferences.changed(this));
        CustomFontStore.applyToViewTree(this, findViewById(R.id.layout_editor_root));
    }

    @Override protected void onResume() {
        super.onResume();
        LyricsDisplayService.setSettingsVisible(this, true);
    }

    @Override protected void onPause() {
        LyricsDisplayService.setSettingsVisible(this, false);
        super.onPause();
    }

    private boolean useTwoColumnLayout() {
        float widthDp = getResources().getDisplayMetrics().widthPixels
                / getResources().getDisplayMetrics().density;
        return getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE
                && widthDp >= 600f;
    }
}
