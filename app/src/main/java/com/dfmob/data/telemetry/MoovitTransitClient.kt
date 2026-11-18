package com.dfmob.data.telemetry

import com.dfmob.BuildConfig
import com.dfmob.data.model.LiveVehicleMarker
import com.dfmob.data.model.TransitStop
import org.json.JSONObject
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLeg
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanLocation
import com.dfmob.data.model.TripPlanFilter
import com.dfmob.data.model.TripTimeMode
import com.dfmob.data.model.TripTransitLineOption
import com.dfmob.data.database.DatabaseHelper
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MoovitTransitClient {

    private const val ARRIVALS_URL = "https://app5.moovitapp.com/services-app/services/V4/StopsArrivals"
    private const val CLIENT_VERSION = "5.204.1.1814"
    private const val PHONE_TYPE = "1"

    // Ofuscacao XOR para prevencao de exposicao de chaves e segredos em texto plano no repositorio
    private const val SECRET_XOR_KEY = 0x5A

    private val OBFUSCATED_API_KEY = byteArrayOf(
        55, 53, 53, 44, 51, 46, 5, 104, 109, 111, 107, 109, 106, 105, 110, 106, 111
    )

    private val OBFUSCATED_USER_KEY = byteArrayOf(
        104, 24, 28, 107, 98, 25, 27, 109, 31, 110, 99, 109, 99, 109, 106, 31,
        104, 28, 106, 104, 28, 110, 31, 27, 27, 106, 24, 31, 98, 110, 108, 107
    )

    private val OBFUSCATED_METRO_ID = byteArrayOf(107, 109, 106, 104)

    private val OBFUSCATED_METRO_REVISION = byteArrayOf(
        107, 109, 99, 110, 99, 109, 110, 104, 104, 110, 109, 104, 105
    )

    private fun deobfuscate(bytes: ByteArray): String {
        val chars = CharArray(bytes.size)
        for (i in bytes.indices) {
            chars[i] = (bytes[i].toInt() xor SECRET_XOR_KEY).toChar()
        }
        return String(chars)
    }

    private fun getApiKey(): String {
        return if (BuildConfig.MOOVIT_API_KEY.isNotBlank()) BuildConfig.MOOVIT_API_KEY else deobfuscate(OBFUSCATED_API_KEY)
    }

    private fun getUserKey(): String {
        return if (BuildConfig.MOOVIT_USER_KEY.isNotBlank()) BuildConfig.MOOVIT_USER_KEY else deobfuscate(OBFUSCATED_USER_KEY)
    }

    private fun getMetroId(): String = deobfuscate(OBFUSCATED_METRO_ID)

    @Volatile
    private var currentMetroRevision: String = deobfuscate(OBFUSCATED_METRO_REVISION)

    private fun getMetroRevision(): String = currentMetroRevision

    private const val LINE_ARRIVALS_URL = "https://app5.moovitapp.com/services-app/services/V4/LineArrivals"

    private fun openMoovitConnection(
        urlStr: String,
        payloadSize: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        metroRevision: String?
    ): HttpURLConnection {
        val url = URL(urlStr)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Host", "app5.moovitapp.com")
            setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; 2412DPC0AG Build/UKQ1.231003.002)")
            setRequestProperty("CLIENT_VERSION", CLIENT_VERSION)
            setRequestProperty("PHONE_TYPE", PHONE_TYPE)
            setRequestProperty("API_KEY", getApiKey())
            setRequestProperty("USER_KEY", getUserKey())
            setRequestProperty("Metro-Revision-Metro-Id", getMetroId())
            if (!metroRevision.isNullOrBlank()) {
                setRequestProperty("Metro-Revision-Number", metroRevision)
            }
            setRequestProperty("Content-Type", "application/octet")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Length", payloadSize.toString())
        }
    }

    private fun executeRequest(
        urlStr: String,
        payload: ByteArray,
        connectTimeoutMs: Int = 4000,
        readTimeoutMs: Int = 5000
    ): String? {
        var conn: HttpURLConnection? = null
        try {
            conn = openMoovitConnection(urlStr, payload.size, connectTimeoutMs, readTimeoutMs, currentMetroRevision)
            conn.outputStream.use { os ->
                os.write(payload)
                os.flush()
            }

            val code = conn.responseCode
            val returnedRev = conn.getHeaderField("Metro-Revision-Number")
            if (!returnedRev.isNullOrBlank()) {
                currentMetroRevision = returnedRev
            }

            if (code == HttpURLConnection.HTTP_OK) {
                return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else if (code == 412) {
                // HTTP 412 Precondition Failed: revisao do metro expirada
                val newRev = returnedRev ?: currentMetroRevision
                conn.disconnect()
                val retryConn = openMoovitConnection(urlStr, payload.size, connectTimeoutMs, readTimeoutMs, newRev)
                retryConn.outputStream.use { os ->
                    os.write(payload)
                    os.flush()
                }
                if (retryConn.responseCode == HttpURLConnection.HTTP_OK) {
                    val retryRev = retryConn.getHeaderField("Metro-Revision-Number")
                    if (!retryRev.isNullOrBlank()) {
                        currentMetroRevision = retryRev
                    }
                    return retryConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                }
            }
        } catch (_: Exception) {
            // Silencioso: fallback suave para dados locais
        } finally {
            conn?.disconnect()
        }
        return null
    }

    data class RawArrival(
        val patternId: Int,
        val tripId: Long,
        val staticEtdUTC: Long,
        val rtEtdUTC: Long?,
        val vehicleId: String?,
        val vehicleLatE6: Int? = null,
        val vehicleLonE6: Int? = null,
        val vehicleSampleTimeUtc: Long? = null,
        val nextStopIndex: Int? = null,
        val patternStopsSize: Int? = null
    )

    data class RawLineArrivals(
        val lineId: Int,
        val arrivals: List<RawArrival>
    )

    fun buildStopArrivalsRequest(stopId: Int, fromTimeMs: Long): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Field 1: stopIds (list<i32>)
        // Type 15 (LIST = 0x0f), Field ID 1
        dos.writeByte(15)
        dos.writeShort(1)
        // List element type: 8 (I32 = 0x08), size = 1
        dos.writeByte(8)
        dos.writeInt(1)
        dos.writeInt(stopId)

        // Field 2: conf (STRUCT = 12 = 0x0c), Field ID 2
        dos.writeByte(12)
        dos.writeShort(2)

        // Field 1: fromTime (i64 = 10 = 0x0a)
        dos.writeByte(10)
        dos.writeShort(1)
        dos.writeLong(fromTimeMs)

        // Field 2: intrestingInLastArrivals (bool = 2)
        dos.writeByte(2)
        dos.writeShort(2)
        dos.writeByte(1)

        // Field 3: includeOnlyLastArrivals (bool = 2)
        dos.writeByte(2)
        dos.writeShort(3)
        dos.writeByte(0)

        // Field 4: includeStaticSchedule (bool = 2)
        dos.writeByte(2)
        dos.writeShort(4)
        dos.writeByte(1)

        // Field 5: includeShapeSegments (bool = 2)
        dos.writeByte(2)
        dos.writeShort(5)
        dos.writeByte(0)

        // Field 6: forceAllArrivalsRetrieval (bool = 2)
        dos.writeByte(2)
        dos.writeShort(6)
        dos.writeByte(1)

        // End of conf struct (0x00)
        dos.writeByte(0)

        // End of outer request struct (0x00)
        dos.writeByte(0)

        dos.flush()
        return baos.toByteArray()
    }

    fun fetchArrivalsForStop(stopId: Int): List<RawLineArrivals> {
        val payload = buildStopArrivalsRequest(stopId, System.currentTimeMillis())
        val result = mutableListOf<RawLineArrivals>()

        val jsonText = executeRequest(ARRIVALS_URL, payload, 4000, 4000) ?: return result
        try {
            val root = JSONObject(jsonText)
            val lineArrivalsObj = root.optJSONObject("3")
            val lstArray = lineArrivalsObj?.optJSONArray("lst")
            if (lstArray != null && lstArray.length() > 2) {
                for (i in 2 until lstArray.length()) {
                    val lineRec = lstArray.optJSONObject(i) ?: continue
                    val lineId = lineRec.optJSONObject("1")?.optInt("i32", 0) ?: 0
                    if (lineId == 0) continue

                    val arrivalsObj = lineRec.optJSONObject("2")
                    val arrLst = arrivalsObj?.optJSONArray("lst")
                    val arrivals = mutableListOf<RawArrival>()

                    if (arrLst != null && arrLst.length() > 2) {
                        for (j in 2 until arrLst.length()) {
                            val arrRec = arrLst.optJSONObject(j) ?: continue
                            val patternId = arrRec.optJSONObject("1")?.optInt("i32", 0) ?: 0
                            val tripId = arrRec.optJSONObject("2")?.optLong("i64", 0L) ?: 0L
                            val staticEtdUTC = arrRec.optJSONObject("3")?.optLong("i64", 0L) ?: 0L
                            val rtEtdUTC = if (arrRec.has("4")) arrRec.optJSONObject("4")?.optLong("i64") else null

                            val vehRec = arrRec.optJSONObject("11")?.optJSONObject("rec")
                            val vehicleId = vehRec?.optJSONObject("3")?.optString("str")
                            val latlonRec = vehRec?.optJSONObject("1")?.optJSONObject("rec")
                            val latE6 = (latlonRec?.optJSONObject("1")?.optInt("i32", 0) ?: latlonRec?.optInt("1", 0)).takeIf { it != null && it != 0 }
                            val lonE6 = (latlonRec?.optJSONObject("2")?.optInt("i32", 0) ?: latlonRec?.optInt("2", 0)).takeIf { it != null && it != 0 }
                            val sampleTime = (vehRec?.optJSONObject("4")?.optLong("i64", 0L) ?: vehRec?.optLong("4", 0L)).takeIf { it != null && it != 0L }
                            val nextStopIdx = (vehRec?.optJSONObject("2")?.optJSONObject("rec")?.optJSONObject("1")?.optInt("i32", -1)
                                ?: vehRec?.optJSONObject("2")?.optJSONObject("rec")?.optInt("1", -1)).takeIf { it != null && it >= 0 }
                            val patternSize = arrRec.optJSONObject("13")?.optInt("i32", 0).takeIf { it != null && it > 0 }

                            arrivals.add(
                                RawArrival(
                                    patternId = patternId,
                                    tripId = tripId,
                                    staticEtdUTC = staticEtdUTC,
                                    rtEtdUTC = rtEtdUTC,
                                    vehicleId = vehicleId,
                                    vehicleLatE6 = latE6,
                                    vehicleLonE6 = lonE6,
                                    vehicleSampleTimeUtc = sampleTime,
                                    nextStopIndex = nextStopIdx,
                                    patternStopsSize = patternSize
                                )
                            )
                        }
                    }

                    result.add(RawLineArrivals(lineId, arrivals))
                }
            }
        } catch (_: Exception) {
            // Silencioso: fallback suave para banco SQLite local em caso de instabilidade de rede
        }

        return result
    }

    fun buildLineArrivalsRequest(pairs: List<Pair<Int, Int>>, fromTimeMs: Long): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // Field 1: stopAndLineIds (list<struct>)
        dos.writeByte(15)
        dos.writeShort(1)
        dos.writeByte(12) // struct
        dos.writeInt(pairs.size)

        for (pair in pairs) {
            val stopId = pair.first
            val lineId = pair.second

            // MVStopLineIds:
            // field 1: stopId (i32)
            dos.writeByte(8)
            dos.writeShort(1)
            dos.writeInt(stopId)

            // field 2: lineId (i32)
            dos.writeByte(8)
            dos.writeShort(2)
            dos.writeInt(lineId)

            dos.writeByte(0) // end MVStopLineIds
        }

        // Field 2: conf (struct)
        dos.writeByte(12)
        dos.writeShort(2)

        // fromTime: i64
        dos.writeByte(10)
        dos.writeShort(1)
        dos.writeLong(fromTimeMs)

        // intrestingInLastArrivals: bool
        dos.writeByte(2)
        dos.writeShort(2)
        dos.writeByte(1)

        // includeOnlyLastArrivals: bool
        dos.writeByte(2)
        dos.writeShort(3)
        dos.writeByte(0)

        // includeStaticSchedule: bool
        dos.writeByte(2)
        dos.writeShort(4)
        dos.writeByte(1)

        // includeShapeSegments: bool
        dos.writeByte(2)
        dos.writeShort(5)
        dos.writeByte(0)

        // forceAllArrivalsRetrieval: bool
        dos.writeByte(2)
        dos.writeShort(6)
        dos.writeByte(1)

        dos.writeByte(0) // end conf
        dos.writeByte(0) // end root

        dos.flush()
        return baos.toByteArray()
    }

    fun fetchLiveVehiclesForLine(
        lineId: Int,
        stopIds: List<Int>,
        lineNumber: String,
        directionId: Int,
        stops: List<TransitStop> = emptyList()
    ): List<LiveVehicleMarker> {
        if (stopIds.isEmpty()) return emptyList()

        val sampledStops = if (stopIds.size <= 28) {
            stopIds
        } else {
            val step = (stopIds.size / 28).coerceAtLeast(1)
            stopIds.filterIndexed { idx, _ -> idx % step == 0 || idx == 0 || idx == stopIds.lastIndex }.distinct()
        }

        val pairs = sampledStops.map { Pair(it, lineId) }
        val payload = buildLineArrivalsRequest(pairs, System.currentTimeMillis())
        val vehiclesMap = mutableMapOf<String, LiveVehicleMarker>()

        val jsonText = executeRequest(LINE_ARRIVALS_URL, payload, 4000, 4000) ?: return emptyList()

        try {
            val tokener = JSONTokener(jsonText)
            while (tokener.more()) {
                val nextChar = tokener.nextClean()
                if (nextChar != '{') break
                tokener.back()
                val obj = JSONObject(tokener)
                val lineRec = obj.optJSONObject("3")?.optJSONObject("rec") ?: continue
                val arrLst = lineRec.optJSONObject("2")?.optJSONArray("lst") ?: continue

                if (arrLst.length() > 2) {
                    for (j in 2 until arrLst.length()) {
                        val arrRec = arrLst.optJSONObject(j) ?: continue
                        val vehRec = arrRec.optJSONObject("11")?.optJSONObject("rec") ?: continue
                        val latlonRec = vehRec.optJSONObject("1")?.optJSONObject("rec") ?: continue
                        val latE6 = latlonRec.optJSONObject("1")?.optInt("i32", 0) ?: latlonRec.optInt("1", 0)
                        val lonE6 = latlonRec.optJSONObject("2")?.optInt("i32", 0) ?: latlonRec.optInt("2", 0)
                        val vid = vehRec.optJSONObject("3")?.optString("str") ?: continue
                        val nextStopIdx = vehRec.optJSONObject("2")?.optJSONObject("rec")?.optJSONObject("1")?.optInt("i32", -1)
                            ?: vehRec.optJSONObject("2")?.optJSONObject("rec")?.optInt("1", -1)
                            ?: -1

                        if (latE6 != 0 && lonE6 != 0 && !vehiclesMap.containsKey(vid)) {
                            var bearing = 0f
                            if (nextStopIdx >= 0 && nextStopIdx < stops.size) {
                                val targetStop = stops[nextStopIdx]
                                bearing = calculateBearing(
                                    latE6 / 1_000_000.0,
                                    lonE6 / 1_000_000.0,
                                    targetStop.latitude,
                                    targetStop.longitude
                                )
                            }

                            vehiclesMap[vid] = LiveVehicleMarker(
                                vehicleId = vid,
                                lineNumber = lineNumber,
                                latE6 = latE6,
                                lonE6 = lonE6,
                                speedKmh = 0f,
                                bearing = bearing,
                                directionId = directionId
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        return vehiclesMap.values.toList()
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)
        val y = Math.sin(deltaLambda) * Math.cos(phi2)
        val x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda)
        val theta = Math.atan2(y, x)
        return ((Math.toDegrees(theta) + 360.0) % 360.0).toFloat()
    }

    private const val TRIP_PLAN_URL = "https://app5.moovitapp.com/services-app/services/V4/TripPlanner2/Search"

    fun buildTripPlanRequest(
        fromLatE6: Int,
        fromLonE6: Int,
        fromCaption: String,
        toLatE6: Int,
        toLonE6: Int,
        toCaption: String,
        pref: Int,
        timeType: Int,
        timeUtc: Long,
        currentTimeSelected: Boolean
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        // 1: tripPlanPref (i32) -> 2 (Fastest), 1 (LeastWalking), 3 (LeastTransfers)
        dos.writeByte(8)
        dos.writeShort(1)
        dos.writeInt(pref)

        // 2: timeUtc (i64)
        dos.writeByte(10)
        dos.writeShort(2)
        dos.writeLong(timeUtc)

        // 3: timeType (i32) -> Departure = 2, Arrival = 1
        dos.writeByte(8)
        dos.writeShort(3)
        dos.writeInt(timeType)

        // 4: currentTimeSelected (bool)
        dos.writeByte(2)
        dos.writeShort(4)
        dos.writeByte(if (currentTimeSelected) 1 else 0)

        // 5: routeTypes (list<i32>) -> Bus = 3, Subway = 1
        dos.writeByte(15)
        dos.writeShort(5)
        dos.writeByte(8)
        dos.writeInt(2)
        dos.writeInt(3) // Bus
        dos.writeInt(1) // Subway

        // 6: fromLocation (struct)
        dos.writeByte(12)
        dos.writeShort(6)
        writeLocationTarget(dos, fromLatE6, fromLonE6, fromCaption)

        // 7: toLocation (struct)
        dos.writeByte(12)
        dos.writeShort(7)
        writeLocationTarget(dos, toLatE6, toLonE6, toCaption)

        // 10: skipTaxiSearch (bool) -> true
        dos.writeByte(2)
        dos.writeShort(10)
        dos.writeByte(1)

        // 15: addFlexTimeSearch (bool) -> true
        dos.writeByte(2)
        dos.writeShort(15)
        dos.writeByte(1)

        // 17: algorithmType (i32) -> 1 (FILTER)
        dos.writeByte(8)
        dos.writeShort(17)
        dos.writeInt(1)

        dos.writeByte(0)
        return baos.toByteArray()
    }

    private fun writeLocationTarget(dos: DataOutputStream, latE6: Int, lonE6: Int, caption: String) {
        dos.writeByte(12)
        dos.writeShort(1)

        dos.writeByte(11)
        dos.writeShort(1)
        val captionBytes = caption.toByteArray(Charsets.UTF_8)
        dos.writeInt(captionBytes.size)
        dos.write(captionBytes)

        dos.writeByte(12)
        dos.writeShort(3)
        dos.writeByte(8)
        dos.writeShort(1)
        dos.writeInt(latE6)
        dos.writeByte(8)
        dos.writeShort(2)
        dos.writeInt(lonE6)
        dos.writeByte(0)

        dos.writeByte(8)
        dos.writeShort(4)
        dos.writeInt(1)

        dos.writeByte(0)

        dos.writeByte(8)
        dos.writeShort(2)
        dos.writeInt(1)

        dos.writeByte(0)
    }

    fun searchTripPlan(
        from: TripPlanLocation,
        to: TripPlanLocation,
        filter: TripPlanFilter,
        dbHelper: DatabaseHelper
    ): List<TripPlanItinerary> {
        val pref = filter.preference.thriftValue
        val timeType = when (filter.mode) {
            TripTimeMode.DEPART_NOW -> 2
            TripTimeMode.DEPART_AT -> 2
            TripTimeMode.ARRIVE_BY -> 1
        }
        val isNow = (filter.mode == TripTimeMode.DEPART_NOW)
        val payload = buildTripPlanRequest(
            fromLatE6 = from.latE6,
            fromLonE6 = from.lonE6,
            fromCaption = from.caption,
            toLatE6 = to.latE6,
            toLonE6 = to.lonE6,
            toCaption = to.caption,
            pref = pref,
            timeType = timeType,
            timeUtc = filter.targetTimeUtc,
            currentTimeSelected = isNow
        )

        val itineraries = mutableListOf<TripPlanItinerary>()
        val jsonText = executeRequest(TRIP_PLAN_URL, payload, 6000, 8000) ?: return itineraries

        try {
            val tokener = JSONTokener(jsonText)
            while (tokener.more()) {
                val nextChar = tokener.nextClean()
                if (nextChar != '{') break
                tokener.back()
                val obj = JSONObject(tokener)

                    val itinRec = obj.optJSONObject("1")?.optJSONObject("rec") ?: continue
                    val guid = itinRec.optJSONObject("1")?.optString("str") ?: continue
                    val legsLst = itinRec.optJSONObject("5")?.optJSONArray("lst") ?: continue
                    if (legsLst.length() <= 2) continue

                    val legs = mutableListOf<TripPlanLeg>()
                    var startOverall: Long? = null
                    var endOverall: Long? = null
                    var totalWalkDistance = 0.0
                    var totalWalkDuration = 0
                    var transfers = 0

                    for (j in 2 until legsLst.length()) {
                        val legDict = legsLst.optJSONObject(j) ?: continue

                        // 1: walkLeg
                        if (legDict.has("1")) {
                            val w = legDict.optJSONObject("1")?.optJSONObject("rec") ?: continue
                            val tRec = w.optJSONObject("1")?.optJSONObject("rec")
                            val st = tRec?.optJSONObject("1")?.optLong("i64", 0L) ?: 0L
                            val et = tRec?.optJSONObject("2")?.optLong("i64", 0L) ?: 0L
                            val dur = if (st > 0 && et > st) Math.round((et - st) / 60000.0).toInt() else 0
                            val shapeRec = w.optJSONObject("3")?.optJSONObject("rec")
                            val dist = shapeRec?.optJSONObject("1")?.optDouble("dbl", 0.0) ?: 0.0
                            val poly = shapeRec?.optJSONObject("2")?.optString("str")

                            if (startOverall == null && st > 0) startOverall = st
                            if (et > 0) endOverall = et
                            totalWalkDistance += dist
                            totalWalkDuration += dur

                            legs.add(
                                TripPlanLeg(
                                    type = TripLegType.WALK,
                                    startTimeUtc = st,
                                    endTimeUtc = et,
                                    durationMinutes = dur,
                                    distanceMeters = dist,
                                    polylineEncoded = poly
                                )
                            )
                        }
                        // 8: pathwayWalkLeg (caminhada interna de integracao)
                        else if (legDict.has("8")) {
                            val pw = legDict.optJSONObject("8")?.optJSONObject("rec") ?: continue
                            val tRec = pw.optJSONObject("1")?.optJSONObject("rec")
                            val st = tRec?.optJSONObject("1")?.optLong("i64", 0L) ?: 0L
                            val et = tRec?.optJSONObject("2")?.optLong("i64", 0L) ?: 0L
                            val dur = if (st > 0 && et > st) Math.round((et - st) / 60000.0).toInt() else 0
                            val shapeRec = pw.optJSONObject("8")?.optJSONObject("rec")
                            val dist = shapeRec?.optJSONObject("1")?.optDouble("dbl", 0.0) ?: 0.0
                            val poly = shapeRec?.optJSONObject("2")?.optString("str")

                            if (startOverall == null && st > 0) startOverall = st
                            if (et > 0) endOverall = et
                            totalWalkDistance += dist
                            totalWalkDuration += dur

                            legs.add(
                                TripPlanLeg(
                                    type = TripLegType.TRANSFER_WALK,
                                    startTimeUtc = st,
                                    endTimeUtc = et,
                                    durationMinutes = dur,
                                    distanceMeters = dist,
                                    polylineEncoded = poly
                                )
                            )
                        }
                        // 10: lineWithAlternativesLeg
                        else if (legDict.has("10")) {
                            val alt = legDict.optJSONObject("10")?.optJSONObject("rec") ?: legDict.optJSONObject("10") ?: continue
                            val altLinesLst = alt.optJSONObject("1")?.optJSONArray("lst") ?: continue
                            val altOptions = mutableListOf<TripTransitLineOption>()
                            var st = 0L
                            var et = 0L
                            var dur = 0
                            var depStopId: Int? = null
                            var fromStopName: String? = null
                            var arrStopId: Int? = null
                            var toStopName: String? = null
                            var depStopLat: Double? = null
                            var depStopLon: Double? = null
                            var arrStopLat: Double? = null
                            var arrStopLon: Double? = null
                            var poly: String? = null
                            var stopCount = 0
                            val intermediatePoints = mutableListOf<Pair<Double, Double>>()

                            for (k in 2 until altLinesLst.length()) {
                                val lItem = altLinesLst.optJSONObject(k) ?: continue
                                val lRec = lItem.optJSONObject("rec") ?: lItem
                                val lid = lRec.optJSONObject("2")?.optInt("i32", 0) ?: 0
                                val lineInfo = dbHelper.getLineInfoById(lid)

                                val tRec = lRec.optJSONObject("1")?.optJSONObject("rec") ?: lRec.optJSONObject("1")
                                val isRealTime = tRec?.optJSONObject("3")?.optInt("tf", 0) == 1
                                val liveDep = tRec?.optJSONObject("4")?.optLong("i64", 0L) ?: 0L
                                val now = System.currentTimeMillis()
                                val liveEtaMin = if (isRealTime && liveDep > 0) {
                                    maxOf(0, Math.round((liveDep - now) / 60000.0).toInt())
                                } else null

                                val opt = if (lineInfo != null) {
                                    TripTransitLineOption(
                                        lineId = lid,
                                        lineNumber = lineInfo.lineNumber,
                                        lineName = lineInfo.lineName,
                                        colorHex = lineInfo.colorHex,
                                        isMetro = lineInfo.isMetro,
                                        agencyName = lineInfo.agencyName,
                                        hasLiveGps = isRealTime,
                                        liveEtaMinutes = liveEtaMin
                                    )
                                } else {
                                    TripTransitLineOption(
                                        lineId = lid,
                                        lineNumber = lid.toString(),
                                        lineName = "",
                                        colorHex = "#007AFF",
                                        isMetro = false,
                                        agencyName = "",
                                        hasLiveGps = isRealTime,
                                        liveEtaMinutes = liveEtaMin
                                    )
                                }
                                altOptions.add(opt)

                                if (k == 2) {
                                    st = tRec?.optJSONObject("1")?.optLong("i64", 0L) ?: 0L
                                    et = tRec?.optJSONObject("2")?.optLong("i64", 0L) ?: 0L
                                    dur = if (st > 0 && et > st) Math.round((et - st) / 60000.0).toInt() else 0
                                    poly = lRec.optJSONObject("4")?.optJSONObject("rec")?.optJSONObject("2")?.optString("str")
                                        ?: lRec.optJSONObject("4")?.optJSONObject("2")?.optString("str")
                                    val stopIds = lRec.optJSONObject("3")?.optJSONArray("lst")
                                    intermediatePoints.clear()
                                    if (stopIds != null && stopIds.length() > 2) {
                                        val firstSid = stopIds.optInt(2, 0)
                                        val lastSid = stopIds.optInt(stopIds.length() - 1, 0)
                                        if (firstSid > 0) {
                                            depStopId = firstSid
                                            val s = dbHelper.getStopById(firstSid)
                                            fromStopName = s?.stopName ?: "Parada $firstSid"
                                            depStopLat = s?.latitude
                                            depStopLon = s?.longitude
                                        }
                                        if (lastSid > 0) {
                                            arrStopId = lastSid
                                            val s = dbHelper.getStopById(lastSid)
                                            toStopName = s?.stopName ?: "Parada $lastSid"
                                            arrStopLat = s?.latitude
                                            arrStopLon = s?.longitude
                                        }
                                        stopCount = stopIds.length() - 2
                                        for (s in 3 until stopIds.length() - 1) {
                                            val sid = stopIds.optInt(s, 0)
                                            if (sid > 0) {
                                                val stop = dbHelper.getStopById(sid)
                                                if (stop != null) {
                                                    intermediatePoints.add(Pair(stop.latitude, stop.longitude))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            if (altOptions.isNotEmpty()) {
                                if (startOverall == null && st > 0) startOverall = st
                                if (et > 0) endOverall = et
                                transfers++

                                legs.add(
                                    TripPlanLeg(
                                        type = TripLegType.TRANSIT,
                                        startTimeUtc = st,
                                        endTimeUtc = et,
                                        durationMinutes = dur,
                                        polylineEncoded = poly,
                                        departureStopId = depStopId,
                                        departureStopName = fromStopName,
                                        arrivalStopId = arrStopId,
                                        arrivalStopName = toStopName,
                                        departureStopLat = depStopLat,
                                        departureStopLon = depStopLon,
                                        arrivalStopLat = arrStopLat,
                                        arrivalStopLon = arrStopLon,
                                        stopsCount = stopCount,
                                        lines = altOptions,
                                        intermediateStopPoints = intermediatePoints
                                    )
                                )
                            }
                        }
                        // 3: lineLeg
                        else if (legDict.has("3")) {
                            val lItem = legDict.optJSONObject("3") ?: continue
                            val lRec = lItem.optJSONObject("rec") ?: lItem
                            val lid = lRec.optJSONObject("2")?.optInt("i32", 0) ?: 0
                            val lineInfo = dbHelper.getLineInfoById(lid)

                            val tRec = lRec.optJSONObject("1")?.optJSONObject("rec") ?: lRec.optJSONObject("1")
                            val isRealTime = tRec?.optJSONObject("3")?.optInt("tf", 0) == 1
                            val liveDep = tRec?.optJSONObject("4")?.optLong("i64", 0L) ?: 0L
                            val now = System.currentTimeMillis()
                            val liveEtaMin = if (isRealTime && liveDep > 0) {
                                maxOf(0, Math.round((liveDep - now) / 60000.0).toInt())
                            } else null

                            val opt = if (lineInfo != null) {
                                TripTransitLineOption(
                                    lineId = lid,
                                    lineNumber = lineInfo.lineNumber,
                                    lineName = lineInfo.lineName,
                                    colorHex = lineInfo.colorHex,
                                    isMetro = lineInfo.isMetro,
                                    agencyName = lineInfo.agencyName,
                                    hasLiveGps = isRealTime,
                                    liveEtaMinutes = liveEtaMin
                                )
                            } else {
                                TripTransitLineOption(
                                    lineId = lid,
                                    lineNumber = lid.toString(),
                                    lineName = "",
                                    colorHex = "#007AFF",
                                    isMetro = false,
                                    agencyName = "",
                                    hasLiveGps = isRealTime,
                                    liveEtaMinutes = liveEtaMin
                                )
                            }
                            val st = tRec?.optJSONObject("1")?.optLong("i64", 0L) ?: 0L
                            val et = tRec?.optJSONObject("2")?.optLong("i64", 0L) ?: 0L
                            val dur = if (st > 0 && et > st) Math.round((et - st) / 60000.0).toInt() else 0
                            val poly = lRec.optJSONObject("4")?.optJSONObject("rec")?.optJSONObject("2")?.optString("str")
                                ?: lRec.optJSONObject("4")?.optJSONObject("2")?.optString("str")
                            var depStopId: Int? = null
                            var fromStopName: String? = null
                            var toStopName: String? = null
                            var stopCount = 0
                            val stopIds = lRec.optJSONObject("3")?.optJSONArray("lst")
                            val intermediatePoints = mutableListOf<Pair<Double, Double>>()
                            var arrStopId: Int? = null
                            var depStopLat: Double? = null
                            var depStopLon: Double? = null
                            var arrStopLat: Double? = null
                            var arrStopLon: Double? = null
                            if (stopIds != null && stopIds.length() > 2) {
                                val firstSid = stopIds.optInt(2, 0)
                                val lastSid = stopIds.optInt(stopIds.length() - 1, 0)
                                if (firstSid > 0) {
                                    depStopId = firstSid
                                    val s = dbHelper.getStopById(firstSid)
                                    fromStopName = s?.stopName ?: "Parada $firstSid"
                                    depStopLat = s?.latitude
                                    depStopLon = s?.longitude
                                }
                                if (lastSid > 0) {
                                    arrStopId = lastSid
                                    val s = dbHelper.getStopById(lastSid)
                                    toStopName = s?.stopName ?: "Parada $lastSid"
                                    arrStopLat = s?.latitude
                                    arrStopLon = s?.longitude
                                }
                                stopCount = stopIds.length() - 2
                                for (s in 3 until stopIds.length() - 1) {
                                    val sid = stopIds.optInt(s, 0)
                                    if (sid > 0) {
                                        val stop = dbHelper.getStopById(sid)
                                        if (stop != null) {
                                            intermediatePoints.add(Pair(stop.latitude, stop.longitude))
                                        }
                                    }
                                }
                            }

                            if (startOverall == null && st > 0) startOverall = st
                            if (et > 0) endOverall = et
                            transfers++

                            legs.add(
                                TripPlanLeg(
                                    type = TripLegType.TRANSIT,
                                    startTimeUtc = st,
                                    endTimeUtc = et,
                                    durationMinutes = dur,
                                    polylineEncoded = poly,
                                    departureStopId = depStopId,
                                    departureStopName = fromStopName,
                                    arrivalStopId = arrStopId,
                                    arrivalStopName = toStopName,
                                    departureStopLat = depStopLat,
                                    departureStopLon = depStopLon,
                                    arrivalStopLat = arrStopLat,
                                    arrivalStopLon = arrStopLon,
                                    stopsCount = stopCount,
                                    lines = listOf(opt),
                                    intermediateStopPoints = intermediatePoints
                                )
                            )
                        }
                    }

                    if (legs.isNotEmpty()) {
                        val finalTransfers = Math.max(0, transfers - 1)
                        val totalDuration = if (startOverall != null && endOverall != null && endOverall > startOverall) {
                            Math.round((endOverall - startOverall) / 60000.0).toInt()
                        } else {
                            legs.sumOf { it.durationMinutes }
                        }

                        itineraries.add(
                            TripPlanItinerary(
                                guid = guid,
                                totalDurationMinutes = totalDuration,
                                startTimeUtc = startOverall ?: System.currentTimeMillis(),
                                endTimeUtc = endOverall ?: (System.currentTimeMillis() + totalDuration * 60000L),
                                walkDurationMinutes = totalWalkDuration,
                                walkDistanceMeters = totalWalkDistance,
                                transfersCount = finalTransfers,
                                legs = legs
                            )
                        )
                    }
                }
        } catch (_: Exception) {
        }

        return itineraries.sortedBy { it.totalDurationMinutes }
    }

}
