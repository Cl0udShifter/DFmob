package com.dfmob.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dfmob_theme_prefs", Context.MODE_PRIVATE)
    private val keyThemeMode = "app_theme_mode"

    private val _themeMode = MutableStateFlow(loadInitialThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private fun loadInitialThemeMode(): AppThemeMode {
        val saved = prefs.getString(keyThemeMode, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(saved ?: AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(keyThemeMode, mode.name).apply()
        _themeMode.value = mode
    }

    fun getThemeMode(): AppThemeMode = _themeMode.value
}
