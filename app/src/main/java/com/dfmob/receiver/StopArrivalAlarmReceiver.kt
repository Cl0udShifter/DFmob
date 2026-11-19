package com.dfmob.receiver

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dfmob.MainActivity
import com.dfmob.data.preferences.NotificationPreferences
import com.dfmob.data.repository.StopAlarmsRepository

class StopArrivalAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dfmob:stop_arrival_alarm_wake_lock")
        try {
            wakeLock?.acquire(5000L)

            val repo = StopAlarmsRepository(context)
            val stopId = intent.getIntExtra("stopId", -1)
            val stopName = intent.getStringExtra("stopName") ?: "sua parada"
            val lineNumber = intent.getStringExtra("lineNumber") ?: return
            val destination = intent.getStringExtra("destination") ?: ""

            if (intent.action == "com.dfmob.ACTION_STOP_ALARM_EXPIRE") {
                // Auto cancelamento apos o onibus ter passado do "Já"
                repo.removeAlarm(stopId, lineNumber, destination)
                return
            }

            if (intent.action == "com.dfmob.ACTION_STOP_ALARM_MILESTONE") {
                val milestone = intent.getIntExtra("milestoneMinutes", 0)
                if (!repo.isAlarmActive(stopId, lineNumber, destination)) return

                val notifPrefs = NotificationPreferences(context)
                if (!notifPrefs.isMilestoneEnabled(milestone)) return

                repo.markMilestoneNotified(stopId, lineNumber, destination, milestone)
                showMilestoneNotification(context, stopId, stopName, lineNumber, destination, milestone)
            }
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "dfmob_stop_arrival_alarms"

        fun showMilestoneNotification(
            context: Context,
            stopId: Int,
            stopName: String,
            lineNumber: String,
            destination: String,
            minutes: Int
        ) {
            createChannel(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val notifyIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, "STOPS")
                putExtra(MainActivity.EXTRA_STOP_ID, stopId)
                putExtra(MainActivity.EXTRA_LINE_NUMBER, lineNumber)
                putExtra(MainActivity.EXTRA_DESTINATION, destination)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                (stopId * 31) + lineNumber.hashCode(),
                notifyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val title = if (minutes <= 0) {
                "Linha $lineNumber chegou à parada"
            } else {
                "Linha $lineNumber a caminho ($minutes min)"
            }
            val body = if (minutes <= 0) {
                "O ônibus sentido $destination já está na parada $stopName."
            } else {
                "O ônibus sentido $destination está a $minutes min da parada $stopName."
            }

            val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(defaultSoundUri)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationId = 20000 + (stopId * 31 + lineNumber.hashCode()).rem(10000).let { if (it < 0) -it else it }
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Avisos de Aproximação de Ônibus"
                val desc = "Notificações de proximidade da linha à sua parada (15, 10, 5 e 2 min)"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = desc
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setShowBadge(true)
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.createNotificationChannel(channel)
            }
        }
    }
}
