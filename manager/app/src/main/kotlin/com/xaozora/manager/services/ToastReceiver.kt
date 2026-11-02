package com.xaozora.manager.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.xaozora.manager.core.shell.RootShellHelper

class ToastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ("com.xaozora.manager.NOTIFY" == intent.action) {
            if (MonitorService.isServiceRunning) return
            val file = java.io.File(context.filesDir, "autd/toast_queue")
            val queued = runCatching { file.readText() }.getOrNull()
                .orEmpty()
                .lineSequence()
                .filter { it.isNotBlank() }
                .toList()
                .takeLast(3)
            if (!file.delete()) {
                RootShellHelper.executeCmd("rm -f ${file.absolutePath}")
            }
            val messages = queued.ifEmpty {
                listOf(intent.getStringExtra("message") ?: "Aozora Notification")
            }
            messages.forEach { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }
}