package com.xaozora.manager.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    companion object {
        var bootCompleted: Boolean = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (bootCompleted) return
        
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED) {
            bootCompleted = true
            try {
                val request = OneTimeWorkRequestBuilder<BootWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "aozora-boot-worker",
                    ExistingWorkPolicy.REPLACE,
                    request
                )

                val prefs = context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
                val isMonitorEnabled = prefs.getBoolean("battery_monitor_service", true)
                val isAutdEnabled = prefs.getBoolean("autd_enabled", true)

                if (isMonitorEnabled || isAutdEnabled) {
                    val serviceIntent = Intent(context, MonitorService::class.java).apply {
                        putExtra("from_boot", true)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}