package com.dfmob.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dfmob.MainActivity
import com.dfmob.data.preferences.NotificationPreferences

class TripNavigationTrackingService : Service(), LocationListener {

    private var locationManager: LocationManager? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        NavigationStateHolder.restore(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTrackingAndSelf()
            return START_NOT_STICKY
        }

        NavigationStateHolder.restore(this)
        TripDescentAlarmHelper.createNotificationChannels(this)
        val initialTitle = if (NavigationStateHolder.isRideTransit && NavigationStateHolder.currentRemainingStops.value > 0) {
            val rem = NavigationStateHolder.currentRemainingStops.value
            when (rem) {
                1 -> "A bordo: Desça na próxima parada!"
                else -> "A bordo: $rem paradas até descida"
            }
        } else if (NavigationStateHolder.isRideTransit) {
            "A bordo: Desembarque agora!"
        } else {
            "Navegação guiada ativa"
        }
        val initialBody = if (NavigationStateHolder.targetStopName.isNotBlank()) {
            "Parada de descida: ${NavigationStateHolder.targetStopName}"
        } else {
            "Acompanhando trajeto em tempo real"
        }

        val notification = buildOngoingNotification(initialTitle, initialBody)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_NAVIGATION_TRACKING_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_NAVIGATION_TRACKING_ID, notification)
            }
        } catch (_: Exception) {}

        requestLocationUpdates()
        return START_STICKY
    }

    private fun requestLocationUpdates() {
        val lm = locationManager ?: return
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                // Avalia imediatamente a localizacao atual
                val lastGps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val lastNet = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                val best = lastGps ?: lastNet
                if (best != null) {
                    onLocationChanged(best)
                }

                if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this)
                }
                if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, this)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)) {
                    lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 1000L, 0f, this)
                }
            }
        } catch (_: SecurityException) {}
    }

    override fun onLocationChanged(location: Location) {
        val state = NavigationStateHolder
        if (!state.isNavigating.value) {
            state.restore(this)
            if (!state.isNavigating.value) return
        }

        if (state.isRideTransit) {
            val destLat = state.targetStopLat
            val destLon = state.targetStopLon
            if (destLat != 0.0 && destLon != 0.0) {
                val distToDest = distanceMeters(location.latitude, location.longitude, destLat, destLon)
                state.currentDistToDescentMeters.value = distToDest

                val fullStopList = state.intermediateStops + listOf(destLat to destLon)
                if (fullStopList.isNotEmpty()) {
                    val closestIndex = fullStopList.indices.minByOrNull { i ->
                        distanceMeters(location.latitude, location.longitude, fullStopList[i].first, fullStopList[i].second)
                    } ?: -1

                    if (closestIndex >= 0) {
                        val distToClosest = distanceMeters(
                            location.latitude, location.longitude,
                            fullStopList[closestIndex].first, fullStopList[closestIndex].second
                        )
                        if (distToClosest < 350f && closestIndex > state.lastPassedStopIndex) {
                            state.lastPassedStopIndex = closestIndex
                        }
                    }

                    val totalStopsInRide = fullStopList.size
                    val remaining = if (closestIndex >= totalStopsInRide - 1 || distToDest <= 120f) {
                        0
                    } else if (state.lastPassedStopIndex >= 0) {
                        (totalStopsInRide - 1 - state.lastPassedStopIndex).coerceAtLeast(0)
                    } else {
                        state.totalStopsCount.coerceAtLeast(1)
                    }
                    state.currentRemainingStops.value = remaining
                    state.save(applicationContext)

                    val notifPrefs = NotificationPreferences(applicationContext)
                    val advanceStops = notifPrefs.getSettings().descentAdvanceStops
                    val maxDistMeters = notifPrefs.getDescentDistanceMeters()

                    if ((remaining <= advanceStops || distToDest <= maxDistMeters) && !state.hasAlertedCurrentLeg) {
                        state.hasAlertedCurrentLeg = true
                        state.isDescentAlarmTriggered.value = true
                        TripDescentAlarmHelper.triggerDescentAlarm(
                            context = applicationContext,
                            destinationStopName = state.targetStopName,
                            remainingStops = remaining,
                            distanceMeters = distToDest
                        )
                    }

                    val title = when {
                        remaining == 0 || distToDest <= 80f -> "A bordo: Desembarque agora!"
                        remaining == 1 -> "A bordo: Desça na próxima parada!"
                        else -> "A bordo: $remaining paradas até descida"
                    }
                    val body = "Parada de descida: ${state.targetStopName} (~${distToDest.toInt()}m)"
                    updateOngoingNotification(title, body)
                }
            }
        }
    }

    private fun updateOngoingNotification(title: String, body: String) {
        val notification = buildOngoingNotification(title, body)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        nm?.notify(NOTIFICATION_NAVIGATION_TRACKING_ID, notification)
    }

    private fun buildOngoingNotification(title: String, body: String): Notification {
        val notifyIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, "TRIP_NAV")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_NAVIGATION_TRACKING_ID,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_NAVIGATION_TRACKING_ID)
            .setSmallIcon(com.dfmob.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun stopTrackingAndSelf() {
        locationManager?.removeUpdates(this)
        TripDescentAlarmHelper.cancelDescentAlarm(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        locationManager?.removeUpdates(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_NAVIGATION_TRACKING_ID = "dfmob_trip_navigation_channel"
        const val NOTIFICATION_NAVIGATION_TRACKING_ID = 30001
        const val ACTION_START = "com.dfmob.ACTION_START_TRIP_NAV"
        const val ACTION_STOP = "com.dfmob.ACTION_STOP_TRIP_NAV"

        fun startTracking(context: Context) {
            val intent = Intent(context, TripNavigationTrackingService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopTracking(context: Context) {
            val intent = Intent(context, TripNavigationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, results)
            return results[0]
        }
    }
}
