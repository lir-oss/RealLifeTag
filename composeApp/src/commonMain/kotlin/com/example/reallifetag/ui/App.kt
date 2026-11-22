package com.example.reallifetag.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.reallifetag.data.rememberLobbyRepository
import com.example.reallifetag.model.GameStage
import com.example.reallifetag.model.LatLng
import com.example.reallifetag.model.LobbyData
import com.example.reallifetag.model.Player
import com.example.reallifetag.model.PlayerNetworkState
import com.example.reallifetag.model.Role
import com.example.reallifetag.platform.rememberAppPreferences
import com.example.reallifetag.platform.rememberAudioHaptics
import com.example.reallifetag.platform.rememberBackgroundGameTimer
import com.example.reallifetag.platform.rememberHeadingTracker
import com.example.reallifetag.platform.rememberLocationTracker
import com.example.reallifetag.ui.screens.GameOverResultsScreen
import com.example.reallifetag.ui.screens.GameplayScreen
import com.example.reallifetag.ui.screens.LobbyScreen
import com.example.reallifetag.ui.screens.MainMenuHomeScreen
import com.example.reallifetag.ui.screens.PublicLobbiesScreen
import com.example.reallifetag.ui.screens.SetupHomeScreen
import com.example.reallifetag.ui.theme.RealLifeTagTheme
import com.example.reallifetag.util.distanceInMeters
import com.example.reallifetag.util.generateRandomizedPlayersWithHost
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun App() {
    RealLifeTagTheme {
        TagGameApp()
    }
}

