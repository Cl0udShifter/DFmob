package com.dfmob.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dfmob.data.model.StopArrivalAlarm
import com.dfmob.data.preferences.NotificationPreferences
import com.dfmob.receiver.StopArrivalAlarmReceiver
import org.json.JSONObject

class StopAlarmsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("dfmob_stop_alarms_prefs", Context.MODE_PRIVATE)

    fun isAlarmActive(stopId: Int, lineNumber: String, destination: String): Boolean {
        val key = "${stopId}_${lineNumber}_${destination}"
        val json = prefs.getString(key, null) ?: return false
        return try {
            val obj = JSONObject(json)
            !obj.optBoolean("isAutoCancelled", false)
        } catch (_: Exception) {
            false
        }
    }

    fun getAlarm(stopId: Int, lineNumber: String, destination: String): StopArrivalAlarm? {
        val key = "${stopId}_${lineNumber}_${destination}"
        val json = prefs.getString(key, null) ?: return null
        return try {
            val obj = JSONObject(json)
            StopArrivalAlarm(
                lineNumber = obj.getString("lineNumber"),
                destination = obj.getString("destination"),
                stopId = obj.getInt("stopId"),
                stopName = obj.getString("stopName"),
                targetEpochMs = obj.getLong("targetEpochMs"),
                notified15Min = obj.optBoolean("notified15Min", false),
                notified10Min = obj.optBoolean("notified10Min", false),
                notified5Min = obj.optBoolean("notified5Min", false),
                notified2Min = obj.optBoolean("notified2Min", false),
                isAutoCancelled = obj.optBoolean("isAutoCancelled", false)
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getAllActiveAlarms(): List<StopArrivalAlarm> {
        val list = mutableListOf<StopArrivalAlarm>()
        val allEntries = prefs.all
        for ((_, value) in allEntries) {
            val json = value as? String ?: continue
            try {
                val obj = JSONObject(json)
                if (!obj.optBoolean("isAutoCancelled", false)) {
                    list.add(
                        StopArrivalAlarm(
                            lineNumber = obj.getString("lineNumber"),
                            destination = obj.getString("destination"),
                            stopId = obj.getInt("stopId"),
                            stopName = obj.getString("stopName"),
                            targetEpochMs = obj.getLong("targetEpochMs"),
                            notified15Min = obj.optBoolean("notified15Min", false),
                            notified10Min = obj.optBoolean("notified10Min", false),
                            notified5Min = obj.optBoolean("notified5Min", false),
                            notified2Min = obj.optBoolean("notified2Min", false),
                            isAutoCancelled = false
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        return list
    }

    fun setAlarm(alarm: StopArrivalAlarm) {
        val now = System.currentTimeMillis()
        val effectiveMinutes = if (alarm.targetEpochMs > 0L) {
            maxOf(0, Math.round((alarm.targetEpochMs - now) / 60000.0).toInt())
        } else 0

        // Disparo imediato caso o alarme tenha sido ativado com o onibus ja dentro do intervalo de um marco
        val notifPrefs = NotificationPreferences(context)
        var updated = alarm
        if (notifPrefs.isMilestoneEnabled(15) && !updated.notified15Min && effectiveMinutes in 11..15) {
            updated = updated.copy(notified15Min = true)
            StopArrivalAlarmReceiver.showMilestoneNotification(
                context = context,
                stopId = updated.stopId,
                stopName = updated.stopName,
                lineNumber = updated.lineNumber,
                destination = updated.destination,
                minutes = effectiveMinutes
            )
        } else if (notifPrefs.isMilestoneEnabled(10) && !updated.notified10Min && effectiveMinutes in 6..10) {
            updated = updated.copy(notified15Min = true, notified10Min = true)
            StopArrivalAlarmReceiver.showMilestoneNotification(
                context = context,
                stopId = updated.stopId,
                stopName = updated.stopName,
                lineNumber = updated.lineNumber,
                destination = updated.destination,
                minutes = effectiveMinutes
            )
        } else if (notifPrefs.isMilestoneEnabled(5) && !updated.notified5Min && effectiveMinutes in 3..5) {
            updated = updated.copy(notified15Min = true, notified10Min = true, notified5Min = true)
            StopArrivalAlarmReceiver.showMilestoneNotification(
                context = context,
                stopId = updated.stopId,
                stopName = updated.stopName,
                lineNumber = updated.lineNumber,
                destination = updated.destination,
                minutes = effectiveMinutes
            )
        } else if (notifPrefs.isMilestoneEnabled(2) && !updated.notified2Min && effectiveMinutes in 1..2) {
            updated = updated.copy(notified15Min = true, notified10Min = true, notified5Min = true, notified2Min = true)
            StopArrivalAlarmReceiver.showMilestoneNotification(
                context = context,
                stopId = updated.stopId,
                stopName = updated.stopName,
                lineNumber = updated.lineNumber,
                destination = updated.destination,
                minutes = effectiveMinutes
            )
        }

        saveToPrefs(updated)
        scheduleSystemAlarms(updated)
        com.dfmob.service.StopArrivalTrackingService.start(context)
    }

    private fun saveToPrefs(alarm: StopArrivalAlarm) {
        val obj = JSONObject().apply {
            put("lineNumber", alarm.lineNumber)
            put("destination", alarm.destination)
            put("stopId", alarm.stopId)
            put("stopName", alarm.stopName)
            put("targetEpochMs", alarm.targetEpochMs)
            put("notified15Min", alarm.notified15Min)
            put("notified10Min", alarm.notified10Min)
            put("notified5Min", alarm.notified5Min)
            put("notified2Min", alarm.notified2Min)
            put("isAutoCancelled", alarm.isAutoCancelled)
        }
        prefs.edit().putString(alarm.key, obj.toString()).apply()
    }

    fun removeAlarm(stopId: Int, lineNumber: String, destination: String) {
        val key = "${stopId}_${lineNumber}_${destination}"
        prefs.edit().remove(key).apply()
        cancelSystemAlarms(stopId, lineNumber, destination)
        if (getAllActiveAlarms().isEmpty()) {
            com.dfmob.service.StopArrivalTrackingService.stop(context)
        }
    }

    fun markMilestoneNotified(stopId: Int, lineNumber: String, destination: String, milestoneMin: Int) {
        val alarm = getAlarm(stopId, lineNumber, destination) ?: return
        val updated = when (milestoneMin) {
            15 -> alarm.copy(notified15Min = true)
            10 -> alarm.copy(notified10Min = true)
            5 -> alarm.copy(notified5Min = true)
            2 -> alarm.copy(notified2Min = true)
            else -> alarm
        }
        saveToPrefs(updated)
    }

    private fun scheduleSystemAlarms(alarm: StopArrivalAlarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val now = System.currentTimeMillis()
        val milestones = listOf(15, 10, 5, 2)
        val notifPrefs = NotificationPreferences(context)

        for (m in milestones) {
            if (!notifPrefs.isMilestoneEnabled(m)) continue
            val triggerTime = alarm.targetEpochMs - m * 60_000L
            val alreadyNotified = when (m) {
                15 -> alarm.notified15Min
                10 -> alarm.notified10Min
                5 -> alarm.notified5Min
                2 -> alarm.notified2Min
                else -> false
            }
            if (!alreadyNotified && triggerTime > now + 1000L) {
                val intent = Intent(context, StopArrivalAlarmReceiver::class.java).apply {
                    action = "com.dfmob.ACTION_STOP_ALARM_MILESTONE"
                    putExtra("stopId", alarm.stopId)
                    putExtra("stopName", alarm.stopName)
                    putExtra("lineNumber", alarm.lineNumber)
                    putExtra("destination", alarm.destination)
                    putExtra("milestoneMinutes", m)
                    putExtra("targetEpochMs", alarm.targetEpochMs)
                }
                val requestCode = (alarm.key.hashCode() * 31) + m
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleAlarmSafely(alarmManager, triggerTime, pendingIntent)
            }
        }

        // Agendamento de auto-cancelamento (1 minuto apos o targetEpochMs)
        val expireTime = alarm.targetEpochMs + 60_000L
        if (expireTime > now) {
            val expireIntent = Intent(context, StopArrivalAlarmReceiver::class.java).apply {
                action = "com.dfmob.ACTION_STOP_ALARM_EXPIRE"
                putExtra("stopId", alarm.stopId)
                putExtra("lineNumber", alarm.lineNumber)
                putExtra("destination", alarm.destination)
            }
            val expireCode = (alarm.key.hashCode() * 31) + 999
            val expirePendingIntent = PendingIntent.getBroadcast(
                context,
                expireCode,
                expireIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            scheduleAlarmSafely(alarmManager, expireTime, expirePendingIntent)
        }
    }

    private fun scheduleAlarmSafely(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                    alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (_: SecurityException) {
            try {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } catch (_: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            }
        } catch (_: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        }
    }

    private fun cancelSystemAlarms(stopId: Int, lineNumber: String, destination: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val key = "${stopId}_${lineNumber}_${destination}"
        val milestones = listOf(15, 10, 5, 2, 999)
        for (m in milestones) {
            val requestCode = (key.hashCode() * 31) + m
            val intent = Intent(context, StopArrivalAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }
}
