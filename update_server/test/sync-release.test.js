"use strict";

const {test} = require("node:test");
const assert = require("node:assert");
const {
  isRetryableFetchError, isRetryableResponse, retryDelayMs, retryCount, fetchWithRetry,
  pickBetaRelease, betaChannelUsesPrerelease,
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
