"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const {FeedbackStore, DiagnosticStore, OnlineTracker, normalizeDiagnostic, normalizeFeedback} = require("../community");

const first = "11111111-1111-4111-8111-111111111111";
const second = "22222222-2222-4222-8222-222222222222";

test("version status keeps channel and unknown state and computes package match", () => {
  const value = {latestKnownVersion: "20261006-a", latestKnownVersionCode: 1791234567,
    versionStatusKnown: true, outdated: true, updateChannel: "beta", daysBehind: 31,
    releasesBehind: 3, actualPackage: "com.android.gallery3d", packageMatches: true};
  for (const normalize of [normalizeFeedback, normalizeDiagnostic]) {
    const status = normalize(value);
    assert.equal(status.outdated, true);
    assert.equal(status.updateChannel, "beta");
    assert.equal(status.packageMatches, false);
    assert.equal(status.daysBehind, 31);
    assert.equal(status.releasesBehind, 3);
    assert.equal(normalize({...value, versionStatusKnown: false}).outdated, null);
    assert.equal(normalize({...value, outdated: "false"}).outdated, null);
    assert.equal(normalize({...value, daysBehind: Infinity}).daysBehind, -1);
    assert.equal(normalize({...value, actualPackage: "com.zuoqirun.lyricscompanion"}).packageMatches, true);
  }
});

test("online tracker deduplicates clients and expires stale heartbeats", () => {
  const tracker = new OnlineTracker(30_000);
  assert.equal(tracker.heartbeat(first, 100_000), 1);
  assert.equal(tracker.heartbeat(first, 105_000), 1);
  assert.equal(tracker.heartbeat(second, 110_000), 2);
  assert.equal(tracker.count(136_000), 1);
  assert.equal(tracker.count(141_000), 0);
});

test("version flags survive persistence and public feedback and diagnostic lists", () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-version-status-"));
  try {
    const version = {latestKnownVersion: "20261006-beta", latestKnownVersionCode: 1791234567,
      versionStatusKnown: true, outdated: true, updateChannel: "beta", daysBehind: 31,
      releasesBehind: 3, actualPackage: "com.android.gallery3d"};
    const feedback = new FeedbackStore(path.join(directory, "feedback.jsonl"), 60000);
    const submitted = feedback.submit(first, {message: "version status feedback", ...version}, 100000);
    assert.equal(submitted.outdated, true);
    const listed = feedback.list()[0];
    assert.equal(listed.outdated, true);
    assert.equal(listed.packageMatches, false);
    assert.equal(listed.latestKnownVersion, "20261006-beta");
    assert.equal(listed.replyTokenHash, undefined);
    const diagnostics = new DiagnosticStore(path.join(directory, "diagnostics.jsonl"), 60000);
    diagnostics.submit(first, {summary: "version status snapshot", details: "details", ...version}, 100000);
    for (const summary of [false, true]) {
      const report = diagnostics.list(20, {summary})[0];
      assert.equal(report.outdated, true);
      assert.equal(report.packageMatches, false);
      assert.equal(report.updateChannel, "beta");
    }
  } finally {
    fs.rmSync(directory, {recursive: true, force: true});
  }
});

test("feedback is bounded, persisted and rate limited", () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-feedback-"));
  const file = path.join(directory, "feedback.jsonl");
  try {
    const store = new FeedbackStore(file, 60_000);
    const entry = store.submit(first, {message: `  ${"好".repeat(2100)}  `,
      contact: `a${"b".repeat(250)}`, appVersion: "1.2.3"}, 100_000, "127.0.0.1");
    assert.equal(entry.message.length, 2000);
    assert.equal(entry.contact.length, 200);
    assert.equal(JSON.parse(fs.readFileSync(file, "utf8")).id, entry.id);
    assert.throws(() => store.submit(first, {message: "第二次反馈"},
      120_000, "127.0.0.1"), /rate limited/);
  } finally {
    fs.rmSync(directory, {recursive: true, force: true});
  }
});

test("feedback normalization does not retain unrelated fields", () => {
  assert.deepEqual(normalizeFeedback({message: " 建议内容 ", contact: " test@example.com ",
    appVersion: "v1", hardwareId: "should-not-be-kept"}),
  {message: "建议内容", contact: "test@example.com", appVersion: "v1"});
});

test("diagnostic normalization keeps a large bounded report and drops unrelated fields", () => {
  const details = "x".repeat(1_300_000);
  const normalized = normalizeDiagnostic({kind: "crash", summary: " failure ", details,
    appVersion: "v1", adminToken: "must-not-be-kept"});
  assert.equal(normalized.kind, "crash");
  assert.equal(normalized.summary, "failure");
  assert.equal(normalized.details.length, 1_200_000);
  assert.equal(normalized.adminToken, undefined);
});

test("diagnostic normalization keeps only a valid feedback association", () => {
  const valid = normalizeDiagnostic({feedbackId: first});
  assert.equal(valid.feedbackId, first);
  assert.equal(normalizeDiagnostic({feedbackId: "not-a-feedback-id"}).feedbackId, "");
});
