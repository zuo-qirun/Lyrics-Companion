"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const CLIENT_ID_PATTERN = /^[a-f0-9-]{16,64}$/i;
const TICKET_LIMIT = 20;
const DIAGNOSTIC_DETAILS_LIMIT = 1_200_000;

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

function normalizeDiagnostic(value) {
  const source = value && typeof value === "object" ? value : {};
  return {
    kind: source.kind === "crash" ? "crash" : "snapshot",
    summary: String(source.summary || "").trim().slice(0, 500),
    details: String(source.details || "").trim().slice(0, DIAGNOSTIC_DETAILS_LIMIT),
    appVersion: String(source.appVersion || "").trim().slice(0, 80),
  };
}

function appendJsonLine(filePath, entry) {
  fs.mkdirSync(path.dirname(filePath), {recursive: true});
  fs.appendFileSync(filePath, JSON.stringify(entry) + "\n", {encoding: "utf8", mode: 0o600});
}

function readJsonLines(filePath) {
  if (!fs.existsSync(filePath)) return [];
  return fs.readFileSync(filePath, "utf8").split(/\r?\n/).filter(Boolean).flatMap((line) => {
    try { return [JSON.parse(line)]; } catch (_error) { return []; }
  });
}

function tokenHash(value) {
  return crypto.createHash("sha256").update(String(value || "")).digest("hex");
}

function sameToken(expectedHash, token) {
  const expected = Buffer.from(String(expectedHash || ""), "hex");
  const actual = Buffer.from(tokenHash(token), "hex");
  return expected.length === actual.length && crypto.timingSafeEqual(expected, actual);
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
      while (this.clients.size >= this.maximumClients) this.clients.delete(this.clients.keys().next().value);
    } else this.clients.delete(id);
    this.clients.set(id, now);
    return this.clients.size;
  }

  count(now = Date.now()) { this.prune(now); return this.clients.size; }

  prune(now = Date.now()) {
    const oldest = now - this.ttlMs;
    for (const [id, seenAt] of this.clients) if (seenAt < oldest) this.clients.delete(id);
  }
}

class RateLimitedStore {
  constructor(filePath, minimumIntervalMs = 60_000) {
    this.filePath = filePath;
    this.minimumIntervalMs = Math.max(1_000, Number(minimumIntervalMs) || 60_000);
    this.lastSubmission = new Map();
  }

  checkRate(clientId, now, rateKey) {
    const id = normalizeClientId(clientId);
    if (!id) throw new Error("invalid client id");
    const keys = [`client:${id}`];
    if (rateKey) keys.push(`source:${String(rateKey)}`);
    const previous = Math.max(...keys.map((key) => this.lastSubmission.get(key) || 0));
    if (now - previous < this.minimumIntervalMs) {
      const error = new Error("submission rate limited");
      error.code = "RATE_LIMITED";
      throw error;
    }
    for (const key of keys) this.lastSubmission.set(key, now);
    const oldest = now - this.minimumIntervalMs * 3;
    for (const [key, submittedAt] of this.lastSubmission) if (submittedAt < oldest) this.lastSubmission.delete(key);
    return id;
  }
}

class FeedbackStore extends RateLimitedStore {
  constructor(filePath, minimumIntervalMs = 60_000, repliesPath = "") {
    super(filePath, minimumIntervalMs);
    this.repliesPath = repliesPath || filePath.replace(/\.jsonl$/i, "-replies.jsonl");
  }

  submit(clientId, value, now = Date.now(), rateKey = "") {
    this.checkRate(clientId, now, rateKey);
    const feedback = normalizeFeedback(value);
    if (feedback.message.length < 5) throw new Error("feedback is too short");
    const replyToken = crypto.randomBytes(24).toString("base64url");
    const entry = {
      id: crypto.randomUUID(), createdAt: new Date(now).toISOString(), ...feedback,
      replyTokenHash: tokenHash(replyToken),
    };
    appendJsonLine(this.filePath, entry);
    return {...this.publicFeedback(entry), replyToken};
  }

  list(limit = 20) {
    const replies = this.repliesByFeedback();
    return readJsonLines(this.filePath).slice(-Math.max(1, Math.min(500, Number(limit) || 100))).reverse()
      .map((entry) => ({...this.publicFeedback(entry), replies: replies.get(entry.id) || []}));
  }

  addReply(feedbackId, message, now = Date.now()) {
    const target = readJsonLines(this.filePath).find((entry) => entry.id === feedbackId);
    if (!target) { const error = new Error("feedback not found"); error.code = "NOT_FOUND"; throw error; }
    const text = String(message || "").trim().slice(0, 4000);
    if (text.length < 1) throw new Error("reply is too short");
    const reply = {id: crypto.randomUUID(), feedbackId, createdAt: new Date(now).toISOString(), message: text};
    appendJsonLine(this.repliesPath, reply);
    return reply;
  }

  repliesForTickets(tickets) {
    const requested = Array.isArray(tickets) ? tickets.slice(0, TICKET_LIMIT) : [];
    const entries = new Map(readJsonLines(this.filePath).map((entry) => [entry.id, entry]));
    const allowed = new Set();
    for (const ticket of requested) {
      const entry = entries.get(String(ticket && ticket.id || ""));
      if (entry && sameToken(entry.replyTokenHash, ticket.token)) allowed.add(entry.id);
    }
    return readJsonLines(this.repliesPath).filter((reply) => allowed.has(reply.feedbackId));
  }

  repliesByFeedback() {
    const result = new Map();
    for (const reply of readJsonLines(this.repliesPath)) {
      if (!result.has(reply.feedbackId)) result.set(reply.feedbackId, []);
      result.get(reply.feedbackId).push(reply);
    }
    return result;
  }

  publicFeedback(entry) {
    return {id: entry.id, createdAt: entry.createdAt, message: entry.message,
      contact: entry.contact, appVersion: entry.appVersion};
  }
}

class DiagnosticStore extends RateLimitedStore {
  submit(clientId, value, now = Date.now(), rateKey = "") {
    this.checkRate(clientId, now, rateKey);
    const report = normalizeDiagnostic(value);
    if (!report.summary && !report.details) throw new Error("diagnostic is empty");
    const entry = {id: crypto.randomUUID(), createdAt: new Date(now).toISOString(), ...report};
    appendJsonLine(this.filePath, entry);
    return entry;
  }

  list(limit = 100) {
    return readJsonLines(this.filePath).slice(
      -Math.max(1, Math.min(50, Number(limit) || 20))).reverse();
  }
}

module.exports = {FeedbackStore, DiagnosticStore, OnlineTracker, normalizeClientId, normalizeFeedback,
  normalizeDiagnostic};
