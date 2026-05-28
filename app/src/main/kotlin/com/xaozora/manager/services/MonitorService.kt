package com.xaozora.manager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.provider.Settings
import android.util.Log
import com.google.gson.Gson
import com.xaozora.manager.MainActivity
import com.xaozora.manager.core.models.BatteryStats
import com.xaozora.manager.core.shell.RootShellHelper
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MonitorService : Service() {

    companion object {
        var isServiceRunning = false
        private const val CHANNEL_ID = "xAozoraServiceV2"
        private const val TAG = "MonitorService"
        private var wasCharging = false
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var powerObserver: ContentObserver? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (Intent.ACTION_SCREEN_ON == action || Intent.ACTION_USER_PRESENT == action) {
                serviceScope.launch {
                    if (!com.xaozora.manager.core.utils.NativeDaemonManager.isDaemonRunning()) {
                        Log.d(TAG, "Daemon was dead on screen on, restarting...")
                        checkAndStartDaemon()
                    }
                }
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

                updatePowerSaveState()

                val prefs = getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("battery_reset_reboot", false)) {
                    launch(Dispatchers.IO) {
                        RootShellHelper.executeCmd("dumpsys batterystats --reset")
                        val statsFile = File(filesDir, "battmon/battery_stats.json")
                        if (statsFile.exists()) statsFile.writeText("{}")
                        Log.d(TAG, "Battery stats reset on boot")
                    }
                }

                startBatteryMonitorLoop()

                val currentBatteryStatus: Intent? = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val currentPlugged = currentBatteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
                wasCharging = currentPlugged > 0
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
            val dir = java.io.File(filesDir, "autd")
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, "autd_ps_state")
            file.writeText(if (isPowerSave) "1" else "0")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkAndStartDaemon() {
        serviceScope.launch {
            try {
                com.xaozora.manager.core.utils.NativeDaemonManager.extractAndStartDaemon(this@MonitorService)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check/start daemon", e)
            }
        }
    }


    private fun createNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Aozora Monitor Service", NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = Notification.Builder(this, CHANNEL_ID)

        return builder.setContentTitle("Aozora Kernel Manager")
            .setContentText("Monitoring system state...")
            .setContentIntent(pendingIntent)
            .setSmallIcon(android.R.drawable.ic_popup_sync).build()
    }

    private fun showNoDaemonNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Aozora Kernel Manager")
            .setContentText("Native daemon not installed. Battery monitoring may not work.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        
        nm.notify(889, builder.build())
    }

    private fun startBatteryMonitorLoop() {
        serviceScope.launch {
            val prefs = getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            while (isServiceRunning) {
                val isMonitorEnabled = prefs.getBoolean("battery_monitor_service", true)
                if (isMonitorEnabled) {
                    updateBatteryNotification(nm, prefs)
                } else {
                    nm.notify(888, createNotification())
                }
                delay(5000L)
            }
        }
    }

    private fun updateBatteryNotification(nm: NotificationManager, prefs: android.content.SharedPreferences) {
        try {
            val showPercentIcon = prefs.getBoolean("battery_show_notif_icon", false)
            val showWattageIcon = prefs.getBoolean("battery_show_wattage", false)
            val tempUnit = prefs.getString("battery_temp_unit", "C") ?: "C"

            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { ifilter ->
                registerReceiver(null, ifilter)
            }
            
            val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1
            
            val tempDeciC = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val tempC = tempDeciC / 10.0
            val tempStr = if (tempUnit == "F") {
                String.format("%.1f°F", (tempC * 9/5) + 32)
            } else {
                String.format("%.1f°C", tempC)
            }
            
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: 0
            val isPlugged = plugged > 0
            val isFullyCharged = isPlugged && (batteryPct == 100 || status == BatteryManager.BATTERY_STATUS_FULL)

            if (isPlugged && !wasCharging) {
                if (prefs.getBoolean("battery_reset_plugged_in", false)) {
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            RootShellHelper.executeCmd("dumpsys batterystats --reset")
                            
                            val battmonDir = java.io.File(filesDir, "battmon")
                            val batteryLogPath = java.io.File(battmonDir, "battery_logger.jsonl").absolutePath
                            
                            val autdArg = if (prefs.getBoolean("autd_enabled", true)) "--enable-autd" else "--disable-autd"
                            val battmonArg = if (prefs.getBoolean("battery_monitor_service", true)) "--battery-logger $batteryLogPath" else ""
                            RootShellHelper.executeCmd("cd ${filesDir.absolutePath} && nohup ./xaozora_daemon $autdArg --reset-stats $battmonArg > /dev/null 2>&1 &")
                            
                            Log.d(TAG, "Battery stats reset via daemon on plug-in")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to reset stats on plug", e)
                        }
                    }
                }
            }
            wasCharging = isPlugged

            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val currentNowMicroAmps = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val currentMa = Math.abs(currentNowMicroAmps / 1000)
            
            val voltageMv = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
            val voltageV = voltageMv / 1000f
            val watts = (currentMa / 1000f) * voltageV
            
            val titleStr = if (isFullyCharged) {
                "$batteryPct% • $tempStr • Fully charged"
            } else {
                val stateStr = if (isPlugged) "Charging" else "Discharging"
                val extraInfo = if (showWattageIcon) {
                    if (isPlugged) " ${currentMa}mA (${String.format(java.util.Locale.US, "%.1f", watts)}W)" else " ${currentMa}mA"
                } else {
                    ""
                }
                "$batteryPct% • $tempStr • $stateStr$extraInfo"
            }

            val iconBitmap = if (showPercentIcon) {
                createIconBitmap("$batteryPct")
            } else {
                null
            }

            var activeDrain = "0.0 %/h"
            var idleDrain = "0.0 %/h"
            var screenOn = "0s"
            var screenOff = "0s"
            var deepSleep = "0s"
            var awake = "0s"

            try {
                val path = "$filesDir/battmon/battery_stats.json"
                val file = java.io.File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    if (content.isNotBlank()) {
                    val stats = Gson().fromJson(content, BatteryStats::class.java)
                    activeDrain = String.format(java.util.Locale.US, "%.2f", stats.activeDrainRatePerHr)
                    idleDrain = String.format(java.util.Locale.US, "%.2f", stats.idleDrainRatePerHr)
                    
                    var capacity = stats.lastLearnedCapacityMah
                    if (capacity <= 0.0) {
                        try {
                            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
                            val mPowerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(this@MonitorService)
                            capacity = powerProfileClass.getMethod("getBatteryCapacity").invoke(mPowerProfile) as Double
                        } catch (e: Exception) {
                            capacity = 4000.0
                        }
                    }

                    fun formatDrain(durationMs: Long, drainMah: Double): String {
                        val duration = formatDuration(durationMs)
                        val pct = if (capacity > 0.0 && drainMah > 0.0) (drainMah / capacity) * 100 else 0.0
                        return "$duration (${String.format(java.util.Locale.US, "%.1f", pct)}%)"
                    }

                    screenOn = formatDrain(stats.screenOnDurationMs, stats.screenOnDischargeMah)
                    screenOff = formatDrain(stats.timeOnBatteryScreenOffMs, stats.screenOffDischargeMah)
                    
                    val screenOffDuration = stats.timeOnBatteryScreenOffMs
                    val dsPct = if (screenOffDuration > 0) (stats.deepSleepMs.toFloat() / screenOffDuration * 100).coerceIn(0f, 100f) else 0f
                    deepSleep = "${formatDuration(stats.deepSleepMs)} (${String.format(java.util.Locale.US, "%.1f", dsPct)}%)"
                    
                    val awakePct = if (screenOffDuration > 0) (stats.awakeScreenOffMs.toFloat() / screenOffDuration * 100).coerceIn(0f, 100f) else 0f
                    awake = "${formatDuration(stats.awakeScreenOffMs)} (${String.format(java.util.Locale.US, "%.1f", awakePct)}%)"
                }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val inboxStyle = Notification.InboxStyle()
                .addLine("Active drain: $activeDrain %/h • idle drain $idleDrain %/h")
                .addLine("Screen on $screenOn")
                .addLine("Screen off $screenOff")
                .addLine("Deep Sleep $deepSleep")
                .addLine("Awake $awake")

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

            val builder = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(titleStr)
                .setStyle(inboxStyle)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
            
            if (iconBitmap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setSmallIcon(Icon.createWithBitmap(iconBitmap))
            } else {
                builder.setSmallIcon(com.xaozora.manager.R.drawable.kai)
            }

            nm.notify(888, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createIconBitmap(text: String): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centerX = canvas.width / 2f

        if (text.endsWith("mA")) {
            val numText = text.removeSuffix("mA")
            val paintNum = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 46f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val paintMa = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 32f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val numY = (canvas.height / 2f) + 2f
            canvas.drawText(numText, centerX, numY, paintNum)
            val maY = (canvas.height / 2f) + paintMa.textSize + 2f
            canvas.drawText("mA", centerX, maY, paintMa)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = if (text.length > 3) 28f else 36f
                textAlign = Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val yPos = (canvas.height / 2f) - ((paint.descent() + paint.ascent()) / 2f)
            canvas.drawText(text, centerX, yPos, paint)
        }
        return bitmap
    }

    private fun formatDuration(ms: Long): String {
        if (ms <= 0) return "0s"
        val totalSecs = ms / 1000
        val h = totalSecs / 3600
        val m = (totalSecs % 3600) / 60
        val s = totalSecs % 60
        
        return buildString {
            if (h > 0) append("${h}h ")
            if (m > 0 || h > 0) append("${m}m ")
            append("${s}s")
        }.trim()
    }
}
