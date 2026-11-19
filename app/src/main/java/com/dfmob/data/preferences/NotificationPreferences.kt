package com.dfmob.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationSettings(
    val descentAdvanceStops: Int = 2,
    val notify15Min: Boolean = true,
    val notify10Min: Boolean = true,
    val notify5Min: Boolean = true,
    val notify2Min: Boolean = true
)

class NotificationPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dfmob_notification_prefs", Context.MODE_PRIVATE)

    private val keyDescentStops = "descent_advance_stops"
    private val keyNotify15 = "notify_15_min"
    private val keyNotify10 = "notify_10_min"
    private val keyNotify5 = "notify_5_min"
    private val keyNotify2 = "notify_2_min"

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<NotificationSettings> = _settings.asStateFlow()

    private fun loadSettings(): NotificationSettings {
        return NotificationSettings(
            descentAdvanceStops = prefs.getInt(keyDescentStops, 2).coerceIn(1, 3),
            notify15Min = prefs.getBoolean(keyNotify15, true),
            notify10Min = prefs.getBoolean(keyNotify10, true),
            notify5Min = prefs.getBoolean(keyNotify5, true),
            notify2Min = prefs.getBoolean(keyNotify2, true)
        )
    }

    fun setDescentAdvanceStops(stops: Int) {
        val clamped = stops.coerceIn(1, 3)
        prefs.edit().putInt(keyDescentStops, clamped).apply()
        _settings.value = _settings.value.copy(descentAdvanceStops = clamped)
    }

    fun setNotify15Min(enabled: Boolean): Boolean {
        if (!enabled && isOnlyOneActive()) return false
        prefs.edit().putBoolean(keyNotify15, enabled).apply()
        _settings.value = _settings.value.copy(notify15Min = enabled)
        return true
    }

    fun setNotify10Min(enabled: Boolean): Boolean {
        if (!enabled && isOnlyOneActive()) return false
        prefs.edit().putBoolean(keyNotify10, enabled).apply()
        _settings.value = _settings.value.copy(notify10Min = enabled)
        return true
    }

    fun setNotify5Min(enabled: Boolean): Boolean {
        if (!enabled && isOnlyOneActive()) return false
        prefs.edit().putBoolean(keyNotify5, enabled).apply()
        _settings.value = _settings.value.copy(notify5Min = enabled)
        return true
    }

    fun setNotify2Min(enabled: Boolean): Boolean {
        if (!enabled && isOnlyOneActive()) return false
        prefs.edit().putBoolean(keyNotify2, enabled).apply()
        _settings.value = _settings.value.copy(notify2Min = enabled)
        return true
    }

    private fun isOnlyOneActive(): Boolean {
        val s = _settings.value
        val activeCount = (if (s.notify15Min) 1 else 0) +
                (if (s.notify10Min) 1 else 0) +
                (if (s.notify5Min) 1 else 0) +
                (if (s.notify2Min) 1 else 0)
        return activeCount <= 1
    }

    fun getSettings(): NotificationSettings = _settings.value

    fun isMilestoneEnabled(milestone: Int): Boolean {
        val s = getSettings()
        return when (milestone) {
            15 -> s.notify15Min
            10 -> s.notify10Min
            5 -> s.notify5Min
            2 -> s.notify2Min
            else -> true
        }
    }

    fun getDescentDistanceMeters(): Float {
        return when (getSettings().descentAdvanceStops) {
            1 -> 250f
            2 -> 400f
            3 -> 600f
            else -> 400f
        }
    }
}
