/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.localmusic

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Data class containing extracted metadata from an audio file
 */
data class AudioMetadata(
    val title: String,
    val artistName: String?,
    val albumName: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val genre: String?,
    val duration: Int, // in seconds
    val dateModified: LocalDateTime,
    val embeddedArt: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioMetadata

        if (title != other.title) return false
        if (artistName != other.artistName) return false
        if (albumName != other.albumName) return false
        if (duration != other.duration) return false

        return true
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + (artistName?.hashCode() ?: 0)
        result = 31 * result + (albumName?.hashCode() ?: 0)
        result = 31 * result + duration
        return result
    }
}

/**
 * Extracts metadata from audio files using MediaMetadataRetriever.
 * Supports MP3, M4A, FLAC, OGG, OPUS, WAV, and other common formats.
 */
class MetadataExtractor(
    private val context: Context
) {

    /**
     * Extracts metadata from an audio file URI
     */
    suspend fun extractMetadata(
        uri: Uri,
        lastModified: Long = 0
    ): AudioMetadata? = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            // Extract basic metadata
            val title = retriever.extractTitle(uri)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.parseTrackNumber()
            val discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.parseDiscNumber()
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.parseYear()
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            // Extract embedded album art
            val embeddedArt = retriever.embeddedPicture

            AudioMetadata(
                title = title,
                artistName = artist.takeIf { it.isNotBlank() },
                albumName = album?.takeIf { it.isNotBlank() },
                albumArtist = albumArtist?.takeIf { it.isNotBlank() },
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                genre = genre?.takeIf { it.isNotBlank() },
                duration = TimeUnit.MILLISECONDS.toSeconds(duration).toInt(),
                dateModified = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(lastModified),
                    ZoneId.systemDefault()
                ),
                embeddedArt = embeddedArt
            )
        } catch (e: Exception) {
            Timber.e(e, "Error extracting metadata from: $uri")
            null
        } finally {
            retriever?.release()
        }
    }

    /**
     * Extracts title from file, falling back to filename if metadata is empty
     */
    private fun MediaMetadataRetriever.extractTitle(uri: Uri): String {
        val metadataTitle = extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
        if (!metadataTitle.isNullOrBlank()) {
            return metadataTitle
        }

        // Extract from filename
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "Unknown"
        return fileName
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' - ')
            .trim()
            .takeIf { it.isNotBlank() } ?: "Unknown"
    }

    /**
     * Parses track number from metadata string (handles "3/12" format)
     */
    private fun String.parseTrackNumber(): Int? {
        return try {
            substringBefore('/').trim().toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses disc number from metadata string
     */
    private fun String.parseDiscNumber(): Int? {
        return try {
            substringBefore('/').trim().toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parses year from metadata string (handles various formats)
     */
    private fun String.parseYear(): Int? {
        return try {
            // Extract first 4 digits that look like a year
            val yearRegex = Regex("\\d{4}")
            yearRegex.find(this)?.value?.toIntOrNull()
                ?.takeIf { it in 1900..2100 }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Batch extracts metadata from multiple files
     */
    suspend fun extractMetadataBatch(
        files: List<LocalAudioFile>,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): Map<LocalAudioFile, AudioMetadata?> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<LocalAudioFile, AudioMetadata?>()

        files.forEachIndexed { index, file ->
            val metadata = extractMetadata(file.uri, file.lastModified)
            results[file] = metadata
            onProgress(index + 1, files.size)
        }

        results
    }

    /**
     * Extracts album art and saves it to app storage
     * Returns the path to the saved artwork
     */
    suspend fun extractAndSaveArtwork(
        uri: Uri,
        songId: String
    ): String? = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)

            val art = retriever.embeddedPicture ?: return@withContext null

            // Save to app cache directory
            val artDir = File(context.cacheDir, "album_art").apply { mkdirs() }
            val artFile = File(artDir, "$songId.jpg")

            artFile.writeBytes(art)

            artFile.absolutePath
        } catch (e: Exception) {
            Timber.e(e, "Error extracting artwork from: $uri")
            null
        } finally {
            retriever?.release()
        }
    }
}
