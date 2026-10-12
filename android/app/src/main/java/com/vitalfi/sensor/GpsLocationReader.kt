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

        /**
         * Formatea coordenadas en grados decimales con indicador cardinal (N/S, E/W):
         * Ejemplo: 10.495889° N, 66.886889° W
         */
        fun formatDecimalCoordinates(lat: Double, lon: Double): String {
            val latH = if (lat >= 0) "N" else "S"
            val lonH = if (lon >= 0) "E" else "W"
            return "%.6f° %s, %.6f° %s".format(abs(lat), latH, abs(lon), lonH)
        }

        /**
         * Formatea latitud decimal individual con 6 dígitos de precisión:
         * Ejemplo: 10.495889° N
         */
        fun formatLatitudeDecimal(lat: Double): String {
            val latH = if (lat >= 0) "N" else "S"
            return "%.6f° %s".format(abs(lat), latH)
        }

        /**
         * Formatea longitud decimal individual con 6 dígitos de precisión:
         * Ejemplo: 66.886889° W
         */
        fun formatLongitudeDecimal(lon: Double): String {
            val lonH = if (lon >= 0) "E" else "W"
            return "%.6f° %s".format(abs(lon), lonH)
        }

        /**
         * Genera el URI 'geo:' nativo de Android para abrir la ubicación en Google Maps u otra app de GPS:
         * Ejemplo: geo:0,0?q=10.495889,-66.886889(Persona+1)
         */
        fun buildGoogleMapsGeoUri(lat: Double, lon: Double, label: String = "Victim"): String {
            val safeLabel = UriUtils.encode(label)
            return "geo:%.6f,%.6f?q=%.6f,%.6f(%s)".format(java.util.Locale.US, lat, lon, lat, lon, safeLabel)
        }

        /**
         * Genera la URL universal de Google Maps para compartir o abrir en navegador:
         */
        fun buildGoogleMapsWebUrl(lat: Double, lon: Double): String {
            return "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f".format(java.util.Locale.US, lat, lon)
        }

        /**
         * Calcula las coordenadas GPS exactas (Latitud, Longitud) de un objetivo/víctima
         * a partir de la posición GPS del rescatista, la distancia en metros y el rumbo en grados.
         */
        fun calculateTargetCoordinates(
            rescuerLat: Double?,
            rescuerLon: Double?,
            distanceM: Double,
            bearingDeg: Double,
        ): Pair<Double, Double>? {
            if (rescuerLat == null || rescuerLon == null || (rescuerLat == 0.0 && rescuerLon == 0.0)) {
                return null
            }
            val earthRadiusM = 6378137.0
            val dByR = distanceM / earthRadiusM
            val bearingRad = Math.toRadians(bearingDeg)
            val lat1Rad = Math.toRadians(rescuerLat)
            val lon1Rad = Math.toRadians(rescuerLon)

            val lat2Rad = Math.asin(
                Math.sin(lat1Rad) * Math.cos(dByR) +
                Math.cos(lat1Rad) * Math.sin(dByR) * Math.cos(bearingRad)
            )
            val lon2Rad = lon1Rad + Math.atan2(
                Math.sin(bearingRad) * Math.sin(dByR) * Math.cos(lat1Rad),
                Math.cos(dByR) - Math.sin(lat1Rad) * Math.sin(lat2Rad)
            )

            val targetLat = Math.toDegrees(lat2Rad)
            val targetLon = ((Math.toDegrees(lon2Rad) + 540.0) % 360.0) - 180.0
            return Pair(targetLat, targetLon)
        }

        /**
         * Calcula la distancia geodésica real en metros entre dos puntos geográficos (Fórmula de Haversine).
         */
        fun distanceBetweenMeters(
            lat1: Double?,
            lon1: Double?,
            lat2: Double?,
            lon2: Double?,
        ): Double {
            if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return Double.MAX_VALUE
            if (lat1 == 0.0 && lon1 == 0.0) return Double.MAX_VALUE
            val earthRadiusM = 6378137.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                    Math.sin(dLon / 2) * Math.sin(dLon / 2)
            val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
            return earthRadiusM * c
        }

        /**
         * Calcula el rumbo azimutal real en grados [0°, 360°) desde el punto 1 hacia el punto 2.
         */
        fun bearingBetweenDegrees(
            lat1: Double?,
            lon1: Double?,
            lat2: Double?,
            lon2: Double?,
        ): Double {
            if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) return 0.0
            val lat1Rad = Math.toRadians(lat1)
            val lat2Rad = Math.toRadians(lat2)
            val dLon = Math.toRadians(lon2 - lon1)

            val y = Math.sin(dLon) * Math.cos(lat2Rad)
            val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon)
            val bearingRad = Math.atan2(y, x)
            return (Math.toDegrees(bearingRad) + 360.0) % 360.0
        }

        /**
         * Genera un Código de Firma Geo-Espacial Único e Inmutable para la persona detectada.
         * Permite que el sistema reconozca siempre el mismo punto físico aunque el rescatista camine.
         * Ejemplo: "GEO-PIN#1 [10.49589°N, 66.88689°W]"
         */
        fun generateGeoSignature(victimId: Int, lat: Double?, lon: Double?): String {
            return if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
                val latDir = if (lat >= 0) "N" else "S"
                val lonDir = if (lon >= 0) "E" else "W"
                "GEO-PIN#$victimId [%.5f°%s, %.5f°%s]".format(java.util.Locale.US, kotlin.math.abs(lat), latDir, kotlin.math.abs(lon), lonDir)
            } else {
                "GEO-PIN#$victimId [LOCAL-CSI]"
            }
        }
    }
}

private object UriUtils {
    fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