// SECTION 7: APP ROOT & STATE MANAGER (TagGameApp)
// Purpose: Orchestrates navigation stages, persistent device IDs, game engine loop,
// sensors, audio/haptics, and multiplayer sync models.
@Composable
fun TagGameApp() {
    val prefs = rememberAppPreferences()
    val scope = rememberCoroutineScope()
    val audioHaptics = rememberAudioHaptics()
    val backgroundTimer = rememberBackgroundGameTimer()
    val lobbyRepository = rememberLobbyRepository()

    val myDeviceId = remember { prefs.getDeviceId() }
    var savedNickname by remember { mutableStateOf(prefs.getUserNickname()) }

    fun updateSavedNickname(newNick: String) {
        val trimmed = newNick.trim().ifBlank { "Player" }
        savedNickname = trimmed
        prefs.setUserNickname(trimmed)
    }

    val locationTracker = rememberLocationTracker()
    val headingTracker = rememberHeadingTracker()

    val hasLocationPermission by locationTracker.hasPermission.collectAsState()
    val userLocation by locationTracker.currentLocation.collectAsState()
    val deviceHeading by headingTracker.headingDegrees.collectAsState()

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            locationTracker.startTracking()
        }
    }

    val defaultCenter = LatLng(37.7749, -122.4194) // Default fallback center (San Francisco)
    var matchCenter by remember { mutableStateOf(defaultCenter) }

    var stage by remember { mutableStateOf(GameStage.MAIN_MENU) }
    var isTestMatchMode by remember { mutableStateOf(false) }
    var isHost by remember { mutableStateOf(true) }
    var currentRoomCode by remember { mutableStateOf("TAG-1001") }
    var currentLobbyId by remember { mutableStateOf("") }

    // Rule Controls State
    var matchDurationMinutes by remember { mutableIntStateOf(30) }
    var headStartMinutes by remember { mutableIntStateOf(3) }
    var pingIntervalMinutes by remember { mutableIntStateOf(2) }
    var lobbyCapacity by remember { mutableIntStateOf(6) }
    var hunterCount by remember { mutableIntStateOf(2) }
    var runnerCount by remember { mutableIntStateOf(4) }
    var zoneSizeMeters by remember { mutableIntStateOf(350) }
    var isZoneShrinkEnabled by remember { mutableStateOf(true) }

    // Game Session Timers State
    var totalGameSeconds by remember { mutableIntStateOf(1800) }
    var headStartSeconds by remember { mutableIntStateOf(180) }
    val pingIntervalSeconds = pingIntervalMinutes * 60
    var currentPingTimer by remember { mutableIntStateOf(pingIntervalSeconds) }
    val zoneShrinkInterval = 300
    var zoneRadiusMeters by remember { mutableFloatStateOf(350f) }
    var zoneShrinkCount by remember { mutableIntStateOf(0) }

    // Dynamic Players state list
    val players = remember { mutableStateListOf<Player>() }
    var winnerText by remember { mutableStateOf("") }
    var zoneShrinkBanner by remember { mutableStateOf("") }

    // Creates a REAL Multiplayer Lobby using host's name
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

        val lobbyData = LobbyData(
            lobbyId = lobbyId,
            hostName = cleanNick,
            lobbyName = "$cleanNick's Lobby",
            matchDuration = matchDurationMinutes,
            zoneSize = zoneSizeMeters,
            isZoneShrinkEnabled = isZoneShrinkEnabled,
            matchCenterLat = hostLoc.latitude,
            matchCenterLng = hostLoc.longitude,
            isMatchStarted = false,
            headStartMinutes = headStartMinutes,
            pingIntervalMinutes = pingIntervalMinutes,
            players = listOf(PlayerNetworkState.fromPlayer(hostPlayer, isHost = true)),
            updatedAt = 0L
        )

        scope.launch {
            lobbyRepository.createLobby(lobbyData)
        }

        stage = GameStage.LOBBY
    }

    // Joins a Public Lobby by Lobby ID
    fun joinLobbyAsync(lobbyId: String, nick: String, onComplete: (Boolean) -> Unit) {
        val cleanNick = nick.trim().ifBlank { "Player" }
        val joinLoc = userLocation ?: defaultCenter

        scope.launch {
            val result = lobbyRepository.getLobby(lobbyId)
            val lobby = result.getOrNull()
            if (lobby != null) {
                matchCenter = LatLng(lobby.matchCenterLat, lobby.matchCenterLng)
                matchDurationMinutes = lobby.matchDuration
                zoneSizeMeters = lobby.zoneSize
                isZoneShrinkEnabled = lobby.isZoneShrinkEnabled

                updateSavedNickname(cleanNick)
                isHost = false
                currentLobbyId = lobbyId
                currentRoomCode = lobby.lobbyName
                winnerText = ""
                zoneShrinkBanner = ""

                totalGameSeconds = matchDurationMinutes * 60
                headStartSeconds = headStartMinutes * 60
                currentPingTimer = pingIntervalSeconds
                zoneRadiusMeters = zoneSizeMeters.toFloat()
                zoneShrinkCount = 0

                val joiningPlayer = PlayerNetworkState(
                    deviceId = myDeviceId,
                    nickname = cleanNick,
                    role = Role.RUNNER,
                    latitude = joinLoc.latitude,
                    longitude = joinLoc.longitude,
                    isTagged = false
                )

                val joinRes = lobbyRepository.joinLobby(lobbyId, joiningPlayer)
                if (joinRes.isSuccess) {
                    val currentList = lobby.players.map { it.toPlayer() }.toMutableList()
                    currentList.removeAll { it.id == myDeviceId }
                    currentList.add(joiningPlayer.toPlayer())

                    players.clear()
                    players.addAll(currentList)

                    stage = GameStage.LOBBY
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            } else {
                onComplete(false)
            }
        }
    }

    fun leaveCurrentLobby() {
        val lobbyIdToLeave = currentLobbyId
        scope.launch {
            if (lobbyIdToLeave.isNotBlank()) {
                lobbyRepository.leaveLobby(lobbyIdToLeave, myDeviceId, isHost)
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
            scope.launch {
                lobbyRepository.startMatch(currentLobbyId, players.map { PlayerNetworkState.fromPlayer(it, it.id == myDeviceId) })
            }
        }

        stage = if (headStartMinutes > 0) GameStage.HEAD_START else GameStage.ACTIVE_GAME
    }

    // Real-time repository sync while waiting in Lobby screen
    LaunchedEffect(stage, currentLobbyId) {
        if (stage == GameStage.LOBBY && currentLobbyId.isNotBlank() && !isTestMatchMode) {
            lobbyRepository.observeLobby(currentLobbyId).collect { lobby ->
                if (lobby == null) {
                    if (!isHost && stage == GameStage.LOBBY) {
                        currentLobbyId = ""
                        stage = GameStage.MAIN_MENU
                    }
                    return@collect
                }

                val hasHost = lobby.players.any { it.isHost || it.nickname.contains("(Host)") }
                if (!hasHost && !isHost && stage == GameStage.LOBBY) {
                    currentLobbyId = ""
                    stage = GameStage.MAIN_MENU
                    return@collect
                }

                val parsedList = lobby.players.map { it.toPlayer() }.distinctBy { it.id }
                players.clear()
                players.addAll(parsedList)

                if (lobby.isMatchStarted && stage == GameStage.LOBBY) {
                    matchDurationMinutes = lobby.matchDuration
                    headStartMinutes = lobby.headStartMinutes
                    pingIntervalMinutes = lobby.pingIntervalMinutes
                    zoneSizeMeters = lobby.zoneSize

                    totalGameSeconds = lobby.matchDuration * 60
                    headStartSeconds = lobby.headStartMinutes * 60
                    currentPingTimer = lobby.pingIntervalMinutes * 60
                    zoneRadiusMeters = lobby.zoneSize.toFloat()
                    zoneShrinkCount = 0

                    stage = if (lobby.headStartMinutes > 0) GameStage.HEAD_START else GameStage.ACTIVE_GAME
                }
            }
        }
    }

    // Active Match Ticker Loop
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

                        // Check Zone Shrink (2 times total during match, each by 15% if enabled)
                        if (isZoneShrinkEnabled && zoneShrinkCount < 2) {
                            val totalMatchSeconds = matchDurationMinutes * 60
                            val elapsedSeconds = totalMatchSeconds - totalGameSeconds
                            val threshold = (totalMatchSeconds / 3) * (zoneShrinkCount + 1)
                            if (elapsedSeconds >= threshold) {
                                zoneRadiusMeters = (zoneRadiusMeters * 0.85f).coerceAtLeast(30f)
                                zoneShrinkCount++
                                audioHaptics.playShrinkSound()
                                zoneShrinkBanner = "⚠️ ZONE SHRUNK! Radius is now ${zoneRadiusMeters.toInt()}m"
                            }
                        }

                        // Check Ping Trigger (Every X min)
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
                                        val autoTagged = newSec >= 30
                                        players[i] = p.copy(
                                            secondsOutsideZone = newSec,
                                            isLiveExposed = true,
                                            isTagged = if (autoTagged) true else p.isTagged
                                        )
                                        if (autoTagged) {
                                            if (currentLobbyId.isNotBlank() && !isTestMatchMode) {
                                                scope.launch {
                                                    lobbyRepository.updatePlayerLocation(
                                                        lobbyId = currentLobbyId,
                                                        playerId = p.id,
                                                        lat = p.latLng.latitude,
                                                        lng = p.latLng.longitude,
                                                        isTagged = true,
                                                        lastPingLat = p.lastPingLocation?.latitude,
                                                        lastPingLng = p.lastPingLocation?.longitude
                                                    )
                                                }
                                            }
                                            if (p.id == myDeviceId || p.id == "6") {
                                                winnerText = "TAGGED! You stayed outside the zone for 30 seconds."
                                                stage = GameStage.GAME_OVER
                                            }
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
    LaunchedEffect(stage, currentLobbyId) {
        if ((stage == GameStage.HEAD_START || stage == GameStage.ACTIVE_GAME) && currentLobbyId.isNotBlank() && !isTestMatchMode) {
            lobbyRepository.observeLobby(currentLobbyId).collect { lobby ->
                if (lobby == null) return@collect
                if (lobby.isHeadStartSkipped && stage == GameStage.HEAD_START) {
                    stage = GameStage.ACTIVE_GAME
                }

                for (pNet in lobby.players) {
                    if (pNet.deviceId == myDeviceId) continue
                    val idx = players.indexOfFirst { it.id == pNet.deviceId }
                    if (idx != -1) {
                        players[idx] = players[idx].copy(
                            latLng = LatLng(pNet.latitude, pNet.longitude),
                            isTagged = pNet.isTagged,
                            lastPingLocation = if (pNet.lastPingLat != null && pNet.lastPingLng != null) LatLng(pNet.lastPingLat, pNet.lastPingLng) else players[idx].lastPingLocation
                        )
                    }
                }
            }
        }
    }

    // Keep "You" updated with live GPS user location and push live location during active game
    LaunchedEffect(userLocation) {
        userLocation?.let { loc ->
            val index = players.indexOfFirst { it.id == myDeviceId || it.id == "6" }
            if (index != -1) {
                players[index] = players[index].copy(latLng = loc)
            }
            if ((stage == GameStage.HEAD_START || stage == GameStage.ACTIVE_GAME) && currentLobbyId.isNotBlank() && !isTestMatchMode) {
                val myP = players.find { it.id == myDeviceId || it.id == "6" }
                scope.launch {
                    lobbyRepository.updatePlayerLocation(
                        lobbyId = currentLobbyId,
                        playerId = myDeviceId,
                        lat = loc.latitude,
                        lng = loc.longitude,
                        isTagged = myP?.isTagged ?: false,
                        lastPingLat = myP?.lastPingLocation?.latitude,
                        lastPingLng = myP?.lastPingLocation?.longitude
                    )
                }
            }
        }
    }

    // Background Service / Timer Integration
    DisposableEffect(stage) {
        if (stage == GameStage.HEAD_START || stage == GameStage.ACTIVE_GAME) {
            backgroundTimer.start(
                statusTextProvider = {
                    if (stage == GameStage.HEAD_START) {
                        "Pre-Run Head Start: ${headStartSeconds}s"
                    } else {
                        val m = totalGameSeconds / 60
                        val s = totalGameSeconds % 60
                        "Time left: $m:${if (s < 10) "0$s" else "$s"}"
                    }
                },
                onTick = {
                    // Periodic background callback
                }
            )
        } else {
            backgroundTimer.stop()
        }
        onDispose {
            if (stage != GameStage.HEAD_START && stage != GameStage.ACTIVE_GAME) {
                backgroundTimer.stop()
            }
        }
    }

    // Observe active public lobbies for JOIN_GAME screen
    val publicLobbies by lobbyRepository.observeLobbies().collectAsState(initial = emptyList())

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
                    onRequestPermission = { locationTracker.requestPermission() },
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
                    onRequestPermission = { locationTracker.requestPermission() },
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
                    lobbiesList = publicLobbies,
                    isLoading = false,
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
                    val formattedShrinkCd = "${sMin}:${if (sSec < 10) "0$sSec" else "$sSec"}"

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
                        onRequestPermission = { locationTracker.requestPermission() },
                        gameOverText = winnerText,
                        zoneShrinkCountdown = formattedShrinkCd,
                        zoneShrinkBanner = zoneShrinkBanner,
                        isZoneShrinkEnabled = isZoneShrinkEnabled,
                        audioHaptics = audioHaptics,
                        deviceHeading = deviceHeading,
                        onTagPlayer = { playerId ->
                            val index = players.indexOfFirst { it.id == playerId }
                            if (index != -1) {
                                val p = players[index].copy(isTagged = true)
                                players[index] = p
                                if (currentLobbyId.isNotBlank() && !isTestMatchMode) {
                                    scope.launch {
                                        lobbyRepository.updatePlayerLocation(
                                            lobbyId = currentLobbyId,
                                            playerId = playerId,
                                            lat = p.latLng.latitude,
                                            lng = p.latLng.longitude,
                                            isTagged = true,
                                            lastPingLat = p.lastPingLocation?.latitude,
                                            lastPingLng = p.lastPingLocation?.longitude
                                        )
                                    }
                                }
                            }
                        },
                        onResetHome = { leaveCurrentLobby() },
                        onSkipHeadStart = {
                            stage = GameStage.ACTIVE_GAME
                            if (isHost && currentLobbyId.isNotBlank() && !isTestMatchMode) {
                                scope.launch {
                                    lobbyRepository.skipHeadStart(currentLobbyId)
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
                        totalGameSeconds = matchDurationMinutes * 60
                        headStartSeconds = headStartMinutes * 60
                        currentPingTimer = pingIntervalSeconds
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
