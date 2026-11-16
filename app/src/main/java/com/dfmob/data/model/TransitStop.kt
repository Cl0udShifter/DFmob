package com.dfmob.data.model

data class TransitStop(
    val stopId: Int,
    val stopName: String,
    val stopCode: String?,
    val latE6: Int,
    val lonE6: Int,
    val isMetro: Boolean,
    val platformCode: String?
) {
    val latitude: Double get() = latE6 / 1_000_000.0
    val longitude: Double get() = lonE6 / 1_000_000.0
}

data class StopArrival(
    val lineNumber: String,
    val lineName: String,
    val destination: String,
    val colorHex: String,
    val textColorHex: String,
    val isMetro: Boolean,
    val minutesAway: Int,
    val isRealtime: Boolean,
    val scheduledTimeFormatted: String,
    val nextDepartures: List<String> = emptyList()
)

data class DFLocation(
    val name: String,
    val description: String,
    val latE6: Int,
    val lonE6: Int
) {
    val latitude: Double get() = latE6 / 1_000_000.0
    val longitude: Double get() = lonE6 / 1_000_000.0
}
