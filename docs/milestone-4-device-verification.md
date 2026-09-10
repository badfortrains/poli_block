# Milestone 4 Android libgm bridge verification

This test proves only the manual Android bridge. It does not connect deletion
to notifications. Use a disposable incoming message and verify the selection
carefully: deletion is permanent across synced Google Messages devices.

## Prepare the phone

1. Finish the milestone 3 pairing and make sure no `gmproof` command is still
   connected.
2. Build and install `app/build/outputs/apk/debug/app-debug.apk` on an arm64
   Android device.
3. Transfer a copy of `libgm-proof/session.json` to the phone without editing
   it. Do not email it, put it in cloud storage, or paste its contents anywhere.
4. Open **Political SMS Filter** and tap **Import paired session.json**.
5. Choose the transferred file. The app should report that the session was
   imported and encrypted.
6. Delete the unencrypted transferred file from the phone's Files/Downloads
   app. Keep the original desktop file private for recovery during this proof.

The app's encrypted copy is excluded from Android backup and device transfer.
**Clear imported credentials** removes both the ciphertext and its app-only
Android Keystore key.

## Fetch without deleting

1. Send the phone a distinctive disposable SMS from another number.
2. Tap **Fetch recent incoming messages**.
3. Confirm the app shows recent incoming messages and the disposable message is
   identifiable by sender and preview.
4. Confirm that merely fetching does not remove anything from Google Messages.

Expected: the app starts a short-lived libgm connection, fetches a small recent
window, persists refreshed auth, and disconnects. It does not list outgoing
messages returned by the bridge.

## Delete exactly one known message

1. Select the distinctive disposable message in the spinner.
2. Tap **Delete selected test message**.
3. Read the confirmation dialog again and tap **Delete** only if its preview is
   the intended test message.
4. Wait for the success status, then open Google Messages on the phone.

Expected:

- The selected message disappears from Google Messages.
- Other messages in the same conversation remain.
- The deleted item disappears from the app's current selection list.
- A later **Fetch recent incoming messages** still connects successfully,
  proving the refreshed auth was retained.

If the app reports that deletion may have completed, inspect Google Messages
before retrying. This avoids accidentally issuing a second destructive request
after an uncertain network or auth-save result.

## Cleanup

When testing is finished, tap **Clear imported credentials**. Re-importing the
desktop `session.json` may not work after later sessions have refreshed its auth;
in that case, pair again with `gmproof` and import the newest session file.
