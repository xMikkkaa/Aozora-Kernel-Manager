package com.xaozora.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import java.io.File

class PowerSaveReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) {
            try {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val file = File(context.filesDir, "autd_ps_state")
                file.writeText(if (pm.isPowerSaveMode) "1" else "0")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}