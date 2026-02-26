package com.xaozora.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import java.io.File

class MonitorService : Service() {

    companion object {
        var isServiceRunning = false
        private const val DAEMON_PATH = "/system/bin/autd"
        private const val CHANNEL_ID = "xAozoraService"
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (Intent.ACTION_SCREEN_ON == action || Intent.ACTION_USER_PRESENT == action) {
                checkAndStartDaemon()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        startForeground(1, createNotification())

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        checkAndStartDaemon()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, createNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered or already unregistered
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun checkAndStartDaemon() {
        Thread {
            if (isDaemonRunning()) return@Thread

            try {
                val f = File(DAEMON_PATH)
                if (!f.exists()) return@Thread

                val cmd = "$DAEMON_PATH > /dev/null 2>&1 &"
                Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun isDaemonRunning(): Boolean {
        return try {
            val cmd = "pidof autd > /dev/null || pgrep -x autd > /dev/null || ps -A | grep autd | grep -v grep > /dev/null"
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = p.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Aozora Monitor Service", NotificationManager.IMPORTANCE_MIN
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder.setContentTitle("Aozora Kernel Manager")
            .setContentText("Monitoring system state...")
            .setSmallIcon(android.R.drawable.ic_popup_sync).build()
    }
}