"use strict";

const REQUIRED_COOKIES = ["SID", "HSID", "SSID", "OSID", "APISID", "SAPISID"];
const OPTIONAL_COOKIES = ["__Secure-1PSIDTS"];

function shellTokens(input) {
  const tokens = [];
  let token = "";
  let state = "plain";
  let started = false;
  for (let i = 0; i < input.length; i++) {
    const char = input[i];
    if (state === "single") {
      if (char === "'") state = "plain";
      else token += char;
      started = true;
    } else if (state === "double") {
      if (char === '"') state = "plain";
      else if (char === "\\" && i + 1 < input.length) token += input[++i];
      else token += char;
      started = true;
    } else if (char === "'") {
      state = "single";
      started = true;
    } else if (char === '"') {
      state = "double";
      started = true;
    } else if (char === "\\" && input[i + 1] === "\n") {
      i++;
    } else if (char === "\\" && input[i + 1] === "\r" && input[i + 2] === "\n") {
      i += 2;
    } else if (char === "\\" && i + 1 < input.length) {
      token += input[++i];
      started = true;
    } else if (/\s/.test(char)) {
      if (started) {
        tokens.push(token);
        token = "";
        started = false;
      }
    } else {
      token += char;
      started = true;
    }
  }
  if (state !== "plain") throw new Error("The pasted cURL command has an unterminated quote.");
  if (started) tokens.push(token);
  return tokens;
}

function extractCookieHeader(curlText) {
  const tokens = shellTokens(curlText);
  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    let candidate = null;
    if (token === "-H" || token === "--header") candidate = tokens[++i];
    else if (token.startsWith("--header=")) candidate = token.substring(9);
    else if (token === "-b" || token === "--cookie") candidate = tokens[++i];
    else if (token.startsWith("--cookie=")) candidate = token.substring(9);

    if (!candidate) continue;
    const colon = candidate.indexOf(":");
    if (colon >= 0 && candidate.substring(0, colon).trim().toLowerCase() === "cookie") {
      return candidate.substring(colon + 1).trim();
    }
    if (token === "-b" || token === "--cookie" || token.startsWith("--cookie=")) {
      return candidate.trim();
    }
  }
  throw new Error("No Cookie header was found in the pasted cURL command.");
}

function parseRequiredCookies(curlText) {
  const header = extractCookieHeader(curlText);
  const available = new Map();
  for (const part of header.split(";")) {
    const equals = part.indexOf("=");
    if (equals <= 0) continue;
    const name = part.substring(0, equals).trim();
    const value = part.substring(equals + 1).trim();
    if (value) available.set(name, value);
  }
  const missing = REQUIRED_COOKIES.filter(name => !available.has(name));
  if (missing.length) throw new Error(`Missing required cookies: ${missing.join(", ")}`);

  const selected = {};
  for (const name of [...REQUIRED_COOKIES, ...OPTIONAL_COOKIES]) {
    if (available.has(name)) selected[name] = available.get(name);
  }
  return selected;
}

async function gzip(bytes) {
  if (typeof CompressionStream === "undefined") {
    throw new Error("This browser does not support local gzip compression. Use a current Firefox, Chrome, or Safari.");
  }
  const stream = new Blob([bytes]).stream().pipeThrough(new CompressionStream("gzip"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}

function base64Url(bytes) {
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function drawQr(payload, canvas) {
  const qr = qrcodegen.QrCode.encodeText(payload, qrcodegen.QrCode.Ecc.LOW);
  const border = 4;
  const scale = Math.max(2, Math.floor(720 / (qr.size + border * 2)));
  canvas.width = canvas.height = (qr.size + border * 2) * scale;
  const context = canvas.getContext("2d");
  context.fillStyle = "#fff";
  context.fillRect(0, 0, canvas.width, canvas.height);
  context.fillStyle = "#000";
  for (let y = 0; y < qr.size; y++) {
    for (let x = 0; x < qr.size; x++) {
      if (qr.getModule(x, y)) {
        context.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
      }
    }
  }
}

async function generate() {
  const input = document.getElementById("curl-input");
  const status = document.getElementById("status");
  const panel = document.getElementById("qr-panel");
  const canvas = document.getElementById("qr");
  status.className = "";
  panel.hidden = true;
  canvas.width = canvas.height = 0;
  let compressed = null;
  try {
    let cookies = parseRequiredCookies(input.value);
    const json = JSON.stringify({type: "gmessages-auth", version: 1, cookies});
    compressed = await gzip(new TextEncoder().encode(json));
    const payload = `GM1:${base64Url(compressed)}`;
    if (payload.length > 2953) throw new Error("Credential payload is too large for one QR code.");
    drawQr(payload, canvas);
    input.value = "";
    cookies = null;
    status.textContent = "QR ready. The pasted cURL text was cleared from the page.";
    status.className = "success";
    panel.hidden = false;
  } catch (error) {
    canvas.width = canvas.height = 0;
    status.textContent = error instanceof Error ? error.message : "Could not generate the credential QR.";
    status.className = "error";
  } finally {
    if (compressed) compressed.fill(0);
  }
}

function clearAll() {
  document.getElementById("curl-input").value = "";
  const canvas = document.getElementById("qr");
  canvas.width = canvas.height = 0;
  document.getElementById("qr-panel").hidden = true;
  const status = document.getElementById("status");
  status.textContent = "Cleared.";
  status.className = "";
}

if (typeof document !== "undefined") {
  document.getElementById("generate").addEventListener("click", generate);
  document.getElementById("clear").addEventListener("click", clearAll);
  window.addEventListener("pagehide", clearAll);
}

if (typeof module !== "undefined") {
  module.exports = {shellTokens, extractCookieHeader, parseRequiredCookies};
}
