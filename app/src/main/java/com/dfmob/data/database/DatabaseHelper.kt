package com.dfmob.data.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.dfmob.data.model.TransitStop
import java.io.File
import java.io.FileOutputStream

class DatabaseHelper(private val context: Context) {

    private val dbName = "dfmob.db"
    private val targetRevision = 20261129
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

    data class LineDetailResult(
        val groupId: Int,
        val lineNumber: String,
        val lineName: String,
        val agencyName: String,
        val circular: Boolean,
        val colorHex: String,
        val isMetro: Boolean,
        val directions: List<LineDirectionInfo>
    )

    data class LineDirectionInfo(
        val lineId: Int,
        val directionId: Int,
        val originName: String,
        val destinationName: String
    )

    fun getLinesPassingAtStop(stopId: Int): List<StopLineInfo> {
        val db = database ?: return emptyList()
        val sql = """
            SELECT DISTINCT l.line_id, g.line_number, g.line_name, l.origin_name, l.destination_name, a.agency_name, g.color_hex, g.is_metro
            FROM stop_sequences sq
            JOIN lines l ON sq.line_id = l.line_id
            JOIN line_groups g ON l.group_id = g.group_id
            JOIN agencies a ON g.agency_id = a.agency_id
            WHERE sq.stop_id = ?
            ORDER BY g.is_metro DESC, g.line_number ASC
        """.trimIndent()

        val cursor = db.rawQuery(sql, arrayOf(stopId.toString()))
        val list = mutableListOf<StopLineInfo>()
        val seen = mutableSetOf<String>()
        cursor.use { c ->
            while (c.moveToNext()) {
                val lineNum = c.getString(1)
                val dest = c.getString(4)
                val key = "${lineNum}_$dest"
                if (seen.add(key)) {
                    list.add(
                        StopLineInfo(
                            lineId = c.getInt(0),
                            lineNumber = lineNum,
                            lineName = c.getString(2),
                            originName = c.getString(3),
                            destinationName = dest,
                            agencyName = c.getString(5),
                            colorHex = c.getString(6),
                            isMetro = c.getInt(7) == 1
                        )
                    )
                }
            }
        }
        return list
    }

    fun getLineInfoById(lineId: Int): StopLineInfo? {
        val db = database ?: return null
        val sql = """
            SELECT l.line_id, g.line_number, g.line_name, l.origin_name, l.destination_name, a.agency_name, g.color_hex, g.is_metro
            FROM lines l
            JOIN line_groups g ON l.group_id = g.group_id
            JOIN agencies a ON g.agency_id = a.agency_id
            WHERE l.line_id = ?
            LIMIT 1
        """.trimIndent()
        return try {
            db.rawQuery(sql, arrayOf(lineId.toString())).use { c ->
                if (c.moveToNext()) {
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
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getLineDetailByNumber(lineNumber: String): LineDetailResult? {
        val db = database ?: return null
        val clean = lineNumber.trim()
        val groupSql = """
            SELECT g.group_id, g.line_number, g.line_name, a.agency_name, g.circular, g.color_hex, g.is_metro
            FROM line_groups g
            JOIN agencies a ON g.agency_id = a.agency_id
            WHERE g.line_number = ?
            LIMIT 1
        """.trimIndent()

        return try {
            val groupInfo = db.rawQuery(groupSql, arrayOf(clean)).use { c ->
                if (c.moveToNext()) {
                    Triple(
                        c.getInt(0),
                        listOf(c.getString(1), c.getString(2), c.getString(3), c.getString(5)),
                        Pair(c.getInt(4) == 1, c.getInt(6) == 1)
                    )
                } else null
            } ?: return null

            val groupId = groupInfo.first
            val strings = groupInfo.second
            val flags = groupInfo.third

            val dirSql = """
                SELECT l.line_id, l.direction_id, l.origin_name, l.destination_name
                FROM lines l
                WHERE l.group_id = ?
                ORDER BY l.direction_id ASC
            """.trimIndent()

            val dirs = mutableListOf<LineDirectionInfo>()
            db.rawQuery(dirSql, arrayOf(groupId.toString())).use { c ->
                while (c.moveToNext()) {
                    dirs.add(
                        LineDirectionInfo(
                            lineId = c.getInt(0),
                            directionId = c.getInt(1),
                            originName = c.getString(2),
                            destinationName = c.getString(3)
                        )
                    )
                }
            }

            LineDetailResult(
                groupId = groupId,
                lineNumber = strings[0],
                lineName = strings[1],
                agencyName = strings[2],
                circular = flags.first,
                colorHex = strings[3],
                isMetro = flags.second,
                directions = dirs
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getLineDetailById(lineId: Int): LineDetailResult? {
        val db = database ?: return null
        val sql = "SELECT g.line_number FROM lines l JOIN line_groups g ON l.group_id = g.group_id WHERE l.line_id = ? LIMIT 1"
        val lineNum = try {
            db.rawQuery(sql, arrayOf(lineId.toString())).use { c ->
                if (c.moveToNext()) c.getString(0) else null
            }
        } catch (_: Exception) { null } ?: return null
        return getLineDetailByNumber(lineNum)
    }

    fun getStopsForLine(lineId: Int): List<TransitStop> {
        val db = database ?: return emptyList()
        val sql = """
            SELECT s.stop_id, s.stop_name, s.stop_code, s.lat_e6, s.lon_e6, s.is_metro, s.platform_code
            FROM stop_sequences sq
            JOIN stops s ON sq.stop_id = s.stop_id
            WHERE sq.line_id = ?
            ORDER BY sq.stop_sequence ASC
        """.trimIndent()
        val list = mutableListOf<TransitStop>()
        return try {
            db.rawQuery(sql, arrayOf(lineId.toString())).use { c ->
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
            list
        } catch (_: Exception) {
            emptyList()
        }
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
