package com.xaozora.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
class BootReceiver : BroadcastReceiver() {
    @Suppress("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}