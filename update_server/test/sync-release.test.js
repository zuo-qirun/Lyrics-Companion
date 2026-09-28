"use strict";

const {test} = require("node:test");
const assert = require("node:assert");
const {
  isRetryableFetchError, isRetryableResponse, retryDelayMs, retryCount, fetchWithRetry,
  pickBetaRelease, betaChannelUsesPrerelease, hasBetaApk, authFailure,
  latestStableRelease, assetApiUrl,
} = require("../sync-release.js");

function fetchError(code, message) {
  const error = new TypeError("fetch failed");
  error.cause = {code, message: message || String(code)};
  return error;
}

test("classifies retryable connection errors", () => {
  for (const code of ["ECONNRESET", "ECONNREFUSED", "ETIMEDOUT", "ENOTFOUND", "EAI_AGAIN", "UND_ERR_CONNECT_TIMEOUT"]) {
    assert.ok(isRetryableFetchError(fetchError(code)), `expected retryable: ${code}`);
  }
  const tls = new TypeError("fetch failed");
  tls.cause = {message: "Client network socket disconnected before secure TLS connection was established"};
  assert.ok(isRetryableFetchError(tls), "expected retryable: TLS disconnect message");
});

test("does not retry non-connection errors", () => {
  assert.equal(isRetryableFetchError(new TypeError("boom")), false);
  assert.equal(isRetryableFetchError(new Error("HTTP 404: https://example.com")), false);
  assert.equal(isRetryableFetchError(fetchError("SOMETHING_ELSE")), false);
});

test("classifies transient HTTP responses", () => {
  for (const status of [408, 425, 429, 500, 502, 503, 504]) {
    assert.equal(isRetryableResponse({status}), true, `expected retryable: ${status}`);
  }
  assert.equal(isRetryableResponse({status: 404}), false);
  assert.equal(isRetryableResponse({status: 200}), false);
});

test("retries a transient HTTP response and cancels its body", async () => {
  const previousRetries = process.env.SYNC_MAX_RETRIES;
  const previousBaseDelay = process.env.SYNC_RETRY_BASE_DELAY_MS;
  const previousMaxDelay = process.env.SYNC_RETRY_MAX_DELAY_MS;
  process.env.SYNC_MAX_RETRIES = "1";
  process.env.SYNC_RETRY_BASE_DELAY_MS = "0";
  process.env.SYNC_RETRY_MAX_DELAY_MS = "0";
  let calls = 0;
  let cancelled = false;
  const transient = {status: 502, body: {cancel: async () => { cancelled = true; }}};
  const success = {status: 200};
  try {
    const result = await fetchWithRetry("https://example.invalid", {}, async () => {
      calls++;
      return calls === 1 ? transient : success;
    });
    assert.equal(result, success);
    assert.equal(calls, 2);
    assert.equal(cancelled, true);
  } finally {
    if (previousRetries === undefined) delete process.env.SYNC_MAX_RETRIES;
    else process.env.SYNC_MAX_RETRIES = previousRetries;
    if (previousBaseDelay === undefined) delete process.env.SYNC_RETRY_BASE_DELAY_MS;
    else process.env.SYNC_RETRY_BASE_DELAY_MS = previousBaseDelay;
    if (previousMaxDelay === undefined) delete process.env.SYNC_RETRY_MAX_DELAY_MS;
    else process.env.SYNC_RETRY_MAX_DELAY_MS = previousMaxDelay;
  }
});

test("retry delay backs off exponentially and caps", () => {
  process.env.SYNC_RETRY_BASE_DELAY_MS = "1000";
  process.env.SYNC_RETRY_MAX_DELAY_MS = "8000";
  try {
    assert.equal(retryDelayMs(0), 1000);
    assert.equal(retryDelayMs(1), 2000);
    assert.equal(retryDelayMs(2), 4000);
    assert.equal(retryDelayMs(3), 8000);
    assert.equal(retryDelayMs(4), 8000);
  } finally {
    delete process.env.SYNC_RETRY_BASE_DELAY_MS;
    delete process.env.SYNC_RETRY_MAX_DELAY_MS;
  }
});

test("allows an explicit zero retry delay", () => {
  process.env.SYNC_RETRY_BASE_DELAY_MS = "0";
  process.env.SYNC_RETRY_MAX_DELAY_MS = "0";
  try {
    assert.equal(retryDelayMs(0), 0);
  } finally {
    delete process.env.SYNC_RETRY_BASE_DELAY_MS;
    delete process.env.SYNC_RETRY_MAX_DELAY_MS;
  }
});

test("retry count honours SYNC_MAX_RETRIES", () => {
  process.env.SYNC_MAX_RETRIES = "5";
  try {
    assert.equal(retryCount(), 5);
  } finally {
    delete process.env.SYNC_MAX_RETRIES;
  }
  assert.equal(retryCount(), 3);
});

test("picks the newest prerelease as the beta build", () => {
  const releases = [
    {tag_name: "apk-300-aaaaaaa", prerelease: false, draft: false},
    {tag_name: "apk-299-bbbbbbb", prerelease: true, draft: false},
    {tag_name: "apk-298-ccccccc", prerelease: true, draft: false},
  ];
  assert.equal(pickBetaRelease(releases).tag_name, "apk-299-bbbbbbb");
});

