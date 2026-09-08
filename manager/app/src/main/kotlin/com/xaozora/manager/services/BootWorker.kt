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
        private const val CHANNEL_ID = "xAozoraBootV2"
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
            val prefs = appContext.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
            val isMonitorEnabled = prefs.getBoolean("battery_monitor_service", true)
            val isAutdEnabled = prefs.getBoolean("autd_enabled", true)
            
            if (isMonitorEnabled || isAutdEnabled) {
                Log.d(TAG, "Starting MonitorService on boot")
                val serviceIntent = Intent(appContext, MonitorService::class.java)
                appContext.startForegroundService(serviceIntent)
            } else {
                Log.d(TAG, "Service and AUTD disabled, not starting")
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
        val channel = NotificationChannel(CHANNEL_ID, "Aozora Boot", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val builder = Notification.Builder(appContext, CHANNEL_ID)

        val notification = builder.setContentTitle("Aozora Kernel Manager")
            .setContentText("Waiting for Root Environment...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        val type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }
}