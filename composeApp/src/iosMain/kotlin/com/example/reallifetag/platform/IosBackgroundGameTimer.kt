package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.CoreLocation.CLLocationManager

class IosBackgroundGameTimer : BackgroundGameTimer {
    private val locationManager = CLLocationManager()

    override fun start(statusTextProvider: () -> String, onTick: () -> Unit) {
        try {
            locationManager.allowsBackgroundLocationUpdates = true
            locationManager.pausesLocationUpdatesAutomatically = false
            locationManager.startUpdatingLocation()
        } catch (_: Exception) {}
    }

    override fun stop() {
        try {
            locationManager.allowsBackgroundLocationUpdates = false
            locationManager.stopUpdatingLocation()
        } catch (_: Exception) {}
    }

    override fun updateNotification(text: String) {
        // iOS background status updates
    }
}

@Composable
actual fun rememberBackgroundGameTimer(): BackgroundGameTimer {
    return remember { IosBackgroundGameTimer() }
}
