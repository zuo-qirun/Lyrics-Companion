package com.zuoqirun.lyricscompanion;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** One channel-scoped cache shared by updates, feedback, snapshots and the update card. */
final class VersionStatus {
    static final String EXPECTED_PACKAGE = "com.zuoqirun.lyricscompanion";

    private VersionStatus() { }

    private static String prefix(Context context) {
        return "known_update_" + AppPreferences.updateChannel(context) + "_";
    }

    static void remember(Context context, String channel, boolean available, int code,
                         String name, int releases) {
        String key = "known_update_" + UpdateChannelRules.normalize(channel) + "_";
        int localCode;
        try { localCode = AppUpdater.localVersionCode(context); }
        catch (Exception ignored) { localCode = 0; }
        AppPreferences.get(context).edit().putInt(key + "code", available ? code : 0)
                .putInt(key + "localCode", localCode)
                .putString(key + "name", available ? name : "")
                .putInt(key + "releases", releases)
                .putLong(key + "checkedAt", System.currentTimeMillis()).apply();
    }

    static JSONObject fields(Context context) throws Exception {
        SharedPreferences prefs = AppPreferences.get(context);
        String key = prefix(context);
        int latest = prefs.getInt(key + "code", 0);
        int local = AppUpdater.localVersionCode(context);
        String latestName = prefs.getString(key + "name", "");
        JSONObject result = new JSONObject();
        result.put("updateChannel", AppPreferences.updateChannel(context));
        result.put("latestKnownVersion", latestName);
        result.put("latestKnownVersionCode", latest);
        result.put("versionStatusKnown", latest > 0);
        result.put("outdated", latest > 0 ? local < latest : JSONObject.NULL);
        result.put("daysBehind", latest <= 0 ? -1 : latest > local
                ? VersionAgeRules.daysBehind(AppUpdater.localVersionName(context), latestName) : 0);
        result.put("releasesBehind", latest <= 0 ? -1 : latest > local
                ? prefs.getInt(key + "localCode", 0) == local ? prefs.getInt(key + "releases", -1) : -1 : 0);
        result.put("versionCheckedAt", prefs.getLong(key + "checkedAt", 0L));
        result.put("expectedPackage", EXPECTED_PACKAGE);
        result.put("actualPackage", context.getPackageName());
        result.put("packageMatches", EXPECTED_PACKAGE.equals(context.getPackageName()));
        return result;
    }

    static String summary(Context context) {
        try {
            JSONObject status = fields(context);
            if (!status.optBoolean("versionStatusKnown")) return "尚未取得所选通道的最新版本";
            String text = "最新 " + status.optString("latestKnownVersion") + " · "
                    + UpdateChannelRules.displayName(status.optString("updateChannel"));
            if (status.optBoolean("outdated")) {
                text += " · 旧版本";
                if (status.optInt("releasesBehind", -1) >= 0) {
                    text += " · 落后 " + status.optInt("releasesBehind") + " 个版本";
                }
                if (status.optInt("daysBehind", -1) >= 0) {
                    text += " · 相差 " + status.optInt("daysBehind") + " 天";
                }
            }
            return text + "（最近一次检查）";
        } catch (Exception ignored) { return "版本状态暂不可用"; }
    }

    static boolean warnBeforeFeedback(Context context, Runnable proceed) {
        try {
            JSONObject status = fields(context);
            int latest = status.optInt("latestKnownVersionCode");
            if (!VersionAgeRules.shouldWarn(status.optBoolean("outdated"),
                    status.optInt("daysBehind", -1), status.optInt("releasesBehind", -1))
                    || AppPreferences.get(context).getInt(prefix(context) + "warned", 0) == latest) {
                return false;
            }
            AppPreferences.get(context).edit().putInt(prefix(context) + "warned", latest).apply();
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("建议先升级再反馈")
                    .setMessage("当前版本较旧，很多问题可能已在新版修复。请先到「高级 → 应用更新」升级。\n"
                            + summary(context))
                    .setNegativeButton("暂不反馈", (dialog, which) -> { })
                    .setPositiveButton("仍要反馈 / 上传", (dialog, which) -> proceed.run()).show();
            return true;
        } catch (Exception ignored) { return false; }
    }
}
