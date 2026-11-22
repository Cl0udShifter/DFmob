package com.dfmob.data.repository

import com.dfmob.data.model.TransitStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopsRepositoryShapeTest {

    private fun makeStop(
        id: Int,
        lat: Double,
        lon: Double,
        isMetro: Boolean = false
    ): TransitStop {
        return TransitStop(
            stopId = id,
            stopName = "Parada $id",
            stopCode = "$id",
            latE6 = (lat * 1_000_000).toInt(),
            lonE6 = (lon * 1_000_000).toInt(),
            isMetro = isMetro,
            platformCode = null
        )
    }

    @Test
    fun alignedShapeIsAcceptedAndReturned() {
        val stops = (0 until 12).map { i ->
            makeStop(i + 1, -15.80 + i * 0.002, -48.10)
        }
        val shape = (0 until 24).map { i ->
            Pair(-15.80 + i * 0.001, -48.10)
        }

        val result = StopsRepository.matchShapeToDirection(
            shapes = mapOf("CIRCULAR" to shape),
            stops = stops
        )

        assertTrue(StopsRepository.isShapeAlignedWithStops(shape, stops))
        assertEquals(shape, result)
    }

    @Test
    fun divergentSemobShapeIsPreservedAndDetectedAsMisaligned() {
        // Simulates line 0.341: SEMOB shape is kept as the real route, while DB stops alignment returns false
        val stops = (0 until 7).map { i ->
            makeStop(i + 1, -15.80 + i * 0.002, -48.10)
        } + (7 until 12).map { i ->
            makeStop(i + 1, -15.80 + i * 0.002, -48.05)
        }
        val semobShape = (0 until 30).map { i ->
            Pair(-15.80 + i * 0.002, -48.10)
        }

        assertFalse(StopsRepository.isShapeAlignedWithStops(semobShape, stops))
        val result = StopsRepository.matchShapeToDirection(
            shapes = mapOf("CIRCULAR" to semobShape),
            stops = stops
        )
        assertEquals(semobShape, result)
    }

    @Test
    fun reconstructStopsAlongShapeOrdersRightSideAndFiltersMetroAndDuplicates() {
        // Eastbound street at lat = -15.80, from lon = -48.10 to -48.04
        val shape = (0..6).map { i -> Pair(-15.80, -48.10 + i * 0.01) }

        // South of street (~28m) = right side of eastbound direction
        val rightStop1 = makeStop(1, -15.80025, -48.09)
        val rightStop2 = makeStop(2, -15.80025, -48.07)
        // North of street (~35m) = opposite side (left of eastbound), should be filtered out
        val oppositeSideStop = makeStop(3, -15.79968, -48.07)
        // Metro station on the route, should be excluded for bus route reconstruction
        val metroStop = makeStop(4, -15.80010, -48.06, isMetro = true)
        // Clustered terminal bays at -48.05 within 15m of each other: keep only closest
        val terminalBayNear = makeStop(5, -15.80005, -48.05)
        val terminalBayFar = makeStop(6, -15.80020, -48.05)
        // Far away stop (> 55m), should be excluded
        val farStop = makeStop(7, -15.80200, -48.07)

        val reconstructed = StopsRepository.reconstructStopsAlongShape(
            shape = shape,
            allStops = listOf(farStop, terminalBayFar, oppositeSideStop, rightStop2, metroStop, terminalBayNear, rightStop1)
        )

        assertEquals(listOf(1, 2, 5), reconstructed.map { it.stopId })
    }

    @Test
    fun expressLineWithFewStopsAndFullStopCoverageIsAccepted() {
        val stops = listOf(
            makeStop(1, -15.90, -48.00),
            makeStop(2, -15.80, -47.90)
        )
        val expressShape = (0..20).map { i ->
            val frac = i / 20.0
            Pair(-15.90 + frac * 0.10, -48.00 + frac * 0.10)
        }

        assertTrue(StopsRepository.isShapeAlignedWithStops(expressShape, stops))
        val result = StopsRepository.matchShapeToDirection(
            shapes = mapOf("IDA" to expressShape),
            stops = stops
        )
        assertEquals(expressShape, result)
    }
}
