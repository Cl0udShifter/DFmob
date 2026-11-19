package com.dfmob.data.repository

import android.content.Context
import com.dfmob.data.database.DatabaseHelper
import com.dfmob.data.model.SemobAlert
import com.dfmob.data.model.TransitLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class AlertsRepository(private val context: Context) {

    private val dbHelper = DatabaseHelper(context)
    val preferences = AlertsPreferences(context)

    // Lista padrao e realista de comunicados operacionais da SEMOB DF
    private val defaultAlerts = listOf(
        SemobAlert(
            id = "semob_alert_2026_01",
            title = "Obras de recapeamento na via EPTG",
            description = "Devido a intervenções no pavimento das faixas centrais exclusivas da EPTG sentido Taguatinga, o tráfego de ônibus opera em pista marginal com previsão de lentidão nos horários de pico.",
            category = "OBRAS",
            dateFormatted = "Hoje, 06:00 - 22:00",
            affectedLineNumbers = listOf("0.373", "0.380", "373.2", "0.092")
        ),
        SemobAlert(
            id = "semob_alert_2026_02",
            title = "Interdição parcial na W3 Sul para reparos viários",
            description = "Trecho entre as quadras 508 e 710 com interdição de uma faixa exclusiva. As linhas circulares e troncais podem sofrer pequenos atrasos no itinerário.",
            category = "DESVIO",
            dateFormatted = "Hoje, 08:30 - 18:00",
            affectedLineNumbers = listOf("0.108", "108.3", "0.102")
        ),
        SemobAlert(
            id = "semob_alert_2026_03",
            title = "Manutenção e pintura na faixa exclusiva da EPNB",
            description = "Serviços de sinalização viária e manutenção no trecho Riacho Fundo I / Núcleo Bandeirante. Ônibus trafegando em faixa compartilhada temporariamente.",
            category = "OPERACAO",
            dateFormatted = "Hoje, 09:00 - 16:30",
            affectedLineNumbers = listOf("0.810", "0.811", "0.092", "0.391")
        ),
        SemobAlert(
            id = "semob_alert_2026_04",
            title = "Operação Especial da Rodoviária do Plano Piloto",
            description = "Reforço programado na frota de viagens noturnas e partidas extras nas plataformas superiores e inferiores.",
            category = "OPERACAO",
            dateFormatted = "Válido até 23:59",
            affectedLineNumbers = listOf("0.006", "0.024", "0.108", "0.092")
        )
    )

    suspend fun getAlerts(): List<SemobAlert> = withContext(Dispatchers.IO) {
        try {
            val endpoint = "https://raw.githubusercontent.com/Cl0udShifter/DFmob/main/semob_alerts.json"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "DFmob/1.4.6 (Android)")
            }
            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(json)
                val list = mutableListOf<SemobAlert>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val linesArr = obj.optJSONArray("affectedLineNumbers")
                    val lines = mutableListOf<String>()
                    if (linesArr != null) {
                        for (k in 0 until linesArr.length()) {
                            lines.add(linesArr.getString(k))
                        }
                    }
                    list.add(
                        SemobAlert(
                            id = obj.getString("id"),
                            title = obj.getString("title"),
                            description = obj.getString("description"),
                            category = obj.optString("category", "OPERACAO"),
                            dateFormatted = obj.optString("dateFormatted", "Hoje"),
                            affectedLineNumbers = lines,
                            timestampMs = obj.optLong("timestampMs", System.currentTimeMillis())
                        )
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            }
        } catch (_: Exception) {
            // Usa os comunicados locais se offline ou endpoint inacessivel
        }
        defaultAlerts
    }

    suspend fun getLineDetail(lineNumber: String): TransitLine? = withContext(Dispatchers.IO) {
        val detail = dbHelper.getLineDetailByNumber(lineNumber) ?: return@withContext null
        val firstDir = detail.directions.firstOrNull()
        TransitLine(
            routeId = (firstDir?.lineId ?: detail.groupId).toString(),
            agencyId = "",
            agencyName = detail.agencyName,
            lineNumber = detail.lineNumber,
            routeName = detail.lineName,
            transitType = if (detail.isMetro) 1 else 3,
            colorHex = detail.colorHex,
            textColorHex = "#FFFFFF",
            price = null,
            isCircular = detail.circular
        )
    }
}
