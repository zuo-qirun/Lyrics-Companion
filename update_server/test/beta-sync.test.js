"use strict";

/**
 * `syncBeta()` 的端到端用例：它是测试版通道的核心，却因为只测了
 * `pickBetaRelease` / `betaChannelUsesPrerelease` 这类纯函数而漏掉过一个
 * ReferenceError（`release` 没有从 `firstUsableBeta()` 的返回值里解构出来），
 * 结果「有更新的 prerelease」这条路径一跑就抛错——恰好是测试版通道存在的意义。
 *
 * sync-release.js 在 require 时就解析输出目录（和 server.js 一样读 PUBLIC_DIR），
 * 所以必须在加载模块之前把目录指到临时路径。`node --test` 每个测试文件各起一个
 * 子进程，这里的 process.env 不会污染其他用例。
 */

const {test, beforeEach, after} = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const publicDir = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-beta-sync-"));
process.env.PUBLIC_DIR = publicDir;
process.env.GITHUB_TOKEN = "";

const {syncBeta} = require("../sync-release.js");

const APK_URL = "https://example.invalid/assets/lyrics-companion.apk";
const CHANGELOG_URL = "https://example.invalid/assets/CHANGELOG.md";
const MANIFEST_URL = "https://example.invalid/assets/release-update.json";

const betaApkPath = path.join(publicDir, "apk", "lyrics_companion_beta.apk");
const betaManifestPath = path.join(publicDir, "update-beta.json");
const betaChangelogPath = path.join(publicDir, "CHANGELOG-beta.md");

/** 造一个 release；assets 由用例显式给出，避免隐式依赖默认 APK 名字。 */
function makeRelease({id = 42, tag = "apk-300-abc1234", prerelease = true, draft = false,
  assets = []} = {}) {
  return {
    id,
    tag_name: tag,
    name: `Release ${tag}`,
    prerelease,
    draft,
    body: `notes for ${tag}`,
    published_at: "2026-09-28T00:00:00Z",
    created_at: "2026-09-28T00:00:00Z",
    target_commitish: "main",
    html_url: `https://example.invalid/releases/${id}`,
    assets,
  };
}

function apkAsset(name = "lyrics-companion-abc1234.apk", url = APK_URL) {
  return {name, size: 11, browser_download_url: url};
}

/** 只在给定 URL 上应答；任何没预期的请求都直接失败，避免用例悄悄走偏。 */
function installFetch(releases, files = {}) {
  global.fetch = async (url) => {
    const key = String(url);
    if (key.startsWith("https://api.github.com/repos/")) {
      return response(JSON.stringify(releases), "application/json");
    }
    if (!Object.prototype.hasOwnProperty.call(files, key)) {
      throw new Error(`unexpected fetch: ${key}`);
    }
    return response(files[key], "application/octet-stream");
  };
}

function response(body, contentType) {
  const buffer = Buffer.from(body, "utf8");
  return {
    ok: true,
    status: 200,
    headers: {get: () => contentType},
    json: async () => JSON.parse(buffer.toString("utf8")),
    text: async () => buffer.toString("utf8"),
    arrayBuffer: async () =>
      buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength),
  };
}

function readBetaManifest() {
  return JSON.parse(fs.readFileSync(betaManifestPath, "utf8"));
}

beforeEach(() => {
  fs.rmSync(publicDir, {recursive: true, force: true});
  fs.mkdirSync(publicDir, {recursive: true});
});

after(() => {
  fs.rmSync(publicDir, {recursive: true, force: true});
});

