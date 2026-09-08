/*
 * Copyright 2026 Aozora Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SessionService pattern adapted from chaldeaprjkt GameSpace (Apache-2.0)
 * via AxionAOSP fork (Apache-2.0):
 * https://github.com/chaldeaprjkt/packages_apps_GameSpace
 * https://github.com/AxionAOSP/android_packages_apps_GameSpace
 * DOJO_SHOW/HIDE foreground overlay rewritten in pure Compose.
 */
package com.xaozora.manager.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.xaozora.manager.MainActivity
import com.xaozora.manager.core.utils.DojoOverlayState
import com.xaozora.manager.ui.overlay.dojo.DojoOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DojoOverlayService : Service() {

    companion object {
        const val DOJO_SHOW = "com.xaozora.manager.DOJO_SHOW"
        const val DOJO_HIDE = "com.xaozora.manager.DOJO_HIDE"
        const val DOJO_EXTRA_KEHAI = "kehai"
        private const val CHANNEL_ID = "xDojoOverlayV1"
        private const val TAG = "DojoOverlayService"
        private const val NOTIF_ID = 887
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var windowManager: WindowManager? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var composeView: ComposeView? = null
    private var viewAttached = false
    private var overlayOwner: OverlayOwner? = null

    private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val savedController = SavedStateRegistryController.create(this)
        fun attachOwner() {
            savedController.performAttach()
            savedController.performRestore(null)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }
        fun detachOwner() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = savedController.savedStateRegistry
    }

    override fun onCreate() {
        super.onCreate()
        startOverlayForeground()
        val prefs = getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("dojo_kaikin", false)) return
        if (!Settings.canDrawOverlays(this)) return
        prepareOverlay()
        serviceScope.launch {
            DojoOverlayState.kehaiEvents.collect { kehai ->
                if (viewAttached) {
                    try {
                        windowManager?.updateViewLayout(composeView, overlayParams)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startOverlayForeground()
        when (intent?.action) {
            DOJO_SHOW -> {
                val prefs = getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)
                if (!prefs.getBoolean("dojo_kaikin", false)) return START_STICKY
                val kehaiJson = intent.getStringExtra(DOJO_EXTRA_KEHAI)
                if (!kehaiJson.isNullOrBlank()) {
                    val kehai = DojoOverlayState.parseKehai(kehaiJson)
                    if (kehai != null) DojoOverlayState.publishKehai(kehai)
                }
                showOverlay()
            }
            DOJO_HIDE -> {
                hideOverlay()
                DojoOverlayState.clearShiai()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideOverlay()
        try {
            overlayOwner?.detachOwner()
        } catch (_: Exception) {
        } finally {
            overlayOwner = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createOverlayParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }
    }

    private fun prepareOverlay() {
        if (composeView != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayParams = createOverlayParams()
        val owner = OverlayOwner()
        owner.attachOwner()
        overlayOwner = owner
        val view = ComposeView(this)
        view.setViewTreeLifecycleOwner(owner)
        view.setViewTreeSavedStateRegistryOwner(owner)
        view.setContent {
            val shiai by DojoOverlayState.shiai.collectAsState()
            var kaikin by remember { mutableStateOf(false) }
            var kehai by remember { mutableStateOf(false) }
            DojoOverlay(
                shiai = shiai,
                kaikin = kaikin,
                onKaikinChange = { kaikin = it },
                kehai = kehai,
                onKehaiChange = { kehai = it },
                onClose = {
                    hideOverlay()
                    DojoOverlayState.clearShiai()
                },
                onDrag = { dx, dy -> moveOverlay(dx, dy) }
            )
        }
        composeView = view
    }

    private fun moveOverlay(dx: Float, dy: Float) {
        val params = overlayParams ?: return
        params.x += dx.toInt()
        params.y += dy.toInt()
        if (!viewAttached) return
        try {
            windowManager?.updateViewLayout(composeView, params)
        } catch (_: Exception) {
        }
    }

    private fun showOverlay() {
        if (!Settings.canDrawOverlays(this)) return
        if (composeView == null) prepareOverlay()
        val wm = windowManager ?: return
        val view = composeView ?: return
        val params = overlayParams ?: return
        if (viewAttached) {
            try {
                wm.updateViewLayout(view, params)
            } catch (_: SecurityException) {
            } catch (_: android.view.WindowManager.BadTokenException) {
            } catch (_: Exception) {
            }
            return
        }
        try {
            wm.addView(view, params)
            viewAttached = true
        } catch (_: SecurityException) {
            viewAttached = false
        } catch (_: android.view.WindowManager.BadTokenException) {
            viewAttached = false
        } catch (_: Exception) {
            viewAttached = false
        }
    }

    private fun hideOverlay() {
        if (!viewAttached) return
        try {
            val wm = windowManager
            val view = composeView
            if (wm != null && view != null) {
                wm.removeViewImmediate(view)
            }
        } catch (_: Exception) {
        } finally {
            viewAttached = false
        }
    }

    private fun startOverlayForeground() {
        try {
            val notification = createOverlayNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start dojo foreground", e)
            try {
                startForeground(NOTIF_ID, createOverlayNotification())
            } catch (_: Exception) {
            }
        }
    }

    private fun createOverlayNotification(): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID, "Dojo Overlay Service", NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Dojo Overlay")
            .setContentText("Game overlay standby...")
            .setContentIntent(pendingIntent)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .build()
    }
}
