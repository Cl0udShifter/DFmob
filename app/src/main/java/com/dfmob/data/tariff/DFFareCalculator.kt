package com.dfmob.data.tariff

import android.content.Context
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanItinerary
import org.json.JSONArray
import java.io.File

object DFFareCalculator {

    private val priceMap = mutableMapOf<String, Double>()
    @Volatile
    private var isLoaded = false

    fun loadPricesIfNeeded(context: Context) {
        if (isLoaded) return
        synchronized(this) {
            if (isLoaded) return
            try {
                val cachedFile = File(context.cacheDir, "routes_cache.json")
                val text = if (cachedFile.exists() && cachedFile.length() > 1000) {
                    cachedFile.readText()
                } else {
                    context.assets.open("data/routes.json").bufferedReader().use { it.readText() }
                }
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val shortName = obj.optString("shortName", "").trim()
                    if (shortName.isNotEmpty() && obj.has("price") && !obj.isNull("price")) {
                        val price = obj.optDouble("price", 5.50)
                        if (price > 0.0) {
                            priceMap[shortName.lowercase()] = price
                        }
                    }
                }
                isLoaded = true
            } catch (_: Exception) {}
        }
    }

    fun getLinePrice(lineNumber: String, isMetro: Boolean): Double {
        if (isMetro) return 5.50
        val clean = lineNumber.trim().lowercase()
        return priceMap[clean] ?: 5.50
    }

    /**
     * Calcula a tarifa total estimada aplicando as regras de integracao tarifaria do STPC/DF
     * (Cartao Mobilidade / Bilhete Unico: teto de R$ 5,50 em ate 3 viagens integradas).
     */
    fun calculateItineraryFare(itinerary: TripPlanItinerary): Pair<Double, Boolean> {
        val transitLegs = itinerary.legs.filter { it.type == TripLegType.TRANSIT && it.lines.isNotEmpty() }
        if (transitLegs.isEmpty()) {
            return Pair(0.0, false)
        }

        // Obtem a tarifa de cada perna de transporte utilizada
        val legFares = transitLegs.map { leg ->
            val firstLine = leg.lines.firstOrNull()
            if (firstLine != null) {
                getLinePrice(firstLine.lineNumber, firstLine.isMetro)
            } else {
                5.50
            }
        }

        // Regra do Bilhete Unico do DF:
        // A tarifa total de uma viagem integrada e igual a maior tarifa entre as pernas utilizadas,
        // limitada ao teto de R$ 5,50 (para ate 3 pernas dentro do periodo de integracao).
        val maxLegFare = legFares.maxOrNull() ?: 5.50
        val integratedFare = maxLegFare.coerceAtMost(5.50)
        val isIntegrated = transitLegs.size > 1

        return Pair(integratedFare, isIntegrated)
    }
}
