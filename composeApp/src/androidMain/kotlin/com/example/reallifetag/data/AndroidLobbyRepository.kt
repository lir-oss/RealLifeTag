package com.example.reallifetag.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.example.reallifetag.model.LobbyData
import com.example.reallifetag.model.PlayerNetworkState
import com.example.reallifetag.model.Role
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AndroidLobbyRepository : LobbyRepository {
    private val db = FirebaseFirestore.getInstance()

    override suspend fun createLobby(lobby: LobbyData): Result<Unit> = runCatching {
        val lobbyData = mapOf(
            "lobbyId" to lobby.lobbyId,
            "hostName" to lobby.hostName,
            "lobbyName" to lobby.lobbyName,
            "matchDuration" to lobby.matchDuration,
            "zoneSize" to lobby.zoneSize,
            "isZoneShrinkEnabled" to lobby.isZoneShrinkEnabled,
            "matchCenterLat" to lobby.matchCenterLat,
            "matchCenterLng" to lobby.matchCenterLng,
            "isMatchStarted" to lobby.isMatchStarted,
            "isHeadStartSkipped" to lobby.isHeadStartSkipped,
            "headStartMinutes" to lobby.headStartMinutes,
            "pingIntervalMinutes" to lobby.pingIntervalMinutes,
            "players" to lobby.players.map { p ->
                mapOf(
                    "id" to p.deviceId,
                    "name" to p.nickname,
                    "role" to p.role.name,
                    "lat" to p.latitude,
                    "lng" to p.longitude,
                    "isTagged" to p.isTagged
                )
            },
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection("lobbies").document(lobby.lobbyId).set(lobbyData).await()
    }

    override suspend fun getLobby(lobbyId: String): Result<LobbyData?> = runCatching {
        val doc = db.collection("lobbies").document(lobbyId).get().await()
        if (doc != null && doc.exists()) parseLobby(doc.id, doc.data) else null
    }

    override suspend fun joinLobby(lobbyId: String, player: PlayerNetworkState): Result<Unit> = runCatching {
        val doc = db.collection("lobbies").document(lobbyId).get().await()
        if (doc != null && doc.exists()) {
            val rawPlayers = doc.get("players") as? List<*> ?: emptyList<Any>()
            val updated = rawPlayers.mapNotNull { it as? Map<*, *> }
                .filter { it["id"]?.toString() != player.deviceId }
                .toMutableList()

            updated.add(
                mapOf(
                    "id" to player.deviceId,
                    "name" to player.nickname,
                    "role" to player.role.name,
                    "lat" to player.latitude,
                    "lng" to player.longitude,
                    "isTagged" to player.isTagged
                )
            )
            db.collection("lobbies").document(lobbyId).update("players", updated).await()
        }
    }

    override suspend fun leaveLobby(lobbyId: String, playerId: String, isHost: Boolean): Result<Unit> = runCatching {
        if (isHost) {
            db.collection("lobbies").document(lobbyId).delete().await()
        } else {
            val doc = db.collection("lobbies").document(lobbyId).get().await()
            if (doc != null && doc.exists()) {
                val rawPlayers = doc.get("players") as? List<*> ?: emptyList<Any>()
                val updated = rawPlayers.mapNotNull { it as? Map<*, *> }
                    .filter { it["id"]?.toString() != playerId }
                db.collection("lobbies").document(lobbyId).update("players", updated).await()
            }
        }
    }

    override fun observeLobbies(): Flow<List<LobbyData>> = callbackFlow {
        val listener = db.collection("lobbies").addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val now = System.currentTimeMillis()
                val list = snapshot.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val isMatchStarted = data["isMatchStarted"] as? Boolean ?: false
                    val rawPlayers = data["players"] as? List<*> ?: emptyList<Any>()
                    val hostName = data["hostName"] as? String ?: ""
                    val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L

                    val hasHost = rawPlayers.any { pObj ->
                        val pMap = pObj as? Map<*, *> ?: return@any false
                        val pName = pMap["name"] as? String ?: ""
                        pName.contains("(Host)") || (hostName.isNotBlank() && pName.startsWith(hostName))
                    }
                    val isStale = (updatedAt > 0L && (now - updatedAt) > 30 * 60 * 1000L)

                    if (isMatchStarted || rawPlayers.isEmpty() || !hasHost || isStale) {
                        try { db.collection("lobbies").document(doc.id).delete() } catch (_: Exception) {}
                        return@mapNotNull null
                    }
                    parseLobby(doc.id, data)
                }
                trySend(list)
            }
        }
        awaitClose { listener.remove() }
    }

    override fun observeLobby(lobbyId: String): Flow<LobbyData?> = callbackFlow {
        val listener = db.collection("lobbies").document(lobbyId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null || !snapshot.exists()) {
                trySend(null)
                return@addSnapshotListener
            }
            trySend(parseLobby(snapshot.id, snapshot.data))
        }
        awaitClose { listener.remove() }
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
        val doc = db.collection("lobbies").document(lobbyId).get().await()
        if (doc != null && doc.exists()) {
            val rawList = doc.get("players") as? List<*> ?: emptyList<Any>()
            val updated = rawList.map { item ->
                val map = (item as? Map<*, *>)?.toMutableMap() ?: mutableMapOf<Any?, Any?>()
                if (map["id"]?.toString() == playerId) {
                    map["lat"] = lat
                    map["lng"] = lng
                    map["isTagged"] = isTagged
                    if (lastPingLat != null) map["lastPingLat"] = lastPingLat
                    if (lastPingLng != null) map["lastPingLng"] = lastPingLng
                }
                map
            }
            db.collection("lobbies").document(lobbyId).update("players", updated).await()
        }
    }

    override suspend fun startMatch(lobbyId: String, players: List<PlayerNetworkState>): Result<Unit> = runCatching {
        val playersData = players.map { p ->
            mapOf(
                "id" to p.deviceId,
                "name" to p.nickname,
                "role" to p.role.name,
                "lat" to p.latitude,
                "lng" to p.longitude,
                "isTagged" to p.isTagged
            )
        }
        db.collection("lobbies").document(lobbyId).update(
            mapOf(
                "isMatchStarted" to true,
                "players" to playersData
            )
        ).await()
    }

    override suspend fun skipHeadStart(lobbyId: String): Result<Unit> = runCatching {
        db.collection("lobbies").document(lobbyId).update("isHeadStartSkipped", true).await()
    }

    private fun parseLobby(docId: String, data: Map<String, Any?>?): LobbyData? {
        if (data == null) return null
        val rawPlayers = data["players"] as? List<*> ?: emptyList<Any>()
        val parsedPlayers = rawPlayers.mapNotNull { item ->
            val pMap = item as? Map<*, *> ?: return@mapNotNull null
            val id = pMap["id"]?.toString() ?: return@mapNotNull null
            val name = pMap["name"]?.toString() ?: "Player"
            val roleStr = pMap["role"]?.toString() ?: Role.RUNNER.name
            val role = try { Role.valueOf(roleStr) } catch (_: Exception) { Role.RUNNER }
            val lat = (pMap["lat"] as? Number)?.toDouble() ?: 0.0
            val lng = (pMap["lng"] as? Number)?.toDouble() ?: 0.0
            val isTagged = (pMap["isTagged"] as? Boolean) ?: false
            val lastPingLat = (pMap["lastPingLat"] as? Number)?.toDouble()
            val lastPingLng = (pMap["lastPingLng"] as? Number)?.toDouble()
            PlayerNetworkState(
                deviceId = id,
                nickname = name,
                role = role,
                latitude = lat,
                longitude = lng,
                isTagged = isTagged,
                lastPingLat = lastPingLat,
                lastPingLng = lastPingLng,
                isHost = name.contains("(Host)")
            )
        }

        return LobbyData(
            lobbyId = (data["lobbyId"] as? String) ?: docId,
            hostName = (data["hostName"] as? String) ?: "Host",
            lobbyName = (data["lobbyName"] as? String) ?: "Match Lobby",
            matchDuration = (data["matchDuration"] as? Number)?.toInt() ?: 30,
            zoneSize = (data["zoneSize"] as? Number)?.toInt() ?: 350,
            isZoneShrinkEnabled = (data["isZoneShrinkEnabled"] as? Boolean) ?: true,
            matchCenterLat = (data["matchCenterLat"] as? Number)?.toDouble() ?: 37.7749,
            matchCenterLng = (data["matchCenterLng"] as? Number)?.toDouble() ?: -122.4194,
            isMatchStarted = (data["isMatchStarted"] as? Boolean) ?: false,
            isHeadStartSkipped = (data["isHeadStartSkipped"] as? Boolean) ?: false,
            headStartMinutes = (data["headStartMinutes"] as? Number)?.toInt() ?: 3,
            pingIntervalMinutes = (data["pingIntervalMinutes"] as? Number)?.toInt() ?: 2,
            players = parsedPlayers,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )
    }
}

@Composable
actual fun rememberLobbyRepository(): LobbyRepository {
    return remember { AndroidLobbyRepository() }
}
