package com.dfmob.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.dfmob.data.model.TransitLine
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
    }

    private val _favoriteLines = MutableStateFlow<List<TransitLine>>(loadFavoriteLinesFromPrefs())
    val favoriteLines: StateFlow<List<TransitLine>> = _favoriteLines.asStateFlow()

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
                        price = if (obj.has("price") && !obj.isNull("price")) obj.getDouble("price") else null
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

    fun toggleFavorite(line: TransitLine): Boolean {
        val currentIds = getFavoriteRouteIds().toMutableSet()
        val currentLines = _favoriteLines.value.toMutableList()
        val isNowFavorite: Boolean

        if (currentIds.contains(line.routeId)) {
            currentIds.remove(line.routeId)
            currentLines.removeAll { it.routeId == line.routeId }
            isNowFavorite = false
        } else {
            currentIds.add(line.routeId)
            currentLines.removeAll { it.routeId == line.routeId }
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

    fun getFavoriteLinesSync(): List<TransitLine> {
        return _favoriteLines.value
    }
}