test("skips drafts and stable-only releases when looking for beta", () => {
  assert.equal(pickBetaRelease([{tag_name: "apk-300", prerelease: true, draft: true}]), null);
  assert.equal(pickBetaRelease([{tag_name: "apk-300", prerelease: false, draft: false}]), null);
  assert.equal(pickBetaRelease([{tag_name: "apk-300", draft: false}]), null);
  assert.equal(pickBetaRelease([]), null);
  assert.equal(pickBetaRelease(null), null);
  assert.equal(pickBetaRelease(undefined), null);
});

test("the beta channel serves a newer stable release instead of the older prerelease", () => {
  // 正式版 300 在测试版 299 之后发布：测试版端点必须给 300，否则测试版用户永远收不到它。
  assert.equal(betaChannelUsesPrerelease({versionCode: 300}, {versionCode: 299}), false);
  // 测试版更高：用测试版。
  assert.equal(betaChannelUsesPrerelease({versionCode: 300}, {versionCode: 301}), true);
  // 版本号相同（几乎不可能，versionCode 是时间戳）：不动正式版。
  assert.equal(betaChannelUsesPrerelease({versionCode: 300}, {versionCode: 300}), false);
  // 没有正式版清单时只有测试版可用。
  assert.equal(betaChannelUsesPrerelease(null, {versionCode: 301}), true);
  assert.equal(betaChannelUsesPrerelease({versionCode: 300}, null), false);
  assert.equal(betaChannelUsesPrerelease({versionCode: 300}, {versionCode: 0}), false);
  assert.equal(betaChannelUsesPrerelease(null, null), false);
});

test("skips prereleases without a matching APK", () => {
  assert.equal(hasBetaApk({assets: [{name: "lyrics-companion-abc1234.apk"}]}), true);
  assert.equal(hasBetaApk({assets: [
    {name: "release-update.json"}, {name: "CHANGELOG.md"}]}), false);
  assert.equal(hasBetaApk({assets: []}), false);
  assert.equal(hasBetaApk({}), false);
  assert.equal(hasBetaApk(null), false);

  // 最新那个测试版是手工发的、没挂 APK：要退到下一个可用的，而不是让整轮同步抛错。
  const releases = [
    {tag_name: "apk-301-manual", prerelease: true, draft: false, assets: [
      {name: "release-update.json"}]},
    {tag_name: "apk-300-bbbbbbb", prerelease: true, draft: false, assets: [
      {name: "lyrics-companion-bbbbbbb.apk"}, {name: "release-update.json"}]},
  ];
  assert.equal(pickBetaRelease(releases, hasBetaApk).tag_name, "apk-300-bbbbbbb");
  // 全不可用就当作"没有测试版"，交给正式版兜底。
  assert.equal(pickBetaRelease([releases[0]], hasBetaApk), null);
});

test("only a rejected token falls back to anonymous requests", () => {
  assert.equal(authFailure(401, ""), true);
  assert.equal(authFailure(403, "Bad credentials"), true);
  assert.equal(authFailure(403, "{\"message\":\"Invalid token\"}"), true);
  assert.equal(authFailure(403, "token expired"), true);
  // 配额用尽不是"token 失效"，不该退化成匿名再打一遍。
  assert.equal(authFailure(403, "API rate limit exceeded for 1.2.3.4"), false);
  assert.equal(authFailure(403, ""), false);
  assert.equal(authFailure(404, "Not Found"), false);
  assert.equal(authFailure(0, "boom"), false);
});

test("the stable release is the newest non-prerelease in one shared list", () => {
  // /releases 按创建时间倒序：最新正式版就是第一条非 prerelease 非 draft。
  assert.equal(latestStableRelease([
    {tag_name: "apk-301-beta", prerelease: true, draft: false},
    {tag_name: "apk-300-stable", prerelease: false, draft: false},
    {tag_name: "apk-299-stable", prerelease: false, draft: false},
  ]).tag_name, "apk-300-stable");
  assert.equal(latestStableRelease([
    {tag_name: "apk-301-stable", prerelease: false, draft: true},
    {tag_name: "apk-300-stable", prerelease: false, draft: false},
  ]).tag_name, "apk-300-stable");
  assert.equal(latestStableRelease([{tag_name: "apk-301-beta", prerelease: true}]), null);
  assert.equal(latestStableRelease([]), null);
  assert.equal(latestStableRelease(null), null);
});

test("builds the API asset URL used as the download fallback", () => {
  assert.equal(assetApiUrl("zuo-qirun/Lyrics-Companion", {id: 594270607}),
    "https://api.github.com/repos/zuo-qirun/Lyrics-Companion/releases/assets/594270607");
  assert.equal(assetApiUrl("zuo-qirun/Lyrics-Companion", {}), "");
  assert.equal(assetApiUrl("zuo-qirun/Lyrics-Companion", null), "");
  assert.equal(assetApiUrl("zuo-qirun/Lyrics-Companion", {id: 0}), "");
});
