/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.metrolist.music.db.entities.LocalMusicFolder
import com.metrolist.music.localmusic.LocalMusicRepository
import com.metrolist.music.localmusic.LocalMusicSyncWorker
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

    private val workManager = WorkManager.getInstance(context)

    private val _folders = MutableStateFlow<List<LocalMusicFolder>>(emptyList())
    val folders: StateFlow<List<LocalMusicFolder>> = _folders.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress: StateFlow<String> = _scanProgress.asStateFlow()
    
    private val _scanDetailMessage = MutableStateFlow("")
    val scanDetailMessage: StateFlow<String> = _scanDetailMessage.asStateFlow()

    private val _lastScanResults = MutableStateFlow<List<ScanResultItem>>(emptyList())
    val lastScanResults: StateFlow<List<ScanResultItem>> = _lastScanResults.asStateFlow()
    
    private val _queuedFolders = MutableStateFlow<Int>(0)
    val queuedFolders: StateFlow<Int> = _queuedFolders.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    data class ScanResultItem(
        val folderName: String,
        val songsAdded: Int,
        val songsRemoved: Int,
        val songsUpdated: Int
    )

    init {
        loadFolders()
        observeWorkManager()
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
     * Observes WorkManager to update scanning state
     */
    private fun observeWorkManager() {
        viewModelScope.launch {
            workManager.getWorkInfosByTagFlow(LocalMusicSyncWorker.WORK_NAME).collect { workInfos ->
                val runningWork = workInfos.filter { it.state == WorkInfo.State.RUNNING }
                val enqueuedWork = workInfos.filter { it.state == WorkInfo.State.ENQUEUED }
                
                _isScanning.value = runningWork.isNotEmpty()
                _queuedFolders.value = enqueuedWork.size
                
                // Update progress from running work
                if (runningWork.isNotEmpty()) {
                    val progress = runningWork.firstOrNull()?.progress
                    val folderName = progress?.getString(LocalMusicSyncWorker.PROGRESS_FOLDER_NAME) ?: ""
                    val subfolderName = progress?.getString(LocalMusicSyncWorker.PROGRESS_SUBFOLDER_NAME) ?: ""
                    val songsFound = progress?.getInt(LocalMusicSyncWorker.PROGRESS_SONGS_FOUND, 0) ?: 0
                    val detailMessage = progress?.getString(LocalMusicSyncWorker.PROGRESS_DETAIL_MESSAGE) ?: ""
                    
                    if (folderName.isNotEmpty()) {
                        val progressText = if (subfolderName.isNotEmpty()) {
                            "$folderName/$subfolderName: $songsFound songs found"
                        } else {
                            "$folderName: $songsFound songs found"
                        }
                        _scanProgress.value = progressText
                        _scanDetailMessage.value = detailMessage
                    }
                } else {
                    // Clear detail message when not scanning
                    _scanDetailMessage.value = ""
                }
                
                // Reload folders when work completes
                if (workInfos.any { it.state == WorkInfo.State.SUCCEEDED }) {
                    loadFolders()
                }
            }
        }
    }

    /**
     * Adds a new music folder by first adding to DB, then enqueueing WorkManager scan
     */
    fun addFolder(uri: Uri) {
        viewModelScope.launch {
            
            // Add folder to database first (without scanning)
            val result = repository.addMusicFolderToDatabase(uri)
            
            result.onSuccess { folderId ->
                
                // Clear any previous error
                _errorMessage.value = null
                
                // Enqueue WorkManager job to scan the folder in background
                LocalMusicSyncWorker.enqueueSyncFolder(context, folderId)
                
            }.onFailure { error ->
                
                // Set user-friendly error message
                _errorMessage.value = when {
                    error.message?.contains("Parent folder conflict") == true -> {
                        // Extract folder name from error message
                        val folderName = error.message?.substringAfter("'")?.substringBefore("'")
                        "Cannot add parent folder: already watching subfolder '$folderName'"
                    }
                    error.message?.contains("Child folder conflict") == true -> {
                        val folderName = error.message?.substringAfter("'")?.substringBefore("'")
                        "Cannot add subfolder: already watching parent folder '$folderName'"
                    }
                    error.message?.contains("already being watched") == true -> {
                        "This folder is already being watched"
                    }
                    error.message?.contains("Cannot access folder") == true -> {
                        "Cannot access this folder. Please check permissions."
                    }
                    else -> {
                        "Failed to add folder: ${error.message}"
                    }
                }
            }
        }
    }
    
    /**
     * Clears the current error message
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Removes a music folder
     */
    suspend fun removeFolder(folderId: String) {
        repository.removeMusicFolder(folderId)
    }

    /**
     * Refreshes a specific folder using WorkManager
     */
    fun refreshFolder(folderId: String) {
        LocalMusicSyncWorker.enqueueSyncFolder(context, folderId)
    }

    /**
     * Refreshes all folders using WorkManager
     */
    fun refreshAllFolders() {
        LocalMusicSyncWorker.enqueueSyncAll(context)
    }

    /**
     * Cancels all pending scans
     */
    fun cancelAllScans() {
        LocalMusicSyncWorker.cancelAllSync(context)
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
