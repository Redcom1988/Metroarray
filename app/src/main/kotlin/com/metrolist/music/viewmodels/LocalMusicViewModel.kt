/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Local Music browser screen.
 * Provides local songs, albums, artists, and playlists.
 */
@HiltViewModel
class LocalMusicViewModel @Inject constructor(
    private val database: MusicDatabase
) : ViewModel() {

    private val _localSongs = MutableStateFlow<List<Song>>(emptyList())
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()

    private val _localAlbums = MutableStateFlow<List<Album>>(emptyList())
    val localAlbums: StateFlow<List<Album>> = _localAlbums.asStateFlow()

    private val _localArtists = MutableStateFlow<List<Artist>>(emptyList())
    val localArtists: StateFlow<List<Artist>> = _localArtists.asStateFlow()

    private val _localPlaylists = MutableStateFlow<List<Playlist>>(emptyList())
    val localPlaylists: StateFlow<List<Playlist>> = _localPlaylists.asStateFlow()

    init {
        loadLocalMusic()
    }

    private fun loadLocalMusic() {
        viewModelScope.launch {
            // Load local songs
            database.getLocalSongs().collectLatest { songs ->
                _localSongs.value = songs
            }
        }

        viewModelScope.launch {
            // Load local albums (filtered from all albums)
            database.albumsByCreateDateAsc().collectLatest { albums ->
                // Filter to only include albums that have local songs
                val localAlbumIds = _localSongs.value.mapNotNull { it.song.albumId }.toSet()
                _localAlbums.value = albums.filter { it.id in localAlbumIds || it.album.isLocal }
            }
        }

        viewModelScope.launch {
            // Load local artists (filtered from all artists)
            database.artistsByCreateDateAsc().collectLatest { artists ->
                // Filter to only include artists that have local songs
                val localArtistIds = _localSongs.value.flatMap { song ->
                    song.artists.map { it.id }
                }.toSet()
                _localArtists.value = artists.filter { it.id in localArtistIds || it.artist.isLocal }
            }
        }

        viewModelScope.launch {
            // Load local playlists
            database.playlistsByCreateDateAsc().collectLatest { playlists ->
                _localPlaylists.value = playlists.filter { it.playlist.isLocal }
            }
        }
    }

    /**
     * Refreshes the local music data.
     * Call this when you want to force a refresh of the data.
     */
    fun refresh() {
        loadLocalMusic()
    }
}
