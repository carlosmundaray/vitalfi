package com.whofi.vitalfi.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.roundToInt

class CompassReader(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    var bearingDeg: Double = 0.0
        private set

    var manualOffsetDeg: Double = 0.0

    var isActive: Boolean = false
        private set

    fun start(): Boolean {
        val sensor = rotationSensor ?: return false
        isActive = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        return isActive
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        isActive = false
    }

    fun adjustBearing(deltaDeg: Double) {
        manualOffsetDeg = ((manualOffsetDeg + deltaDeg) % 360.0 + 360.0) % 360.0
    }

    fun resetManual() {
        manualOffsetDeg = 0.0
    }

    fun effectiveBearing(): Double =
        (bearingDeg + manualOffsetDeg + 360.0) % 360.0

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        // Azimut en grados, 0 = Norte magnético
        val azimuth = Math.toDegrees(orientationAngles[0].toDouble())
        bearingDeg = (azimuth + 360.0) % 360.0
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    fun sourceLabel(): String =
        if (rotationSensor != null) "Brújula del teléfono" else "Manual (← →)"
}
