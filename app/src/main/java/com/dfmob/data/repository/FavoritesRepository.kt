package com.dfmob.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.dfmob.data.model.TransitLine

class FavoritesRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("dfmob_favorites", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_FAVORITES = "favorite_route_ids"
    }

    fun getFavoriteRouteIds(): Set<String> {
        return prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    }

    fun isFavorite(routeId: String): Boolean {
        return getFavoriteRouteIds().contains(routeId)
    }

    fun toggleFavorite(routeId: String): Boolean {
        val current = getFavoriteRouteIds().toMutableSet()
        val isNowFavorite = if (current.contains(routeId)) {
            current.remove(routeId)
            false
        } else {
            current.add(routeId)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return isNowFavorite
    }

    fun getFavoriteLines(allLines: List<TransitLine>): List<TransitLine> {
        val ids = getFavoriteRouteIds()
        return allLines.filter { ids.contains(it.routeId) }
    }
}
