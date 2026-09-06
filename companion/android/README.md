# CLumo Android App

[English] | [日本語 (README.ja.md)](README.ja.md)

The Android app for the companion firmware. What it does and how the device
behaves alongside it is covered in the [companion README](../README.md).

## Requirements

- A phone running Android 8.0 or newer with Bluetooth Low Energy
- JDK 17. Android Studio brings one; otherwise install one and let the Gradle
  wrapper fetch everything else.

## Build

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. A debug build has its own
package name, so it installs next to a release build instead of replacing it.

## Release builds

Release builds are signed with a keystore named by four environment variables:
`CLUMO_KEYSTORE_PATH`, `CLUMO_KEYSTORE_PASSWORD`, `CLUMO_KEY_ALIAS`, and
`CLUMO_KEY_PASSWORD`. A release task fails when any of them is missing rather
than falling back to the debug key. Signed release APKs are published on
[GitHub Releases](https://github.com/Cespresso/CLumo/releases/latest) as
`clumo.apk`.
