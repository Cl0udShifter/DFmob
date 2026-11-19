package com.dfmob.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dfmob.MainActivity
import com.dfmob.data.repository.AlertsRepository
import java.util.concurrent.TimeUnit

class SemobAlertsWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "semob_alerts_channel"
        const val NOTIFICATION_ID = 2001
        const val WORK_NAME = "semob_alerts_periodic_worker"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SemobAlertsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val repository = AlertsRepository(appContext)
            val alerts = repository.getAlerts()
            if (alerts.isEmpty()) return Result.success()

            val prefs = repository.preferences
            val lastNotifiedId = prefs.getLastNotifiedAlertId()

            val latestAlert = alerts.firstOrNull() ?: return Result.success()

            if (lastNotifiedId == null) {
                // Primeira execucao: salva o id atual sem disparar alerta retroativo em massa
                prefs.setLastNotifiedAlertId(latestAlert.id)
                return Result.success()
            }

            // Dispara notificacao caso haja um alerta mais recente ainda nao notificado
            if (latestAlert.id != lastNotifiedId) {
                showNotification(latestAlert.id, latestAlert.title, latestAlert.description)
                prefs.setLastNotifiedAlertId(latestAlert.id)
            }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun showNotification(alertId: String, title: String, description: String) {
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "ALERTS")
            putExtra("EXTRA_ALERT_ID", alertId)
        }

        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("SEMOB: $title")
            .setContentText(description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(description))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alertas Operacionais SEMOB"
            val desc = "Notificações de desvios, manutenções e alterações no transporte do DF"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = desc
            }
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }
}
