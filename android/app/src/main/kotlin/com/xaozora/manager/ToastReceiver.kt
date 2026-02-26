package com.xaozora.manager

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ToastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if ("com.xaozora.manager.NOTIFY" == intent.action) {
            var pesan = intent.getStringExtra("message")
            if (pesan == null) pesan = "Aozora Notification"
            Toast.makeText(context, pesan, Toast.LENGTH_SHORT).show()
        }
    }
}