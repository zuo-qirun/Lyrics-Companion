"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {compileAssetPattern, parseHistoryLimit, safeSegment} = require("../release-sync-config");

test("matches only Lyrics Companion release APKs by default", () => {
  const pattern = compileAssetPattern();
  assert.equal(pattern.test("lyrics-companion-abcdef0.apk"), true);
  assert.equal(pattern.test("app-debug.apk"), false);
});

test("normalizes history limits", () => {
  assert.equal(parseHistoryLimit("12"), 12);
  assert.equal(parseHistoryLimit("-4"), 0);
  assert.equal(parseHistoryLimit("bad", 20), 20);
});

test("creates safe history file segments", () => {
  assert.equal(safeSegment("apk/123 bad"), "apk-123-bad");
  assert.equal(safeSegment("..", "release"), "release");
});
