package com.dfmob.service

import kotlinx.coroutines.flow.MutableStateFlow

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

    fun reset() {
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
    }
}
