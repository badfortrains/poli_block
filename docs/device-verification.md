# Milestones 1–2 device verification

These checks require a physical phone using Google Messages. The app cannot
programmatically make another app's notification channels silent, so verify
that setup before interpreting the results.

## Preparation

1. Install the debug APK and open **Political SMS Filter**.
2. Grant notification posting permission.
3. Grant notification-listener access.
4. In Google Messages notification settings, leave notifications enabled but
   disable sound and vibration on every channel that receives incoming SMS.
5. In this app's notification settings, make sure the `Messages` channel uses
   sound and/or vibration.
6. Tap **Test replacement notification** once. Confirm exactly one alert.

For safe diagnostics, use:

```bash
adb logcat -s PoliticalSmsFilter
```

The log reports receipt, filter decision, and replacement state, but not sender
or message content.

## Milestone 1 — replacement

Send this SMS from another number:

```text
Hey, want to get dinner tomorrow?
```

Expected:

- Google Messages stores the SMS.
- Google Messages itself produces no sound or vibration.
- Its original notification is canceled.
- Political SMS Filter produces exactly one alerting notification.
- The replacement displays sender and preview.
- Tapping it opens the corresponding Google Messages conversation. If the
  source notification has no usable content intent, it opens Google Messages.

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
- Political SMS Filter posts no replacement notification.
- No sound or vibration occurs, assuming Google Messages was configured silent.

Finally, send:

```text
This says stop to end, but not the keyword.
```

Expected: it is allowed and receives one replacement notification.

## Fail-open check

Non-message and group-summary notifications from Google Messages should not be
turned into replacement alerts. Notifications that do not expose a parseable
message body are left untouched rather than guessed at.
