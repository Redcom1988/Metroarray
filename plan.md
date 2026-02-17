# Local Music Support Implementation Plan

## Overview
Implementation of local music playback support using Storage Access Framework (SAF) with folder-based playlist organization.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     Local Music Module                       │
├─────────────────────────────────────────────────────────────┤
│  UI Layer                                                    │
│  ├── LocalMusicSettingsScreen (folder management)            │
│  ├── LocalMusicBrowserScreen (browse local library)          │
│  └── PlaybackSourceDialog (choose local vs streaming)        │
├─────────────────────────────────────────────────────────────┤
│  Service Layer                                               │
│  ├── LocalMusicScanner (SAF folder scanning)                 │
│  ├── MetadataExtractor (ID3 tags, album art)                 │
│  ├── LocalMusicSyncWorker (background sync)                  │
│  └── LocalMusicRepository (data operations)                  │
├─────────────────────────────────────────────────────────────┤
│  Data Layer                                                  │
│  ├── LocalMusicFolder (entity)                               │
│  ├── LocalMusicScanResult (entity)                           │
│  └── SongEntity.localPath (column addition)                  │
└─────────────────────────────────────────────────────────────┘
```

## Implementation Phases

### Phase 1: Database Schema 
**Status:** ✅ **COMPLETE**

**Files Created/Modified:**

- [x] `app/src/main/kotlin/com/metrolist/music/db/entities/LocalMusicFolder.kt` (NEW)
  - Store SAF folder URIs with persisted permissions
  - Track last scan time and song count

- [x] `app/src/main/kotlin/com/metrolist/music/db/entities/LocalMusicScanResult.kt` (NEW)
  - History of scan operations for debugging

- [x] `app/src/main/kotlin/com/metrolist/music/db/entities/SongEntity.kt` (MODIFY)
  - Add `localPath: String?` column

- [x] `app/src/main/kotlin/com/metrolist/music/db/MusicDatabase.kt` (MODIFY)
  - Added new entities (LocalMusicFolder, LocalMusicScanResult)
  - Created migration 32→33 (Migration32To33)

- [x] `app/src/main/kotlin/com/metrolist/music/db/DatabaseDao.kt` (MODIFY)
  - Added CRUD operations for local music entities
  - Added queries for local songs management

---

### Phase 2: Media Scanning 
**Status:** ✅ **COMPLETE**

**Files Created:**

- [x] `app/src/main/kotlin/com/metrolist/music/localmusic/LocalMusicScanner.kt`
  - Scans folders using `DocumentFile` from SAF
  - Recursively finds audio files (MP3, M4A, FLAC, OGG, WAV, OPUS, AAC)
  - Generates unique IDs for songs: `local_<file_hash>`
  - Returns `Flow<FolderScanResult>` for reactive processing

- [x] `app/src/main/kotlin/com/metrolist/music/localmusic/MetadataExtractor.kt`
  - Uses `MediaMetadataRetriever` for metadata extraction
  - Extracts: title, artist, album, track number, duration, year, genre, embedded art
  - Handles fallback to filename for missing titles
  - Batch processing support with progress callbacks

- [x] `app/src/main/kotlin/com/metrolist/music/localmusic/LocalMusicRepository.kt`
  - Coordinates between scanner, metadata extractor, and database
  - Handles folder-to-playlist mapping (subfolders become playlists)
  - Provides methods: addFolder, removeFolder, refreshFolder, refreshAllFolders
  - Tracks scan statistics and saves results

**Folder Structure Logic:**
```
Selected Folder: /Music
├── Rock/                    → Playlist "Rock"
│   ├── song1.mp3
│   └── song2.mp3
├── Jazz/                    → Playlist "Jazz"
│   └── song3.flac
└── Singles/                 → Playlist "Singles"
    └── song4.m4a
