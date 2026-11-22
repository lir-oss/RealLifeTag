package com.example.reallifetag.platform

import androidx.compose.runtime.Composable

interface BackgroundGameTimer {
    fun start(statusTextProvider: () -> String, onTick: () -> Unit)
    fun stop()
    fun updateNotification(text: String)
}

@Composable
expect fun rememberBackgroundGameTimer(): BackgroundGameTimer
