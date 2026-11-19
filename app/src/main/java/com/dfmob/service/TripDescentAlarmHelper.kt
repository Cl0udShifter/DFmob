package com.dfmob.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dfmob.MainActivity
import kotlin.math.roundToInt

object TripDescentAlarmHelper {
    const val CHANNEL_DESCENT_ALARM_ID = "dfmob_trip_descent_channel"
    const val NOTIFICATION_DESCENT_ALARM_ID = 30002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

            // Canal do Foreground Service de Navegacao (baixa importancia para nao emitir som continuo)
            val trackingChannel = NotificationChannel(
                TripNavigationTrackingService.CHANNEL_NAVIGATION_TRACKING_ID,
                "Navegação Ativa em Tempo Real",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificação contínua durante a navegação guiada"
                setShowBadge(false)
            }
            nm.createNotificationChannel(trackingChannel)

            // Canal do Alarme de Desembarque (alta importancia com som e vibracao)
            val descentChannel = NotificationChannel(
                CHANNEL_DESCENT_ALARM_ID,
                "Alarme de Desembarque",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alertas sonoros e de vibração quando a parada de descida estiver próxima"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 250, 600, 250, 600)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            nm.createNotificationChannel(descentChannel)
        }
    }

    fun triggerDescentAlarm(
        context: Context,
        destinationStopName: String,
        remainingStops: Int,
        distanceMeters: Float
    ) {
        createNotificationChannels(context)

        // Vibra o aparelho
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 600, 250, 600, 250, 600), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 600, 250, 600, 250, 600), -1)
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val notifyIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_DESCENT_ALARM_ID,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Prepare-se para desembarcar!"
        val stopsText = if (remainingStops <= 1) "na próxima parada" else "em $remainingStops paradas"
        val distText = if (distanceMeters > 0) " (${distanceMeters.roundToInt()}m)" else ""
        val body = "Sua descida é $stopsText: $destinationStopName$distText."

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notification = NotificationCompat.Builder(context, CHANNEL_DESCENT_ALARM_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(defaultSoundUri)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 600, 250, 600, 250, 600))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_DESCENT_ALARM_ID, notification)
    }

    fun cancelDescentAlarm(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_DESCENT_ALARM_ID)
        } catch (_: Exception) {}
    }
}
