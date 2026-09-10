# Android libgm bridge

This Go module exposes the narrow milestone-4 API needed by Android:

- validate existing paired `AuthData`
- connect on demand
- fetch a small window of incoming messages as JSON
- delete exactly one caller-selected message ID
- return refreshed auth data
- disconnect

It does not pair, scan credentials, match notifications, run persistently, or
delete conversations. Android owns encrypted storage and runs every blocking
call away from the main thread.

## Build

Install Go 1.26 or newer and Android NDK 28.2.13676358, then run from the
repository root:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
./libgm-android/build-android.sh
```

The script pins Go Mobile, includes arm64 and x86_64 native libraries, and
writes `app/libs/libgmbridge.aar`. The AAR is committed so normal Android builds
do not require Go or the NDK.
