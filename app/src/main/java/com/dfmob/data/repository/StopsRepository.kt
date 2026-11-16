package com.dfmob.data.repository

import android.content.Context
import com.dfmob.data.database.DatabaseHelper
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.StopArrival
import com.dfmob.data.model.TransitStop
import com.dfmob.data.telemetry.SemobTelemetryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun getArrivalsForStop(stop: TransitStop): List<StopArrival> = withContext(Dispatchers.IO) {
        val lines = dbHelper.getLinesPassingAtStop(stop.stopId)
        val cal = Calendar.getInstance()
        val currentSeconds = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)

        val arrivals = mutableListOf<StopArrival>()

        for (line in lines) {
            var liveFound = false
            var etaMinutes = 0

            // 1. Verificar GPS ao vivo na telemetria oficial da Semob
            if (!line.isMetro) {
                val liveVehicles = telemetryService.getLiveVehiclePositions(line.lineNumber)
                if (liveVehicles.isNotEmpty()) {
                    val candidateEtas = liveVehicles.mapNotNull { v ->
                        val distMeters = calculateDistanceMeters(v.latE6, v.lonE6, stop.latE6, stop.lonE6)
                        if (distMeters >= 50.0 && distMeters <= 25000.0) {
                            val travelTimeSec = (distMeters / 6.1).toInt() + 45
                            maxOf(1, Math.round(travelTimeSec / 60.0).toInt())
                        } else null
                    }
                    if (candidateEtas.isNotEmpty()) {
                        etaMinutes = candidateEtas.minOrNull() ?: 1
                        liveFound = true
                    }
                }
            } else {
                // Intervalo operacional do Metro-DF (4 a 8 minutos no pico)
                val baseMins = when (line.lineId) {
                    1001, 1002 -> 4 // Linha Verde
                    else -> 7 // Linha Laranja
                }
                etaMinutes = baseMins
                liveFound = true
            }

            // 2. Horarios programados seguintes (trips_schedule da linha)
            val scheduledTimes = dbHelper.getNextScheduledDepartures(line.lineId, currentSeconds, 3)
            val nextTimesList = if (scheduledTimes.isNotEmpty()) {
                scheduledTimes.map { formatSecondsToHHMM(it) }
            } else {
                listOf(
                    formatSecondsToHHMM(currentSeconds + 900),
                    formatSecondsToHHMM(currentSeconds + 1800),
                    formatSecondsToHHMM(currentSeconds + 2700)
                )
            }

            // 3. Regra de formatacao:
            // Se minutos <= 60: exibe minutos. Se liveFound for verdadeiro, texto verde.
            // Se minutos > 60: exibe o horario padrao HH:MM.
            val isLive = liveFound && etaMinutes <= 60
            val scheduledFormatted = formatSecondsToHHMM(currentSeconds + etaMinutes * 60)

            arrivals.add(
                StopArrival(
                    lineNumber = line.lineNumber,
                    lineName = line.lineName,
                    destination = line.destinationName,
                    colorHex = line.colorHex,
                    textColorHex = if (line.isMetro) "#FFFFFF" else "#000000",
                    isMetro = line.isMetro,
                    minutesAway = etaMinutes,
                    isRealtime = isLive,
                    scheduledTimeFormatted = scheduledFormatted,
                    nextDepartures = nextTimesList
                )
            )
        }

        arrivals
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
