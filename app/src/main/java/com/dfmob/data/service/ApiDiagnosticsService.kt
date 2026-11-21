package com.dfmob.data.service

import android.content.Context
import com.dfmob.data.telemetry.MoovitTransitClient
import com.dfmob.data.traffic.TrafficEndpointManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class ApiHealthStatus(
    val name: String,
    val description: String,
    val isHealthy: Boolean,
    val responseTimeMs: Long
)

class ApiDiagnosticsService(
    private val context: Context? = null,
    private val stadiaService: StadiaMapsService = StadiaMapsService()
) {
    suspend fun checkAllApis(): List<ApiHealthStatus> = coroutineScope {
        val moovitDeferred = async { checkMoovit() }
        val semobDeferred = async { checkSemob() }
        val stadiaDeferred = async { checkStadia() }
        val trafficDeferred = async { checkGoogleTraffic() }

        listOf(moovitDeferred.await(), semobDeferred.await(), stadiaDeferred.await(), trafficDeferred.await())
    }

    private suspend fun checkMoovit(): ApiHealthStatus = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val ok = try {
            // Testa a chamada real do cliente Moovit do app consultando a Rodoviária do Plano Piloto (ID 6342602)
            val arrivals = MoovitTransitClient.fetchArrivalsForStop(6342602)
            arrivals.isNotEmpty()
        } catch (_: Exception) {
            false
        }
        val elapsed = System.currentTimeMillis() - start
        ApiHealthStatus(
            name = "Moovit Transit API",
            description = "Itinerários e conexões de transporte",
            isHealthy = ok,
            responseTimeMs = elapsed
        )
    }

    private suspend fun checkSemob(): ApiHealthStatus = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val ok = try {
            // URL WFS oficial usada diariamente pelo SemobTelemetryService
            val url = URL("http://geoserver.semob.df.gov.br/geoserver/semob/wfs?service=WFS&version=1.0.0&request=GetFeature&typeName=semob:ultima_posicao&outputFormat=application/json&maxFeatures=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "DFmob/1.4.7 (Android)")
            }
            conn.responseCode in 200..399
        } catch (_: Exception) {
            false
        }
        val elapsed = System.currentTimeMillis() - start
        ApiHealthStatus(
            name = "DF no Ponto / SEMOB",
            description = "Telemetria GPS de ônibus em tempo real",
            isHealthy = ok,
            responseTimeMs = elapsed
        )
    }

    private suspend fun checkStadia(): ApiHealthStatus = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        val ok = stadiaService.checkApiStatus()
        val elapsed = System.currentTimeMillis() - start
        ApiHealthStatus(
            name = "Stadia Maps API",
            description = "Mapas de alta resolução e busca de locais",
            isHealthy = ok,
            responseTimeMs = elapsed
        )
    }

    private suspend fun checkGoogleTraffic(): ApiHealthStatus = withContext(Dispatchers.IO) {
        val trafficManager = context?.let { TrafficEndpointManager(it) }
        val (ok, elapsed) = trafficManager?.checkAndResolveEndpoint() ?: run {
            val start = System.currentTimeMillis()
            val healthy = try {
                val conn = (URL("https://mt1.google.com/vt/lyrs=h,traffic&x=6012&y=8920&z=14").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    requestMethod = "HEAD"
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                conn.responseCode in 200..399 && conn.contentLength > 500
            } catch (_: Exception) {
                false
            }
            Pair(healthy, System.currentTimeMillis() - start)
        }

        ApiHealthStatus(
            name = "Google Traffic API",
            description = "Camada de fluxo e trânsito em tempo real",
            isHealthy = ok,
            responseTimeMs = elapsed
        )
    }
}
