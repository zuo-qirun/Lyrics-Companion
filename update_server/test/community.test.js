"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const {FeedbackStore, OnlineTracker, normalizeDiagnostic, normalizeFeedback} = require("../community");

const first = "11111111-1111-4111-8111-111111111111";
const second = "22222222-2222-4222-8222-222222222222";

test("online tracker deduplicates clients and expires stale heartbeats", () => {
  const tracker = new OnlineTracker(30_000);
  assert.equal(tracker.heartbeat(first, 100_000), 1);
  assert.equal(tracker.heartbeat(first, 105_000), 1);
  assert.equal(tracker.heartbeat(second, 110_000), 2);
  assert.equal(tracker.count(136_000), 1);
  assert.equal(tracker.count(141_000), 0);
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
