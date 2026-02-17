/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

/**
 * Represents a user-selected local music folder with Storage Access Framework (SAF) URI.
 * Each folder and its subfolders containing audio files will be scanned and imported.
 */
@Immutable
@Entity(tableName = "local_music_folder")
data class LocalMusicFolder(
    @PrimaryKey val id: String = generateFolderId(),
    val folderUri: String,
    val folderName: String,
    val displayPath: String? = null,
    val lastScanned: LocalDateTime? = null,
    @ColumnInfo(defaultValue = "0")
    val songCount: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun generateFolderId() = "LF" + RandomStringUtils.insecure().next(8, true, false)
    }

    fun updateScanStats(songCount: Int) = copy(
        lastScanned = LocalDateTime.now(),
        songCount = songCount
    )
}
