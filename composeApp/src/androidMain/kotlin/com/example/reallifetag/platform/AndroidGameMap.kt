package com.example.reallifetag.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.reallifetag.model.LatLng
import com.example.reallifetag.model.Player
import com.example.reallifetag.model.Role
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng as GmsLatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

fun createDotBitmapDescriptor(
    context: Context,
    fillColor: Int,
    strokeColor: Int = android.graphics.Color.WHITE,
    withWhiteInner: Boolean = true
): BitmapDescriptor? {
    return try {
        val density = context.resources.displayMetrics.density
        val sizePx = (12 * density).toInt().coerceAtLeast(16)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, paint)

        if (withWhiteInner) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = strokeColor
            }
            canvas.drawCircle(radius, radius, (radius - borderPaint.strokeWidth / 2f).coerceAtLeast(1f), borderPaint)

            val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.WHITE
            }
            canvas.drawCircle(radius, radius, radius * 0.35f, innerDotPaint)
        }

        BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (_: Throwable) {
        null
    }
}

@Composable
actual fun GameMap(
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
) {
    val context = LocalContext.current
    val isMyRoleRunner = myRole == Role.RUNNER

    var blueSelfIcon by remember {
        mutableStateOf(createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#29B6F6"), withWhiteInner = true))
    }
    var greenTeammateIcon by remember {
        mutableStateOf(createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#00E676"), withWhiteInner = true))
    }
    var redEnemyIcon by remember {
        mutableStateOf(createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#FF5252"), withWhiteInner = false))
    }

    LaunchedEffect(Unit) {
        if (blueSelfIcon == null) {
            blueSelfIcon = createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#29B6F6"), withWhiteInner = true)
        }
        if (greenTeammateIcon == null) {
            greenTeammateIcon = createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#00E676"), withWhiteInner = true)
        }
        if (redEnemyIcon == null) {
            redEnemyIcon = createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#FF5252"), withWhiteInner = false)
        }
    }

    val gmsCameraTarget = GmsLatLng(cameraTarget.latitude, cameraTarget.longitude)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(gmsCameraTarget, targetZoom)
    }

    LaunchedEffect(cameraTarget, targetZoom, targetBearing) {
        try {
            cameraPositionState.move(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(cameraPositionState.position)
                        .target(gmsCameraTarget)
                        .zoom(targetZoom)
                        .bearing(targetBearing)
                        .build()
                )
            )
        } catch (_: Exception) {}
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            zoomGesturesEnabled = false,
            myLocationButtonEnabled = false,
            compassEnabled = false,
            scrollGesturesEnabled = false,
            rotationGesturesEnabled = false
        )
    ) {
        // Red Boundary Circle (The Zone)
        Circle(
            center = GmsLatLng(zoneCenter.latitude, zoneCenter.longitude),
            radius = zoneRadiusMeters.toDouble(),
            strokeColor = Color.Red,
            strokeWidth = 6f,
            fillColor = Color(0x33E53935)
        )

        // Player Markers
        players.forEach { p ->
            if (!p.isTagged) {
                val isYou = (p.id == myDeviceId || p.id == "6")
                val isTeammate = (p.role == myRole)

                val showMarker = when {
                    isYou -> true
                    !isHeadStart -> true
                    isTeammate -> true
                    isMyRoleRunner && p.role == Role.HUNTER -> true
                    else -> false
                }

                if (showMarker) {
                    val icon = when {
                        isYou -> blueSelfIcon
                        isTeammate -> greenTeammateIcon
                        else -> redEnemyIcon
                    }
                    val pos = if (isYou || p.role == Role.HUNTER || p.isLiveExposed) p.latLng else (p.lastPingLocation ?: p.latLng)
                    val alpha = if (isYou || p.role == Role.HUNTER || p.isLiveExposed || isTeammate) 1.0f else pingAlpha

                    Marker(
                        state = MarkerState(position = GmsLatLng(pos.latitude, pos.longitude)),
                        icon = icon ?: BitmapDescriptorFactory.defaultMarker(),
                        alpha = alpha,
                        title = if (isYou) "${p.name} (You)" else "${p.name} (${if (isTeammate) "Teammate" else "Enemy"})",
                        snippet = if (isYou) "Your Live Location" else if (isTeammate) "Teammate Location" else "Last Known Location"
                    )
                }
            }
        }
    }
}
