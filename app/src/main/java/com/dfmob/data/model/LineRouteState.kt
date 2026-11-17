package com.dfmob.data.model

data class LineDirectionOption(
    val lineId: Int,
    val directionId: Int,
    val originName: String,
    val destinationName: String
)

data class LiveVehicleMarker(
    val vehicleId: String,
    val lineNumber: String,
    val latE6: Int,
    val lonE6: Int,
    val speedKmh: Float,
    val bearing: Float,
    val directionId: Int?
) {
    val latitude: Double get() = latE6 / 1_000_000.0
    val longitude: Double get() = lonE6 / 1_000_000.0
}

data class LineRouteState(
    val groupId: Int,
    val lineNumber: String,
    val lineName: String,
    val agencyName: String,
    val circular: Boolean,
    val colorHex: String,
    val textColorHex: String,
    val isMetro: Boolean,
    val directions: List<LineDirectionOption>,
    val currentDirectionIndex: Int,
    val stops: List<TransitStop>,
    val selectedStop: TransitStop?,
    val liveVehicles: List<LiveVehicleMarker> = emptyList(),
    val selectedStopArrival: StopArrival? = null
) {
    val activeLineId: Int get() = directions.getOrNull(currentDirectionIndex)?.lineId ?: 0
    val activeDirectionId: Int get() = directions.getOrNull(currentDirectionIndex)?.directionId ?: 0
    val activeDestination: String get() = directions.getOrNull(currentDirectionIndex)?.destinationName ?: lineName
}
