package com.example.reallifetag.model

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
    val lastUpdatedMillis: Long = 0L
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

    companion object {
        fun fromPlayer(player: Player, isHost: Boolean = false, timestamp: Long = 0L): PlayerNetworkState = PlayerNetworkState(
            deviceId = player.id,
            nickname = player.name,
            role = player.role,
            latitude = player.latLng.latitude,
            longitude = player.latLng.longitude,
            isTagged = player.isTagged,
            lastPingLat = player.lastPingLocation?.latitude,
            lastPingLng = player.lastPingLocation?.longitude,
            secondsOutsideZone = player.secondsOutsideZone,
            isLiveExposed = player.isLiveExposed,
            isHost = isHost,
            lastUpdatedMillis = timestamp
        )
    }
}
