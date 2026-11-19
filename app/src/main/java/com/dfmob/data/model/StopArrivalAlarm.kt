package com.dfmob.data.model

data class StopArrivalAlarm(
    val lineNumber: String,
    val destination: String,
    val stopId: Int,
    val stopName: String,
    val targetEpochMs: Long,
    val notified15Min: Boolean = false,
    val notified10Min: Boolean = false,
    val notified5Min: Boolean = false,
    val notified2Min: Boolean = false,
    val isAutoCancelled: Boolean = false
) {
    val key: String get() = "${stopId}_${lineNumber}_${destination}"
}
