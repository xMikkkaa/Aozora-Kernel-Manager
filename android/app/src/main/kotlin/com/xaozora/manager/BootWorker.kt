package com.xaozora.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import java.io.File
import kotlinx.coroutines.delay

class BootWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val NOTIFICATION_ID = 999
        private const val CHANNEL_ID = "xAozoraBoot"
    }

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo())

        delay(7000L)

        try {
            val check = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", "[ -f /system/bin/autd ]"))
            if (check.waitFor() == 0) {
                val serviceIntent = Intent(appContext, MonitorService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(serviceIntent)
                } else {
                    appContext.startService(serviceIntent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }

        return Result.success()
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Aozora Boot", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(appContext, CHANNEL_ID)
        } else {
            Notification.Builder(appContext)
        }

        val notification = builder.setContentTitle("Aozora Kernel Manager")
            .setContentText("Waiting for Root Environment...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}