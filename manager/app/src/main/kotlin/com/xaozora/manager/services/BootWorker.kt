package com.xaozora.manager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.xaozora.manager.core.shell.RootShellHelper
import com.xaozora.manager.core.utils.NativeDaemonManager
import kotlinx.coroutines.delay

class BootWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val NOTIFICATION_ID = 999
        private const val CHANNEL_ID = "xAozoraBootV3"
        private const val TAG = "BootWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "BootWorker started")
        try {
            setForeground(createForegroundInfo("Waiting for Root Environment..."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set foreground", e)
        }

        delay(2500L)

        try {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification("Starting Native Daemon..."))

            NativeDaemonManager.extractAndStartDaemon(appContext)

            val prefs = appContext.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
            val isMonitorEnabled = prefs.getBoolean("battery_monitor_service", true)
            val isAutdEnabled = prefs.getBoolean("autd_enabled", true)

            if (isMonitorEnabled || isAutdEnabled) {
                RootShellHelper.executeCmd("am start-foreground-service -n com.xaozora.manager/.services.MonitorService")
                try {
                    val serviceIntent = Intent(appContext, MonitorService::class.java).apply {
                        putExtra("from_boot", true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(serviceIntent)
                    } else {
                        appContext.startService(serviceIntent)
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in BootWorker", e)
            return Result.failure()
        }

        return Result.success()
    }

    private fun buildNotification(text: String): Notification {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(CHANNEL_ID, "Aozora Boot", NotificationManager.IMPORTANCE_LOW).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        return Notification.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Aozora Kernel Manager")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
    }

    private fun createForegroundInfo(text: String): ForegroundInfo {
        val notification = buildNotification(text)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        return ForegroundInfo(NOTIFICATION_ID, notification, type)
    }
}
