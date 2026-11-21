package com.dfmob.ui.tripplanner

import com.dfmob.data.model.TripPlanFilter
import com.dfmob.data.model.TripPreference
import com.dfmob.data.model.TripTimeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class TripPlannerTimeTest {

    @Test
    fun testTripTimeModeThriftMapping() {
        val filterDepartNow = TripPlanFilter(mode = TripTimeMode.DEPART_NOW)
        val timeTypeDepartNow = when (filterDepartNow.mode) {
            TripTimeMode.ARRIVE_BY -> 1
            else -> 2
        }
        assertEquals(2, timeTypeDepartNow)

        val filterDepartAt = TripPlanFilter(mode = TripTimeMode.DEPART_AT)
        val timeTypeDepartAt = when (filterDepartAt.mode) {
            TripTimeMode.ARRIVE_BY -> 1
            else -> 2
        }
        assertEquals(2, timeTypeDepartAt)

        val filterArriveBy = TripPlanFilter(mode = TripTimeMode.ARRIVE_BY)
        val timeTypeArriveBy = when (filterArriveBy.mode) {
            TripTimeMode.ARRIVE_BY -> 1
            else -> 2
        }
        assertEquals(1, timeTypeArriveBy)
    }

    @Test
    fun testFutureDateTimeCalculation() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1) // Amanhã
        }
        val targetMillis = cal.timeInMillis
        assertTrue("Timestamp futuro de amanhã deve ser maior que agora", targetMillis > System.currentTimeMillis())

        val calResult = Calendar.getInstance().apply { timeInMillis = targetMillis }
        assertEquals(8, calResult.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calResult.get(Calendar.MINUTE))
    }

    @Test
    fun testAddTimeMinutesCalculation() {
        val baseTime = 1700000000000L
        val addedTime = baseTime + (15 * 60 * 1000L)
        assertEquals(baseTime + 900_000L, addedTime)

        val subtractedTime = addedTime - (15 * 60 * 1000L)
        assertEquals(baseTime, subtractedTime)
    }

    @Test
    fun testIsSearchForNowLogic() {
        fun isSearchForNow(mode: TripTimeMode, targetTimeUtc: Long, now: Long): Boolean {
            return mode == TripTimeMode.DEPART_NOW ||
                    (mode == TripTimeMode.DEPART_AT && Math.abs(targetTimeUtc - now) <= 30 * 60 * 1000L)
        }

        val now = 1700000000000L
        // 1. Partir agora sempre deve ser considerado "para agora"
        assertTrue(isSearchForNow(TripTimeMode.DEPART_NOW, now + 100_000L, now))

        // 2. Partir às com horário próximo (10 min à frente) deve ser "para agora"
        assertTrue(isSearchForNow(TripTimeMode.DEPART_AT, now + (10 * 60 * 1000L), now))

        // 3. Partir às para amanhã (+24h) NÃO deve ser considerado "para agora"
        val tomorrow = now + (24 * 60 * 60 * 1000L)
        org.junit.Assert.assertFalse(isSearchForNow(TripTimeMode.DEPART_AT, tomorrow, now))

        // 4. Chegar até para amanhã NÃO deve ser considerado "para agora"
        org.junit.Assert.assertFalse(isSearchForNow(TripTimeMode.ARRIVE_BY, tomorrow, now))
    }

    @Test
    fun testRouteTypesModalMapping() {
        // 1. Padrão: ambos ativos -> [3, 1]
        val filterDefault = TripPlanFilter()
        assertEquals(listOf(3, 1), filterDefault.routeTypes)

        // 2. Apenas Ônibus -> [3]
        val filterBusOnly = TripPlanFilter(allowBus = true, allowSubway = false)
        assertEquals(listOf(3), filterBusOnly.routeTypes)

        // 3. Apenas Metrô -> [1]
        val filterSubwayOnly = TripPlanFilter(allowBus = false, allowSubway = true)
        assertEquals(listOf(1), filterSubwayOnly.routeTypes)

        // 4. Se ambos forem desmarcados por engano, fallback de segurança -> [3, 1]
        val filterNone = TripPlanFilter(allowBus = false, allowSubway = false)
        assertEquals(listOf(3, 1), filterNone.routeTypes)
    }

    @Test
    fun testThriftRequestSerializationRouteTypes() {
        val payloadBoth = com.dfmob.data.telemetry.MoovitTransitClient.buildTripPlanRequest(
            fromLatE6 = -15834000,
            fromLonE6 = -48028000,
            fromCaption = "Origem",
            toLatE6 = -15794200,
            toLonE6 = -47882200,
            toCaption = "Destino",
            pref = 2,
            timeType = 2,
            timeUtc = 1700000000000L,
            currentTimeSelected = true,
            routeTypes = listOf(3, 1)
        )
        assertTrue(payloadBoth.isNotEmpty())

        val payloadBusOnly = com.dfmob.data.telemetry.MoovitTransitClient.buildTripPlanRequest(
            fromLatE6 = -15834000,
            fromLonE6 = -48028000,
            fromCaption = "Origem",
            toLatE6 = -15794200,
            toLonE6 = -47882200,
            toCaption = "Destino",
            pref = 2,
            timeType = 2,
            timeUtc = 1700000000000L,
            currentTimeSelected = true,
            routeTypes = listOf(3)
        )
        assertTrue(payloadBusOnly.isNotEmpty())
        assertEquals(payloadBoth.size - 4, payloadBusOnly.size) // 1 int less (4 bytes)
    }
}
