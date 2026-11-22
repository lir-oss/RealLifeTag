// ============================================================================
// REAL LIFE TAG - MAIN APPLICATION FILE
// ----------------------------------------------------------------------------
// Purpose: Core Activity, Jetpack Compose UI screens, Google Maps integration,
// real-time GPS tracking, game rules setup, game engine loop, multiplayer models.
// ============================================================================

package com.example.reallifetag

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.reallifetag.ui.theme.RealLifeTagTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// ============================================================================
// SECTION 1: DATA MODELS & ENUMS
// ----------------------------------------------------------------------------
// Purpose: Core game entities, player state data, and navigation stages.
// ============================================================================

// Player roles: HUNTER (Catcher) or RUNNER
enum class Role { HUNTER, RUNNER }

// Immutable data model holding player state, coordinates, tag status, and last ping location
data class Player(
    val id: String,                         // Unique ID for player
    val name: String,                       // Nickname of player
    val role: Role,                         // Role (HUNTER or RUNNER)
    val latLng: LatLng,                     // Live LatLng coordinates
    val isTagged: Boolean = false,          // Tag status (true if caught)
    val lastPingLocation: LatLng? = null,   // Location snapshot from most recent 2-min ping
    val secondsOutsideZone: Int = 0,        // Counter for seconds spent outside zone
    val isLiveExposed: Boolean = false,     // True if runner is exposed live for staying out >30s
)

// Main navigation lifecycle stages of the app
enum class GameStage {
    MAIN_MENU,   // Landing screen: Host vs Participate choice
    SETUP,       // Host game creation & rule setup
    JOIN_GAME,   // Participate / Join game with room code
    LOBBY,       // Lobby Waiting Room (share Lobby ID, see real joined players)
    HEAD_START,  // Pre-run head start period
    ACTIVE_GAME, // Live match gameplay
    GAME_OVER,   // Match end results screen
}



// Network-serializable player state for multiplayer cloud / peer sync
data class PlayerNetworkState(
    val deviceId: String,
    val nickname: String,
    val role: Role,
    val latitude: Double,
    val longitude: Double,
    val isTagged: Boolean = false,
    val lastPingLat: Double? = null,
    val lastPingLng: Double? = null,
    val secondsOutsideZone: Int = 0,
    val isLiveExposed: Boolean = false,
    val isHost: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
) {
    fun toPlayer(): Player = Player(
        id = deviceId,
        name = nickname,
        role = role,
        latLng = LatLng(latitude, longitude),
        isTagged = isTagged,
        lastPingLocation = if (lastPingLat != null && lastPingLng != null) LatLng(lastPingLat, lastPingLng) else null,
        secondsOutsideZone = secondsOutsideZone,
        isLiveExposed = isLiveExposed
    )
}

// Room session payload holding global match state for multiplayer synchronization
data class RoomSessionPayload(
    val roomCode: String,
    val hostDeviceId: String,
    val stageName: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val zoneRadiusMeters: Float,
    val matchDurationMinutes: Int,
    val headStartMinutes: Int,
    val pingIntervalMinutes: Int,
    val remainingGameSeconds: Int,
    val remainingHeadStartSeconds: Int,
    val currentPingTimerSeconds: Int,
    val winnerMessage: String = "",
    val playersMap: Map<String, PlayerNetworkState> = emptyMap()
)

// ============================================================================
// SECTION 2: AUDIO & VIBRATION HOOKS (Ping Ting & Vibration Alerts)
// ----------------------------------------------------------------------------
// Purpose: Plays single high-pitched "ting" on location pings and triggers vibration.
// ============================================================================

@Composable
fun rememberTingSound(): () -> Unit {
    // Acquire system ToneGenerator for playing max volume notification ting sound for both sides
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100) // Max 100% volume
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                toneGenerator?.release()
            } catch (_: Exception) {
            }
        }
    }

    return {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200) // Loud high-pitched single ting
        } catch (_: Exception) {
        }
    }
}

@Composable
fun rememberVibration(): () -> Unit {
    val context = LocalContext.current
    val vibrator = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    return {
        try {
            vibrator?.let { v ->
                if (v.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(400L)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }
}

// ============================================================================
// SECTION 3: APP LOGO COMPOSABLE
// ----------------------------------------------------------------------------
// Purpose: Displays rounded squircle badge with dynamic red running figure.
// ============================================================================

@Composable
fun RunningManLogo(sizeDp: Int = 84) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp * 0.28f).dp))
            .background(Color(0xFFF5F5F7)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.game_logo),
            contentDescription = "Real Life Tag Logo",
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ============================================================================
// SECTION 4: MATHEMATICAL & GEOGRAPHIC HELPER FUNCTIONS
// ----------------------------------------------------------------------------
// Purpose: Offset LatLng by meters, calculate real distance, calculate map zoom,
// and generate custom circle BitmapDescriptors for Teammates (Green) & Enemies (Red).
// ============================================================================

// Helper function creating a custom circle dot BitmapDescriptor for Google Map markers (12dp size)
fun createDotBitmapDescriptor(
    context: Context,
    fillColor: Int,
    strokeColor: Int = android.graphics.Color.WHITE,
    withWhiteInner: Boolean = true
): BitmapDescriptor? {
    return try {
        val density = context.resources.displayMetrics.density
        val sizePx = (12 * density).toInt().coerceAtLeast(16) // 12dp size matching center pointer
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = fillColor
        }
        val radius = sizePx / 2f
        canvas.drawCircle(radius, radius, radius, paint) // Main colored fill circle

        if (withWhiteInner) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                color = strokeColor
            }
            canvas.drawCircle(radius, radius, (radius - borderPaint.strokeWidth / 2f).coerceAtLeast(1f), borderPaint) // Outer white border

            val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = android.graphics.Color.WHITE
            }
            canvas.drawCircle(radius, radius, radius * 0.35f, innerDotPaint) // Inner white center dot
        }

        BitmapDescriptorFactory.fromBitmap(bitmap)
    } catch (_: Throwable) {
        null
    }
}

// Extension function on LatLng to generate a new coordinate shifted by meters North & East
fun LatLng.offsetByMeters(northMeters: Double, eastMeters: Double): LatLng {
    val latDiff = northMeters / 111111.0 // 1 degree latitude ≈ 111,111 meters
    val lngDiff = eastMeters / (111111.0 * kotlin.math.cos(Math.toRadians(latitude))) // 1 degree longitude scaled by cos(lat)
    return LatLng(latitude + latDiff, longitude + lngDiff) // Return new shifted LatLng
}

// Calculates accurate real-world distance in meters between two LatLng coordinates
fun distanceInMeters(loc1: LatLng, loc2: LatLng): Float {
    val results = FloatArray(1) // Array to store Android Location distance result
    Location.distanceBetween(
        loc1.latitude, loc1.longitude, // Point A lat/lng
        loc2.latitude, loc2.longitude, // Point B lat/lng
        results,                       // Output distance result in meters
    )
    return results[0] // Return distance in meters
}

fun playSpecialShrinkSound() {
    try {
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tg.startTone(ToneGenerator.TONE_PROP_BEEP, 300)
        android.os.Handler(Looper.getMainLooper()).postDelayed({
            try {
                tg.startTone(ToneGenerator.TONE_PROP_ACK, 400)
                tg.release()
            } catch (_: Exception) {}
        }, 350)
    } catch (_: Exception) {}
}

// ============================================================================
// >>> WHERE TO CHANGE MAP ZOOM LEVEL <<<
// ----------------------------------------------------------------------------
// To make the map MORE ZOOMED IN: Increase `zoneCoverageRatio` (e.g., from 0.82f to 0.95f)
// To make the map MORE ZOOMED OUT: Decrease `zoneCoverageRatio` (e.g., from 0.82f to 0.60f)
// Or adjust the return value offset: (zoom + 0.5f) to zoom in, (zoom - 0.5f) to zoom out.
// ============================================================================
fun calculateZoomToFitZone(
    zoneRadiusMeters: Float,    // Current radius of zone in meters
    windowWidthDp: Float,       // Map window width in dp
    windowHeightDp: Float,      // Map window height in dp
): Float {
    if (windowWidthDp <= 0f || windowHeightDp <= 0f || windowWidthDp.isNaN() || windowHeightDp.isNaN()) {
        return 14.5f // Safe default zoom level during initial measurement
    }
    val limitingDimensionDp = minOf(windowWidthDp, windowHeightDp).coerceAtLeast(10f)
    val maxRadiusNeeded = (zoneRadiusMeters * 1.25f).coerceAtLeast(30f) // 25% margin around zone

    // ---> CHANGE THIS RATIO TO ADJUST MAP ZOOM COVERAGE <---
    val zoneCoverageRatio = 4.5f // 4.5f visible coverage ratio

    val targetDiameterMeters = maxRadiusNeeded * 2f / zoneCoverageRatio
    val factor = (25046f * limitingDimensionDp) / targetDiameterMeters
    if (factor <= 0f || factor.isNaN()) return 14.5f

    val zoom = (kotlin.math.ln(factor) / kotlin.math.ln(2f)).toFloat()
    if (zoom.isNaN() || zoom.isInfinite()) return 14.5f

    return zoom.coerceIn(10f, 18.5f)
}

