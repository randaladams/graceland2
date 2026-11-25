package com.randaladams.graceland

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.abs

data class CompassReading(
    val azimuthDegrees: Double,
    val lowAccuracy: Boolean,
    val hasSensor: Boolean
)

/**
 * Streams smoothed true-north azimuth readings from the device's rotation vector sensor
 * (falling back to accelerometer + magnetometer when needed).
 */
class CompassSensorManager(private val context: Context) {

    fun observeAzimuth(
        userLat: Double? = null,
        userLon: Double? = null,
        userAltMeters: Double = 0.0
    ): Flow<CompassReading> = callbackFlow {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        if (rotationVectorSensor == null && (accelerometer == null || magnetometer == null)) {
            trySend(CompassReading(azimuthDegrees = 0.0, lowAccuracy = true, hasSensor = false))
            awaitClose {}
            return@callbackFlow
        }

        var smoothedAzimuth: Double? = null
        var isLowAccuracy = false
        val accelValues = FloatArray(3)
        val magValues = FloatArray(3)
        var hasAccel = false
        var hasMag = false

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val declinationDegrees = if (userLat != null && userLon != null) {
            GeomagneticField(
                userLat.toFloat(),
                userLon.toFloat(),
                userAltMeters.toFloat(),
                System.currentTimeMillis()
            ).declination.toDouble()
        } else {
            0.0
        }

        fun emitAzimuthFromMatrix(matrix: FloatArray) {
            val activeMatrix = remapForDisplayAndPosture(matrix, remappedMatrix)
            SensorManager.getOrientation(activeMatrix, orientation)
            val magneticAzimuth = Math.toDegrees(orientation[0].toDouble())
            val trueAzimuth = GracelandMath.normalizeDegrees(magneticAzimuth + declinationDegrees)
            val nextSmoothed = smoothedAzimuth?.let {
                GracelandMath.smoothAngleDegrees(it, trueAzimuth, alpha = 0.18)
            } ?: trueAzimuth
            smoothedAzimuth = nextSmoothed
            trySend(CompassReading(nextSmoothed, isLowAccuracy, hasSensor = true))
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        emitAzimuthFromMatrix(rotationMatrix)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, accelValues, 0, 3)
                        hasAccel = true
                        if (hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magValues)) {
                            emitAzimuthFromMatrix(rotationMatrix)
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, magValues, 0, 3)
                        hasMag = true
                        if (hasAccel && SensorManager.getRotationMatrix(rotationMatrix, null, accelValues, magValues)) {
                            emitAzimuthFromMatrix(rotationMatrix)
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                if (sensor.type == Sensor.TYPE_MAGNETIC_FIELD || sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    isLowAccuracy = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
                }
            }
        }

        if (rotationVectorSensor != null) {
            sensorManager.registerListener(listener, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            sensorManager.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_GAME)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun remapForDisplayAndPosture(
        source: FloatArray,
        destination: FloatArray
    ): FloatArray {
        val rotation = getDisplayRotation()
        // If device is held steeply upright (pitch close to 90 deg), project forward through rear axis.
        val isUpright = abs(source[8]) < 0.45f
        val (axisX, axisY) = if (isUpright) {
            when (rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Z to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Z
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Z to SensorManager.AXIS_X
                else -> SensorManager.AXIS_X to SensorManager.AXIS_Z
            }
        } else {
            when (rotation) {
                Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                else -> return source
            }
        }
        SensorManager.remapCoordinateSystem(source, axisX, axisY, destination)
        return destination
    }

    @Suppress("DEPRECATION")
    private fun getDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.rotation
        }
    }
}
