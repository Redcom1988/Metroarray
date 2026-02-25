/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.lyrics

import android.content.Context
import android.util.LruCache
import com.metrolist.music.constants.PreferSyncedLyricsKey
import com.metrolist.music.constants.PreferredLyricsProvider
import com.metrolist.music.constants.PreferredLyricsProviderKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
    private val database: MusicDatabase,
) {
    private var lyricsProviders =
        listOf(
            BetterLyricsProvider,
            SimpMusicLyricsProvider,
            LrcLibLyricsProvider,
            KuGouLyricsProvider,
            YouTubeSubtitleLyricsProvider,
            YouTubeLyricsProvider
        )

    val preferred =
        context.dataStore.data
            .map {
                it[PreferredLyricsProviderKey].toEnum(PreferredLyricsProvider.LRCLIB)
            }.distinctUntilChanged()
            .map {
                lyricsProviders = when (it) {
                    PreferredLyricsProvider.LRCLIB -> listOf(
                        BetterLyricsProvider,
                        LrcLibLyricsProvider,
                        SimpMusicLyricsProvider,
                        KuGouLyricsProvider,
                        YouTubeSubtitleLyricsProvider,
                        YouTubeLyricsProvider
                    )
                    PreferredLyricsProvider.KUGOU -> listOf(
                        BetterLyricsProvider,
                        KuGouLyricsProvider,
                        SimpMusicLyricsProvider,
                        LrcLibLyricsProvider,
                        YouTubeSubtitleLyricsProvider,
                        YouTubeLyricsProvider
                    )
                    PreferredLyricsProvider.BETTER_LYRICS -> listOf(
                        BetterLyricsProvider,
                        SimpMusicLyricsProvider,
                        LrcLibLyricsProvider,
                        KuGouLyricsProvider,
                        YouTubeSubtitleLyricsProvider,
                        YouTubeLyricsProvider
                    )
                    PreferredLyricsProvider.SIMPMUSIC -> listOf(
                        BetterLyricsProvider,
                        SimpMusicLyricsProvider,
                        LrcLibLyricsProvider,
                        KuGouLyricsProvider,
                        YouTubeSubtitleLyricsProvider,
                        YouTubeLyricsProvider
                    )
                }
            }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()
        
        // Check if song is tagged as instrumental
        val song = database.song(mediaMetadata.id).first()
        if (song?.song?.isInstrumental == true) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Instrumental")
        }

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // If network check fails, try to proceed anyway
            true
        }
        
        if (!isNetworkAvailable) {
            // Still proceed but return not found to avoid hanging
            return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        // Get prefer synced lyrics setting
        val preferSynced = context.dataStore.data.first()[PreferSyncedLyricsKey] ?: true

        val scope = CoroutineScope(SupervisorJob())
        val deferred = scope.async {
            if (preferSynced) {
                // First pass: Try to find synced lyrics only
                for (provider in lyricsProviders) {
                    if (provider.isEnabled(context)) {
                        try {
                            val result = provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            )
                            result.onSuccess { lyrics ->
                                if (lyrics.startsWith("[")) {  // Is synced
                                    return@async LyricsWithProvider(lyrics, provider.name)
                                }
                            }.onFailure {
                                reportException(it)
                            }
                        } catch (e: Exception) {
                            // Catch network-related exceptions like UnresolvedAddressException
                            reportException(e)
                        }
                    }
                }
                
                // Second pass: Accept unsynced lyrics as fallback
                for (provider in lyricsProviders) {
                    if (provider.isEnabled(context)) {
                        try {
                            val result = provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            )
                            result.onSuccess { lyrics ->
                                return@async LyricsWithProvider(lyrics, provider.name)
                            }.onFailure {
                                reportException(it)
                            }
                        } catch (e: Exception) {
                            // Catch network-related exceptions like UnresolvedAddressException
                            reportException(e)
                        }
                    }
                }
            } else {
                // Original behavior: first successful result
                for (provider in lyricsProviders) {
                    if (provider.isEnabled(context)) {
                        try {
                            val result = provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            )
                            result.onSuccess { lyrics ->
                                return@async LyricsWithProvider(lyrics, provider.name)
                            }.onFailure {
                                reportException(it)
                            }
                        } catch (e: Exception) {
                            // Catch network-related exceptions like UnresolvedAddressException
                            reportException(e)
                        }
                    }
                }
            }
            return@async LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
        }

        val result = deferred.await()
        scope.cancel()
        return result
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        singleProvider: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { results ->
            val filteredResults = if (singleProvider != null) {
                results.filter { it.providerName == singleProvider }
            } else {
                results
            }
            filteredResults.forEach {
                callback(it)
            }
            return
        }

        // Check network connectivity before making network requests
        // Use synchronous check as fallback if flow doesn't emit
        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            // If network check fails, try to proceed anyway
            true
        }
        
        if (!isNetworkAvailable) {
            // Still try to proceed in case of false negative
            return
        }

        // Filter providers if singleProvider is specified
        val providersToQuery = if (singleProvider != null) {
            lyricsProviders.filter { it.name == singleProvider }
        } else {
            lyricsProviders
        }

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob()).launch {
            providersToQuery.forEach { provider ->
                if (provider.isEnabled(context)) {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: Exception) {
                        // Catch network-related exceptions like UnresolvedAddressException
                        reportException(e)
                    }
                }
            }
            cache.put(cacheKey, allResult)
        }

        currentLyricsJob?.join()
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)