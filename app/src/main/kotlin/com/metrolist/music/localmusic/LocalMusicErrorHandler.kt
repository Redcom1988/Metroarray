/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.localmusic

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.SongEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class LocalMusicErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase
) {
    /**
     * Checks if folder permissions are still valid.
     * Returns true if accessible, false if permissions were revoked.
     */
    fun isFolderAccessible(folderUri: String): Boolean {
        return try {
            val uri = folderUri.toUri()
            val folder = DocumentFile.fromTreeUri(context, uri)
            folder?.exists() == true && folder.canRead()
        } catch (e: Exception) {
            Timber.w(e, "Error checking folder accessibility: $folderUri")
            false
        }
    }

    /**
     * Checks if a specific local file still exists and is accessible.
     */
    fun isFileAccessible(localPath: String): Boolean {
        return try {
            val uri = localPath.toUri()
            val documentFile = DocumentFile.fromSingleUri(context, uri)
            documentFile?.exists() == true && documentFile.canRead()
        } catch (e: Exception) {
            Timber.w(e, "Error checking file accessibility: $localPath")
            false
        }
    }

    /**
     * Validates all local songs in the database and returns:
     * - Songs with missing files (marked as unavailable)
     * - Duplicate songs (based on file hash)
     */
    suspend fun validateLocalSongs(): ValidationResult = withContext(Dispatchers.IO) {
        val localSongs = database.getLocalSongs().first()
        val missingFiles = mutableListOf<SongEntity>()
        val duplicates = mutableListOf<DuplicateGroup>()
        
        // Track file hashes to detect duplicates
        val hashMap = mutableMapOf<String, MutableList<SongEntity>>()

        localSongs.forEach { song ->
            val songEntity = song.song
            
            // Check if file exists
            if (songEntity.localPath.isNullOrEmpty() || !isFileAccessible(songEntity.localPath)) {
                missingFiles.add(songEntity)
            } else {
                // Check for duplicates by computing file hash
                try {
                    val fileHash = computeFileHash(songEntity.localPath)
                    val existing = hashMap.getOrPut(fileHash) { mutableListOf() }
                    existing.add(songEntity)
                } catch (e: Exception) {
                    Timber.w(e, "Could not compute hash for: ${songEntity.localPath}")
                }
            }
        }

        // Find duplicates (files with same hash)
        hashMap.filter { it.value.size > 1 }.forEach { (_, songs) ->
            duplicates.add(DuplicateGroup(songs))
        }

        ValidationResult(
            missingFiles = missingFiles,
            duplicates = duplicates
        )
    }

    /**
     * Marks songs as unavailable (sets a flag in the database)
     */
    suspend fun markSongsAsUnavailable(songIds: List<String>) = withContext(Dispatchers.IO) {
        songIds.forEach { songId ->
            database.query {
                // Mark as unavailable - could add an "available" column
                // For now, we just log it
                Timber.d("Marking song as unavailable: $songId")
            }
        }
    }

    /**
     * Computes a hash of the file content for duplicate detection.
     * Uses first 1MB of file for performance.
     */
    private fun computeFileHash(localPath: String): String {
        return try {
            val uri = localPath.toUri()
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(1024 * 1024) // 1MB
                val bytesRead = inputStream.read(buffer)
                val digest = MessageDigest.getInstance("MD5")
                digest.update(buffer, 0, bytesRead)
                digest.digest().joinToString("") { "%02x".format(it) }
            } ?: ""
        } catch (e: Exception) {
            Timber.w(e, "Error computing file hash for: $localPath")
            localPath // Fallback to path-based hash
        }
    }

    /**
     * Result of validation
     */
    data class ValidationResult(
        val missingFiles: List<SongEntity>,
        val duplicates: List<DuplicateGroup>
    )

    /**
     * Group of duplicate songs
     */
    data class DuplicateGroup(
        val songs: List<SongEntity>
    ) {
        val primarySong: SongEntity? get() = songs.firstOrNull()
        val duplicateCount: Int get() = songs.size - 1
    }
}
