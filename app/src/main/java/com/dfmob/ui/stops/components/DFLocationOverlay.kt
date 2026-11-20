package com.dfmob.ui.stops.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.location.Location
import android.view.animation.DecelerateInterpolator
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Overlay de localizacao moderno com puck azul, anel branco e animacao de deslizamento suave (slide).
 */
class DFLocationOverlay(
    provider: GpsMyLocationProvider,
    mapView: MapView,
    context: Context
) : MyLocationNewOverlay(provider, mapView) {

    private var locationAnimator: ValueAnimator? = null
    private val animLocation = Location("smooth_anim")

    init {
        val puckBitmap = createLocationPuckBitmap(context)
        setPersonIcon(puckBitmap)
        setDirectionIcon(puckBitmap)
        setPersonAnchor(0.5f, 0.5f)
        setDirectionAnchor(0.5f, 0.5f)
        enableMyLocation()
        setDrawAccuracyEnabled(true)
        mCirclePaint.apply {
            color = android.graphics.Color.parseColor("#221A73E8")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    override fun onLocationChanged(newLocation: Location?, source: IMyLocationProvider?) {
        if (newLocation == null) return

        val current = lastFix
        if (current == null) {
            // Primeiro fix: posiciona imediatamente sem animar de lugar desconhecido
            setLocation(newLocation)
            return
        }

        val dist = FloatArray(1)
        Location.distanceBetween(
            current.latitude, current.longitude,
            newLocation.latitude, newLocation.longitude,
            dist
        )
        val distanceMeters = dist[0]

        // Protecao contra teleporte ou saltos gigantescos (> 300m)
        if (distanceMeters > 300f) {
            locationAnimator?.cancel()
            locationAnimator = null
            setLocation(newLocation)
            return
        }

        // Movimento minimo imperceptivel
        val hasBearingChange = (newLocation.hasBearing() || current.hasBearing()) &&
                Math.abs(newLocation.bearing - current.bearing) >= 1.0f
        if (distanceMeters < 0.2f && !hasBearingChange) {
            return
        }

        locationAnimator?.cancel()

        val startLat = current.latitude
        val startLon = current.longitude
        val targetLat = newLocation.latitude
        val targetLon = newLocation.longitude

        val startBearing = if (current.hasBearing()) current.bearing else newLocation.bearing
        val targetBearing = if (newLocation.hasBearing()) newLocation.bearing else startBearing
        val bearingDiff = ((targetBearing - startBearing + 540f) % 360f) - 180f

        val startAccuracy = current.accuracy
        val targetAccuracy = newLocation.accuracy

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { va ->
                val f = va.animatedFraction
                val curLat = startLat + (targetLat - startLat) * f
                val curLon = startLon + (targetLon - startLon) * f
                val curBearing = (startBearing + bearingDiff * f + 360f) % 360f
                val curAcc = startAccuracy + (targetAccuracy - startAccuracy) * f

                animLocation.set(newLocation)
                animLocation.latitude = curLat
                animLocation.longitude = curLon
                if (newLocation.hasBearing() || current.hasBearing()) {
                    animLocation.bearing = curBearing
                }
                animLocation.accuracy = curAcc
                setLocation(animLocation)
            }
        }
        anim.start()
        locationAnimator = anim
    }

    fun cleanup() {
        locationAnimator?.cancel()
        locationAnimator = null
    }

    override fun onDetach(mapView: MapView?) {
        cleanup()
        super.onDetach(mapView)
    }

    companion object {
        fun createLocationPuckBitmap(context: Context): Bitmap {
            val density = context.resources.displayMetrics.density
            val sizePx = (24 * density).toInt()
            val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val center = sizePx / 2f

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 1. Sombra suave (Drop Shadow)
            paint.color = android.graphics.Color.argb(45, 0, 0, 0)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(center, center + (1f * density), 10.5f * density, paint)

            // 2. Anel branco solido de alto contraste
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, 10f * density, paint)

            // 3. Disco azul vibrante (Google / Transit Blue)
            paint.color = android.graphics.Color.parseColor("#1A73E8")
            canvas.drawCircle(center, center, 7.5f * density, paint)

            // 4. Ponto central branco
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(center, center, 2.5f * density, paint)

            return bitmap
        }
    }
}
