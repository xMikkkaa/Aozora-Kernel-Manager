package com.xaozora.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            try {
                val request = OneTimeWorkRequestBuilder<BootWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "aozora-boot-worker", 
                    ExistingWorkPolicy.KEEP, 
                    request
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}