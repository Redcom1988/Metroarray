/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.db.entities

import androidx.room.ColumnInfo
import java.time.LocalDateTime

/**
 * Sample song info for diagnostics
 */
data class SampleSongInfo(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "isLocal") val isLocal: Boolean,
    @ColumnInfo(name = "localPath") val localPath: String?,
)

/**
 * Event diagnostic info combining event and song data
 */
data class EventDiagnosticInfo(
    @ColumnInfo(name = "id") val eventId: Long,
    @ColumnInfo(name = "songId") val songId: String,
    @ColumnInfo(name = "timestamp") val timestamp: LocalDateTime,
    @ColumnInfo(name = "playTime") val playTime: Long,
    @ColumnInfo(name = "title") val songTitle: String,
    @ColumnInfo(name = "isLocal") val isLocal: Boolean,
)
