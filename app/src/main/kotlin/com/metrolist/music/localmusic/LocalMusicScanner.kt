/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.localmusic

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest

/**
 * Data class representing a discovered audio file with its metadata
 */
data class LocalAudioFile(
    val uri: Uri,
    val fileName: String,
    val folderName: String,
    val folderPath: String,
    val mimeType: String,
    val lastModified: Long,
    val fileId: String
)

/**
 * Result of scanning a folder
 */
data class FolderScanResult(
    val folderName: String,
    val folderPath: String,
    val audioFiles: List<LocalAudioFile>,
    val subfolders: List<String>
)

/**
 * Scans local music folders using Storage Access Framework (SAF).
 * Recursively finds audio files and maps subfolders to playlists.
 */
class LocalMusicScanner(
    private val context: Context
) {
    companion object {
        private val SUPPORTED_AUDIO_FORMATS = setOf(
            "audio/mpeg",      // MP3
            "audio/mp3",
            "audio/mp4",       // M4A, AAC
            "audio/aac",
            "audio/flac",      // FLAC
            "audio/ogg",       // OGG
            "audio/opus",      // OPUS
            "audio/wav",       // WAV
            "audio/x-wav",
            "audio/x-matroska", // Some formats
            "audio/webm"
        )

        private val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "m4a", "flac", "ogg", "opus", "wav", "aac", "wma"
        )
    }

    /**
     * Scans a folder and all its subfolders recursively.
     * Returns a flow of FolderScanResult for each folder containing audio files.
     */
    suspend fun scanFolder(
        folderUri: Uri,
        onProgress: (String, Int) -> Unit = { _, _ -> }
    ): Flow<FolderScanResult> = flow {
        withContext(Dispatchers.IO) {
            try {
                val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
                    ?: throw IllegalArgumentException("Cannot access folder: $folderUri")

                if (!rootFolder.exists() || !rootFolder.isDirectory) {
                    throw IllegalArgumentException("Invalid folder: $folderUri")
                }

                val rootFolderName = rootFolder.name ?: "Unknown"
                scanFolderRecursive(
                    rootFolder,
                    rootFolderName,
                    rootFolderName,
                    emit,
                    onProgress
                )
            } catch (e: Exception) {
                Timber.e(e, "Error scanning folder: $folderUri")
                throw e
            }
        }
    }

    /**
     * Recursively scans a folder and emits results for folders containing audio files
     */
    private suspend fun scanFolderRecursive(
        folder: DocumentFile,
        rootName: String,
        currentPath: String,
        emit: suspend (FolderScanResult) -> Unit,
        onProgress: (String, Int) -> Unit
    ) {
        val audioFiles = mutableListOf<LocalAudioFile>()
        val subfolders = mutableListOf<String>()

        folder.listFiles().forEach { file ->
            when {
                file.isDirectory -> {
                    val subfolderName = file.name ?: "Unknown"
                    val newPath = "$currentPath/$subfolderName"
                    subfolders.add(subfolderName)

                    // Recursively scan subfolder
                    scanFolderRecursive(file, rootName, newPath, emit, onProgress)
                }
                file.isFile && isAudioFile(file) -> {
                    val audioFile = createAudioFile(file, currentPath)
                    audioFiles.add(audioFile)
                    onProgress(currentPath, audioFiles.size)
                }
            }
        }

        // Emit result if this folder contains audio files
        if (audioFiles.isNotEmpty()) {
            val folderName = folder.name ?: "Unknown"
            emit(
                FolderScanResult(
                    folderName = folderName,
                    folderPath = currentPath,
                    audioFiles = audioFiles,
                    subfolders = subfolders
                )
            )
        }
    }

    /**
     * Checks if a file is a supported audio file
     */
    private fun isAudioFile(file: DocumentFile): Boolean {
        val mimeType = file.type ?: return false

        // Check MIME type
        if (SUPPORTED_AUDIO_FORMATS.any { mimeType.equals(it, ignoreCase = true) }) {
            return true
        }

        // Check file extension as fallback
        val extension = file.name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return SUPPORTED_EXTENSIONS.contains(extension)
    }

    /**
     * Creates a LocalAudioFile from a DocumentFile
     */
    private fun createAudioFile(file: DocumentFile, folderPath: String): LocalAudioFile {
        val fileName = file.name ?: "Unknown"
        val folderName = folderPath.substringAfterLast('/')

        return LocalAudioFile(
            uri = file.uri,
            fileName = fileName,
            folderName = folderName,
            folderPath = folderPath,
            mimeType = file.type ?: "audio/unknown",
            lastModified = file.lastModified(),
            fileId = generateFileId(file.uri)
        )
    }

    /**
     * Generates a unique ID for a file based on its URI
     */
    private fun generateFileId(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(uri.toString().toByteArray())
        return "local_" + hashBytes.joinToString("") { "%02x".format(it) }.take(16)
    }

    /**
     * Validates if a folder URI is still accessible
     */
    fun isFolderAccessible(folderUri: Uri): Boolean {
        return try {
            val folder = DocumentFile.fromTreeUri(context, folderUri)
            folder?.exists() == true && folder.canRead()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets the display path for a folder URI (for UI display)
     */
    fun getFolderDisplayPath(folderUri: Uri): String? {
        return try {
            val folder = DocumentFile.fromTreeUri(context, folderUri)
            folder?.name
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Counts total audio files in a folder (for progress reporting)
     */
    suspend fun countAudioFiles(folderUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            val rootFolder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@withContext 0

            countAudioFilesRecursive(rootFolder)
        } catch (e: Exception) {
            Timber.e(e, "Error counting files in folder: $folderUri")
            0
        }
    }

    private fun countAudioFilesRecursive(folder: DocumentFile): Int {
        var count = 0

        folder.listFiles().forEach { file ->
            when {
                file.isDirectory -> count += countAudioFilesRecursive(file)
                file.isFile && isAudioFile(file) -> count++
            }
        }

        return count
    }
}
