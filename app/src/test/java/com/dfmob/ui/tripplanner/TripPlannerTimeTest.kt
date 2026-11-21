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
}
