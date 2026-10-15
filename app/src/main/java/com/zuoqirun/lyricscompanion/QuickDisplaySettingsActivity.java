package com.zuoqirun.lyricscompanion;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/** Retains historical shortcuts while sharing daily controls with the complete settings page. */
public final class QuickDisplaySettingsActivity extends AppCompatActivity {
    static final String EXTRA_SECONDARY = "secondary";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent settings = new Intent(this, DisplaySettingsActivity.class);
        if (getIntent().getExtras() != null) settings.putExtras(getIntent().getExtras());
        startActivity(settings);
        finish();
    }
}
