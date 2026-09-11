# Offline credential QR helper

Open `index.html` directly in a current private Firefox, Chrome, or Safari
window. No server or build step is required.

The page accepts only a DevTools **Copy as cURL** command for the Google
Messages `/web/config` request. It extracts the six required cookies and the
one supported optional cookie, compresses a versioned payload locally, and
renders a QR using the vendored Nayuki QR Code generator. Its Content Security
Policy blocks network connections and form submissions.

The pasted command is cleared after generation. The QR still contains account
credentials, so scan it immediately, click **Clear**, and close the private
window. Nothing is written to local storage or the clipboard.

Run the parser tests with:

```bash
node app.test.js
```

Vendored dependency:

- Nayuki QR Code generator v1.8.0, MIT License
- Official release asset: `qrcodegen-v1.8.0-es6.js`
- SHA-256: `6a1116192ed1dd67fa1bf31e77f5817103d71c23bbac24c382e698b7668bdd01`
