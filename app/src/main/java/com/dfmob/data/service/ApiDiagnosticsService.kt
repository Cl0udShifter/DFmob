package com.dfmob.data.service

import com.dfmob.BuildConfig
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
    private val stadiaService: StadiaMapsService = StadiaMapsService()
) {
    suspend fun checkAllApis(): List<ApiHealthStatus> = coroutineScope {
        val moovitDeferred = async { checkMoovit() }
        val semobDeferred = async { checkSemob() }
        val stadiaDeferred = async { checkStadia() }

        listOf(moovitDeferred.await(), semobDeferred.await(), stadiaDeferred.await())
    }

    private suspend fun checkMoovit(): ApiHealthStatus = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var ok = false
        try {
            val url = URL("https://app.moovit.com/services/metroman/currentrevision?metroId=242&customerId=4928")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Moovit/10.0 (Android)")
            }
            ok = (conn.responseCode in 200..399)
        } catch (_: Exception) {
            ok = false
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
        var ok = false
        try {
            val url = URL("https://geoserver.semob.df.gov.br/geoserver/wfs?service=wfs&version=2.0.0&request=GetFeature&typeNames=semob:vw_posicao_veiculo&outputFormat=application/json&count=1")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "DFmob/1.4.0 (Android)")
            }
            ok = (conn.responseCode in 200..399)
        } catch (_: Exception) {
            ok = false
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
}
