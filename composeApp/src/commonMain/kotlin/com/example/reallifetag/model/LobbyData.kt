package com.example.reallifetag.model

data class LobbyData(
    val lobbyId: String,
    val hostName: String,
    val lobbyName: String,
    val matchDuration: Int,
    val zoneSize: Int,
    val isZoneShrinkEnabled: Boolean,
    val matchCenterLat: Double,
    val matchCenterLng: Double,
    val isMatchStarted: Boolean = false,
    val isHeadStartSkipped: Boolean = false,
    val headStartMinutes: Int = 3,
    val pingIntervalMinutes: Int = 2,
    val players: List<PlayerNetworkState> = emptyList(),
    val updatedAt: Long = 0L
)
