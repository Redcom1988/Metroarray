/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.localmusic

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * WorkManager worker for syncing local music folders.
 * Scans all watched folders on app startup and updates the library.
 */
@HiltWorker
class LocalMusicSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: LocalMusicRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "local_music_sync"
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
                ExistingWorkPolicy.KEEP,
                workRequest
            )

            Timber.d("Enqueued local music sync for all folders")
        }

        /**
         * Enqueues a one-time sync for a specific folder.
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
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "${WORK_NAME}_$folderId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )

            Timber.d("Enqueued local music sync for folder: $folderId")
        }

        /**
         * Cancels all pending sync work.
         */
        fun cancelAllSync(context: Context) {
            WorkManager.getInstance(context).cancelAllWorkByTag(WORK_NAME)
            Timber.d("Cancelled all local music sync work")
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
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_ALL

        return try {
            Timber.d("Starting local music sync (type: $syncType)")

            val result = when (syncType) {
                SYNC_TYPE_SINGLE -> {
                    val folderId = inputData.getString(KEY_FOLDER_ID)
                        ?: return Result.failure(
                            workDataOf("error" to "No folder ID provided for single sync")
                        )
                    syncSingleFolder(folderId)
                }
                else -> syncAllFolders()
            }

            if (result.isSuccess) {
                val scanResult = result.getOrThrow()
                Timber.d("Local music sync completed successfully: $scanResult")
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
                Timber.e("Local music sync failed: $error")
                Result.failure(workDataOf("error" to error))
            }
        } catch (e: Exception) {
            Timber.e(e, "Local music sync crashed")
            Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    /**
     * Syncs all active folders.
     */
    private suspend fun syncAllFolders(): Result<ScanOperationResult> {
        val results = mutableMapOf<String, ScanOperationResult>()

        repository.refreshAllFolders { folderName, subfolder, count ->
            Timber.d("Scanning $folderName/$subfolder: $count songs found")
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
            Result.failure(Exception("Some folders failed to sync: $errors"))
        } else {
            Result.success(
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
    private suspend fun syncSingleFolder(folderId: String): Result<ScanOperationResult> {
        return repository.refreshFolder(folderId) { folderName, count ->
            Timber.d("Scanning $folderName: $count songs found")
        }
    }
}
