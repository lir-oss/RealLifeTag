package com.example.reallifetag.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import java.util.UUID

class AndroidPreferences(private val context: Context) : AppPreferences {
    private val prefs = context.getSharedPreferences("reallifetag_prefs", Context.MODE_PRIVATE)

    override fun getDeviceId(): String {
        return prefs.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString("device_id", it) }
        }
    }

    override fun getUserNickname(): String {
        return prefs.getString("user_nickname", "Player") ?: "Player"
    }

    override fun setUserNickname(nickname: String) {
        prefs.edit { putString("user_nickname", nickname) }
    }
}

@Composable
actual fun rememberAppPreferences(): AppPreferences {
    val context = LocalContext.current
    return remember(context) { AndroidPreferences(context) }
}
