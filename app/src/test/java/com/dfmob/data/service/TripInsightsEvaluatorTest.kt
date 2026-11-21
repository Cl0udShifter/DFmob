package com.dfmob.data.service

import com.dfmob.data.model.TripDepartureItem
import com.dfmob.data.model.TripInsight
import com.dfmob.data.model.TripInsightType
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripInsightsEvaluatorTest {

    private fun createDummyItinerary(
        guid: String,
        durationMinutes: Int,
        transfers: Int,
        walkMinutes: Int,
        walkDistanceMeters: Double = walkMinutes * 70.0,
        departureMinutesAway: Int? = null
    ): TripPlanItinerary {
        val now = System.currentTimeMillis()
        val legs = mutableListOf<TripPlanLeg>()

        if (walkMinutes > 0) {
            legs.add(
                TripPlanLeg(
                    type = TripLegType.WALK,
                    startTimeUtc = now,
                    endTimeUtc = now + (walkMinutes * 60_000L),
                    durationMinutes = walkMinutes,
                    distanceMeters = walkDistanceMeters
                )
            )
        }

        val transitLegCount = if (transfers == 0) 1 else 2
        for (i in 0 until transitLegCount) {
            val departures = if (i == 0 && departureMinutesAway != null) {
                listOf(
                    TripDepartureItem(
                        lineNumber = "0.100",
                        timeFormatted = "${departureMinutesAway} min",
                        isRealtime = true,
                        targetEpochMs = now + (departureMinutesAway * 60_000L)
                    )
                )
            } else emptyList<TripDepartureItem>()

            val legStart = now + (walkMinutes * 60_000L)
            val legDuration = (durationMinutes - walkMinutes) / transitLegCount
            legs.add(
                TripPlanLeg(
                    type = TripLegType.TRANSIT,
                    startTimeUtc = legStart,
                    endTimeUtc = legStart + (legDuration * 60_000L),
                    durationMinutes = legDuration,
                    distanceMeters = 5000.0,
                    lines = emptyList(),
                    upcomingDepartures = departures
                )
            )
        }

        return TripPlanItinerary(
            guid = guid,
            totalDurationMinutes = durationMinutes,
            startTimeUtc = now,
            endTimeUtc = now + (durationMinutes * 60_000L),
            walkDurationMinutes = walkMinutes,
            walkDistanceMeters = walkDistanceMeters,
            transfersCount = transfers,
            legs = legs
        )
    }

    @Test
    fun testSingleItineraryReturnsNoInsight() {
        val single = createDummyItinerary("itin-1", durationMinutes = 30, transfers = 0, walkMinutes = 5)
        val result = TripInsightsEvaluator.evaluate(listOf(single))
        assertEquals(1, result.size)
        assertNull(result[0].insight)
    }

    @Test
    fun testFastestAndDirectInsights() {
        // itin1: 25 min, 1 transfer, walk 8 min -> FASTEST
        // itin2: 32 min, 0 transfers, walk 6 min -> DIRECT
        // itin3: 40 min, 1 transfer, walk 7 min -> No extra insight
        val itin1 = createDummyItinerary("itin-1", durationMinutes = 25, transfers = 1, walkMinutes = 8)
        val itin2 = createDummyItinerary("itin-2", durationMinutes = 32, transfers = 0, walkMinutes = 6)
        val itin3 = createDummyItinerary("itin-3", durationMinutes = 40, transfers = 1, walkMinutes = 7)

        val result = TripInsightsEvaluator.evaluate(listOf(itin1, itin2, itin3))
        assertEquals(3, result.size)

        assertEquals(TripInsightType.FASTEST, result[0].insight?.type)
        assertEquals("Mais rápida", result[0].insight?.label)

        assertEquals(TripInsightType.DIRECT, result[1].insight?.type)
        assertEquals("Sem baldeação", result[1].insight?.label)

        assertNull(result[2].insight)
    }

    @Test
    fun testLeastWalkingInsight() {
        // itin1: 20 min, 0 transfers, walk 10 min -> FASTEST (also direct, but fastest takes priority)
        // itin2: 26 min, 0 transfers, walk 2 min (walk diff = 8 min >= 4 min, walk <= 6 min) -> LEAST_WALKING
        val itin1 = createDummyItinerary("itin-1", durationMinutes = 20, transfers = 0, walkMinutes = 10)
        val itin2 = createDummyItinerary("itin-2", durationMinutes = 26, transfers = 0, walkMinutes = 2)

        val result = TripInsightsEvaluator.evaluate(listOf(itin1, itin2))
        assertEquals(2, result.size)

        assertEquals(TripInsightType.FASTEST, result[0].insight?.type)
        assertEquals(TripInsightType.LEAST_WALKING, result[1].insight?.type)
        assertEquals("Menos caminhada", result[1].insight?.label)
    }

    @Test
    fun testDepartingSoonInsight() {
        // itin1: 25 min, 0 transfers, walk 5 min, leaves in 15 min -> FASTEST
        // itin2: 28 min, 0 transfers, walk 5 min, leaves in 2 min -> DEPARTING_SOON
        val itin1 = createDummyItinerary("itin-1", durationMinutes = 25, transfers = 0, walkMinutes = 5, departureMinutesAway = 15)
        val itin2 = createDummyItinerary("itin-2", durationMinutes = 28, transfers = 0, walkMinutes = 5, departureMinutesAway = 2)

        val result = TripInsightsEvaluator.evaluate(listOf(itin1, itin2))
        assertEquals(2, result.size)

        assertEquals(TripInsightType.FASTEST, result[0].insight?.type)
        assertEquals(TripInsightType.DEPARTING_SOON, result[1].insight?.type)
        assertEquals("Partida em breve", result[1].insight?.label)
    }
}
