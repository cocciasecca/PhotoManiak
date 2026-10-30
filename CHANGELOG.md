# Changelog

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
