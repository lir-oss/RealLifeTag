package com.example.reallifetag.util

import com.example.reallifetag.model.LatLng
import com.example.reallifetag.model.Player
import com.example.reallifetag.model.Role
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

// Extension function on LatLng to generate a new coordinate shifted by meters North & East
fun LatLng.offsetByMeters(northMeters: Double, eastMeters: Double): LatLng {
    val latDiff = northMeters / 111111.0 // 1 degree latitude ≈ 111,111 meters
    val radLat = latitude * (kotlin.math.PI / 180.0)
    val lngDiff = eastMeters / (111111.0 * cos(radLat)) // 1 degree longitude scaled by cos(lat)
    return LatLng(latitude + latDiff, longitude + lngDiff)
}

// Calculates accurate real-world distance in meters between two LatLng coordinates (Haversine formula)
fun distanceInMeters(loc1: LatLng, loc2: LatLng): Float {
    val earthRadius = 6371000.0 // Earth mean radius in meters
    val dLat = (loc2.latitude - loc1.latitude) * (kotlin.math.PI / 180.0)
    val dLng = (loc2.longitude - loc1.longitude) * (kotlin.math.PI / 180.0)
    val lat1 = loc1.latitude * (kotlin.math.PI / 180.0)
    val lat2 = loc2.latitude * (kotlin.math.PI / 180.0)

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(lat1) * cos(lat2) *
            sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return (earthRadius * c).toFloat()
}

// Calculates dynamic map zoom to fit zone boundary comfortably on screen
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

    val zoneCoverageRatio = 4.5f // 4.5f visible coverage ratio

    val targetDiameterMeters = maxRadiusNeeded * 2f / zoneCoverageRatio
    val factor = (25046f * limitingDimensionDp) / targetDiameterMeters
    if (factor <= 0f || factor.isNaN()) return 14.5f

    val zoom = (ln(factor) / ln(2f)).toFloat()
    if (zoom.isNaN() || zoom.isInfinite()) return 14.5f

    return zoom.coerceIn(10f, 18.5f)
}

// Generates initial randomized players list for a match with host's ratio (used in test match & fallback)
fun generateRandomizedPlayersWithHost(
    hunterCount: Int,
    runnerCount: Int,
    gameCenter: LatLng,
    userNick: String,
    myDeviceId: String = "6"
): List<Player> {
    val totalPlayers = (hunterCount + runnerCount).coerceAtLeast(2)
    val rolesList = mutableListOf<Role>()
    repeat(hunterCount.coerceAtLeast(1)) { rolesList.add(Role.HUNTER) }
    repeat(runnerCount.coerceAtLeast(1)) { rolesList.add(Role.RUNNER) }
    rolesList.shuffle()

    val myNickname = userNick.trim().ifBlank { "Player" }

    val list = mutableListOf<Player>()
    var hunterIdx = 1
    var runnerIdx = 1

    for (i in 0 until totalPlayers) {
        val role = if (i < rolesList.size) rolesList[i] else Role.RUNNER
        val isYou = (i == totalPlayers - 1)
        val angle = (i * 360.0 / totalPlayers) * (kotlin.math.PI / 180.0)
        val dist = if (isYou) 0.0 else (60.0 + i * 20.0)
        val offsetN = dist * cos(angle)
        val offsetE = dist * sin(angle)

        val name = if (isYou) {
            myNickname
        } else if (role == Role.HUNTER) {
            "Catcher ${hunterIdx++}"
        } else {
            "Runner ${runnerIdx++}"
        }

        list.add(
            Player(
                id = if (isYou) myDeviceId else (i + 1).toString(),
                name = name,
                role = role,
                latLng = gameCenter.offsetByMeters(offsetN, offsetE),
            )
        )
    }

    return list
}
