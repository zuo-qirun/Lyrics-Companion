package com.zuoqirun.lyricscompanion;

/**
 * 更新通道（正式版 / 测试版）的纯判定：通道名、清单地址，以及"不再提醒此版本"的取舍。
 *
 * <p>几个容易写错的组合都收在这里，且不碰任何 Android 类型，便于单测覆盖
 * （{@code UpdateChannelRulesTest}）：
 * <ul>
 *   <li>用户把渠道切回正式版后，本机版本号仍高于正式版最新版（测试版号通常更高）；</li>
 *   <li>被"不再提醒"的版本，自动检查不弹窗，但手动点「检查更新」必须能看到结果；</li>
 *   <li>{@code force} 清单不能被"不再提醒"吞掉。</li>
 * </ul>
 */
final class UpdateChannelRules {
    /** 正式版通道（默认）：只取 GitHub 的非 prerelease。 */
    static final String CHANNEL_STABLE = "stable";
    /** 测试版通道：取最新的 prerelease。 */
    static final String CHANNEL_BETA = "beta";

    private UpdateChannelRules() { }

    /** 只接受已知通道名；未知或空值一律回到正式版。 */
    static String normalize(String channel) {
        return CHANNEL_BETA.equals(channel) ? CHANNEL_BETA : CHANNEL_STABLE;
    }

    static boolean isBeta(String channel) {
        return CHANNEL_BETA.equals(normalize(channel));
    }

    /** 界面上显示的名字。 */
    static String displayName(String channel) {
        return isBeta(channel) ? "测试版" : "正式版";
    }

    /** 该通道对应的更新清单地址。 */
    static String manifestUrl(String channel, String stableUrl, String betaUrl) {
        return isBeta(channel) ? betaUrl : stableUrl;
    }

    /**
     * 自动检查时，是否因为这个版本已被跳过而不再弹窗。
     *
     * <p>手动点「检查更新」永远要看结果（{@code manual} 传 true），{@code force} 清单同理。
     *
     * @param skippedVersionCode 该通道上一次"不再提醒"记下的版本号，没有记录时传 {@code <= 0}。
     */
    static boolean skipPrompt(boolean manual, boolean force,
                              int remoteVersionCode, int skippedVersionCode) {
        if (manual || force) return false;
        if (remoteVersionCode <= 0 || skippedVersionCode <= 0) return false;
        return remoteVersionCode <= skippedVersionCode;
    }

    /** 已安装版本高于所选通道的最新版：切回正式版时会遇到，需要给一句说明而不是"已是最新"。 */
    static boolean localIsAhead(int localVersionCode, int remoteVersionCode) {
        return remoteVersionCode > 0 && localVersionCode > remoteVersionCode;
    }

    /**
     * 清单里是否真的带了一个可用版本。
     *
     * <p>服务端在没有测试版时会给出 `betaAvailable: false`、`versionCode: 0` 的占位清单；
     * 这种响应里 {@code hasUpdate()} 与 {@link #localIsAhead} 都是 false，如果直接当"已是最新"
     * 处理，用户开了测试版通道却只看到"已是最新版本"，看不出是通道里没有包（Codex review P2）。
     *
     * @param available 清单的 `available` 字段（缺省视为 true）。
     * @param betaAvailable 清单的 `betaAvailable` 字段（缺省视为 true）。
     * @param versionCode 清单里的版本号。
     */
    static boolean hasUsableVersion(boolean available, boolean betaAvailable, int versionCode) {
        return available && betaAvailable && versionCode > 0;
    }
}
