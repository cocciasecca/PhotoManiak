# Verification

## Version 0.7.0 — November 13, 2026

- APK build, Android Lint, and all 61 JVM tests passed.
- 23 Android 15 device tests passed, including the no-Google-Play-services Cast explanation and existing gallery/collection regressions. The Google-dependent picker test was skipped on that emulator.
- The official Cast framework initialized successfully and opened its native device chooser in a separate Android 37 emulator with Google Play services. This additional integration test passed, and the chooser screenshot was inspected.
- Relay tests cover GET/HEAD/OPTIONS, byte ranges, malformed requests, unsupported methods, exact resource matching, URL rotation/revocation, listener shutdown, upstream failures, and keeping kDrive bearer credentials out of receiver URLs/headers/bodies.
- The initial picker test harness used an Espresso API incompatible with the newer emulator. The final test uses native ActivityScenario and passed there; application code was unchanged by that harness adjustment.
- No real Chromecast/TV playback, codec compatibility, discovery on a physical Wi-Fi network, or live kDrive streaming was tested. These require a receiver and the user's account. Keep the app open while casting.
- The application ID and signing certificate match earlier releases for in-place updates.

APK: `PhotoManiak-0.7.0-debug.apk`.

SHA-256: `34f594525d986da904494697246947ef19d0ff81de03e1ff769ab14e8b7b5adc`.

## Version 0.6.0 — November 12, 2026

- APK build, Android Lint, 52 JVM tests, and 22 Android 15 device tests passed.
- Collection tests cover deterministic merging of concurrent edits, removal markers, deleted-album handling, file ID stability after moves/renames, schema and drive validation, create-only upload parameters, redirect rejection, uncertain-upload recovery, and keeping failed uploads pending.
- Device tests cover SQLite persistence after reopening, durable outbox bytes, edits made during upload, transactional rejection of altered edit IDs, republishing after changing sync locations, and Lamport clock advancement.
- UI tests exercise viewer favorite/archive actions and album creation, renaming, deletion confirmation/cancellation. Existing timeline scroll restoration, selection/trash confirmation, appearance, and sharing checks continue to pass.
- No real cloud files were created, uploaded, or deleted during testing. Collection synchronization uses a mock server; live kDrive write permissions and cross-device behavior should be checked with the user's account.
- The signing certificate and application ID match 0.5.0 for in-place updates.

APK: `PhotoManiak-0.6.0-debug.apk`.

SHA-256: `9599c981a353082c70a73391d07a7639c6e7aee7e0b610943c861c035cea6fdf`.

## Version 0.5.0 — November 3, 2026

- APK build, Android Lint, 37 JVM tests, and 15 Android 15 device tests passed.
- Added checks for persisted appearance settings, following system theme changes, explicit light/dark overrides, and adaptive/monochrome icon layers.
- Visually checked the login screen in system light and dark modes, including system-bar contrast.
- Themed icon colors depend on a compatible launcher and its themed-icons setting.
- The signing certificate and application ID match previous releases for in-place updates.

APK: `PhotoManiak-0.5.0-debug.apk`.

SHA-256: `a5f6d3eb3cb358551c2e41c8e985b595bb8dcf33af73cfd359e99f086370ce58`.

## Version 0.4.0 — October 30, 2026

- Build, Android Lint, 37 JVM tests, and 12 Android 15 device tests passed.
- API tests cover the exact trash endpoint, token handling, permission failure, explicit success responses, redirect rejection, and refusing folder/root deletion. The media HTTP client still rejects mutation requests.
- UI regression tests cover returning to the previous timeline position after opening a photo and recreating the screen, multiple selection, and cancelling the trash confirmation.
- Device tests also verify multiple sharing URIs and MIME types, read-only grants, and removal of only the intended indexed file.
- Included the pending screen/string-resource cleanup; existing interface and preference tests passed.
- No real cloud files were deleted during development. Live trash permissions and multi-file sharing should be checked with the user’s own account.
- The signing certificate and application ID match the published 0.3.0 APK for in-place updates.

APK: `PhotoManiak-0.4.0-debug.apk`.

SHA-256: `3cfcf87f4cf84defe824ebce335e146907de4060cfb82288587df7427cdbad10`.

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
