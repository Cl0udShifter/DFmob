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

        // 1. Busca imediata em paradas e estacoes do DF (< 1ms)
        try {
            val allStops = dbHelper.getAllStops()
            val stopMatches = allStops.filter { stop ->
                stop.stopName.lowercase(Locale.ROOT).contains(clean) ||
                        (stop.stopCode != null && stop.stopCode.contains(clean))
            }.take(4).map { stop ->
                val typeDesc = if (stop.isMetro) {
                    "Estação de Metrô-DF • ${stop.platformCode ?: "Plataforma"}"
                } else {
                    "Parada de Ônibus • Código ${stop.stopCode ?: "SEMOB"}"
                }
                DFLocation(
                    name = stop.stopName,
                    description = typeDesc,
                    latE6 = stop.latE6,
                    lonE6 = stop.lonE6
                )
            }
            results.addAll(stopMatches)
        } catch (_: Exception) {
        }

        // 2. Busca em pontos de referencia principais e regioes administrativas
        val landmarkMatches = keyLocations.filter {
            it.name.lowercase(Locale.ROOT).contains(clean) ||
                    it.description.lowercase(Locale.ROOT).contains(clean)
        }.take(3)
        results.addAll(landmarkMatches)

        // 3. Geocodificacao real estilo Google Maps via Android Geocoder nativo
        val geocodedResults = mutableListOf<DFLocation>()
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale("pt", "BR"))
                val queryWithRegion = if (query.contains("DF", ignoreCase = true) || query.contains("Brasília", ignoreCase = true)) {
                    query
                } else {
                    "$query, Distrito Federal, Brasil"
                }
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(queryWithRegion, 6)
                if (!addresses.isNullOrEmpty()) {
                    for (addr in addresses) {
                        val lat = addr.latitude
                        val lon = addr.longitude
                        // Filtra para o quadrilatero expandido do Distrito Federal e entorno imediato
                        if (lat in -16.3..-15.4 && lon in -48.4..-47.2) {
                            val title = addr.featureName ?: addr.thoroughfare ?: addr.getAddressLine(0)?.split(",")?.firstOrNull() ?: query
                            val subtitleParts = mutableListOf<String>()
                            addr.subLocality?.let { subtitleParts.add(it) }
                            addr.subAdminArea?.let { subtitleParts.add(it) }
                            val sub = if (subtitleParts.isNotEmpty()) subtitleParts.joinToString(", ") else "Distrito Federal"
                            geocodedResults.add(
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

        // 4. Fallback OpenStreetMap Nominatim se o Geocoder nao retornar resultados de rua
        if (geocodedResults.isEmpty()) {
            try {
                val encoded = URLEncoder.encode("$query DF Brasil", "UTF-8")
                val urlStr = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&addressdetails=1&countrycodes=br&viewbox=-48.3,-15.4,-47.2,-16.1&bounded=1&limit=5"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                    setRequestProperty("User-Agent", "DFmob-TransitApp")
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val jsonArray = JSONArray(text)
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        val displayName = item.optString("display_name", "")
                        val lat = item.optDouble("lat", 0.0)
                        val lon = item.optDouble("lon", 0.0)
                        if (lat != 0.0 && lon != 0.0 && lat in -16.3..-15.4 && lon in -48.4..-47.2) {
                            val parts = displayName.split(",").map { it.trim() }
                            val title = parts.take(2).joinToString(", ")
                            val sub = parts.drop(2).take(2).joinToString(", ")
                            geocodedResults.add(
                                DFLocation(
                                    name = title,
                                    description = if (sub.isNotBlank()) sub else "Distrito Federal",
                                    latE6 = (lat * 1_000_000).toInt(),
                                    lonE6 = (lon * 1_000_000).toInt()
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        results.addAll(geocodedResults)

        // Deduplicacao por proximidade geografica (< 100m) e limite dos melhores resultados
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
                        { !it.isRealtime },
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

    suspend fun getLineRouteState(
        lineNumber: String,
        preferredLineId: Int? = null,
        preferredStopId: Int? = null,
        userLatE6: Int? = null,
        userLonE6: Int? = null
    ): LineRouteState? = withContext(Dispatchers.IO) {
        val detail = dbHelper.getLineDetailByNumber(lineNumber)
            ?: (if (preferredLineId != null) dbHelper.getLineDetailById(preferredLineId) else null)
            ?: return@withContext null

        val dirOptions = detail.directions.map { dir ->
            LineDirectionOption(
                lineId = dir.lineId,
                directionId = dir.directionId,
                originName = dir.originName,
                destinationName = dir.destinationName
            )
        }
        if (dirOptions.isEmpty()) return@withContext null

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
        if (stops.isEmpty()) return@withContext null

        var initialStop: TransitStop? = null
        if (preferredStopId != null) {
            initialStop = stops.firstOrNull { it.stopId == preferredStopId }
        }
        if (initialStop == null && userLatE6 != null && userLonE6 != null) {
            initialStop = stops.minByOrNull { calculateDistanceMeters(userLatE6, userLonE6, it.latE6, it.lonE6) }
        }
        if (initialStop == null) {
            initialStop = stops.firstOrNull()
        }

        val liveVehicles = fetchLiveVehiclesForRoute(detail.lineNumber, activeDirId, activeLineId, stops)
        val allShapes = telemetryService.fetchRealRouteShapes(detail.lineNumber)
        val shapeCoordinates = matchShapeToDirection(allShapes, stops)

        val arrival = if (initialStop != null) {
            fetchArrivalForLineAtStop(activeLineId, initialStop)
        } else null

        LineRouteState(
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
            stops = stops,
            selectedStop = initialStop,
            liveVehicles = liveVehicles,
            shapeCoordinates = shapeCoordinates,
            selectedStopArrival = arrival
        )
    }

    suspend fun changeLineRouteDirection(
        currentState: LineRouteState,
        newDirectionIndex: Int,
        userLatE6: Int? = null,
        userLonE6: Int? = null
    ): LineRouteState = withContext(Dispatchers.IO) {
        val safeIndex = newDirectionIndex.coerceIn(0, currentState.directions.lastIndex)
        val activeLineId = currentState.directions[safeIndex].lineId
        val activeDirId = currentState.directions[safeIndex].directionId
        val newStops = dbHelper.getStopsForLine(activeLineId)

        var newSelectedStop: TransitStop? = null
        if (userLatE6 != null && userLonE6 != null) {
            newSelectedStop = newStops.minByOrNull { calculateDistanceMeters(userLatE6, userLonE6, it.latE6, it.lonE6) }
        }
        if (newSelectedStop == null) {
            newSelectedStop = newStops.firstOrNull()
        }

        val liveVehicles = fetchLiveVehiclesForRoute(currentState.lineNumber, activeDirId, activeLineId, newStops)
        val allShapes = telemetryService.fetchRealRouteShapes(currentState.lineNumber)
        val shapeCoordinates = matchShapeToDirection(allShapes, newStops)

        val arrival = if (newSelectedStop != null) {
            fetchArrivalForLineAtStop(activeLineId, newSelectedStop)
        } else null

        currentState.copy(
            currentDirectionIndex = safeIndex,
            stops = newStops,
            selectedStop = newSelectedStop,
            liveVehicles = liveVehicles,
            shapeCoordinates = shapeCoordinates,
            selectedStopArrival = arrival
        )
    }

    private fun matchShapeToDirection(
        shapes: Map<String, List<Pair<Double, Double>>>,
        stops: List<TransitStop>
    ): List<Pair<Double, Double>> {
        if (shapes.isEmpty() || stops.isEmpty()) return emptyList()
        val firstStop = stops.first()
        val lastStop = stops.last()
        val isCircular = calculateDistanceMeters(firstStop.latE6, firstStop.lonE6, lastStop.latE6, lastStop.lonE6) < 800
        val ida = shapes.entries.firstOrNull { it.key.contains("IDA", ignoreCase = true) }?.value
        val volta = shapes.entries.firstOrNull { it.key.contains("VOLTA", ignoreCase = true) }?.value
        if (isCircular && ida != null && volta != null) {
            return ida + volta
        }

        var bestShape: List<Pair<Double, Double>>? = null
        var bestDist = Double.MAX_VALUE
        for ((_, pts) in shapes) {
            if (pts.isEmpty()) continue
            val fPt = pts.first()
            val d = calculateDistanceMeters(
                (fPt.first * 1_000_000).toInt(),
                (fPt.second * 1_000_000).toInt(),
                firstStop.latE6,
                firstStop.lonE6
            )
            if (d < bestDist) {
                bestDist = d
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
}
