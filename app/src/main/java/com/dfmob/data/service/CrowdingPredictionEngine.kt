package com.dfmob.data.service

import com.dfmob.data.model.CongestionLevel
import com.dfmob.data.model.CongestionSource
import com.dfmob.data.model.CrowdingInfo
import java.util.Calendar
import java.util.TimeZone

object CrowdingPredictionEngine {

    private val CENTRAL_KEYWORDS = listOf(
        "rod.p.piloto", "rodoviaria", "rodoviária", "plano piloto", "w3 sul", "w3 norte",
        "w3", "eixo", "esplanada", "l2 sul", "l2 norte", "l2", "unb", "setor comercial",
        "scs", "scn", "asa sul", "asa norte", "zona central", "patio brasil", "pátio brasil"
    )

    private val RA_KEYWORDS = listOf(
        "ceilandia", "ceilândia", "taguatinga", "tag.", "samambaia", "riacho fundo", "riacho",
        "recanto das emas", "recanto", "gama", "santa maria", "planaltina", "sobradinho",
        "guara", "guará", "brazlandia", "brazlândia", "sao sebastiao", "são sebastião",
        "paranoa", "paranoá", "itapoa", "itapoã", "estrutural", "aguas claras", "águas claras",
        "vicente pires", "sol nascente", "varjao", "varjão", "nucleo bandeirante", "núcleo bandeirante"
    )

    fun predict(
        destination: String,
        lineName: String,
        targetEpochMs: Long = System.currentTimeMillis(),
        stopName: String? = null
    ): CrowdingInfo {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("America/Sao_Paulo"))
        cal.timeInMillis = targetEpochMs

        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val timeMinutes = hour * 60 + minute

        // Finais de semana e feriados: baixa lotacao na maioria das linhas
        if (dayOfWeek == Calendar.SUNDAY) {
            return CrowdingInfo(CongestionLevel.EMPTY, CongestionSource.PREDICTION)
        }
        if (dayOfWeek == Calendar.SATURDAY) {
            // Sabado de manha (07:30 as 12:30): lotacao moderada em linhas de feira/comercio
            return if (timeMinutes in 450..750) {
                CrowdingInfo(CongestionLevel.MED, CongestionSource.PREDICTION)
            } else {
                CrowdingInfo(CongestionLevel.LOW, CongestionSource.PREDICTION)
            }
        }

        // Madrugada em dias uteis (23:00 as 05:30)
        if (timeMinutes >= 1380 || timeMinutes < 330) {
            return CrowdingInfo(CongestionLevel.EMPTY, CongestionSource.PREDICTION)
        }

        val destLower = destination.lowercase()
        val lineLower = lineName.lowercase()
        val stopLower = stopName?.lowercase() ?: ""

        val isGoingToCenter = if (destLower.isNotBlank()) {
            CENTRAL_KEYWORDS.any { destLower.contains(it) }
        } else {
            CENTRAL_KEYWORDS.any { lineLower.contains(it) }
        }

        val isGoingToRA = if (destLower.isNotBlank()) {
            RA_KEYWORDS.any { destLower.contains(it) }
        } else {
            RA_KEYWORDS.any { lineLower.contains(it) }
        }

        val isOriginFromCenter = CENTRAL_KEYWORDS.any { stopLower.contains(it) }

        // Pico da manha (06:00 as 08:45 -> 360 as 525 minutos)
        if (timeMinutes in 360..525) {
            return if (isGoingToCenter) {
                CrowdingInfo(CongestionLevel.FULL, CongestionSource.PREDICTION)
            } else if (isGoingToRA && isOriginFromCenter) {
                // Contra-pico
                CrowdingInfo(CongestionLevel.FEW, CongestionSource.PREDICTION)
            } else {
                // Circular ou intra-RA
                CrowdingInfo(CongestionLevel.MED, CongestionSource.PREDICTION)
            }
        }

        // Transicao matutina (08:46 as 09:45 -> 526 as 585 minutos)
        if (timeMinutes in 526..585) {
            return if (isGoingToCenter) {
                CrowdingInfo(CongestionLevel.MED, CongestionSource.PREDICTION)
            } else {
                CrowdingInfo(CongestionLevel.LOW, CongestionSource.PREDICTION)
            }
        }

        // Entre-pico (09:46 as 15:45 -> 586 as 945 minutos)
        if (timeMinutes in 586..945) {
            return CrowdingInfo(CongestionLevel.LOW, CongestionSource.PREDICTION)
        }

        // Transicao vespertina (15:46 as 16:59 -> 946 as 1019 minutos)
        if (timeMinutes in 946..1019) {
            return if (isGoingToRA || isOriginFromCenter) {
                CrowdingInfo(CongestionLevel.MED, CongestionSource.PREDICTION)
            } else {
                CrowdingInfo(CongestionLevel.LOW, CongestionSource.PREDICTION)
            }
        }

        // Pico da tarde/noite (17:00 as 19:30 -> 1020 as 1170 minutos)
        if (timeMinutes in 1020..1170) {
            return if (isGoingToRA || isOriginFromCenter) {
                CrowdingInfo(CongestionLevel.FULL, CongestionSource.PREDICTION)
            } else if (isGoingToCenter) {
                // Contra-pico no sentido centro
                CrowdingInfo(CongestionLevel.FEW, CongestionSource.PREDICTION)
            } else {
                CrowdingInfo(CongestionLevel.MED, CongestionSource.PREDICTION)
            }
        }

        // Descompressao noturna (19:31 as 20:45 -> 1171 as 1245 minutos)
        if (timeMinutes in 1171..1245) {
            return if (isGoingToRA || isOriginFromCenter) {
                CrowdingInfo(CongestionLevel.MED, CongestionSource.PREDICTION)
            } else {
                CrowdingInfo(CongestionLevel.LOW, CongestionSource.PREDICTION)
            }
        }

        // Noite (20:46 as 22:59 -> 1246 as 1379 minutos)
        return CrowdingInfo(CongestionLevel.LOW, CongestionSource.PREDICTION)
    }
}
