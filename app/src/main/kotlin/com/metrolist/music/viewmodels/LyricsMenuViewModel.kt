/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.EnableBetterLyricsKey
import com.metrolist.music.constants.EnableKugouKey
import com.metrolist.music.constants.EnableLrcLibKey
import com.metrolist.music.constants.EnableSimpMusicKey
import com.metrolist.music.constants.LastSelectedLyricsProviderKey
import com.metrolist.music.constants.PreferSyncedLyricsKey
import com.metrolist.music.constants.PreferredLyricsProvider
import com.metrolist.music.constants.PreferredLyricsProviderKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.lyrics.LyricsHelper
import com.metrolist.music.lyrics.LyricsResult
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class LyricsMenuViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val lyricsHelper: LyricsHelper,
    val database: MusicDatabase,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ViewModel() {
    private var job: Job? = null
    val results = MutableStateFlow(emptyList<LyricsResult>())
    val isLoading = MutableStateFlow(false)

    private val _isNetworkAvailable = MutableStateFlow(false)
    val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

    private val _currentSong = mutableStateOf<Song?>(null)
    val currentSong: State<Song?> = _currentSong

    // Provider selection dialog state
    var showProviderSelectionDialog by mutableStateOf(false)
        private set
    
    var availableProviders = mutableStateOf<List<ProviderInfo>>(emptyList())
        private set
    
    var selectedProviders = mutableStateOf<Set<String>>(emptySet())
        private set
    
    // Store search parameters for provider dialog
    private var pendingSearchParams: SearchParams? = null

    init {
        viewModelScope.launch {
            networkConnectivity.networkStatus.collect { isConnected ->
                _isNetworkAvailable.value = isConnected
            }
        }

        _isNetworkAvailable.value = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true // Assume connected as fallback
        }
        
        // Load available providers with their enabled status
        viewModelScope.launch {
            loadAvailableProviders()
        }
    }
    
    private suspend fun loadAvailableProviders() {
        val dataStore = context.dataStore.data.first()
        val enableBetterLyrics = dataStore[EnableBetterLyricsKey] ?: true
        val enableSimpMusic = dataStore[EnableSimpMusicKey] ?: true
        val enableLrcLib = dataStore[EnableLrcLibKey] ?: true
        val enableKugou = dataStore[EnableKugouKey] ?: true
        
        availableProviders.value = listOf(
            ProviderInfo("LrcLib", "LrcLib", enableLrcLib),
            ProviderInfo("KuGou", "KuGou", enableKugou),
            ProviderInfo("BetterLyrics", "BetterLyrics", enableBetterLyrics),
            ProviderInfo("SimpMusic", "SimpMusic", enableSimpMusic),
            ProviderInfo("YouTubeSubtitle", "YouTube Subtitle", true),
            ProviderInfo("YouTube", "YouTube", true)
        )
    }

    fun setCurrentSong(song: Song) {
        _currentSong.value = song
    }

    fun search(
        mediaId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        selectedProviders: Set<String>? = null,
    ) {
        isLoading.value = true
        results.value = emptyList()
        job?.cancel()
        job =
            viewModelScope.launch(Dispatchers.IO) {
                if (selectedProviders != null && selectedProviders.isNotEmpty()) {
                    // Search with selected providers
                    selectedProviders.forEach { providerName ->
                        lyricsHelper.getAllLyrics(
                            mediaId, 
                            title, 
                            artist, 
                            duration, 
                            album,
                            singleProvider = providerName
                        ) { result ->
                            results.update {
                                it + result
                            }
                        }
                    }
                } else {
                    // Search all providers
                    lyricsHelper.getAllLyrics(mediaId, title, artist, duration, album) { result ->
                        results.update {
                            it + result
                        }
                    }
                }
                isLoading.value = false
            }
    }

    fun cancelSearch() {
        job?.cancel()
        job = null
    }

    fun refetchLyrics(
        mediaMetadata: MediaMetadata,
        lyricsEntity: LyricsEntity?,
    ) {
        database.query {
            lyricsEntity?.let(::delete)
            val lyricsWithProvider =
                runBlocking {
                    lyricsHelper.getLyrics(mediaMetadata)
                }
            upsert(LyricsEntity(mediaMetadata.id, lyricsWithProvider.lyrics, lyricsWithProvider.provider))
        }
    }
    
    // Provider selection dialog methods
    fun openProviderSelectionDialog(
        mediaId: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null
    ) {
        // Store search parameters
        pendingSearchParams = SearchParams(mediaId, title, artist, duration, album)
        
        // Load provider selection state
        viewModelScope.launch {
            val dataStore = context.dataStore.data.first()
            val lastSelected = dataStore[LastSelectedLyricsProviderKey]
            
            // Pre-select last used provider or all enabled providers
            selectedProviders.value = if (lastSelected != null) {
                setOf(lastSelected)
            } else {
                availableProviders.value.filter { it.isEnabled }.map { it.name }.toSet()
            }
            
            showProviderSelectionDialog = true
        }
    }
    
    fun closeProviderSelectionDialog() {
        showProviderSelectionDialog = false
        pendingSearchParams = null
    }
    
    fun toggleProviderSelection(providerName: String) {
        selectedProviders.value = if (selectedProviders.value.contains(providerName)) {
            selectedProviders.value - providerName
        } else {
            selectedProviders.value + providerName
        }
    }
    
    fun searchWithSelectedProviders() {
        val params = pendingSearchParams ?: return
        
        // Save first selected provider for next time
        selectedProviders.value.firstOrNull()?.let { firstProvider ->
            viewModelScope.launch {
                context.dataStore.updateData { settings ->
                    settings.toMutablePreferences().apply {
                        this[LastSelectedLyricsProviderKey] = firstProvider
                    }
                }
            }
        }
        
        // Execute search with selected providers
        search(
            params.mediaId,
            params.title,
            params.artist,
            params.duration,
            params.album,
            selectedProviders.value
        )
        
        closeProviderSelectionDialog()
    }
}

data class ProviderInfo(
    val name: String,
    val displayName: String,
    val isEnabled: Boolean
)

private data class SearchParams(
    val mediaId: String,
    val title: String,
    val artist: String,
    val duration: Int,
    val album: String?
)
