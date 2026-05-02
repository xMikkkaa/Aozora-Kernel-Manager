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

class MonitorService : Service() {

    companion object {
        var isServiceRunning = false
        private const val DAEMON_PATH = "/system/bin/autd"
        private const val CHANNEL_ID = "xAozoraService"
        private const val TAG = "MonitorService"
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
        Log.d(TAG, "Service onCreate")

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

                isServiceRunning = true
                startServiceForeground()

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service in foreground", e)
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
            if (isDaemonRunning()) {
                Log.d(TAG, "Daemon is already running, skipping startup to prevent double process")
                return@launch
            }
            try {
                Log.d(TAG, "Starting daemon: $DAEMON_PATH")
                val cmd = "$DAEMON_PATH > /dev/null 2>&1 &"
                RootShellHelper.executeCmd(cmd)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start daemon", e)
            }
        }
    }

    private fun isDaemonRunning(): Boolean {
        return try {
            val cmd = "pidof autd > /dev/null || pgrep -x autd > /dev/null || ps -A | grep autd | grep -v grep > /dev/null"
            RootShellHelper.executeCmd(cmd)
        } catch (e: Exception) {
            false
        }
    }

    private fun createNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "Aozora Monitor Service", NotificationManager.IMPORTANCE_MIN
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val builder = Notification.Builder(this, CHANNEL_ID)

        return builder.setContentTitle("Aozora Kernel Manager")
            .setContentText("Monitoring system state...")
            .setSmallIcon(android.R.drawable.ic_popup_sync).build()
    }

    private fun showNoAutdNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aozora Kernel Manager")
            .setContentText("AUTD Binary not installed. Service disabled.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
        
        nm.notify(1, builder.build())
    }
}
