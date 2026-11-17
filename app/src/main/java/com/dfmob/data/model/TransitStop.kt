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

data class NextDeparture(
    val timeFormatted: String,
    val minutesAway: Int,
    val isRealtime: Boolean,
    val targetEpochMs: Long = 0L
) {
    fun displayLabel(currentNowMs: Long = System.currentTimeMillis()): String {
        val diffMins = if (targetEpochMs > 0L) {
            maxOf(0, Math.round((targetEpochMs - currentNowMs) / 60000.0).toInt())
        } else {
            minutesAway
        }

        return if (isRealtime) {
            if (diffMins <= 0) "Já"
            else if (diffMins <= 60) "$diffMins min"
            else timeFormatted
        } else {
            if (diffMins <= 0) "Já"
            else timeFormatted
        }
    }
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
    val targetEpochMs: Long = 0L,
    val nextDepartures: List<NextDeparture> = emptyList()
) {
    fun getEffectiveMinutes(currentNowMs: Long = System.currentTimeMillis()): Int {
        return if (targetEpochMs > 0L) {
            maxOf(0, Math.round((targetEpochMs - currentNowMs) / 60000.0).toInt())
        } else {
            minutesAway
        }
    }

    fun getDisplayEta(currentNowMs: Long = System.currentTimeMillis()): String {
        val mins = getEffectiveMinutes(currentNowMs)
        return when {
            mins <= 0 -> "Já"
            mins > 60 -> scheduledTimeFormatted
            else -> "$mins min"
        }
    }
}

data class DFLocation(
    val name: String,
    val description: String,
    val latE6: Int,
    val lonE6: Int
) {
    val latitude: Double get() = latE6 / 1_000_000.0
    val longitude: Double get() = lonE6 / 1_000_000.0
}
