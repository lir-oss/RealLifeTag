package com.example.reallifetag.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.reallifetag.model.LobbyData
import com.example.reallifetag.model.PlayerNetworkState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

class IosLobbyRepository : LobbyRepository {
    private val lobbiesFlow = MutableStateFlow<Map<String, LobbyData>>(emptyMap())

    override suspend fun createLobby(lobby: LobbyData): Result<Unit> = runCatching {
        val current = lobbiesFlow.value.toMutableMap()
        current[lobby.lobbyId] = lobby
        lobbiesFlow.value = current
    }

    override suspend fun getLobby(lobbyId: String): Result<LobbyData?> = runCatching {
        lobbiesFlow.value[lobbyId]
    }

    override suspend fun joinLobby(lobbyId: String, player: PlayerNetworkState): Result<Unit> = runCatching {
        val current = lobbiesFlow.value.toMutableMap()
        val lobby = current[lobbyId] ?: return@runCatching
        val updatedPlayers = lobby.players.filter { it.deviceId != player.deviceId }.toMutableList()
        updatedPlayers.add(player)
        current[lobbyId] = lobby.copy(players = updatedPlayers)
        lobbiesFlow.value = current
    }

    override suspend fun leaveLobby(lobbyId: String, playerId: String, isHost: Boolean): Result<Unit> = runCatching {
        val current = lobbiesFlow.value.toMutableMap()
        if (isHost) {
            current.remove(lobbyId)
        } else {
            val lobby = current[lobbyId] ?: return@runCatching
            val updatedPlayers = lobby.players.filter { it.deviceId != playerId }
            current[lobbyId] = lobby.copy(players = updatedPlayers)
        }
        lobbiesFlow.value = current
    }

    override fun observeLobbies(): Flow<List<LobbyData>> = lobbiesFlow.asStateFlow().map { map ->
        map.values.filter { !it.isMatchStarted && it.players.isNotEmpty() }
    }

    override fun observeLobby(lobbyId: String): Flow<LobbyData?> = lobbiesFlow.asStateFlow().map { map ->
        map[lobbyId]
    }

    override suspend fun updatePlayerLocation(
        lobbyId: String,
        playerId: String,
        lat: Double,
        lng: Double,
        isTagged: Boolean,
        lastPingLat: Double?,
        lastPingLng: Double?
    ): Result<Unit> = runCatching {
        val current = lobbiesFlow.value.toMutableMap()
        val lobby = current[lobbyId] ?: return@runCatching
        val updatedPlayers = lobby.players.map { p ->
            if (p.deviceId == playerId) {
                p.copy(
                    latitude = lat,
                    longitude = lng,
                    isTagged = isTagged,
                    lastPingLat = lastPingLat ?: p.lastPingLat,
                    lastPingLng = lastPingLng ?: p.lastPingLng
                )
            } else {
                p
            }
        }
        current[lobbyId] = lobby.copy(players = updatedPlayers)
        lobbiesFlow.value = current
    }

    override suspend fun startMatch(lobbyId: String, players: List<PlayerNetworkState>): Result<Unit> = runCatching {
        val current = lobbiesFlow.value.toMutableMap()
        val lobby = current[lobbyId] ?: return@runCatching
        current[lobbyId] = lobby.copy(isMatchStarted = true, players = players)
        lobbiesFlow.value = current
    }

    override suspend fun skipHeadStart(lobbyId: String): Result<Unit> = runCatching {
        val current = lobbiesFlow.value.toMutableMap()
        val lobby = current[lobbyId] ?: return@runCatching
        current[lobbyId] = lobby.copy(isHeadStartSkipped = true)
        lobbiesFlow.value = current
    }
}

@Composable
actual fun rememberLobbyRepository(): LobbyRepository {
    return remember { IosLobbyRepository() }
}
