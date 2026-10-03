"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
    normalizeRelease,
    filterHistory,
    releaseChannel,
    safeUrl,
    releaseNotes,
} = require("../public/site");

const stable = {
    channel: "stable",
    versionCode: 100,
    versionName: "stable-100",
    apkUrl: "/apk/stable.apk",
    downloadChannel: "server",
    changelog: ["正式版修复"],
};
const beta = {
    channel: "beta",
    betaAvailable: true,
    versionCode: 200,
    versionName: "beta-200",
    apkUrl: "/apk/beta.apk",
    downloadChannel: "github",
    changelog: ["测试版功能"],
};

test("stable and beta cards keep their releases and download sources separate", () => {
    const normal = normalizeRelease(stable, "stable");
    const preview = normalizeRelease(beta, "beta");
    assert.equal(normal.channel, "stable");
    assert.equal(normal.url, "/apk/stable.apk");
    assert.equal(normal.source, "官方镜像");
    assert.equal(preview.channel, "beta");
    assert.equal(preview.url, "/apk/beta.apk");
    assert.equal(preview.source, "GitHub");
    assert.deepEqual(normal.notes, ["正式版修复"]);
    assert.deepEqual(preview.notes, ["测试版功能"]);
    assert.throws(
        () => normalizeRelease(beta, "stable"),
        /Unexpected release channel/,
    );
});

test("a newer stable build in the beta endpoint is explicitly marked as a fallback", () => {
    const fallback = normalizeRelease(
        { ...stable, betaAvailable: true },
        "beta",
    );
    assert.equal(fallback.kind, "fallback");
    assert.equal(fallback.channel, "stable");
    assert.equal(fallback.url, stable.apkUrl);
    assert.equal(fallback.version, stable.versionName);
});

test("unavailable beta placeholders never expose stale stable downloads or notes", () => {
    const empty = normalizeRelease(
        {
            ...beta,
            betaAvailable: false,
            versionCode: 0,
            changelogText: "# 正式版更新日志",
            apkUrl: stable.apkUrl,
        },
        "beta",
    );
    assert.equal(empty.kind, "unavailable");
    assert.equal(empty.downloadable, false);
    assert.equal(empty.url, "");
    assert.equal(empty.page, "");
    assert.deepEqual(empty.notes, []);
});

test("a manifest without an APK cannot render an active download button", () => {
    assert.equal(
        normalizeRelease({ ...beta, apkUrl: undefined }, "beta").downloadable,
        false,
    );
    assert.equal(
        normalizeRelease({ ...beta, apkUrl: "javascript:alert(1)" }, "beta")
            .downloadable,
        false,
    );
    assert.equal(
        normalizeRelease({ ...stable, versionCode: 0 }, "stable").downloadable,
        false,
    );
});

test("archive filters separate channels, preserve old stable records and sort newest first", () => {
    const old = { versionCode: 50, versionName: "legacy" };
    const releases = [
        old,
        beta,
        stable,
        null,
        { channel: "unknown", versionCode: 300 },
    ];
    assert.deepEqual(filterHistory(releases, "stable"), [stable, old]);
    assert.deepEqual(filterHistory(releases, "beta"), [beta]);
    assert.deepEqual(filterHistory(releases, "all"), [beta, stable, old]);
    assert.equal(releaseChannel({ prerelease: true }), "beta");
    assert.equal(releases[0], old);
});

test("download links reject script protocols and accept mirror or HTTPS release links", () => {
    assert.equal(safeUrl("javascript:alert(1)"), "");
    assert.equal(safeUrl("data:text/html,hello"), "");
    assert.equal(safeUrl(null), "");
    assert.equal(
        safeUrl("/apk/beta.apk?sha256=abc"),
        "/apk/beta.apk?sha256=abc",
    );
    assert.equal(
        safeUrl("https://github.com/example.apk"),
        "https://github.com/example.apk",
    );
});

test("release notices survive extraction without leaking Markdown headings", () => {
    const notes = releaseNotes({
        changelogText:
            "# 更新日志\n\n## beta\n\n> ⚠️ 待实机验证\n\n- 新增功能\n",
    });
    assert.deepEqual(notes, ["> ⚠️ 待实机验证", "- 新增功能"]);
    assert.deepEqual(releaseNotes({ changelog: [null, "", "修复问题", 1] }), [
        "修复问题",
    ]);
});

test("invalid manifests and unknown channels fail rather than inventing a release", () => {
    assert.throws(() => normalizeRelease(null, "beta"), /Invalid release/);
    assert.throws(() => normalizeRelease([], "stable"), /Invalid release/);
    assert.throws(
        () => normalizeRelease({ ...stable, channel: "preview" }, "beta"),
        /Unexpected release channel/,
    );
});
