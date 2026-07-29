"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {publicBaseUrl} = require("../server-address");

test("configured public URL remains the canonical HTTPS domain", () => {
  assert.equal(publicBaseUrl({host: "127.0.0.1:8790"},
    "https://lyrics-companion.zuoqirun.top/"),
  "https://lyrics-companion.zuoqirun.top");
});

test("HTTPS proxy headers remove accidental port 80", () => {
  assert.equal(publicBaseUrl({host: "127.0.0.1:8790",
    "x-forwarded-proto": "https", "x-forwarded-host": "lyrics-companion.zuoqirun.top:80"}),
  "https://lyrics-companion.zuoqirun.top");
});

test("direct non-default server port is retained", () => {
  assert.equal(publicBaseUrl({host: "127.0.0.1:8790"}), "http://127.0.0.1:8790");
});
