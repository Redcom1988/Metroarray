/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.localmusic

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.GridItemSize
import com.metrolist.music.constants.GridItemsSizeKey
import com.metrolist.music.constants.GridThumbnailHeight
import com.metrolist.music.constants.LibraryViewType
import com.metrolist.music.constants.PlaylistViewTypeKey
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.AlbumGridItem
import com.metrolist.music.ui.component.AlbumListItem
import com.metrolist.music.ui.component.ArtistGridItem
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.PlaylistGridItem
import com.metrolist.music.ui.component.PlaylistListItem
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.rememberEnumPreference
import kotlinx.coroutines.launch

/**
 * Enum representing the tabs in the local music browser
 */
enum class LocalMusicTab {
    SONGS, ALBUMS, ARTISTS, PLAYLISTS
}

/**
 * Main screen for browsing local music library.
 * Features tabs for Songs, Albums, Artists, and Playlists.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalMusicScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalMusicViewModel = hiltViewModel()
) {
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(pageCount = { LocalMusicTab.entries.size })
    val currentTab = LocalMusicTab.entries[pagerState.currentPage]

    val localSongs by viewModel.localSongs.collectAsState()
    val localAlbums by viewModel.localAlbums.collectAsState()
    val localArtists by viewModel.localArtists.collectAsState()
    val localPlaylists by viewModel.localPlaylists.collectAsState()

    var viewType by rememberEnumPreference(PlaylistViewTypeKey, LibraryViewType.LIST)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.local_music)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain,
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )

        // Tab Row
        ScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            edgePadding = 0.dp
        ) {
            LocalMusicTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            when (tab) {
                                LocalMusicTab.SONGS -> stringResource(R.string.filter_songs)
                                LocalMusicTab.ALBUMS -> stringResource(R.string.filter_albums)
                                LocalMusicTab.ARTISTS -> stringResource(R.string.filter_artists)
                                LocalMusicTab.PLAYLISTS -> stringResource(R.string.filter_playlists)
                            }
                        )
                    }
                )
            }
        }

        // Pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (LocalMusicTab.entries[page]) {
                LocalMusicTab.SONGS -> LocalSongsTab(
                    songs = localSongs,
                    viewType = viewType,
                    navController = navController,
                    menuState = menuState,
                    listState = lazyListState,
                    gridState = lazyGridState
                )
                LocalMusicTab.ALBUMS -> LocalAlbumsTab(
                    albums = localAlbums,
                    viewType = viewType,
                    navController = navController,
                    menuState = menuState,
                    listState = lazyListState,
                    gridState = lazyGridState,
                    gridItemSize = gridItemSize
                )
                LocalMusicTab.ARTISTS -> LocalArtistsTab(
                    artists = localArtists,
                    viewType = viewType,
                    navController = navController,
                    menuState = menuState,
                    listState = lazyListState,
                    gridState = lazyGridState,
                    gridItemSize = gridItemSize
                )
                LocalMusicTab.PLAYLISTS -> LocalPlaylistsTab(
                    playlists = localPlaylists,
                    viewType = viewType,
                    navController = navController,
                    menuState = menuState,
                    listState = lazyListState,
                    gridState = lazyGridState,
                    gridItemSize = gridItemSize
                )
            }
        }
    }
}

@Composable
private fun LocalSongsTab(
    songs: List<Song>,
    viewType: LibraryViewType,
    navController: NavController,
    menuState: com.metrolist.music.ui.component.MenuState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    if (songs.isEmpty()) {
        EmptyState(message = stringResource(R.string.no_local_songs))
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = pluralStringResource(
                        R.plurals.n_element,
                        songs.size,
                        songs.size
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        }

        items(
            items = songs,
            key = { it.id }
        ) { song ->
            SongListItem(
                song = song,
                navController = navController,
                onPlay = {
                    // Play song
                },
                onAddToQueue = {
                    // Add to queue
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalAlbumsTab(
    albums: List<Album>,
    viewType: LibraryViewType,
    navController: NavController,
    menuState: com.metrolist.music.ui.component.MenuState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    gridItemSize: GridItemSize
) {
    if (albums.isEmpty()) {
        EmptyState(message = stringResource(R.string.no_local_albums))
        return
    }

    when (viewType) {
        LibraryViewType.LIST -> {
            LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                items(
                    items = albums,
                    key = { it.id }
                ) { album ->
                    AlbumListItem(
                        album = album,
                        isActive = false,
                        isPlaying = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("album/${album.id}")
                            }
                    )
                }
            }
        }
        LibraryViewType.GRID -> {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                items(
                    items = albums,
                    key = { it.id }
                ) { album ->
                    AlbumGridItem(
                        album = album,
                        isActive = false,
                        isPlaying = false,
                        coroutineScope = rememberCoroutineScope(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { navController.navigate("album/${album.id}") }
                            )
                            .animateItem()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalArtistsTab(
    artists: List<Artist>,
    viewType: LibraryViewType,
    navController: NavController,
    menuState: com.metrolist.music.ui.component.MenuState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    gridItemSize: GridItemSize
) {
    if (artists.isEmpty()) {
        EmptyState(message = stringResource(R.string.no_local_artists))
        return
    }

    when (viewType) {
        LibraryViewType.LIST -> {
            LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                items(
                    items = artists,
                    key = { it.id }
                ) { artist ->
                    // Using ArtistListItem if available, otherwise simple text
                    Text(
                        text = artist.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate("artist/${artist.id}") }
                            .padding(16.dp)
                    )
                }
            }
        }
        LibraryViewType.GRID -> {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                items(
                    items = artists,
                    key = { it.id }
                ) { artist ->
                    ArtistGridItem(
                        artist = artist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { navController.navigate("artist/${artist.id}") }
                            )
                            .animateItem()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LocalPlaylistsTab(
    playlists: List<Playlist>,
    viewType: LibraryViewType,
    navController: NavController,
    menuState: com.metrolist.music.ui.component.MenuState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    gridItemSize: GridItemSize
) {
    if (playlists.isEmpty()) {
        EmptyState(message = stringResource(R.string.no_local_playlists))
        return
    }

    when (viewType) {
        LibraryViewType.LIST -> {
            LazyColumn(
                state = listState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                items(
                    items = playlists,
                    key = { it.id }
                ) { playlist ->
                    PlaylistListItem(
                        playlist = playlist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("local_playlist/${playlist.id}")
                            }
                    )
                }
            }
        }
        LibraryViewType.GRID -> {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                items(
                    items = playlists,
                    key = { it.id }
                ) { playlist ->
                    PlaylistGridItem(
                        playlist = playlist,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { navController.navigate("local_playlist/${playlist.id}") }
                            )
                            .animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
