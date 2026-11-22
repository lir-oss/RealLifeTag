package com.example.reallifetag.data

import androidx.compose.runtime.Composable
import com.example.reallifetag.model.LobbyData
import com.example.reallifetag.model.PlayerNetworkState
import kotlinx.coroutines.flow.Flow

interface LobbyRepository {
    suspend fun createLobby(lobby: LobbyData): Result<Unit>
    suspend fun getLobby(lobbyId: String): Result<LobbyData?>
    suspend fun joinLobby(lobbyId: String, player: PlayerNetworkState): Result<Unit>
    suspend fun leaveLobby(lobbyId: String, playerId: String, isHost: Boolean): Result<Unit>
    fun observeLobbies(): Flow<List<LobbyData>>
    fun observeLobby(lobbyId: String): Flow<LobbyData?>
    suspend fun updatePlayerLocation(
        lobbyId: String,
        playerId: String,
        lat: Double,
        lng: Double,
        isTagged: Boolean,
        lastPingLat: Double?,
        lastPingLng: Double?
    ): Result<Unit>
    suspend fun startMatch(lobbyId: String, players: List<PlayerNetworkState>): Result<Unit>
    suspend fun skipHeadStart(lobbyId: String): Result<Unit>
}

@Composable
expect fun rememberLobbyRepository(): LobbyRepository
