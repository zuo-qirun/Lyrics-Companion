"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const net = require("net");
const os = require("os");
const path = require("path");
const {spawn} = require("child_process");

function availablePort() {
  return new Promise((resolve, reject) => {
    const probe = net.createServer();
    probe.once("error", reject);
    probe.listen(0, "127.0.0.1", () => {
      const port = probe.address().port;
      probe.close((error) => error ? reject(error) : resolve(port));
    });
  });
}

function waitUntilListening(child) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("server start timed out")), 5_000);
    child.once("error", (error) => { clearTimeout(timeout); reject(error); });
    child.stdout.on("data", (chunk) => {
      if (String(chunk).includes("update server listening")) {
        clearTimeout(timeout);
        resolve();
      }
    });
    child.once("exit", (code) => {
      clearTimeout(timeout);
      reject(new Error(`server exited early with ${code}`));
    });
  });
}

test("HTTP server accepts heartbeats and persists bounded feedback", async () => {
  const port = await availablePort();
  const stateDir = fs.mkdtempSync(path.join(os.tmpdir(), "lyrics-community-server-"));
  const serverDir = path.resolve(__dirname, "..");
  const child = spawn(process.execPath, ["server.js"], {
    cwd: serverDir,
    env: {...process.env, HOST: "127.0.0.1", PORT: String(port), AUTO_SYNC: "0",
      STATE_DIR: stateDir, ADMIN_TOKEN: "test-admin-token"},
    stdio: ["ignore", "pipe", "pipe"],
  });
  try {
    await waitUntilListening(child);
    const post = (pathname, body) => fetch(`http://127.0.0.1:${port}${pathname}`, {
      method: "POST", headers: {"content-type": "application/json"},
      body: JSON.stringify(body),
    });
    const first = await post("/api/online/heartbeat",
      {clientId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", appVersion: "test"});
    assert.equal(first.status, 200);
    assert.equal((await first.json()).online, 1);
    const second = await post("/api/online/heartbeat",
      {clientId: "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", appVersion: "test"});
    assert.equal((await second.json()).online, 2);
    const online = await fetch(`http://127.0.0.1:${port}/api/online`);
    assert.equal((await online.json()).online, 2);

    const update = await fetch(`http://127.0.0.1:${port}/update.json`);
    const updateManifest = await update.json();
    assert.match(updateManifest.historyUrl, /\/versions\.json$/);

    const feedback = await post("/api/feedback", {
      clientId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      appVersion: "test", message: "本地接口集成测试反馈", contact: "",
    });
    assert.equal(feedback.status, 201);
    const ticket = await feedback.json();
    assert.equal(ticket.ok, true);
    assert.ok(ticket.replyToken);
    const stored = fs.readFileSync(path.join(stateDir, "feedback.jsonl"), "utf8").trim();
    assert.equal(JSON.parse(stored).message, "本地接口集成测试反馈");
    assert.notEqual(JSON.parse(stored).replyTokenHash, ticket.replyToken);

    const adminHeaders = {authorization: "Bearer test-admin-token", "content-type": "application/json"};
    const inbox = await fetch(`http://127.0.0.1:${port}/api/admin/feedback`, {headers: adminHeaders});
    assert.equal(inbox.status, 200);
    assert.equal((await inbox.json()).feedback.length, 1);
    const reply = await fetch(`http://127.0.0.1:${port}/api/admin/feedback/${ticket.id}/replies`, {
      method: "POST", headers: adminHeaders, body: JSON.stringify({message: "已收到，正在排查。"}),
    });
    assert.equal(reply.status, 201);
    const replies = await post("/api/feedback/replies", {tickets: [{id: ticket.id, token: ticket.replyToken}]});
    assert.equal((await replies.json()).replies[0].message, "已收到，正在排查。");

    const largeDetails = `stack trace\n${"x".repeat(200_000)}`;
    const diagnostic = await post("/api/diagnostics/crash", {clientId: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
      appVersion: "test", summary: "IllegalStateException", details: largeDetails});
    assert.equal(diagnostic.status, 201);
    const diagnostics = await fetch(`http://127.0.0.1:${port}/api/admin/diagnostics`, {headers: adminHeaders});
    const diagnosticItems = (await diagnostics.json()).diagnostics;
    assert.equal(diagnosticItems[0].kind, "crash");
    assert.equal(diagnosticItems[0].details.length, largeDetails.length);
  } finally {
    if (child.exitCode === null) {
      child.kill();
      await new Promise((resolve) => child.once("exit", resolve));
    }
    fs.rmSync(stateDir, {recursive: true, force: true});
  }
});
