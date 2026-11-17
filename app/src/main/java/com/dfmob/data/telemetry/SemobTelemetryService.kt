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
        val timestampSeconds: Long
    )

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
            val urlString = "http://geoserver.semob.df.gov.br/geoserver/semob/wfs?service=WFS&version=1.0.0&request=GetFeature&typeName=semob:ultima_posicao&outputFormat=application/json&maxFeatures=15&cql_filter=$encodedCql"
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 400
                readTimeout = 600
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
                                val speed = props.optDouble("velocidade", 0.0).toFloat()

                                list.add(
                                    LiveVehicle(
                                        vehicleId = prefixo,
                                        lineNumber = linhaCod,
                                        latE6 = (lat * 1_000_000).toInt(),
                                        lonE6 = (lon * 1_000_000).toInt(),
                                        speedKmh = speed,
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
}
