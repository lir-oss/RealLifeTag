package com.example.reallifetag.model

data class Player(
    val id: String,                         // Unique ID for player
    val name: String,                       // Nickname of player
    val role: Role,                         // Role (HUNTER or RUNNER)
    val latLng: LatLng,                     // Live LatLng coordinates
    val isTagged: Boolean = false,          // Tag status (true if caught)
    val lastPingLocation: LatLng? = null,   // Location snapshot from most recent ping
    val secondsOutsideZone: Int = 0,        // Counter for seconds spent outside zone
    val isLiveExposed: Boolean = false,     // True if runner is exposed live for staying out >30s
)