```

---

### Phase 3: Background Sync 
**Status:** ✅ **COMPLETE**

**Files Created:**

- [x] `app/src/main/kotlin/com/metrolist/music/localmusic/LocalMusicSyncWorker.kt`
  - WorkManager worker for background scanning
  - Triggered on app startup via `enqueueSyncAll()`
  - Supports single folder or all folders sync
  - Provides progress tracking and error handling
  - Methods: `enqueueSyncAll()`, `enqueueSyncFolder()`, `cancelAllSync()`, `isSyncRunning()`

- [x] `app/src/main/kotlin/com/metrolist/music/di/LocalMusicModule.kt`
  - Hilt dependency injection for local music components
  - Provides LocalMusicScanner and MetadataExtractor

**Dependencies Added:**
- WorkManager runtime library
- Hilt WorkManager integration
- KSP compiler for Hilt worker support

---

### Phase 4: Settings UI 
**Status:** ✅ **COMPLETE**

**Files Created/Modified:**

- [x] `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/LocalMusicSettings.kt` (NEW)
  - Uses SAF folder picker (`ActivityResultContracts.OpenDocumentTree()`)
  - Displays list of watched folders with song count and last scan time
  - Shows scan progress with CircularProgressIndicator
  - Displays last scan results summary
  - Actions: Add Folder, Refresh, Rescan All, Remove Folder
  - Dialogs for confirmation and info

- [x] `app/src/main/kotlin/com/metrolist/music/viewmodels/LocalMusicSettingsViewModel.kt` (NEW)
  - HiltViewModel for managing UI state
  - Methods: addFolder(), removeFolder(), refreshFolder(), refreshAllFolders()
  - State flows: folders, isScanning, scanProgress, lastScanResults

- [x] `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt` (MODIFY)
  - Added route "settings/local_music" for LocalMusicSettings
  - Added import for LocalMusicSettings

- [x] `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/SettingsScreen.kt` (MODIFY)
  - Added "Local Music" option in Storage & Data section
  - Navigates to "settings/local_music" on click

---

### Phase 5: Browser UI 
**Status:** ✅ **COMPLETE**

**Files Created:**

- [x] `app/src/main/kotlin/com/metrolist/music/ui/screens/localmusic/LocalMusicScreen.kt`
  - Tab layout with HorizontalPager: Songs | Albums | Artists | Playlists
  - Each tab has List and Grid view support
  - Empty state messages when no content
  - Navigation to album/artist/playlist detail screens
  - TopAppBar with back navigation

- [x] `app/src/main/kotlin/com/metrolist/music/viewmodels/LocalMusicViewModel.kt`
  - HiltViewModel for local music data
  - StateFlows: localSongs, localAlbums, localArtists, localPlaylists
  - Filters to show only local content

**Files Modified:**

- [x] `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt`
  - Added route "local_music_browser" for LocalMusicScreen

- [x] `app/src/main/res/values/metrolist_strings.xml`
  - Added empty state strings for browser tabs

---

### Phase 6: Playback Integration 
**Status:** ✅ **COMPLETE**

**Files Modified:**

- [x] `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`
   - Handle local file URIs in `createDataSourceFactory`
   - Check for `local_` prefix in media ID
   - Use local path from SongEntity for playback
   - Handle missing local path with error

- [x] `app/src/main/kotlin/com/metrolist/music/extensions/MediaItemExt.kt`
   - Already handles local songs via media ID resolution in MusicService

- [x] `app/src/main/kotlin/com/metrolist/music/ui/component/PlaybackSourceDialog.kt` (NEW)
   - Dialog shown when song exists both locally and on YouTube Music
   - Options: "Play Local", "Play from YouTube Music", "Remember my choice"
   - If offline → Auto-play local without dialog

**Files Modified:**
- [x] `app/src/main/res/values/metrolist_strings.xml`
   - Added: local_file_not_found, choose_playback_source, play_local, play_streaming, remember_choice

---

### Phase 7: Album Art Handling 
**Status:** ✅ **COMPLETE**

**Files Created:**

- [x] `app/src/main/kotlin/com/metrolist/music/utils/LocalAlbumArtProvider.kt`
  - Cache extracted album art to app storage
  - Generate thumbnails (200x200)
  - Provide URIs for Coil image loading
  - Methods: getArtworkUri, getThumbnailUri, getBestArtworkUri, cacheArtwork, clearCache

**Integration:**
- MetadataExtractor already saves artwork during scanning (extractAndSaveArtwork)
- LocalAlbumArtProvider provides URIs for UI display via getBestArtworkUri()
- Hilt will auto-provide LocalAlbumArtProvider due to @Singleton annotation

---

### Phase 8: Error Handling & Edge Cases 
**Status:** ⏳ Pending

**Features to Implement:**

- [ ] Permission Loss Detection
  - Check URI validity before operations
  - Prompt user to re-select if permission revoked

- [ ] Missing File Handling
  - Mark songs as unavailable (greyed out)
  - Don't auto-delete, let user clean up manually

- [ ] Duplicate Detection
  - Hash-based duplicate detection within local library
  - Show warning for duplicates

---

## String Resources

✅ **Added to `metrolist_strings.xml`:**
```xml
<string name="local_music">Local Music</string>
<string name="local_music_folders">Music Folders</string>
<string name="add_music_folder">Add Music Folder</string>
<string name="add_music_folder_desc">Select a folder containing your music files. Subfolders will be converted to playlists automatically.</string>
<string name="add_music_folder_desc_short">Import music from device storage</string>
<string name="remove_folder">Remove Folder</string>
<string name="remove_folder_confirm">Remove "%s" from watched folders? Your music files will not be deleted.</string>
<string name="refresh_library">Refresh Library</string>
<string name="rescan_all">Rescan All Folders</string>
<string name="rescan_all_desc">Scan all watched folders for changes</string>
<string name="rescan_all_confirm">Rescan all folders? This will check for new, modified, or removed music files.</string>
<string name="rescan">Rescan</string>
<string name="last_scanned">Last scanned: %s</string>
<string name="songs_count">%d songs</string>
<string name="local_folder_removed">Folder access removed</string>
<string name="choose_playback_source">Choose Playback Source</string>
<string name="play_local">Play Local File</string>
<string name="play_streaming">Play from YouTube Music</string>
<string name="remember_choice">Remember my choice</string>
<string name="local_file_not_found">Local file not found</string>
<string name="scanning_folders">Scanning folders…</string>
<string name="scan_complete">Scan complete: %d songs added, %d removed</string>
<string name="last_scan">Last Scan</string>
<string name="scan_result_summary">%1$d added, %2$d updated, %3$d removed</string>
<string name="folder_inaccessible">Folder no longer accessible</string>
<string name="local_music_info_title">Add Your Music</string>
<string name="local_music_info_desc">Add folders from your device to play your local music collection. Each subfolder will become a playlist.</string>
<string name="select_folder">Select Folder</string>
```

---

## Design Decisions

1. **Storage Access Method:** Storage Access Framework (SAF) - User picks folders, more privacy-friendly
2. **Duplicate Handling:** Prefer local if no internet, otherwise show dialog for selection
3. **Auto-refresh:** On app startup via WorkManager
4. **Folder-to-Playlist mapping:** All subfolders become playlists automatically
5. **Album Art:** Extract from file metadata only

---

## Dependencies

Already available in project:
- Media3 (ExoPlayer) - for local playback
- Room - for database
- WorkManager - for background sync
- Hilt - for DI
- Coil - for image loading

New dependencies (if needed):
```kotlin
implementation("androidx.documentfile:documentfile:1.0.1") // SAF support
```

---

## Estimated Timeline

- Phase 1: 2-3 hours
- Phase 2: 4-5 hours
- Phase 3: 2-3 hours
- Phase 4: 3-4 hours
- Phase 5: 4-5 hours
- Phase 6: 3-4 hours
- Phase 7: 2-3 hours
- Phase 8: 2-3 hours

**Total: 22-30 hours**

---

## Testing Checklist

- [ ] Add folder via SAF picker
- [ ] Scan folder with subfolders
- [ ] Verify playlists created from subfolders
- [ ] Play local audio file
- [ ] Refresh library detects changes
- [ ] Offline mode plays local automatically
- [ ] Dialog appears when both sources available
- [ ] Album art displays correctly
- [ ] Handle permission loss gracefully
- [ ] Handle deleted files gracefully

---

## Notes

- Branch: `dev`
- Follow existing Kotlin coding standards
- Use Material 3 design guidelines
- Add comments for complex logic only
- Test on physical device with actual music files
