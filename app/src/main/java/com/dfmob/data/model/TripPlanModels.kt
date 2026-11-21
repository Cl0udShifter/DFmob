package com.dfmob.data.model

import android.location.Location

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
    val liveEtaMinutes: Int? = null,
    val crowding: CrowdingInfo = CrowdingInfo.DEFAULT_LOW
)

data class TripDepartureItem(
    val lineNumber: String,
    val timeFormatted: String,
    val isRealtime: Boolean,
    val targetEpochMs: Long,
    val lineId: Int = 0
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
    val arrivalStopId: Int? = null,
    val arrivalStopName: String? = null,
    val departureStopLat: Double? = null,
    val departureStopLon: Double? = null,
    val arrivalStopLat: Double? = null,
    val arrivalStopLon: Double? = null,
    val stopsCount: Int = 0,
    val lines: List<TripTransitLineOption> = emptyList(),
    val upcomingDepartures: List<TripDepartureItem> = emptyList(),
    val intermediateStopPoints: List<Pair<Double, Double>> = emptyList()
)

enum class TripInsightType {
    FASTEST,
    DIRECT,
    LEAST_WALKING,
    DEPARTING_SOON
}

data class TripInsight(
    val type: TripInsightType,
    val label: String,
    val shortLabel: String
) {
    companion object {
        val FASTEST = TripInsight(TripInsightType.FASTEST, "Mais rápida", "Mais rápida")
        val DIRECT = TripInsight(TripInsightType.DIRECT, "Sem baldeação", "Direta")
        val LEAST_WALKING = TripInsight(TripInsightType.LEAST_WALKING, "Menos caminhada", "Pouco a pé")
        val DEPARTING_SOON = TripInsight(TripInsightType.DEPARTING_SOON, "Partida em breve", "Em breve")
    }
}

data class TripPlanItinerary(
    val guid: String,
    val totalDurationMinutes: Int,
    val startTimeUtc: Long,
    val endTimeUtc: Long,
    val walkDurationMinutes: Int,
    val walkDistanceMeters: Double,
    val transfersCount: Int,
    val legs: List<TripPlanLeg>,
    val estimatedFare: Double = 0.0,
    val isFareIntegrated: Boolean = false,
    val insight: TripInsight? = null
) {
    val formattedFare: String
        get() = if (estimatedFare <= 0.0) {
            "Gratuito"
        } else {
            String.format(java.util.Locale("pt", "BR"), "R$\u00A0%.2f", estimatedFare)
        }
}

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

val TripPlanLeg.isWalk: Boolean
    get() = type == TripLegType.WALK || type == TripLegType.TRANSFER_WALK

fun TripPlanLeg.getStartPoint(): Pair<Double, Double>? {
    if (departureStopLat != null && departureStopLon != null) {
        return Pair(departureStopLat, departureStopLon)
    }
    if (!polylineEncoded.isNullOrBlank()) {
        return PolylineDecoder.decode(polylineEncoded).firstOrNull()
    }
    return null
}

fun TripPlanLeg.getEndPoint(): Pair<Double, Double>? {
    if (arrivalStopLat != null && arrivalStopLon != null) {
        return Pair(arrivalStopLat, arrivalStopLon)
    }
    if (!polylineEncoded.isNullOrBlank()) {
        return PolylineDecoder.decode(polylineEncoded).lastOrNull()
    }
    return null
}

fun calculateGeoDistance(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
    val results = FloatArray(1)
    Location.distanceBetween(p1.first, p1.second, p2.first, p2.second, results)
    return results[0].toDouble()
}

object PolylineEncoder {
    fun encode(points: List<Pair<Double, Double>>): String {
        val result = StringBuilder()
        var lastLat = 0
        var lastLng = 0
        for (point in points) {
            val lat = Math.round(point.first * 1E5).toInt()
            val lng = Math.round(point.second * 1E5).toInt()
            encodeValue(lat - lastLat, result)
            encodeValue(lng - lastLng, result)
            lastLat = lat
            lastLng = lng
        }
        return result.toString()
    }

    private fun encodeValue(value: Int, result: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else (value shl 1)
        while (v >= 0x20) {
            result.append((((v and 0x1f) or 0x20) + 63).toChar())
            v = v shr 5
        }
        result.append((v + 63).toChar())
    }

    fun merge(poly1: String?, poly2: String?): String? {
        if (poly1.isNullOrBlank()) return poly2
        if (poly2.isNullOrBlank()) return poly1
        val pts1 = PolylineDecoder.decode(poly1)
        val pts2 = PolylineDecoder.decode(poly2)
        if (pts1.isEmpty()) return poly2
        if (pts2.isEmpty()) return poly1
        return encode(pts1 + pts2)
    }
}

/**
 * Valida a continuidade fisica do itinerario e consolida pernas consecutivas de caminhada.
 * Se houver descontinuidade (salto espacial inexplicado > 450m sem transporte), retorna null para descarte.
 */
fun sanitizeAndConsolidateLegs(rawLegs: List<TripPlanLeg>): List<TripPlanLeg>? {
    if (rawLegs.isEmpty()) return null
    val consolidated = mutableListOf<TripPlanLeg>()

    for (leg in rawLegs) {
        val last = consolidated.lastOrNull()
        if (last != null && last.isWalk && leg.isWalk) {
            val endLast = last.getEndPoint()
            val startCurrent = leg.getStartPoint()
            val gap = if (endLast != null && startCurrent != null) {
                calculateGeoDistance(endLast, startCurrent)
            } else 0.0

            if (gap > 450.0) {
                // Descontinuidade: duas caminhadas separadas por centenas de metros ou quilometros
                // indica perda de perna intermediaria no trajeto
                return null
            }

            val mergedPoly = PolylineEncoder.merge(last.polylineEncoded, leg.polylineEncoded)
            val mergedDuration = last.durationMinutes + leg.durationMinutes
            val mergedDistance = last.distanceMeters + leg.distanceMeters + gap
            val mergedType = if (last.type == TripLegType.TRANSFER_WALK || leg.type == TripLegType.TRANSFER_WALK) {
                TripLegType.TRANSFER_WALK
            } else {
                TripLegType.WALK
            }
            val mergedLeg = last.copy(
                type = mergedType,
                endTimeUtc = maxOf(last.endTimeUtc, leg.endTimeUtc),
                durationMinutes = mergedDuration,
                distanceMeters = mergedDistance,
                polylineEncoded = mergedPoly,
                arrivalStopId = leg.arrivalStopId ?: last.arrivalStopId,
                arrivalStopName = leg.arrivalStopName ?: last.arrivalStopName,
                arrivalStopLat = leg.arrivalStopLat ?: last.arrivalStopLat,
                arrivalStopLon = leg.arrivalStopLon ?: last.arrivalStopLon
            )
            consolidated[consolidated.size - 1] = mergedLeg
        } else {
            consolidated.add(leg)
        }
    }

    // Verifica continuidade global entre etapas consecutivas
    for (i in 0 until consolidated.size - 1) {
        val curr = consolidated[i]
        val next = consolidated[i + 1]
        val endCurr = curr.getEndPoint()
        val startNext = next.getStartPoint()
        if (endCurr != null && startNext != null) {
            val gap = calculateGeoDistance(endCurr, startNext)
            if (gap > 450.0) {
                return null
            }
        }
    }

    return consolidated
}
