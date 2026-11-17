package com.dfmob.data.telemetry

import com.dfmob.BuildConfig
import com.dfmob.data.model.LiveVehicleMarker
import com.dfmob.data.model.TransitStop
import org.json.JSONObject
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
        107, 109, 99, 110, 110, 108, 110, 111, 109, 108, 111, 106, 104
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
    private fun getMetroRevision(): String = deobfuscate(OBFUSCATED_METRO_REVISION)

    private const val LINE_ARRIVALS_URL = "https://app5.moovitapp.com/services-app/services/V4/LineArrivals"

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
        var conn: HttpURLConnection? = null
        val result = mutableListOf<RawLineArrivals>()

        try {
            val url = URL(ARRIVALS_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Host", "app5.moovitapp.com")
                setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; 2412DPC0AG Build/UKQ1.231003.002)")
                setRequestProperty("CLIENT_VERSION", CLIENT_VERSION)
                setRequestProperty("PHONE_TYPE", PHONE_TYPE)
                setRequestProperty("API_KEY", getApiKey())
                setRequestProperty("USER_KEY", getUserKey())
                setRequestProperty("Metro-Revision-Metro-Id", getMetroId())
                setRequestProperty("Metro-Revision-Number", getMetroRevision())
                setRequestProperty("Content-Type", "application/octet")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Length", payload.size.toString())
            }

            conn.outputStream.use { os ->
                os.write(payload)
                os.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
            }
        } catch (_: Exception) {
            // Silencioso: fallback suave para banco SQLite local em caso de instabilidade de rede
        } finally {
            conn?.disconnect()
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
        var conn: HttpURLConnection? = null
        val vehiclesMap = mutableMapOf<String, LiveVehicleMarker>()

        try {
            val url = URL(LINE_ARRIVALS_URL)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Host", "app5.moovitapp.com")
                setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14; 2412DPC0AG Build/UKQ1.231003.002)")
                setRequestProperty("CLIENT_VERSION", CLIENT_VERSION)
                setRequestProperty("PHONE_TYPE", PHONE_TYPE)
                setRequestProperty("API_KEY", getApiKey())
                setRequestProperty("USER_KEY", getUserKey())
                setRequestProperty("Metro-Revision-Metro-Id", getMetroId())
                setRequestProperty("Metro-Revision-Number", getMetroRevision())
                setRequestProperty("Content-Type", "application/octet")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Length", payload.size.toString())
            }

            conn.outputStream.use { os ->
                os.write(payload)
                os.flush()
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
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
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
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
}
