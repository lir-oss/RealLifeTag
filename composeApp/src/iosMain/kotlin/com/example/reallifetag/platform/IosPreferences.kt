package com.example.reallifetag.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults

class IosPreferences : AppPreferences {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getDeviceId(): String {
        val existing = defaults.stringForKey("device_id")
        if (!existing.isNullOrBlank()) return existing
        val newId = NSUUID().UUIDString()
        defaults.setObject(newId, forKey = "device_id")
        return newId
    }

    override fun getUserNickname(): String {
        return defaults.stringForKey("user_nickname") ?: "Player"
    }

    override fun setUserNickname(nickname: String) {
        defaults.setObject(nickname, forKey = "user_nickname")
    }
}

@Composable
actual fun rememberAppPreferences(): AppPreferences {
    return remember { IosPreferences() }
}
