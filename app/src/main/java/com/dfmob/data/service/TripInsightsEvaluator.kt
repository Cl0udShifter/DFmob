package com.dfmob.data.service

import com.dfmob.data.model.TripInsight
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanItinerary

object TripInsightsEvaluator {

    fun evaluate(itineraries: List<TripPlanItinerary>): List<TripPlanItinerary> {
        if (itineraries.size <= 1) {
            return itineraries
        }

        val minDuration = itineraries.minOfOrNull { it.totalDurationMinutes } ?: return itineraries
        val minWalkDuration = itineraries.minOfOrNull { it.walkDurationMinutes } ?: 0
        val minWalkDistance = itineraries.minOfOrNull { it.walkDistanceMeters } ?: 0.0
        val now = System.currentTimeMillis()

        return itineraries.map { itin ->
            val qualified = mutableListOf<TripInsight>()

            // 1. Mais rapida (menor duracao total da pesquisa)
            if (itin.totalDurationMinutes == minDuration) {
                qualified.add(TripInsight.FASTEST)
            }

            // 2. Sem baldeacao (trajeto direto, 0 transferencias e no maximo 1 leg de transporte)
            val transitLegsCount = itin.legs.count { it.type == TripLegType.TRANSIT }
            if (transitLegsCount <= 1 && itin.transfersCount == 0) {
                qualified.add(TripInsight.DIRECT)
            }

            // 3. Menos caminhada (menor caminhada do grupo ou caminhada total <= 4 min / <= 300m)
            val hasMinWalk = (itin.walkDurationMinutes == minWalkDuration || itin.walkDistanceMeters <= minWalkDistance + 50.0) && itin.walkDurationMinutes <= 8
            val hasVeryLowWalk = itin.walkDurationMinutes <= 4 || itin.walkDistanceMeters <= 300.0
            if (hasMinWalk || hasVeryLowWalk) {
                qualified.add(TripInsight.LEAST_WALKING)
            }

            // 4. Partida em breve (onibus com partida em tempo real em ate 10 minutos ou inicio da viagem nos proximos 10 min)
            val firstTransit = itin.legs.firstOrNull { it.type == TripLegType.TRANSIT }
            var isDepartingSoon = false
            if (firstTransit != null) {
                val nextDep = firstTransit.upcomingDepartures.firstOrNull()
                if (nextDep != null && nextDep.isRealtime) {
                    val minsAway = if (nextDep.targetEpochMs >= now - 30_000L) {
                        Math.round((nextDep.targetEpochMs - now) / 60000.0).toInt()
                    } else -1
                    if (minsAway in 0..10) {
                        isDepartingSoon = true
                    }
                }
                if (!isDepartingSoon && firstTransit.startTimeUtc in (now - 30_000L)..(now + 10 * 60 * 1000L)) {
                    isDepartingSoon = true
                }
            }
            if (isDepartingSoon) {
                qualified.add(TripInsight.DEPARTING_SOON)
            }

            // Limitar a no maximo 2 dicas por card
            val top2 = qualified.distinctBy { it.type }.take(2)
            itin.copy(
                insight = top2.firstOrNull(),
                insights = top2
            )
        }
    }
}
