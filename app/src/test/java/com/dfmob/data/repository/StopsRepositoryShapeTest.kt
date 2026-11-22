package com.dfmob.data.repository

import com.dfmob.data.model.TransitStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopsRepositoryShapeTest {

    private fun makeStop(id: Int, lat: Double, lon: Double): TransitStop {
        return TransitStop(
            stopId = id,
            stopName = "Parada $id",
            stopCode = "$id",
            latE6 = (lat * 1_000_000).toInt(),
            lonE6 = (lon * 1_000_000).toInt(),
            isMetro = false,
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
    fun divergentShapeWithUncoveredStopsIsRejected() {
        // Simulates line 0.341: 12 stops, 5 of which (~42%) are >1km off the outdated SEMOB shape
        val stops = (0 until 7).map { i ->
            makeStop(i + 1, -15.80 + i * 0.002, -48.10)
        } + (7 until 12).map { i ->
            makeStop(i + 1, -15.80 + i * 0.002, -48.05) // ~5km away from shape
        }
        val outdatedShape = (0 until 30).map { i ->
            Pair(-15.80 + i * 0.002, -48.10)
        }

        assertFalse(StopsRepository.isShapeAlignedWithStops(outdatedShape, stops))
        val result = StopsRepository.matchShapeToDirection(
            shapes = mapOf("CIRCULAR" to outdatedShape),
            stops = stops
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun shapeWithLongPhantomSegmentIsRejected() {
        // All 10 stops are near the first half of the shape, while the second half (>60%) has no stops
        val stops = (0 until 10).map { i ->
            makeStop(i + 1, -15.80, -48.15 + i * 0.002)
        }
        val phantomShape = (0 until 60).map { i ->
            Pair(-15.80, -48.15 + i * 0.003) // extends far east with zero stops
        }

        assertFalse(StopsRepository.isShapeAlignedWithStops(phantomShape, stops))
        val result = StopsRepository.matchShapeToDirection(
            shapes = mapOf("IDA" to phantomShape),
            stops = stops
        )
        assertTrue(result.isEmpty())
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
