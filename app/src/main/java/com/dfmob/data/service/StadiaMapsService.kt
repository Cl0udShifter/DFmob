package com.dfmob.data.service

import com.dfmob.BuildConfig
import com.dfmob.data.model.DFLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

class StadiaMapsService(
    private val apiKey: String = BuildConfig.STADIA_API_KEY
) {
    // Cache LRU em memoria para 50 buscas recentes evitando chamadas de rede repetidas
    private val memoryCache = object : LinkedHashMap<String, List<DFLocation>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<DFLocation>>?): Boolean {
            return size > 50
        }
    }

    suspend fun autocomplete(query: String): List<DFLocation> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        // Regra de otimizacao: minimo 3 caracteres
        if (cleanQuery.length < 3) return@withContext emptyList()

        val cacheKey = cleanQuery.lowercase(Locale.ROOT)
        synchronized(memoryCache) {
            memoryCache[cacheKey]?.let { return@withContext it }
        }

        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            // Foco geografico rigoroso no quadrilatero do Distrito Federal
            val urlString = "https://api.stadiamaps.com/geocoding/v1/autocomplete" +
                    "?text=$encodedQuery" +
                    "&focus.point.lat=-15.7939" +
                    "&focus.point.lon=-47.8828" +
                    "&boundary.country=BRA" +
                    "&api_key=$apiKey"

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "DFmob/1.4.6 (Android)")
            }

            if (connection.responseCode != 200) {
                return@withContext emptyList()
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseBody)
            val features = json.optJSONArray("features") ?: return@withContext emptyList()

            val locations = mutableListOf<DFLocation>()
            for (i in 0 until features.length()) {
                val feature = features.optJSONObject(i) ?: continue
                val properties = feature.optJSONObject("properties") ?: continue
                val geometry = feature.optJSONObject("geometry") ?: continue
                val coordinates = geometry.optJSONArray("coordinates") ?: continue

                if (coordinates.length() >= 2) {
                    val lon = coordinates.optDouble(0)
                    val lat = coordinates.optDouble(1)

                    // Filtro de delimitacao do DF e entorno imediato
                    if (lat in -16.3..-15.4 && lon in -48.4..-47.2) {
                        val name = properties.optString("name").ifBlank {
                            properties.optString("label")
                        }
                        val county = properties.optString("county")
                        val region = properties.optString("region")
                        val locality = properties.optString("locality")

                        val descParts = listOf(county, locality, region).filter { it.isNotBlank() }
                        val description = if (descParts.isNotEmpty()) {
                            descParts.distinct().joinToString(", ")
                        } else {
                            "Distrito Federal, Brasil"
                        }

                        locations.add(
                            DFLocation(
                                name = name,
                                description = description,
                                latE6 = (lat * 1_000_000).toInt(),
                                lonE6 = (lon * 1_000_000).toInt()
                            )
                        )
                    }
                }
            }

            synchronized(memoryCache) {
                memoryCache[cacheKey] = locations
            }
            locations
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun checkApiStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val testUrl = URL("https://api.stadiamaps.com/geocoding/v1/autocomplete?text=Brasilia&api_key=$apiKey")
            val connection = (testUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "DFmob/1.4.6 (Android)")
            }
            connection.responseCode == 200
        } catch (_: Exception) {
            false
        }
    }
}
