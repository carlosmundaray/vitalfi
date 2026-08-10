package com.vitalfi.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Detecta pasos / caminata real del rescatista (sensor del teléfono).
 * Usado para acercar el punto del radar hacia “Yo” al caminar hacia la víctima.
 */
class WalkStepReader(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    private var stepCount: Int = 0

    @Volatile
    private var walking: Boolean = false

    private var lastStepAtMs: Long = 0L
    private var lastAccelMag: Double = 0.0
    private var accelBurst: Int = 0

    val hasSensor: Boolean
        get() = stepDetector != null || accel != null

    fun start() {
        stepDetector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        if (stepDetector == null) {
            accel?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    /** Pasos desde la última lectura (y pone el contador a 0). */
    fun consumeSteps(): Int {
        val n = stepCount
        stepCount = 0
        return n.coerceAtLeast(0)
    }

    fun isWalking(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (nowMs - lastStepAtMs < 1_400L) return true
        return walking
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                stepCount += 1
                lastStepAtMs = now
                walking = true
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()
                val mag = sqrt(x * x + y * y + z * z)
                val delta = kotlin.math.abs(mag - lastAccelMag)
                lastAccelMag = mag
                // Umbral simple de zancada si no hay step detector
                if (delta > 1.35) {
                    accelBurst++
                    if (accelBurst >= 2) {
                        stepCount += 1
                        accelBurst = 0
                        lastStepAtMs = now
                        walking = true
                    }
                } else {
                    accelBurst = 0
                }
                walking = now - lastStepAtMs < 1_200L
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
