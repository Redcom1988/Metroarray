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
**Status:** ⏳ Pending

**Files to Create:**

- [ ] `app/src/main/kotlin/com/metrolist/music/localmusic/LocalMusicSyncWorker.kt`
  - WorkManager worker for background scanning
  - Triggered on app startup
  - Compares current state with previous scan
  - Updates database with changes

- [ ] `app/src/main/kotlin/com/metrolist/music/di/LocalMusicModule.kt`
  - Hilt dependency injection for local music components

---

### Phase 4: Settings UI 
**Status:** ⏳ Pending

**Files to Create/Modify:**

- [ ] `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/LocalMusicSettings.kt` (NEW)
  - "Add Music Folder" button → Launches `Intent.ACTION_OPEN_DOCUMENT_TREE`
  - List of added folders with:
    - Folder name
    - Song count
    - Last scanned time
    - Remove button
  - "Refresh Now" button
  - "Rescan All" button

- [ ] `app/src/main/kotlin/com/metrolist/music/ui/screens/NavigationBuilder.kt` (MODIFY)
  - Add navigation route for local music settings

- [ ] `app/src/main/kotlin/com/metrolist/music/ui/screens/settings/SettingsScreen.kt` (MODIFY)
  - Add "Local Music" section in storage settings

---

### Phase 5: Browser UI 
**Status:** ⏳ Pending

**Files to Create:**

- [ ] `app/src/main/kotlin/com/metrolist/music/ui/screens/localmusic/LocalMusicScreen.kt`
  - Tab layout: Songs | Albums | Artists | Playlists
  - Show folder-based playlists
  - Indicate local-only songs with icon
  - Support for filtering/search

---

### Phase 6: Playback Integration 
**Status:** ⏳ Pending

**Files to Modify:**

- [ ] `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`
  - Handle local file URIs in `onMediaItemTransition`
  - Use `DefaultDataSource` for local files

- [ ] `app/src/main/kotlin/com/metrolist/music/extensions/MediaItemExt.kt` (MODIFY)
  - Support creating MediaItem from local path

- [ ] `app/src/main/kotlin/com/metrolist/music/ui/components/PlaybackSourceDialog.kt` (NEW)
  - Dialog shown when song exists both locally and on YouTube Music
  - Options: "Play Local", "Play from YouTube Music", "Remember my choice"
  - If offline → Auto-play local without dialog

---

### Phase 7: Album Art Handling 
**Status:** ⏳ Pending

**Files to Create:**

- [ ] `app/src/main/kotlin/com/metrolist/music/utils/LocalAlbumArtProvider.kt`
  - Cache extracted album art to app storage
  - Generate thumbnails
  - Provide URIs for Coil image loading

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

Add to `metrolist_strings.xml`:
```xml
<string name="local_music">Local Music</string>
<string name="local_music_folders">Music Folders</string>
<string name="add_music_folder">Add Music Folder</string>
<string name="remove_folder">Remove Folder</string>
<string name="refresh_library">Refresh Library</string>
<string name="rescan_all">Rescan All Folders</string>
<string name="last_scanned">Last scanned: %s</string>
<string name="songs_count">%d songs</string>
<string name="local_folder_removed">Folder access removed</string>
<string name="choose_playback_source">Choose Playback Source</string>
<string name="play_local">Play Local File</string>
<string name="play_streaming">Play from YouTube Music</string>
<string name="remember_choice">Remember my choice</string>
<string name="local_file_not_found">Local file not found</string>
<string name="scanning_folders">Scanning music folders...</string>
<string name="scan_complete">Scan complete: %d songs added, %d removed</string>
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
