package com.xaozora.manager.services

import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.WindowManager
import com.xaozora.manager.core.shell.RootShellHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ProfileTileService : TileService() {

    private val profiles = listOf(
        Pair("powersave", "Power Save"),
        Pair("balance", "Balance"),
        Pair("gaming", "Gaming"),
        Pair("gaming2", "Gaming 2"),
        Pair("performance", "Performance")
    )

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        
        CoroutineScope(Dispatchers.IO).launch {
            val prefs = getSharedPreferences("aozora_prefs", MODE_PRIVATE)
            val autdEnabled = prefs.getBoolean("autd_enabled", true)
            val autdExists = RootShellHelper.checkFileExists("${filesDir.path}/xaozora_daemon")
            
            val helperBindMounted = RootShellHelper.checkFileExists("/system/bin/balance")
            val isAvailable = autdEnabled && autdExists && helperBindMounted
            
            withContext(Dispatchers.Main) {
                if (isAvailable) {
                    tile.state = Tile.STATE_ACTIVE
                    try {
                        val statusFile = File("/data/data/com.xaozora.manager/files/autd/autd_status")
                        if (statusFile.exists() && statusFile.canRead()) {
                            val activeId = statusFile.readText().trim()
                            val activeProfile = profiles.find { it.first == activeId }
                            tile.subtitle = activeProfile?.second ?: activeId
                        } else {
                            tile.subtitle = "Active"
                        }
                    } catch (e: Exception) {
                        tile.subtitle = "Active"
                    }
                } else {
                    tile.state = Tile.STATE_UNAVAILABLE
                    tile.subtitle = "Unavailable"
                }
                tile.updateTile()
            }
        }
    }

    override fun onClick() {
        super.onClick()
        val tile = qsTile ?: return
        
        if (tile.state == Tile.STATE_UNAVAILABLE) {
            return
        }

        showProfileDialog()
    }

    private fun showProfileDialog() {
        CoroutineScope(Dispatchers.IO).launch {
            val availableProfiles = profiles.filter {
                if (it.first == "gaming" || it.first == "gaming2") {
                    RootShellHelper.checkFileExists("/system/bin/${it.first}")
                } else {
                    true
                }
            }

            withContext(Dispatchers.Main) {
                val themedContext = android.view.ContextThemeWrapper(this@ProfileTileService, android.R.style.Theme_DeviceDefault_DayNight)
                val dialogView = android.view.LayoutInflater.from(themedContext).inflate(com.xaozora.manager.R.layout.layout_profile_dialog, null)
                
                val builder = android.app.AlertDialog.Builder(themedContext)
                builder.setView(dialogView)
                val dialog = builder.create()

                var currentId = "balance"
                try {
                    val statusFile = File("/data/data/com.xaozora.manager/files/autd/autd_status")
                    if (statusFile.exists() && statusFile.canRead()) {
                        currentId = statusFile.readText().trim()
                    }
                } catch (e: Exception) {}

                val primaryColor = try { 
                    getColor(android.R.color.system_accent1_500) 
                } catch(e: Exception) { 
                    val typedValue = android.util.TypedValue()
                    themedContext.theme.resolveAttribute(android.R.attr.colorAccent, typedValue, true)
                    typedValue.data
                }
                
                val tertiaryColor = try { 
                    getColor(android.R.color.system_accent3_500) 
                } catch(e: Exception) { 
                    android.graphics.Color.parseColor("#888888")
                }

                val setupButton = { layoutId: Int, iconId: Int, textId: Int, profileId: String ->
                    val layout = dialogView.findViewById<android.view.View>(layoutId)
                    if (availableProfiles.any { it.first == profileId }) {
                        val icon = dialogView.findViewById<android.widget.ImageView>(iconId)
                        val text = dialogView.findViewById<android.widget.TextView>(textId)
                        
                        if (currentId == profileId) {
                            icon.setColorFilter(primaryColor)
                            text.setTextColor(primaryColor)
                            text.setTypeface(null, android.graphics.Typeface.BOLD)
                        } else {
                            icon.setColorFilter(tertiaryColor)
                            text.setTextColor(tertiaryColor)
                            text.setTypeface(null, android.graphics.Typeface.NORMAL)
                        }
                        
                        layout.setOnClickListener {
                            applyProfile(profileId)
                            dialog.dismiss()
                        }
                    } else {
                        layout.visibility = android.view.View.GONE
                    }
                }

                setupButton(com.xaozora.manager.R.id.btnPowerSave, com.xaozora.manager.R.id.icPowerSave, com.xaozora.manager.R.id.tvPowerSave, "powersave")
                setupButton(com.xaozora.manager.R.id.btnBalance, com.xaozora.manager.R.id.icBalance, com.xaozora.manager.R.id.tvBalance, "balance")
                setupButton(com.xaozora.manager.R.id.btnGaming, com.xaozora.manager.R.id.icGaming, com.xaozora.manager.R.id.tvGaming, "gaming")
                setupButton(com.xaozora.manager.R.id.btnGaming2, com.xaozora.manager.R.id.icGaming2, com.xaozora.manager.R.id.tvGaming2, "gaming2")
                setupButton(com.xaozora.manager.R.id.btnPerformance, com.xaozora.manager.R.id.icPerformance, com.xaozora.manager.R.id.tvPerformance, "performance")

                dialog.window?.let { window ->
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                }

                val typedValueBg = android.util.TypedValue()
                themedContext.theme.resolveAttribute(android.R.attr.colorBackgroundFloating, typedValueBg, true)
                val bgColor = typedValueBg.data
                
                val bgDrawable = android.graphics.drawable.GradientDrawable()
                bgDrawable.cornerRadius = 64f
                bgDrawable.setColor(bgColor)
                dialogView.findViewById<android.view.View>(com.xaozora.manager.R.id.dialogBackgroundContainer).background = bgDrawable

                showDialog(dialog)
            }
        }
    }

    private fun applyProfile(profileId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val autdDir = "/data/data/com.xaozora.manager/files/autd"
                val writeCmd = "rm -f $autdDir/autd_base_mode; echo -n '$profileId' > $autdDir/autd_base_mode"
                
                if (RootShellHelper.executeCmd(writeCmd)) {
                    android.util.Log.d("ProfileTileService", "Successfully wrote profile $profileId via root")
                } else {
                    android.util.Log.e("ProfileTileService", "Failed to write profile via root")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            updateTileState()
        }
    }
}
