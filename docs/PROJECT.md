# Project notes

## Scope

A native Android gallery for existing Infomaniak kDrive photos and videos. Each user configures their own account and selects folders in the app. No credentials or personal folder paths are hardcoded.

- One active account and drive.
- Recursive folder discovery, with explicit inclusion and exclusion.
- A unified timeline grouped by capture day, newest first.
- Photo zoom, swipe navigation, video playback, and original-file sharing.
- Small, Medium, and Large thumbnail preferences, stored locally.
- English UI; viewer with a back arrow and bottom Share/Delete/Info buttons, without a top toolbar or item counter.
- Thousands of items supported through a local index, paginated API listing, lazy grids, and thumbnail caching.
- Confirmed moves of individual media files to the kDrive trash; no uploads, permanent deletion, renaming, folder deletion, or deduplication.

## Implementation

Kotlin, Jetpack Compose, SQLite, Coil, OkHttp, Android ExifInterface, and Media3. REST API access uses a personal token with the `drive` scope and a user-provided drive ID. Browsing permits only GET and HEAD. A separate client permits DELETE only on `/2/drive/{drive_id}/files/{file_id}` for individual media files, with redirects and connection retries disabled. The `/trash` permanent-deletion endpoints are not used. Bearer credentials are sent only to the configured API origin and stripped on download redirects to allowed content hosts.

Credentials are encrypted with Android Keystore. Account backups are disabled. Sharing uses a narrowly scoped FileProvider for private temporary copies, with temporary read permission; it does not share credentials or authenticated URLs. Signing out clears local account data, preferences, indexes, and caches.

Folder discovery preserves the previous index until all selected folders have been listed successfully. Metadata processing is incremental and cancellable. Refresh reuses unchanged files and retries dates whose metadata could not be read.

## Capture dates

Priority inspired by Immich:

1. SubSecDateTimeOriginal
2. SubSecCreateDate
3. DateTimeOriginal
4. CreationDate
5. CreateDate
6. MediaCreateDate
7. DateTimeCreated
8. GPSDateTime
9. DateTimeUTC
10. SonyDateTime2
11. SourceImageCreateTime

Only tags available through the supported readers are considered. Preserve the capture-day offset where available. Use an unambiguous filename date, then remote modification time, when capture metadata is unavailable. Never use download time. Info shows the source, including a provisional marker when applicable. Existing indexes with Italian fallback labels are displayed in English without rescanning.

## History

Version 0.1.0 used WebDAV. Version 0.2.0 replaced that transport with kDrive REST APIs while retaining the native UI and date reader. Version 0.3.0 adds English text, thumbnail sizes, and a simplified viewer with file sharing.

WebDAV is not the current connection method. Account-plan compatibility still depends on API access and permissions.

## Source layout

- `MainActivity.kt`: activity setup, English locale, and theme.
- `GalleryApp.kt`: navigation and application shell.
- `LoginScreen.kt`, `FolderScreen.kt`, `TimelineScreen.kt`, `SettingsScreen.kt`, `ViewerScreen.kt`: individual screens.
- `MediaComponents.kt`, `VideoPlayer.kt`, `ErrorCard.kt`: shared UI components.
- `GalleryModel.kt`: state, folder selection, and indexing.
- `DriveClient.kt`, `MediaReader.kt`, `ShareFiles.kt`: API browsing/trash transport, metadata, and sharing.
- `Models.kt`, `Storage.kt`: data models and local persistence.
- `res/values/strings.xml`: English interface and progress messages. Technical transport exceptions remain in the non-UI layer.

The Android application ID and signing key are kept stable across name changes and refactors. The APK attached to a published release is not replaced by maintenance builds.

## Version 0.4.0

The grid state is owned by `GalleryContent`, above the viewer branch, so opening a photo no longer discards the timeline position. Compose saves the grid index and offset across screen recreation.

Deletion requires an explicit confirmation dialog. The ViewModel serializes it against scanning and other account operations. Each successful response removes only that file from SQLite and the visible library; failure stops the batch and preserves unconfirmed items. Shared-file intents use ACTION_SEND for one item or ACTION_SEND_MULTIPLE for multiple items, with read-only grants for every URI.

The trash route matches `deleteFile` in the [official Android client](https://github.com/Infomaniak/android-kDrive/blob/master/app/src/main/java/com/infomaniak/drive/data/api/ApiRepository.kt). The public API description calls it “Trash a file, move it to the trash can”; permanent removal is a separate route.
