package com.dfmob.data.repository

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.dfmob.data.model.StopArrivalAlarm
import com.dfmob.data.preferences.NotificationPreferences
import com.dfmob.receiver.StopArrivalAlarmReceiver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class StopAlarmsRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("dfmob_stop_alarms_prefs", Context.MODE_PRIVATE)

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        notifyAlarmsChanged()
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    companion object {
        private val _alarmsRevisionFlow = MutableStateFlow(0L)
        val alarmsRevisionFlow: StateFlow<Long> = _alarmsRevisionFlow.asStateFlow()

        fun notifyAlarmsChanged() {
            _alarmsRevisionFlow.value = System.currentTimeMillis()
        }
    }

    fun isAlarmActive(stopId: Int, lineNumber: String, destination: String = ""): Boolean {
        val cleanLine = lineNumber.trim()
        val allEntries = prefs.all
        for ((_, value) in allEntries) {
            val json = value as? String ?: continue
            try {
                val obj = JSONObject(json)
                if (obj.optBoolean("isAutoCancelled", false)) continue
                val sId = obj.optInt("stopId", -1)
                val lNum = obj.optString("lineNumber", "").trim()
                if (sId == stopId && lNum.equals(cleanLine, ignoreCase = true)) {
                    val dest = obj.optString("destination", "").trim()
                    if (destination.isBlank() || dest.isBlank() ||
                        dest.equals(destination.trim(), ignoreCase = true) ||
                        dest.contains(destination.trim(), ignoreCase = true) ||
                        destination.trim().contains(dest, ignoreCase = true)) {
                        return true
                    }
                    // Em uma parada física específica há apenas um trajeto desta linha passando
                    return true
                }
            } catch (_: Exception) {}
        }
        return false
    }

    fun getAlarm(stopId: Int, lineNumber: String, destination: String = ""): StopArrivalAlarm? {
        val cleanLine = lineNumber.trim()
        val allEntries = prefs.all
        var fallbackMatch: StopArrivalAlarm? = null
        for ((_, value) in allEntries) {
            val json = value as? String ?: continue
            try {
                val obj = JSONObject(json)
                if (obj.optBoolean("isAutoCancelled", false)) continue
                val sId = obj.optInt("stopId", -1)
                val lNum = obj.optString("lineNumber", "").trim()
                if (sId == stopId && lNum.equals(cleanLine, ignoreCase = true)) {
                    val alarmObj = StopArrivalAlarm(
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
                    val dest = obj.optString("destination", "").trim()
                    if (destination.isBlank() || dest.isBlank() ||
                        dest.equals(destination.trim(), ignoreCase = true) ||
                        dest.contains(destination.trim(), ignoreCase = true) ||
                        destination.trim().contains(dest, ignoreCase = true)) {
                        return alarmObj
                    }
                    if (fallbackMatch == null) {
                        fallbackMatch = alarmObj
                    }
                }
            } catch (_: Exception) {}
        }
        return fallbackMatch
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

        // Marca marcos ja decorridos no passado como notificados de forma silenciosa, sem disparar notificacao imediata
        var updated = alarm
        val milestones = listOf(15, 10, 5, 2)
        for (m in milestones) {
            val triggerTime = alarm.targetEpochMs - m * 60_000L
            if (triggerTime <= now + 3000L) {
                updated = when (m) {
                    15 -> updated.copy(notified15Min = true)
                    10 -> updated.copy(notified10Min = true)
                    5 -> updated.copy(notified5Min = true)
                    2 -> updated.copy(notified2Min = true)
                    else -> updated
                }
            }
        }

        saveToPrefs(updated)
        scheduleSystemAlarms(updated)
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
        notifyAlarmsChanged()
    }

    fun removeAlarm(stopId: Int, lineNumber: String, destination: String = "") {
        val cleanLine = lineNumber.trim()
        val allEntries = prefs.all
        val editor = prefs.edit()
        for ((key, value) in allEntries) {
            val json = value as? String ?: continue
            try {
                val obj = JSONObject(json)
                val sId = obj.optInt("stopId", -1)
                val lNum = obj.optString("lineNumber", "").trim()
                if (sId == stopId && lNum.equals(cleanLine, ignoreCase = true)) {
                    val dest = obj.optString("destination", "").trim()
                    if (destination.isBlank() || dest.isBlank() ||
                        dest.equals(destination.trim(), ignoreCase = true) ||
                        dest.contains(destination.trim(), ignoreCase = true) ||
                        destination.trim().contains(dest, ignoreCase = true)) {
                        editor.remove(key)
                        cancelSystemAlarms(sId, lNum, dest)
                    }
                }
            } catch (_: Exception) {}
        }
        editor.apply()
        notifyAlarmsChanged()

        // Cancela qualquer notificacao de alerta ativa na gaveta para esta parada/linha
        val notificationId = 20000 + (stopId * 31 + lineNumber.hashCode()).rem(10000).let { if (it < 0) -it else it }
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
            NotificationManagerCompat.from(context).cancel(29999)
        } catch (_: Exception) {}
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
                    addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
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

                val showIntent = Intent(context, com.dfmob.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(com.dfmob.MainActivity.EXTRA_NAVIGATE_TO, "STOPS")
                    putExtra(com.dfmob.MainActivity.EXTRA_STOP_ID, alarm.stopId)
                    putExtra(com.dfmob.MainActivity.EXTRA_LINE_NUMBER, alarm.lineNumber)
                    putExtra(com.dfmob.MainActivity.EXTRA_DESTINATION, alarm.destination)
                }
                val showPendingIntent = PendingIntent.getActivity(
                    context,
                    (alarm.key.hashCode() * 31) + m + 50000,
                    showIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                scheduleAlarmSafely(alarmManager, triggerTime, pendingIntent, showPendingIntent)
            }
        }

        // Agendamento de auto-cancelamento (15 minutos apos o targetEpochMs)
        val expireTime = alarm.targetEpochMs + 900_000L
        if (expireTime > now) {
            val expireIntent = Intent(context, StopArrivalAlarmReceiver::class.java).apply {
                action = "com.dfmob.ACTION_STOP_ALARM_EXPIRE"
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
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
            scheduleAlarmSafely(alarmManager, expireTime, expirePendingIntent, null)
        }
    }

    private fun scheduleAlarmSafely(
        alarmManager: AlarmManager,
        triggerTime: Long,
        pendingIntent: PendingIntent,
        showIntent: PendingIntent? = null
    ) {
        try {
            if (showIntent != null) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (_: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (_: Exception) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
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
