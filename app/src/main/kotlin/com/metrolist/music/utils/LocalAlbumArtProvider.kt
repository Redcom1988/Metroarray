/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.metrolist.music.localmusic.MetadataExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAlbumArtProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val metadataExtractor: MetadataExtractor
) {
    private val artworkDir: File by lazy {
        File(context.filesDir, ALBUM_ART_DIR).apply { mkdirs() }
    }

    private val thumbnailDir: File by lazy {
        File(context.filesDir, THUMBNAIL_DIR).apply { mkdirs() }
    }

    companion object {
        private const val ALBUM_ART_DIR = "album_art"
        private const val THUMBNAIL_DIR = "album_art_thumbnails"
        private const val THUMBNAIL_SIZE = 200
        private const val JPEG_QUALITY = 85
    }

    /**
     * Gets the artwork URI for a local song.
     * Returns null if no artwork is cached.
     */
    fun getArtworkUri(songId: String): Uri? {
        val artFile = File(artworkDir, "$songId.jpg")
        return if (artFile.exists()) {
            Uri.fromFile(artFile)
        } else {
            null
        }
    }

    /**
     * Gets the thumbnail URI for a local song.
     * Returns null if no thumbnail is cached.
     */
    fun getThumbnailUri(songId: String): Uri? {
        val thumbFile = File(thumbnailDir, "$songId.jpg")
        return if (thumbFile.exists()) {
            Uri.fromFile(thumbFile)
        } else {
            null
        }
    }

    /**
     * Extracts and caches artwork for a local song.
     * Also generates a thumbnail.
     * Returns the artwork file path or null if extraction failed.
     */
    suspend fun cacheArtwork(
        songId: String,
        fileUri: Uri
    ): String? = withContext(Dispatchers.IO) {
        try {
            val artFile = File(artworkDir, "$songId.jpg")
            
            // Skip if already cached
            if (artFile.exists()) {
                return@withContext artFile.absolutePath
            }

            // Extract artwork from the file
            val artworkPath = metadataExtractor.extractAndSaveArtwork(fileUri, songId)
            
            if (artworkPath != null) {
                // Generate thumbnail after extracting artwork
                generateThumbnail(songId, File(artworkPath))
            }
            
            artworkPath
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache artwork for song: $songId")
            null
        }
    }

    /**
     * Generates a thumbnail from the full-size artwork.
     */
    private fun generateThumbnail(songId: String, sourceFile: File): Boolean {
        return try {
            val thumbFile = File(thumbnailDir, "$songId.jpg")
            
            // Skip if thumbnail already exists
            if (thumbFile.exists()) {
                return true
            }

            val original = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return false
            
            // Calculate scaled dimensions while maintaining aspect ratio
            val (width, height) = if (original.width > original.height) {
                THUMBNAIL_SIZE to (THUMBNAIL_SIZE * original.height / original.width)
            } else {
                (THUMBNAIL_SIZE * original.width / original.height) to THUMBNAIL_SIZE
            }

            val thumbnail = Bitmap.createScaledBitmap(original, width, height, true)
            
            // Save thumbnail
            FileOutputStream(thumbFile).use { out ->
                thumbnail.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }

            // Clean up
            thumbnail.recycle()
            if (!original.isRecycled) {
                original.recycle()
            }

            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate thumbnail for: $songId")
            false
        }
    }

    /**
     * Gets the best available artwork URI (thumbnail if available, else full-size).
     */
    fun getBestArtworkUri(songId: String): Uri? {
        return getThumbnailUri(songId) ?: getArtworkUri(songId)
    }

    /**
     * Clears all cached artwork.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        artworkDir.listFiles()?.forEach { it.delete() }
        thumbnailDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Clears artwork for a specific song.
     */
    fun clearArtwork(songId: String) {
        File(artworkDir, "$songId.jpg").delete()
        File(thumbnailDir, "$songId.jpg").delete()
    }

    /**
     * Gets the total cache size in bytes.
     */
    fun getCacheSize(): Long {
        val artSize = artworkDir.listFiles()?.sumOf { it.length() } ?: 0L
        val thumbSize = thumbnailDir.listFiles()?.sumOf { it.length() } ?: 0L
        return artSize + thumbSize
    }
}
