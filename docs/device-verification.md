# Milestones 1–2 device verification

These checks require a physical phone using Google Messages. The app cannot
programmatically make another app's notification channels silent, so verify
that setup before interpreting the results.

## Preparation

1. Install the debug APK and open **Political SMS Filter**.
2. Grant notification-listener access.
3. In Google Messages notification settings, leave notifications enabled but
   disable sound and vibration on every channel that receives incoming SMS.
4. Tap **Test vibration** once. Confirm exactly one vibration.

For safe diagnostics, use:

```bash
adb logcat -s PoliticalSmsFilter
```

The log reports receipt, filter decision, and vibration state, but not sender
or message content.

## Milestone 1 — retained notification and direct vibration

Send this SMS from another number:

```text
Hey, want to get dinner tomorrow?
```

Expected:

- Google Messages stores the SMS.
- Google Messages itself produces no sound or vibration.
- Its original notification remains visible with Google Messages' native UI.
- Political SMS Filter produces exactly one direct vibration and no notification.
- Tapping the notification opens the corresponding Google Messages conversation.

## Milestone 2 — filtering

Send each SMS separately:

```text
Campaign update. Stop2End
Campaign update. stop2end
Campaign update. STOP2END
```

Expected for each:

- Google Messages stores the SMS; milestones 1–2 do not delete inbox content.
- Its notification is canceled.
- Political SMS Filter posts no notification.
- No sound or vibration occurs, assuming Google Messages was configured silent.

Finally, send:

```text
This says stop to end, but not the keyword.
```

Expected: its Google Messages notification remains and it receives one direct vibration.

## Fail-open check

Non-message and group-summary notifications from Google Messages should not
trigger vibration. Notifications that do not expose a parseable message body
are left untouched rather than guessed at.
