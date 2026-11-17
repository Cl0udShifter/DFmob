package com.dfmob.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.dfmob.data.model.TransitStop
import java.io.File
import java.io.FileOutputStream

class DatabaseHelper(private val context: Context) {

    private val dbName = "dfmob.db"
    private val targetRevision = 20261128
    private var database: SQLiteDatabase? = null

    init {
        ensureDatabaseCopied()
        openDatabase()
    }

    private fun ensureDatabaseCopied() {
        val dbFile = context.getDatabasePath(dbName)
        val prefs = context.getSharedPreferences("dfmob_db_prefs", Context.MODE_PRIVATE)
        val currentInstalledRevision = prefs.getInt("installed_revision", -1)

        if (!dbFile.exists() || currentInstalledRevision < targetRevision) {
            dbFile.parentFile?.mkdirs()
            context.assets.open("databases/$dbName").use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }
            prefs.edit().putInt("installed_revision", targetRevision).apply()
        }
    }

    private fun openDatabase() {
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            database = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
        }
    }

    fun getAllStops(): List<TransitStop> {
        val db = database ?: return emptyList()
        val cursor = db.rawQuery(
            "SELECT stop_id, stop_name, stop_code, lat_e6, lon_e6, is_metro, platform_code FROM stops",
            null
        )
        val list = mutableListOf<TransitStop>()
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    TransitStop(
                        stopId = c.getInt(0),
                        stopName = c.getString(1),
                        stopCode = c.getString(2),
                        latE6 = c.getInt(3),
                        lonE6 = c.getInt(4),
                        isMetro = c.getInt(5) == 1,
                        platformCode = c.getString(6)
                    )
                )
            }
        }
        return list
    }

    data class StopLineInfo(
        val lineId: Int,
        val lineNumber: String,
        val lineName: String,
        val originName: String,
        val destinationName: String,
        val agencyName: String,
        val colorHex: String,
        val isMetro: Boolean
    )

    fun getLinesPassingAtStop(stopId: Int): List<StopLineInfo> {
        val db = database ?: return emptyList()
        val sql = """
            SELECT DISTINCT l.line_id, g.line_number, g.line_name, l.origin_name, l.destination_name, a.agency_name, a.color_hex, g.is_metro
            FROM stop_sequences sq
            JOIN lines l ON sq.line_id = l.line_id
            JOIN line_groups g ON l.group_id = g.group_id
            JOIN agencies a ON g.agency_id = a.agency_id
            WHERE sq.stop_id = ?
            ORDER BY g.is_metro DESC, g.line_number ASC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(stopId.toString()))
        val list = mutableListOf<StopLineInfo>()
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    StopLineInfo(
                        lineId = c.getInt(0),
                        lineNumber = c.getString(1),
                        lineName = c.getString(2),
                        originName = c.getString(3),
                        destinationName = c.getString(4),
                        agencyName = c.getString(5),
                        colorHex = c.getString(6),
                        isMetro = c.getInt(7) == 1
                    )
                )
            }
        }
        return list
    }

    fun getNextScheduledDepartures(lineId: Int, stopId: Int, currentSeconds: Int, count: Int): List<Int> {
        val db = database ?: return emptyList()
        val list = mutableListOf<Int>()

        // 1. Obter a sequencia da parada nesta linha para calcular o tempo de trajeto
        var stopSequence = 1
        val seqSql = "SELECT stop_sequence FROM stop_sequences WHERE line_id = ? AND stop_id = ? LIMIT 1"
        try {
            db.rawQuery(seqSql, arrayOf(lineId.toString(), stopId.toString())).use { c ->
                if (c.moveToNext()) {
                    stopSequence = c.getInt(0)
                }
            }
        } catch (_: Exception) {}
        val travelTimeOffsetSec = (stopSequence - 1) * 75

        // 2. Viagens que partiram ontem antes da meia-noite e chegam hoje (se currentSeconds < travelTimeOffsetSec)
        if (currentSeconds < travelTimeOffsetSec) {
            val minDepYesterday = currentSeconds - travelTimeOffsetSec + 86400
            val sqlYesterday = """
                SELECT departure_time_seconds 
                FROM trips_schedule 
                WHERE line_id = ? AND departure_time_seconds >= ?
                ORDER BY departure_time_seconds ASC
            """.trimIndent()
            try {
                db.rawQuery(sqlYesterday, arrayOf(lineId.toString(), minDepYesterday.toString())).use { c ->
                    while (c.moveToNext() && list.size < count) {
                        val arr = c.getInt(0) - 86400 + travelTimeOffsetSec
                        if (arr >= currentSeconds && !list.contains(arr)) {
                            list.add(arr)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Viagens que partem hoje e chegam nesta parada a partir de currentSeconds
        val minDepToday = maxOf(0, currentSeconds - travelTimeOffsetSec)
        val sqlToday = """
            SELECT departure_time_seconds 
            FROM trips_schedule 
            WHERE line_id = ? AND departure_time_seconds >= ?
            ORDER BY departure_time_seconds ASC 
            LIMIT ?
        """.trimIndent()

        try {
            val cursorToday = db.rawQuery(sqlToday, arrayOf(lineId.toString(), minDepToday.toString(), (count + 3).toString()))
            cursorToday.use { c ->
                while (c.moveToNext() && list.size < count) {
                    val arrivalSec = c.getInt(0) + travelTimeOffsetSec
                    if (arrivalSec >= currentSeconds && !list.contains(arrivalSec)) {
                        list.add(arrivalSec)
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Se faltar horarios, busca nas partidas de amanha a partir da meia-noite
        if (list.size < count) {
            val needed = count - list.size
            val sqlTomorrow = """
                SELECT departure_time_seconds 
                FROM trips_schedule 
                WHERE line_id = ? AND departure_time_seconds >= 0
                ORDER BY departure_time_seconds ASC 
                LIMIT ?
            """.trimIndent()
            try {
                val cursorTomorrow = db.rawQuery(sqlTomorrow, arrayOf(lineId.toString(), needed.toString()))
                cursorTomorrow.use { c ->
                    while (c.moveToNext() && list.size < count) {
                        val arrivalTomorrowSec = c.getInt(0) + travelTimeOffsetSec + 86400
                        if (!list.contains(arrivalTomorrowSec)) {
                            list.add(arrivalTomorrowSec)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return list
    }
}
