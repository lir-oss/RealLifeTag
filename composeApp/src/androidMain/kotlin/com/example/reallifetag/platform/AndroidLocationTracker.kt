package com.example.reallifetag.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.reallifetag.model.LatLng
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AndroidLocationTracker(
    private val context: Context,
    private val triggerPermissionRequest: () -> Unit
) : LocationTracker {
    private val _currentLocation = MutableStateFlow<LatLng?>(null)
    override val currentLocation: StateFlow<LatLng?> = _currentLocation.asStateFlow()

    private val _hasPermission = MutableStateFlow(checkPermission(context))
    override val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var fusedClient = LocationServices.getFusedLocationProviderClient(context)
    private var callback: LocationCallback? = null

    fun updatePermissionStatus(granted: Boolean) {
        _hasPermission.value = granted
        if (granted) startTracking()
    }

    override fun requestPermission() {
        triggerPermissionRequest()
    }

    override fun startTracking() {
        if (!_hasPermission.value) return
        if (callback != null) return

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        ).setMinUpdateIntervalMillis(500L).build()

        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    _currentLocation.value = LatLng(loc.latitude, loc.longitude)
                }
            }
        }
        callback = cb

        try {
            fusedClient.requestLocationUpdates(locationRequest, cb, Looper.getMainLooper())
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { _currentLocation.value = LatLng(it.latitude, it.longitude) }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    override fun stopTracking() {
        callback?.let {
            try {
                fusedClient.removeLocationUpdates(it)
            } catch (_: Exception) {}
            callback = null
        }
    }

    companion object {
        fun checkPermission(context: Context): Boolean {
            return (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        }
    }
}

@Composable
actual fun rememberLocationTracker(): LocationTracker {
    val context = LocalContext.current
    var requestAction: (() -> Unit)? = null
    val tracker = remember(context) {
        AndroidLocationTracker(context) {
            requestAction?.invoke()
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        tracker.updatePermissionStatus(granted)
    }

    requestAction = {
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    DisposableEffect(Unit) {
        if (tracker.hasPermission.value) {
            tracker.startTracking()
        }
        onDispose {
            tracker.stopTracking()
        }
    }

    return tracker
}
