package com.vitalfi.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2

@Suppress("DEPRECATION")
class CompassReader(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val orientationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var gravityValues = FloatArray(3)
    private var geoValues = FloatArray(3)
    private var hasGravity = false
    private var hasGeo = false

    var bearingDeg: Double = 0.0
        private set

    var manualOffsetDeg: Double = 0.0

    var isActive: Boolean = false
        private set

    fun start(): Boolean {
        val sm = sensorManager ?: return false
        var registered = false

        if (rotationSensor != null) {
            registered = sm.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        if (!registered && accelerometer != null && magnetometer != null) {
            val regAcc = sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            val regMag = sm.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME)
            registered = regAcc && regMag
        }
        if (!registered && orientationSensor != null) {
            registered = sm.registerListener(this, orientationSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        isActive = registered
        return isActive
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        isActive = false
        hasGravity = false
        hasGeo = false
        hasFirstSample = false
    }

    fun adjustBearing(deltaDeg: Double) {
        manualOffsetDeg = ((manualOffsetDeg + deltaDeg) % 360.0 + 360.0) % 360.0
    }

    fun resetManual() {
        manualOffsetDeg = 0.0
    }

    fun effectiveBearing(): Double =
        (bearingDeg + manualOffsetDeg + 360.0) % 360.0

    private var sinSmoothed: Double = 0.0
    private var cosSmoothed: Double = 1.0
    private var hasFirstSample = false

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        var rawAzimuthDeg: Double? = null

        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                val azRad = orientationAngles[0].toDouble()
                rawAzimuthDeg = Math.toDegrees(azRad)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, gravityValues, 0, 3)
                hasGravity = true
                if (hasGeo) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geoValues)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        rawAzimuthDeg = Math.toDegrees(orientationAngles[0].toDouble())
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, geoValues, 0, 3)
                hasGeo = true
                if (hasGravity) {
                    if (SensorManager.getRotationMatrix(rotationMatrix, null, gravityValues, geoValues)) {
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        rawAzimuthDeg = Math.toDegrees(orientationAngles[0].toDouble())
                    }
                }
            }
            Sensor.TYPE_ORIENTATION -> {
                rawAzimuthDeg = event.values[0].toDouble()
            }
        }

        if (rawAzimuthDeg != null) {
            val azRad = Math.toRadians(rawAzimuthDeg)
            val currentSin = kotlin.math.sin(azRad)
            val currentCos = kotlin.math.cos(azRad)

            if (!hasFirstSample) {
                sinSmoothed = currentSin
                cosSmoothed = currentCos
                hasFirstSample = true
            } else {
                val alpha = 0.25
                sinSmoothed = (1.0 - alpha) * sinSmoothed + alpha * currentSin
                cosSmoothed = (1.0 - alpha) * cosSmoothed + alpha * currentCos
            }

            val smoothedRad = atan2(sinSmoothed, cosSmoothed)
            bearingDeg = (Math.toDegrees(smoothedRad) + 360.0) % 360.0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun sourceLabel(): String = when {
        rotationSensor != null -> "Brújula del teléfono"
        accelerometer != null && magnetometer != null -> "Sensor Magnético"
        orientationSensor != null -> "Sensor de Orientación"
        else -> "Manual (← →)"
    }
}
