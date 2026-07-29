"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const CLIENT_ID_PATTERN = /^[a-f0-9-]{16,64}$/i;

function normalizeClientId(value) {
  const id = String(value || "").trim();
  return CLIENT_ID_PATTERN.test(id) ? id.toLowerCase() : "";
}

function normalizeFeedback(value) {
  const source = value && typeof value === "object" ? value : {};
  return {
    message: String(source.message || "").trim().slice(0, 2000),
    contact: String(source.contact || "").trim().slice(0, 200),
    appVersion: String(source.appVersion || "").trim().slice(0, 80),
  };
}

class OnlineTracker {
  constructor(ttlMs = 120_000, maximumClients = 10_000) {
    this.ttlMs = Math.max(30_000, Number(ttlMs) || 120_000);
    this.maximumClients = Math.max(100, Number(maximumClients) || 10_000);
    this.clients = new Map();
  }

  heartbeat(clientId, now = Date.now()) {
    const id = normalizeClientId(clientId);
    if (!id) throw new Error("invalid client id");
    this.prune(now);
    if (!this.clients.has(id)) {
      while (this.clients.size >= this.maximumClients) {
        this.clients.delete(this.clients.keys().next().value);
      }
    } else {
      this.clients.delete(id);
    }
    this.clients.set(id, now);
    return this.clients.size;
  }

  count(now = Date.now()) {
    this.prune(now);
    return this.clients.size;
  }

  prune(now = Date.now()) {
    const oldest = now - this.ttlMs;
    for (const [id, seenAt] of this.clients) {
      if (seenAt < oldest) this.clients.delete(id);
    }
  }
}

class FeedbackStore {
  constructor(filePath, minimumIntervalMs = 60_000) {
    this.filePath = filePath;
    this.minimumIntervalMs = Math.max(1_000, Number(minimumIntervalMs) || 60_000);
    this.lastSubmission = new Map();
  }

  submit(clientId, value, now = Date.now(), rateKey = "") {
    const id = normalizeClientId(clientId);
    if (!id) throw new Error("invalid client id");
    const feedback = normalizeFeedback(value);
    if (feedback.message.length < 5) throw new Error("feedback is too short");
    const keys = [`client:${id}`];
    if (rateKey) keys.push(`source:${String(rateKey)}`);
    const previous = Math.max(...keys.map((key) => this.lastSubmission.get(key) || 0));
    if (now - previous < this.minimumIntervalMs) {
      const error = new Error("feedback rate limited");
      error.code = "RATE_LIMITED";
      throw error;
    }
    for (const key of keys) this.lastSubmission.set(key, now);
    this.pruneRates(now);
    const entry = {
      id: crypto.randomUUID(),
      createdAt: new Date(now).toISOString(),
      message: feedback.message,
      contact: feedback.contact,
      appVersion: feedback.appVersion,
    };
    fs.mkdirSync(path.dirname(this.filePath), {recursive: true});
    fs.appendFileSync(this.filePath, JSON.stringify(entry) + "\n", {encoding: "utf8", mode: 0o600});
    return entry;
  }

  pruneRates(now = Date.now()) {
    const oldest = now - this.minimumIntervalMs * 3;
    for (const [key, submittedAt] of this.lastSubmission) {
      if (submittedAt < oldest) this.lastSubmission.delete(key);
    }
    while (this.lastSubmission.size > 20_000) {
      this.lastSubmission.delete(this.lastSubmission.keys().next().value);
    }
  }
}

module.exports = {FeedbackStore, OnlineTracker, normalizeClientId, normalizeFeedback};
