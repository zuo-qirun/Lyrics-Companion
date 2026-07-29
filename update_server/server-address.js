"use strict";

function publicBaseUrl(headers, configuredBaseUrl = "") {
  const configured = String(configuredBaseUrl || "").trim();
  if (configured) return configured.replace(/\/+$/, "");

  const forwardedProtocol = String(headers["x-forwarded-proto"] || "")
    .split(",")[0].trim().toLowerCase();
  const protocol = forwardedProtocol === "https" ? "https" : "http";
  const forwardedHost = String(headers["x-forwarded-host"] || "").split(",")[0].trim();
  const host = forwardedHost || String(headers.host || "localhost").split(",")[0].trim();
  const origin = new URL(`${protocol}://${host}`);
  if ((protocol === "https" && (origin.port === "80" || origin.port === "443"))
      || (protocol === "http" && origin.port === "80")) {
    origin.port = "";
  }
  return origin.origin;
}

module.exports = {publicBaseUrl};
