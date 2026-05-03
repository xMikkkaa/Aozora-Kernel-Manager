package com.xaozora.manager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.xaozora.manager.core.shell.RootShellHelper
import kotlinx.coroutines.delay

class BootWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val NOTIFICATION_ID = 999
        private const val CHANNEL_ID = "xAozoraBoot"
        private const val TAG = "BootWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "BootWorker started")
        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set foreground", e)
        }

        delay(3000L)

        try {
            if (RootShellHelper.checkFileExists("/system/bin/autd")) {
                Log.d(TAG, "autd found, starting MonitorService")
                val serviceIntent = Intent(appContext, MonitorService::class.java)
                appContext.startForegroundService(serviceIntent)
            } else {
                Log.w(TAG, "autd not found during boot, stopping")
                updateNotificationNoAutd()
                delay(5000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in BootWorker", e)
            return Result.failure()
        }

        return Result.success()
    }

    private fun updateNotificationNoAutd() {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = Notification.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Aozora Kernel Manager")
            .setContentText("AUTD Binary not found. Service stopped.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)

        nm.notify(NOTIFICATION_ID, builder.build())
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Aozora Boot", NotificationManager.IMPORTANCE_LOW)
        nm.createNotificationChannel(channel)

        val builder = Notification.Builder(appContext, CHANNEL_ID)

        val notification = builder.setContentTitle("Aozora Kernel Manager")
            .setContentText("Waiting for Root Environment...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}