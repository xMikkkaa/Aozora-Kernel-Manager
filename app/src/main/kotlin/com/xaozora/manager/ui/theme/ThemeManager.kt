package com.xaozora.manager.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode { AUTO, LIGHT, DARK }

class ThemeManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aozora_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(
        AppThemeMode.entries[prefs.getInt("themeMode", AppThemeMode.AUTO.ordinal)]
    )
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putInt("themeMode", mode.ordinal).apply()
        _themeMode.value = mode
    }
}

val LocalThemeManager = compositionLocalOf<ThemeManager> { error("ThemeManager not provided") }