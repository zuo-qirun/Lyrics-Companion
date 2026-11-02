package com.zuoqirun.lyricscompanion;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Explicit full appearance recipes. Runtime performance profiles never call this code. */
final class AppearancePresets {
    static final String[] NAMES = {"车机全屏 · 大字", "车机全屏 · 封面沉浸", "副屏 / HUD · 简洁单行", "夜间低调"};
    private AppearancePresets() { }

    static String style(int preset) { return preset == 1 ? "refined" : preset == 2 ? "compact" : "pure"; }

    static void show(Activity activity, boolean secondary, Point screenDp, Runnable applied) {
        new MaterialAlertDialogBuilder(activity).setTitle("一键外观预设 · " + (secondary ? "本副屏" : "主屏"))
                .setItems(NAMES, (dialog, which) -> new MaterialAlertDialogBuilder(activity)
                        .setTitle(NAMES[which]).setMessage("将替换本屏的样式、窗口尺寸、字号、配色和位置。"
                                + "其它屏幕、权限、自启动与性能档位保持原设置。之后可继续微调。")
                        .setNegativeButton("取消", null).setPositiveButton("套用", (ignored, index) -> {
                            apply(activity, secondary, which, screenDp.x, screenDp.y);
                            applied.run();
                        }).show()).setNegativeButton("取消", null).show();
    }

    static void apply(Context context, boolean secondary, int preset, int screenWidth, int screenHeight) {
        String style = style(preset);
        AppPreferences.setOverlayStyle(context, secondary, style);
        boolean hud = preset == 2, night = preset == 3;
        int width = Math.max(120, hud ? Math.round(screenWidth * .8f) : screenWidth);
        int height = Math.max(70, hud ? Math.round(screenHeight * .2f) : screenHeight);
        AppPreferences.setPanelWidthDp(context, secondary, width);
        AppPreferences.setPanelHeightDp(context, secondary, height);
        AppPreferences.putDisplayInt(context, secondary, AppPreferences.KEY_TEXT_SCALE, hud ? 100 : 150);
        AppPreferences.putDisplayInt(context, secondary, AppPreferences.KEY_OPACITY, hud ? 35 : 100);
        AppPreferences.putDisplayInt(context, secondary, AppPreferences.KEY_STYLE_LYRIC_LINES, hud ? 1 : 3);
        if (hud) {
            AppPreferences.putDisplayBoolean(context, secondary, AppPreferences.KEY_COMPACT_SHOW_COVER, false);
            AppPreferences.putDisplayBoolean(context, secondary, AppPreferences.KEY_COMPACT_SHOW_NEXT_LINE, false);
            AppPreferences.putDisplayBoolean(context, secondary, AppPreferences.KEY_COMPACT_SHOW_BARS, false);
        }
        AppPreferences.putDisplayString(context, secondary, AppPreferences.KEY_REFINED_BACKGROUND_TYPE,
                preset == 1 ? "blur" : "solid");
        AppPreferences.putDisplayString(context, secondary, AppPreferences.KEY_CONTENT_ALIGN, "center");
        AppPreferences.putDisplayString(context, secondary, AppPreferences.KEY_LYRIC_ALIGN, "center");
        AppPreferences.putDisplayString(context, secondary, AppPreferences.KEY_LONG_LINE_MODE, "ellipsize");
        AppPreferences.putDisplayString(context, secondary, AudioVisualRenderer.MODE, "off");
        AppPreferences.putDisplayString(context, secondary, AppPreferences.KEY_LYRIC_EFFECT, "none");
        AppPreferences.putDisplayString(context, secondary, AppPreferences.KEY_CURRENT_LYRIC_COLOR_MODE, "fixed");
        AppPreferences.putDisplayString(context, secondary, AppPreferences.KEY_INACTIVE_LYRIC_COLOR_MODE, "fixed");
        AppPreferences.setLyricColor(context, secondary, night ? 0xFFABB8C9 : 0xFFF6F8FF);
        AppPreferences.setCurrentLyricColor(context, secondary, night ? 0xFFABB8C9 : 0xFFF6F8FF);
        AppPreferences.setInactiveLyricColor(context, secondary, night ? 0xFF69788B : 0xFF9BAFC8);
        AppPreferences.setCurrentLyricLightColor(context, secondary, 0);
        AppPreferences.setCurrentLyricDarkColor(context, secondary, 0);
        AppPreferences.setInactiveLyricLightColor(context, secondary, 0);
        AppPreferences.setInactiveLyricDarkColor(context, secondary, 0);
        AppPreferences.setBackgroundColor(context, secondary, true, preset == 1 ? 0 : 0xFF07111F);
        AppPreferences.setBackgroundColor(context, secondary, false, preset == 1 ? 0 : 0xFF07111F);
        AppPreferences.putDisplayBoolean(context, secondary, AppPreferences.KEY_CURRENT_LYRIC_OUTLINE, true);
        AppPreferences.putDisplayBoolean(context, secondary, AppPreferences.KEY_INACTIVE_LYRIC_OUTLINE, true);
        float density = context.getResources().getDisplayMetrics().density;
        AppPreferences.putOverlayPosition(context, secondary, AppPreferences.overlayPositionKey(secondary, style, true),
                hud ? Math.round((screenWidth - width) * density * .5f) : 0);
        AppPreferences.putOverlayPosition(context, secondary, AppPreferences.overlayPositionKey(secondary, style, false),
                hud ? Math.round(screenHeight * density * .1f) : 0);
        AppPreferences.changed(context);
    }
}
