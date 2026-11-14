# Build editions

PhotoManiak 0.8.0 provides two APKs built from the same commit:

- **FOSS** (`FOSS`): gallery features without Google Cast, Google Play services SDKs, media relay, Cast discovery permissions, or Cast buttons.
- **nonFOSS** (`nonFOSS`): the same gallery plus Google's proprietary Cast SDK and the local Cast relay.

Project source is licensed under [MIT](../LICENSE). Dependencies retain their own licenses; see the [runtime library inventory](THIRD_PARTY.md). Having a FOSS build does not mean the app is already available on F-Droid.

## Updates and switching

Both editions use `app.inphotomaniak`, version code 10 for 0.8.0, and the existing distribution certificate. Install either edition over earlier releases, or replace one 0.8.0 edition with the other, without uninstalling. Android retains the encrypted account, selected folders, index, collections, and appearance preferences. Older version-code downgrades are not supported through the normal installer. Because the app ID is shared, the editions cannot be installed side by side.

Stop an active Cast session before switching to FOSS. The installed edition is shown in Settings. Release APKs remain debug-signed development builds; a locally built APK with a different key cannot replace a published APK in place.

## Release assets

Publish only the FOSS and nonFOSS APKs in future releases. Do not attach a separate SHA256SUMS file; GitHub provides a SHA-256 digest for each release asset. The existing 0.7.2 checksum attachment is historical.

## Source layout

- `src/main`: shared gallery and data code, resources, and inactive Cast state model.
- `src/nonFOSS`: Cast implementation, relay, Google SDK integration, manifest entries, and styles.
- `src/FOSS`: inert Cast controller and empty composables; no SDK, socket, discovery, or placeholder buttons.
- `src/testNonFOSS`, `src/androidTestNonFOSS`: Cast-specific tests.
- `src/androidTestFOSS`: checks for missing SDK/relay classes and absent Cast UI.
- Shared unit and device tests run for both editions.

## Build and verify

```sh
./gradlew :app:testFOSSDebugUnitTest :app:lintFOSSDebug :app:assembleFOSSDebug
./gradlew :app:testNonFOSSDebugUnitTest :app:lintNonFOSSDebug :app:assembleNonFOSSDebug
./gradlew :app:fossDependencyReport
python3 scripts/check-foss-apk.py app/build/outputs/apk/FOSS/debug/app-FOSS-debug.apk
```

The Gradle FOSS configurations reject known proprietary Google SDK groups. The APK check checks defined DEX classes and manifest entries for those SDK namespaces. Optional integration strings in open-source libraries (such as AndroidX photo-picker intents or OkHttp provider names) are not bundled proprietary code. These are regression guards, not a universal license audit. Review licenses and update the inventory/notices when adding or upgrading dependencies. Open-source Google libraries such as Guava, Accompanist, and AndroidX are allowed; the vendor name alone does not determine the license.

Run `:app:connectedFOSSDebugAndroidTest` and `:app:connectedNonFOSSDebugAndroidTest` separately on a device/emulator. The nonFOSS native-picker test needs Google Play services. Other Cast tests cover the explanation shown without them.

For update testing, `EditionPersistenceTest` supports `-e editionCheck seed` and `-e editionCheck verify` around `adb install -r` on a dedicated test device. It uses fixture credentials only and does not contact kDrive. The normal suite performs a local round trip and clears the fixture.

## Automated release publishing

Push a version tag such as `v0.8.0` after updating `versionName`, `versionCode`, and `docs/releases/<version>.md`. The Android workflow builds and tests both variants, checks the FOSS APK, verifies the signing certificate, and publishes only `PhotoManiak-<version>-FOSS.apk` and `PhotoManiak-<version>-nonFOSS.apk` after both jobs succeed. It refuses mismatched tags and does not overwrite an existing release.

`ANDROID_SIGNING_KEY_B64` is a repository Actions secret containing the existing distribution keystore. Only version-tag builds restore it, with restricted file permissions, and remove it afterward. The key is never committed. Branch/PR builds use a runner-local development key and must not replace the release APKs. Releases continue using the existing debug certificate to preserve update compatibility; changing that certificate requires a separate migration plan.
