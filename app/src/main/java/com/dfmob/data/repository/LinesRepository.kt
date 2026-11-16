package com.dfmob.data.repository

import android.content.Context
import com.dfmob.data.model.TransitLine
import com.dfmob.data.model.TransitModeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer

class LinesRepository(private val context: Context) {

    private var cachedLines: List<TransitLine> = emptyList()
    private val agenciesMap = mutableMapOf<String, String>()

    suspend fun loadLines(): List<TransitLine> = withContext(Dispatchers.IO) {
        if (cachedLines.isNotEmpty()) {
            return@withContext cachedLines
        }

        loadAgencies()
        val lines = loadRoutesFromStorageOrAsset()
        cachedLines = lines

        // Tenta atualizar em segundo plano se houver conexao
        try {
            fetchRemoteRoutes()?.let { remoteLines ->
                if (remoteLines.isNotEmpty()) {
                    cachedLines = remoteLines
                }
            }
        } catch (_: Exception) {
            // Continua com os dados locais
        }

        cachedLines
    }

    fun searchLines(
        lines: List<TransitLine>,
        mode: TransitModeFilter,
        query: String
    ): List<TransitLine> {
        val targetType = when (mode) {
            TransitModeFilter.BUS -> 3
            TransitModeFilter.METRO -> 1
        }

        val modeFiltered = lines.filter { it.transitType == targetType }

        val cleanQuery = normalizeString(query.trim())
        if (cleanQuery.isEmpty()) {
            return modeFiltered
        }

        return modeFiltered.filter { line ->
            val normNumber = normalizeString(line.lineNumber)
            val normName = normalizeString(line.routeName)
            val normAgency = normalizeString(line.agencyName)

            normNumber.startsWith(cleanQuery) ||
                    normNumber.contains(cleanQuery) ||
                    normName.contains(cleanQuery) ||
                    normAgency.contains(cleanQuery)
        }
    }

    private fun loadAgencies() {
        if (agenciesMap.isNotEmpty()) return
        try {
            val jsonString = context.assets.open("data/agencies.json").use { it.bufferedReader().readText() }
            val array = JSONArray(jsonString)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("agencyId")
                val name = obj.optString("name")
                if (id.isNotBlank() && name.isNotBlank()) {
                    agenciesMap[id] = name
                }
            }
        } catch (_: Exception) {
            // Fallback manual oficial das concessionarias do DF
            agenciesMap["1pq1"] = "Urbi Mobilidade"
            agenciesMap["1pq2"] = "Viação Pioneira"
            agenciesMap["1pq3"] = "Viação Piracicabana"
            agenciesMap["1pq4"] = "Transporte Complementar Rural"
            agenciesMap["1pq5"] = "Operação Consorciada"
            agenciesMap["1pq6"] = "BsBus Mobilidade"
            agenciesMap["1pq7"] = "Viação Marechal"
            agenciesMap["1pq8"] = "Coobrataete"
            agenciesMap["29ks"] = "Metrô-DF"
        }
    }

    private fun loadRoutesFromStorageOrAsset(): List<TransitLine> {
        // Verifica se ha arquivo atualizado em cache
        val cachedFile = File(context.cacheDir, "routes_cache.json")
        val inputStream: InputStream = if (cachedFile.exists() && cachedFile.length() > 1000) {
            cachedFile.inputStream()
        } else {
            context.assets.open("data/routes.json")
        }

        return inputStream.use { stream ->
            val text = stream.bufferedReader().readText()
            parseRoutesJson(text)
        }
    }

    private suspend fun fetchRemoteRoutes(): List<TransitLine>? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://mobilibus.com/api/routes?project_hash=3c189")
            conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "DFmob-Android")
            }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = parseRoutesJson(text)
                if (parsed.isNotEmpty()) {
                    File(context.cacheDir, "routes_cache.json").writeText(text)
                    return@withContext parsed
                }
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseRoutesJson(jsonText: String): List<TransitLine> {
        val list = mutableListOf<TransitLine>()
        try {
            val array = JSONArray(jsonText)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val routeId = obj.optString("routeId", "")
                val agencyId = obj.optString("agencyId", "")
                val rawShortName = obj.optString("shortName", "")
                val rawLongName = obj.optString("longName", "")
                val type = obj.optInt("type", 3)
                val color = obj.optString("color", if (type == 1) "#159900" else "#00D26A")
                val textColor = obj.optString("textColor", "#FFFFFF")
                val price = if (obj.has("price") && !obj.isNull("price")) obj.optDouble("price") else null

                val agencyName = agenciesMap[agencyId] ?: when (type) {
                    1 -> "Metrô-DF"
                    else -> "STPC / SEMOB-DF"
                }

                val (lineNumber, routeName) = if (type == 1) {
                    // Linhas do Metro DF
                    val metroColorName = if (color.contains("d65600", ignoreCase = true) || rawLongName.contains("Samambaia", ignoreCase = true)) {
                        "LARANJA"
                    } else {
                        "VERDE"
                    }
                    val formattedMetroName = "Central ↔ Estação Terminal $rawLongName"
                    Pair(metroColorName, formattedMetroName)
                } else {
                    val lineNum = if (rawShortName.isBlank() || rawShortName == "null") "SEMOB" else rawShortName
                    Pair(lineNum, rawLongName)
                }

                list.add(
                    TransitLine(
                        routeId = routeId,
                        agencyId = agencyId,
                        agencyName = agencyName,
                        lineNumber = lineNumber,
                        routeName = routeName,
                        transitType = type,
                        colorHex = color,
                        textColorHex = textColor,
                        price = price
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun normalizeString(input: String): String {
        return Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .lowercase()
    }
}
