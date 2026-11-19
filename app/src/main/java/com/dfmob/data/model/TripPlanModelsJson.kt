package com.dfmob.data.model

import org.json.JSONArray
import org.json.JSONObject

object TripPlanModelsJson {

    fun locationToJson(loc: TripPlanLocation): JSONObject {
        return JSONObject().apply {
            put("caption", loc.caption)
            put("latE6", loc.latE6)
            put("lonE6", loc.lonE6)
            put("isUserLocation", loc.isUserLocation)
        }
    }

    fun locationFromJson(obj: JSONObject): TripPlanLocation {
        return TripPlanLocation(
            caption = obj.optString("caption", ""),
            latE6 = obj.optInt("latE6", 0),
            lonE6 = obj.optInt("lonE6", 0),
            isUserLocation = obj.optBoolean("isUserLocation", false)
        )
    }

    fun itineraryToJson(itin: TripPlanItinerary): String {
        val root = JSONObject().apply {
            put("guid", itin.guid)
            put("totalDurationMinutes", itin.totalDurationMinutes)
            put("startTimeUtc", itin.startTimeUtc)
            put("endTimeUtc", itin.endTimeUtc)
            put("walkDurationMinutes", itin.walkDurationMinutes)
            put("walkDistanceMeters", itin.walkDistanceMeters)
            put("transfersCount", itin.transfersCount)
            put("estimatedFare", itin.estimatedFare)
            put("isFareIntegrated", itin.isFareIntegrated)

            val legsArr = JSONArray()
            for (leg in itin.legs) {
                val legObj = JSONObject().apply {
                    put("type", leg.type.name)
                    put("startTimeUtc", leg.startTimeUtc)
                    put("endTimeUtc", leg.endTimeUtc)
                    put("durationMinutes", leg.durationMinutes)
                    put("distanceMeters", leg.distanceMeters)
                    if (leg.polylineEncoded != null) put("polylineEncoded", leg.polylineEncoded)
                    if (leg.departureStopId != null) put("departureStopId", leg.departureStopId)
                    if (leg.departureStopName != null) put("departureStopName", leg.departureStopName)
                    if (leg.arrivalStopId != null) put("arrivalStopId", leg.arrivalStopId)
                    if (leg.arrivalStopName != null) put("arrivalStopName", leg.arrivalStopName)
                    if (leg.departureStopLat != null) put("departureStopLat", leg.departureStopLat)
                    if (leg.departureStopLon != null) put("departureStopLon", leg.departureStopLon)
                    if (leg.arrivalStopLat != null) put("arrivalStopLat", leg.arrivalStopLat)
                    if (leg.arrivalStopLon != null) put("arrivalStopLon", leg.arrivalStopLon)
                    put("stopsCount", leg.stopsCount)

                    val linesArr = JSONArray()
                    for (l in leg.lines) {
                        val lObj = JSONObject().apply {
                            put("lineId", l.lineId)
                            put("lineNumber", l.lineNumber)
                            put("lineName", l.lineName)
                            put("colorHex", l.colorHex)
                            put("isMetro", l.isMetro)
                            put("agencyName", l.agencyName)
                            if (l.tripId != null) put("tripId", l.tripId)
                            put("hasLiveGps", l.hasLiveGps)
                            if (l.liveEtaMinutes != null) put("liveEtaMinutes", l.liveEtaMinutes)
                        }
                        linesArr.put(lObj)
                    }
                    put("lines", linesArr)

                    val depsArr = JSONArray()
                    for (d in leg.upcomingDepartures) {
                        val dObj = JSONObject().apply {
                            put("lineNumber", d.lineNumber)
                            put("timeFormatted", d.timeFormatted)
                            put("isRealtime", d.isRealtime)
                            put("targetEpochMs", d.targetEpochMs)
                            put("lineId", d.lineId)
                        }
                        depsArr.put(dObj)
                    }
                    put("upcomingDepartures", depsArr)

                    val interArr = JSONArray()
                    for (p in leg.intermediateStopPoints) {
                        interArr.put("${p.first},${p.second}")
                    }
                    put("intermediateStopPoints", interArr)
                }
                legsArr.put(legObj)
            }
            put("legs", legsArr)
        }
        return root.toString()
    }

