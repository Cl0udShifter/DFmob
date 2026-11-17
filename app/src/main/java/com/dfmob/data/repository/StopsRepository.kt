package com.dfmob.data.repository

import android.content.Context
import com.dfmob.data.database.DatabaseHelper
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.StopArrival
import com.dfmob.data.model.TransitStop
import com.dfmob.data.telemetry.SemobTelemetryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class StopsRepository(context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val telemetryService = SemobTelemetryService()

    private val keyLocations = listOf(
        DFLocation("Rodoviária do Plano Piloto", "Zona Central, Brasília", -15794167, -47882778),
        DFLocation("Esplanada dos Ministérios", "Zona Cívico-Administrativa, Brasília", -15797500, -47868611),
        DFLocation("Congresso Nacional", "Praça dos Três Poderes, Brasília", -15799722, -47864167),
        DFLocation("Praça dos Três Poderes", "Plano Piloto, Brasília", -15800556, -47861111),
        DFLocation("Aeroporto Internacional de Brasília", "Lago Sul, DF", -15871111, -47918611),
        DFLocation("Taguatinga Centro", "Praça do Relógio, Taguatinga", -15833611, -48056667),
        DFLocation("Taguatinga Sul", "Setor Sul, Taguatinga", -15845000, -48052000),
        DFLocation("Taguatinga Norte", "Avenida Hélio Prates, Taguatinga", -15815000, -48065000),
        DFLocation("Ceilândia Centro", "Região Administrativa IX, Ceilândia", -15819722, -48110556),
        DFLocation("Ceilândia Sul", "Estação Terminal Ceilândia", -15830000, -48115000),
        DFLocation("Ceilândia Norte", "Região Administrativa IX, Ceilândia", -15805000, -48105000),
        DFLocation("Samambaia Centro", "Estação Samambaia, RA XII", -15875000, -48085000),
        DFLocation("Águas Claras", "Avenida Araucárias, Águas Claras", -15838889, -48028611),
        DFLocation("Guará I e II", "Feira do Guará, RA X", -15823611, -47981944),
        DFLocation("Gama Centro", "Região Administrativa II, Gama", -16015000, -48065000),
        DFLocation("Sobradinho", "Região Administrativa V, Sobradinho", -15651944, -47789167),
        DFLocation("Planaltina", "Região Administrativa VI, Planaltina", -15620000, -47650000),
        DFLocation("Santa Maria", "Região Administrativa XIII, Santa Maria", -16018056, -48012500),
        DFLocation("São Sebastião", "Região Administrativa XIV, São Sebastião", -15908056, -47771944),
        DFLocation("Vicente Pires", "Rua 4 / Rua 10, Vicente Pires", -15803611, -48026944),
        DFLocation("Riacho Fundo I e II", "Região Administrativa XVII", -15885000, -48015000),
        DFLocation("ParkShopping", "SMAS Trecho 1, Guará / Plano Piloto", -15833889, -47954722),
        DFLocation("Taguatinga Shopping", "QS 1 Rua 210, Taguatinga", -15835000, -48040000),
        DFLocation("Universidade de Brasília (UnB)", "Campus Darcy Ribeiro, Asa Norte", -15763333, -47870556),
        DFLocation("Hospital de Base (HBDF)", "SMHS Área Especial, Asa Sul", -15798333, -47887778),
        DFLocation("Setor Comercial Sul (SCS)", "Quadra 1 a 6, Asa Sul", -15798000, -47892000),
        DFLocation("Setor Comercial Norte (SCN)", "Quadra 1 a 5, Asa Norte", -15790000, -47887000),
        DFLocation("W3 Sul", "Avenida W3, Asa Sul", -15808000, -47905000),
        DFLocation("W3 Norte", "Avenida W3, Asa Norte", -15765000, -47890000)
    )

    fun getAllStops(): List<TransitStop> {
        return dbHelper.getAllStops()
    }

    /**
     * Filtro de paradas com Viewport Culling e Zoom Threshold (Moovit pattern).
     * Se o zoom estiver afastado (scale < 1.35f), exibe apenas estacoes de metro para evitar poluicao visual.
     */
    fun getStopsForZoom(allStops: List<TransitStop>, zoomScale: Float): List<TransitStop> {
        return if (zoomScale < 1.35f) {
            allStops.filter { it.isMetro }
        } else {
            allStops
        }
    }

    fun searchLocations(query: String): List<DFLocation> {
        if (query.isBlank()) return emptyList()
        val clean = query.trim().lowercase(Locale.ROOT)
        return keyLocations.filter {
            it.name.lowercase(Locale.ROOT).contains(clean) ||
                    it.description.lowercase(Locale.ROOT).contains(clean)
        }
    }

    /**
     * Carregamento IMEDIATO das chegadas programadas a partir do SQLite local (Padrao Moovit / Transit).
     * Executa em < 1ms sem tocar na rede, permitindo que a tela de paradas abra instantaneamente.
     */
    fun getStaticArrivalsForStop(stop: TransitStop): List<StopArrival> {
        val lines = dbHelper.getLinesPassingAtStop(stop.stopId)
        val cal = Calendar.getInstance()
        val currentSeconds = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)

        val rawArrivals = lines.map { line ->
            val isMetro = line.isMetro
            val scheduledTimes = dbHelper.getNextScheduledDepartures(line.lineId, currentSeconds, 3)

            val etaMinutes: Int
            val isLive: Boolean
            val scheduledFormatted: String
            val nextTimesList: List<String>

            if (isMetro) {
                // Headway fixo de alta frequencia do Metro-DF (4-7 min)
                etaMinutes = when (line.lineId) {
                    1001, 1002 -> 4
                    else -> 6
                }
                isLive = true
                scheduledFormatted = formatSecondsToHHMM(currentSeconds + etaMinutes * 60)
                nextTimesList = listOf(
                    formatSecondsToHHMM(currentSeconds + (etaMinutes + 6) * 60),
                    formatSecondsToHHMM(currentSeconds + (etaMinutes + 12) * 60)
                )
            } else {
                val nextDep = scheduledTimes.firstOrNull()
                if (nextDep != null) {
                    val diffSec = maxOf(0, nextDep - currentSeconds)
                    etaMinutes = maxOf(1, (diffSec + 30) / 60)
                    scheduledFormatted = formatSecondsToHHMM(nextDep)
                    // Partidas subsequentes (sem repetir o horario atual)
                    nextTimesList = scheduledTimes.drop(1).take(2).map { formatSecondsToHHMM(it) }
                } else {
                    etaMinutes = 25
                    scheduledFormatted = formatSecondsToHHMM(currentSeconds + 1500)
                    nextTimesList = listOf(
                        formatSecondsToHHMM(currentSeconds + 2400),
                        formatSecondsToHHMM(currentSeconds + 3300)
                    )
                }
                isLive = false
            }

            StopArrival(
                lineNumber = line.lineNumber,
                lineName = line.lineName,
                destination = line.destinationName,
                colorHex = line.colorHex,
                textColorHex = if (isMetro) "#FFFFFF" else "#000000",
                isMetro = isMetro,
                minutesAway = etaMinutes,
                isRealtime = isLive,
                scheduledTimeFormatted = scheduledFormatted,
                nextDepartures = nextTimesList
            )
        }

        // Ordenacao Padrao Moovit: primeiro chegadas com GPS ao vivo, depois proximos horarios, depois numero da linha
        return rawArrivals.sortedWith(
            compareBy(
                { !it.isRealtime },
                { it.minutesAway },
                { it.lineNumber }
            )
        )
    }

    /**
     * Atualizacao assincrona em segundo plano de telemetria GPS ao vivo (Padrao Moovit).
     * Nao bloqueia a interface e roda em paralelo com timeout estrito de 2s.
     */
    suspend fun fetchLiveTelemetryUpdates(
        stop: TransitStop,
        currentArrivals: List<StopArrival>
    ): List<StopArrival> = withContext(Dispatchers.IO) {
        val busLines = currentArrivals.filter { !it.isMetro }
        if (busLines.isEmpty()) return@withContext currentArrivals

        val distinctLineNumbers = busLines.map { it.lineNumber }.distinct()
        val liveVehiclesMap = mutableMapOf<String, List<SemobTelemetryService.LiveVehicle>>()

        withTimeoutOrNull(2000L) {
            coroutineScope {
                distinctLineNumbers.map { lineNum ->
                    async {
                        try {
                            val vehicles = telemetryService.getLiveVehiclePositions(lineNum, stop.latE6, stop.lonE6)
                            lineNum to vehicles
                        } catch (_: Exception) {
                            lineNum to emptyList()
                        }
                    }
                }.awaitAll().forEach { (lineNum, vehicles) ->
                    if (vehicles.isNotEmpty()) {
                        liveVehiclesMap[lineNum] = vehicles
                    }
                }
            }
        }

        if (liveVehiclesMap.isEmpty()) return@withContext currentArrivals

        val updated = currentArrivals.map { arrival ->
            if (arrival.isMetro) {
                arrival
            } else {
                val vehicles = liveVehiclesMap[arrival.lineNumber]
                if (!vehicles.isNullOrEmpty()) {
                    val candidateEtas = vehicles.mapNotNull { v ->
                        val distMeters = calculateDistanceMeters(v.latE6, v.lonE6, stop.latE6, stop.lonE6)
                        if (distMeters in 50.0..30000.0) {
                            val travelTimeSec = (distMeters / 6.5).toInt() + 45
                            maxOf(1, Math.round(travelTimeSec / 60.0).toInt())
                        } else null
                    }
                    if (candidateEtas.isNotEmpty()) {
                        val minEta = candidateEtas.minOrNull() ?: 1
                        if (minEta <= 60) {
                            arrival.copy(
                                minutesAway = minEta,
                                isRealtime = true
                            )
                        } else arrival
                    } else arrival
                } else arrival
            }
        }

        updated.sortedWith(
            compareBy(
                { !it.isRealtime },
                { it.minutesAway },
                { it.lineNumber }
            )
        )
    }

    suspend fun getArrivalsForStop(stop: TransitStop): List<StopArrival> {
        val static = getStaticArrivalsForStop(stop)
        return fetchLiveTelemetryUpdates(stop, static)
    }

    private fun calculateDistanceMeters(lat1E6: Int, lon1E6: Int, lat2E6: Int, lon2E6: Int): Double {
        val r = 6371000.0 // Raio da Terra em metros
        val lat1 = Math.toRadians(lat1E6 / 1_000_000.0)
        val lon1 = Math.toRadians(lon1E6 / 1_000_000.0)
        val lat2 = Math.toRadians(lat2E6 / 1_000_000.0)
        val lon2 = Math.toRadians(lon2E6 / 1_000_000.0)

        val dLat = lat2 - lat1
        val dLon = lon2 - lon1
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun formatSecondsToHHMM(seconds: Int): String {
        val totalMins = (seconds / 60) % 1440
        val hours = totalMins / 60
        val mins = totalMins % 60
        return String.format(Locale.ROOT, "%02d:%02d", hours, mins)
    }
}
