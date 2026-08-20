package com.vitalfi.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import kotlin.math.abs

/**
 * Lector de ubicación GPS nativo y utilidades para formato de coordenadas GMS (Grados, Minutos, Segundos).
 */
class GpsLocationReader(private val context: Context) {

    data class GpsData(
        val latitude: Double,
        val longitude: Double,
        val altitudeM: Double = 0.0,
        val accuracyM: Float = 0f,
        val latitudeGms: String = "",
        val longitudeGms: String = "",
        val fullGms: String = "",
        val timestampMs: Long = System.currentTimeMillis(),
        val isGpsActive: Boolean = false,
    )

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private var lastLocation: Location? = null
    private var listener: LocationListener? = null

    var onLocationChanged: ((GpsData) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (locationManager == null) return

        val locListener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                lastLocation = loc
                val data = createGpsData(loc, isActive = true)
                onLocationChanged?.invoke(data)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        listener = locListener

        try {
            // Intentar con GPS
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1500L,
                    1f,
                    locListener,
                    Looper.getMainLooper(),
                )
            }
            // Fallback a Network / Pasivo para fijación rápida en interiores
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    2f,
                    locListener,
                    Looper.getMainLooper(),
                )
            }

            // Última ubicación conocida de inmediato
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = lastGps ?: lastNet
            if (bestLast != null) {
                lastLocation = bestLast
                onLocationChanged?.invoke(createGpsData(bestLast, isActive = true))
            }
        } catch (e: SecurityException) {
            Log.w("GpsLocationReader", "Permiso de ubicación denegado para GPS: ${e.message}")
        } catch (e: Throwable) {
            Log.w("GpsLocationReader", "Error iniciando GPS: ${e.message}")
        }
    }

    fun stop() {
        listener?.let {
            try {
                locationManager?.removeUpdates(it)
            } catch (_: Throwable) {}
        }
        listener = null
    }

    fun getCurrentGpsData(): GpsData {
        val loc = lastLocation
        return if (loc != null) {
            createGpsData(loc, isActive = true)
        } else {
            // Coordenada base de referencia táctica si el GPS aún no tiene fix
            val defaultLat = 0.0
            val defaultLon = 0.0
            GpsData(
                latitude = defaultLat,
                longitude = defaultLon,
                latitudeGms = formatLatitudeGms(defaultLat),
                longitudeGms = formatLongitudeGms(defaultLon),
                fullGms = "${formatLatitudeGms(defaultLat)} ${formatLongitudeGms(defaultLon)}",
                isGpsActive = false,
            )
        }
    }

    private fun createGpsData(loc: Location, isActive: Boolean): GpsData {
        val latGms = formatLatitudeGms(loc.latitude)
        val lonGms = formatLongitudeGms(loc.longitude)
        val full = "$latGms $lonGms"
        return GpsData(
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitudeM = if (loc.hasAltitude()) loc.altitude else 0.0,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy else 0f,
            latitudeGms = latGms,
            longitudeGms = lonGms,
            fullGms = full,
            timestampMs = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            isGpsActive = isActive,
        )
    }

    companion object {
        /**
         * Convierte un valor decimal de latitud a formato GMS (Grados, Minutos, Segundos):
         * Ejemplo: 10.495889 -> 10°29'45.2"N
         */
        fun formatLatitudeGms(lat: Double): String {
            val hemisphere = if (lat >= 0) "N" else "S"
            val absLat = abs(lat)
            val degrees = absLat.toInt()
            val minutesDecimal = (absLat - degrees) * 60.0
            val minutes = minutesDecimal.toInt()
            val seconds = (minutesDecimal - minutes) * 60.0
            return "%d°%02d'%04.1f\"%s".format(degrees, minutes, seconds, hemisphere)
        }

        /**
         * Convierte un valor decimal de longitud a formato GMS (Grados, Minutos, Segundos):
         * Ejemplo: -66.886889 -> 66°53'12.8"W
         */
        fun formatLongitudeGms(lon: Double): String {
            val hemisphere = if (lon >= 0) "E" else "W"
            val absLon = abs(lon)
            val degrees = absLon.toInt()
            val minutesDecimal = (absLon - degrees) * 60.0
            val minutes = minutesDecimal.toInt()
            val seconds = (minutesDecimal - minutes) * 60.0
            return "%d°%02d'%04.1f\"%s".format(degrees, minutes, seconds, hemisphere)
        }

        /**
         * Formatea latitud y longitud combinadas en GMS:
         * Ejemplo: 10°29'45.2"N 66°53'12.8"W
         */
        fun formatCoordinatesGms(lat: Double, lon: Double): String {
            return "${formatLatitudeGms(lat)} ${formatLongitudeGms(lon)}"
        }
    }
}