    fun itineraryFromJson(jsonStr: String): TripPlanItinerary? {
        return try {
            val root = JSONObject(jsonStr)
            val legs = mutableListOf<TripPlanLeg>()
            val legsArr = root.optJSONArray("legs")
            if (legsArr != null) {
                for (i in 0 until legsArr.length()) {
                    val legObj = legsArr.getJSONObject(i)
                    val typeStr = legObj.optString("type", "WALK")
                    val type = try { TripLegType.valueOf(typeStr) } catch (_: Exception) { TripLegType.WALK }

                    val lines = mutableListOf<TripTransitLineOption>()
                    val linesArr = legObj.optJSONArray("lines")
                    if (linesArr != null) {
                        for (j in 0 until linesArr.length()) {
                            val lObj = linesArr.getJSONObject(j)
                            lines.add(
                                TripTransitLineOption(
                                    lineId = lObj.optInt("lineId", 0),
                                    lineNumber = lObj.optString("lineNumber", ""),
                                    lineName = lObj.optString("lineName", ""),
                                    colorHex = lObj.optString("colorHex", "#00C853"),
                                    isMetro = lObj.optBoolean("isMetro", false),
                                    agencyName = lObj.optString("agencyName", ""),
                                    tripId = if (lObj.has("tripId")) lObj.optLong("tripId") else null,
                                    hasLiveGps = lObj.optBoolean("hasLiveGps", false),
                                    liveEtaMinutes = if (lObj.has("liveEtaMinutes")) lObj.optInt("liveEtaMinutes") else null
                                )
                            )
                        }
                    }

                    val deps = mutableListOf<TripDepartureItem>()
                    val depsArr = legObj.optJSONArray("upcomingDepartures")
                    if (depsArr != null) {
                        for (j in 0 until depsArr.length()) {
                            val dObj = depsArr.getJSONObject(j)
                            deps.add(
                                TripDepartureItem(
                                    lineNumber = dObj.optString("lineNumber", ""),
                                    timeFormatted = dObj.optString("timeFormatted", ""),
                                    isRealtime = dObj.optBoolean("isRealtime", false),
                                    targetEpochMs = dObj.optLong("targetEpochMs", 0L),
                                    lineId = dObj.optInt("lineId", 0)
                                )
                            )
                        }
                    }

                    val inter = mutableListOf<Pair<Double, Double>>()
                    val interArr = legObj.optJSONArray("intermediateStopPoints")
                    if (interArr != null) {
                        for (j in 0 until interArr.length()) {
                            val raw = interArr.optString(j)
                            val parts = raw.split(",")
                            if (parts.size == 2) {
                                val lat = parts[0].toDoubleOrNull()
                                val lon = parts[1].toDoubleOrNull()
                                if (lat != null && lon != null) {
                                    inter.add(lat to lon)
                                }
                            }
                        }
                    }

                    legs.add(
                        TripPlanLeg(
                            type = type,
                            startTimeUtc = legObj.optLong("startTimeUtc", 0L),
                            endTimeUtc = legObj.optLong("endTimeUtc", 0L),
                            durationMinutes = legObj.optInt("durationMinutes", 0),
                            distanceMeters = legObj.optDouble("distanceMeters", 0.0),
                            polylineEncoded = if (legObj.has("polylineEncoded")) legObj.optString("polylineEncoded") else null,
                            departureStopId = if (legObj.has("departureStopId")) legObj.optInt("departureStopId") else null,
                            departureStopName = if (legObj.has("departureStopName")) legObj.optString("departureStopName") else null,
                            arrivalStopId = if (legObj.has("arrivalStopId")) legObj.optInt("arrivalStopId") else null,
                            arrivalStopName = if (legObj.has("arrivalStopName")) legObj.optString("arrivalStopName") else null,
                            departureStopLat = if (legObj.has("departureStopLat")) legObj.optDouble("departureStopLat") else null,
                            departureStopLon = if (legObj.has("departureStopLon")) legObj.optDouble("departureStopLon") else null,
                            arrivalStopLat = if (legObj.has("arrivalStopLat")) legObj.optDouble("arrivalStopLat") else null,
                            arrivalStopLon = if (legObj.has("arrivalStopLon")) legObj.optDouble("arrivalStopLon") else null,
                            stopsCount = legObj.optInt("stopsCount", 0),
                            lines = lines,
                            upcomingDepartures = deps,
                            intermediateStopPoints = inter
                        )
                    )
                }
            }

            TripPlanItinerary(
                guid = root.optString("guid", ""),
                totalDurationMinutes = root.optInt("totalDurationMinutes", 0),
                startTimeUtc = root.optLong("startTimeUtc", 0L),
                endTimeUtc = root.optLong("endTimeUtc", 0L),
                walkDurationMinutes = root.optInt("walkDurationMinutes", 0),
                walkDistanceMeters = root.optDouble("walkDistanceMeters", 0.0),
                transfersCount = root.optInt("transfersCount", 0),
                legs = legs,
                estimatedFare = root.optDouble("estimatedFare", 0.0),
                isFareIntegrated = root.optBoolean("isFareIntegrated", false)
            )
        } catch (_: Exception) {
            null
        }
    }
}
