/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import com.metrolist.lrclib.LrcLib
import com.metrolist.lrclib.QueryMode
import com.metrolist.music.constants.EnableLrcLibKey
import com.metrolist.music.constants.LyricsQueryFallbackToCoarseKey
import com.metrolist.music.constants.LyricsQueryModeKey
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get
import kotlinx.coroutines.flow.first

object LrcLibLyricsProvider : LyricsProvider {
    override val name = "LrcLib"
    
    @Volatile
    private var appContext: Context? = null

    override fun isEnabled(context: Context): Boolean {
        appContext = context.applicationContext
        return context.dataStore[EnableLrcLibKey] ?: true
    }
    
    private suspend fun getQueryMode(): QueryMode {
        val context = appContext ?: return QueryMode.FINE
        val dataStore = context.dataStore.data.first()
        val queryModeString = dataStore[LyricsQueryModeKey] ?: "FINE"
        val fallbackToCoarse = dataStore[LyricsQueryFallbackToCoarseKey] ?: false
        
        // Determine query mode based on preferences
        return when (queryModeString) {
            "COARSE" -> QueryMode.COARSE
            "FINE" -> {
                if (fallbackToCoarse) {
                    QueryMode.FINE_WITH_COARSE_FALLBACK
                } else {
                    QueryMode.FINE
                }
            }
            else -> QueryMode.FINE  // Default to FINE
        }
    }

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> {
        val queryMode = getQueryMode()
        return LrcLib.getLyrics(title, artist, duration, album, queryMode)
    }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        val queryMode = getQueryMode()
        LrcLib.getAllLyrics(title, artist, duration, album, queryMode, callback)
    }
}
