package com.dfmob.data.telemetry

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

class SemobTelemetryService {

    data class LiveVehicle(
        val vehicleId: String,
        val lineNumber: String,
        val latE6: Int,
        val lonE6: Int,
        val speedKmh: Float,
        val bearing: Float = 0f,
        val directionId: Int? = null,
        val timestampSeconds: Long
    )

    private val shapesCache = mutableMapOf<String, Map<String, List<Pair<Double, Double>>>>()

    fun getCachedShapes(lineNumber: String): Map<String, List<Pair<Double, Double>>>? {
        val clean = lineNumber.trim()
        return shapesCache[clean]
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun getLiveVehiclePositions(
        lineNumber: String,
        referenceLatE6: Int? = null,
        referenceLonE6: Int? = null
    ): List<LiveVehicle> = withContext(Dispatchers.IO) {
        val list = mutableListOf<LiveVehicle>()
        val cleanLine = lineNumber.trim()
        if (cleanLine.isBlank() || cleanLine.startsWith("Linha", ignoreCase = true)) {
            return@withContext emptyList()
        }

        var conn: HttpURLConnection? = null
        try {
            val cql = "cd_linha='$cleanLine'"
            val encodedCql = URLEncoder.encode(cql, "UTF-8")
            val urlString = "http://geoserver.semob.df.gov.br/geoserver/semob/wfs?service=WFS&version=1.0.0&request=GetFeature&typeName=semob:ultima_posicao&outputFormat=application/json&maxFeatures=50&cql_filter=$encodedCql"
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "DFmob-Android")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val stream = conn.inputStream
                val text = stream.bufferedReader().use { it.readText() }
                if (text.isNotBlank() && text.trimStart().startsWith("{")) {
                    val root = JSONObject(text)
                    val features = root.optJSONArray("features")
                    if (features != null) {
                        val now = System.currentTimeMillis() / 1000
                        for (i in 0 until features.length()) {
                            val feat = features.getJSONObject(i)
                            val props = feat.optJSONObject("properties") ?: continue
                            val geom = feat.optJSONObject("geometry")

                            var lat: Double? = null
                            var lon: Double? = null

                            if (geom != null) {
                                val coords = geom.optJSONArray("coordinates")
                                if (coords != null && coords.length() >= 2) {
                                    lon = coords.getDouble(0)
                                    lat = coords.getDouble(1)
                                }
                            }

                            if (lat == null || lon == null) {
                                if (props.has("latitude") && props.has("longitude")) {
                                    lat = props.getDouble("latitude")
                                    lon = props.getDouble("longitude")
                                }
                            }

                            if (lat != null && lon != null && lat != 0.0 && lon != 0.0) {
                                val prefixo = props.optString("prefixo", "DF-$i")
                                val linhaCod = props.optString("cd_linha", cleanLine)

                                val velRaw = props.optString("velocidade", "0").replace(",", ".").trim()
                                val speed = velRaw.toFloatOrNull() ?: 0f

                                val direcaoRaw = props.optString("direcao", "0").replace(",", ".").trim()
                                val bearing = direcaoRaw.toFloatOrNull() ?: 0f

                                val sentidoRaw = props.optString("sentido", "").trim()
                                val dirId = when {
                                    sentidoRaw == "0" -> 0
                                    sentidoRaw == "1" -> 1
                                    sentidoRaw.startsWith("0") -> 0
                                    sentidoRaw.startsWith("1") -> 1
                                    else -> null
                                }

                                list.add(
                                    LiveVehicle(
                                        vehicleId = prefixo,
                                        lineNumber = linhaCod,
                                        latE6 = (lat * 1_000_000).toInt(),
                                        lonE6 = (lon * 1_000_000).toInt(),
                                        speedKmh = speed,
                                        bearing = bearing,
                                        directionId = dirId,
                                        timestampSeconds = now
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Falha graciosa se GeoServer Semob estiver fora do ar
        } finally {
            conn?.disconnect()
        }

        list
    }

    suspend fun fetchRealRouteShapes(lineNumber: String): Map<String, List<Pair<Double, Double>>> = withContext(Dispatchers.IO) {
        val clean = lineNumber.trim()
        if (clean.isBlank()) return@withContext emptyMap()
        shapesCache[clean]?.let { return@withContext it }

        var conn: HttpURLConnection? = null
        val result = mutableMapOf<String, List<Pair<Double, Double>>>()
        try {
            val cql = "cd_linha='$clean'"
            val encodedCql = URLEncoder.encode(cql, "UTF-8")
            val urlString = "http://geoserver.semob.df.gov.br/geoserver/semob/wfs?service=WFS&version=1.0.0&request=GetFeature&typeName=semob:percuso%20das%20linhas&outputFormat=application/json&srsName=EPSG:4326&maxFeatures=10&cql_filter=$encodedCql"
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 5000
                setRequestProperty("User-Agent", "DFmob-Android")
                setRequestProperty("Accept", "application/json")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val stream = conn.inputStream
                val text = stream.bufferedReader().use { it.readText() }
                if (text.isNotBlank() && text.trimStart().startsWith("{")) {
                    val root = JSONObject(text)
                    val features = root.optJSONArray("features")
                    if (features != null) {
                        for (i in 0 until features.length()) {
                            val feat = features.getJSONObject(i)
                            val props = feat.optJSONObject("properties") ?: continue
                            val geom = feat.optJSONObject("geometry") ?: continue
                            val sentido = props.optString("lin_sentido", "CIRCULAR_$i").trim().uppercase()
                            val coordsArray = geom.optJSONArray("coordinates")
                            if (coordsArray != null && coordsArray.length() > 0) {
                                val pts = mutableListOf<Pair<Double, Double>>()
                                for (j in 0 until coordsArray.length()) {
                                    val pt = coordsArray.optJSONArray(j)
                                    if (pt != null && pt.length() >= 2) {
                                        val lon = pt.optDouble(0)
                                        val lat = pt.optDouble(1)
                                        if (lat != 0.0 && lon != 0.0) {
                                            pts.add(Pair(lat, lon))
                                        }
                                    }
                                }
                                if (pts.isNotEmpty()) {
                                    result[sentido] = pts
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            conn?.disconnect()
        }

        if (result.isNotEmpty()) {
            shapesCache[clean] = result
        }
        result
    }
}
