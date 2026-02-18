/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
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
import com.metrolist.music.viewmodels.LocalMusicSettingsViewModel
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalMusicSettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val folders by viewModel.folders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val scanResults by viewModel.lastScanResults.collectAsState()

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var folderToRemove by remember { mutableStateOf<String?>(null) }
    var showRescanDialog by remember { mutableStateOf(false) }

    // SAF folder picker launcher - use createOpenDocumentTreeIntent to get persistable permission
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { selectedUri ->
            // Take persistable URI permission so we can access the folder later
            try {
                context.contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Timber.w("Could not take persistable permission: ${e.message}")
            }

            viewModel.addFolder(selectedUri)
        }
    }

    if (showAddFolderDialog) {
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = { Text(stringResource(R.string.add_music_folder)) },
            text = {
                Text(stringResource(R.string.add_music_folder_desc))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAddFolderDialog = false
                        folderPickerLauncher.launch(null)
                    }
                ) {
                    Text(stringResource(R.string.select_folder))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (folderToRemove != null) {
        val folder = folders.find { it.id == folderToRemove }
        AlertDialog(
            onDismissRequest = { folderToRemove = null },
            title = { Text(stringResource(R.string.remove_folder)) },
            text = {
                Text(
                    stringResource(
                        R.string.remove_folder_confirm,
                        folder?.folderName ?: ""
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        folderToRemove?.let { id ->
                            coroutineScope.launch {
                                viewModel.removeFolder(id)
                            }
                        }
                        folderToRemove = null
                    }
                ) {
                    Text(
                        stringResource(R.string.remove),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRemove = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showRescanDialog) {
        AlertDialog(
            onDismissRequest = { showRescanDialog = false },
            title = { Text(stringResource(R.string.rescan_all)) },
            text = {
                Text(stringResource(R.string.rescan_all_confirm))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRescanDialog = false
                        viewModel.refreshAllFolders()
                    }
                ) {
                    Text(stringResource(R.string.rescan))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRescanDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

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
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        // Add Folder Button
        Material3SettingsGroup(
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.add),
                    title = { Text(stringResource(R.string.add_music_folder)) },
                    description = { Text(stringResource(R.string.add_music_folder_desc_short)) },
                    onClick = { showAddFolderDialog = true }
                )
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Scan Progress
        if (isScanning) {
            Material3SettingsGroup(
                title = stringResource(R.string.scanning_folders),
                items = listOf(
                    Material3SettingsItem(
                        icon = null,
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(scanProgress)
                            }
                        },
                        description = {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = StrokeCap.Round
                            )
                        }
                    )
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Last Scan Results
        if (scanResults.isNotEmpty() && !isScanning) {
            Material3SettingsGroup(
                title = stringResource(R.string.last_scan),
                items = scanResults.map { result ->
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.done),
                        title = { Text(result.folderName) },
                        description = {
                            Text(
                                stringResource(
                                    R.string.scan_result_summary,
                                    result.songsAdded,
                                    result.songsUpdated,
                                    result.songsRemoved
                                )
                            )
                        }
                    )
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Watched Folders
        if (folders.isNotEmpty()) {
            Material3SettingsGroup(
                title = stringResource(R.string.local_music_folders),
                items = folders.map { folder ->
                    val lastScanned = folder.lastScanned
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.storage),
                        title = { Text(folder.folderName) },
                        description = {
                            Column {
                                Text(
                                    stringResource(
                                        R.string.songs_count,
                                        folder.songCount
                                    )
                                )
                                if (lastScanned != null) {
                                    Text(
                                        stringResource(
                                            R.string.last_scanned,
                                            viewModel.formatDateTime(lastScanned)
                                        ),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Row {
//                                if (!folder.isActive) {
//                                    Icon(
//                                        painter = painterResource(R.drawable.warning),
//                                        contentDescription = stringResource(R.string.folder_inaccessible),
//                                        tint = MaterialTheme.colorScheme.error,
//                                        modifier = Modifier.size(20.dp)
//                                    )
//                                    Spacer(modifier = Modifier.width(8.dp))
//                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.refreshFolder(folder.id)
                                        }
                                    },
                                    onLongClick = {}
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.refresh),
                                        contentDescription = stringResource(R.string.refresh_library)
                                    )
                                }
                                IconButton(
                                    onClick = { folderToRemove = folder.id },
                                    onLongClick = {}
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.close),
                                        contentDescription = stringResource(R.string.remove_folder),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    )
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }


        // Actions
        if (folders.isNotEmpty()) {
            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.refresh),
                        title = { Text(stringResource(R.string.rescan_all)) },
                        description = { Text(stringResource(R.string.rescan_all_desc)) },
                        onClick = { showRescanDialog = true }
                    )
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
        }


        // Info
        if (folders.isEmpty()) {
            Material3SettingsGroup(
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text(stringResource(R.string.local_music_info_title)) },
                        description = { Text(stringResource(R.string.local_music_info_desc)) }
                    )
                )
            )
        }
    }

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
        }
    )
}
