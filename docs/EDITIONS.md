# Build editions

PhotoManiak 0.7.2 provides two APKs built from the same commit:

- **FOSS** (`foss`): gallery features without Google Cast, Google Play services SDKs, media relay, Cast discovery permissions, or Cast buttons.
- **With Cast / non-FOSS** (`withCast`): the same gallery plus Google's proprietary Cast SDK and the local Cast relay.

Project source is licensed under [MIT](../LICENSE). Dependencies retain their own licenses; see the [runtime library inventory](THIRD_PARTY.md). Having a FOSS build does not mean the app is already available on F-Droid.

## Updates and switching

Both editions use `app.inphotomaniak`, version code 9 for 0.7.2, and the existing distribution certificate. Install either edition over 0.7.1, or replace one 0.7.2 edition with the other, without uninstalling. Android retains the encrypted account, selected folders, index, collections, and appearance preferences. Older version-code downgrades are not supported through the normal installer. Because the app ID is shared, the editions cannot be installed side by side.

Stop an active Cast session before switching to FOSS. The installed edition is shown in Settings. Release APKs remain debug-signed development builds; a locally built APK with a different key cannot replace a published APK in place.

## Source layout

- `src/main`: shared gallery and data code, resources, and inactive Cast state model.
- `src/withCast`: Cast implementation, relay, Google SDK integration, manifest entries, and styles.
- `src/foss`: inert Cast controller and empty composables; no SDK, socket, discovery, or placeholder buttons.
- `src/testWithCast`, `src/androidTestWithCast`: Cast-specific tests.
- `src/androidTestFoss`: checks for missing SDK/relay classes and absent Cast UI.
- Shared unit and device tests run for both editions.

## Build and verify

```sh
./gradlew :app:testFossDebugUnitTest :app:lintFossDebug :app:assembleFossDebug
./gradlew :app:testWithCastDebugUnitTest :app:lintWithCastDebug :app:assembleWithCastDebug
./gradlew :app:fossDependencyReport
python3 scripts/check-foss-apk.py app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

The Gradle FOSS configurations reject known proprietary Google SDK groups. The APK check checks defined DEX classes and manifest entries for those SDK namespaces. Optional integration strings in open-source libraries (such as AndroidX photo-picker intents or OkHttp provider names) are not bundled proprietary code. These are regression guards, not a universal license audit. Review licenses and update the inventory/notices when adding or upgrading dependencies. Open-source Google libraries such as Guava, Accompanist, and AndroidX are allowed; the vendor name alone does not determine the license.

Run `:app:connectedFossDebugAndroidTest` and `:app:connectedWithCastDebugAndroidTest` separately on a device/emulator. The with-Cast native-picker test needs Google Play services. Other Cast tests cover the explanation shown without them.

For update testing, `EditionPersistenceTest` supports `-e editionCheck seed` and `-e editionCheck verify` around `adb install -r` on a dedicated test device. It uses fixture credentials only and does not contact kDrive. The normal suite performs a local round trip and clears the fixture.
