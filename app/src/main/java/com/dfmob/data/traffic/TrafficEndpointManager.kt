package com.dfmob.data.traffic

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class TrafficEndpointManager(context: Context) {
    private val oldPrefs: SharedPreferences = context.getSharedPreferences("dfmob_traffic_prefs_v2", Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = context.getSharedPreferences("dfmob_traffic_prefs_v3", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ENDPOINT_INDEX = "active_endpoint_index"
        private const val KEY_TRAFFIC_ENABLED = "traffic_layer_enabled"

        // Modelos de endpoints com camada h,traffic e supressão de POIs comerciais e estações via apistyle
        val ENDPOINT_CANDIDATES = listOf(
            "https://mt%d.google.com/vt/lyrs=h,traffic&x=%d&y=%d&z=%d&apistyle=s.t:2|p.v:off|s.t:4|p.v:off",
            "https://mt%d.google.com/vt?lyrs=h,traffic&x=%d&y=%d&z=%d&apistyle=s.t:2|p.v:off|s.t:4|p.v:off",
            "https://mt%d.google.com/vt/lyrs=h,traffic|smartmaps&x=%d&y=%d&z=%d&apistyle=s.t:2|p.v:off|s.t:4|p.v:off",
            "https://mt%d.google.com/vt/lyrs=h@221097413,traffic&x=%d&y=%d&z=%d&apistyle=s.t:2|p.v:off|s.t:4|p.v:off",
            "https://www.google.com/maps/vt/lyrs=h,traffic&x=%d&y=%d&z=%d&apistyle=s.t:2|p.v:off|s.t:4|p.v:off"
        )
    }

    private val subDomainCounter = AtomicInteger(0)
    private var activeCandidateIndex: Int = prefs.getInt(KEY_ENDPOINT_INDEX, 0).coerceIn(0, ENDPOINT_CANDIDATES.lastIndex)

    private val _isTrafficEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_TRAFFIC_ENABLED, oldPrefs.getBoolean(KEY_TRAFFIC_ENABLED, false))
    )
    val isTrafficEnabled: StateFlow<Boolean> = _isTrafficEnabled.asStateFlow()

    fun isTrafficEnabled(): Boolean = _isTrafficEnabled.value

    fun setTrafficEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRAFFIC_ENABLED, enabled).apply()
        _isTrafficEnabled.value = enabled
    }

    fun getActiveEndpointIndex(): Int = activeCandidateIndex

    fun getTileUrl(x: Int, y: Int, z: Int): String {
        val template = ENDPOINT_CANDIDATES[activeCandidateIndex]
        val sub = (subDomainCounter.getAndIncrement() and 0x7FFFFFFF) % 4
        return if (template.contains("%d.google.com")) {
            String.format(template, sub, x, y, z)
        } else {
            String.format(template, x, y, z)
        }
    }

    suspend fun checkAndResolveEndpoint(): Pair<Boolean, Long> = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        // Ponto de teste em Brasília (Plano Piloto: z=14, x=6012, y=8920)
        val testX = 6012
        val testY = 8920
        val testZ = 14

        // 1. Testa primeiro o endpoint ativo atual
        if (probeTemplate(activeCandidateIndex, testX, testY, testZ)) {
            val elapsed = System.currentTimeMillis() - start
            return@withContext Pair(true, elapsed)
        }

        // 2. Se falhar, faz auto-recuperação testando todos os outros candidatos
        for (i in ENDPOINT_CANDIDATES.indices) {
            if (i == activeCandidateIndex) continue
            if (probeTemplate(i, testX, testY, testZ)) {
                activeCandidateIndex = i
                prefs.edit().putInt(KEY_ENDPOINT_INDEX, i).apply()
                val elapsed = System.currentTimeMillis() - start
                return@withContext Pair(true, elapsed)
            }
        }

        val elapsed = System.currentTimeMillis() - start
        Pair(false, elapsed)
    }

    private fun probeTemplate(index: Int, x: Int, y: Int, z: Int): Boolean {
        return try {
            val template = ENDPOINT_CANDIDATES[index]
            val sub = 1
            val urlStr = if (template.contains("%d.google.com")) {
                String.format(template, sub, x, y, z)
            } else {
                String.format(template, x, y, z)
            }
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            }
            conn.responseCode in 200..399 && conn.contentLength > 500
        } catch (_: Exception) {
            false
        }
    }
}
