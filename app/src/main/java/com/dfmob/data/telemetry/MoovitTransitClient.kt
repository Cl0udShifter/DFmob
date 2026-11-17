package com.dfmob.data.telemetry

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MoovitTransitClient {

    private const val ARRIVALS_URL = "https://app5.moovitapp.com/services-app/services/V4/StopsArrivals"
    private const val CLIENT_VERSION = "5.204.1.1814"
    private const val PHONE_TYPE = "1"
    private const val API_KEY = "moovit_2751703405"
    private const val USER_KEY = "2BF18CA7E497970E2F02F4EAA0BE8461"
    private const val METRO_ID = "1702"
    private const val METRO_REVISION = "1794464576502"

    data class RawArrival(
        val patternId: Int,
        val tripId: Long,
        val staticEtdUTC: Long,
        val rtEtdUTC: Long?,
        val vehicleId: String?
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
                setRequestProperty("API_KEY", API_KEY)
                setRequestProperty("USER_KEY", USER_KEY)
                setRequestProperty("Metro-Revision-Metro-Id", METRO_ID)
                setRequestProperty("Metro-Revision-Number", METRO_REVISION)
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

                                arrivals.add(
                                    RawArrival(
                                        patternId = patternId,
                                        tripId = tripId,
                                        staticEtdUTC = staticEtdUTC,
                                        rtEtdUTC = rtEtdUTC,
                                        vehicleId = vehicleId
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
}