// Generates initial randomized players list for a match with host's ratio
fun generateRandomizedPlayersWithHost(
    hunterCount: Int,
    runnerCount: Int,
    gameCenter: LatLng,
    userNick: String,
    myDeviceId: String = "6"
): List<Player> {
    val totalPlayers = (hunterCount + runnerCount).coerceAtLeast(2) // Compute total player capacity
    val rolesList = mutableListOf<Role>()
    repeat(hunterCount.coerceAtLeast(1)) { rolesList.add(Role.HUNTER) } // Add hunter roles to pool
    repeat(runnerCount.coerceAtLeast(1)) { rolesList.add(Role.RUNNER) } // Add runner roles to pool
    rolesList.shuffle() // Randomly assign roles according to the host's set ratio

    val myNickname = userNick.trim().ifBlank { "Player" } // Default fallback if nickname is empty

    val list = mutableListOf<Player>()
    var hunterIdx = 1 // Counter for mock hunters
    var runnerIdx = 1 // Counter for mock runners

    for (i in 0 until totalPlayers) {
        val role = if (i < rolesList.size) rolesList[i] else Role.RUNNER // Assign shuffled role
        val isYou = (i == totalPlayers - 1)       // Designate last index as "You"
        val angle = (i * 360.0 / totalPlayers) * Math.PI / 180.0 // Angle distribution around center
        val dist = if (isYou) 0.0 else (60.0 + i * 20.0)         // Radial distance offset
        val offsetN = dist * kotlin.math.cos(angle)              // North offset in meters
        val offsetE = dist * kotlin.math.sin(angle)              // East offset in meters

        val name = if (isYou) {
            myNickname // Set user's chosen nickname
        } else if (role == Role.HUNTER) {
            "Catcher ${hunterIdx++}"
        } else {
            "Runner ${runnerIdx++}"
        }

        list.add(
            Player(
                id = if (isYou) myDeviceId else (i + 1).toString(), // Persistent device ID
                name = name,
                role = role,
                latLng = gameCenter.offsetByMeters(offsetN, offsetE), // Set offset coordinates
            )
        )
    }

    return list // Return generated player list
}

// ============================================================================
// SECTION 5: LOCATION PERMISSION & GPS HOOKS
// ----------------------------------------------------------------------------
// Purpose: Request runtime location permissions and track live GPS coordinates.
// ============================================================================

@Composable
fun rememberLocationPermission(): Pair<Boolean, () -> Unit> {
    val context = LocalContext.current // Get Android context
    var hasPermission by remember {
        mutableStateOf(
            (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED) ||
                    (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED)
        )
    }

    // Permission launcher to trigger system permission prompt
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
                (permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
    }

    val requestPermission = {
        launcher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    return Pair(hasPermission, requestPermission) // Return permission state and trigger launcher
}

@Composable
fun rememberUserLocation(hasPermission: Boolean): LatLng? {
    val context = LocalContext.current // Get Android context
    var location by remember { mutableStateOf<LatLng?>(null) } // Current GPS LatLng state

    DisposableEffect(hasPermission) {
        if (!hasPermission) return@DisposableEffect onDispose {} // Exit if permission not granted

        val fusedClient = LocationServices.getFusedLocationProviderClient(context) // Fused location client
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, // High accuracy GPS priority
            1000L                            // 1 second interval
        ).setMinUpdateIntervalMillis(500L).build() // 500ms min update interval

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    location = LatLng(loc.latitude, loc.longitude) // Update LatLng location state
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper()) // Start updates
            fusedClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let { location = LatLng(it.latitude, it.longitude) } // Immediate last known location
            }
        } catch (_: SecurityException) {
            // Permission missing safety catch
        } catch (_: Exception) {
            // Play services fallback
        }

        onDispose {
            try {
                fusedClient.removeLocationUpdates(callback) // Clean up location updates on disposal
            } catch (_: Exception) {
            }
        }
    }

    return location // Return live GPS LatLng location
}

@Composable
fun rememberDeviceHeading(): Float {
    val context = LocalContext.current
    var headingDegrees by remember { mutableFloatStateOf(0f) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ORIENTATION)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    val azimuthDeg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                    headingDegrees = (azimuthDeg + 360f) % 360f
                } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
                    headingDegrees = (event.values[0] + 360f) % 360f
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (rotationSensor != null && sensorManager != null) {
            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
        }
    }

    return headingDegrees
}

// ============================================================================
// SECTION 6: MAIN ACTIVITY ENTRY POINT
// ----------------------------------------------------------------------------
// Purpose: Android Activity host launching Jetpack Compose UI.
// ============================================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // Call super onCreate
        setContent {
            RealLifeTagTheme { // Apply app theme
                TagGameApp()    // Launch main Compose app container
            }
        }
    }
}

// ============================================================================
// SECTION 7: APP ROOT & STATE MANAGER (TagGameApp)
// ----------------------------------------------------------------------------
// Purpose: Orchestrates navigation stages, SharedPreferences, game engine loop,
// persistent device IDs, and multiplayer sync models.
// ============================================================================

