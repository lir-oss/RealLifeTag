package com.example.reallifetag.platform

import androidx.compose.runtime.Composable

interface AudioHaptics {
    fun playTingSound()
    fun playShrinkSound()
    fun triggerVibration(durationMillis: Long = 400L)
}

@Composable
expect fun rememberAudioHaptics(): AudioHaptics
