# Political SMS Filter

An Android proof-of-concept that leaves Google Messages as the default SMS/RCS
app, makes its own notifications silent, and selectively replaces allowed
notifications. Messages containing `Stop2End` (case-insensitive) are suppressed.

This repository implements development milestones 1–4. It deliberately does
not yet connect the Android listener to libgm or delete messages automatically.

## Status

- **Milestone 1 — implemented:** listen only to Google Messages, cancel a
  parseable conversation notification, post an alerting replacement, and carry
  forward the original conversation `PendingIntent`.
- **Milestone 2 — implemented:** messages containing `Stop2End`, in any case,
  have their Google notification canceled with no replacement.
- **Milestone 3 — implemented:** the standalone Go proof can pair through
  imported Google cookies, connect, list recent messages, locate one unique
  exact incoming match, explicitly delete a known message ID, save refreshed
  auth, and disconnect.
- **Milestone 4 — implemented, awaiting device verification:** a minimal Go
  wrapper is compiled into an Android AAR. The app can import an existing
  paired session, encrypt it with Android Keystore, fetch recent incoming
  messages, and delete one explicitly selected message after confirmation.
- **Milestones 5–6 — not started.** The notification listener is not connected
  to deletion, and there is no QR credential importer yet.

The project is intended for personal/sideloaded use. libgm uses an unofficial
protocol that can change without notice.

## Repository layout

```text
app/            Android notification replacement and filter proof
libgm-proof/    Independent desktop Go/libgm command-line proof
libgm-android/  Minimal Go Mobile wrapper and reproducible AAR build script
docs/           Manual device verification checklists
```

## Build and test Android

Requirements:

- Android Studio Quail 4 or compatible
- Android SDK Platform 37 and Build Tools 36.0.0
- JDK 17 or newer (Android Studio's bundled JDK works)
- Android NDK 28.2.13676358 only when rebuilding the checked-in Go AAR
- Go 1.26 or newer only when rebuilding the checked-in Go AAR

From the repository root on macOS:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it from Android Studio or with:

```bash
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch:

1. Allow this app to post notifications.
2. Tap **Grant notification access** and enable Political SMS Filter.
3. Open the Google Messages notification settings from the app.
4. Leave Google Messages notifications enabled, but set all relevant incoming
   message channels to no sound and no vibration.
5. Tap **Test replacement notification** and confirm this app's `Messages`
   channel produces the desired sound/vibration.

Do not disable Google Messages notifications entirely: the listener needs the
silent notification as its event signal.

See [docs/device-verification.md](docs/device-verification.md) for the milestone
1–2 acceptance test.

## Verify the Android libgm bridge

The checked-in `app/libs/libgmbridge.aar` contains arm64 device and x86_64
emulator libraries. To regenerate it from the pinned Go source:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./libgm-android/build-android.sh
```

Milestone 4 intentionally uses a manual document import rather than putting
credentials in app source or build files. Transfer a copy of the paired
`libgm-proof/session.json` to the phone, import it from the app, then remove the
unencrypted transferred copy. The app validates it and stores only an
AES-GCM-encrypted copy backed by an app-only Android Keystore key. Every
connection saves refreshed auth before disconnecting.

See [docs/milestone-4-device-verification.md](docs/milestone-4-device-verification.md)
for the conservative one-message deletion test. The notification listener does
not invoke this bridge in milestone 4.

## Build and test the libgm proof

The Go module pins `go.mau.fi/mautrix-gmessages` to `v0.2605.0`. The tagged
library requires Go 1.25 or newer.

```bash
cd libgm-proof
go test ./...
go build -o gmproof ./cmd/gmproof
./gmproof help
```

### Pair

Use a private Firefox window to sign into the Google Messages `/web/config`
page, then obtain the request cookies through DevTools. Milestone 6 will provide
the local cURL-to-QR helper; for this isolated proof, put only the required
cookie values in a local `cookies.json`:

```json
{
  "SID": "...",
  "HSID": "...",
  "SSID": "...",
  "OSID": "...",
  "APISID": "...",
  "SAPISID": "...",
  "__Secure-1PSIDTS": "..."
}
```

`__Secure-1PSIDTS` is optional. The other six keys are required. Never commit
this file, paste it into an issue, or send it to anyone.

```bash
chmod 600 cookies.json
./gmproof pair --cookies cookies.json --session session.json
```

The command displays an emoji. Approve that emoji in Google Messages on the
phone. The resulting `session.json` is written atomically with mode `0600` and
is git-ignored.

### List, identify, and delete one known message

List IDs and timestamps without displaying content:

```bash
./gmproof list --session session.json
```

For a one-time inspection that also displays names and message bodies:

```bash
./gmproof list --session session.json --show-content
```

Prefer an ignored local file over putting sensitive text into shell history:

```bash
printf '%s' 'the complete known message text' > target-message.txt
./gmproof find --session session.json --text-file target-message.txt
```

`find` considers incoming messages only and requires an exact text match. It
returns a candidate only when exactly one match exists. It never deletes.

Deletion is a separate, destructive command and requires the exact message ID
twice:

```bash
./gmproof delete \
  --session session.json \
  --message-id MESSAGE_ID \
  --confirm MESSAGE_ID
```

Confirm on the phone that only the intended message disappeared. Keep the
session file for subsequent tests; delete both credential files when finished.

## Privacy and safety properties

- No SMS role or SMS-provider permissions.
- No analytics or remote service.
- Notification bodies and senders are not logged by the Android app.
- Unparseable Google Messages notifications are left untouched.
- Duplicate notification updates are suppressed for five minutes.
- Group-summary notifications are ignored.
- The CLI suppresses libgm's internal logs by default.
- The CLI refuses symlinked or group/world-readable session files.
- Exact matching rejects outgoing and ambiguous results.
- Imported Android auth is AES-GCM encrypted with an Android Keystore key and
  excluded from backup/device transfer.
- Android deletion requires a recent incoming message to be selected manually
  and confirmed explicitly.
- Automatic Android inbox deletion is not present in milestones 1–4.

## License

AGPL-3.0-or-later. Importing libgm requires AGPL-compatible distribution. See
[LICENSE](LICENSE) and [NOTICE](NOTICE).
