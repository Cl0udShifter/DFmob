package com.dfmob.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.dfmob.data.model.TransitLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class RecentLinesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dfmob_recent_lines_prefs", Context.MODE_PRIVATE)

    private val _recentLines = MutableStateFlow<List<TransitLine>>(emptyList())
    val recentLines: StateFlow<List<TransitLine>> = _recentLines.asStateFlow()

    init {
        loadRecentLines()
    }

    private fun loadRecentLines() {
        val jsonStr = prefs.getString(KEY_RECENT_LINES, null) ?: return
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<TransitLine>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    TransitLine(
                        routeId = obj.getString("routeId"),
                        agencyId = obj.optString("agencyId", ""),
                        agencyName = obj.optString("agencyName", ""),
                        lineNumber = obj.getString("lineNumber"),
                        routeName = obj.getString("routeName"),
                        transitType = obj.optInt("transitType", 3),
                        colorHex = obj.optString("colorHex", "#00883E"),
                        textColorHex = obj.optString("textColorHex", "#FFFFFF"),
                        price = if (obj.has("price") && !obj.isNull("price")) obj.getDouble("price") else null,
                        directionId = if (obj.has("directionId") && !obj.isNull("directionId")) obj.getInt("directionId") else null,
                        destinationName = if (obj.has("destinationName") && !obj.isNull("destinationName")) obj.getString("destinationName") else null,
                        isCircular = obj.optBoolean("isCircular", false)
                    )
                )
            }
            _recentLines.value = list.take(3)
        } catch (_: Exception) {}
    }

    fun addLine(line: TransitLine) {
        val current = _recentLines.value.toMutableList()
        current.removeAll { it.routeId == line.routeId || it.lineNumber.equals(line.lineNumber, ignoreCase = true) }
        current.add(0, line)
        val trimmed = current.take(3)
        _recentLines.value = trimmed
        saveToPrefs(trimmed)
    }

    private fun saveToPrefs(lines: List<TransitLine>) {
        try {
            val arr = JSONArray()
            for (line in lines) {
                val obj = JSONObject().apply {
                    put("routeId", line.routeId)
                    put("agencyId", line.agencyId)
                    put("agencyName", line.agencyName)
                    put("lineNumber", line.lineNumber)
                    put("routeName", line.routeName)
                    put("transitType", line.transitType)
                    put("colorHex", line.colorHex)
                    put("textColorHex", line.textColorHex)
                    put("price", line.price)
                    put("directionId", line.directionId)
                    put("destinationName", line.destinationName)
                    put("isCircular", line.isCircular)
                }
                arr.put(obj)
            }
            prefs.edit().putString(KEY_RECENT_LINES, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun clearRecentLines() {
        _recentLines.value = emptyList()
        prefs.edit().remove(KEY_RECENT_LINES).apply()
    }

    companion object {
        private const val KEY_RECENT_LINES = "recent_lines_json"
    }
}
