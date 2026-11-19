package com.dfmob.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dfmob.MainActivity
import com.dfmob.data.database.DatabaseHelper
import com.dfmob.data.model.StopArrivalAlarm
import com.dfmob.data.preferences.NotificationPreferences
import com.dfmob.data.repository.StopAlarmsRepository
import com.dfmob.data.telemetry.MoovitTransitClient
import com.dfmob.receiver.StopArrivalAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StopArrivalTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var trackingJob: Job? = null
    private lateinit var repo: StopAlarmsRepository
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate() {
        super.onCreate()
        repo = StopAlarmsRepository(applicationContext)
        dbHelper = DatabaseHelper(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val stopId = intent.getIntExtra(EXTRA_STOP_ID, -1)
            val lineNumber = intent.getStringExtra(EXTRA_LINE_NUMBER) ?: ""
            val destination = intent.getStringExtra(EXTRA_DESTINATION) ?: ""
            if (stopId > 0 && lineNumber.isNotBlank()) {
                repo.removeAlarm(stopId, lineNumber, destination)
            }
        }
        stopTrackingAndSelf()
        return START_NOT_STICKY
    }

    private fun startForegroundWithOngoingNotification() {
        createTrackingChannel()
        val activeAlarms = repo.getAllActiveAlarms()
        if (activeAlarms.isEmpty()) {
            stopTrackingAndSelf()
            return
        }

        val primaryAlarm = activeAlarms.first()
        val now = System.currentTimeMillis()
        val minutesAway = if (primaryAlarm.targetEpochMs > 0L) {
            maxOf(0, Math.round((primaryAlarm.targetEpochMs - now) / 60000.0).toInt())
        } else 0

        val notification = buildOngoingNotification(primaryAlarm, minutesAway)
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: Exception) {}
    }

    private fun startTrackingLoop() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                val activeAlarms = repo.getAllActiveAlarms()
                if (activeAlarms.isEmpty()) {
                    stopTrackingAndSelf()
                    break
                }

                val now = System.currentTimeMillis()
                var nearestAlarm: StopArrivalAlarm? = null
                var nearestMinutes = Int.MAX_VALUE

                val alarmsByStop = activeAlarms.groupBy { it.stopId }

                val notifPrefs = NotificationPreferences(applicationContext)

                for ((stopId, alarms) in alarmsByStop) {
                    val moovitData = try {
                        MoovitTransitClient.fetchArrivalsForStop(stopId)
                    } catch (_: Exception) {
                        emptyList()
                    }

                    val linesAtStop = try {
                        dbHelper.getLinesPassingAtStop(stopId).associateBy { it.lineId }
                    } catch (_: Exception) {
                        emptyMap()
                    }

                    for (alarm in alarms) {
                        var updatedTargetEpoch = alarm.targetEpochMs

                        if (moovitData.isNotEmpty()) {
                            var bestMatchEpoch: Long? = null
                            var minDiffMs = Long.MAX_VALUE

                            for (lineArr in moovitData) {
                                val lineInfo = linesAtStop[lineArr.lineId]
                                    ?: dbHelper.getLineInfoById(lineArr.lineId)
                                    ?: continue

                                // Filtra estritamente pela linha monitorada pelo alarme
                                if (lineInfo.lineNumber.trim() != alarm.lineNumber.trim()) continue

                                for (arr in lineArr.arrivals) {
                                    val etd = arr.rtEtdUTC ?: arr.staticEtdUTC
                                    if (etd >= now - 120_000L) {
                                        val diff = Math.abs(etd - alarm.targetEpochMs)
                                        if (diff < minDiffMs) {
                                            minDiffMs = diff
                                            bestMatchEpoch = etd
                                        }
                                    }
                                }
                            }

                            if (bestMatchEpoch != null) {
                                updatedTargetEpoch = bestMatchEpoch
                            }
                        }

                        val currentMinutes = if (updatedTargetEpoch > 0L) {
                            maxOf(0, Math.round((updatedTargetEpoch - now) / 60000.0).toInt())
                        } else 0

                        if (currentMinutes < nearestMinutes) {
                            nearestMinutes = currentMinutes
                            nearestAlarm = alarm
                        }

                        if (!alarm.notified15Min && currentMinutes <= 15) {
                            repo.markMilestoneNotified(alarm.stopId, alarm.lineNumber, alarm.destination, 15)
                            if (notifPrefs.isMilestoneEnabled(15)) {
                                StopArrivalAlarmReceiver.showMilestoneNotification(
                                    applicationContext,
                                    alarm.stopId,
                                    alarm.stopName,
                                    alarm.lineNumber,
                                    alarm.destination,
                                    15
                                )
                            }
                        }
                        if (!alarm.notified10Min && currentMinutes <= 10) {
                            repo.markMilestoneNotified(alarm.stopId, alarm.lineNumber, alarm.destination, 10)
                            if (notifPrefs.isMilestoneEnabled(10)) {
                                StopArrivalAlarmReceiver.showMilestoneNotification(
                                    applicationContext,
                                    alarm.stopId,
                                    alarm.stopName,
                                    alarm.lineNumber,
                                    alarm.destination,
                                    10
                                )
                            }
                        }
                        if (!alarm.notified5Min && currentMinutes <= 5) {
                            repo.markMilestoneNotified(alarm.stopId, alarm.lineNumber, alarm.destination, 5)
                            if (notifPrefs.isMilestoneEnabled(5)) {
                                StopArrivalAlarmReceiver.showMilestoneNotification(
                                    applicationContext,
                                    alarm.stopId,
                                    alarm.stopName,
                                    alarm.lineNumber,
                                    alarm.destination,
                                    5
                                )
                            }
                        }
                        if (!alarm.notified2Min && currentMinutes <= 2) {
                            repo.markMilestoneNotified(alarm.stopId, alarm.lineNumber, alarm.destination, 2)
                            if (notifPrefs.isMilestoneEnabled(2)) {
                                StopArrivalAlarmReceiver.showMilestoneNotification(
                                    applicationContext,
                                    alarm.stopId,
                                    alarm.stopName,
                                    alarm.lineNumber,
                                    alarm.destination,
                                    2
                                )
                            }
                        }

                        if (currentMinutes <= 0 && (now - updatedTargetEpoch) > 45_000L) {
                            StopArrivalAlarmReceiver.showMilestoneNotification(
                                applicationContext,
                                alarm.stopId,
                                alarm.stopName,
                                alarm.lineNumber,
                                alarm.destination,
                                0
                            )
                            repo.removeAlarm(alarm.stopId, alarm.lineNumber, alarm.destination)
                        }
                    }
                }

                if (nearestAlarm != null && nearestMinutes != Int.MAX_VALUE) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NOTIFICATION_ID, buildOngoingNotification(nearestAlarm, nearestMinutes))
                }

                delay(20_000L)
            }
        }
    }

    private fun buildOngoingNotification(
        alarm: StopArrivalAlarm,
        minutesAway: Int
    ): android.app.Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "STOPS")
            putExtra(MainActivity.EXTRA_STOP_ID, alarm.stopId)
            putExtra(MainActivity.EXTRA_LINE_NUMBER, alarm.lineNumber)
            putExtra(MainActivity.EXTRA_DESTINATION, alarm.destination)
        }
        val tapPendingIntent = PendingIntent.getActivity(
            this,
            1001,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, StopArrivalTrackingService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_STOP_ID, alarm.stopId)
            putExtra(EXTRA_LINE_NUMBER, alarm.lineNumber)
            putExtra(EXTRA_DESTINATION, alarm.destination)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1002,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = if (minutesAway <= 0) "já na parada" else "~$minutesAway min"
        val title = "DFmob • Monitoramento ativo"
        val content = "Linha ${alarm.lineNumber} a caminho de ${alarm.stopName} ($timeText)"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancelar aviso", cancelPendingIntent)
            .build()
    }

    private fun stopTrackingAndSelf() {
        trackingJob?.cancel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createTrackingChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Acompanhamento de Ônibus em Tempo Real"
            val desc = "Notificação contínua mostrando a aproximação do ônibus à sua parada"
            val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = desc
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "dfmob_stop_arrival_tracking_channel"
        const val NOTIFICATION_ID = 29999

        const val ACTION_START = "com.dfmob.ACTION_START_TRACKING"
        const val ACTION_CANCEL = "com.dfmob.ACTION_CANCEL_TRACKING"

        const val EXTRA_STOP_ID = "EXTRA_STOP_ID"
        const val EXTRA_LINE_NUMBER = "EXTRA_LINE_NUMBER"
        const val EXTRA_DESTINATION = "EXTRA_DESTINATION"

        fun start(context: Context) {
            val intent = Intent(context, StopArrivalTrackingService::class.java).apply {
                action = ACTION_START
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {}
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, StopArrivalTrackingService::class.java))
            } catch (_: Exception) {}
        }
    }
}
