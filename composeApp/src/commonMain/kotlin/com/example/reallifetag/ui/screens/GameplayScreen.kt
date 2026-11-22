package com.example.reallifetag.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.reallifetag.model.LatLng
import com.example.reallifetag.model.Player
import com.example.reallifetag.model.Role
import com.example.reallifetag.platform.AudioHaptics
import com.example.reallifetag.platform.GameMap
import com.example.reallifetag.util.calculateZoomToFitZone
import com.example.reallifetag.util.distanceInMeters
import kotlinx.coroutines.delay

// SECTION 11: GAMEPLAY SCREEN WITH MAP BACKGROUND
// Purpose: Live match view rendering map, player markers, zone boundary,
// status bars, audio/vibration alerts, and runner self-tagging controls.
@Composable
fun GameplayScreen(
    myDeviceId: String = "6",
    isHost: Boolean = false,
    isHeadStart: Boolean = false,
    headStartSeconds: Int = 0,
    totalSeconds: Int,
    pingSeconds: Int,
    pingIntervalSeconds: Int = 120,
    zoneCenter: LatLng,
    zoneRadiusMeters: Float,
    players: List<Player>,
    userLocation: LatLng?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    gameOverText: String,
    zoneShrinkCountdown: String = "",
    zoneShrinkBanner: String = "",
    isZoneShrinkEnabled: Boolean = true,
    audioHaptics: AudioHaptics,
    deviceHeading: Float,
    onTagPlayer: (String) -> Unit,
    onResetHome: () -> Unit,
    onSkipHeadStart: () -> Unit = {}
) {
    val myPlayer = players.find { it.id == myDeviceId || it.id == "6" }
    val myRole = myPlayer?.role ?: Role.RUNNER
    val isMyRoleRunner = myRole == Role.RUNNER

    val myDist = myPlayer?.let { distanceInMeters(it.latLng, zoneCenter) } ?: 0f
    val isOutsideZone = myDist > zoneRadiusMeters && isMyRoleRunner && !(myPlayer?.isTagged ?: false)
    val secondsOutside = myPlayer?.secondsOutsideZone ?: 0

    // Aggressive vibration while outside zone
    LaunchedEffect(isOutsideZone) {
        if (isOutsideZone) {
            while (isOutsideZone) {
                audioHaptics.triggerVibration(400L)
                delay(500L)
            }
        }
    }

    // Ping alerts: Hunters hear ting sound 1.5s before ping; Runners vibrate 2s before ping
    LaunchedEffect(pingSeconds, isHeadStart) {
        if (!isHeadStart && pingSeconds == 2) {
            if (isMyRoleRunner) {
                // Runner vibrates 2 seconds before ping
                audioHaptics.triggerVibration(400L)
            } else {
                // Hunter hears ting sound 1.5 seconds before ping (after 500ms delay)
                delay(500L)
                audioHaptics.playTingSound()
            }
        }
    }

    val displaySeconds = (if (isHeadStart) headStartSeconds else totalSeconds).coerceAtLeast(0)
    val gameMin = displaySeconds / 60
    val gameSec = displaySeconds % 60
    val formattedGameTime = "${gameMin}:${if (gameSec < 10) "0$gameSec" else "$gameSec"}"

    val safePingSeconds = pingSeconds.coerceAtLeast(0)
    val pingMin = safePingSeconds / 60
    val pingSec = safePingSeconds % 60
    val formattedPingTime = "${pingMin}:${if (pingSec < 10) "0$pingSec" else "$pingSec"}"

    // Time since last ping calculation
    val timeSinceLastPingSeconds = (pingIntervalSeconds - pingSeconds).coerceAtLeast(0)
    val lastPingMin = timeSinceLastPingSeconds / 60
    val lastPingSec = timeSinceLastPingSeconds % 60
    val formattedLastPingTime = "${lastPingMin}:${if (lastPingSec < 10) "0$lastPingSec" else "$lastPingSec"}"

    // Alpha / opacity for ghost ping markers slowly fading down to 0.25 (never fully disappears)
    val pingAlpha = (pingSeconds.toFloat() / pingIntervalSeconds.toFloat()).coerceIn(0.25f, 1.0f)

    var isMapEnlarged by remember { mutableStateOf(false) }
    var isMapLockedToHeading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
    ) {
        // TOP STATUS BAR (Hidden when map is enlarged)
        if (!isMapEnlarged) {
            if (isOutsideZone) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF510000)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚠️ OUT OF ZONE! Return within ${30 - secondsOutside}s or auto-tagged!",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (zoneShrinkBanner.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF8E24AA)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            zoneShrinkBanner,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isHeadStart) "Pre-Run" else "Time left", color = Color.Gray, fontSize = 11.sp)
                        Text(formattedGameTime, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (isZoneShrinkEnabled && !isHeadStart && zoneShrinkCountdown.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Next shrink", color = Color(0xFFFFB74D), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(zoneShrinkCountdown, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (isHeadStart) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF332200)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.End
                        ) {
                            Text("Head Start", color = Color(0xFFFFB74D), fontSize = 11.sp)
                            Text("Scatter & Hide", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Next ping: $formattedPingTime", color = Color(0xFFFF5252), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Since last: $formattedLastPingTime", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }

            if (!hasPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF332200)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍 Location permission required for live map tracking", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Button(
                            onClick = onRequestPermission,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Allow GPS", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // MAP VIEW CONTAINER
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(if (isMapEnlarged) 8.dp else 16.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            val widthDp = maxWidth.value
            val heightDp = maxHeight.value

            val targetZoom = calculateZoomToFitZone(
                zoneRadiusMeters = zoneRadiusMeters,
                windowWidthDp = widthDp,
                windowHeightDp = heightDp,
            )

            val cameraTarget = zoneCenter
            val targetBearing = if (isMapLockedToHeading) deviceHeading else 0f

            // Native Map View (Google Maps on Android, Apple Maps on iOS)
            GameMap(
                modifier = Modifier.fillMaxSize(),
                zoneCenter = zoneCenter,
                zoneRadiusMeters = zoneRadiusMeters,
                cameraTarget = cameraTarget,
                targetZoom = targetZoom,
                targetBearing = targetBearing,
                isMapLockedToHeading = isMapLockedToHeading,
                players = players,
                myDeviceId = myDeviceId,
                myRole = myRole,
                isHeadStart = isHeadStart,
                pingAlpha = pingAlpha
            )

            // Center Player Pointer with Sight Cone
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Field-of-View Sight Cone
                Canvas(modifier = Modifier.size(64.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val coneRadius = size.width * 0.45f
                    val arcAngle = 50f

                    val coneAngle = if (isMapLockedToHeading) -90f else (deviceHeading - 90f)

                    drawArc(
                        color = Color(0x6629B6F6),
                        startAngle = coneAngle - (arcAngle / 2f),
                        sweepAngle = arcAngle,
                        useCenter = true,
                        size = androidx.compose.ui.geometry.Size(coneRadius * 2f, coneRadius * 2f),
                        topLeft = Offset(center.x - coneRadius, center.y - coneRadius)
                    )
                }

                // Center Player Dot Pointer
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF29B6F6), shape = CircleShape)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(4.dp)) {
                        drawCircle(color = Color.White)
                    }
                }
            }

            // Enlarge / Collapse Map Button
            IconButton(
                onClick = { isMapEnlarged = !isMapEnlarged },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(44.dp)
                    .background(Color(0xDD222222), shape = RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFF555555), RoundedCornerShape(12.dp))
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val strokeWidth = 2.5.dp.toPx()
                    val color = Color.White
                    val w = size.width
                    val h = size.height

                    if (!isMapEnlarged) {
                        // Top-Left arrow
                        drawLine(color, Offset(w * 0.38f, h * 0.38f), Offset(0f, 0f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(0f, 0f), Offset(w * 0.30f, 0f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(0f, 0f), Offset(0f, h * 0.30f), strokeWidth = strokeWidth, cap = StrokeCap.Round)

                        // Bottom-Right arrow
                        drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w, h), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(w, h), Offset(w * 0.70f, h), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(w, h), Offset(w, h * 0.70f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                    } else {
                        // Top-Left arrow
                        drawLine(color, Offset(0f, 0f), Offset(w * 0.38f, h * 0.38f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(w * 0.38f, h * 0.38f), Offset(w * 0.12f, h * 0.38f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(w * 0.38f, h * 0.38f), Offset(w * 0.38f, h * 0.12f), strokeWidth = strokeWidth, cap = StrokeCap.Round)

                        // Bottom-Right arrow
                        drawLine(color, Offset(w, h), Offset(w * 0.62f, h * 0.62f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.88f, h * 0.62f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w * 0.62f, h * 0.88f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                    }
                }
            }
        }

        // BOTTOM INFORMATION / CONTROLS PANEL (Hidden when map is enlarged)
        if (!isMapEnlarged) {
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isHeadStart) {
                        Text("HEAD START PERIOD", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Runners get a pre-run head start to scatter.", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("Rival squad movements are hidden on the map.", color = Color.LightGray, fontSize = 12.sp)
                        if (isHost) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onSkipHeadStart,
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Skip Head Start", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (gameOverText.isNotEmpty()) {
                        Text("GAME OVER", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(gameOverText, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onResetHome,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("🏠 Back to Home & Create New Game", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val isMyRoleCatcher = myRole == Role.HUNTER

                        if (isMyRoleCatcher) {
                            val totalRunners = players.count { it.role == Role.RUNNER }
                            val activeRunners = players.count { it.role == Role.RUNNER && !it.isTagged }

                            Text("🎯 CATCHER DASHBOARD", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Runners Remaining: $activeRunners / $totalRunners", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Hunters see ghost pings every 2 min ($formattedLastPingTime since last ping). Hunt down all runners!", color = Color.LightGray, fontSize = 12.sp)
                        } else {
                            var showTagConfirmDialog by remember { mutableStateOf(false) }

                            Text("🏃 RUNNER SURVIVAL", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))

                            if (myPlayer?.isTagged == true) {
                                Text("You have been tagged!", color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Stay safe and spectate the remaining runners.", color = Color.LightGray, fontSize = 12.sp)
                            } else {
                                Text("Evade catchers & stay inside the zone!", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showTagConfirmDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("✋ I Was Tagged!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }

                            if (showTagConfirmDialog) {
                                AlertDialog(
                                    onDismissRequest = { showTagConfirmDialog = false },
                                    title = { Text("Were you tagged?", fontWeight = FontWeight.Bold, color = Color.White) },
                                    text = { Text("Confirm if a catcher has tagged you in the game.", color = Color.LightGray) },
                                    containerColor = Color(0xFF222222),
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                onTagPlayer(myPlayer?.id ?: "6")
                                                showTagConfirmDialog = false
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                        ) {
                                            Text("Yes", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        Button(
                                            onClick = { showTagConfirmDialog = false },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF444444))
                                        ) {
                                            Text("No", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
