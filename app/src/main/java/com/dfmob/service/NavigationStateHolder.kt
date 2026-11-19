package com.dfmob.service

import android.content.Context
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLocation
import com.dfmob.data.model.TripPlanModelsJson
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject

object NavigationStateHolder {
    val isNavigating = MutableStateFlow(false)
    val isDescentAlarmTriggered = MutableStateFlow(false)
    val currentRemainingStops = MutableStateFlow(0)
    val currentDistToDescentMeters = MutableStateFlow(0f)

    var activeItinerary: TripPlanItinerary? = null
    var activeOrigin: TripPlanLocation? = null
    var activeDestination: TripPlanLocation? = null

    val currentStageIndexFlow = MutableStateFlow(0)
    var currentStageIndex: Int
        get() = currentStageIndexFlow.value
        set(value) { currentStageIndexFlow.value = value }

    val currentStageTitleFlow = MutableStateFlow("")
    var currentStageTitle: String
        get() = currentStageTitleFlow.value
        set(value) { currentStageTitleFlow.value = value }

    val currentLineNumberFlow = MutableStateFlow("")
    var currentLineNumber: String
        get() = currentLineNumberFlow.value
        set(value) { currentLineNumberFlow.value = value }

    val currentLineColorHexFlow = MutableStateFlow("#00C853")
    var currentLineColorHex: String
        get() = currentLineColorHexFlow.value
        set(value) { currentLineColorHexFlow.value = value }

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

        val itinJson = activeItinerary?.let { TripPlanModelsJson.itineraryToJson(it) } ?: ""
        val origJson = activeOrigin?.let { TripPlanModelsJson.locationToJson(it).toString() } ?: ""
        val destJson = activeDestination?.let { TripPlanModelsJson.locationToJson(it).toString() } ?: ""

        prefs.edit()
            .putBoolean("isNavigating", isNavigating.value)
            .putBoolean("isRideTransit", isRideTransit)
            .putInt("currentStageIndex", currentStageIndex)
            .putString("currentStageTitle", currentStageTitle)
            .putString("currentLineNumber", currentLineNumber)
            .putString("currentLineColorHex", currentLineColorHex)
            .putString("targetStopName", targetStopName)
            .putLong("targetStopLatBits", java.lang.Double.doubleToRawLongBits(targetStopLat))
            .putLong("targetStopLonBits", java.lang.Double.doubleToRawLongBits(targetStopLon))
            .putString("intermediateStops", arr.toString())
            .putInt("totalStopsCount", totalStopsCount)
            .putBoolean("hasAlertedCurrentLeg", hasAlertedCurrentLeg)
            .putInt("lastPassedStopIndex", lastPassedStopIndex)
            .putInt("remainingStops", currentRemainingStops.value)
            .putFloat("distToDescent", currentDistToDescentMeters.value)
            .putString("activeItineraryJson", itinJson)
            .putString("activeOriginJson", origJson)
            .putString("activeDestinationJson", destJson)
            .apply()
    }

    fun restore(context: Context) {
        val prefs = context.getSharedPreferences("dfmob_navigation_state_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("isNavigating")) return
        val nav = prefs.getBoolean("isNavigating", false)
        isNavigating.value = nav
        if (!nav) return

        isRideTransit = prefs.getBoolean("isRideTransit", false)
        currentStageIndex = prefs.getInt("currentStageIndex", 0)
        currentStageTitle = prefs.getString("currentStageTitle", "") ?: ""
        currentLineNumber = prefs.getString("currentLineNumber", "") ?: ""
        currentLineColorHex = prefs.getString("currentLineColorHex", "#00C853") ?: "#00C853"
        targetStopName = prefs.getString("targetStopName", "") ?: ""
        targetStopLat = java.lang.Double.longBitsToDouble(prefs.getLong("targetStopLatBits", 0L))
        targetStopLon = java.lang.Double.longBitsToDouble(prefs.getLong("targetStopLonBits", 0L))
        totalStopsCount = prefs.getInt("totalStopsCount", 0)
        hasAlertedCurrentLeg = prefs.getBoolean("hasAlertedCurrentLeg", false)
        lastPassedStopIndex = prefs.getInt("lastPassedStopIndex", -1)
        currentRemainingStops.value = prefs.getInt("remainingStops", 0)
        currentDistToDescentMeters.value = prefs.getFloat("distToDescent", 0f)

        val itinJson = prefs.getString("activeItineraryJson", null)
        if (!itinJson.isNullOrBlank()) {
            activeItinerary = TripPlanModelsJson.itineraryFromJson(itinJson)
        }
        val origJson = prefs.getString("activeOriginJson", null)
        if (!origJson.isNullOrBlank()) {
            try {
                activeOrigin = TripPlanModelsJson.locationFromJson(JSONObject(origJson))
            } catch (_: Exception) {}
        }
        val destJson = prefs.getString("activeDestinationJson", null)
        if (!destJson.isNullOrBlank()) {
            try {
                activeDestination = TripPlanModelsJson.locationFromJson(JSONObject(destJson))
            } catch (_: Exception) {}
        }

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
        activeItinerary = null
        activeOrigin = null
        activeDestination = null
        currentStageIndex = 0
        currentStageTitle = ""
        currentLineNumber = ""
        currentLineColorHex = "#00C853"
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
