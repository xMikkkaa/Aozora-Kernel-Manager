package com.xaozora.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MonitorService : Service() {

    companion object {
        var isServiceRunning = false
        private const val DAEMON_PATH = "/system/bin/autd"
        private const val CHANNEL_ID = "xAozoraService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var powerObserver: ContentObserver? = null

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
            registerReceiver(screenReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }

        val uri = Settings.Global.getUriFor("low_power")
        powerObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                updatePowerSaveState()
            }
        }
        contentResolver.registerContentObserver(uri, false, powerObserver!!)

        checkAndStartDaemon()
        updatePowerSaveState()
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
        } catch (e: Exception) {}
        
        powerObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun updatePowerSaveState() {
        try {
            val isPowerSave = Settings.Global.getInt(contentResolver, "low_power", 0) == 1
            val file = File(filesDir, "autd_ps_state")
            file.writeText(if (isPowerSave) "1" else "0")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndStartDaemon() {
        serviceScope.launch {
            if (isDaemonRunning()) return@launch
            try {
                val f = File(DAEMON_PATH)
                if (!f.exists()) return@launch

                val cmd = "$DAEMON_PATH > /dev/null 2>&1 &"
                Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", cmd))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isDaemonRunning(): Boolean {
        return try {
            val cmd = "pidof autd > /dev/null || pgrep -x autd > /dev/null || ps -A | grep autd | grep -v grep > /dev/null"
            val p = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", cmd))
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