package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.example.reallifetag.model.LatLng
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation
import platform.darwin.NSObject

class IosLocationTracker : NSObject(), CLLocationManagerDelegateProtocol, LocationTracker {
    private val locationManager = CLLocationManager()

    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    override val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _hasPermission = MutableStateFlow(isAuthorized(CLLocationManager.authorizationStatus()))
    override val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    init {
        locationManager.delegate = this
        locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
        try {
            // Allows location updates to continue even when phone is locked / screen is off
            locationManager.allowsBackgroundLocationUpdates = true
            locationManager.pausesLocationUpdatesAutomatically = false
            locationManager.showsBackgroundLocationIndicator = true
        } catch (_: Exception) {}
    }

    override fun requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    override fun startTracking() {
        locationManager.startUpdatingLocation()
    }

    override fun stopTracking() {
        locationManager.stopUpdatingLocation()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val lastLocation = didUpdateLocations.lastOrNull() as? CLLocation ?: return
        lastLocation.coordinate.useContents {
            _currentLocation.value = LatLng(latitude, longitude)
        }
    }

    override fun locationManager(manager: CLLocationManager, didChangeAuthorizationStatus: CLAuthorizationStatus) {
        val granted = isAuthorized(didChangeAuthorizationStatus)
        _hasPermission.value = granted
        if (granted) {
            locationManager.startUpdatingLocation()
        }
    }

    companion object {
        fun isAuthorized(status: CLAuthorizationStatus): Boolean {
            return status == kCLAuthorizationStatusAuthorizedWhenInUse ||
                    status == kCLAuthorizationStatusAuthorizedAlways
        }
    }
}

@Composable
actual fun rememberLocationTracker(): LocationTracker {
    val tracker = remember { IosLocationTracker() }

    DisposableEffect(tracker) {
        if (tracker.hasPermission.value) {
            tracker.startTracking()
        }
        onDispose {
            tracker.stopTracking()
        }
    }

    return tracker
}
