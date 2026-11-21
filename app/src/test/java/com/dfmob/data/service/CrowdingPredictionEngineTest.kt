package com.dfmob.data.service

import com.dfmob.data.model.CongestionLevel
import com.dfmob.data.model.CrowdingCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CrowdingPredictionEngineTest {

    private fun getTimestampFor(
        dayOfWeek: Int,
        hour: Int,
        minute: Int
    ): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"))
        cal.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun testSundayAlwaysEmpty() {
        val time = getTimestampFor(Calendar.SUNDAY, 10, 0)
        val crowding = CrowdingPredictionEngine.predict(
            destination = "Rod.P.Piloto",
            lineName = "0.373",
            targetEpochMs = time
        )
        assertEquals(CrowdingCategory.LOW, crowding.category)
        assertEquals(CongestionLevel.EMPTY, crowding.level)
        assertEquals("Geralmente com assentos", crowding.displayLabel)
    }

    @Test
    fun testMorningPeakHeadingToPlanoPiloto() {
        // Segunda-feira as 07:30 da manha: saindo de Taguatinga para a Rodoviaria do Plano Piloto
        val time = getTimestampFor(Calendar.MONDAY, 7, 30)
        val crowding = CrowdingPredictionEngine.predict(
            destination = "Rod.P.Piloto",
            lineName = "Taguatinga Sul / Eixo Sul",
            targetEpochMs = time,
            stopName = "Tag. Centro | Praça do Relógio"
        )
        assertEquals(CrowdingCategory.HIGH, crowding.category)
        assertEquals(CongestionLevel.FULL, crowding.level)
        assertEquals("Geralmente lotado", crowding.displayLabel)
        assertEquals("Lotado", crowding.shortLabel)
    }

    @Test
    fun testMorningPeakContraPicoHeadingToRA() {
        // Segunda-feira as 07:30 da manha: saindo da Rodoviaria em direcao a Ceilandia (contra-pico)
        val time = getTimestampFor(Calendar.MONDAY, 7, 30)
        val crowding = CrowdingPredictionEngine.predict(
            destination = "Ceilândia Sul",
            lineName = "Rod.P.Piloto / Ceilândia",
            targetEpochMs = time,
            stopName = "Rod.P.Piloto | Plataforma A"
        )
        assertEquals(CrowdingCategory.LOW, crowding.category)
        assertEquals(CongestionLevel.FEW, crowding.level)
        assertEquals("Geralmente com assentos", crowding.displayLabel)
    }

    @Test
    fun testAfternoonPeakHeadingToRA() {
        // Quarta-feira as 18:15: saindo da Rodoviaria para Samambaia (pico de volta para casa)
        val time = getTimestampFor(Calendar.WEDNESDAY, 18, 15)
        val crowding = CrowdingPredictionEngine.predict(
            destination = "Samambaia Norte",
            lineName = "Rod.P.Piloto / EPNB",
            targetEpochMs = time,
            stopName = "Rod.P.Piloto | Plataforma E"
        )
        assertEquals(CrowdingCategory.HIGH, crowding.category)
        assertEquals(CongestionLevel.FULL, crowding.level)
        assertEquals("Geralmente lotado", crowding.displayLabel)
    }

    @Test
    fun testAfternoonPeakContraPicoHeadingToCenter() {
        // Quarta-feira as 18:15: indo para o Plano Piloto (contra-pico)
        val time = getTimestampFor(Calendar.WEDNESDAY, 18, 15)
        val crowding = CrowdingPredictionEngine.predict(
            destination = "Rod.P.Piloto",
            lineName = "Samambaia / Eixo",
            targetEpochMs = time
        )
        assertEquals(CrowdingCategory.LOW, crowding.category)
        assertEquals(CongestionLevel.FEW, crowding.level)
    }

    @Test
    fun testOffPeakMidday() {
        // Terca-feira as 14:00 (entre-pico)
        val time = getTimestampFor(Calendar.TUESDAY, 14, 0)
        val crowding = CrowdingPredictionEngine.predict(
            destination = "W3 Sul",
            lineName = "0.809",
            targetEpochMs = time
        )
        assertEquals(CrowdingCategory.LOW, crowding.category)
        assertEquals(CongestionLevel.LOW, crowding.level)
        assertEquals("Geralmente com assentos", crowding.displayLabel)
    }

    @Test
    fun testLateNightMadrugada() {
        // Quinta-feira as 23:45 (madrugada)
        val time = getTimestampFor(Calendar.THURSDAY, 23, 45)
        val crowding = CrowdingPredictionEngine.predict(
            destination = "Rod.P.Piloto",
            lineName = "Corujão",
            targetEpochMs = time
        )
        assertEquals(CrowdingCategory.LOW, crowding.category)
        assertEquals(CongestionLevel.EMPTY, crowding.level)
    }
}
