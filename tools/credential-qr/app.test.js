"use strict";

const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const {shellTokens, extractCookieHeader, parseRequiredCookies} = require("./app.js");

const cookieHeader = "SID=sid-value; HSID=h; SSID=s; OSID=o; APISID=a; SAPISID=sa; __Secure-1PSIDTS=optional; EXTRA=no";
const continuedCurl = "curl 'https://example.test' " + "\\" + "\n  -H 'accept: */*'";

assert.deepEqual(
  shellTokens(continuedCurl),
  ["curl", "https://example.test", "-H", "accept: */*"],
);
assert.equal(
  extractCookieHeader(`curl 'https://messages.google.com/web/config' -H 'Cookie: ${cookieHeader}'`),
  cookieHeader,
);
assert.equal(
  extractCookieHeader(`curl https://example.test --cookie "${cookieHeader}"`),
  cookieHeader,
);

const selected = parseRequiredCookies(`curl https://example.test -H 'cookie: ${cookieHeader}'`);
assert.equal(selected.SID, "sid-value");
assert.equal(selected["__Secure-1PSIDTS"], "optional");
assert.equal(Object.prototype.hasOwnProperty.call(selected, "EXTRA"), false);
assert.throws(
  () => parseRequiredCookies("curl https://example.test -H 'cookie: SID=x'"),
  /Missing required cookies/,
);

const qrContext = {};
vm.createContext(qrContext);
vm.runInContext(fs.readFileSync(`${__dirname}/vendor/qrcodegen.js`, "utf8"), qrContext);
const qr = qrContext.qrcodegen.QrCode.encodeText("GM1:test-payload", qrContext.qrcodegen.QrCode.Ecc.LOW);
assert.ok(qr.size >= 21);

console.log("credential QR parser tests passed");
