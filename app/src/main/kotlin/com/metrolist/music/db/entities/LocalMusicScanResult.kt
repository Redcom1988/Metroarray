/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

/**
 * Tracks the results of local music folder scans for debugging and user information.
 */
@Immutable
@Entity(
    tableName = "local_music_scan_result",
    foreignKeys = [
        ForeignKey(
            entity = LocalMusicFolder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["scannedAt"])
    ]
)
data class LocalMusicScanResult(
    @PrimaryKey val id: String = generateScanId(),
    val folderId: String,
    val scannedAt: LocalDateTime = LocalDateTime.now(),
    @ColumnInfo(defaultValue = "0")
    val songsAdded: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val songsRemoved: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val songsUpdated: Int = 0,
    @ColumnInfo(defaultValue = "0")
    val songsSkipped: Int = 0,
    val durationMs: Long = 0,
    val errorMessage: String? = null
) {
    companion object {
        fun generateScanId() = "LS" + RandomStringUtils.insecure().next(8, true, false)
    }
}
