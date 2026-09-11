# Political SMS Filter

An Android proof-of-concept that leaves Google Messages as the default SMS/RCS
app, keeps its notifications silent, and vibrates directly for allowed messages.
Messages containing `Stop2End` (case-insensitive) are suppressed.

This repository implements development milestones 1–6. Milestones 1–4 are
device-verified; the new automatic deletion and QR bootstrap paths await their
final physical-device acceptance tests.

## Status

- **Milestone 1 — implemented:** listen only to Google Messages, retain allowed
  conversation notifications, and vibrate directly without posting a replacement.
- **Milestone 2 — implemented:** messages containing `Stop2End`, in any case,
  have their Google notification canceled without triggering vibration.
- **Milestone 3 — implemented:** the standalone Go proof can pair through
  imported Google cookies, connect, list recent messages, locate one unique
  exact incoming match, explicitly delete a known message ID, save refreshed
  auth, and disconnect.
- **Milestone 4 — implemented and device-verified:** a minimal Go wrapper is
  compiled into an Android AAR. The app can import an existing paired session,
  encrypt it with Android Keystore, fetch recent incoming messages, and delete
  one explicitly selected message after confirmation. This was verified on a
  physical Pixel 9a on September 10, 2026.
- **Milestone 5 — implemented, awaiting device verification:** blocked
  notifications enqueue encrypted WorkManager jobs. A message is deleted only
  when exact text, normalized sender, incoming direction, and a two-minute
  timestamp window yield one unique candidate. Work is retried at most three
  times; ambiguous and uncertain deletes are never retried.
- **Milestone 6 — implemented, awaiting device verification:** the offline
  desktop helper converts a `/web/config` cURL request to a versioned compressed
  QR. Android scans it without camera permission, validates and minimizes the
  cookie set, displays the pairing emoji, and encrypts final libgm auth.

The project is intended for personal/sideloaded use. libgm uses an unofficial
protocol that can change without notice.

## Repository layout

```text
app/            Android notification listener, vibration, and filter proof
libgm-proof/    Independent desktop Go/libgm command-line proof
libgm-android/  Minimal Go Mobile wrapper and reproducible AAR build script
tools/          Offline desktop credential QR helper
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

1. Tap **Grant notification access** and enable Political SMS Filter.
2. Open the Google Messages notification settings from the app.
3. Leave Google Messages notifications enabled, but set all relevant incoming
   message channels to no sound and no vibration.
4. Tap **Test vibration** and confirm the phone produces the desired vibration.
5. Complete Google Messages pairing using the offline QR flow below, or retain
   an already imported milestone-4 session.

Do not disable Google Messages notifications entirely: the listener needs the
silent notification as its event signal.

See [docs/device-verification.md](docs/device-verification.md) for the milestone
1–2 acceptance test.

## Android libgm bridge and QR pairing

The checked-in `app/libs/libgmbridge.aar` contains arm64 device and x86_64
emulator libraries. To regenerate it from the pinned Go source:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./libgm-android/build-android.sh
```

For normal setup, open `tools/credential-qr/index.html` directly in a current
private browser window. Paste the Google Messages `/web/config` request copied
as cURL and generate the QR. The page is self-contained, blocks network
connections with Content Security Policy, keeps nothing in local storage, and
clears the pasted command after generation.

In the Android app, tap **Scan credential QR and pair**, scan the displayed QR,
then approve the exact emoji in Google Messages. The scanner processes the QR
on-device without this app requesting camera permission. The app accepts only
the versioned `GM1` payload, retains only the supported cookies, and stores the
resulting session with Android Keystore-backed AES-GCM encryption.

The manual `session.json` import remains as a diagnostic fallback. Every libgm
connection persists refreshed auth before disconnecting, and all libgm
operations are serialized to prevent competing refreshes.

See [docs/milestone-4-device-verification.md](docs/milestone-4-device-verification.md)
for the conservative one-message deletion test. See
[docs/milestones-5-6-device-verification.md](docs/milestones-5-6-device-verification.md)
for automatic deletion and QR pairing acceptance tests.

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
page, then obtain the request cookies through DevTools. The QR helper above is
the preferred Android setup; for the isolated CLI proof, put only the required
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
- Pending automatic-deletion targets are separately encrypted at rest and are
  removed after a terminal outcome.
- Android deletion requires a recent incoming message to be selected manually
  and confirmed explicitly, or a unique automatic match across exact text,
  sender, direction, and timestamp.
- Automatic deletion uses WorkManager network constraints, exponential backoff,
  and no more than three attempts. Missing and ambiguous matches preserve the
  inbox message; uncertain delete responses are not retried.
- Notification bodies, senders, raw cookies, and auth data are never logged.

## License

AGPL-3.0-or-later. Importing libgm requires AGPL-compatible distribution. See
[LICENSE](LICENSE) and [NOTICE](NOTICE).
