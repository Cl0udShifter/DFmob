package com.dfmob.data.service

import com.dfmob.data.model.TripDepartureItem
import com.dfmob.data.model.TripInsightType
import com.dfmob.data.model.TripLegType
import com.dfmob.data.model.TripPlanItinerary
import com.dfmob.data.model.TripPlanLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertTrue(result[0].insights.isEmpty())
        assertNull(result[0].insight)
    }

    @Test
    fun testMultipleInsightsPerCard() {
        // itin1: 20 min, 0 transfers, walk 8 min -> FASTEST and DIRECT (2 insights on 1 card)
        // itin2: 28 min, 0 transfers, walk 2 min -> DIRECT and LEAST_WALKING (2 insights on 2nd card)
        // itin3: 35 min, 1 transfer, walk 10 min, bus leaves in 3 min -> DEPARTING_SOON (1 insight on 3rd card)
        val itin1 = createDummyItinerary("itin-1", durationMinutes = 20, transfers = 0, walkMinutes = 8)
        val itin2 = createDummyItinerary("itin-2", durationMinutes = 28, transfers = 0, walkMinutes = 2)
        val itin3 = createDummyItinerary("itin-3", durationMinutes = 35, transfers = 1, walkMinutes = 10, departureMinutesAway = 3)

        val result = TripInsightsEvaluator.evaluate(listOf(itin1, itin2, itin3))
        assertEquals(3, result.size)

        // Card 1: FASTEST + DIRECT
        assertEquals(2, result[0].insights.size)
        assertEquals(TripInsightType.FASTEST, result[0].insights[0].type)
        assertEquals(TripInsightType.DIRECT, result[0].insights[1].type)

        // Card 2: DIRECT + LEAST_WALKING
        assertEquals(2, result[1].insights.size)
        assertEquals(TripInsightType.DIRECT, result[1].insights[0].type)
        assertEquals(TripInsightType.LEAST_WALKING, result[1].insights[1].type)

        // Card 3: DEPARTING_SOON
        assertEquals(1, result[2].insights.size)
        assertEquals(TripInsightType.DEPARTING_SOON, result[2].insights[0].type)
        assertEquals("Partida em breve", result[2].insights[0].label)
    }

    @Test
    fun testMaximumTwoInsightsPerCardEnforced() {
        // itin1: qualifies for FASTEST, DIRECT, LEAST_WALKING, DEPARTING_SOON -> exactly 2 insights
        val itin1 = createDummyItinerary("itin-1", durationMinutes = 15, transfers = 0, walkMinutes = 2, departureMinutesAway = 2)
        val itin2 = createDummyItinerary("itin-2", durationMinutes = 30, transfers = 1, walkMinutes = 10)

        val result = TripInsightsEvaluator.evaluate(listOf(itin1, itin2))
        assertEquals(2, result[0].insights.size)
        assertTrue(result[0].insights.size <= 2)
    }
}
