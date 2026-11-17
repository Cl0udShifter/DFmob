package com.dfmob.data.model

data class TripPlanLocation(
    val caption: String,
    val latE6: Int,
    val lonE6: Int,
    val isUserLocation: Boolean = false
) {
    val latitude: Double get() = latE6 / 1_000_000.0
    val longitude: Double get() = lonE6 / 1_000_000.0
}

enum class TripTimeMode {
    DEPART_NOW,
    DEPART_AT,
    ARRIVE_BY
}

enum class TripPreference(val thriftValue: Int) {
    FASTEST(2),
    LEAST_WALKING(1),
    LEAST_TRANSFERS(3)
}

data class TripPlanFilter(
    val mode: TripTimeMode = TripTimeMode.DEPART_NOW,
    val targetTimeUtc: Long = System.currentTimeMillis(),
    val preference: TripPreference = TripPreference.FASTEST
)

enum class TripLegType {
    WALK,
    TRANSIT,
    WAIT,
    TRANSFER_WALK
}

data class TripTransitLineOption(
    val lineId: Int,
    val lineNumber: String,
    val lineName: String,
    val colorHex: String,
    val isMetro: Boolean,
    val agencyName: String,
    val tripId: Long? = null,
    val hasLiveGps: Boolean = false,
    val liveEtaMinutes: Int? = null
)

data class TripDepartureItem(
    val lineNumber: String,
    val timeFormatted: String,
    val isRealtime: Boolean,
    val targetEpochMs: Long
)

data class TripPlanLeg(
    val type: TripLegType,
    val startTimeUtc: Long,
    val endTimeUtc: Long,
    val durationMinutes: Int,
    val distanceMeters: Double = 0.0,
    val polylineEncoded: String? = null,
    val departureStopId: Int? = null,
    val departureStopName: String? = null,
    val arrivalStopName: String? = null,
    val stopsCount: Int = 0,
    val lines: List<TripTransitLineOption> = emptyList(),
    val upcomingDepartures: List<TripDepartureItem> = emptyList()
)

data class TripPlanItinerary(
    val guid: String,
    val totalDurationMinutes: Int,
    val startTimeUtc: Long,
    val endTimeUtc: Long,
    val walkDurationMinutes: Int,
    val walkDistanceMeters: Double,
    val transfersCount: Int,
    val legs: List<TripPlanLeg>
)

object PolylineDecoder {
    fun decode(encoded: String): List<Pair<Double, Double>> {
        val poly = ArrayList<Pair<Double, Double>>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dlat

            shift = 0
            result = 0
            do {
                if (index >= len) break
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lng += dlng

            poly.add(Pair(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}
