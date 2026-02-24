package com.metrolist.music.localmusic

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.metrolist.music.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import kotlin.Result as kotlinResult

/**
 * WorkManager worker for syncing local music folders.
 * Runs as a foreground service with persistent notification that updates in real-time.
 */
@HiltWorker
class LocalMusicSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: LocalMusicRepository
) : CoroutineWorker(appContext, workerParams) {

    private val notificationManager by lazy { 
        try {
            LocalMusicScanNotificationManager(applicationContext)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize notification manager")
            throw e
        }
    }

    init {
        try {
        } catch (e: Exception) {
            Timber.e(e, "Constructor error")
            throw e
        }
    }
    
    companion object {
        const val WORK_NAME = "local_music_sync"
        
        // Progress data keys
        const val PROGRESS_FOLDER_NAME = "folder_name"
        const val PROGRESS_SUBFOLDER_NAME = "subfolder_name"
        const val PROGRESS_SONGS_FOUND = "songs_found"
        const val PROGRESS_DETAIL_MESSAGE = "detail_message"
        const val KEY_FOLDER_ID = "folder_id"
        const val KEY_SYNC_TYPE = "sync_type"
        const val SYNC_TYPE_ALL = "sync_all"
        const val SYNC_TYPE_SINGLE = "sync_single"

        /**
         * Enqueues a one-time sync for all folders.
         * Called on app startup.
         */
        fun enqueueSyncAll(context: Context) {
            
            val workRequest = OneTimeWorkRequestBuilder<LocalMusicSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SYNC_TYPE to SYNC_TYPE_ALL
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_all",
                ExistingWorkPolicy.APPEND,
                workRequest
            )

        }

        /**
         * Enqueues a one-time sync for a specific folder.
         * Uses APPEND policy to queue folders instead of blocking.
         */
        fun enqueueSyncFolder(context: Context, folderId: String) {
            
            val workRequest = OneTimeWorkRequestBuilder<LocalMusicSyncWorker>()
                .setInputData(
                    workDataOf(
                        KEY_SYNC_TYPE to SYNC_TYPE_SINGLE,
                        KEY_FOLDER_ID to folderId
                    )
                )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) // Try to run immediately
                .addTag(WORK_NAME)
                .build()

            val workManager = WorkManager.getInstance(context)
            val operation = workManager.enqueueUniqueWork(
                "${WORK_NAME}_$folderId",
                ExistingWorkPolicy.APPEND,
                workRequest
            )

            
            // Check work status immediately after enqueueing
            try {
                val workInfos = workManager.getWorkInfosByTag(WORK_NAME).get()
                workInfos.forEach { workInfo ->
                    if (workInfo.state == androidx.work.WorkInfo.State.FAILED) {
                        val errorMessage = workInfo.outputData.getString("error") ?: "Unknown error"
                    }
                }
            } catch (e: Exception) {
            }
        }

        /**
         * Cancels all pending sync work.
         */
        fun cancelAllSync(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
        }

        /**
         * Checks if a sync is currently running.
         */
        fun isSyncRunning(context: Context): Boolean {
            val workManager = WorkManager.getInstance(context)
            val workInfos = workManager.getWorkInfosByTag(WORK_NAME).get()
            return workInfos.any { it.state == androidx.work.WorkInfo.State.RUNNING }
        }
    }

    override suspend fun doWork(): Result {
        
        return try {
            val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_ALL

            // Set up foreground service with notification
            
            try {
                setForeground(createForegroundInfo(applicationContext.getString(R.string.scanning_folders), "", 0))
            } catch (e: Exception) {
                return Result.failure(workDataOf("error" to "Failed to start foreground service: ${e.message}"))
            }
            

            val result: kotlinResult<ScanOperationResult> = when (syncType) {
                SYNC_TYPE_SINGLE -> {
                    val folderId = inputData.getString(KEY_FOLDER_ID)
                    
                    if (folderId == null) {
                        return Result.failure(
                            workDataOf("error" to "No folder ID provided for single sync")
                        )
                    }
                    syncSingleFolder(folderId)
                }
                else -> {
                    syncAllFolders()
                }
            }

            if (result.isSuccess) {
                val scanResult = result.getOrThrow()
                
                // Show completion notification
                notificationManager.showCompletionNotification(
                    songsAdded = scanResult.songsAdded,
                    songsUpdated = scanResult.songsUpdated,
                    playlistsCreated = scanResult.playlistsCreated
                )
                
                Result.success(
                    workDataOf(
                        "songs_added" to scanResult.songsAdded,
                        "songs_removed" to scanResult.songsRemoved,
                        "songs_updated" to scanResult.songsUpdated,
                        "playlists_created" to scanResult.playlistsCreated
                    )
                )
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                
                // Show error notification
                notificationManager.showErrorNotification(error)
                
                Result.failure(workDataOf("error" to error))
            }
        } catch (e: Exception) {
            notificationManager.showErrorNotification(e.message ?: "Unknown error")
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    /**
     * Creates foreground info for the notification.
     */
    private fun createForegroundInfo(
        folderName: String,
        subfolderName: String,
        songsFound: Int
    ): ForegroundInfo {
        val notification = notificationManager.createScanningNotification(
            folderName = folderName,
            subfolderName = subfolderName,
            songsFound = songsFound,
            workId = id.toString()
        )
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                LocalMusicScanNotificationManager.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(
                LocalMusicScanNotificationManager.NOTIFICATION_ID,
                notification
            )
        }
    }

    /**
     * Updates progress and foreground notification.
     * This can be called from non-suspend callbacks.
     */
    private suspend fun updateProgress(
        folderName: String,
        subfolderName: String = "",
        songsFound: Int = 0,
        detailMessage: String = ""
    ) {
        // Update WorkManager progress data (for ViewModel observation)
        setProgress(
            workDataOf(
                PROGRESS_FOLDER_NAME to folderName,
                PROGRESS_SUBFOLDER_NAME to subfolderName,
                PROGRESS_SONGS_FOUND to songsFound,
                PROGRESS_DETAIL_MESSAGE to detailMessage
            )
        )
        
        // Update foreground notification with new info
        setForeground(createForegroundInfo(folderName, subfolderName, songsFound))
    }

    /**
     * Syncs all active folders.
     */
    private suspend fun syncAllFolders(): kotlinResult<ScanOperationResult> {
        
        val results = mutableMapOf<String, ScanOperationResult>()

        repository.refreshAllFolders { folderName, subfolderName, count, detailMessage ->
            
            // Update progress and notification in real-time
            updateProgress(folderName, subfolderName, count, detailMessage)
        }.forEach { (folderId, result) ->
            results[folderId] = result
        }

        // Aggregate results
        val totalAdded = results.values.sumOf { it.songsAdded }
        val totalRemoved = results.values.sumOf { it.songsRemoved }
        val totalUpdated = results.values.sumOf { it.songsUpdated }
        val totalPlaylists = results.values.sumOf { it.playlistsCreated }
        val hasErrors = results.values.any { !it.success }


        return if (hasErrors) {
            val errors = results.filter { !it.value.success }
                .map { "${it.key}: ${it.value.errorMessage}" }
                .joinToString("; ")
            kotlinResult.failure(Exception("Some folders failed to sync: $errors"))
        } else {
            kotlinResult.success(
                ScanOperationResult(
                    success = true,
                    songsAdded = totalAdded,
                    songsRemoved = totalRemoved,
                    songsUpdated = totalUpdated,
                    playlistsCreated = totalPlaylists
                )
            )
        }
    }

    /**
     * Syncs a single folder.
     */
    private suspend fun syncSingleFolder(folderId: String): kotlinResult<ScanOperationResult> {
        
        return repository.refreshFolder(folderId) { folderName, subfolderName, count, detailMessage ->
            
            // Update progress and notification in real-time
            updateProgress(folderName, subfolderName, count, detailMessage)
        }
    }
}
