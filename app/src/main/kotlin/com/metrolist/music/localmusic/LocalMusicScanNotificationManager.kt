/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.localmusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.WorkManager
import com.metrolist.music.R

/**
 * Manages notifications for local music scanning operations.
 * Provides persistent progress notifications and completion/error notifications.
 */
class LocalMusicScanNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    companion object {
        const val CHANNEL_ID = "local_music_scan"
        const val NOTIFICATION_ID = 1002
        
        // Notification actions
        const val ACTION_CANCEL_SCAN = "com.metrolist.music.ACTION_CANCEL_SCAN"
        const val EXTRA_WORK_ID = "work_id"
    }

    init {
        createNotificationChannel()
    }

    /**
     * Creates the notification channel for local music scanning.
     */
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_local_music_scan),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_local_music_scan_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Creates a foreground notification for scanning progress.
     * 
     * @param folderName Name of the folder being scanned
     * @param subfolderName Name of the current subfolder (if applicable)
     * @param songsFound Number of songs found so far
     * @param workId WorkManager work ID for cancellation
     * @return Notification that can be used with setForeground()
     */
    fun createScanningNotification(
        folderName: String,
        subfolderName: String = "",
        songsFound: Int = 0,
        workId: String? = null
    ): android.app.Notification {
        val contentText = if (subfolderName.isNotEmpty()) {
            context.getString(
                R.string.notification_scanning_progress,
                folderName,
                subfolderName,
                songsFound
            )
        } else {
            context.getString(
                R.string.notification_scanning_progress_folder,
                folderName,
                songsFound
            )
        }

        // Create intent to open LocalMusicSettings when notification is tapped
        val tapIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "local_music_settings")
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_scanning_local_music))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.library_music)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setProgress(0, 0, true) // Indeterminate progress
            .setContentIntent(tapPendingIntent)

        // Add cancel action if workId is provided
        workId?.let {
            val cancelIntent = Intent(ACTION_CANCEL_SCAN).apply {
                putExtra(EXTRA_WORK_ID, it)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                NOTIFICATION_ID,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.close,
                context.getString(R.string.notification_scan_cancel),
                cancelPendingIntent
            )
        }

        return builder.build()
    }

    /**
     * Shows a notification indicating folders are queued for scanning.
     * 
     * @param queuedCount Number of folders in the queue
     */
    fun showQueuedNotification(queuedCount: Int) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_scanning_local_music))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.notification_scan_queued,
                    queuedCount,
                    queuedCount
                )
            )
            .setSmallIcon(R.drawable.library_music)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(0, 0, true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Shows a completion notification with scan results.
     * 
     * @param songsAdded Number of songs added
     * @param songsUpdated Number of songs updated
     * @param playlistsCreated Number of playlists created
     */
    fun showCompletionNotification(
        songsAdded: Int,
        songsUpdated: Int,
        playlistsCreated: Int
    ) {
        val contentText = context.getString(
            R.string.notification_scan_complete,
            songsAdded,
            playlistsCreated
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_scan_complete_title))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.library_music)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Shows an error notification when scanning fails.
     * 
     * @param errorMessage The error message to display
     */
    fun showErrorNotification(errorMessage: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notification_scan_error))
            .setContentText(errorMessage)
            .setSmallIcon(R.drawable.library_music)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * Cancels the scanning notification.
     */
    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Handles cancel action from notification.
     * 
     * @param workId The WorkManager work ID to cancel
     */
    fun handleCancelAction(workId: String) {
        WorkManager.getInstance(context).cancelWorkById(java.util.UUID.fromString(workId))
        cancelNotification()
    }
}
