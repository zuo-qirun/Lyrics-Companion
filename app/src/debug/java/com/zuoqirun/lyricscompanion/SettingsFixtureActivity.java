package com.zuoqirun.lyricscompanion;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** DUMP-protected entry point for emulator settings navigation and screen-isolation checks. */
public final class SettingsFixtureActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        int slot = Math.max(0, getIntent().getIntExtra("slot", 0));
        boolean secondary = slot > 0;
        Context scoped = slot >= 2 ? new DisplaySlotContext(this, slot) : this;
        if (getIntent().hasExtra("uiScale")) {
            AppPreferences.setSettingsUiScale(this, getIntent().getIntExtra("uiScale", 100));
        }
        if (getIntent().hasExtra("style")) {
            AppPreferences.setOverlayStyle(scoped, secondary, getIntent().getStringExtra("style"));
        }
        String page = getIntent().getStringExtra("page");
        Class<?> target = "main".equals(page) ? MainActivity.class
                : "status".equals(page) ? StatusLyricSettingsActivity.class
                : "colors".equals(page) ? ColorSettingsActivity.class
                : "refined".equals(page) ? RefinedSettingsActivity.class
                : "compact".equals(page) ? CompactSettingsActivity.class
                : "quick".equals(page) ? QuickDisplaySettingsActivity.class : DisplaySettingsActivity.class;
        startActivity(new Intent(this, target).putExtra("secondary", secondary)
                .putExtra(DisplaySlotContext.EXTRA_SLOT, slot)
                .putExtra(SettingsNavigator.EXTRA_QUERY, getIntent().getStringExtra("query"))
                .putExtra(ColorSettingsActivity.EXTRA_SCOPE, slot < 2 ? slot : slot + 1));
        finish();
    }
}
