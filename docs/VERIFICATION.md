# Verification

## Version 0.3.0

- APK compilation and Android Lint completed without errors.
- 31 JVM tests passed, covering capture dates, folder rules, and the read-only API transport.
- 8 Android 15 tests passed, including persistent thumbnail preferences, sharing content-URI access and read-only grants, English legacy date labels, and viewer controls without a photo counter.
- The renamed PhotoManiak APK retains application ID `app.inphotomaniak` and the same signing certificate as versions 0.1.0 and 0.2.0, allowing in-place updates.
- Share-sheet file access was tested with a local fixture; downloading and sharing originals from a real kDrive account still requires a device check with that account.

APK: `PhotoManiak-0.3.0-debug.apk`.

SHA-256: `d96bb9fb9e76b27b9b140a8de68368b050d7b87b1e88cf2e018978b773360b4b`.

## Version 0.2.0 — October 27, 2026

- APK compilation and Android Lint completed without errors.
- 31 JVM tests passed: 10 date tests, 6 folder-selection tests, and 15 API tests covering pagination, errors, credentials, redirects, and read-only access.
- 4 Android 15 tests passed: encrypted credentials, folder persistence, Android JSON parsing, and SQLite updates.
- The APK signature matches the previously distributed 0.1.0 build, allowing an in-place update.
- No real token was used in automated development checks. The user subsequently confirmed account access on their device.

APK: `inPhoToManiak-0.2.0-debug.apk`.

SHA-256: `4b7d34677d4a6f9ee36c123b79c2ec0b0b6347fa1520a9cf88a141cb864d0d6d`.

## Historical version 0.1.0 — October 26, 2026

- Debug APK built with JDK 17, Gradle 8.11.1, and Android SDK 35.
- 25 JVM tests passed: 10 date tests, 6 recursive folder-selection tests, and 9 WebDAV/security tests.
- 4 Android 15 emulator tests passed: credential encryption and removal, SQLite updates preserving other rows, native XML parsing, and folder-selection persistence.
- Android Lint completed without errors. Remaining suggestions concerned dependency versions and KTX extensions.
- APK installed and login screen launched on a Pixel 6 emulator without an AndroidRuntime crash. Status-bar contrast was corrected after visual inspection.

APK: `inPhoToManiak-0.1.0-debug.apk`.

SHA-256: `348e2e9e0a136c2cd8038b2445980470d3df39480bcc346f5f31e89177902675`.

## Scope of validation

API tests use a mock server. Real-account compatibility, codecs, and performance on thousands of items require testing with actual collections. Android 8 compatibility is checked statically, not on an API 26 emulator. Debug builds are not production-signed releases.
