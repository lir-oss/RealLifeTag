package com.example.reallifetag.platform

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.example.reallifetag.GameTimerService

class AndroidBackgroundGameTimer(private val context: Context) : BackgroundGameTimer {
    override fun start(statusTextProvider: () -> String, onTick: () -> Unit) {
        GameTimerService.getStatusText = statusTextProvider
        GameTimerService.onTick = onTick
        GameTimerService.startService(context)
    }

    override fun stop() {
        GameTimerService.stopService(context)
        GameTimerService.onTick = null
    }

    override fun updateNotification(text: String) {
        GameTimerService.getStatusText = { text }
    }
}

@Composable
actual fun rememberBackgroundGameTimer(): BackgroundGameTimer {
    val context = LocalContext.current
    return remember(context) { AndroidBackgroundGameTimer(context) }
}
