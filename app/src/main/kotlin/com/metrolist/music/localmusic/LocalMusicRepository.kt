/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.localmusic

import android.content.Context
import android.net.Uri
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.ArtistEntity
import com.metrolist.music.db.entities.LocalMusicFolder
import com.metrolist.music.db.entities.LocalMusicScanResult
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.db.entities.SongAlbumMap
import com.metrolist.music.db.entities.SongArtistMap
import com.metrolist.music.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.RandomStringUtils
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Result of a scan operation
 */
data class ScanOperationResult(
    val success: Boolean,
    val songsAdded: Int,
    val songsRemoved: Int,
    val songsUpdated: Int,
    val playlistsCreated: Int,
    val errorMessage: String? = null
)

/**
 * Repository that coordinates local music scanning, metadata extraction,
 * and database operations.
 */
@Singleton
class LocalMusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) {
    private val scanner = LocalMusicScanner(context)
    private val metadataExtractor = MetadataExtractor(context)

    /**
     * Adds a new music folder to watch and scans it immediately
     */
    suspend fun addMusicFolder(
        folderUri: Uri,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): Result<ScanOperationResult> = withContext(Dispatchers.IO) {
        try {
            // Check if folder is accessible
            if (!scanner.isFolderAccessible(folderUri)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Cannot access folder: $folderUri")
                )
            }

            // Get folder name
            val folderName = scanner.getFolderDisplayPath(folderUri) ?: "Unknown"
            val displayPath = folderUri.toString()

            // Check if folder already exists
            val existingFolders = database.getAllLocalMusicFolders().first()
            if (existingFolders.any { it.folderUri == folderUri.toString() }) {
                return@withContext Result.failure(
                    IllegalStateException("Folder already added: $folderName")
                )
            }

            // Create folder record
            val folder = LocalMusicFolder(
                folderUri = folderUri.toString(),
                folderName = folderName,
                displayPath = displayPath
            )
            database.insert(folder)

            // Scan the folder
            val result = performScan(folder, onProgress)

            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error adding music folder: $folderUri")
            Result.failure(e)
        }
    }

    /**
     * Removes a music folder and optionally cleans up associated data
     */
    suspend fun removeMusicFolder(
        folderId: String,
        removeSongs: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val folder = database.getLocalMusicFolder(folderId).first()
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Folder not found: $folderId")
                )

            if (removeSongs) {
                // Get all local songs from this folder and mark them as removed
                val songs = database.getSongsInFolder(folder.folderUri).first()
                songs.forEach { song ->
                    database.markSongAsRemoved(song.song.localPath ?: "")
                }
            }

            // Delete scan results for this folder
            database.deleteScanResultsForFolder(folderId)

            // Delete folder record
            database.delete(folder)

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error removing music folder: $folderId")
            Result.failure(e)
        }
    }

    /**
     * Refreshes a specific folder - rescans for changes
     */
    suspend fun refreshFolder(
        folderId: String,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): Result<ScanOperationResult> = withContext(Dispatchers.IO) {
        try {
            val folder = database.getLocalMusicFolder(folderId).first()
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Folder not found: $folderId")
                )

            val folderUri = Uri.parse(folder.folderUri)

            // Check if still accessible
            if (!scanner.isFolderAccessible(folderUri)) {
                // Mark folder as inactive
                database.setFolderActive(folderId, false)
                return@withContext Result.failure(
                    IllegalStateException("Folder no longer accessible: ${folder.folderName}")
                )
            }

            // Mark as active again (in case it was inactive)
            database.setFolderActive(folderId, true)

            // Perform the scan
            val result = performScan(folder, onProgress)

            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error refreshing folder: $folderId")
            Result.failure(e)
        }
    }

    /**
     * Refreshes all active folders
     */
    suspend fun refreshAllFolders(
        onProgress: (String, String, Int) -> Unit = { _, _, _ -> }
    ): Map<String, ScanOperationResult> = withContext(Dispatchers.IO) {
        val folders = database.getActiveLocalMusicFolders().first()
        val results = mutableMapOf<String, ScanOperationResult>()

        folders.forEach { folder ->
            val result = refreshFolder(folder.id) { folderName, count ->
                onProgress(folder.folderName, folderName, count)
            }
            results[folder.id] = result.getOrElse {
                ScanOperationResult(
                    success = false,
                    songsAdded = 0,
                    songsRemoved = 0,
                    songsUpdated = 0,
                    playlistsCreated = 0,
                    errorMessage = it.message
                )
            }
        }

        results
    }

    /**
     * Performs the actual scanning operation
     */
    private suspend fun performScan(
        folder: LocalMusicFolder,
        onProgress: (String, Int) -> Unit
    ): ScanOperationResult {
        val startTime = System.currentTimeMillis()
        val folderUri = Uri.parse(folder.folderUri)

        var songsAdded = 0
        var songsRemoved = 0
        var songsUpdated = 0
        var playlistsCreated = 0
        var songsSkipped = 0

        try {
            // Collect all scan results
            val scanResults = mutableListOf<FolderScanResult>()
            scanner.scanFolder(folderUri).collect { result ->
                scanResults.add(result)
                onProgress(result.folderName, result.audioFiles.size)
            }

            // Process each folder result
            scanResults.forEach { folderResult ->
                // Create playlist for this subfolder
                val playlistId = createOrUpdatePlaylist(folderResult, folder)
                playlistsCreated++

                // Process each audio file
                folderResult.audioFiles.forEach { audioFile ->
                    val result = processAudioFile(audioFile, playlistId, folder)
                    when (result) {
                        is FileProcessResult.Added -> songsAdded++
                        is FileProcessResult.Updated -> songsUpdated++
                        is FileProcessResult.Skipped -> songsSkipped++
                    }
                }
            }

            // Update folder record
            val totalSongs = scanResults.sumOf { it.audioFiles.size }
            database.update(folder.updateScanStats(totalSongs))

            // Save scan result
            val scanResult = LocalMusicScanResult(
                folderId = folder.id,
                songsAdded = songsAdded,
                songsRemoved = songsRemoved,
                songsUpdated = songsUpdated,
                songsSkipped = songsSkipped,
                durationMs = System.currentTimeMillis() - startTime
            )
            database.insert(scanResult)

            return ScanOperationResult(
                success = true,
                songsAdded = songsAdded,
                songsRemoved = songsRemoved,
                songsUpdated = songsUpdated,
                playlistsCreated = playlistsCreated
            )
        } catch (e: Exception) {
            // Save failed scan result
            val scanResult = LocalMusicScanResult(
                folderId = folder.id,
                durationMs = System.currentTimeMillis() - startTime,
                errorMessage = e.message
            )
            database.insert(scanResult)

            return ScanOperationResult(
                success = false,
                songsAdded = songsAdded,
                songsRemoved = songsRemoved,
                songsUpdated = songsUpdated,
                playlistsCreated = playlistsCreated,
                errorMessage = e.message
            )
        }
    }

    /**
     * Creates or updates a playlist for a folder
     */
    private suspend fun createOrUpdatePlaylist(
        folderResult: FolderScanResult,
        parentFolder: LocalMusicFolder
    ): String {
        val playlistName = if (folderResult.folderPath == parentFolder.folderName) {
            // Root folder - use a special name or "Local Music"
            "${parentFolder.folderName}"
        } else {
            folderResult.folderName
        }

        // Check if playlist already exists for this folder
        val existingPlaylists = database.playlistsByNameAsc().first()
        val existingPlaylist = existingPlaylists.find { it.playlist.name == playlistName && it.playlist.isLocal }

        return if (existingPlaylist != null) {
            // Clear existing songs from this playlist (will be repopulated)
            database.clearPlaylist(existingPlaylist.playlist.id)
            existingPlaylist.playlist.id
        } else {
            // Create new playlist
            val playlist = PlaylistEntity(
                name = playlistName,
                isEditable = true,
                isLocal = true
            )
            database.insert(playlist)
            playlist.id
        }
    }

    /**
     * Processes a single audio file - extracts metadata and saves to database
     */
    private suspend fun processAudioFile(
        audioFile: LocalAudioFile,
        playlistId: String,
        folder: LocalMusicFolder
    ): FileProcessResult {
        // Check if file already exists
        val existingSong = database.getSongByLocalPath(audioFile.uri.toString())

        // Extract metadata
        val metadata = metadataExtractor.extractMetadata(audioFile.uri, audioFile.lastModified)
            ?: return FileProcessResult.Skipped

        // Create or update artist
        val artistId = createOrUpdateArtist(metadata.artistName)

        // Create or update album
        val albumId = metadata.albumName?.let { createOrUpdateAlbum(it, artistId, metadata.year) }

        // Extract album art
        val thumbnailUrl = if (metadata.embeddedArt != null) {
            metadataExtractor.extractAndSaveArtwork(audioFile.uri, audioFile.fileId)
        } else null

        val song = SongEntity(
            id = audioFile.fileId,
            title = metadata.title,
            duration = metadata.duration,
            thumbnailUrl = thumbnailUrl,
            albumId = albumId,
            albumName = metadata.albumName,
            year = metadata.year,
            date = metadata.dateModified,
            dateModified = metadata.dateModified,
            isLocal = true,
            localPath = audioFile.uri.toString(),
            inLibrary = LocalDateTime.now()
        )

        return if (existingSong == null) {
            // New song
            database.insert(song)

            // Create artist mapping
            database.insert(SongArtistMap(song.id, artistId, 0))

            // Create album mapping if applicable
            albumId?.let {
                database.insert(SongAlbumMap(song.id, it, metadata.trackNumber ?: 0))
            }

            // Add to playlist
            addSongToPlaylist(song.id, playlistId)

            FileProcessResult.Added
        } else {
            // Update existing song
            database.update(song.copy(id = existingSong.song.id))

            // Add to playlist if not already there
            addSongToPlaylist(existingSong.song.id, playlistId)

            FileProcessResult.Updated
        }
    }

    /**
     * Adds a song to a playlist if not already present
     */
    private suspend fun addSongToPlaylist(songId: String, playlistId: String) {
        val existingMapping = database.getPlaylistSongMap(playlistId, songId)
        if (existingMapping == null) {
            val position = database.getPlaylistSongCount(playlistId)
            database.insert(PlaylistSongMap(playlistId = playlistId, songId = songId, position = position))
        }
    }

    /**
     * Creates or gets an artist entity
     */
    private fun createOrUpdateArtist(artistName: String?): String {
        val name = artistName ?: "Unknown Artist"

        // Check if artist exists
        val existingArtist = database.artistByName(name)
        if (existingArtist != null) {
            return existingArtist.id
        }

        // Create new artist
        val artist = ArtistEntity(
            id = ArtistEntity.generateArtistId(),
            name = name,
            isLocal = true
        )
        database.insert(artist)
        return artist.id
    }

    /**
     * Creates or gets an album entity
     */
    private suspend fun createOrUpdateAlbum(
        albumName: String,
        artistId: String,
        year: Int?
    ): String {
        // Generate a deterministic ID for the album
        val albumId = "LA${albumName.hashCode().toString().take(8)}${artistId.takeLast(4)}"

        // Check if album exists
        val existingAlbum = runCatching {
            database.album(albumId).first()
        }.getOrNull()

        if (existingAlbum != null) {
            return albumId
        }

        // Create new album
        val album = AlbumEntity(
            id = albumId,
            title = albumName,
            year = year,
            songCount = 0,
            duration = 0,
            isLocal = true
        )
        database.insert(album)

        return albumId
    }

    /**
     * Gets all watched folders
     */
    fun getAllFolders(): Flow<List<LocalMusicFolder>> = database.getAllLocalMusicFolders()

    /**
     * Gets scan history for a folder
     */
    fun getScanHistory(folderId: String, limit: Int = 5): Flow<List<LocalMusicScanResult>> {
        return database.getScanResultsForFolder(folderId)
    }

    /**
     * Validates if a folder is still accessible
     */
    fun isFolderAccessible(folderUri: Uri): Boolean {
        return scanner.isFolderAccessible(folderUri)
    }

    private sealed class FileProcessResult {
        object Added : FileProcessResult()
        object Updated : FileProcessResult()
        object Skipped : FileProcessResult()
    }
}
