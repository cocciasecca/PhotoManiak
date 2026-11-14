# Changelog

## 0.7.2 — 2026-11-14

- Publish FOSS and with-Cast APKs from separate build flavors, with the same application ID/signature for in-place updates and edition switching.
- Exclude Google Cast SDKs, Cast relay/implementation, discovery permissions, and Cast UI from the FOSS edition.
- Keep existing gallery features unchanged and show the installed edition in Settings.
- Add the MIT license for project code, dependency documentation, and CI checks for both editions.
- Separate Cast-only source, resources, manifest entries, and tests as build cleanup.

## 0.7.1 — 2026-11-14

- Add persistent color schemes: Green, Blue, Purple, Orange, Wallpaper, and Custom.
- Add a custom color picker with hue slider and hex input, generating readable light/dark shades. Wallpaper colors require Android 12+ and fall back to green on older Android versions.
- Apply the selected palette to gallery surfaces and viewer controls while preserving the dark media viewer.
- Remove promotional slogans from the login screen, app header, empty timeline, and media count; use concise functional labels.


- Replace the Info dialog with a scrollable bottom sheet showing available EXIF GPS coordinates, camera, and dimensions.
- Move Favorite to the top right and group Cast, Add to album, and Archive in the overflow menu.

## 0.7.0 — 2026-11-13

- Add Google Cast device selection in the gallery and viewer.
- Cast the current photo/video and follow viewer swipes; add remote play/pause, seek, and stop controls.
- Prepare TV-sized JPEG photos and relay video byte ranges through the phone without sharing kDrive credentials or creating public links.
- Revoke local media URLs when changing items or stopping casting, and stop the session on sign-out.
- Explain the Google Play services requirement on unsupported phones while keeping the gallery usable.


## 0.6.0 — 2026-11-12

- Add Favorites, Archive, and virtual albums with persistent offline storage.
- Add collection actions in the viewer and multi-selection timeline, including removing items from an album.
- Add a Collections tab for browsing and managing albums; deleting an album leaves originals untouched.
- Add optional kDrive JSON synchronization in a user-selected location, with immutable change files, deterministic conflict merging, removal markers, and a durable retry outbox.
- Preserve references across renaming/moving originals using drive file IDs; respect each device's selected photo folders.
- Show synchronization errors/pending edits and warn before signing out with unsynced changes.

## 0.5.0

- Add persistent System, Light, and Dark appearance options, with System as the default.
- Keep viewer controls and system bars readable against the black photo/video background.
- Replace the legacy launcher drawable with an adaptive icon using separate foreground and background layers.
- Add a transparent monochrome layer for Android 13+ themed launcher icons.

## 0.4.0

- Preserve timeline scroll position when closing a photo, including after screen recreation.
- Add timeline multi-selection through the selection icon or long press.
- Share multiple original photos/videos through Android's share sheet.
- Add confirmed moves to kDrive trash from the timeline and viewer, with permission errors and partial-batch handling.
- Keep permanent deletion, folder deletion, and trash emptying unavailable.

- Split the main activity into focused screen and shared media components.
- Move interface and progress text into Android string resources.
- Rename obsolete WebDAV variable names to match the kDrive API client.
- Read the displayed app version from build configuration.

## 0.3.0

- Rename the app to PhotoManiak while preserving its Android application ID for in-place updates.

- Translate the app and project documentation into English.
- Add persistent Small, Medium, and Large thumbnail settings.
- Replace the viewer toolbar and photo counter with a single back arrow.
- Add bottom Share and Info buttons. Share sends a private temporary copy of the original through Android's share sheet.
- Display legacy date-source labels in English without changing capture dates or requiring a new scan.

## 0.2.0

- Replace WebDAV with kDrive REST APIs and personal-token authentication.
- Load cloud thumbnails and use paginated folder listings.
- Preserve the recursive folder selection, capture-date timeline, image viewer, and video playback.

## 0.1.0

- Initial native Android gallery with WebDAV access, local indexing, capture-date parsing, and read-only cloud browsing.
