package com.dfmob.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.dfmob.data.model.DFLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RecentSearchItem(
    val name: String,
    val description: String,
    val latE6: Int,
    val lonE6: Int,
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toDFLocation(): DFLocation = DFLocation(name, description, latE6, lonE6)
}

class RecentSearchesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("dfmob_recent_places", Context.MODE_PRIVATE)

    private val _recentSearches = MutableStateFlow<List<RecentSearchItem>>(emptyList())
    val recentSearches: StateFlow<List<RecentSearchItem>> = _recentSearches.asStateFlow()

    private val _homePlace = MutableStateFlow<DFLocation?>(null)
    val homePlace: StateFlow<DFLocation?> = _homePlace.asStateFlow()

    private val _workPlace = MutableStateFlow<DFLocation?>(null)
    val workPlace: StateFlow<DFLocation?> = _workPlace.asStateFlow()

    init {
        loadRecentSearches()
        loadFrequentPlaces()
    }

    private fun loadRecentSearches() {
        val jsonStr = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<RecentSearchItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RecentSearchItem(
                        name = obj.optString("name"),
                        description = obj.optString("description"),
                        latE6 = obj.optInt("latE6"),
                        lonE6 = obj.optInt("lonE6"),
                        isPinned = obj.optBoolean("isPinned", false),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _recentSearches.value = sortList(list)
        } catch (_: Exception) {}
    }

    private fun saveRecentSearches(list: List<RecentSearchItem>) {
        val sorted = sortList(list)
        _recentSearches.value = sorted
        try {
            val arr = JSONArray()
            for (item in sorted) {
                val obj = JSONObject().apply {
                    put("name", item.name)
                    put("description", item.description)
                    put("latE6", item.latE6)
                    put("lonE6", item.lonE6)
                    put("isPinned", item.isPinned)
                    put("timestamp", item.timestamp)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_RECENT_SEARCHES, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun addSearch(location: DFLocation) {
        val current = _recentSearches.value.toMutableList()
        val existingIndex = current.indexOfFirst {
            it.name.equals(location.name, ignoreCase = true) ||
            (Math.abs(it.latE6 - location.latE6) < 100 && Math.abs(it.lonE6 - location.lonE6) < 100)
        }

        val isPinned = if (existingIndex >= 0) current[existingIndex].isPinned else false
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
        }

        val newItem = RecentSearchItem(
            name = location.name,
            description = location.description,
            latE6 = location.latE6,
            lonE6 = location.lonE6,
            isPinned = isPinned,
            timestamp = System.currentTimeMillis()
        )
        current.add(0, newItem)
        saveRecentSearches(current)
    }

    fun togglePin(item: RecentSearchItem) {
        val current = _recentSearches.value.map {
            if (it.name == item.name && it.latE6 == item.latE6 && it.lonE6 == item.lonE6) {
                it.copy(isPinned = !it.isPinned, timestamp = System.currentTimeMillis())
            } else {
                it
            }
        }
        saveRecentSearches(current)
    }

    fun clearUnpinned() {
        val pinnedOnly = _recentSearches.value.filter { it.isPinned }
        saveRecentSearches(pinnedOnly)
    }

    private fun sortList(list: List<RecentSearchItem>): List<RecentSearchItem> {
        val pinned = list.filter { it.isPinned }.sortedByDescending { it.timestamp }
        val unpinned = list.filter { !it.isPinned }.sortedByDescending { it.timestamp }.take(10)
        return pinned + unpinned
    }

    private fun loadFrequentPlaces() {
        val homeJson = prefs.getString(KEY_HOME, null)
        if (homeJson != null) {
            try {
                val obj = JSONObject(homeJson)
                _homePlace.value = DFLocation(
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    latE6 = obj.optInt("latE6"),
                    lonE6 = obj.optInt("lonE6")
                )
            } catch (_: Exception) {}
        }

        val workJson = prefs.getString(KEY_WORK, null)
        if (workJson != null) {
            try {
                val obj = JSONObject(workJson)
                _workPlace.value = DFLocation(
                    name = obj.optString("name"),
                    description = obj.optString("description"),
                    latE6 = obj.optInt("latE6"),
                    lonE6 = obj.optInt("lonE6")
                )
            } catch (_: Exception) {}
        }
    }

    fun setHomePlace(location: DFLocation) {
        _homePlace.value = location
        savePlace(KEY_HOME, location)
    }

    fun setWorkPlace(location: DFLocation) {
        _workPlace.value = location
        savePlace(KEY_WORK, location)
    }

    private fun savePlace(key: String, location: DFLocation) {
        try {
            val obj = JSONObject().apply {
                put("name", location.name)
                put("description", location.description)
                put("latE6", location.latE6)
                put("lonE6", location.lonE6)
            }
            prefs.edit().putString(key, obj.toString()).apply()
        } catch (_: Exception) {}
    }

    companion object {
        private const val KEY_RECENT_SEARCHES = "recent_searches_list"
        private const val KEY_HOME = "place_home"
        private const val KEY_WORK = "place_work"
    }
}
