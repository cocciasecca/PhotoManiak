# Favorites, archive, and virtual albums

PhotoManiak stores collection edits separately from its replaceable media index in `collections.db`. Refreshing or changing included photo folders does not remove collection membership. References use kDrive file IDs, scoped to the configured drive, instead of paths. Renaming or moving an original within that drive therefore preserves membership when the file is rediscovered. Copying or re-uploading an original with a new ID creates a different item.

## Using collections

- In the viewer, use the heart, archive/unarchive, or add-to-album controls.
- In the timeline, long-press or use selection mode to favorite, archive, or add multiple items to an album. Within an album, selection mode also offers **Remove from album**. The action row scrolls horizontally on narrow screens.
- Open **Collections** to browse Favorites, Archive, and albums, or create, rename, and delete albums.
- Archive hides media only from the main Photos timeline. It does not move, rename, or delete the original. Archived media can still appear in Favorites and albums.
- Deleting an album removes only its virtual grouping. Moving original media to the kDrive trash remains a separate action with its existing confirmation.
- All collection screens respect the included/excluded photo folders. References to media outside those folders are retained, but the media is not displayed. Each device chooses its own photo folders.

## Optional synchronization

Collection changes work locally without an upload permission. To synchronize them, open **Settings → Collection sync → Choose location**, choose a writable parent folder, and tap **Use this folder**. PhotoManiak creates or reuses a `PhotoManiak` subfolder. Some kDrive roots are not writable; choose an accessible personal/team folder instead if creation is denied. On other devices, use the same kDrive and the same parent folder.

The folder contains small immutable `changes-<UUID>.json` files. This replaces the initially proposed single `library.json`: a single shared mutable file would need a confirmed atomic compare-and-swap API to prevent simultaneous overwrites. Create-only uploads (`conflict=error`) let devices publish separate changes without replacing each other's files. The app does not upload photos or videos.

Sync runs when opening/returning to the signed-in app, after collection edits (with a short debounce), on library refresh, or using **Sync now**. It is not a permanent background service. Network errors retain local changes and show a sync error in Settings, with a link from the main screens. A killed process retains pending upload bytes and filenames for the next attempt. Successful sync reflects files visible in that listing; edits uploaded concurrently by another device are picked up on the next sync.

Choosing a different sync location republishes the complete local history there and merges collections already stored at the destination. Disabling sync keeps local data and does not delete remote files. Signing out clears local collections and credentials; the confirmation warns if there are unsynced edits. Synced data can be recovered by connecting to the same location again.

## Conflict handling

Each edit has a UUID, logical Lamport clock, kind, target ID, value, and optional member ID. Per-field merging chooses the greatest `(clock, edit ID)`, independent of arrival order and phone wall clocks. Edits made after importing another edit have a greater logical clock. Independent changes combine. For conflicting offline edits to the same field, the deterministic clock/UUID ordering chooses one; it does not guarantee the latest wall-clock action wins.

Favorite removal, unarchiving, and removal from an album are explicit false-value edits retained in the history. Deleted albums use a permanent deletion marker, so an old offline device cannot resurrect them through a late rename or membership change. A new album receives a new ID.

The SQLite outbox stores the exact JSON and unique filename before upload. Retrying an uncertain upload first checks whether the filename already exists and verifies its full decoded contents. Only confirmed upload or exact-content recovery acknowledges those edits; changes made during upload remain pending. Malformed files, unsupported versions, drive mismatches, and altered edit IDs stop synchronization rather than silently replacing local data.

## Storage and privacy

These JSON files contain media IDs, collection changes, and album names, without credentials or image contents. They inherit their kDrive folder's access permissions; people who can read that folder can read the collection information. They are not additionally encrypted by PhotoManiak.

Do not manually edit or delete individual change files: they are the shared history, including removal markers. Back up the entire `PhotoManiak` folder. Automatic history compaction and recovery from manually deleted remote history are not implemented. A long history increases storage and first-sync API requests; each upload is capped at 1,000 edits and 2 MiB, and later syncs cache unchanged remote files locally.

The API transport follows the official Infomaniak Android client's folder-creation and direct-upload routes. Automated tests use a local mock server; real-account write permissions and compatibility require a device check with that account.

References: [official API routes](https://github.com/Infomaniak/android-kDrive/blob/main/app/src/main/java/com/infomaniak/drive/data/api/ApiRoutes.kt), [upload implementation](https://github.com/Infomaniak/android-kDrive/blob/main/app/src/main/java/com/infomaniak/drive/data/api/UploadTask.kt).
