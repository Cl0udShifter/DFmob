package com.dfmob.data.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.MapTileIndex

class TrafficEndpointManagerTest {

    @Test
    fun testCandidateEndpointsTemplates() {
        val candidates = TrafficEndpointManager.ENDPOINT_CANDIDATES
        assertTrue(candidates.isNotEmpty())
        for (c in candidates) {
            assertTrue(c.contains("traffic"))
        }
    }

    @Test
    fun testMapTileIndexMethods() {
        val zoom = 14
        val x = 9469
        val y = 9469
        val tileIndex = MapTileIndex.getTileIndex(zoom, x, y)
        assertEquals(zoom, MapTileIndex.getZoom(tileIndex))
        assertEquals(x, MapTileIndex.getX(tileIndex))
        assertEquals(y, MapTileIndex.getY(tileIndex))
    }
}
