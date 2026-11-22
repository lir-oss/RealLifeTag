package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import com.example.reallifetag.model.LatLng
import kotlinx.coroutines.flow.StateFlow

interface LocationTracker {
    val currentLocation: StateFlow<LatLng?>
    val hasPermission: StateFlow<Boolean>
    fun requestPermission()
    fun startTracking()
    fun stopTracking()
}

@Composable
expect fun rememberLocationTracker(): LocationTracker
