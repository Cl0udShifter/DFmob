package com.dfmob.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.dfmob.data.model.FavoriteTrip
import com.dfmob.data.model.TransitLine
import com.dfmob.data.model.TransitStop
import com.dfmob.data.model.TripPlanLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class FavoritesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dfmob_favorites", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAVORITES = "favorite_route_ids"
        private const val KEY_FAVORITE_LINES_JSON = "favorite_lines_json"
        private const val KEY_FAVORITE_STOPS = "favorite_stop_ids"
        private const val KEY_FAVORITE_STOPS_JSON = "favorite_stops_json"
        private const val KEY_FAVORITE_TRIPS_JSON = "favorite_trips_json"

        fun getLineKey(line: TransitLine): String {
            return if (line.isCircular || line.directionId == null) {
                line.lineNumber
            } else {
                "${line.lineNumber}_${line.directionId}"
            }
        }
    }

    private val _favoriteLines = MutableStateFlow<List<TransitLine>>(loadFavoriteLinesFromPrefs())
    val favoriteLines: StateFlow<List<TransitLine>> = _favoriteLines.asStateFlow()

    private val _favoriteStops = MutableStateFlow<List<TransitStop>>(loadFavoriteStopsFromPrefs())
    val favoriteStops: StateFlow<List<TransitStop>> = _favoriteStops.asStateFlow()

    private fun loadFavoriteStopsFromPrefs(): List<TransitStop> {
        val rawJson = prefs.getString(KEY_FAVORITE_STOPS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(rawJson)
            val list = mutableListOf<TransitStop>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TransitStop(
                        stopId = obj.getInt("stopId"),
                        stopName = obj.getString("stopName"),
                        stopCode = if (obj.has("stopCode") && !obj.isNull("stopCode")) obj.getString("stopCode") else null,
                        latE6 = obj.getInt("latE6"),
                        lonE6 = obj.getInt("lonE6"),
                        isMetro = obj.optBoolean("isMetro", false),
                        platformCode = if (obj.has("platformCode") && !obj.isNull("platformCode")) obj.getString("platformCode") else null
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getFavoriteStopIds(): Set<Int> {
        val stringSet = prefs.getStringSet(KEY_FAVORITE_STOPS, emptySet()) ?: emptySet()
        return stringSet.mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun isFavoriteStop(stopId: Int): Boolean {
        return getFavoriteStopIds().contains(stopId)
    }

    fun toggleFavoriteStop(stop: TransitStop): Boolean {
        val currentIds = prefs.getStringSet(KEY_FAVORITE_STOPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val currentStops = _favoriteStops.value.toMutableList()
        val stopIdStr = stop.stopId.toString()
        val isNowFavorite: Boolean

        if (currentIds.contains(stopIdStr)) {
            currentIds.remove(stopIdStr)
            currentStops.removeAll { it.stopId == stop.stopId }
            isNowFavorite = false
        } else {
            currentIds.add(stopIdStr)
            currentStops.removeAll { it.stopId == stop.stopId }
            currentStops.add(stop)
            isNowFavorite = true
        }

        val editor = prefs.edit()
        editor.putStringSet(KEY_FAVORITE_STOPS, currentIds)

        val array = JSONArray()
        for (item in currentStops) {
            val obj = JSONObject()
            obj.put("stopId", item.stopId)
            obj.put("stopName", item.stopName)
            if (item.stopCode != null) obj.put("stopCode", item.stopCode)
            obj.put("latE6", item.latE6)
            obj.put("lonE6", item.lonE6)
            obj.put("isMetro", item.isMetro)
            if (item.platformCode != null) obj.put("platformCode", item.platformCode)
            array.put(obj)
        }
        editor.putString(KEY_FAVORITE_STOPS_JSON, array.toString())
        editor.apply()

        _favoriteStops.value = currentStops
        return isNowFavorite
    }

    fun getFavoriteStopsSync(): List<TransitStop> {
        return _favoriteStops.value
    }

    init {
        // Migracao para nova estrutura de favoritos com direcao especifica:
        // Limpa a lista de linhas favoritas antigas uma unica vez, mantendo as paradas intactas
        val isMigrated = prefs.getBoolean("favorites_lines_v2_migrated", false)
        if (!isMigrated) {
            prefs.edit()
                .remove(KEY_FAVORITES)
                .remove(KEY_FAVORITE_LINES_JSON)
                .putBoolean("favorites_lines_v2_migrated", true)
                .apply()
            _favoriteLines.value = emptyList()
        }
    }

    private fun loadFavoriteLinesFromPrefs(): List<TransitLine> {
        val rawJson = prefs.getString(KEY_FAVORITE_LINES_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(rawJson)
            val list = mutableListOf<TransitLine>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    TransitLine(
                        routeId = obj.getString("routeId"),
                        agencyId = obj.optString("agencyId", ""),
                        agencyName = obj.optString("agencyName", ""),
                        lineNumber = obj.optString("lineNumber", ""),
                        routeName = obj.optString("routeName", ""),
                        transitType = obj.optInt("transitType", 3),
                        colorHex = obj.optString("colorHex", "#00DC64"),
                        textColorHex = obj.optString("textColorHex", "#000000"),
                        price = if (obj.has("price") && !obj.isNull("price")) obj.getDouble("price") else null,
                        directionId = if (obj.has("directionId") && !obj.isNull("directionId")) obj.getInt("directionId") else null,
                        destinationName = if (obj.has("destinationName") && !obj.isNull("destinationName")) obj.getString("destinationName") else null,
                        isCircular = obj.optBoolean("isCircular", false)
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getFavoriteRouteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun isFavorite(routeId: String): Boolean {
        return getFavoriteRouteIds().contains(routeId)
    }

    fun isLineFavorite(lineNumber: String, directionId: Int? = null, isCircular: Boolean = false): Boolean {
        return if (isCircular || directionId == null) {
            _favoriteLines.value.any { it.lineNumber == lineNumber }
        } else {
            _favoriteLines.value.any { it.lineNumber == lineNumber && (it.directionId == null || it.directionId == directionId) }
        }
    }

    fun toggleFavorite(line: TransitLine): Boolean {
        val key = getLineKey(line)
        val currentIds = getFavoriteRouteIds().toMutableSet()
        val currentLines = _favoriteLines.value.toMutableList()
        val isNowFavorite: Boolean

        val existingIndex = currentLines.indexOfFirst { getLineKey(it) == key }

        if (existingIndex >= 0) {
            currentIds.remove(line.routeId)
            currentIds.remove(key)
            currentLines.removeAt(existingIndex)
            isNowFavorite = false
        } else {
            currentIds.add(key)
            currentLines.add(line)
            isNowFavorite = true
        }

        val editor = prefs.edit()
        editor.putStringSet(KEY_FAVORITES, currentIds)

        val array = JSONArray()
        for (item in currentLines) {
            val obj = JSONObject()
            obj.put("routeId", item.routeId)
            obj.put("agencyId", item.agencyId)
            obj.put("agencyName", item.agencyName)
            obj.put("lineNumber", item.lineNumber)
            obj.put("routeName", item.routeName)
            obj.put("transitType", item.transitType)
            obj.put("colorHex", item.colorHex)
            obj.put("textColorHex", item.textColorHex)
            if (item.price != null) {
                obj.put("price", item.price)
            }
            if (item.directionId != null) {
                obj.put("directionId", item.directionId)
            }
            if (item.destinationName != null) {
                obj.put("destinationName", item.destinationName)
            }
            obj.put("isCircular", item.isCircular)
            array.put(obj)
        }
        editor.putString(KEY_FAVORITE_LINES_JSON, array.toString())
        editor.apply()

        _favoriteLines.value = currentLines
        return isNowFavorite
    }

    fun syncWithAllLines(allLines: List<TransitLine>) {
        val ids = getFavoriteRouteIds()
        if (ids.isNotEmpty() && _favoriteLines.value.isEmpty()) {
            val matching = allLines.filter { ids.contains(it.routeId) }
            if (matching.isNotEmpty()) {
                val array = JSONArray()
                for (item in matching) {
                    val obj = JSONObject()
                    obj.put("routeId", item.routeId)
                    obj.put("agencyId", item.agencyId)
                    obj.put("agencyName", item.agencyName)
                    obj.put("lineNumber", item.lineNumber)
                    obj.put("routeName", item.routeName)
                    obj.put("transitType", item.transitType)
                    obj.put("colorHex", item.colorHex)
                    obj.put("textColorHex", item.textColorHex)
                    if (item.price != null) {
                        obj.put("price", item.price)
                    }
                    array.put(obj)
                }
                prefs.edit().putString(KEY_FAVORITE_LINES_JSON, array.toString()).apply()
                _favoriteLines.value = matching
            }
        }
    }

    private val _favoriteTrips = MutableStateFlow<List<FavoriteTrip>>(loadFavoriteTripsFromPrefs())
    val favoriteTrips: StateFlow<List<FavoriteTrip>> = _favoriteTrips.asStateFlow()

    private fun loadFavoriteTripsFromPrefs(): List<FavoriteTrip> {
        val rawJson = prefs.getString(KEY_FAVORITE_TRIPS_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(rawJson)
            val list = mutableListOf<FavoriteTrip>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val origObj = obj.getJSONObject("origin")
                val destObj = obj.getJSONObject("destination")
                list.add(
                    FavoriteTrip(
                        id = obj.getString("id"),
                        customName = obj.getString("customName"),
                        origin = TripPlanLocation(
                            caption = origObj.getString("caption"),
                            latE6 = origObj.getInt("latE6"),
                            lonE6 = origObj.getInt("lonE6"),
                            isUserLocation = origObj.optBoolean("isUserLocation", false)
                        ),
                        destination = TripPlanLocation(
                            caption = destObj.getString("caption"),
                            latE6 = destObj.getInt("latE6"),
                            lonE6 = destObj.getInt("lonE6"),
                            isUserLocation = destObj.optBoolean("isUserLocation", false)
                        )
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addFavoriteTrip(customName: String, origin: TripPlanLocation, destination: TripPlanLocation): FavoriteTrip {
        val current = _favoriteTrips.value.toMutableList()
        current.removeAll {
            (it.origin.latE6 == origin.latE6 && it.origin.lonE6 == origin.lonE6 &&
             it.destination.latE6 == destination.latE6 && it.destination.lonE6 == destination.lonE6) ||
            it.customName.equals(customName, ignoreCase = true)
        }
        val trip = FavoriteTrip(
            customName = customName,
            origin = origin,
            destination = destination
        )
        current.add(0, trip)
        saveFavoriteTripsToPrefs(current)
        _favoriteTrips.value = current
        return trip
    }

    fun removeFavoriteTrip(tripId: String) {
        val current = _favoriteTrips.value.toMutableList()
        current.removeAll { it.id == tripId }
        saveFavoriteTripsToPrefs(current)
        _favoriteTrips.value = current
    }

    fun removeFavoriteStop(stop: TransitStop) {
        val currentIds = prefs.getStringSet(KEY_FAVORITE_STOPS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val currentStops = _favoriteStops.value.toMutableList()
        currentIds.remove(stop.stopId.toString())
        currentStops.removeAll { it.stopId == stop.stopId }
        val editor = prefs.edit()
        editor.putStringSet(KEY_FAVORITE_STOPS, currentIds)
        val array = JSONArray()
        for (item in currentStops) {
            val obj = JSONObject()
            obj.put("stopId", item.stopId)
            obj.put("stopName", item.stopName)
            if (item.stopCode != null) obj.put("stopCode", item.stopCode)
            obj.put("latE6", item.latE6)
            obj.put("lonE6", item.lonE6)
            obj.put("isMetro", item.isMetro)
            if (item.platformCode != null) obj.put("platformCode", item.platformCode)
            array.put(obj)
        }
        editor.putString(KEY_FAVORITE_STOPS_JSON, array.toString())
        editor.apply()
        _favoriteStops.value = currentStops
    }

    fun removeFavoriteLine(line: TransitLine) {
        val key = getLineKey(line)
        val currentIds = getFavoriteRouteIds().toMutableSet()
        val currentLines = _favoriteLines.value.toMutableList()
        currentIds.remove(line.routeId)
        currentIds.remove(key)
        currentLines.removeAll { getLineKey(it) == key }
        val editor = prefs.edit()
        editor.putStringSet(KEY_FAVORITES, currentIds)
        val array = JSONArray()
        for (item in currentLines) {
            val obj = JSONObject()
            obj.put("routeId", item.routeId)
            obj.put("agencyId", item.agencyId)
            obj.put("agencyName", item.agencyName)
            obj.put("lineNumber", item.lineNumber)
            obj.put("routeName", item.routeName)
            obj.put("transitType", item.transitType)
            obj.put("colorHex", item.colorHex)
            obj.put("textColorHex", item.textColorHex)
            if (item.price != null) obj.put("price", item.price)
            if (item.directionId != null) obj.put("directionId", item.directionId)
            if (item.destinationName != null) obj.put("destinationName", item.destinationName)
            obj.put("isCircular", item.isCircular)
            array.put(obj)
        }
        editor.putString(KEY_FAVORITE_LINES_JSON, array.toString())
        editor.apply()
        _favoriteLines.value = currentLines
    }

    fun isTripFavorite(origin: TripPlanLocation?, destination: TripPlanLocation?): Boolean {
        if (origin == null || destination == null) return false
        return _favoriteTrips.value.any {
            it.origin.latE6 == origin.latE6 && it.origin.lonE6 == origin.lonE6 &&
            it.destination.latE6 == destination.latE6 && it.destination.lonE6 == destination.lonE6
        }
    }

    fun getFavoriteTrip(origin: TripPlanLocation?, destination: TripPlanLocation?): FavoriteTrip? {
        if (origin == null || destination == null) return null
        return _favoriteTrips.value.firstOrNull {
            it.origin.latE6 == origin.latE6 && it.origin.lonE6 == origin.lonE6 &&
            it.destination.latE6 == destination.latE6 && it.destination.lonE6 == destination.lonE6
        }
    }

    private fun saveFavoriteTripsToPrefs(trips: List<FavoriteTrip>) {
        val array = JSONArray()
        for (item in trips) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("customName", item.customName)
            val origObj = JSONObject()
            origObj.put("caption", item.origin.caption)
            origObj.put("latE6", item.origin.latE6)
            origObj.put("lonE6", item.origin.lonE6)
            origObj.put("isUserLocation", item.origin.isUserLocation)
            obj.put("origin", origObj)

            val destObj = JSONObject()
            destObj.put("caption", item.destination.caption)
            destObj.put("latE6", item.destination.latE6)
            destObj.put("lonE6", item.destination.lonE6)
            destObj.put("isUserLocation", item.destination.isUserLocation)
            obj.put("destination", destObj)

            array.put(obj)
        }
        prefs.edit().putString(KEY_FAVORITE_TRIPS_JSON, array.toString()).apply()
    }

    fun getFavoriteLinesSync(): List<TransitLine> {
        return _favoriteLines.value
    }
}
