package com.dfmob.data.service

import com.dfmob.data.model.TripInsight
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanItinerary

object TripInsightsEvaluator {

    fun evaluate(itineraries: List<TripPlanItinerary>): List<TripPlanItinerary> {
        if (itineraries.size <= 1) {
            return itineraries
        }

        // 1. Identificar o itinerario mais rapido (menor duracao total)
        val minDuration = itineraries.minOfOrNull { it.totalDurationMinutes } ?: return itineraries
        val fastestIndex = itineraries.indexOfFirst { it.totalDurationMinutes == minDuration }
        val fastest = itineraries[fastestIndex]

        val results = itineraries.toMutableList()
        val assignedIndices = mutableSetOf<Int>()

        // Atribuir "Mais rapida" ao de menor tempo
        results[fastestIndex] = fastest.copy(insight = TripInsight.FASTEST)
        assignedIndices.add(fastestIndex)

        // 2. Se a mais rapida possui transbordo, verificar se existe alternativa direta (sem baldeacao)
        val fastestTransitLegs = fastest.legs.count { it.type == TripLegType.TRANSIT }
        if (fastestTransitLegs > 1 || fastest.transfersCount > 0) {
            val directCandidates = itineraries.mapIndexedNotNull { idx, itin ->
                if (!assignedIndices.contains(idx)) {
                    val transitCount = itin.legs.count { it.type == TripLegType.TRANSIT }
                    if (transitCount == 1 && itin.transfersCount == 0) idx else null
                } else null
            }
            if (directCandidates.isNotEmpty()) {
                val bestDirectIdx = directCandidates.minByOrNull { itineraries[it].totalDurationMinutes }
                if (bestDirectIdx != null) {
                    results[bestDirectIdx] = results[bestDirectIdx].copy(insight = TripInsight.DIRECT)
                    assignedIndices.add(bestDirectIdx)
                }
            }
        }

        // 3. Verificar se ha trajeto com significativamente menos caminhada
        val leastWalkCandidates = itineraries.mapIndexedNotNull { idx, itin ->
            if (!assignedIndices.contains(idx)) {
                val walkDiff = fastest.walkDurationMinutes - itin.walkDurationMinutes
                val distDiff = fastest.walkDistanceMeters - itin.walkDistanceMeters
                if ((walkDiff >= 4 || distDiff >= 250.0) && itin.walkDurationMinutes <= 6) {
                    Pair(idx, itin.walkDurationMinutes)
                } else null
            } else null
        }
        if (leastWalkCandidates.isNotEmpty()) {
            val bestWalkIdx = leastWalkCandidates.minByOrNull { it.second }?.first
            if (bestWalkIdx != null) {
                results[bestWalkIdx] = results[bestWalkIdx].copy(insight = TripInsight.LEAST_WALKING)
                assignedIndices.add(bestWalkIdx)
            }
        }

        // 4. Verificar partida imediata (onibus saindo nos proximos 5 minutos)
        val now = System.currentTimeMillis()
        for (i in results.indices) {
            if (!assignedIndices.contains(i)) {
                val itin = results[i]
                val firstTransit = itin.legs.firstOrNull { it.type == TripLegType.TRANSIT }
                val nextDep = firstTransit?.upcomingDepartures?.firstOrNull()
                if (nextDep != null) {
                    val minsAway = if (nextDep.targetEpochMs > now) {
                        Math.round((nextDep.targetEpochMs - now) / 60000.0).toInt()
                    } else -1
                    if (minsAway in 0..5) {
                        results[i] = itin.copy(insight = TripInsight.DEPARTING_SOON)
                        assignedIndices.add(i)
                        break
                    }
                }
            }
        }

        return results
    }
}
