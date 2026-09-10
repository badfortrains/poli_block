# Milestone 3 libgm verification

This is a destructive manual proof. Use a disposable test SMS and verify every
ID before running `delete`.

## Acceptance sequence

1. Build and test `libgm-proof` as documented in the root README.
2. Create mode-`0600` `cookies.json` with the required `/web/config` cookies.
3. Run `gmproof pair` and approve the displayed emoji on the phone.
4. Run `gmproof list`; confirm recent conversation and message IDs appear.
5. Send a uniquely worded SMS to the phone.
6. Put its complete text in a local `target-message.txt` and run `gmproof find`.
7. Confirm the result is incoming, unique, and has the expected timestamp.
8. Run the printed `gmproof delete` command only after checking the ID twice.
9. Confirm that exact message disappears from Google Messages and adjacent
   legitimate messages remain.
10. Run `gmproof list` again to confirm the connection still works.

## Pass criteria

- Pairing completes with the current Google-account/emoji flow.
- Listing retrieves recent conversations and messages.
- Exact matching returns one known incoming message and refuses zero/multiple
  matches.
- `DeleteMessage` returns success and only the selected phone message vanishes.
- Each command exits and disconnects instead of maintaining a receiver.
- `session.json` remains mode `0600` and contains the refreshed auth state.

Live pairing and deletion cannot be automated in the test suite because they
require the user's Google account, phone approval, and a real disposable SMS.
