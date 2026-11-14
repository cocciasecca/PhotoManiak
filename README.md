# PhotoManiak

A native Android gallery for photos and videos stored on Infomaniak kDrive. Browse selected folders in a timeline ordered by capture date, without moving or reorganizing your files.

**[Download](https://github.com/cocciasecca/PhotoManiak/releases)** · Android 8.0+ · English interface

## FOSS and non-FOSS editions

Starting with the **next release after 0.7.1**, PhotoManiak will offer two APKs from the same project:

| | PhotoManiak FOSS | PhotoManiak with Cast (non-FOSS) |
|---|---|---|
| Gallery, folders, sharing, Favorites, Archive, albums, and appearance settings | Yes | Yes |
| Google Cast | No | Yes |
| Proprietary Google libraries included in the APK | No | Yes — Google Cast SDK |
| Google Play services needed | No | Only for Cast |

**The current 0.7.1 APK is the non-FOSS edition with Cast. A FOSS APK is not available yet.** The split and an explicit open-source license for the project are planned for the next release. Publishing the source on GitHub alone does not make it FOSS. F-Droid availability is not yet promised.

## Connect your kDrive

1. Install the APK from [Releases](https://github.com/cocciasecca/PhotoManiak/releases).
2. Create a personal API token in [Infomaniak Manager](https://manager.infomaniak.com/v3/ng/accounts/token/list) with the **drive** scope.
3. Enter the token and your kDrive ID, found in the kDrive web address after `/drive/`.
4. Open **Folders**, select the folders to include, and tap **Show my library**. Subfolders are included automatically; you can exclude individual subfolders.

### Where are my folders?

The folder picker may show **Private** and **Common documents** at the top level:

- **Private** is your personal space. Start here to find the folders you normally browse or upload to in your personal kDrive space on the website.
- **Common documents** is the shared space. Files uploaded through the browser into that shared space will be here instead.

You can include multiple folders from the spaces your account can access. Each user connects their own account; never share your API token.

## Features

- Capture-date timeline using photo/video metadata, with filename and file-date fallbacks.
- Photo zoom, swipe navigation, video playback, and original-file sharing.
- Favorites, Archive, and virtual albums, with optional [collection synchronization through kDrive](docs/COLLECTIONS.md).
- Small, Medium, or Large thumbnails; System, Light, or Dark appearance; preset, wallpaper, or custom colors.
- A scrollable **Info** panel with date source, file path, and available GPS coordinates, camera, and dimensions.
- Multiple selection for sharing or deletion. **Delete moves originals to the kDrive trash after confirmation**, affecting the files on kDrive too.
- [Google Cast](docs/CAST.md) in the non-FOSS edition: show photos and videos on a compatible TV on the same Wi-Fi network. Keep the app open while casting.

PhotoManiak does not upload photos or videos. Virtual albums do not move originals. Optional collection sync only writes its own metadata folder and JSON files.

## Before you start

The first scan can take time and use mobile data while reading capture metadata. Sharing downloads the full original. Supported formats depend on kDrive thumbnails and your device's codecs; account access depends on your plan and permissions.

Release APKs are currently development builds signed with a debug key. Credentials are encrypted on the device; signing out clears local account data and settings.

## Build

Requires JDK 17, Android SDK Platform 35, and Build Tools 35.0.0. Set `ANDROID_HOME` or `sdk.dir` in `local.properties`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Current APK output: `app/build/outputs/apk/debug/app-debug.apk` (includes Cast). Run `./gradlew :app:connectedDebugAndroidTest` with a connected device or emulator for device tests.

## More details

[Changelog](CHANGELOG.md) · [Collection sync](docs/COLLECTIONS.md) · [Cast](docs/CAST.md) · [Project notes](docs/PROJECT.md) · [Verification](docs/VERIFICATION.md)

Independent app, not affiliated with Infomaniak. Inspired by [kPhotos](https://github.com/mateocarciu/kPhotos) and [Immich](https://github.com/immich-app/immich).
