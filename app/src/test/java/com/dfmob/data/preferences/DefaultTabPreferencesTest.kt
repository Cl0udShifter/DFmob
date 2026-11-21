package com.dfmob.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultTabPreferencesTest {

    @Test
    fun testDefaultAppTabEnumValues() {
        val tabs = DefaultAppTab.values()
        assertEquals(4, tabs.size)
        assertEquals(DefaultAppTab.HOME, DefaultAppTab.valueOf("HOME"))
        assertEquals(DefaultAppTab.STOPS, DefaultAppTab.valueOf("STOPS"))
        assertEquals(DefaultAppTab.LINES, DefaultAppTab.valueOf("LINES"))
        assertEquals(DefaultAppTab.TRIP_PLANNER, DefaultAppTab.valueOf("TRIP_PLANNER"))
    }

    @Test
    fun testDefaultFallbackOnInvalidString() {
        val savedString = "INVALID_TAB"
        val tab = try {
            DefaultAppTab.valueOf(savedString)
        } catch (_: Exception) {
            DefaultAppTab.HOME
        }
        assertEquals(DefaultAppTab.HOME, tab)
    }

    @Test
    fun testDefaultTabLabelsMapping() {
        val labels = listOf(
            DefaultAppTab.HOME to "Início",
            DefaultAppTab.STOPS to "Paradas",
            DefaultAppTab.LINES to "Linhas",
            DefaultAppTab.TRIP_PLANNER to "Viagens"
        )
        assertEquals("Início", labels.first { it.first == DefaultAppTab.HOME }.second)
        assertEquals("Paradas", labels.first { it.first == DefaultAppTab.STOPS }.second)
        assertEquals("Linhas", labels.first { it.first == DefaultAppTab.LINES }.second)
        assertEquals("Viagens", labels.first { it.first == DefaultAppTab.TRIP_PLANNER }.second)
    }
}