test("publishes the newest prerelease to its own manifest and APK", async () => {
  installFetch([makeRelease({
    assets: [apkAsset(), {name: "CHANGELOG.md", size: 5, browser_download_url: CHANGELOG_URL}],
  })], {[APK_URL]: "beta-apk-bytes", [CHANGELOG_URL]: "# beta changelog\n"});

  const result = await syncBeta(null);

  assert.ok(result, "syncBeta() must resolve when a usable prerelease exists");
  assert.equal(result.manifest.channel, "beta");
  assert.equal(result.manifest.versionCode, 300);
  assert.equal(result.manifest.betaSource, "beta");
  assert.equal(result.manifest.releaseTag, "apk-300-abc1234");

  const written = readBetaManifest();
  assert.equal(written.versionCode, 300);
  assert.equal(written.betaAvailable, true);
  assert.equal(written.betaSource, "beta");
  assert.equal(written.apkPath, "apk/lyrics_companion_beta.apk");
  assert.equal(written.changelogPath, "CHANGELOG-beta.md");

  const digest = crypto.createHash("sha256").update(fs.readFileSync(betaApkPath)).digest("hex");
  assert.equal(written.sha256, digest, "manifest sha256 must describe the downloaded APK");
  assert.equal(written.size, fs.statSync(betaApkPath).size);
  assert.equal(fs.readFileSync(betaChangelogPath, "utf8"), "# beta changelog\n");
});

test("keeps the stable channel's own files untouched", async () => {
  fs.writeFileSync(path.join(publicDir, "CHANGELOG.md"), "# stable changelog\n");
  fs.writeFileSync(path.join(publicDir, "update.json"), '{"channel":"stable","versionCode":900}\n');
  installFetch([makeRelease({assets: [apkAsset()]})], {[APK_URL]: "beta-apk-bytes"});

  await syncBeta(null);

  assert.equal(fs.readFileSync(path.join(publicDir, "CHANGELOG.md"), "utf8"), "# stable changelog\n");
  assert.equal(JSON.parse(fs.readFileSync(path.join(publicDir, "update.json"), "utf8")).versionCode,
    900);
  assert.equal(fs.existsSync(betaChangelogPath), false, "no CHANGELOG.md asset means no beta log");
});

test("skips an unusable prerelease and falls through to the next candidate", async () => {
  const broken = makeRelease({
    id: 1,
    tag: "apk-400-broken1",
    assets: [apkAsset("lyrics-companion-broken1.apk"),
      {name: "release-update.json", size: 9, browser_download_url: MANIFEST_URL}],
  });
  const good = makeRelease({
    id: 2,
    tag: "apk-350-good222",
    assets: [apkAsset("lyrics-companion-good222.apk",
      "https://example.invalid/assets/good222.apk")],
  });
  installFetch([broken, good], {
    [MANIFEST_URL]: "{ this is not json",
    "https://example.invalid/assets/good222.apk": "good-apk-bytes",
  });

  const result = await syncBeta(null);

  assert.ok(result, "a broken candidate must not abort the whole sync");
  assert.equal(result.release.tag_name, "apk-350-good222");
  assert.equal(readBetaManifest().releaseTag, "apk-350-good222");
});

test("serves the newer stable build instead of an older prerelease", async () => {
  installFetch([makeRelease({assets: [apkAsset()]})]);
  const stableManifest = {
    schemaVersion: 1,
    channel: "stable",
    versionCode: 900,
    versionName: "stable-900",
    apkPath: "apk/lyrics_companion.apk",
  };

  const result = await syncBeta({
    release: makeRelease({id: 9, tag: "apk-900-ffffff9", prerelease: false}),
    apkAsset: apkAsset("lyrics-companion-ffffff9.apk", "https://example.invalid/assets/stable.apk"),
    manifest: stableManifest,
  });

  assert.equal(result.manifest.betaSource, "stable");
  assert.equal(result.manifest.versionCode, 900);
  assert.equal(result.manifest.betaAvailable, true);
  const written = readBetaManifest();
  assert.equal(written.betaSource, "stable");
  assert.equal(written.versionCode, 900);
  assert.equal(fs.existsSync(betaApkPath), false, "the stable APK is not re-downloaded for beta");
});

test("answers an empty channel with an unavailable placeholder", async () => {
  installFetch([]);

  const result = await syncBeta(null);

  assert.equal(result, null);
  const written = readBetaManifest();
  assert.equal(written.channel, "beta");
  assert.equal(written.betaAvailable, false);
  assert.equal(written.versionCode, 0, "must not fall back to the stable template's version");
});
