/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.entities.LocalMusicFolder
import com.metrolist.music.db.entities.LocalMusicScanResult
import com.metrolist.music.localmusic.LocalMusicRepository
import com.metrolist.music.localmusic.ScanOperationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/**
 * ViewModel for LocalMusicSettings screen
 */
@HiltViewModel
class LocalMusicSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: LocalMusicRepository
) : ViewModel() {

    private val _folders = MutableStateFlow<List<LocalMusicFolder>>(emptyList())
    val folders: StateFlow<List<LocalMusicFolder>> = _folders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()

    private val _lastScanResults = MutableStateFlow<List<ScanResultItem>>(emptyList())
    val lastScanResults: StateFlow<List<ScanResultItem>> = _lastScanResults.asStateFlow()

    data class ScanResultItem(
        val folderName: String,
        val songsAdded: Int,
        val songsRemoved: Int,
        val songsUpdated: Int
    )

    init {
        loadFolders()
    }

    private fun loadFolders() {
        viewModelScope.launch {
            repository.getAllFolders().collect { folderList ->
                _folders.value = folderList

                // Load scan results for each folder
                val results = mutableListOf<ScanResultItem>()
                folderList.forEach { folder ->
                    val scanHistory = repository.getScanHistory(folder.id, 1).first()
                    scanHistory.firstOrNull()?.let { scan ->
                        results.add(
                            ScanResultItem(
                                folderName = folder.folderName,
                                songsAdded = scan.songsAdded,
                                songsRemoved = scan.songsRemoved,
                                songsUpdated = scan.songsUpdated
                            )
                        )
                    }
                }
                _lastScanResults.value = results
            }
        }
    }

    /**
     * Adds a new music folder
     */
    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = context.getString(com.metrolist.music.R.string.scanning_folders)

            val result = repository.addMusicFolder(uri) { folderName, count ->
                _scanProgress.value = "$folderName: $count songs found"
            }

            _isScanning.value = false

            result.onSuccess { scanResult ->
                updateScanResults(scanResult)
            }
        }
    }

    /**
     * Removes a music folder
     */
    suspend fun removeFolder(folderId: String) {
        repository.removeMusicFolder(folderId)
    }

    /**
     * Refreshes a specific folder
     */
    fun refreshFolder(folderId: String) {
        viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = context.getString(com.metrolist.music.R.string.scanning_folders)

            val result = repository.refreshFolder(folderId) { folderName, count ->
                _scanProgress.value = "$folderName: $count songs found"
            }

            _isScanning.value = false

            result.onSuccess { scanResult ->
                updateScanResults(scanResult)
            }
        }
    }

    /**
     * Refreshes all folders
     */
    fun refreshAllFolders() {
        viewModelScope.launch {
            _isScanning.value = true
            _scanProgress.value = context.getString(com.metrolist.music.R.string.scanning_folders)

            val aggregatedResult = ScanOperationResult(
                success = true,
                songsAdded = 0,
                songsRemoved = 0,
                songsUpdated = 0,
                playlistsCreated = 0
            )

            repository.refreshAllFolders { folderName, subfolder, count ->
                _scanProgress.value = "$folderName/$subfolder: $count songs found"
            }.forEach { (_, result) ->
                if (result.success) {
                    aggregatedResult.copy(
                        songsAdded = aggregatedResult.songsAdded + result.songsAdded,
                        songsRemoved = aggregatedResult.songsRemoved + result.songsRemoved,
                        songsUpdated = aggregatedResult.songsUpdated + result.songsUpdated,
                        playlistsCreated = aggregatedResult.playlistsCreated + result.playlistsCreated
                    )
                }
            }

            _isScanning.value = false
            updateScanResults(aggregatedResult)
        }
    }

    private fun updateScanResults(result: ScanOperationResult) {
        viewModelScope.launch {
            // Reload scan results
            val results = mutableListOf<ScanResultItem>()
            _folders.value.forEach { folder ->
                val scanHistory = repository.getScanHistory(folder.id, 1).first()
                scanHistory.firstOrNull()?.let { scan ->
                    results.add(
                        ScanResultItem(
                            folderName = folder.folderName,
                            songsAdded = scan.songsAdded,
                            songsRemoved = scan.songsRemoved,
                            songsUpdated = scan.songsUpdated
                        )
                    )
                }
            }
            _lastScanResults.value = results
        }
    }

    /**
     * Formats a LocalDateTime for display
     */
    fun formatDateTime(dateTime: LocalDateTime): String {
        return dateTime.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))
    }
}
