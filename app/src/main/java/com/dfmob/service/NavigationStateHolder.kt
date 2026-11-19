package com.dfmob.service

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray

object NavigationStateHolder {
    val isNavigating = MutableStateFlow(false)
    val isDescentAlarmTriggered = MutableStateFlow(false)
    val currentRemainingStops = MutableStateFlow(0)
    val currentDistToDescentMeters = MutableStateFlow(0f)

    var targetStopName: String = ""
    var targetStopLat: Double = 0.0
    var targetStopLon: Double = 0.0
    var intermediateStops: List<Pair<Double, Double>> = emptyList()
    var totalStopsCount: Int = 0
    var isRideTransit: Boolean = false
    var hasAlertedCurrentLeg: Boolean = false
    var lastPassedStopIndex: Int = -1

    fun save(context: Context) {
        val prefs = context.getSharedPreferences("dfmob_navigation_state_prefs", Context.MODE_PRIVATE)
        val arr = JSONArray()
        intermediateStops.forEach { (lat, lon) ->
            arr.put("$lat,$lon")
        }
        prefs.edit()
            .putBoolean("isNavigating", isNavigating.value)
            .putBoolean("isRideTransit", isRideTransit)
            .putString("targetStopName", targetStopName)
            .putLong("targetStopLatBits", java.lang.Double.doubleToRawLongBits(targetStopLat))
            .putLong("targetStopLonBits", java.lang.Double.doubleToRawLongBits(targetStopLon))
            .putString("intermediateStops", arr.toString())
            .putInt("totalStopsCount", totalStopsCount)
            .putBoolean("hasAlertedCurrentLeg", hasAlertedCurrentLeg)
            .putInt("lastPassedStopIndex", lastPassedStopIndex)
            .putInt("remainingStops", currentRemainingStops.value)
            .putFloat("distToDescent", currentDistToDescentMeters.value)
            .apply()
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences("dfmob_navigation_state_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("isNavigating")) return
        isNavigating.value = prefs.getBoolean("isNavigating", false)
        isRideTransit = prefs.getBoolean("isRideTransit", false)
        targetStopName = prefs.getString("targetStopName", "") ?: ""
        targetStopLat = java.lang.Double.longBitsToDouble(prefs.getLong("targetStopLatBits", 0L))
        targetStopLon = java.lang.Double.longBitsToDouble(prefs.getLong("targetStopLonBits", 0L))
        totalStopsCount = prefs.getInt("totalStopsCount", 0)
        hasAlertedCurrentLeg = prefs.getBoolean("hasAlertedCurrentLeg", false)
        lastPassedStopIndex = prefs.getInt("lastPassedStopIndex", -1)
        currentRemainingStops.value = prefs.getInt("remainingStops", 0)
        currentDistToDescentMeters.value = prefs.getFloat("distToDescent", 0f)

        val arrStr = prefs.getString("intermediateStops", null)
        if (!arrStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(arrStr)
                val list = mutableListOf<Pair<Double, Double>>()
                for (i in 0 until arr.length()) {
                    val parts = arr.getString(i).split(",")
                    if (parts.size == 2) {
                        list.add(parts[0].toDouble() to parts[1].toDouble())
                    }
                }
                intermediateStops = list
            } catch (_: Exception) {}
        }
    }

    fun reset(context: Context? = null) {
        isNavigating.value = false
        isDescentAlarmTriggered.value = false
        currentRemainingStops.value = 0
        currentDistToDescentMeters.value = 0f
        targetStopName = ""
        targetStopLat = 0.0
        targetStopLon = 0.0
        intermediateStops = emptyList()
        totalStopsCount = 0
        isRideTransit = false
        hasAlertedCurrentLeg = false
        lastPassedStopIndex = -1

        context?.getSharedPreferences("dfmob_navigation_state_prefs", Context.MODE_PRIVATE)
            ?.edit()
            ?.clear()
            ?.apply()
    }
}
