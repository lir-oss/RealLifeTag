package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.reallifetag.model.LatLng
import com.example.reallifetag.model.Player
import com.example.reallifetag.model.Role

@Composable
expect fun GameMap(
    modifier: Modifier,
    zoneCenter: LatLng,
    zoneRadiusMeters: Float,
    cameraTarget: LatLng,
    targetZoom: Float,
    targetBearing: Float,
    isMapLockedToHeading: Boolean,
    players: List<Player>,
    myDeviceId: String,
    myRole: Role,
    isHeadStart: Boolean,
    pingAlpha: Float
)
