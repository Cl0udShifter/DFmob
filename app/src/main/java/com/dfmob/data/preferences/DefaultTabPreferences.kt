package com.dfmob.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DefaultAppTab {
    HOME,
    STOPS,
    LINES,
    TRIP_PLANNER
}

class DefaultTabPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dfmob_default_tab_prefs", Context.MODE_PRIVATE)
    private val keyDefaultTab = "default_app_tab"

    private val _defaultTab = MutableStateFlow(loadInitialDefaultTab())
    val defaultTab: StateFlow<DefaultAppTab> = _defaultTab.asStateFlow()

    private fun loadInitialDefaultTab(): DefaultAppTab {
        val saved = prefs.getString(keyDefaultTab, DefaultAppTab.HOME.name)
        return try {
            DefaultAppTab.valueOf(saved ?: DefaultAppTab.HOME.name)
        } catch (_: Exception) {
            DefaultAppTab.HOME
        }
    }

    fun setDefaultTab(tab: DefaultAppTab) {
        prefs.edit().putString(keyDefaultTab, tab.name).apply()
        _defaultTab.value = tab
    }

    fun getDefaultTab(): DefaultAppTab = _defaultTab.value
}
