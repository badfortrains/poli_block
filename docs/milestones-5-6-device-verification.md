# Milestones 5–6 device verification

Use disposable incoming messages. Automatic deletion is intentionally strict:
false negatives are acceptable, but deleting a legitimate message is not.

## Milestone 6 — QR bootstrap

1. In a private desktop browser window, sign in to the Google Messages
   `/web/config` page and use DevTools to copy its request as cURL.
2. Open `tools/credential-qr/index.html` directly from this repository.
3. Paste the cURL command and click **Generate local QR**. Confirm the cURL text
   clears itself and no cookie values are displayed.
4. In Political SMS Filter, tap **Scan credential QR and pair** and scan it.
5. When the app displays an emoji, open Google Messages and approve that exact
   emoji for the new paired device.
6. Return to Political SMS Filter. It should report that pairing succeeded.
7. Clear the desktop QR and close the private window.
8. Tap **Fetch recent incoming messages** to verify the new encrypted session.

Expected: no credential file is copied to the phone; this app never requests
camera permission; final auth is encrypted and survives an app restart.

## Milestone 5 — normal and blocked messages

First confirm Google Messages notification channels remain enabled but silent,
and the Political SMS Filter `Messages` channel can alert.

Send a normal disposable message:

```text
Hey, want to get dinner tomorrow?
```

Expected: it stays in Google Messages and produces one replacement alert from
Political SMS Filter.

Then send a distinctive disposable blocked message:

```text
Acceptance test 7f31. Stop2End
```

Expected:

- Google Messages itself remains silent.
- Its notification is canceled and no replacement appears.
- WorkManager connects only when a network is available.
- The message disappears from Google Messages after one unique exact match.
- The app status reports that the last blocked message was deleted.
- Other messages in that conversation remain.

## Fail-safe checks

### Authentication unavailable

1. Tap **Clear imported credentials**.
2. Send a new disposable message containing `Stop2End`.

Expected: its notification is suppressed, but the inbox message is preserved.
The app status says pairing needs repair. Pair again before continuing.

### Ambiguous exact matches

1. Temporarily disable network connectivity on the phone.
2. Send two identical messages from the same sender within two minutes, both
   containing `Stop2End`.
3. Restore network connectivity so queued work can run.

Expected: both inbox messages are preserved because the recent window contains
multiple high-confidence candidates. The app status reports an ambiguous match.

## Safe diagnostics

```bash
adb logcat -s PoliticalSmsFilter
```

Logs report only lifecycle decisions such as queued, unique, ambiguous, retry,
or auth required. They must not contain sender names, phone numbers, message
bodies, cookies, message IDs, or auth material.
