package com.whofi.vitalfi.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

class BarometerReader(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasSensor: Boolean = pressureSensor != null

    var isActive: Boolean = false
        private set

    private var referencePressure: Float? = null

    var relativeElevationM: Double = 0.0
        private set

    fun start(): Boolean {
        val sensor = pressureSensor ?: return false
        isActive = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        return isActive
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        isActive = false
    }

    fun calibrateBaseline() {
        referencePressure = null
        relativeElevationM = 0.0
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PRESSURE) return
        val currentPressure = event.values[0]

        if (referencePressure == null) {
            referencePressure = currentPressure
        }

        val ref = referencePressure ?: return
        
        val currentAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, currentPressure)
        val refAlt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, ref)
        
        relativeElevationM = (currentAlt - refAlt).toDouble()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
