/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.viewmodels.ListeningStatisticsViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListeningStatisticsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: ListeningStatisticsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss")

    Column {
        TopAppBar(
            title = { Text(stringResource(R.string.listening_statistics)) },
            navigationIcon = {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { viewModel.loadStatistics() }
                ) {
                    Icon(
                        painterResource(R.drawable.refresh),
                        contentDescription = "Refresh"
                    )
                }
            },
            scrollBehavior = scrollBehavior
        )

        if (stats.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.loading))
            }
        } else if (stats.error != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${stringResource(R.string.error)}: ${stats.error}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Column(
                Modifier
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Overall Statistics
                Material3SettingsGroup(
                    title = stringResource(R.string.overall_statistics),
                    items = buildList {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.timer),
                                title = { Text(stringResource(R.string.total_listening_time)) },
                                description = { 
                                    val hours = stats.totalListeningHours
                                    val minutes = stats.totalListeningMinutes
                                    if (hours > 0) {
                                        Text(stringResource(R.string.hours_minutes_format, hours, minutes))
                                    } else {
                                        Text(stringResource(R.string.minutes_format, minutes))
                                    }
                                }
                            )
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.music_note),
                                title = { Text(stringResource(R.string.total_songs_played)) },
                                description = { Text("${stats.totalSongsPlayed}") }
                            )
                        )
                        if (stats.daysSinceFirstPlay > 0) {
                            add(
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.today),
                                    title = { Text(stringResource(R.string.days_since_first_play)) },
                                    description = { Text("${stats.daysSinceFirstPlay}") }
                                )
                            )
                            add(
                                Material3SettingsItem(
                                    icon = painterResource(R.drawable.trending_up),
                                    title = { Text(stringResource(R.string.average_per_day)) },
                                    description = { 
                                        Text(stringResource(R.string.minutes_format, stats.averageMinutesPerDay))
                                    }
                                )
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(27.dp))

                // Library Statistics
                Material3SettingsGroup(
                    title = stringResource(R.string.library_statistics),
                    items = listOf(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.queue_music),
                            title = { Text(stringResource(R.string.unique_songs_listened)) },
                            description = { Text("${stats.uniqueSongsListened}") }
                        ),
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.artist),
                            title = { Text(stringResource(R.string.unique_artists)) },
                            description = { Text("${stats.uniqueArtists}") }
                        ),
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.album),
                            title = { Text(stringResource(R.string.unique_albums)) },
                            description = { Text("${stats.uniqueAlbums}") }
                        )
                    )
                )

                Spacer(modifier = Modifier.height(27.dp))

                // Music Sources
                Material3SettingsGroup(
                    title = stringResource(R.string.music_sources),
                    items = buildList {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.library_music),
                                title = { Text(stringResource(R.string.local_music)) },
                                description = { 
                                    val percentage = stats.localPlayPercentage
                                    val hours = stats.localListeningHours
                                    Text(
                                        stringResource(
                                            R.string.music_source_stats,
                                            stats.localSongCount,
                                            percentage,
                                            hours
                                        )
                                    )
                                }
                            )
                        )
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.language),
                                title = { Text(stringResource(R.string.youtube_music)) },
                                description = { 
                                    val percentage = stats.youtubePlayPercentage
                                    val hours = stats.youtubeListeningHours
                                    Text(
                                        stringResource(
                                            R.string.music_source_stats,
                                            stats.youtubeSongCount,
                                            percentage,
                                            hours
                                        )
                                    )
                                }
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(27.dp))

                // Recent Events (for reference)
                if (stats.recentEvents.isNotEmpty()) {
                    Material3SettingsGroup(
                        title = "${stringResource(R.string.recent_plays)} (${stats.recentEvents.size})",
                        items = stats.recentEvents.map { event ->
                            Material3SettingsItem(
                                icon = painterResource(
                                    if (event.isLocal) R.drawable.library_music else R.drawable.language
                                ),
                                title = { Text(event.songTitle) },
                                description = { 
                                    Text(
                                        "${if (event.isLocal) stringResource(R.string.filter_local) else "YouTube"} • ${event.playTime / 1000}s • ${event.timestamp.format(dateFormatter)}"
                                    )
                                }
                            )
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(27.dp))
                }
            }
        }
    }
}
