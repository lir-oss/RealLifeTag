package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLHeading
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.darwin.NSObject

class IosHeadingTracker : NSObject(), CLLocationManagerDelegateProtocol, HeadingTracker {
    private val locationManager = CLLocationManager()

    private val _headingDegrees = MutableStateFlow(0f)
    override val headingDegrees: StateFlow<Float> = _headingDegrees.asStateFlow()

    init {
        locationManager.delegate = this
    }

    override fun start() {
        if (CLLocationManager.headingAvailable()) {
            locationManager.startUpdatingHeading()
        }
    }

    override fun stop() {
        locationManager.stopUpdatingHeading()
    }

    override fun locationManager(manager: CLLocationManager, didUpdateHeading: CLHeading) {
        val degrees = if (didUpdateHeading.trueHeading >= 0) {
            didUpdateHeading.trueHeading.toFloat()
        } else {
            didUpdateHeading.magneticHeading.toFloat()
        }
        _headingDegrees.value = (degrees + 360f) % 360f
    }
}

@Composable
actual fun rememberHeadingTracker(): HeadingTracker {
    val tracker = remember { IosHeadingTracker() }

    DisposableEffect(tracker) {
        tracker.start()
        onDispose {
            tracker.stop()
        }
    }

    return tracker
}
