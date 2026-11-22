package com.example.reallifetag.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidHeadingTracker(private val context: Context) : HeadingTracker {
    private val _headingDegrees = MutableStateFlow(0f)
    override val headingDegrees: StateFlow<Float> = _headingDegrees.asStateFlow()

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                _headingDegrees.value = (azimuthDeg + 360f) % 360f
            } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                _headingDegrees.value = (event.values[0] + 360f) % 360f
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun start() {
        if (rotationSensor != null && sensorManager != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun stop() {
        sensorManager?.unregisterListener(listener)
    }
}

@Composable
actual fun rememberHeadingTracker(): HeadingTracker {
    val context = LocalContext.current
    val tracker = remember(context) { AndroidHeadingTracker(context) }

    DisposableEffect(tracker) {
        tracker.start()
        onDispose {
            tracker.stop()
        }
    }

    return tracker
}
