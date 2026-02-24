/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.EventDiagnosticInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

data class ListeningStatistics(
    val totalListeningTimeMs: Long = 0,
    val totalSongsPlayed: Int = 0,
    val uniqueSongsListened: Int = 0,
    val uniqueArtists: Int = 0,
    val uniqueAlbums: Int = 0,
    val localSongCount: Int = 0,
    val youtubeSongCount: Int = 0,
    val localEventCount: Int = 0,
    val youtubeEventCount: Int = 0,
    val localListeningTimeMs: Long = 0,
    val youtubeListeningTimeMs: Long = 0,
    val daysSinceFirstPlay: Int = 0,
    val recentEvents: List<EventDiagnosticInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
) {
    val totalListeningHours: Int
        get() = (totalListeningTimeMs / 3600000).toInt()
    
    val totalListeningMinutes: Int
        get() = ((totalListeningTimeMs % 3600000) / 60000).toInt()
    
    val averageMinutesPerDay: Int
        get() = if (daysSinceFirstPlay > 0) {
            ((totalListeningTimeMs / 60000) / daysSinceFirstPlay).toInt()
        } else 0
    
    val localPlayPercentage: Int
        get() = if (totalSongsPlayed > 0) {
            ((localEventCount.toFloat() / totalSongsPlayed) * 100).roundToInt()
        } else 0
    
    val youtubePlayPercentage: Int
        get() = if (totalSongsPlayed > 0) {
            ((youtubeEventCount.toFloat() / totalSongsPlayed) * 100).roundToInt()
        } else 0
    
    val localListeningHours: Int
        get() = (localListeningTimeMs / 3600000).toInt()
    
    val youtubeListeningHours: Int
        get() = (youtubeListeningTimeMs / 3600000).toInt()
}

@HiltViewModel
class ListeningStatisticsViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    private val _stats = MutableStateFlow(ListeningStatistics(isLoading = true))
    val stats: StateFlow<ListeningStatistics> = _stats.asStateFlow()

    init {
        loadStatistics()
    }

    fun loadStatistics() {
        viewModelScope.launch(Dispatchers.IO) {
            _stats.value = _stats.value.copy(isLoading = true, error = null)
            
            try {
                val now = System.currentTimeMillis()
                
                // Get total listening time (all time) - remove timestamp filter for testing
                val totalListeningTimeMs = database.getTotalPlayTime()
                
                // Get counts - count all events without time filtering
                val totalEvents = database.eventCount().first()
                
                val uniqueSongs = database.getUniqueSongCount()
                
                val uniqueArtists = database.getUniqueArtistCount()
                
                val uniqueAlbums = database.getUniqueAlbumCount()
                
                // Get local vs YouTube breakdown
                val localSongCount = database.getLocalSongCount()
                val youtubeSongCount = database.getYouTubeSongCount()
                val localEventCount = database.getLocalEventCount()
                val youtubeEventCount = database.getYouTubeEventCount()

                
                // Calculate local vs YouTube listening time
                val localEvents = database.getLocalListeningTime().first()
                val youtubeEvents = database.getYouTubeListeningTime().first()
                
                // Get first event timestamp for days calculation
                val firstEventTimestamp = database.getFirstEventTimestamp().first() ?: now

                val daysSinceFirstPlay = if (firstEventTimestamp < now) {
                    val diffMs = now - firstEventTimestamp
                    val days = (diffMs / 86400000).toInt()
                    if (days == 0) 1 else days // At least 1 day
                } else 0
                
                // Get recent events for reference
                val recentEvents = database.getRecentEventsWithInfo()
                
                _stats.value = ListeningStatistics(
                    totalListeningTimeMs = totalListeningTimeMs,
                    totalSongsPlayed = totalEvents,
                    uniqueSongsListened = uniqueSongs,
                    uniqueArtists = uniqueArtists,
                    uniqueAlbums = uniqueAlbums,
                    localSongCount = localSongCount,
                    youtubeSongCount = youtubeSongCount,
                    localEventCount = localEventCount,
                    youtubeEventCount = youtubeEventCount,
                    localListeningTimeMs = localEvents,
                    youtubeListeningTimeMs = youtubeEvents,
                    daysSinceFirstPlay = daysSinceFirstPlay,
                    recentEvents = recentEvents,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _stats.value = _stats.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }
}
