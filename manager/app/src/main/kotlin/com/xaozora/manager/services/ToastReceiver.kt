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
                .map { it.replace('\n', ' ').replace('\r', ' ').take(200) }
                .toList()
                .takeLast(3)
            if (!file.delete()) {
                RootShellHelper.executeCmd("rm -f ${file.absolutePath}")
            }
            val fallback = intent.getStringExtra("message")
                ?.replace('\n', ' ')
                ?.replace('\r', ' ')
                ?.trim()
                ?.take(200)
                ?.takeIf { it.isNotEmpty() }
            val messages = queued.ifEmpty {
                listOf(fallback ?: "Aozora Notification")
            }
            messages.forEach { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
        }
    }
}