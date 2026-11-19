package com.dfmob.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlertsPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("dfmob_alerts_prefs", Context.MODE_PRIVATE)

    private val _seenAlertIds = MutableStateFlow<Set<String>>(loadSeenAlertIds())
    val seenAlertIds: StateFlow<Set<String>> = _seenAlertIds.asStateFlow()

    private fun loadSeenAlertIds(): Set<String> {
        return prefs.getStringSet("seen_alert_ids", emptySet()) ?: emptySet()
    }

    fun markAllAsSeen(alertIds: List<String>) {
        val updated = _seenAlertIds.value + alertIds
        prefs.edit().putStringSet("seen_alert_ids", updated).apply()
        _seenAlertIds.value = updated
    }

    fun hasUnseenAlerts(availableAlertIds: List<String>): Boolean {
        if (availableAlertIds.isEmpty()) return false
        val seen = _seenAlertIds.value
        return availableAlertIds.any { it !in seen }
    }

    fun getLastNotifiedAlertId(): String? {
        return prefs.getString("last_notified_alert_id", null)
    }

    fun setLastNotifiedAlertId(id: String) {
        prefs.edit().putString("last_notified_alert_id", id).apply()
    }
}
