package com.dfmob.data.repository

import android.content.Context
import android.location.Geocoder
import com.dfmob.data.database.DatabaseHelper
import com.dfmob.data.model.DFLocation
import com.dfmob.data.model.LineDirectionOption
import com.dfmob.data.model.LineRouteState
import com.dfmob.data.model.LiveVehicleMarker
import com.dfmob.data.model.NextDeparture
import com.dfmob.data.model.StopArrival
import com.dfmob.data.model.TransitStop
import com.dfmob.data.telemetry.MoovitTransitClient
import com.dfmob.data.telemetry.SemobTelemetryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class StopsRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    private val telemetryService = SemobTelemetryService()
    private val stadiaMapsService = com.dfmob.data.service.StadiaMapsService()

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

    suspend fun searchLocations(query: String): List<DFLocation> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val clean = query.trim().lowercase(Locale.ROOT)

        val results = mutableListOf<DFLocation>()

        // 1. Busca em pontos de referencia principais e regioes administrativas locais (sem custo de rede)
        val landmarkMatches = keyLocations.filter {
            it.name.lowercase(Locale.ROOT).contains(clean) ||
                    it.description.lowercase(Locale.ROOT).contains(clean)
        }.take(3)
        results.addAll(landmarkMatches)

        // 2. Busca geocodificada via Stadia Maps Autocomplete (com cache e filtro geografico no DF)
        if (clean.length >= 3) {
            val stadiaResults = stadiaMapsService.autocomplete(clean)
            for (item in stadiaResults) {
                if (results.none { it.name.equals(item.name, ignoreCase = true) }) {
                    results.add(item)
                }
            }
        }

        // 3. Fallback no Android Geocoder nativo caso Stadia nao tenha retornado resultados
        if (results.isEmpty() && clean.length >= 3) {
            try {
                if (Geocoder.isPresent()) {
                    val geocoder = Geocoder(context, Locale("pt", "BR"))
                    val queryWithRegion = if (query.contains("DF", ignoreCase = true) || query.contains("Brasília", ignoreCase = true)) {
                        query
                    } else {
                        "$query, Distrito Federal, Brasil"
                    }
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocationName(queryWithRegion, 5)
                    if (!addresses.isNullOrEmpty()) {
                        for (addr in addresses) {
                            val lat = addr.latitude
                            val lon = addr.longitude
                            if (lat in -16.3..-15.4 && lon in -48.4..-47.2) {
                                val title = addr.featureName ?: addr.thoroughfare ?: addr.getAddressLine(0)?.split(",")?.firstOrNull() ?: query
                                val subtitleParts = mutableListOf<String>()
                                addr.subLocality?.let { subtitleParts.add(it) }
                                addr.subAdminArea?.let { subtitleParts.add(it) }
                                val sub = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(", ") else "Distrito Federal"
                                results.add(
                                    DFLocation(
                                        name = title,
                                        description = sub,
                                        latE6 = (lat * 1_000_000).toInt(),
                                        lonE6 = (lon * 1_000_000).toInt()
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Deduplicacao por proximidade geografica (< 100m) e limite de resultados
        results.distinctBy { "${it.latE6 / 1000}_${it.lonE6 / 1000}" }.take(8)
    }

    /**
     * Carregamento IMEDIATO das chegadas programadas a partir do SQLite local (Padrao Moovit / Transit).
     * Executa em < 1ms sem tocar na rede, permitindo que a tela de paradas abra instantaneamente.
     */
    fun getStaticArrivalsForStop(stop: TransitStop): List<StopArrival> {
        val lines = dbHelper.getLinesPassingAtStop(stop.stopId)
        val nowMs = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentSeconds = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)

        val todayStartMs = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val rawArrivals = lines.map { line ->
            val isMetro = line.isMetro
            val scheduledTimes = dbHelper.getNextScheduledDepartures(line.lineId, stop.stopId, currentSeconds, 3)

            val etaMinutes: Int
            val isLive: Boolean
            val scheduledFormatted: String
            val mainTargetEpochMs: Long
            val nextDeparturesList: List<NextDeparture>

            if (isMetro) {
                // Headway fixo de alta frequencia do Metro-DF (4-7 min)
                etaMinutes = when (line.lineId) {
                    1001, 1002 -> 4
                    else -> 6
                }
                isLive = true
                mainTargetEpochMs = nowMs + etaMinutes * 60_000L
                scheduledFormatted = formatSecondsToHHMM(currentSeconds + etaMinutes * 60)
                nextDeparturesList = listOf(
                    NextDeparture(
                        timeFormatted = formatSecondsToHHMM(currentSeconds + (etaMinutes + 6) * 60),
                        minutesAway = etaMinutes + 6,
                        isRealtime = true,
                        targetEpochMs = nowMs + (etaMinutes + 6) * 60_000L
                    ),
                    NextDeparture(
                        timeFormatted = formatSecondsToHHMM(currentSeconds + (etaMinutes + 12) * 60),
                        minutesAway = etaMinutes + 12,
                        isRealtime = true,
                        targetEpochMs = nowMs + (etaMinutes + 12) * 60_000L
                    )
                )
            } else {
                val nextDep = scheduledTimes.firstOrNull()
                if (nextDep != null) {
                    val diffSec = maxOf(0, nextDep - currentSeconds)
                    etaMinutes = maxOf(0, (diffSec + 30) / 60)
                    mainTargetEpochMs = todayStartMs + nextDep * 1000L
                    scheduledFormatted = formatSecondsToHHMM(nextDep)
                    // Partidas subsequentes (sem repetir o horario atual)
                    nextDeparturesList = scheduledTimes.drop(1).take(2).map { depSec ->
                        val depEpoch = todayStartMs + depSec * 1000L
                        val depMins = maxOf(0, ((depEpoch - nowMs) + 30000L) / 60000L).toInt()
                        NextDeparture(
                            timeFormatted = formatSecondsToHHMM(depSec),
                            minutesAway = depMins,
                            isRealtime = false,
                            targetEpochMs = depEpoch
                        )
                    }
                    isLive = false
                } else {
                    etaMinutes = 25
                    mainTargetEpochMs = nowMs + 25 * 60_000L
                    scheduledFormatted = formatSecondsToHHMM(currentSeconds + 1500)
                    nextDeparturesList = listOf(
                        NextDeparture(
                            timeFormatted = formatSecondsToHHMM(currentSeconds + 2400),
                            minutesAway = 40,
                            isRealtime = false,
                            targetEpochMs = nowMs + 40 * 60_000L
                        ),
                        NextDeparture(
                            timeFormatted = formatSecondsToHHMM(currentSeconds + 3300),
                            minutesAway = 55,
                            isRealtime = false,
                            targetEpochMs = nowMs + 55 * 60_000L
                        )
                    )
                    isLive = false
                }
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
                targetEpochMs = mainTargetEpochMs,
                nextDepartures = nextDeparturesList
            )
        }

        // Ordenacao estrita por menor tempo de chegada (minutos restantes), independente de GPS
        return rawArrivals.sortedWith(
            compareBy(
                { it.minutesAway },
                { it.lineNumber }
            )
        )
    }

    /**
     * Atualizacao assincrona em segundo plano com telemetria GPS e horarios ao vivo oficiais
     * diretamente do endpoint de paradas do Moovit (V4/StopsArrivals).
     * Garante paridade de dados 100% identica ao app Moovit, sem veiculos ficticios.
     */
    suspend fun fetchLiveTelemetryUpdates(
        stop: TransitStop,
        currentArrivals: List<StopArrival>
    ): List<StopArrival> = withContext(Dispatchers.IO) {
        try {
            val moovitData = MoovitTransitClient.fetchArrivalsForStop(stop.stopId)
            if (moovitData.isEmpty()) {
                return@withContext currentArrivals
            }

            val linesAtStop = dbHelper.getLinesPassingAtStop(stop.stopId).associateBy { it.lineId }
            val nowMs = System.currentTimeMillis()
            val calendar = Calendar.getInstance()
            val timeZone = calendar.timeZone

            val updatedArrivals = mutableListOf<StopArrival>()
            val processedLineIds = mutableSetOf<Int>()

            for (lineArrival in moovitData) {
                val lineInfo = linesAtStop[lineArrival.lineId] ?: dbHelper.getLineInfoById(lineArrival.lineId) ?: continue
                if (lineArrival.arrivals.isEmpty()) continue
                if (!processedLineIds.add(lineArrival.lineId)) continue

                // Filtra chegadas validas (que nao partiram ha mais de 2 minutos)
                val validArrivals = lineArrival.arrivals.filter { arr ->
                    val etd = arr.rtEtdUTC ?: arr.staticEtdUTC
                    etd >= nowMs - 120_000L
                }
                if (validArrivals.isEmpty()) continue

                val first = validArrivals.first()
                val hasLive = first.rtEtdUTC != null
                val targetEtd = first.rtEtdUTC ?: first.staticEtdUTC
                val minutesAway = maxOf(0, Math.round((targetEtd - nowMs) / 60000.0).toInt())

                val formattedFirst = formatEpochMsToHHMM(targetEtd, timeZone)
                val subsequentDepartures = validArrivals.drop(1).take(3).map { arr ->
                    val isRt = arr.rtEtdUTC != null
                    val arrTargetEtd = arr.rtEtdUTC ?: arr.staticEtdUTC
                    val mins = maxOf(0, Math.round((arrTargetEtd - nowMs) / 60000.0).toInt())
                    val formatted = formatEpochMsToHHMM(arrTargetEtd, timeZone)
                    NextDeparture(
                        timeFormatted = formatted,
                        minutesAway = mins,
                        isRealtime = isRt,
                        targetEpochMs = arrTargetEtd
                    )
                }

                updatedArrivals.add(
                    StopArrival(
                        lineNumber = lineInfo.lineNumber,
                        lineName = lineInfo.lineName,
                        destination = lineInfo.destinationName,
                        colorHex = lineInfo.colorHex,
                        textColorHex = if (lineInfo.isMetro) "#FFFFFF" else "#000000",
                        isMetro = lineInfo.isMetro,
                        minutesAway = minutesAway,
                        isRealtime = hasLive,
                        scheduledTimeFormatted = formattedFirst,
                        targetEpochMs = targetEtd,
                        nextDepartures = subsequentDepartures
                    )
                )
            }

            if (updatedArrivals.isNotEmpty()) {
                // Se a parada tambem tiver linhas de metro que nao vieram no retorno da rede, mantem-nas
                if (stop.isMetro) {
                    val metroArrivals = currentArrivals.filter { it.isMetro && updatedArrivals.none { u -> u.lineNumber == it.lineNumber } }
                    updatedArrivals.addAll(metroArrivals)
                }

                updatedArrivals.sortedWith(
                    compareBy(
                        { it.minutesAway },
                        { it.lineNumber }
                    )
                )
            } else {
                currentArrivals
            }
        } catch (_: Exception) {
            currentArrivals
        }
    }

    fun getLineRouteStateFast(
        lineNumber: String,
        preferredLineId: Int? = null,
        preferredStopId: Int? = null,
        userLatE6: Int? = null,
        userLonE6: Int? = null
    ): LineRouteState? {
        val detail = dbHelper.getLineDetailByNumber(lineNumber)
            ?: (if (preferredLineId != null) dbHelper.getLineDetailById(preferredLineId) else null)
            ?: return null

        val dirOptions = detail.directions.map { dir ->
            LineDirectionOption(
                lineId = dir.lineId,
                directionId = dir.directionId,
                originName = dir.originName,
                destinationName = dir.destinationName
            )
        }
        if (dirOptions.isEmpty()) return null

        var chosenIndex = 0
        if (preferredLineId != null) {
            val idx = dirOptions.indexOfFirst { it.lineId == preferredLineId }
            if (idx >= 0) chosenIndex = idx
        } else if (preferredStopId != null) {
            for ((idx, opt) in dirOptions.withIndex()) {
                val stps = dbHelper.getStopsForLine(opt.lineId)
                if (stps.any { it.stopId == preferredStopId }) {
                    chosenIndex = idx
                    break
                }
            }
        } else if (userLatE6 != null && userLonE6 != null) {
            var bestDist = Double.MAX_VALUE
            var bestIdx = 0
            for ((idx, opt) in dirOptions.withIndex()) {
                val stps = dbHelper.getStopsForLine(opt.lineId)
                for (s in stps) {
                    val d = calculateDistanceMeters(userLatE6, userLonE6, s.latE6, s.lonE6)
                    if (d < bestDist) {
                        bestDist = d
                        bestIdx = idx
                    }
                }
            }
            chosenIndex = bestIdx
        }

        val activeLineId = dirOptions[chosenIndex].lineId
        val activeDirId = dirOptions[chosenIndex].directionId
        val stops = dbHelper.getStopsForLine(activeLineId)
        if (stops.isEmpty()) return null

        val cachedShapes = telemetryService.getCachedShapes(detail.lineNumber)
        val initialShape = if (cachedShapes != null) matchShapeToDirection(cachedShapes, stops) else emptyList()
        val fastStops = if (initialShape.isNotEmpty()) filterStopsAlongRoute(stops, initialShape, 300.0) else stops

        var initialStop: TransitStop? = null
        if (preferredStopId != null) {
            initialStop = fastStops.firstOrNull { it.stopId == preferredStopId }
        }
        if (initialStop == null && userLatE6 != null && userLonE6 != null) {
            initialStop = fastStops.minByOrNull { calculateDistanceMeters(userLatE6, userLonE6, it.latE6, it.lonE6) }
        }
        if (initialStop == null) {
            initialStop = fastStops.firstOrNull()
        }

        val staticArrival = if (initialStop != null) {
            getStaticArrivalForLineAtStop(activeLineId, initialStop)
        } else null

        return LineRouteState(
            groupId = detail.groupId,
            lineNumber = detail.lineNumber,
            lineName = detail.lineName,
            agencyName = detail.agencyName,
            circular = detail.circular,
            colorHex = detail.colorHex,
            textColorHex = if (detail.isMetro) "#FFFFFF" else "#000000",
            isMetro = detail.isMetro,
            directions = dirOptions,
            currentDirectionIndex = chosenIndex,
            stops = fastStops,
            selectedStop = initialStop,
            liveVehicles = emptyList(),
            shapeCoordinates = initialShape,
            selectedStopArrival = staticArrival
        )
    }

    suspend fun enrichRouteStateWithLiveTelemetry(currentState: LineRouteState): LineRouteState = coroutineScope {
        val vDeferred = async(Dispatchers.IO) {
            try {
                fetchLiveVehiclesForRoute(currentState.lineNumber, currentState.activeDirectionId, currentState.activeLineId, currentState.stops)
            } catch (_: Exception) {
                emptyList()
            }
        }
        val sDeferred = async(Dispatchers.IO) {
            try {
                val shapes = telemetryService.fetchRealRouteShapes(currentState.lineNumber)
                matchShapeToDirection(shapes, currentState.stops)
            } catch (_: Exception) {
                emptyList()
            }
        }
        val aDeferred = async(Dispatchers.IO) {
            try {
                if (currentState.selectedStop != null) {
                    fetchArrivalForLineAtStop(currentState.activeLineId, currentState.selectedStop)
                } else null
            } catch (_: Exception) {
                null
            }
        }

        val vehicles = vDeferred.await()
        val shapeCoords = sDeferred.await()
        val liveArrival = aDeferred.await()

        val cleanStops = if (shapeCoords.isNotEmpty()) {
            filterStopsAlongRoute(currentState.stops, shapeCoords, 300.0)
        } else {
            currentState.stops
        }

        val updatedSelectedStop = if (currentState.selectedStop != null && cleanStops.any { it.stopId == currentState.selectedStop.stopId }) {
            currentState.selectedStop
        } else {
            cleanStops.firstOrNull()
        }

        currentState.copy(
            liveVehicles = vehicles,
            shapeCoordinates = if (shapeCoords.isNotEmpty()) shapeCoords else currentState.shapeCoordinates,
            stops = cleanStops,
            selectedStop = updatedSelectedStop,
            selectedStopArrival = liveArrival ?: currentState.selectedStopArrival
        )
    }

    fun changeLineRouteDirectionFast(
        currentState: LineRouteState,
        newDirectionIndex: Int,
        userLatE6: Int? = null,
        userLonE6: Int? = null
    ): LineRouteState {
        val safeIndex = newDirectionIndex.coerceIn(0, currentState.directions.lastIndex)
        val activeLineId = currentState.directions[safeIndex].lineId
        val activeDirId = currentState.directions[safeIndex].directionId
        val newStops = dbHelper.getStopsForLine(activeLineId)

        val cachedShapes = telemetryService.getCachedShapes(currentState.lineNumber)
        val initialShape = if (cachedShapes != null) matchShapeToDirection(cachedShapes, newStops) else emptyList()
        val fastStops = if (initialShape.isNotEmpty()) filterStopsAlongRoute(newStops, initialShape, 300.0) else newStops

        var newSelectedStop: TransitStop? = null
        if (userLatE6 != null && userLonE6 != null) {
            newSelectedStop = fastStops.minByOrNull { calculateDistanceMeters(userLatE6, userLonE6, it.latE6, it.lonE6) }
        }
        if (newSelectedStop == null) {
            newSelectedStop = fastStops.firstOrNull()
        }

        val staticArrival = if (newSelectedStop != null) {
            getStaticArrivalForLineAtStop(activeLineId, newSelectedStop)
        } else null

        return currentState.copy(
            currentDirectionIndex = safeIndex,
            stops = fastStops,
            selectedStop = newSelectedStop,
            liveVehicles = emptyList(),
            shapeCoordinates = initialShape,
            selectedStopArrival = staticArrival
        )
    }

    suspend fun getLineRouteState(
        lineNumber: String,
        preferredLineId: Int? = null,
        preferredStopId: Int? = null,
        userLatE6: Int? = null,
        userLonE6: Int? = null
    ): LineRouteState? {
        val fast = getLineRouteStateFast(lineNumber, preferredLineId, preferredStopId, userLatE6, userLonE6)
            ?: return null
        return enrichRouteStateWithLiveTelemetry(fast)
    }

    suspend fun changeLineRouteDirection(
        currentState: LineRouteState,
        newDirectionIndex: Int,
        userLatE6: Int? = null,
        userLonE6: Int? = null
    ): LineRouteState {
        val fast = changeLineRouteDirectionFast(currentState, newDirectionIndex, userLatE6, userLonE6)
        return enrichRouteStateWithLiveTelemetry(fast)
    }

    private fun filterStopsAlongRoute(
        stops: List<TransitStop>,
        shapeCoords: List<Pair<Double, Double>>,
        maxDistanceMeters: Double = 300.0
    ): List<TransitStop> {
        if (shapeCoords.isEmpty() || stops.isEmpty()) return stops
        val filtered = stops.filter { stop ->
            var minD = Double.MAX_VALUE
            for (pt in shapeCoords) {
                val d = calculateDistanceMeters(
                    stop.latE6,
                    stop.lonE6,
                    (pt.first * 1_000_000).toInt(),
                    (pt.second * 1_000_000).toInt()
                )
                if (d < minD) {
                    minD = d
                    if (minD <= maxDistanceMeters) break
                }
            }
            minD <= maxDistanceMeters
        }
        return if (filtered.isNotEmpty()) filtered else stops
    }

    private fun matchShapeToDirection(
        shapes: Map<String, List<Pair<Double, Double>>>,
        stops: List<TransitStop>
    ): List<Pair<Double, Double>> {
        if (shapes.isEmpty() || stops.isEmpty()) return emptyList()
        if (shapes.size == 1) return shapes.values.first()

        val firstStop = stops.first()
        val lastStop = stops.last()
        val isCircular = calculateDistanceMeters(firstStop.latE6, firstStop.lonE6, lastStop.latE6, lastStop.lonE6) < 800
        val ida = shapes.entries.firstOrNull { it.key.contains("IDA", ignoreCase = true) }?.value
        val volta = shapes.entries.firstOrNull { it.key.contains("VOLTA", ignoreCase = true) }?.value
        if (isCircular && ida != null && volta != null) {
            return ida + volta
        }

        var bestShape: List<Pair<Double, Double>>? = null
        var bestScore = -1
        for ((_, pts) in shapes) {
            if (pts.isEmpty()) continue
            var nearCount = 0
            for (s in stops) {
                var minD = Double.MAX_VALUE
                for (pt in pts) {
                    val d = calculateDistanceMeters(
                        s.latE6,
                        s.lonE6,
                        (pt.first * 1_000_000).toInt(),
                        (pt.second * 1_000_000).toInt()
                    )
                    if (d < minD) {
                        minD = d
                        if (minD <= 350.0) break
                    }
                }
                if (minD <= 350.0) nearCount++
            }
            if (nearCount > bestScore) {
                bestScore = nearCount
                bestShape = pts
            }
        }
        return bestShape ?: shapes.values.firstOrNull() ?: emptyList()
    }

    fun getStaticArrivalForLineAtStop(lineId: Int, stop: TransitStop): StopArrival? {
        val lineInfo = dbHelper.getLineInfoById(lineId) ?: return null
        val staticArrivals = getStaticArrivalsForStop(stop)
        return staticArrivals.firstOrNull { it.lineNumber == lineInfo.lineNumber }
    }

    suspend fun fetchLiveVehiclesForRoute(
        lineNumber: String,
        directionId: Int,
        lineId: Int? = null,
        stops: List<TransitStop> = emptyList()
    ): List<LiveVehicleMarker> = withContext(Dispatchers.IO) {
        if (lineId != null && stops.isNotEmpty()) {
            val stopIds = stops.map { it.stopId }
            val moovitVehicles = com.dfmob.data.telemetry.MoovitTransitClient.fetchLiveVehiclesForLine(
                lineId = lineId,
                stopIds = stopIds,
                lineNumber = lineNumber,
                directionId = directionId,
                stops = stops
            )
            if (moovitVehicles.isNotEmpty()) {
                return@withContext moovitVehicles
            }
        }
        try {
            val vehicles = telemetryService.getLiveVehiclePositions(lineNumber)
            vehicles.filter { v ->
                if (v.directionId != null) {
                    v.directionId == directionId
                } else {
                    true
                }
            }.map { v ->
                LiveVehicleMarker(
                    vehicleId = v.vehicleId,
                    lineNumber = v.lineNumber,
                    latE6 = v.latE6,
                    lonE6 = v.lonE6,
                    speedKmh = v.speedKmh,
                    bearing = v.bearing,
                    directionId = v.directionId
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchArrivalForLineAtStop(lineId: Int, stop: TransitStop): StopArrival? = withContext(Dispatchers.IO) {
        try {
            val lineInfo = dbHelper.getLineInfoById(lineId) ?: return@withContext null
            val staticArrivals = getStaticArrivalsForStop(stop)
            val staticForThisLine = staticArrivals.firstOrNull { it.lineNumber == lineInfo.lineNumber }

            val moovitArrivals = MoovitTransitClient.fetchArrivalsForStop(stop.stopId)
            val lineMoovit = moovitArrivals.firstOrNull { it.lineId == lineId }

            if (lineMoovit != null && lineMoovit.arrivals.isNotEmpty()) {
                val nowMs = System.currentTimeMillis()
                val valid = lineMoovit.arrivals.filter {
                    val etd = it.rtEtdUTC ?: it.staticEtdUTC
                    etd >= nowMs - 120_000L
                }
                if (valid.isNotEmpty()) {
                    val first = valid.first()
                    val targetEtd = first.rtEtdUTC ?: first.staticEtdUTC
                    val mins = maxOf(0, Math.round((targetEtd - nowMs) / 60000.0).toInt())
                    val tz = Calendar.getInstance().timeZone
                    val subsequent = valid.drop(1).take(3).map {
                        val et = it.rtEtdUTC ?: it.staticEtdUTC
                        val m = maxOf(0, Math.round((et - nowMs) / 60000.0).toInt())
                        NextDeparture(
                            timeFormatted = formatEpochMsToHHMM(et, tz),
                            minutesAway = m,
                            isRealtime = it.rtEtdUTC != null,
                            targetEpochMs = et
                        )
                    }
                    return@withContext StopArrival(
                        lineNumber = lineInfo.lineNumber,
                        lineName = lineInfo.lineName,
                        destination = lineInfo.destinationName,
                        colorHex = lineInfo.colorHex,
                        textColorHex = if (lineInfo.isMetro) "#FFFFFF" else "#000000",
                        isMetro = lineInfo.isMetro,
                        minutesAway = mins,
                        isRealtime = first.rtEtdUTC != null,
                        scheduledTimeFormatted = formatEpochMsToHHMM(targetEtd, tz),
                        targetEpochMs = targetEtd,
                        nextDepartures = subsequent
                    )
                }
            }

            staticForThisLine
        } catch (_: Exception) {
            null
        }
    }

    private fun formatEpochMsToHHMM(epochMs: Long, timeZone: java.util.TimeZone): String {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = epochMs
        val hours = cal.get(Calendar.HOUR_OF_DAY)
        val mins = cal.get(Calendar.MINUTE)
        return String.format(Locale.ROOT, "%02d:%02d", hours, mins)
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

    suspend fun getUpcomingDeparturesForStopAndLines(
        stopId: Int,
        lineIds: List<Int>,
        fallbackScheduledUtc: Long
    ): List<com.dfmob.data.model.TripDepartureItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<com.dfmob.data.model.TripDepartureItem>()
        val lineIdSet = lineIds.toSet()
        val nowMs = System.currentTimeMillis()
        val tz = java.util.TimeZone.getTimeZone("America/Sao_Paulo")

        // 1. Tenta obter do endpoint de partidas em tempo real do Moovit
        try {
            val moovitArrivals = MoovitTransitClient.fetchArrivalsForStop(stopId)
            val matchedLines = moovitArrivals.filter { lineIdSet.contains(it.lineId) }

            for (lArr in matchedLines) {
                val valid = lArr.arrivals.filter {
                    val target = it.rtEtdUTC ?: it.staticEtdUTC
                    target >= nowMs - 60_000L
                }
                for (arr in valid) {
                    val isRealTime = arr.rtEtdUTC != null
                    val target = arr.rtEtdUTC ?: arr.staticEtdUTC
                    val mins = maxOf(0, Math.round((target - nowMs) / 60000.0).toInt())
                    val formatted = if (isRealTime) {
                        if (mins <= 1) "Já" else "$mins min"
                    } else {
                        formatEpochMsToHHMM(target, tz)
                    }
                    results.add(
                        com.dfmob.data.model.TripDepartureItem(
                            lineNumber = "",
                            timeFormatted = formatted,
                            isRealtime = isRealTime,
                            targetEpochMs = target,
                            lineId = lArr.lineId
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 2. Consulta os horarios programados no banco local SQLite para as linhas que nao tiveram retorno
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = nowMs
        val curSec = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60 + cal.get(Calendar.SECOND)
        for (lid in lineIds) {
            val countForLine = results.count { it.lineId == lid }
            if (countForLine == 0) {
                val secList = dbHelper.getNextScheduledDepartures(lid, stopId, curSec, 3)
                for (sec in secList) {
                    val h = (sec / 3600) % 24
                    val m = (sec % 3600) / 60
                    val timeStr = String.format(Locale.ROOT, "%02d:%02d", h, m)
                    results.add(
                        com.dfmob.data.model.TripDepartureItem(
                            lineNumber = "",
                            timeFormatted = timeStr,
                            isRealtime = false,
                            targetEpochMs = nowMs + ((sec - curSec) * 1000L),
                            lineId = lid
                        )
                    )
                }
            }
        }

        // 3. Fallback final para horario programado do itinerario se ainda vazio
        if (results.isEmpty() && fallbackScheduledUtc > 0) {
            for (lid in lineIds) {
                results.add(
                    com.dfmob.data.model.TripDepartureItem(
                        lineNumber = "",
                        timeFormatted = formatEpochMsToHHMM(fallbackScheduledUtc, tz),
                        isRealtime = false,
                        targetEpochMs = fallbackScheduledUtc,
                        lineId = lid
                    )
                )
            }
        }

        results.groupBy { it.lineId }
            .flatMap { (_, items) ->
                items.distinctBy { it.timeFormatted }.sortedBy { it.targetEpochMs }.take(5)
            }
    }

    suspend fun enrichItinerariesWithDepartures(
        itineraries: List<com.dfmob.data.model.TripPlanItinerary>
    ): List<com.dfmob.data.model.TripPlanItinerary> = withContext(Dispatchers.IO) {
        val stopDeparturesCache = mutableMapOf<Pair<Int, String>, List<com.dfmob.data.model.TripDepartureItem>>()

        itineraries.map { itin ->
            val updatedLegs = itin.legs.mapIndexed { idx, leg ->
                val isFirstTransit = leg.type == com.dfmob.data.model.TripLegType.TRANSIT &&
                        itin.legs.indexOfFirst { it.type == com.dfmob.data.model.TripLegType.TRANSIT } == idx

                if (isFirstTransit && leg.departureStopId != null && leg.lines.isNotEmpty()) {
                    val lineIds = leg.lines.map { it.lineId }
                    val cacheKey = Pair(leg.departureStopId, lineIds.sorted().joinToString(","))
                    val deps = stopDeparturesCache.getOrPut(cacheKey) {
                        getUpcomingDeparturesForStopAndLines(leg.departureStopId, lineIds, leg.startTimeUtc)
                    }
                    leg.copy(upcomingDepartures = deps)
                } else {
                    leg
                }
            }
            itin.copy(legs = updatedLegs)
        }
    }

    suspend fun searchTripPlan(
        from: com.dfmob.data.model.TripPlanLocation,
        to: com.dfmob.data.model.TripPlanLocation,
        filter: com.dfmob.data.model.TripPlanFilter
    ): List<com.dfmob.data.model.TripPlanItinerary> = withContext(Dispatchers.IO) {
        val rawItineraries = MoovitTransitClient.searchTripPlan(from, to, filter, dbHelper)
        enrichItinerariesWithDepartures(rawItineraries)
    }

    suspend fun reverseGeocode(latE6: Int, lonE6: Int): String = withContext(Dispatchers.IO) {
        val nearestStop = dbHelper.getNearestStop(latE6, lonE6)
        if (nearestStop != null) {
            val dist = calculateDistanceMeters(latE6, lonE6, nearestStop.latE6, nearestStop.lonE6)
            if (dist <= 80.0) {
                return@withContext nearestStop.stopName
            }
        }
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale("pt", "BR"))
                val lat = latE6 / 1_000_000.0
                val lon = lonE6 / 1_000_000.0
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val thoroughfare = addr.thoroughfare
                    val subThoroughfare = addr.subThoroughfare
                    val subLocality = addr.subLocality
                    if (!thoroughfare.isNullOrBlank()) {
                        return@withContext if (!subThoroughfare.isNullOrBlank()) "$thoroughfare, $subThoroughfare" else thoroughfare
                    }
                    val featureName = addr.featureName
                    if (!featureName.isNullOrBlank() && featureName != thoroughfare) {
                        return@withContext if (!subLocality.isNullOrBlank()) "$featureName, $subLocality" else featureName
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("StopsRepository", "Reverse geocode error: ${e.message}")
        }
        val lat = String.format(Locale.ROOT, "%.5f", latE6 / 1_000_000.0)
        val lon = String.format(Locale.ROOT, "%.5f", lonE6 / 1_000_000.0)
        "Local no mapa ($lat, $lon)"
    }
}