@Composable
fun TagGameApp() {
    val context = LocalContext.current // Get LocalContext
    val prefs = remember { context.getSharedPreferences("reallifetag_prefs", Context.MODE_PRIVATE) } // SharedPreferences

    // Persistent Unique Device ID for Multiplayer Synchronization
    val myDeviceId = remember {
        prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString("device_id", it) }
        }
    }

    var savedNickname by remember { mutableStateOf(prefs.getString("user_nickname", "Player") ?: "Player") } // Persistent nickname

    fun updateSavedNickname(newNick: String) {
        val trimmed = newNick.trim().ifBlank { "Player" }
        savedNickname = trimmed
        prefs.edit { putString("user_nickname", trimmed) } // Save updated nickname locally
    }

    val (hasLocationPermission, requestLocationPermission) = rememberLocationPermission() // GPS permission hook
    val userLocation = rememberUserLocation(hasLocationPermission)                        // Live GPS location hook

    val defaultCenter = LatLng(37.7749, -122.4194) // Default fallback center (San Francisco)
    var matchCenter by remember { mutableStateOf(defaultCenter) } // Active match center

    var stage by remember { mutableStateOf(GameStage.MAIN_MENU) } // Active navigation stage
    var isTestMatchMode by remember { mutableStateOf(false) }     // Flag for Solo Test Match mode
    var isHost by remember { mutableStateOf(true) }               // True if You are the Host
    var currentRoomCode by remember { mutableStateOf("TAG-1001") } // Active room code
    var currentLobbyId by remember { mutableStateOf("") }         // Active Firestore lobby ID

    // Rule Controls State
    var matchDurationMinutes by remember { mutableIntStateOf(30) } // Match length in minutes
    var headStartMinutes by remember { mutableIntStateOf(3) }     // Pre-run head start in minutes
    var pingIntervalMinutes by remember { mutableIntStateOf(2) }   // Ping interval in minutes (default 2 min)
    var lobbyCapacity by remember { mutableIntStateOf(6) }        // Lobby player capacity
    var hunterCount by remember { mutableIntStateOf(2) }          // Hunter count
    var runnerCount by remember { mutableIntStateOf(4) }          // Runner count
    var zoneSizeMeters by remember { mutableIntStateOf(350) }     // Initial zone radius in meters
    var isZoneShrinkEnabled by remember { mutableStateOf(true) }  // Toggle for 3x zone shrinking mechanic

    // Game Session Timers State
    var totalGameSeconds by remember { mutableIntStateOf(1800) }   // Active total game seconds
    var headStartSeconds by remember { mutableIntStateOf(180) }   // Active head start seconds
    val pingIntervalSeconds = pingIntervalMinutes * 60             // Dynamic Ping interval in seconds
    var currentPingTimer by remember { mutableIntStateOf(pingIntervalSeconds) } // Active ping countdown
    val zoneShrinkInterval = 300                                   // Zone shrink interval (5 min)
    var zoneShrinkTimer by remember { mutableIntStateOf(zoneShrinkInterval) }   // Active zone shrink countdown
    var zoneRadiusMeters by remember { mutableFloatStateOf(350f) } // Active zone radius in meters
    var zoneShrinkCount by remember { mutableIntStateOf(0) }       // Counter for the 3 zone shrink events

    // Dynamic Players state list
    val players = remember { mutableStateListOf<Player>() }
    var winnerText by remember { mutableStateOf("") } // Winner message state
    var zoneShrinkBanner by remember { mutableStateOf("") } // Zone shrink alert banner message

    // Creates a REAL Multiplayer Lobby using host's name & saves to Firebase Firestore
    fun createRealHostLobby(nick: String) {
        val cleanNick = nick.trim().ifBlank { "Host" }
        val hostLoc = userLocation ?: defaultCenter
        matchCenter = hostLoc
        updateSavedNickname(cleanNick)
        isHost = true
        val lobbyId = myDeviceId
        currentLobbyId = lobbyId
        currentRoomCode = "$cleanNick's Lobby"
        winnerText = ""
        zoneShrinkBanner = ""

        totalGameSeconds = matchDurationMinutes * 60
        headStartSeconds = headStartMinutes * 60
        currentPingTimer = pingIntervalSeconds
        zoneShrinkTimer = zoneShrinkInterval
        zoneRadiusMeters = zoneSizeMeters.toFloat()
        zoneShrinkCount = 0

        val hostPlayer = Player(
            id = myDeviceId,
            name = "$cleanNick (Host)",
            role = Role.RUNNER,
            latLng = hostLoc
        )

        players.clear()
        players.add(hostPlayer)

        // Save to Firebase Firestore
        try {
            val db = FirebaseFirestore.getInstance()
            val lobbyData = mapOf(
                "lobbyId" to lobbyId,
                "hostName" to cleanNick,
                "lobbyName" to "$cleanNick's Lobby",
                "matchDuration" to matchDurationMinutes,
                "zoneSize" to zoneSizeMeters,
                "isZoneShrinkEnabled" to isZoneShrinkEnabled,
                "matchCenterLat" to hostLoc.latitude,
                "matchCenterLng" to hostLoc.longitude,
                "isMatchStarted" to false,
                "players" to listOf(
                    mapOf(
                        "id" to myDeviceId,
                        "name" to "$cleanNick (Host)",
                        "role" to Role.RUNNER.name,
                        "lat" to hostLoc.latitude,
                        "lng" to hostLoc.longitude,
                        "isTagged" to false
                    )
                ),
                "updatedAt" to System.currentTimeMillis()
            )
            db.collection("lobbies").document(lobbyId).set(lobbyData)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        stage = GameStage.LOBBY
    }

    // Joins a Public Lobby by Lobby ID from Firebase Firestore asynchronously
    fun joinLobbyAsync(lobbyId: String, nick: String, onComplete: (Boolean) -> Unit) {
        val cleanNick = nick.trim().ifBlank { "Player" }
        val joinLoc = userLocation ?: defaultCenter

        val db = FirebaseFirestore.getInstance()
        db.collection("lobbies").document(lobbyId).get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    matchCenter = LatLng(
                        doc.getDouble("matchCenterLat") ?: joinLoc.latitude,
                        doc.getDouble("matchCenterLng") ?: joinLoc.longitude
                    )
                    matchDurationMinutes = (doc.getLong("matchDuration") ?: 30).toInt()
                    zoneSizeMeters = (doc.getLong("zoneSize") ?: 350).toInt()
                    isZoneShrinkEnabled = doc.getBoolean("isZoneShrinkEnabled") ?: true

                    updateSavedNickname(cleanNick)
                    isHost = false
                    currentLobbyId = lobbyId
                    currentRoomCode = doc.getString("lobbyName") ?: "Public Lobby"
                    winnerText = ""
                    zoneShrinkBanner = ""

                    totalGameSeconds = matchDurationMinutes * 60
                    headStartSeconds = headStartMinutes * 60
                    currentPingTimer = pingIntervalSeconds
                    zoneShrinkTimer = zoneShrinkInterval
                    zoneRadiusMeters = zoneSizeMeters.toFloat()
                    zoneShrinkCount = 0

                    val rawPlayersList = doc.get("players") as? List<*> ?: emptyList<Any>()
                    val updatedPlayers = mutableListOf<Map<String, Any>>()
                    for (item in rawPlayersList) {
                        if (item is Map<*, *>) {
                            val pId = item["id"]?.toString() ?: ""
                            if (pId != myDeviceId && pId.isNotBlank()) {
                                updatedPlayers.add(
                                    mapOf(
                                        "id" to pId,
                                        "name" to (item["name"]?.toString() ?: "Player"),
                                        "role" to (item["role"]?.toString() ?: Role.RUNNER.name),
                                        "lat" to ((item["lat"] as? Number)?.toDouble() ?: joinLoc.latitude),
                                        "lng" to ((item["lng"] as? Number)?.toDouble() ?: joinLoc.longitude),
                                        "isTagged" to ((item["isTagged"] as? Boolean) ?: false)
                                    )
                                )
                            }
                        }
                    }
                    updatedPlayers.add(
                        mapOf(
                            "id" to myDeviceId,
                            "name" to cleanNick,
                            "role" to Role.RUNNER.name,
                            "lat" to joinLoc.latitude,
                            "lng" to joinLoc.longitude,
                            "isTagged" to false
                        )
                    )

                    // Immediately populate local players list so it contains the Host, existing players, AND the joining player
                    val parsedList = updatedPlayers.mapNotNull { pMap ->
                        val pId = pMap["id"]?.toString() ?: return@mapNotNull null
                        val pName = pMap["name"]?.toString() ?: "Player"
                        val pRoleStr = pMap["role"]?.toString() ?: Role.RUNNER.name
                        val pRole = try { Role.valueOf(pRoleStr) } catch (e: Exception) { Role.RUNNER }
                        val pLat = (pMap["lat"] as? Number)?.toDouble() ?: joinLoc.latitude
                        val pLng = (pMap["lng"] as? Number)?.toDouble() ?: joinLoc.longitude
                        val pTagged = (pMap["isTagged"] as? Boolean) ?: false
                        Player(
                            id = pId,
                            name = pName,
                            role = pRole,
                            latLng = LatLng(pLat, pLng),
                            isTagged = pTagged
                        )
                    }.distinctBy { it.id }

                    players.clear()
                    players.addAll(parsedList)

                    db.collection("lobbies").document(lobbyId).update("players", updatedPlayers)
                        .addOnSuccessListener {
                            stage = GameStage.LOBBY
                            onComplete(true)
                        }
                        .addOnFailureListener {
                            onComplete(false)
                        }
                } else {
                    onComplete(false)
                }
            }
            .addOnFailureListener {
                onComplete(false)
            }
    }

    fun deleteHostLobby() {
        try {
            val db = FirebaseFirestore.getInstance()
            if (currentLobbyId.isNotBlank()) {
                db.collection("lobbies").document(currentLobbyId).delete()
            }
            if (myDeviceId.isNotBlank()) {
                db.collection("lobbies").document(myDeviceId).delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun leaveCurrentLobby() {
        val lobbyIdToLeave = currentLobbyId
        if (isHost) {
            deleteHostLobby()
        } else if (lobbyIdToLeave.isNotBlank()) {
            try {
                val db = FirebaseFirestore.getInstance()
                db.collection("lobbies").document(lobbyIdToLeave).get()
                    .addOnSuccessListener { doc ->
                        if (doc != null && doc.exists()) {
                            val rawPlayersList = doc.get("players") as? List<*> ?: emptyList<Any>()
                            val updatedPlayers = rawPlayersList.mapNotNull { item ->
                                val pMap = item as? Map<*, *> ?: return@mapNotNull null
                                val pId = pMap["id"]?.toString() ?: return@mapNotNull null
                                if (pId == myDeviceId) null else pMap
                            }
                            db.collection("lobbies").document(lobbyIdToLeave).update("players", updatedPlayers)
                        }
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        currentLobbyId = ""
        stage = GameStage.MAIN_MENU
    }

    // SOLO MATCH TESTER MODE (generates mock players for testing)
    fun startSoloTestMatch(nick: String) {
        val cleanNick = nick.trim().ifBlank { "Tester" }
        val testLoc = userLocation ?: defaultCenter
        matchCenter = testLoc
        updateSavedNickname(cleanNick)
        isHost = true
        val testLobbyId = myDeviceId
        currentLobbyId = testLobbyId
        currentRoomCode = "$cleanNick's Test Lobby"
        winnerText = ""
        zoneShrinkBanner = ""

        totalGameSeconds = matchDurationMinutes * 60
        headStartSeconds = headStartMinutes * 60
        currentPingTimer = pingIntervalSeconds
        zoneShrinkTimer = zoneShrinkInterval
        zoneRadiusMeters = zoneSizeMeters.toFloat()
        zoneShrinkCount = 0

        players.clear()
        players.addAll(generateRandomizedPlayersWithHost(hunterCount, runnerCount, testLoc, cleanNick, myDeviceId))

        stage = if (headStartMinutes > 0) GameStage.HEAD_START else GameStage.ACTIVE_GAME
    }

    fun startLobbyMatch() {
        winnerText = ""
        zoneShrinkBanner = ""
        totalGameSeconds = matchDurationMinutes * 60
        headStartSeconds = headStartMinutes * 60
        currentPingTimer = pingIntervalSeconds
        zoneShrinkTimer = zoneShrinkInterval
        zoneRadiusMeters = zoneSizeMeters.toFloat()
        zoneShrinkCount = 0

        // Assign balanced roles to joined players when match starts
        if (players.isNotEmpty()) {
            val total = players.size
            val huntersNeeded = (hunterCount.toFloat() / (hunterCount + runnerCount).toFloat() * total).toInt().coerceIn(1, (total - 1).coerceAtLeast(1))
            val roles = mutableListOf<Role>()
            repeat(huntersNeeded) { roles.add(Role.HUNTER) }
            repeat(total - huntersNeeded) { roles.add(Role.RUNNER) }
            roles.shuffle()

            for (i in players.indices) {
                players[i] = players[i].copy(
                    role = roles[i],
                    isTagged = false,
                    lastPingLocation = null,
                    secondsOutsideZone = 0,
                    isLiveExposed = false
                )
            }
        }

        if (!isTestMatchMode && currentLobbyId.isNotBlank() && isHost) {
            try {
                val db = FirebaseFirestore.getInstance()
                val updatedPlayersMap = players.map { p ->
                    mapOf(
                        "id" to p.id,
                        "name" to p.name,
                        "role" to p.role.name,
                        "lat" to p.latLng.latitude,
                        "lng" to p.latLng.longitude,
                        "isTagged" to p.isTagged
                    )
                }
                db.collection("lobbies").document(currentLobbyId).update(
                    mapOf(
                        "isMatchStarted" to true,
                        "players" to updatedPlayersMap
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        stage = if (headStartMinutes > 0) GameStage.HEAD_START else GameStage.ACTIVE_GAME
    }

    // Real-time Firestore sync while waiting in Lobby screen
    DisposableEffect(stage, currentLobbyId) {
        if (stage == GameStage.LOBBY && currentLobbyId.isNotBlank()) {
            val db = FirebaseFirestore.getInstance()
            val listener = db.collection("lobbies").document(currentLobbyId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    
                    if (snapshot == null || !snapshot.exists()) {
                        if (!isHost && stage == GameStage.LOBBY) {
                            currentLobbyId = ""
                            stage = GameStage.MAIN_MENU
                        }
                        return@addSnapshotListener
                    }

                    val isMatchStarted = snapshot.getBoolean("isMatchStarted") ?: false
                    val rawPlayersList = snapshot.get("players") as? List<*> ?: emptyList<Any>()
                    val hostName = snapshot.getString("hostName") ?: ""

                    // Check if host is still in players list
                    val hasHost = rawPlayersList.any { pObj ->
                        val pMap = pObj as? Map<*, *> ?: return@any false
                        val pName = pMap["name"]?.toString() ?: ""
                        pName.contains("(Host)") || (hostName.isNotBlank() && pName.startsWith(hostName))
                    }

                    if (!hasHost && !isHost && stage == GameStage.LOBBY) {
                        currentLobbyId = ""
                        stage = GameStage.MAIN_MENU
                        return@addSnapshotListener
                    }
                    
                    val defaultLoc = userLocation ?: defaultCenter
                    val parsedList = rawPlayersList.mapNotNull { pObj ->
                        val pMap = pObj as? Map<*, *> ?: return@mapNotNull null
                        val pId = pMap["id"]?.toString() ?: return@mapNotNull null
                        val pName = pMap["name"]?.toString() ?: "Player"
                        val pRoleStr = pMap["role"]?.toString() ?: Role.RUNNER.name
                        val pRole = try { Role.valueOf(pRoleStr) } catch (e: Exception) { Role.RUNNER }
                        val pLat = (pMap["lat"] as? Number)?.toDouble() ?: defaultLoc.latitude
                        val pLng = (pMap["lng"] as? Number)?.toDouble() ?: defaultLoc.longitude
                        val pTagged = (pMap["isTagged"] as? Boolean) ?: false
                        Player(
                            id = pId,
                            name = pName,
                            role = pRole,
                            latLng = LatLng(pLat, pLng),
                            isTagged = pTagged
                        )
                    }.distinctBy { it.id }

                    players.clear()
                    players.addAll(parsedList)

                    if (isMatchStarted && stage == GameStage.LOBBY) {
                        val durationMin = (snapshot.getLong("matchDuration") ?: matchDurationMinutes.toLong()).toInt()
                        val headStartMin = (snapshot.getLong("headStartMinutes") ?: headStartMinutes.toLong()).toInt()
                        val pingMin = (snapshot.getLong("pingIntervalMinutes") ?: pingIntervalMinutes.toLong()).toInt()
                        val zoneMeters = (snapshot.getLong("zoneSize") ?: zoneSizeMeters.toLong()).toInt()

                        matchDurationMinutes = durationMin
                        headStartMinutes = headStartMin
                        pingIntervalMinutes = pingMin
                        zoneSizeMeters = zoneMeters

                        totalGameSeconds = durationMin * 60
                        headStartSeconds = headStartMin * 60
                        currentPingTimer = pingMin * 60
                        zoneShrinkTimer = zoneShrinkInterval
                        zoneRadiusMeters = zoneMeters.toFloat()
                        zoneShrinkCount = 0

                        stage = if (headStartMin > 0) GameStage.HEAD_START else GameStage.ACTIVE_GAME
                    }
                }
            onDispose {
                listener.remove()
            }
        } else {
            onDispose {}
        }
    }

    // Active Match Ticker Loop: Guarantees in-app timers count down every 1 sec without freezing
    LaunchedEffect(stage) {
        if (stage == GameStage.HEAD_START || stage == GameStage.ACTIVE_GAME) {
            while (stage == GameStage.HEAD_START || stage == GameStage.ACTIVE_GAME) {
                delay(1000L)
                if (stage == GameStage.HEAD_START) {
                    if (headStartSeconds > 0) {
                        headStartSeconds--
                    }
                    if (headStartSeconds <= 0) {
                        stage = GameStage.ACTIVE_GAME
                        val initialList = players.toList()
                        for (i in initialList.indices) {
                            if (i < players.size && players[i].role == Role.RUNNER) {
                                players[i] = players[i].copy(lastPingLocation = players[i].latLng)
                            }
                        }
                    }
                } else if (stage == GameStage.ACTIVE_GAME) {
                    if (totalGameSeconds > 0) {
                        totalGameSeconds--
                        currentPingTimer--
                        zoneShrinkTimer--

                        // Check Zone Shrink (2 times total during match, each by 15% if enabled)
                        if (isZoneShrinkEnabled && zoneShrinkCount < 2) {
                            val totalMatchSeconds = matchDurationMinutes * 60
                            val elapsedSeconds = totalMatchSeconds - totalGameSeconds
                            val threshold = (totalMatchSeconds / 3) * (zoneShrinkCount + 1)
                            if (elapsedSeconds >= threshold) {
                                zoneRadiusMeters = (zoneRadiusMeters * 0.85f).coerceAtLeast(30f)
                                zoneShrinkCount++
                                playSpecialShrinkSound()
                                zoneShrinkBanner = "⚠️ ZONE SHRUNK! Radius is now ${zoneRadiusMeters.toInt()}m"
                            }
                        }

                        // Check Ping Trigger (Every 2 min)
                        if (currentPingTimer <= 0) {
                            val pingSnapshot = players.toList()
                            for (i in pingSnapshot.indices) {
                                if (i < players.size && players[i].role == Role.RUNNER && !players[i].isTagged) {
                                    players[i] = players[i].copy(lastPingLocation = players[i].latLng)
                                }
                            }
                            currentPingTimer = pingIntervalSeconds
                        }

                        // Check Outside Zone Rule (30s out = Auto-tagged)
                        val zoneSnapshot = players.toList()
                        for (i in zoneSnapshot.indices) {
                            if (i < players.size) {
                                val p = zoneSnapshot[i]
                                if (p.role == Role.RUNNER && !p.isTagged) {
                                    val dist = distanceInMeters(p.latLng, matchCenter)
                                    if (dist > zoneRadiusMeters) {
                                        val newSec = p.secondsOutsideZone + 1
                                        val autoTagged = newSec >= 30 // 30 seconds to return before auto-tag
                                        players[i] = p.copy(
                                            secondsOutsideZone = newSec,
                                            isLiveExposed = true,
                                            isTagged = if (autoTagged) true else p.isTagged
                                        )
                                        if (autoTagged && (p.id == myDeviceId || p.id == "6")) {
                                            winnerText = "TAGGED! You stayed outside the zone for 30 seconds."
                                            stage = GameStage.GAME_OVER
                                        }
                                    } else {
                                        if (p.secondsOutsideZone != 0 || p.isLiveExposed) {
                                            players[i] = p.copy(secondsOutsideZone = 0, isLiveExposed = false)
                                        }
                                    }
                                }
                            }
                        }

                        // Check Win Conditions
                        val activeRunners = players.count { it.role == Role.RUNNER && !it.isTagged }
                        if (activeRunners == 0 && players.isNotEmpty()) {
                            winnerText = "HUNTERS WIN! All runners captured."
                            stage = GameStage.GAME_OVER
                        } else if (totalGameSeconds <= 0) {
                            winnerText = "RUNNERS WIN! Time expired with free runners remaining."
                            stage = GameStage.GAME_OVER
                        }
                    }
                }
            }
        }
    }

    // Real-time snapshot listener for player updates during ACTIVE_GAME and HEAD_START
    DisposableEffect(stage, currentLobbyId) {
        if ((stage == GameStage.HEAD_START || stage == GameStage.ACTIVE_GAME) && currentLobbyId.isNotBlank()) {
            val db = FirebaseFirestore.getInstance()
            val listener = db.collection("lobbies").document(currentLobbyId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                    
                    val isHeadStartSkipped = snapshot.getBoolean("isHeadStartSkipped") ?: false
                    if (isHeadStartSkipped && stage == GameStage.HEAD_START) {
                        stage = GameStage.ACTIVE_GAME
                    }

                    val rawPlayersList = snapshot.get("players") as? List<*> ?: emptyList<Any>()
                    for (item in rawPlayersList) {
                        val pMap = item as? Map<*, *> ?: continue
                        val pId = pMap["id"]?.toString() ?: continue
                        if (pId == myDeviceId) continue // Skip "You" (updated locally by GPS)

                        val pLat = (pMap["lat"] as? Number)?.toDouble() ?: continue
                        val pLng = (pMap["lng"] as? Number)?.toDouble() ?: continue
                        val pTagged = (pMap["isTagged"] as? Boolean) ?: false
                        val lastPingLat = (pMap["lastPingLat"] as? Number)?.toDouble()
                        val lastPingLng = (pMap["lastPingLng"] as? Number)?.toDouble()
                        val lastPingLoc = if (lastPingLat != null && lastPingLng != null) LatLng(lastPingLat, lastPingLng) else null

                        val idx = players.indexOfFirst { it.id == pId }
                        if (idx != -1) {
                            players[idx] = players[idx].copy(
                                latLng = LatLng(pLat, pLng),
                                isTagged = pTagged,
                                lastPingLocation = lastPingLoc ?: players[idx].lastPingLocation
                            )
                        }
                    }
                }
            onDispose {
                listener.remove()
            }
        } else {
            onDispose {}
        }
    }

    // Keep "You" updated with live GPS user location and push live location to Firestore during active game
    LaunchedEffect(userLocation) {
        userLocation?.let { loc ->
            val index = players.indexOfFirst { it.id == myDeviceId || it.id == "6" }
            if (index != -1) {
                players[index] = players[index].copy(latLng = loc) // Update live coordinates for You
            }
            if ((stage == GameStage.HEAD_START || stage == GameStage.ACTIVE_GAME) && currentLobbyId.isNotBlank()) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    db.collection("lobbies").document(currentLobbyId).get()
                        .addOnSuccessListener { doc ->
                            if (doc != null && doc.exists()) {
                                val rawList = doc.get("players") as? List<*> ?: emptyList<Any>()
                                val updatedList = rawList.map { item ->
                                    val map = (item as? Map<*, *>)?.toMutableMap() ?: mutableMapOf<Any?, Any?>()
                                    if (map["id"]?.toString() == myDeviceId) {
                                        map["lat"] = loc.latitude
                                        map["lng"] = loc.longitude
                                        val myP = players.find { it.id == myDeviceId || it.id == "6" }
                                        if (myP != null) {
                                            map["isTagged"] = myP.isTagged
                                            if (myP.lastPingLocation != null) {
                                                map["lastPingLat"] = myP.lastPingLocation.latitude
                                                map["lastPingLng"] = myP.lastPingLocation.longitude
                                            }
                                        }
                                    }
                                    map
                                }
                                db.collection("lobbies").document(currentLobbyId).update("players", updatedList)
                            }
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // --- GAME ENGINE LOOP & FOREGROUND SERVICE TIMER ---
    DisposableEffect(stage) {
        if (stage == GameStage.HEAD_START || stage == GameStage.ACTIVE_GAME) {
            GameTimerService.startService(context)
            GameTimerService.getStatusText = {
                if (stage == GameStage.HEAD_START) {
                    "Pre-Run Head Start: ${headStartSeconds}s"
                } else {
                    val m = totalGameSeconds / 60
                    val s = totalGameSeconds % 60
                    "Time left: $m:${String.format(java.util.Locale.getDefault(), "%02d", s)}"
                }
            }
            GameTimerService.onTick = {
                if (stage == GameStage.HEAD_START) {
                    if (headStartSeconds > 0) {
                        headStartSeconds--
                    }
                    if (headStartSeconds <= 0) {
                        stage = GameStage.ACTIVE_GAME
                        val initialList = players.toList()
                        for (i in initialList.indices) {
                            if (i < players.size && players[i].role == Role.RUNNER) {
                                players[i] = players[i].copy(lastPingLocation = players[i].latLng)
                            }
                        }
                    }
                } else if (stage == GameStage.ACTIVE_GAME) {
                    if (totalGameSeconds > 0) {
                        totalGameSeconds--
                        currentPingTimer--
                        zoneShrinkTimer--

                        // Check Zone Shrink (2 times total during match, each by 15% if enabled)
                        if (isZoneShrinkEnabled && zoneShrinkCount < 2) {
                            val totalMatchSeconds = matchDurationMinutes * 60
                            val elapsedSeconds = totalMatchSeconds - totalGameSeconds
                            val threshold = (totalMatchSeconds / 3) * (zoneShrinkCount + 1)
                            if (elapsedSeconds >= threshold) {
                                zoneRadiusMeters = (zoneRadiusMeters * 0.85f).coerceAtLeast(30f)
                                zoneShrinkCount++
                                playSpecialShrinkSound()
                                zoneShrinkBanner = "⚠️ ZONE SHRUNK! Radius is now ${zoneRadiusMeters.toInt()}m"
                            }
                        }

                        // Check Ping Trigger (Every 2 min)
                        if (currentPingTimer <= 0) {
                            val pingSnapshot = players.toList()
                            for (i in pingSnapshot.indices) {
                                if (i < players.size && players[i].role == Role.RUNNER && !players[i].isTagged) {
                                    players[i] = players[i].copy(lastPingLocation = players[i].latLng)
                                }
                            }
                            currentPingTimer = pingIntervalSeconds
                        }

                        // Check Outside Zone Rule (30s out = Auto-tagged)
                        val zoneSnapshot = players.toList()
                        for (i in zoneSnapshot.indices) {
                            if (i < players.size) {
                                val p = zoneSnapshot[i]
                                if (p.role == Role.RUNNER && !p.isTagged) {
                                    val dist = distanceInMeters(p.latLng, matchCenter)
                                    if (dist > zoneRadiusMeters) {
                                        val newSec = p.secondsOutsideZone + 1
                                        val autoTagged = newSec >= 30 // 30 seconds to return before auto-tag
                                        players[i] = p.copy(
                                            secondsOutsideZone = newSec,
                                            isLiveExposed = true,
                                            isTagged = if (autoTagged) true else p.isTagged
                                        )
                                        if (autoTagged && (p.id == myDeviceId || p.id == "6")) {
                                            winnerText = "TAGGED! You stayed outside the zone for 30 seconds."
                                            stage = GameStage.GAME_OVER
                                        }
                                    } else {
                                        if (p.secondsOutsideZone != 0 || p.isLiveExposed) {
                                            players[i] = p.copy(secondsOutsideZone = 0, isLiveExposed = false)
                                        }
                                    }
                                }
                            }
                        }

                        // Check Win Conditions
                        val activeRunners = players.count { it.role == Role.RUNNER && !it.isTagged }
                        if (activeRunners == 0 && players.isNotEmpty()) {
                            winnerText = "HUNTERS WIN! All runners captured."
                            stage = GameStage.GAME_OVER
                        } else if (totalGameSeconds <= 0) {
                            winnerText = "RUNNERS WIN! Time expired with free runners remaining."
                            stage = GameStage.GAME_OVER
                        }
                    }
                }
            }
        } else {
            GameTimerService.stopService(context)
            GameTimerService.onTick = null
        }
        onDispose {
            if (stage != GameStage.HEAD_START && stage != GameStage.ACTIVE_GAME) {
                GameTimerService.stopService(context)
                GameTimerService.onTick = null
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF141414)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (stage) {
                GameStage.MAIN_MENU -> MainMenuHomeScreen(
                    hasPermission = hasLocationPermission,
                    onRequestPermission = requestLocationPermission,
                    onSelectHost = {
                        isTestMatchMode = false
                        stage = GameStage.SETUP
                    },
                    onSelectParticipate = { stage = GameStage.JOIN_GAME },
                    onSelectSoloTestMatch = {
                        isTestMatchMode = true
                        stage = GameStage.SETUP
                    }
                )
                GameStage.SETUP -> SetupHomeScreen(
                    savedNickname = savedNickname,
                    isTestMatchMode = isTestMatchMode,
                    matchDurationMinutes = matchDurationMinutes,
                    onMatchDurationChange = { matchDurationMinutes = it },
                    headStartMinutes = headStartMinutes,
                    onHeadStartChange = { headStartMinutes = it },
                    pingIntervalMinutes = pingIntervalMinutes,
                    onPingIntervalChange = { pingIntervalMinutes = it },
                    lobbyCapacity = lobbyCapacity,
                    onLobbyCapacityChange = { newCap ->
                        lobbyCapacity = newCap
                        hunterCount = (newCap / 3).coerceAtLeast(1)
                        runnerCount = newCap - hunterCount
                    },
                    hunterCount = hunterCount,
                    onHunterCountChange = { newHunters ->
                        hunterCount = newHunters.coerceIn(1, lobbyCapacity - 1)
                        runnerCount = lobbyCapacity - hunterCount
                    },
                    runnerCount = runnerCount,
                    onRunnerCountChange = { newRunners ->
                        runnerCount = newRunners.coerceIn(1, lobbyCapacity - 1)
                        hunterCount = lobbyCapacity - runnerCount
                    },
                    zoneSizeMeters = zoneSizeMeters,
                    onZoneSizeChange = { zoneSizeMeters = it },
                    isZoneShrinkEnabled = isZoneShrinkEnabled,
                    onZoneShrinkToggle = { isZoneShrinkEnabled = it },
                    hasPermission = hasLocationPermission,
                    onRequestPermission = requestLocationPermission,
                    onCreateLobby = { nick ->
                        if (isTestMatchMode) {
                            startSoloTestMatch(nick)
                        } else {
                            createRealHostLobby(nick)
                        }
                    },
                    onBackToMain = { stage = GameStage.MAIN_MENU }
                )
                GameStage.JOIN_GAME -> PublicLobbiesScreen(
                    savedNickname = savedNickname,
                    onJoinLobby = { lobbyId, nick, onComplete -> joinLobbyAsync(lobbyId, nick, onComplete) },
                    onBackToMain = { stage = GameStage.MAIN_MENU }
                )
                GameStage.LOBBY -> LobbyScreen(
                    roomCode = currentRoomCode,
                    isHost = isHost,
                    userNickname = savedNickname,
                    myDeviceId = myDeviceId,
                    lobbyCapacity = lobbyCapacity,
                    players = players,
                    onStartMatch = { startLobbyMatch() },
                    onLeaveLobby = { leaveCurrentLobby() }
                )
                GameStage.HEAD_START, GameStage.ACTIVE_GAME -> {
                    val totalMatchSecs = matchDurationMinutes * 60
                    val elapsedSecs = totalMatchSecs - totalGameSeconds
                    val nextThresh = (totalMatchSecs / 3) * (zoneShrinkCount + 1)
                    val secsUntil = if (isZoneShrinkEnabled && zoneShrinkCount < 2) (nextThresh - elapsedSecs).coerceAtLeast(0) else 0
                    val sMin = secsUntil / 60
                    val sSec = secsUntil % 60
                    val formattedShrinkCd = String.format(Locale.getDefault(), "%d:%02d", sMin, sSec)

                    GameplayScreen(
                        myDeviceId = myDeviceId,
                        isHost = isHost,
                        isHeadStart = (stage == GameStage.HEAD_START),
                        headStartSeconds = headStartSeconds,
                        totalSeconds = totalGameSeconds,
                        pingSeconds = currentPingTimer,
                        pingIntervalSeconds = pingIntervalSeconds,
                        zoneCenter = matchCenter,
                        zoneRadiusMeters = zoneRadiusMeters,
                        players = players,
                        userLocation = userLocation,
                        hasPermission = hasLocationPermission,
                        onRequestPermission = requestLocationPermission,
                        gameOverText = winnerText,
                        zoneShrinkCountdown = formattedShrinkCd,
                        zoneShrinkBanner = zoneShrinkBanner,
                        isZoneShrinkEnabled = isZoneShrinkEnabled,
                        onTagPlayer = { playerId ->
                            val index = players.indexOfFirst { it.id == playerId }
                            if (index != -1) {
                                players[index] = players[index].copy(isTagged = true)
                            }
                        },
                        onResetHome = { leaveCurrentLobby() },
                        onSkipHeadStart = {
                            stage = GameStage.ACTIVE_GAME
                            if (isHost && currentLobbyId.isNotBlank()) {
                                try {
                                    FirebaseFirestore.getInstance().collection("lobbies").document(currentLobbyId).update("isHeadStartSkipped", true)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    )
                }
                GameStage.GAME_OVER -> GameOverResultsScreen(
                    myDeviceId = myDeviceId,
                    winnerText = winnerText,
                    players = players,
                    matchDurationMinutes = matchDurationMinutes,
                    onPlayAgain = {
                        // Reset session timers and player status before returning to Lobby page
                        totalGameSeconds = matchDurationMinutes * 60
                        headStartSeconds = headStartMinutes * 60
                        currentPingTimer = pingIntervalSeconds
                        zoneShrinkTimer = zoneShrinkInterval
                        zoneRadiusMeters = zoneSizeMeters.toFloat()
                        winnerText = ""

                        for (i in players.indices) {
                            players[i] = players[i].copy(
                                isTagged = false,
                                lastPingLocation = null,
                                secondsOutsideZone = 0,
                                isLiveExposed = false
                            )
                        }

                        stage = GameStage.LOBBY
                    },
                    onBackToMain = { leaveCurrentLobby() }
                )
            }
        }
    }
}

// ============================================================================
// SECTION 8: MAIN LANDING HOME SCREEN
// ----------------------------------------------------------------------------
// Purpose: Landing screen offering Host Game, Participate, and Solo Test options.
// ============================================================================

@Composable
fun MainMenuHomeScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onSelectHost: () -> Unit,
    onSelectParticipate: () -> Unit,
    onSelectSoloTestMatch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        RunningManLogo(sizeDp = 84) // Render logo badge

        Spacer(modifier = Modifier.height(16.dp))

        Text("REAL LIFE TAG", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Welcome to The Zone",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp
        )
        Text(
            "Real-world street tag with GPS tracking and shrinking boundary zones.",
            color = Color.LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))

        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2211)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📍 GPS Permission Required", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Enable location access to show the real Google Map centered on your position.", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant GPS Access", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // OPTION 1: HOST A GAME
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("👑", fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Host a Game", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Create match, configure rules, boundaries & timers", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSelectHost,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Host New Match", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // OPTION 2: PARTICIPATE IN A GAME
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎮", fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Participate / Join Game", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Join an existing match hosted by a friend with room code", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSelectParticipate,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Join Game Room", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // OPTION 3: SOLO MATCH TESTER
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF333333))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧪", fontSize = 26.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Solo Match Tester", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Test game mechanics solo with simulated players", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSelectSoloTestMatch,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Launch Solo Test Match", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ============================================================================
// SECTION 9: PUBLIC LOBBIES SCREEN
// ----------------------------------------------------------------------------
// Purpose: Screen listing active host lobbies from Firestore for players to join.
// ============================================================================

@Composable
fun PublicLobbiesScreen(
    savedNickname: String,
    onJoinLobby: (String, String, (Boolean) -> Unit) -> Unit,
    onBackToMain: () -> Unit
) {
    var playerNickname by remember { mutableStateOf(savedNickname) }
    var lobbiesList by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isJoining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf("") }
    var selectedLobbyId by remember { mutableStateOf<String?>(null) }
    var selectedHostName by remember { mutableStateOf("") }
    var showJoinDialog by remember { mutableStateOf(false) }

    // Real-time listener for active lobbies from Firestore
    DisposableEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("lobbies").addSnapshotListener { snapshot, e ->
            if (e != null) {
                isLoading = false
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val now = System.currentTimeMillis()
                val items = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data?.toMutableMap() ?: run {
                        // Purge empty document from Firestore
                        try { db.collection("lobbies").document(doc.id).delete() } catch (_: Exception) {}
                        return@mapNotNull null
                    }
                    val id = (data["lobbyId"] as? String)?.ifBlank { doc.id } ?: doc.id
                    data["lobbyId"] = id

                    val isMatchStarted = data["isMatchStarted"] as? Boolean ?: false
                    val rawPlayers = data["players"] as? List<*> ?: emptyList<Any>()
                    val hostName = data["hostName"] as? String ?: ""
                    val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L

                    // Check if host is present in players list
                    val hasHost = rawPlayers.any { pObj ->
                        val pMap = pObj as? Map<*, *> ?: return@any false
                        val pName = pMap["name"] as? String ?: ""
                        pName.contains("(Host)") || (hostName.isNotBlank() && pName.startsWith(hostName))
                    }

                    val isStale = (updatedAt > 0L && (now - updatedAt) > 30 * 60 * 1000L) // Stale after 30 mins

                    // Automatically delete non-active lobbies (started matches, no players, host left, or stale) from Firestore
                    if (isMatchStarted || rawPlayers.isEmpty() || !hasHost || isStale) {
                        try {
                            db.collection("lobbies").document(doc.id).delete()
                        } catch (_: Exception) {}
                        return@mapNotNull null
                    }

                    data
                }.distinctBy { it["lobbyId"] as? String }
                lobbiesList = items
                isLoading = false
            }
        }
        onDispose {
            listener.remove()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(onClick = onBackToMain) {
                Text("← Back to Main Menu", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("PUBLIC LOBBIES", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Available Host Matches",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 26.sp
        )
        Text(
            "Select an active lobby hosted by another player to join.",
            color = Color.LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // NICKNAME FIELD
        OutlinedTextField(
            value = playerNickname,
            onValueChange = { playerNickname = it },
            label = { Text("Your Nickname", color = Color.Gray) },
            placeholder = { Text("e.g. Sam", color = Color.DarkGray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF00E676),
                unfocusedBorderColor = Color(0xFF444444),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF222222),
                unfocusedContainerColor = Color(0xFF222222)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            Text("Loading available lobbies...", color = Color.Gray, fontSize = 14.sp)
        } else if (lobbiesList.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No active lobbies found.", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ask a friend to host a new match!", color = Color.Gray, fontSize = 13.sp)
                }
            }
        } else {
            lobbiesList.forEach { lobby ->
                val lobbyId = lobby["lobbyId"] as? String ?: return@forEach
                val hostName = lobby["hostName"] as? String ?: "Host"
                val lobbyName = lobby["lobbyName"] as? String ?: "$hostName's Lobby"
                val playersList = lobby["players"] as? List<*> ?: emptyList<Any>()

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFF333333))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(lobbyName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Players in lobby: ${playersList.size}", color = Color(0xFF00E676), fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                selectedLobbyId = lobbyId
                                selectedHostName = hostName
                                showJoinDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Join", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // CONFIRMATION DIALOG: "Would you like to join [HostName]'s lobby?"
        if (showJoinDialog && selectedLobbyId != null) {
            AlertDialog(
                onDismissRequest = { showJoinDialog = false },
                title = { Text("Join Lobby", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("Would you like to join $selectedHostName's lobby?", color = Color.LightGray) },
                confirmButton = {
                    Button(
                        onClick = {
                            showJoinDialog = false
                            val nick = playerNickname.trim().ifBlank { "Player" }
                            selectedLobbyId?.let { id ->
                                isJoining = true
                                joinError = ""
                                onJoinLobby(id, nick) { success ->
                                    isJoining = false
                                    if (!success) {
                                        joinError = "Failed to join $selectedHostName's lobby. Please try again."
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Join", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showJoinDialog = false }) {
                        Text("Cancel", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF222222)
            )
        }
    }
}

// ============================================================================
// SECTION 10: HOST GAME SETUP SCREEN
// ----------------------------------------------------------------------------
// Purpose: Screen for host to configure rules, zone size, and lobby capacity.
// ============================================================================

@Composable
fun SetupHomeScreen(
    savedNickname: String,
    isTestMatchMode: Boolean = false,
    matchDurationMinutes: Int,
    onMatchDurationChange: (Int) -> Unit,
    headStartMinutes: Int,
    onHeadStartChange: (Int) -> Unit,
    pingIntervalMinutes: Int,
    onPingIntervalChange: (Int) -> Unit,
    lobbyCapacity: Int,
    onLobbyCapacityChange: (Int) -> Unit,
    hunterCount: Int,
    onHunterCountChange: (Int) -> Unit,
    runnerCount: Int,
    onRunnerCountChange: (Int) -> Unit,
    zoneSizeMeters: Int,
    onZoneSizeChange: (Int) -> Unit,
    isZoneShrinkEnabled: Boolean,
    onZoneShrinkToggle: (Boolean) -> Unit,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onCreateLobby: (String) -> Unit,
    onBackToMain: () -> Unit
) {
    var hostNickname by remember { mutableStateOf(savedNickname) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Enlarged back arrow button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(onClick = onBackToMain) {
                Text("← Back to Main Menu", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        RunningManLogo(sizeDp = 72)

        Spacer(modifier = Modifier.height(12.dp))

        Text("REAL LIFE TAG", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isTestMatchMode) "Configure Test Match" else "Create New Match",
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 28.sp
        )
        Text(
            text = if (isTestMatchMode)
                "Set host rules, zone size, and player ratios for your solo test run."
            else
                "Configure game rules, zone size, and player capacity for your match.",
            color = Color.LightGray,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // HOST NICKNAME FIELD
        OutlinedTextField(
            value = hostNickname,
            onValueChange = { hostNickname = it },
            label = { Text("Your Host Nickname", color = Color.Gray) },
            placeholder = { Text("e.g. Alex", color = Color.DarkGray) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFD32F2F),
                unfocusedBorderColor = Color(0xFF444444),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF222222),
                unfocusedContainerColor = Color(0xFF222222)
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2211)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📍 GPS Permission Required", color = Color(0xFFFFB74D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Enable location access to show the real Google Map centered on your position.", color = Color.LightGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Grant GPS Access", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("ZONE & LOBBY CONFIGURATION", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // INITIAL ZONE SIZE CONTROL
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("📍 Initial Zone Size", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Starting boundary radius in meters", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(200 to "200m", 350 to "350m", 500 to "500m", 800 to "800m")
                    options.forEach { (sizeVal, label) ->
                        Button(
                            onClick = { onZoneSizeChange(sizeVal) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (zoneSizeMeters == sizeVal) Color(0xFFD32F2F) else Color(0xFF333333)
                            ),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ZONE SHRINK TOGGLE CONTROL
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("🌪️ Shrinking Boundary Zone", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Shrinks 2 times (each time by 15%)", color = Color.Gray, fontSize = 11.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onZoneShrinkToggle(true) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isZoneShrinkEnabled) Color(0xFFD32F2F) else Color(0xFF333333)
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("ON (2x Shrinks)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Button(
                        onClick = { onZoneShrinkToggle(false) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isZoneShrinkEnabled) Color(0xFFD32F2F) else Color(0xFF333333)
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text("OFF (Fixed Zone)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // LOBBY CAPACITY
        RuleStepper(
            title = "👥 Lobby Player Capacity",
            subtitle = "Maximum players allowed in match",
            valueText = "$lobbyCapacity Players",
            onDecrease = { onLobbyCapacityChange((lobbyCapacity - 1).coerceAtLeast(2)) },
            onIncrease = { onLobbyCapacityChange((lobbyCapacity + 1).coerceAtMost(20)) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("ROLE RATIOS (Randomly Assigned)", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // CATCHERS RATIO
        RuleStepper(
            title = "🎯 Catchers Ratio",
            subtitle = "Number of catchers randomly chosen",
            valueText = "$hunterCount Catchers",
            onDecrease = { onHunterCountChange(hunterCount - 1) },
            onIncrease = { onHunterCountChange(hunterCount + 1) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // RUNNERS RATIO
        RuleStepper(
            title = "🏃‍♂️ Runners Ratio",
            subtitle = "Number of runners randomly chosen",
            valueText = "$runnerCount Runners",
            onDecrease = { onRunnerCountChange(runnerCount - 1) },
            onIncrease = { onRunnerCountChange(runnerCount + 1) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text("GAME TIMERS & RULES", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        // MATCH DURATION
        RuleStepper(
            title = "⏱️ Match Duration",
            subtitle = "Total game time before time expires",
            valueText = "$matchDurationMinutes min",
            onDecrease = { onMatchDurationChange((matchDurationMinutes - 5).coerceAtLeast(5)) },
            onIncrease = { onMatchDurationChange((matchDurationMinutes + 5).coerceAtMost(120)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // PRE-RUN / HEAD START DURATION
        RuleStepper(
            title = "🏃 Pre-Run Head Start",
            subtitle = "Time runners get to scatter before catchers start",
            valueText = "$headStartMinutes min",
            onDecrease = { onHeadStartChange((headStartMinutes - 1).coerceAtLeast(0)) },
            onIncrease = { onHeadStartChange((headStartMinutes + 1).coerceAtMost(15)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // PING INTERVAL CONTROL
        RuleStepper(
            title = "📡 Ping Interval",
            subtitle = "Time between runner location updates",
            valueText = "$pingIntervalMinutes min",
            onDecrease = { onPingIntervalChange((pingIntervalMinutes - 1).coerceAtLeast(1)) },
            onIncrease = { onPingIntervalChange((pingIntervalMinutes + 1).coerceAtMost(15)) }
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = {
                val nick = hostNickname.trim().ifBlank { if (isTestMatchMode) "Tester" else "Host" }
                onCreateLobby(nick)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isTestMatchMode) Color(0xFFFF9800) else Color(0xFFD32F2F)
            )
        ) {
            Text(
                text = if (isTestMatchMode) "🧪 Launch Solo Test Match" else "🏠 Open Lobby Waiting Room",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (isTestMatchMode) Color.Black else Color.White
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// --- LOBBY WAITING ROOM SCREEN ---

@Composable
fun LobbyScreen(
    roomCode: String,
    isHost: Boolean,
    userNickname: String,
    myDeviceId: String,
    lobbyCapacity: Int,
    players: List<Player>,
    onStartMatch: () -> Unit,
    onLeaveLobby: () -> Unit
) {
    val uniquePlayers = players.distinctBy { it.id }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            TextButton(onClick = onLeaveLobby) {
                Text("← Leave Lobby", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("LOBBY WAITING ROOM", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Share Room Code", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // ROOM CODE CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2211)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.5.dp, Color(0xFFFF9800)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ROOM CODE / GAME ID", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(roomCode, color = Color(0xFFFFB74D), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Give this code to friends so they can join your match!", color = Color.LightGray, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("JOINED PLAYERS", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("${uniquePlayers.size} / $lobbyCapacity Players", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // LIST OF JOINED PLAYERS IN LOBBY
        uniquePlayers.forEach { p ->
            val isYou = (p.id == myDeviceId)
            val displayName = when {
                isYou && p.name.contains("(Host)") -> "${userNickname.ifBlank { "Host" }} (Host, You)"
                isYou && !p.name.contains("(You)") -> "${p.name} (You)"
                else -> p.name
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                border = BorderStroke(1.dp, if (isYou) Color(0xFF29B6F6) else Color(0xFF333333))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (p.role == Role.HUNTER) "🎯" else "🏃‍♂️", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (p.role == Role.HUNTER) "Catcher" else "Runner",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1E3A2B), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Ready", color = Color(0xFF00E676), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        if (isHost) {
            Button(
                onClick = onStartMatch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
            ) {
                Text("🚀 Start Match for All", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("⏳", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Waiting for Host to start match...", color = Color.LightGray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

// Reusable stepper component for adjusting numerical rules
@Composable
fun RuleStepper(
    title: String,
    subtitle: String,
    valueText: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, color = Color.Gray, fontSize = 11.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = onDecrease,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFF333333), shape = CircleShape)
                ) {
                    Text("-", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }

                Text(
                    text = valueText,
                    color = Color(0xFFFF5252),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier.width(72.dp),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = onIncrease,
                    modifier = Modifier
                        .size(34.dp)
                        .background(Color(0xFF333333), shape = CircleShape)
                ) {
                    Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
        }
    }
}

// ============================================================================
// SECTION 11: GAMEPLAY SCREEN WITH GOOGLE MAP BACKGROUND
// ----------------------------------------------------------------------------
// Purpose: Live match view rendering Google Map, player markers, zone boundary,
// status bars, suspense sound generator, and runner self-tagging controls.
// ============================================================================

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
    onTagPlayer: (String) -> Unit,
    onResetHome: () -> Unit,
    onSkipHeadStart: () -> Unit = {}
) {
    val playTingSound = rememberTingSound()       // Single ting sound generator hook
    val triggerVibration = rememberVibration()   // Vibration hook
    val deviceHeading = rememberDeviceHeading()  // Live compass device heading

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
                triggerVibration()
                delay(500.milliseconds)
            }
        }
    }

    // Ping alerts: Hunters hear ting sound 1.5s before ping; Runners vibrate 2s before ping
    LaunchedEffect(pingSeconds, isHeadStart) {
        if (!isHeadStart && pingSeconds == 2) {
            if (isMyRoleRunner) {
                // Runner vibrates 2 seconds before ping
                triggerVibration()
            } else {
                // Hunter hears ting sound 1.5 seconds before ping (after 500ms delay)
                delay(500.milliseconds)
                playTingSound()
            }
        }
    }

    val displaySeconds = (if (isHeadStart) headStartSeconds else totalSeconds).coerceAtLeast(0)
    val gameMin = displaySeconds / 60
    val gameSec = displaySeconds % 60
    val formattedGameTime = String.format(Locale.getDefault(), "%d:%02d", gameMin, gameSec)

    val safePingSeconds = pingSeconds.coerceAtLeast(0)
    val pingMin = safePingSeconds / 60
    val pingSec = safePingSeconds % 60
    val formattedPingTime = String.format(Locale.getDefault(), "%d:%02d", pingMin, pingSec)

    // Time since last ping calculation
    val timeSinceLastPingSeconds = (pingIntervalSeconds - pingSeconds).coerceAtLeast(0)
    val lastPingMin = timeSinceLastPingSeconds / 60
    val lastPingSec = timeSinceLastPingSeconds % 60
    val formattedLastPingTime = String.format(Locale.getDefault(), "%d:%02d", lastPingMin, lastPingSec)

    // Alpha / opacity for ghost ping markers slowly fading down to 0.25 (never fully disappears)
    val pingAlpha = (pingSeconds.toFloat() / pingIntervalSeconds.toFloat()).coerceIn(0.25f, 1.0f)

    var isMapEnlarged by remember { mutableStateOf(false) } // State for map window enlargement
    var isMapLockedToHeading by remember { mutableStateOf(false) } // Toggle for Map Heading Lock mode



    val context = LocalContext.current

    // Pre-create custom circle bitmap descriptors matching center pointer size (12dp)
    var greenTeammateIcon by remember {
        mutableStateOf(createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#00E676"), withWhiteInner = true))
    }
    var redEnemyIcon by remember {
        mutableStateOf(createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#FF5252"), withWhiteInner = false))
    }

    LaunchedEffect(Unit) {
        if (greenTeammateIcon == null) {
            greenTeammateIcon = createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#00E676"), withWhiteInner = true)
        }
        if (redEnemyIcon == null) {
            redEnemyIcon = createDotBitmapDescriptor(context, android.graphics.Color.parseColor("#FF5252"), withWhiteInner = false)
        }
    }

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

        // GOOGLE MAP VIEW CONTAINER
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(if (isMapEnlarged) 8.dp else 16.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            val widthDp = maxWidth.value
            val heightDp = maxHeight.value

            // Compute dynamic zoom level to cover ~82% of limiting window bounds
            val targetZoom = calculateZoomToFitZone(
                zoneRadiusMeters = zoneRadiusMeters,
                windowWidthDp = widthDp,
                windowHeightDp = heightDp,
            )

            val cameraTarget = myPlayer?.latLng ?: userLocation ?: zoneCenter

            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(cameraTarget, targetZoom)
            }

            val targetBearing = if (isMapLockedToHeading) deviceHeading else 0f

            // Keep camera target dynamically centered on user's live position, zoom, & bearing
            LaunchedEffect(cameraTarget, targetZoom, targetBearing) {
                try {
                    cameraPositionState.move(
                        CameraUpdateFactory.newCameraPosition(
                            CameraPosition.Builder(cameraPositionState.position)
                                .target(cameraTarget)
                                .zoom(targetZoom)
                                .bearing(targetBearing)
                                .build()
                        )
                    )
                } catch (_: Exception) {
                }
            }

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = false),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    zoomGesturesEnabled = false, // Zooming in and out disabled
                    myLocationButtonEnabled = false,
                    compassEnabled = false,
                    scrollGesturesEnabled = false, // Panning disabled to keep player centered
                    rotationGesturesEnabled = false // Map rotation / spinning disabled
                )
            ) {
                // Draw Red Boundary Circle (The Zone)
                Circle(
                    center = zoneCenter,
                    radius = zoneRadiusMeters.toDouble(),
                    strokeColor = Color.Red,
                    strokeWidth = 6f,
                    fillColor = Color(0x33E53935)
                )

                // Render Teammates (Green) & Enemies (Red) Markers on Map
                players.forEach { p ->
                    if (!p.isTagged && p.id != myDeviceId && p.id != "6") { // Skip "You" (drawn in fixed screen center pointer)
                        val isTeammate = (p.role == myRole)

                        // Head Start visibility rule: Runners CAN see Catchers during Head Start!
                        val showMarker = when {
                            !isHeadStart -> true // Outside Head Start, show markers according to ping/exposure rules
                            isTeammate -> true   // Teammates always visible
                            isMyRoleRunner && p.role == Role.HUNTER -> true // Runners see Catchers during Head Start!
                            else -> false        // Catchers cannot see Runners during Head Start
                        }

                        if (showMarker) {
                            val icon = if (isTeammate) greenTeammateIcon else redEnemyIcon
                            val position = if (p.role == Role.HUNTER || p.isLiveExposed) p.latLng else (p.lastPingLocation ?: p.latLng)
                            val alpha = if (p.role == Role.RUNNER && !p.isLiveExposed && !isTeammate) pingAlpha else 1.0f

                            Marker(
                                state = MarkerState(position = position),
                                icon = icon ?: BitmapDescriptorFactory.defaultMarker(),
                                alpha = alpha,
                                title = "${p.name} (${if (isTeammate) "Teammate" else "Enemy"})",
                                snippet = if (isTeammate) "Teammate Location" else "Last Known Location"
                            )
                        }
                    }
                }
            }

            // PLAYER POINTER STAYS FIXED IN THE CENTER OF THE SCREEN WITH SIGHT CONE
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Field-of-View Sight Cone showing direction the player is looking
                Canvas(modifier = Modifier.size(64.dp)) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val coneRadius = size.width * 0.45f
                    val arcAngle = 50f // 50 degree field of view cone

                    // If map rotates with player's heading, sight cone points straight UP (-90°); otherwise points in deviceHeading
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



            // ENLARGE / COLLAPSE MAP BUTTON AT BOTTOM RIGHT OF MAP WINDOW
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
                        // ENLARGE ICON: 2 separate arrows pointing away (Top-Left & Bottom-Right)
                        // Top-Left arrow
                        drawLine(color, Offset(w * 0.38f, h * 0.38f), Offset(0f, 0f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(0f, 0f), Offset(w * 0.30f, 0f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(0f, 0f), Offset(0f, h * 0.30f), strokeWidth = strokeWidth, cap = StrokeCap.Round)

                        // Bottom-Right arrow
                        drawLine(color, Offset(w * 0.62f, h * 0.62f), Offset(w, h), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(w, h), Offset(w * 0.70f, h), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                        drawLine(color, Offset(w, h), Offset(w, h * 0.70f), strokeWidth = strokeWidth, cap = StrokeCap.Round)
                    } else {
                        // COLLAPSE ICON: 2 separate arrows pointing inward toward center
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
                            // CATCHER / HUNTER DASHBOARD VIEW
                            val totalRunners = players.count { it.role == Role.RUNNER }
                            val activeRunners = players.count { it.role == Role.RUNNER && !it.isTagged }

                            Text("🎯 CATCHER DASHBOARD", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Runners Remaining: $activeRunners / $totalRunners", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Hunters see ghost pings every 2 min ($formattedLastPingTime since last ping). Hunt down all runners!", color = Color.LightGray, fontSize = 12.sp)
                        } else {
                            // RUNNER SURVIVAL VIEW: SELF-TAGGING BUTTON
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

                            // CONFIRMATION DIALOG FOR RUNNER
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

// ============================================================================
// SECTION 12: GAME OVER / RESULTS SCREEN (Win vs Lose Page)
// ----------------------------------------------------------------------------
// Purpose: Dedicated match results screen displaying Victory / Defeat status,
// match statistics, player list summary, and play again / home buttons.
// ============================================================================

@Composable
fun GameOverResultsScreen(
    myDeviceId: String,
    winnerText: String,
    players: List<Player>,
    matchDurationMinutes: Int,
    onPlayAgain: () -> Unit,
    onBackToMain: () -> Unit
) {
    val myPlayer = players.find { it.id == myDeviceId || it.id == "6" }
    val myRole = myPlayer?.role ?: Role.RUNNER
    val isMyRoleRunner = myRole == Role.RUNNER
    val isMyPlayerTagged = myPlayer?.isTagged ?: false

    // Determine if You Won or Lost
    val isHuntersWin = winnerText.contains("HUNTERS WIN", ignoreCase = true)
    val isRunnersWin = winnerText.contains("RUNNERS WIN", ignoreCase = true)

    val didIWin = if (isMyRoleRunner) {
        isRunnersWin && !isMyPlayerTagged
    } else {
        isHuntersWin
    }

    val totalRunners = players.count { it.role == Role.RUNNER }
    val taggedRunners = players.count { it.role == Role.RUNNER && it.isTagged }
    val survivingRunners = totalRunners - taggedRunners

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // VICTORY / DEFEAT BADGE ICON
        Text(
            text = if (didIWin) "🏆" else "💀",
            fontSize = 72.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        // TITLE HEADER
        Text(
            text = if (didIWin) "VICTORY!" else "DEFEAT!",
            color = if (didIWin) Color(0xFF00E676) else Color(0xFFFF5252),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 36.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = winnerText.ifBlank { if (didIWin) "You won the match!" else "You were eliminated!" },
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // YOUR MATCH PERFORMANCE CARD
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (didIWin) Color(0xFF1E3A2B) else Color(0xFF3A1E1E)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.5.dp,
                color = if (didIWin) Color(0xFF00E676) else Color(0xFFFF5252)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "YOUR MATCH SUMMARY",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Role", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (isMyRoleRunner) "🏃 Runner" else "🎯 Catcher",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Status", color = Color.Gray, fontSize = 11.sp)
                        Text(
                            text = if (isMyRoleRunner) {
                                if (isMyPlayerTagged) "Captured ❌" else "Survived ✅"
                            } else {
                                if (isHuntersWin) "Hunt Master 🏆" else "Time Expired ⏳"
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // MATCH STATS BREAKDOWN CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222222)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "MATCH STATISTICS",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Duration", color = Color.LightGray, fontSize = 14.sp)
                    Text("$matchDurationMinutes min", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Runners Captured", color = Color.LightGray, fontSize = 14.sp)
                    Text("$taggedRunners / $totalRunners", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Runners Survived", color = Color.LightGray, fontSize = 14.sp)
                    Text("$survivingRunners / $totalRunners", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // PLAY AGAIN BUTTON - RETURNS TO LOBBY PAGE
        Button(
            onClick = onPlayAgain,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (didIWin) Color(0xFF00E676) else Color(0xFFFF5252)
            )
        ) {
            Text(
                text = "🔄 Return to Lobby",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = if (didIWin) Color.Black else Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // MAIN MENU BUTTON
        Button(
            onClick = onBackToMain,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
        ) {
            Text("🏠 Back to Main Menu", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
