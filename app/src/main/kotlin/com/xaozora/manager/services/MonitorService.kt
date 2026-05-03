package com.xaozora.manager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.xaozora.manager.core.shell.RootShellHelper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MonitorService : Service() {

    companion object {
        var isServiceRunning = false
        private const val DAEMON_PATH = "/system/bin/autd"
        private const val CHANNEL_ID = "xAozoraService"
        private const val TAG = "MonitorService"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val daemonMutex = Mutex()

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
        Log.d(TAG, "Service onCreate")
        
        isServiceRunning = true
        startServiceForeground()

        serviceScope.launch {
            try {
                val exists = RootShellHelper.checkFileExists(DAEMON_PATH)
                if (!exists) {
                    Log.w(TAG, "Daemon not found at $DAEMON_PATH, notifying and stopping service")
                    showNoAutdNotification()
                    isServiceRunning = false
                    delay(5000L)
                    stopSelf()
                    return@launch
                }

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
            } catch (e: Exception) {
                Log.e(TAG, "Error in Service onCreate scope", e)
                stopSelf()
            }
        }
    }

    private fun startServiceForeground() {
        try {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(888, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(888, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service in foreground", e)
            try {
                startForeground(888, createNotification())
            } catch (e2: Exception) {
                Log.e(TAG, "Critical: Could not start foreground at all", e2)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        startServiceForeground()
        checkAndStartDaemon()
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
            daemonMutex.withLock {
                if (isDaemonRunning()) {
                    Log.d(TAG, "Daemon is already running, skipping startup")
                    return@withLock
                }
                try {
                    Log.d(TAG, "Starting daemon: $DAEMON_PATH")
                    val cmd = "export PATH=\$PATH:/system/bin:/system/xbin; $DAEMON_PATH > /dev/null 2>&1 &"
                    if (RootShellHelper.executeCmd(cmd)) {
                        delay(1500L)
                        Log.d(TAG, "Daemon start command executed, waited for stabilization")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start daemon", e)
                }
            }
        }
    }

    private fun isDaemonRunning(): Boolean {
        return try {
            val cmd = "pgrep -x autd"
            val output = RootShellHelper.executeCmdAndGetOutput(cmd)
            output.isNotBlank() && (output.toLongOrNull() != null)
        } catch (_: Exception) {
            try {
                val cmdFallback = "pidof autd"
                RootShellHelper.executeCmdAndGetOutput(cmdFallback).isNotBlank()
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun createNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Aozora Monitor Service", NotificationManager.IMPORTANCE_LOW
        )
        nm.createNotificationChannel(channel)

        val builder = Notification.Builder(this, CHANNEL_ID)

        return builder.setContentTitle("Aozora Kernel Manager")
            .setContentText("Monitoring system state...")
            .setSmallIcon(android.R.drawable.ic_popup_sync).build()
    }

    private fun showNoAutdNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aozora Kernel Manager")
            .setContentText("AUTD Binary not installed. Service disabled.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
        
        nm.notify(889, builder.build())
    }
}
